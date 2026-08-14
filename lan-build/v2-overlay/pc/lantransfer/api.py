from __future__ import annotations

import asyncio
import hmac
import json
import re
import time
from contextlib import asynccontextmanager
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import quote

from fastapi import FastAPI, Header, HTTPException, Request, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse, StreamingResponse

from .config import AppConfig
from .connection_manager import ConnectionManager
from .models import PairRequest, UploadInitRequest
from .security import PairingStore, is_private_client, sanitize_filename
from .transfer_store import OffsetError, TransferStore

_RANGE_RE = re.compile(r"bytes=(\d+)-(\d*)")
_CONTENT_RANGE_RE = re.compile(r"bytes (\d+)-(\d+)/(\d+)")


@dataclass(slots=True)
class AppContext:
    config: AppConfig
    pin: str
    auth: PairingStore
    store: TransferStore
    manager: ConnectionManager
    instance_id: str = ""
    device_name: str = "Windows PC"


def create_app(ctx: AppContext) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        ctx.manager.bind_loop(asyncio.get_running_loop())
        yield

    app = FastAPI(title="LAN File Transfer API", version="2.0.0", lifespan=lifespan)
    pair_failures: dict[str, list[float]] = {}

    @app.middleware("http")
    async def lan_only(request: Request, call_next):
        if ctx.config.private_network_only:
            host = request.client.host if request.client else ""
            if not is_private_client(host):
                return JSONResponse({"detail": "LAN clients only"}, status_code=403)
        return await call_next(request)

    def auth_device(authorization: str | None, device_id: str | None) -> str:
        if not authorization or not authorization.lower().startswith("bearer "):
            raise HTTPException(401, "Bearer token required")
        if not device_id:
            raise HTTPException(401, "X-Device-Id required")
        token = authorization.split(" ", 1)[1].strip()
        if not ctx.auth.verify(device_id, token):
            raise HTTPException(401, "Invalid device token")
        return device_id

    @app.get("/api/ping")
    async def ping():
        return {"service": "LAN File Transfer", "version": "2.0.0", "port": ctx.config.port,
                "instance_id": ctx.instance_id, "device_name": ctx.device_name}

    @app.post("/api/pair")
    async def pair(payload: PairRequest, request: Request):
        host = request.client.host if request.client else "unknown"
        now = time.time()
        recent = [t for t in pair_failures.get(host, []) if now - t < 60]
        pair_failures[host] = recent
        if len(recent) >= 10:
            raise HTTPException(429, "تلاش‌های Pair زیاد است؛ یک دقیقه بعد دوباره امتحان کنید")
        if ctx.config.pin_enabled and not hmac.compare_digest(payload.pin, ctx.pin):
            recent.append(now)
            pair_failures[host] = recent
            raise HTTPException(403, "PIN اشتباه است")
        pair_failures.pop(host, None)
        device_id, token = ctx.auth.pair(payload.device_name)
        return {"device_id": device_id, "token": token, "device_name": payload.device_name,
                "instance_id": ctx.instance_id, "pc_name": ctx.device_name}

    @app.get("/api/transfers")
    async def transfers(authorization: str | None = Header(default=None), x_device_id: str | None = Header(default=None)):
        device_id = auth_device(authorization, x_device_id)
        return [r.to_public() for r in ctx.store.list() if r.device_id == device_id]

    @app.post("/api/upload/init")
    async def upload_init(payload: UploadInitRequest, authorization: str | None = Header(default=None), x_device_id: str | None = Header(default=None)):
        device_id = auth_device(authorization, x_device_id)
        try:
            rec = ctx.store.init_upload(device_id, payload.name, payload.size)
        except ValueError as exc:
            raise HTTPException(400, str(exc)) from exc
        return {"transfer_id": rec.id, "offset": rec.bytes_done, "chunk_size": ctx.config.chunk_size}

    @app.get("/api/upload/{transfer_id}/status")
    async def upload_status(transfer_id: str, authorization: str | None = Header(default=None), x_device_id: str | None = Header(default=None)):
        device_id = auth_device(authorization, x_device_id)
        rec = ctx.store.get(transfer_id)
        if not rec or rec.device_id != device_id or rec.direction != "android_to_pc":
            raise HTTPException(404, "Transfer not found")
        return {"transfer_id": rec.id, "offset": rec.bytes_done, "status": rec.status, "size": rec.size}

    @app.put("/api/upload/{transfer_id}")
    async def upload_chunk(transfer_id: str, request: Request, content_range: str | None = Header(default=None),
                           authorization: str | None = Header(default=None), x_device_id: str | None = Header(default=None)):
        device_id = auth_device(authorization, x_device_id)
        rec = ctx.store.get(transfer_id)
        if not rec or rec.device_id != device_id or rec.direction != "android_to_pc":
            raise HTTPException(404, "Transfer not found")
        if not content_range:
            raise HTTPException(400, "Content-Range required")
        match = _CONTENT_RANGE_RE.fullmatch(content_range.strip())
        if not match:
            raise HTTPException(400, "Invalid Content-Range")
        start, end, total = map(int, match.groups())
        body = await request.body()
        if end - start + 1 != len(body):
            raise HTTPException(400, "Chunk length mismatch")
        if len(body) > max(ctx.config.chunk_size * 2, 2 * 1024 * 1024):
            raise HTTPException(413, "Chunk too large")
        try:
            updated = await asyncio.to_thread(ctx.store.write_upload_chunk, transfer_id, start, body, total)
        except OffsetError as exc:
            return JSONResponse({"detail": "offset_mismatch", "expected_offset": exc.expected_offset}, status_code=409)
        except ValueError as exc:
            raise HTTPException(400, str(exc)) from exc
        return {"transfer_id": updated.id, "offset": updated.bytes_done, "status": updated.status}

    @app.get("/api/download/{transfer_id}")
    async def download(transfer_id: str, range_header: str | None = Header(default=None, alias="Range"),
                       authorization: str | None = Header(default=None), x_device_id: str | None = Header(default=None)):
        device_id = auth_device(authorization, x_device_id)
        rec = ctx.store.get(transfer_id)
        if not rec or rec.device_id != device_id or rec.direction != "pc_to_android" or not rec.source_path:
            raise HTTPException(404, "Transfer not found")
        source = Path(rec.source_path)
        if not source.exists() or not source.is_file():
            raise HTTPException(410, "Source file no longer exists")
        size = source.stat().st_size
        if size != rec.size:
            raise HTTPException(409, "Source file changed after transfer was queued")
        start = 0
        if range_header:
            m = _RANGE_RE.fullmatch(range_header.strip())
            if not m:
                raise HTTPException(416, "Invalid Range")
            start = int(m.group(1))
            if start >= size and size > 0:
                raise HTTPException(416, "Range out of bounds")
        status_code = 206 if start > 0 else 200
        headers = {"Accept-Ranges": "bytes", "Content-Length": str(max(0, size - start)),
                   "Content-Disposition": f"attachment; filename=\"download.bin\"; filename*=UTF-8''{quote(sanitize_filename(rec.name))}"}
        if status_code == 206:
            headers["Content-Range"] = f"bytes {start}-{size - 1}/{size}"

        async def iterator():
            with source.open("rb") as f:
                f.seek(start)
                while True:
                    chunk = f.read(256 * 1024)
                    if not chunk:
                        break
                    yield chunk
        return StreamingResponse(iterator(), status_code=status_code, headers=headers, media_type="application/octet-stream")

    @app.websocket("/ws")
    async def websocket_endpoint(ws: WebSocket):
        host = ws.client.host if ws.client else ""
        if ctx.config.private_network_only and not is_private_client(host):
            await ws.close(code=1008)
            return
        await ws.accept()
        device_id: str | None = None
        try:
            raw = await asyncio.wait_for(ws.receive_text(), timeout=10)
            msg = json.loads(raw)
            if msg.get("type") != "auth":
                await ws.close(code=1008)
                return
            device_id = str(msg.get("device_id", ""))
            token = str(msg.get("token", ""))
            if not ctx.auth.verify(device_id, token):
                await ws.close(code=1008)
                return
            await ctx.manager.attach(device_id, ws)
            await ws.send_text(json.dumps({"type": "auth_ok", "device_id": device_id}))
            while True:
                raw = await ws.receive_text()
                try:
                    event = json.loads(raw)
                except json.JSONDecodeError:
                    continue
                if event.get("type") == "transfer_progress":
                    tid = str(event.get("transfer_id", ""))
                    done = int(event.get("bytes_done", 0))
                    status = str(event.get("status", "transferring"))
                    error = event.get("error")
                    ctx.store.update_remote_progress(tid, done, status=status, error=str(error) if error else None)
                elif event.get("type") == "ping":
                    await ws.send_text('{"type":"pong"}')
        except (WebSocketDisconnect, asyncio.TimeoutError):
            pass
        except Exception:
            pass
        finally:
            if device_id:
                await ctx.manager.detach(device_id, ws)

    return app
