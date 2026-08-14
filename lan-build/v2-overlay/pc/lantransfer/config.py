from __future__ import annotations

import json
from dataclasses import dataclass, asdict
from pathlib import Path


@dataclass(slots=True)
class AppConfig:
    port: int = 8765
    discovery_port: int = 8766
    bind_host: str = "0.0.0.0"
    receive_dir: str = "~/Downloads/LanTransfer"
    pin_enabled: bool = True
    private_network_only: bool = True
    chunk_size: int = 1024 * 1024
    tls_certfile: str | None = None
    tls_keyfile: str | None = None

    @property
    def tls_enabled(self) -> bool:
        if not self.tls_certfile or not self.tls_keyfile:
            return False
        return Path(self.tls_certfile).expanduser().exists() and Path(self.tls_keyfile).expanduser().exists()

    @property
    def receive_path(self) -> Path:
        path = Path(self.receive_dir).expanduser().resolve()
        path.mkdir(parents=True, exist_ok=True)
        return path

    @classmethod
    def load(cls, path: Path) -> "AppConfig":
        if not path.exists():
            cfg = cls()
            path.write_text(json.dumps(asdict(cfg), indent=2), encoding="utf-8")
            return cfg
        data = json.loads(path.read_text(encoding="utf-8"))
        allowed = {k: v for k, v in data.items() if k in cls.__dataclass_fields__}
        return cls(**allowed)
