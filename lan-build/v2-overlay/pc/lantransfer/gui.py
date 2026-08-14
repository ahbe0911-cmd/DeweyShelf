from __future__ import annotations

import os
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

from .config import AppConfig
from .connection_manager import ConnectionManager
from .transfer_store import TransferStore
from .utils import get_lan_ipv4s, human_bytes, human_eta

NAVY = "#101936"
NAVY_2 = "#172448"
BLUE = "#526BF4"
CORAL = "#FF5B65"
MUTED = "#97A3C6"
WHITE = "#FFFFFF"
CARD = "#1A2850"
GREEN = "#4DD6A8"


class DesktopGui:
    """Zapya-inspired (not cloned) Windows UI focused on one-tap LAN transfer."""

    def __init__(self, root: tk.Tk, config: AppConfig, pin: str, store: TransferStore,
                 manager: ConnectionManager, device_name: str = "Windows PC"):
        self.root = root
        self.config = config
        self.pin = pin
        self.store = store
        self.manager = manager
        self.device_name = device_name
        self.current_dir = Path.home()
        self.device_map: dict[str, str] = {}
        self.root.title("LAN File Transfer 2")
        self.root.geometry("1080x720")
        self.root.minsize(900, 620)
        self.root.configure(bg=NAVY)
        self._configure_styles()
        self._build()
        self._refresh_files()
        self._poll()

    def _configure_styles(self) -> None:
        style = ttk.Style(self.root)
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass
        style.configure("Dark.Treeview", background=NAVY_2, fieldbackground=NAVY_2,
                        foreground=WHITE, rowheight=28, borderwidth=0)
        style.map("Dark.Treeview", background=[("selected", BLUE)])
        style.configure("Dark.Treeview.Heading", background=CARD, foreground=WHITE,
                        relief="flat", padding=6)
        style.configure("Dark.TCombobox", fieldbackground=NAVY_2, background=NAVY_2,
                        foreground=WHITE, arrowcolor=WHITE)

    def _pill_button(self, parent, text: str, command, accent: bool = True, width: int = 16):
        return tk.Button(parent, text=text, command=command, bd=0, relief="flat",
                         bg=CORAL if accent else BLUE, fg=WHITE,
                         activebackground="#ff737b" if accent else "#6d82f6",
                         activeforeground=WHITE, font=("Segoe UI", 10, "bold"),
                         cursor="hand2", padx=14, pady=9, width=width)

    def _build(self) -> None:
        header = tk.Frame(self.root, bg=BLUE, height=64)
        header.pack(fill=tk.X)
        header.pack_propagate(False)
        tk.Label(header, text="LAN FILE TRANSFER", bg=BLUE, fg=WHITE,
                 font=("Segoe UI", 16, "bold")).pack(side=tk.LEFT, padx=20)
        tk.Label(header, text=self.device_name, bg=BLUE, fg="#E8ECFF",
                 font=("Segoe UI", 10)).pack(side=tk.RIGHT, padx=20)

        body = tk.Frame(self.root, bg=NAVY)
        body.pack(fill=tk.BOTH, expand=True, padx=18, pady=16)

        left = tk.Frame(body, bg=NAVY, width=310)
        left.pack(side=tk.LEFT, fill=tk.Y, padx=(0, 14))
        left.pack_propagate(False)

        # Connection/radar card.
        radar_card = tk.Frame(left, bg=CARD, highlightthickness=0)
        radar_card.pack(fill=tk.X)
        tk.Label(radar_card, text="اتصال سریع", bg=CARD, fg=WHITE,
                 font=("Segoe UI", 14, "bold")).pack(pady=(16, 2))
        tk.Label(radar_card, text="گوشی و کامپیوتر روی یک Wi‑Fi", bg=CARD, fg=MUTED,
                 font=("Segoe UI", 9)).pack()

        self.radar = tk.Canvas(radar_card, width=180, height=180, bg=CARD, highlightthickness=0)
        self.radar.pack(pady=4)
        self.radar.create_oval(20, 20, 160, 160, outline=BLUE, width=3, tags="ring")
        self.radar.create_oval(45, 45, 135, 135, fill=NAVY_2, outline="#6D80FF", width=2)
        self.radar.create_text(90, 78, text="Wi‑Fi", fill=WHITE, font=("Segoe UI", 16, "bold"))
        self.radar.create_text(90, 106, text="READY", fill=GREEN, font=("Segoe UI", 9, "bold"))

        self.connection_state = tk.Label(radar_card, text="منتظر اتصال گوشی...", bg=CARD,
                                         fg=MUTED, font=("Segoe UI", 10, "bold"))
        self.connection_state.pack(pady=(0, 12))

        info = tk.Frame(radar_card, bg=NAVY_2)
        info.pack(fill=tk.X, padx=12, pady=(0, 12))
        ips = get_lan_ipv4s()
        ip_text = ips[0] if ips else "IP پیدا نشد"
        tk.Label(info, text=f"IP  {ip_text}:{self.config.port}", bg=NAVY_2, fg=WHITE,
                 font=("Consolas", 10, "bold")).pack(anchor="w", padx=12, pady=(10, 2))
        tk.Label(info, text=f"PIN  {self.pin if self.config.pin_enabled else 'OFF'}",
                 bg=NAVY_2, fg=CORAL, font=("Segoe UI", 15, "bold")).pack(anchor="w", padx=12, pady=(0, 2))
        tk.Label(info, text=f"Radar UDP  {self.config.discovery_port}", bg=NAVY_2, fg=MUTED,
                 font=("Segoe UI", 8)).pack(anchor="w", padx=12, pady=(0, 10))

        device_card = tk.Frame(left, bg=CARD)
        device_card.pack(fill=tk.X, pady=(12, 0))
        tk.Label(device_card, text="دستگاه متصل", bg=CARD, fg=WHITE,
                 font=("Segoe UI", 11, "bold")).pack(anchor="w", padx=12, pady=(12, 5))
        self.devices = ttk.Combobox(device_card, state="readonly", style="Dark.TCombobox")
        self.devices.pack(fill=tk.X, padx=12, pady=(0, 12))

        self._pill_button(left, "ارسال فایل به گوشی", self._choose_and_send, True, 22).pack(fill=tk.X, pady=(12, 6))
        self._pill_button(left, "پوشه فایل‌های دریافتی", self._open_receive_dir, False, 22).pack(fill=tk.X)

        right = tk.Frame(body, bg=NAVY)
        right.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        top_cards = tk.Frame(right, bg=NAVY)
        top_cards.pack(fill=tk.X)
        self.stat_connected = self._stat_card(top_cards, "وضعیت", "آماده", GREEN)
        self.stat_connected.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 8))
        self.stat_transfers = self._stat_card(top_cards, "انتقال‌ها", "0", CORAL)
        self.stat_transfers.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(8, 0))

        notebook = ttk.Notebook(right)
        notebook.pack(fill=tk.BOTH, expand=True, pady=(14, 0))

        browser = tk.Frame(notebook, bg=NAVY_2)
        transfers = tk.Frame(notebook, bg=NAVY_2)
        notebook.add(browser, text="  فایل‌های PC  ")
        notebook.add(transfers, text="  انتقال‌ها  ")

        browser_top = tk.Frame(browser, bg=NAVY_2)
        browser_top.pack(fill=tk.X, padx=10, pady=10)
        self.path_var = tk.StringVar(value=str(self.current_dir))
        self._pill_button(browser_top, "بالا", self._go_up, False, 6).pack(side=tk.LEFT)
        path_entry = tk.Entry(browser_top, textvariable=self.path_var, bd=0, bg=CARD, fg=WHITE,
                              insertbackground=WHITE, font=("Segoe UI", 10))
        path_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=8, ipady=9)
        self._pill_button(browser_top, "رفتن", self._go_path, False, 6).pack(side=tk.LEFT)
        self._pill_button(browser_top, "ارسال انتخاب‌شده", self._send_selected_tree_files, True, 15).pack(side=tk.LEFT, padx=(8, 0))

        self.file_tree = ttk.Treeview(browser, columns=("type", "size"), show="tree headings",
                                      selectmode="extended", style="Dark.Treeview")
        self.file_tree.heading("#0", text="نام")
        self.file_tree.heading("type", text="نوع")
        self.file_tree.heading("size", text="حجم")
        self.file_tree.column("#0", width=520)
        self.file_tree.column("type", width=100, anchor=tk.CENTER)
        self.file_tree.column("size", width=120, anchor=tk.E)
        self.file_tree.pack(fill=tk.BOTH, expand=True, padx=10, pady=(0, 10))
        self.file_tree.bind("<Double-1>", self._tree_double_click)

        cols = ("direction", "file", "progress", "speed", "eta", "status")
        self.transfer_tree = ttk.Treeview(transfers, columns=cols, show="headings", style="Dark.Treeview")
        labels = {"direction": "جهت", "file": "فایل", "progress": "پیشرفت",
                  "speed": "سرعت", "eta": "زمان باقی‌مانده", "status": "وضعیت"}
        widths = {"direction": 115, "file": 310, "progress": 100, "speed": 110, "eta": 120, "status": 130}
        for c in cols:
            self.transfer_tree.heading(c, text=labels[c])
            self.transfer_tree.column(c, width=widths[c], anchor=tk.CENTER if c != "file" else tk.W)
        self.transfer_tree.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)

    def _stat_card(self, parent, title: str, value: str, value_color: str):
        frame = tk.Frame(parent, bg=CARD)
        tk.Label(frame, text=title, bg=CARD, fg=MUTED, font=("Segoe UI", 9)).pack(anchor="w", padx=14, pady=(10, 2))
        label = tk.Label(frame, text=value, bg=CARD, fg=value_color, font=("Segoe UI", 16, "bold"))
        label.pack(anchor="w", padx=14, pady=(0, 10))
        frame.value_label = label  # type: ignore[attr-defined]
        return frame

    def _selected_device_id(self) -> str | None:
        display = self.devices.get()
        for did, text in self.device_map.items():
            if text == display:
                return did
        return None

    def _choose_and_send(self) -> None:
        device_id = self._selected_device_id()
        if not device_id:
            messagebox.showwarning("گوشی متصل نیست", "ابتدا در اپ اندروید دکمه اتصال را بزنید.")
            return
        files = filedialog.askopenfilenames(title="انتخاب یک یا چند فایل")
        self._queue_paths(device_id, [Path(p) for p in files])

    def _queue_paths(self, device_id: str, paths: list[Path]) -> None:
        errors: list[str] = []
        for path in paths:
            if not path.is_file():
                continue
            try:
                rec = self.store.register_download(device_id, path)
                sent = self.manager.offer_download_threadsafe(rec)
                if not sent:
                    rec.status = "queued"
            except Exception as exc:
                errors.append(f"{path.name}: {exc}")
        if errors:
            messagebox.showerror("خطا", "\n".join(errors[:10]))

    def _refresh_files(self) -> None:
        self.path_var.set(str(self.current_dir))
        for item in self.file_tree.get_children():
            self.file_tree.delete(item)
        try:
            entries = sorted(self.current_dir.iterdir(), key=lambda p: (not p.is_dir(), p.name.lower()))
        except OSError as exc:
            messagebox.showerror("خطا", str(exc))
            return
        for p in entries:
            try:
                size = "" if p.is_dir() else human_bytes(p.stat().st_size)
                kind = "پوشه" if p.is_dir() else "فایل"
                self.file_tree.insert("", tk.END, iid=str(p), text=p.name, values=(kind, size))
            except OSError:
                continue

    def _tree_double_click(self, _event=None) -> None:
        sel = self.file_tree.selection()
        if len(sel) == 1:
            p = Path(sel[0])
            if p.is_dir():
                self.current_dir = p
                self._refresh_files()

    def _send_selected_tree_files(self) -> None:
        device_id = self._selected_device_id()
        if not device_id:
            messagebox.showwarning("گوشی متصل نیست", "ابتدا گوشی را وصل کنید.")
            return
        paths = [Path(i) for i in self.file_tree.selection() if Path(i).is_file()]
        self._queue_paths(device_id, paths)

    def _go_up(self) -> None:
        self.current_dir = self.current_dir.parent
        self._refresh_files()

    def _go_path(self) -> None:
        p = Path(self.path_var.get()).expanduser()
        if p.exists() and p.is_dir():
            self.current_dir = p.resolve()
            self._refresh_files()

    def _open_receive_dir(self) -> None:
        path = self.config.receive_path
        if os.name == "nt":
            os.startfile(path)  # type: ignore[attr-defined]
        else:
            import subprocess
            subprocess.Popen(["xdg-open", str(path)])

    def _poll(self) -> None:
        connected = self.manager.connected_devices()
        new_map = {did: f"{name} — {did[:8]}" for did, name in connected}
        old_selection = self._selected_device_id()
        if new_map != self.device_map:
            self.device_map = new_map
            values = list(new_map.values())
            self.devices["values"] = values
            if old_selection in new_map:
                self.devices.set(new_map[old_selection])
            elif values:
                self.devices.current(0)
            else:
                self.devices.set("")

        if connected:
            self.connection_state.config(text=f"متصل: {connected[0][1]}", fg=GREEN)
            self.stat_connected.value_label.config(text="متصل", fg=GREEN)  # type: ignore[attr-defined]
            self.radar.itemconfig("ring", outline=GREEN)
        else:
            self.connection_state.config(text="منتظر اتصال گوشی...", fg=MUTED)
            self.stat_connected.value_label.config(text="آماده", fg=MUTED)  # type: ignore[attr-defined]
            self.radar.itemconfig("ring", outline=BLUE)

        records = self.store.list()
        self.stat_transfers.value_label.config(text=str(len(records)))  # type: ignore[attr-defined]
        present = set()
        for rec in records:
            iid = rec.id
            present.add(iid)
            direction = "PC → Android" if rec.direction == "pc_to_android" else "Android → PC"
            progress = f"{rec.progress_percent:.1f}%"
            speed = f"{human_bytes(rec.speed_bps)}/s" if rec.speed_bps > 0 else "—"
            eta = human_eta(rec.eta_seconds)
            values = (direction, rec.name, progress, speed, eta, rec.status)
            if self.transfer_tree.exists(iid):
                self.transfer_tree.item(iid, values=values)
            else:
                self.transfer_tree.insert("", 0, iid=iid, values=values)
        for iid in self.transfer_tree.get_children():
            if iid not in present:
                self.transfer_tree.delete(iid)

        self.root.after(500, self._poll)
