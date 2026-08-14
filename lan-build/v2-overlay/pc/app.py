from __future__ import annotations

import json
import os
import socket
import sys
import tkinter as tk
import uuid
from pathlib import Path

from lantransfer.api import AppContext, create_app
from lantransfer.config import AppConfig
from lantransfer.connection_manager import ConnectionManager
from lantransfer.discovery import DiscoveryService
from lantransfer.gui import DesktopGui
from lantransfer.security import PairingStore, generate_pin
from lantransfer.server import ServerThread
from lantransfer.transfer_store import TransferStore


def _load_instance_id(base: Path) -> str:
    path = base / "instance.json"
    try:
        if path.exists():
            value = json.loads(path.read_text(encoding="utf-8")).get("instance_id")
            if value:
                return str(value)
    except Exception:
        pass
    value = str(uuid.uuid4())
    path.write_text(json.dumps({"instance_id": value}, indent=2), encoding="utf-8")
    return value


def main() -> None:
    if getattr(sys, "frozen", False) and os.name == "nt":
        appdata = Path(os.environ.get("LOCALAPPDATA", str(Path.home())))
        base = appdata / "LANFileTransfer"
        base.mkdir(parents=True, exist_ok=True)
    else:
        base = Path(__file__).resolve().parent

    config = AppConfig.load(base / "config.json")
    pin = generate_pin()
    instance_id = _load_instance_id(base)
    device_name = socket.gethostname() or "Windows PC"
    auth = PairingStore(base / "paired_devices.json")
    store = TransferStore(base / "transfer_state.json", config.receive_path)
    manager = ConnectionManager(auth, store)
    app = create_app(AppContext(config=config, pin=pin, auth=auth, store=store, manager=manager,
                                instance_id=instance_id, device_name=device_name))

    server = ServerThread(app, config)
    discovery = DiscoveryService(config, instance_id, device_name)
    server.start()
    discovery.start()

    root = tk.Tk()
    DesktopGui(root, config, pin, store, manager, device_name=device_name)

    def close() -> None:
        discovery.stop()
        server.stop()
        root.destroy()

    root.protocol("WM_DELETE_WINDOW", close)
    root.mainloop()


if __name__ == "__main__":
    main()
