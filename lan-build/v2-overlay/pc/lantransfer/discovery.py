from __future__ import annotations

import json
import socket
import threading

from .config import AppConfig

DISCOVERY_MAGIC = b"LANFT_DISCOVER_V2"


class DiscoveryService(threading.Thread):
    """UDP responder used for reliable nearby-device discovery on the local Wi-Fi."""

    def __init__(self, config: AppConfig, instance_id: str, device_name: str):
        super().__init__(daemon=True, name="lan-transfer-discovery")
        self.config = config
        self.instance_id = instance_id
        self.device_name = device_name
        self._stop_event = threading.Event()
        self._sock: socket.socket | None = None

    def run(self) -> None:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self._sock = sock
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.bind(("0.0.0.0", self.config.discovery_port))
        except OSError:
            sock.close()
            return
        sock.settimeout(0.5)
        while not self._stop_event.is_set():
            try:
                data, addr = sock.recvfrom(2048)
            except socket.timeout:
                continue
            except OSError:
                break
            if data.strip() != DISCOVERY_MAGIC:
                continue
            payload = {
                "service": "LAN File Transfer",
                "version": "2.0.0",
                "instance_id": self.instance_id,
                "device_name": self.device_name,
                "port": self.config.port,
                "scheme": "https" if self.config.tls_enabled else "http",
            }
            try:
                sock.sendto(json.dumps(payload, ensure_ascii=False).encode("utf-8"), addr)
            except OSError:
                continue

    def stop(self) -> None:
        self._stop_event.set()
        if self._sock is not None:
            try:
                self._sock.close()
            except OSError:
                pass
