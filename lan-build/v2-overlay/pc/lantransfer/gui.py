from __future__ import annotations

import ctypes
import os
import sys
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

from .config import AppConfig
from .connection_manager import ConnectionManager
from .transfer_store import TransferStore
from .utils import get_lan_ipv4s, human_bytes, human_eta

BLUE = "#1688F5"
BLUE_DARK = "#0C73E3"
BLUE_LIGHT = "#EAF4FF"
BG = "#F4F6FA"
WHITE = "#FFFFFF"
TEXT = "#202633"
MUTED = "#7D8796"
BORDER = "#E1E6EE"
GREEN = "#22B981"
RED = "#EE5253"
SOFT = "#F8FAFD"


class DesktopGui:
    """Clean SHAREit-inspired Windows UI. Networking/transfer behavior is unchanged."""

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

        self.root.title("LAN SHARE")
        self.root.geometry("1180x760")
        self.root.minsize(980, 650)
        self.root.configure(bg=BG)
        self._set_window_identity()
        self._configure_styles()
        self._build()
        self._refresh_files()
        self._poll()

    @staticmethod
    def _resource_path(relative: str) -> str:
        if hasattr(sys, "_MEIPASS"):
            return str(Path(getattr(sys, "_MEIPASS")) / relative)
        return str(Path(__file__).resolve().parents[1] / relative)

    def _set_window_identity(self) -> None:
        if os.name == "nt":
            try:
                ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID("LANShare.Transfer.2.1")
            except Exception:
                pass
        try:
            self.root.iconbitmap(self._resource_path("assets/lan_share.ico"))
        except Exception:
            pass

    def _configure_styles(self) -> None:
        style = ttk.Style(self.root)
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass

        style.configure(
            "Clean.Treeview",
            background=WHITE,
            fieldbackground=WHITE,
            foreground=TEXT,
            rowheight=34,
            borderwidth=0,
            font=("Segoe UI", 10),
        )
        style.map(
            "Clean.Treeview",
            background=[("selected", "#DCEEFF")],
            foreground=[("selected", TEXT)],
        )
        style.configure(
            "Clean.Treeview.Heading",
            background="#F2F5F9",
            foreground="#596579",
            relief="flat",
            padding=(8, 9),
            font=("Segoe UI", 9, "bold"),
        )
        style.configure(
            "Clean.TCombobox",
            fieldbackground=WHITE,
            background=WHITE,
            foreground=TEXT,
            arrowcolor=BLUE,
            padding=7,
        )

    def _button(self, parent, text: str, command, primary: bool = True, width: int = 14):
        return tk.Button(
            parent,
            text=text,
            command=command,
            bd=0,
            relief="flat",
            bg=BLUE if primary else WHITE,
            fg=WHITE if primary else BLUE,
            activebackground=BLUE_DARK if primary else BLUE_LIGHT,
            activeforeground=WHITE if primary else BLUE,
            font=("Segoe UI", 10, "bold"),
            cursor="hand2",
            padx=14,
            pady=9,
            width=width,
            highlightthickness=1 if not primary else 0,
            highlightbackground=BORDER,
        )

    def _build(self) -> None:
        # Blue hero header similar to the supplied desktop reference.
        header = tk.Frame(self.root, bg=BLUE, height=178)
        header.pack(fill=tk.X)
        header.pack_propagate(False)

        top = tk.Frame(header, bg=BLUE)
        top.pack(fill=tk.X, padx=28, pady=(20, 10))

        logo = tk.Canvas(top, width=46, height=46, bg=BLUE, highlightthickness=0)
        logo.pack(side=tk.LEFT)
        logo.create_oval(2, 2, 44, 44, outline=WHITE, width=2)
        logo.create_rectangle(10, 13, 27, 27, outline=WHITE, width=2)
        logo.create_rectangle(30, 20, 39, 35, outline=WHITE, width=2)
        logo.create_line(24, 23, 31, 27, fill=WHITE, width=2, arrow=tk.LAST)
        logo.create_line(31, 31, 24, 28, fill=WHITE, width=2, arrow=tk.LAST)

        brand = tk.Frame(top, bg=BLUE)
        brand.pack(side=tk.LEFT, padx=(10, 0))
        tk.Label(brand, text="LAN SHARE", bg=BLUE, fg=WHITE,
                 font=("Segoe UI", 19, "bold")).pack(anchor="w")
        tk.Label(brand, text="Local Wi‑Fi File Transfer  •  v2.1",
                 bg=BLUE, fg="#D8ECFF", font=("Segoe UI", 9)).pack(anchor="w")

        receive_btn = self._button(top, "پوشه دریافتی‌ها", self._open_receive_dir, False, 15)
        receive_btn.pack(side=tk.RIGHT, padx=(10, 0))
        receive_btn.configure(bg="#2A96F7", fg=WHITE, activebackground="#52A8F8",
                              highlightthickness=0)

        identity = tk.Frame(header, bg=BLUE)
        identity.pack(fill=tk.X, padx=28, pady=(4, 0))

        avatar = tk.Canvas(identity, width=58, height=58, bg=BLUE, highlightthickness=0)
        avatar.pack(side=tk.LEFT)
        avatar.create_oval(3, 3, 55, 55, fill="#EAF4FF", outline="#FFFFFF", width=2)
        avatar.create_rectangle(17, 18, 41, 36, outline=BLUE, width=2)
        avatar.create_rectangle(25, 37, 33, 41, fill=BLUE, outline=BLUE)
        avatar.create_line(21, 42, 37, 42, fill=BLUE, width=2)

        ident_text = tk.Frame(identity, bg=BLUE)
        ident_text.pack(side=tk.LEFT, padx=(12, 0))
        tk.Label(ident_text, text=self.device_name, bg=BLUE, fg=WHITE,
                 font=("Segoe UI", 15, "bold")).pack(anchor="w")

        ips = get_lan_ipv4s()
        ip_text = ips[0] if ips else "IP پیدا نشد"
        self.ip_label = tk.Label(
            ident_text,
            text=f"{ip_text}:{self.config.port}   •   Radar UDP {self.config.discovery_port}",
            bg=BLUE, fg="#D8ECFF", font=("Consolas", 9)
        )
        self.ip_label.pack(anchor="w", pady=(2, 0))

        status_box = tk.Frame(identity, bg="#2A96F7")
        status_box.pack(side=tk.RIGHT)
        self.status_dot = tk.Canvas(status_box, width=18, height=18, bg="#2A96F7", highlightthickness=0)
        self.status_dot.pack(side=tk.LEFT, padx=(12, 2), pady=10)
        self.status_circle = self.status_dot.create_oval(5, 5, 13, 13, fill="#CFE7FF", outline="")
        self.connection_state = tk.Label(
            status_box, text="منتظر اتصال گوشی", bg="#2A96F7", fg=WHITE,
            font=("Segoe UI", 10, "bold")
        )
        self.connection_state.pack(side=tk.LEFT, padx=(2, 14), pady=10)

        pin_box = tk.Frame(identity, bg="#2A96F7")
        pin_box.pack(side=tk.RIGHT, padx=(0, 10))
        tk.Label(pin_box, text="PIN", bg="#2A96F7", fg="#D8ECFF",
                 font=("Segoe UI", 8, "bold")).pack(side=tk.LEFT, padx=(12, 5), pady=10)
        tk.Label(pin_box, text=self.pin if self.config.pin_enabled else "OFF",
                 bg="#2A96F7", fg=WHITE, font=("Segoe UI", 13, "bold")).pack(
                     side=tk.LEFT, padx=(0, 12), pady=8)

        body = tk.Frame(self.root, bg=BG)
        body.pack(fill=tk.BOTH, expand=True, padx=24, pady=20)

        left = tk.Frame(body, bg=WHITE, highlightthickness=1, highlightbackground=BORDER)
        left.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=(0, 10))

        right = tk.Frame(body, bg=WHITE, width=410, highlightthickness=1, highlightbackground=BORDER)
        right.pack(side=tk.LEFT, fill=tk.BOTH, padx=(10, 0))
        right.pack_propagate(False)

        # Left: Send to phone + file browser.
        left_header = tk.Frame(left, bg=WHITE)
        left_header.pack(fill=tk.X, padx=18, pady=(16, 10))
        tk.Label(left_header, text="ارسال به گوشی", bg=WHITE, fg=TEXT,
                 font=("Segoe UI", 14, "bold")).pack(side=tk.LEFT)

        self.devices = ttk.Combobox(left_header, state="readonly", style="Clean.TCombobox", width=28)
        self.devices.pack(side=tk.RIGHT)

        shortcuts = tk.Frame(left, bg=WHITE)
        shortcuts.pack(fill=tk.X, padx=18, pady=(2, 12))
        self._shortcut(shortcuts, "▧", "انتخاب فایل", "یک یا چند فایل", self._choose_and_send).pack(
            side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 6))
        self._shortcut(shortcuts, "▣", "فایل‌های PC", "مرور پوشه‌ها", lambda: None).pack(
            side=tk.LEFT, fill=tk.X, expand=True, padx=6)
        self._shortcut(shortcuts, "⇩", "دریافتی‌ها", "پوشه ذخیره", self._open_receive_dir).pack(
            side=tk.LEFT, fill=tk.X, expand=True, padx=(6, 0))

        drop = tk.Canvas(left, height=104, bg="#F7FBFF", highlightthickness=0, cursor="hand2")
        drop.pack(fill=tk.X, padx=18, pady=(0, 14))
        drop.bind("<Button-1>", lambda _e: self._choose_and_send())
        drop.bind("<Configure>", self._draw_select_area)

        browser_top = tk.Frame(left, bg=WHITE)
        browser_top.pack(fill=tk.X, padx=18, pady=(0, 10))
        self.path_var = tk.StringVar(value=str(self.current_dir))
        self._button(browser_top, "↑", self._go_up, False, 3).pack(side=tk.LEFT)
        path_entry = tk.Entry(
            browser_top, textvariable=self.path_var, bd=0, bg=SOFT, fg=TEXT,
            insertbackground=TEXT, font=("Segoe UI", 9), highlightthickness=1,
            highlightbackground=BORDER, highlightcolor=BLUE
        )
        path_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=8, ipady=9)
        self._button(browser_top, "رفتن", self._go_path, False, 6).pack(side=tk.LEFT)
        self._button(browser_top, "ارسال انتخاب‌شده", self._send_selected_tree_files, True, 14).pack(
            side=tk.LEFT, padx=(8, 0))

        self.file_tree = ttk.Treeview(
            left, columns=("type", "size"), show="tree headings",
            selectmode="extended", style="Clean.Treeview"
        )
        self.file_tree.heading("#0", text="نام")
        self.file_tree.heading("type", text="نوع")
        self.file_tree.heading("size", text="حجم")
        self.file_tree.column("#0", width=430)
        self.file_tree.column("type", width=100, anchor=tk.CENTER)
        self.file_tree.column("size", width=110, anchor=tk.E)
        self.file_tree.pack(fill=tk.BOTH, expand=True, padx=18, pady=(0, 18))
        self.file_tree.bind("<Double-1>", self._tree_double_click)

        # Right: transfer history.
        history_header = tk.Frame(right, bg=WHITE)
        history_header.pack(fill=tk.X, padx=18, pady=(18, 12))
        tk.Label(history_header, text="History", bg=WHITE, fg=TEXT,
                 font=("Segoe UI", 14, "bold")).pack(side=tk.LEFT)
        self.transfer_count_label = tk.Label(
            history_header, text="0 انتقال", bg=BLUE_LIGHT, fg=BLUE,
            font=("Segoe UI", 9, "bold"), padx=10, pady=5
        )
        self.transfer_count_label.pack(side=tk.RIGHT)

        tk.Frame(right, bg=BORDER, height=1).pack(fill=tk.X, padx=18)

        cols = ("direction", "file", "progress", "speed", "eta", "status")
        self.transfer_tree = ttk.Treeview(
            right, columns=cols, show="headings", style="Clean.Treeview"
        )
        labels = {
            "direction": "جهت", "file": "فایل", "progress": "٪",
            "speed": "سرعت", "eta": "ETA", "status": "وضعیت"
        }
        widths = {
            "direction": 90, "file": 170, "progress": 62,
            "speed": 86, "eta": 70, "status": 90
        }
        for c in cols:
            self.transfer_tree.heading(c, text=labels[c])
            self.transfer_tree.column(
                c, width=widths[c],
                anchor=tk.W if c == "file" else tk.CENTER,
                stretch=(c == "file")
            )
        self.transfer_tree.pack(fill=tk.BOTH, expand=True, padx=18, pady=14)

        footer = tk.Frame(right, bg="#F8FAFD")
        footer.pack(fill=tk.X, side=tk.BOTTOM)
        tk.Label(
            footer,
            text="فایل‌ها مستقیم داخل شبکه Wi‑Fi منتقل می‌شوند.",
            bg="#F8FAFD", fg=MUTED, font=("Segoe UI", 8)
        ).pack(pady=10)

    def _shortcut(self, parent, symbol: str, title: str, subtitle: str, command):
        box = tk.Frame(parent, bg=SOFT, highlightthickness=1, highlightbackground=BORDER, cursor="hand2")
        for widget in (box,):
            widget.bind("<Button-1>", lambda _e: command())

        icon = tk.Label(box, text=symbol, bg=SOFT, fg=BLUE, font=("Segoe UI Symbol", 22, "bold"))
        icon.pack(pady=(12, 2))
        icon.bind("<Button-1>", lambda _e: command())

        title_label = tk.Label(box, text=title, bg=SOFT, fg=TEXT, font=("Segoe UI", 10, "bold"))
        title_label.pack()
        title_label.bind("<Button-1>", lambda _e: command())

        sub_label = tk.Label(box, text=subtitle, bg=SOFT, fg=MUTED, font=("Segoe UI", 8))
        sub_label.pack(pady=(2, 10))
        sub_label.bind("<Button-1>", lambda _e: command())
        return box

    def _draw_select_area(self, event) -> None:
        canvas = event.widget
        canvas.delete("all")
        w, h = max(event.width, 20), max(event.height, 20)
        canvas.create_rectangle(5, 5, w - 5, h - 5, outline="#A8CFF5", width=2, dash=(5, 4))
        canvas.create_text(
            w // 2, h // 2 - 10,
            text="＋  برای انتخاب و ارسال فایل کلیک کنید",
            fill=BLUE, font=("Segoe UI", 11, "bold")
        )
        canvas.create_text(
            w // 2, h // 2 + 18,
            text="ارسال چند فایل همزمان پشتیبانی می‌شود",
            fill=MUTED, font=("Segoe UI", 8)
        )

    def _selected_device_id(self) -> str | None:
        display = self.devices.get()
        for did, text in self.device_map.items():
            if text == display:
                return did
        return None

    def _choose_and_send(self) -> None:
        device_id = self._selected_device_id()
        if not device_id:
            messagebox.showwarning("گوشی متصل نیست", "ابتدا در اپ اندروید به این کامپیوتر متصل شوید.")
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
            self.connection_state.config(text=f"متصل به {connected[0][1]}")
            self.status_dot.itemconfig(self.status_circle, fill=GREEN)
        else:
            self.connection_state.config(text="منتظر اتصال گوشی")
            self.status_dot.itemconfig(self.status_circle, fill="#CFE7FF")

        records = self.store.list()
        self.transfer_count_label.config(text=f"{len(records)} انتقال")
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
