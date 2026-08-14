from pathlib import Path

from fastapi.testclient import TestClient

from lantransfer.api import AppContext, create_app
from lantransfer.config import AppConfig
from lantransfer.connection_manager import ConnectionManager
from lantransfer.security import PairingStore
from lantransfer.transfer_store import TransferStore


def make_client(tmp_path: Path):
    cfg = AppConfig(receive_dir=str(tmp_path / "recv"), private_network_only=False)
    auth = PairingStore(tmp_path / "paired.json")
    store = TransferStore(tmp_path / "state.json", cfg.receive_path)
    manager = ConnectionManager(auth, store)
    app = create_app(AppContext(cfg, "123456", auth, store, manager))
    return TestClient(app), store


def test_pair_upload_and_range_download(tmp_path: Path):
    client, store = make_client(tmp_path)
    with client:
        pair = client.post("/api/pair", json={"device_name": "Phone", "pin": "123456"})
        assert pair.status_code == 200
        device_id = pair.json()["device_id"]
        token = pair.json()["token"]
        headers = {"Authorization": f"Bearer {token}", "X-Device-Id": device_id}

        init = client.post("/api/upload/init", json={"name": "hello.bin", "size": 6}, headers=headers)
        assert init.status_code == 200
        tid = init.json()["transfer_id"]
        r1 = client.put(
            f"/api/upload/{tid}",
            content=b"abc",
            headers={**headers, "Content-Range": "bytes 0-2/6", "Content-Type": "application/octet-stream"},
        )
        assert r1.status_code == 200
        r2 = client.put(
            f"/api/upload/{tid}",
            content=b"def",
            headers={**headers, "Content-Range": "bytes 3-5/6", "Content-Type": "application/octet-stream"},
        )
        assert r2.json()["status"] == "done"

        source = tmp_path / "source.bin"
        source.write_bytes(b"0123456789")
        rec = store.register_download(device_id, source)
        dl = client.get(f"/api/download/{rec.id}", headers={**headers, "Range": "bytes=4-"})
        assert dl.status_code == 206
        assert dl.content == b"456789"
        assert dl.headers["content-range"] == "bytes 4-9/10"
