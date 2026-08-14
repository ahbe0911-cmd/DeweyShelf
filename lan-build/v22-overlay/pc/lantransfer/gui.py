from __future__ import annotations

import ctypes
import os
import sys
import tkinter as tk
from datetime import datetime
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

from .config import AppConfig
from .connection_manager import ConnectionManager
from .transfer_store import TransferStore
from .utils import get_lan_ipv4s, human_bytes, human_eta
from . import theme


PAGE_CONNECT = "connect"
PAGE_SEND = "send"
PAGE_TRANSFERS = "transfers"
PAGE_INBOX = "inbox"
PAGE_SETTINGS = "settings"


class DesktopGui:
    """Desktop-first, RTL-aware LAN SHARE interface.

    Networking and transfer services are intentionally kept outside this class.
    This class only owns presentation and direct user interaction.
    """

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
        self.current_page = PAGE_CONNECT
        self.pages: dict[str, tk.Frame] = {}
        self.nav_items: dict[str, tuple[tk.Frame, tk.Label, tk.Label]] = {}

        self.root.title("LAN SHARE")
        self.root.geometry("1220x780")
        self.root.minsize(980, 650)
        self.root.configure(bg=theme.BACKGROUND)
        self._set_window_identity()
        self._configure_styles()
        self._build_shell()
        self._bind_shortcuts()
        self._show_page(PAGE_CONNECT)
        self._poll()

    @staticmethod
    def _resource_path(relative: str) -> str:
        if hasattr(sys, "_MEIPASS"):
            return str(Path(getattr(sys, "_MEIPASS")) / relative)
        return str(Path(__file__).resolve().parents[1] / relative)

    def _set_window_identity(self) -> None:
        if os.name == "nt":
            try:
                ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID("LANShare.Transfer.2.2")
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
            "App.Treeview",
            background=theme.SURFACE,
            fieldbackground=theme.SURFACE,
            foreground=theme.TEXT_PRIMARY,
            rowheight=36,
            borderwidth=0,
            font=(theme.FONT_FAMILY, 10),
        )
        style.map(
            "App.Treeview",
            background=[("selected", theme.PRIMARY_CONTAINER)],
            foreground=[("selected", theme.TEXT_PRIMARY)],
        )
        style.configure(
            "App.Treeview.Heading",
            background=theme.SURFACE_VARIANT,
            foreground=theme.TEXT_SECONDARY,
            relief="flat",
            padding=(8, 10),
            font=(theme.FONT_FAMILY, 9, "bold"),
        )
        style.configure(
            "App.TCombobox",
            fieldbackground=theme.SURFACE,
            background=theme.SURFACE,
            foreground=theme.TEXT_PRIMARY,
            arrowcolor=theme.PRIMARY,
            padding=8,
        )
        style.map("App.TCombobox", fieldbackground=[("readonly", theme.SURFACE)])

    def _build_shell(self) -> None:
        shell = tk.Frame(self.root, bg=theme.BACKGROUND)
        shell.pack(fill=tk.BOTH, expand=True)

        self.sidebar = tk.Frame(shell, bg=theme.SIDEBAR, width=210,
                                highlightthickness=1, highlightbackground=theme.BORDER)
        self.sidebar.pack(side=tk.RIGHT, fill=tk.Y)
        self.sidebar.pack_propagate(False)
        self._build_sidebar()

        workspace = tk.Frame(shell, bg=theme.BACKGROUND)
        workspace.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        self.topbar = tk.Frame(workspace, bg=theme.SURFACE, height=64,
                               highlightthickness=1, highlightbackground=theme.BORDER)
        self.topbar.pack(fill=tk.X)
        self.topbar.pack_propagate(False)
        self._build_topbar()

        self.page_host = tk.Frame(workspace, bg=theme.BACKGROUND)
        self.page_host.pack(fill=tk.BOTH, expand=True, padx=24, pady=22)
        self._build_pages()

    def _build_sidebar(self) -> None:
        brand = tk.Frame(self.sidebar, bg=theme.SIDEBAR)
        brand.pack(fill=tk.X, padx=18, pady=(22, 26))
        mark = tk.Canvas(brand, width=36, height=36, bg=theme.SIDEBAR, highlightthickness=0)
        mark.pack(side=tk.RIGHT)
        mark.create_rectangle(4, 7, 19, 20, outline=theme.PRIMARY, width=2)
        mark.create_rectangle(22, 13, 32, 28, outline=theme.PRIMARY, width=2)
        mark.create_line(18, 15, 24, 18, fill=theme.PRIMARY, width=2, arrow=tk.LAST)
        mark.create_line(23, 23, 17, 20, fill=theme.PRIMARY, width=2, arrow=tk.LAST)

        text_box = tk.Frame(brand, bg=theme.SIDEBAR)
        text_box.pack(side=tk.RIGHT, padx=(0, 10))
        tk.Label(text_box, text="LAN SHARE", bg=theme.SIDEBAR, fg=theme.TEXT_PRIMARY,
                 font=(theme.FONT_FAMILY, 16, "bold")).pack(anchor="e")
        tk.Label(text_box, text="انتقال محلی فایل", bg=theme.SIDEBAR, fg=theme.TEXT_SECONDARY,
                 font=(theme.FONT_FAMILY, 9)).pack(anchor="e")

        nav = tk.Frame(self.sidebar, bg=theme.SIDEBAR)
        nav.pack(fill=tk.X, padx=10)
        self._add_nav(nav, PAGE_CONNECT, "\uE71B", "اتصال")
        self._add_nav(nav, PAGE_SEND, "\uE724", "ارسال فایل")
        self._add_nav(nav, PAGE_TRANSFERS, "\uE823", "انتقال‌ها")
        self._add_nav(nav, PAGE_INBOX, "\uE8B7", "دریافتی‌ها")

        bottom = tk.Frame(self.sidebar, bg=theme.SIDEBAR)
        bottom.pack(fill=tk.X, side=tk.BOTTOM, padx=10, pady=14)
        self._add_nav(bottom, PAGE_SETTINGS, "\uE713", "تنظیمات")

        version = tk.Label(self.sidebar, text="LAN SHARE 2.2", bg=theme.SIDEBAR,
                           fg=theme.TEXT_DISABLED, font=(theme.FONT_FAMILY, 8))
        version.pack(side=tk.BOTTOM, pady=(0, 8))

    def _add_nav(self, parent: tk.Widget, page: str, glyph: str, label: str) -> None:
        frame = tk.Frame(parent, bg=theme.SIDEBAR, cursor="hand2", height=44)
        frame.pack(fill=tk.X, pady=2)
        frame.pack_propagate(False)

        icon = tk.Label(frame, text=glyph, bg=theme.SIDEBAR, fg=theme.TEXT_SECONDARY,
                        font=(theme.ICON_FONT, 14), width=3)
        icon.pack(side=tk.RIGHT, padx=(5, 7))
        text = tk.Label(frame, text=label, bg=theme.SIDEBAR, fg=theme.TEXT_SECONDARY,
                        font=(theme.FONT_FAMILY, 10), anchor="e")
        text.pack(side=tk.RIGHT, fill=tk.X, expand=True)

        for widget in (frame, icon, text):
            widget.bind("<Button-1>", lambda _e, p=page: self._show_page(p))
            widget.bind("<Enter>", lambda _e, p=page: self._nav_hover(p, True))
            widget.bind("<Leave>", lambda _e, p=page: self._nav_hover(p, False))
        self.nav_items[page] = (frame, icon, text)

    def _nav_hover(self, page: str, entered: bool) -> None:
        if page == self.current_page:
            return
        frame, icon, text = self.nav_items[page]
        bg = theme.SURFACE_VARIANT if entered else theme.SIDEBAR
        for widget in (frame, icon, text):
            widget.configure(bg=bg)

    def _build_topbar(self) -> None:
        self.page_title = tk.Label(self.topbar, text="", bg=theme.SURFACE,
                                   fg=theme.TEXT_PRIMARY, font=(theme.FONT_FAMILY, 15, "bold"))
        self.page_title.pack(side=tk.RIGHT, padx=(10, 24))

        self.connection_pill = tk.Frame(self.topbar, bg=theme.SURFACE_VARIANT)
        self.connection_pill.pack(side=tk.LEFT, padx=18, pady=14)
        self.status_dot = tk.Canvas(self.connection_pill, width=18, height=18,
                                    bg=theme.SURFACE_VARIANT, highlightthickness=0)
        self.status_dot.pack(side=tk.LEFT, padx=(10, 2))
        self.status_circle = self.status_dot.create_oval(5, 5, 13, 13,
                                                        fill=theme.TEXT_DISABLED, outline="")
        self.connection_state = tk.Label(self.connection_pill, text="منتظر گوشی",
                                         bg=theme.SURFACE_VARIANT, fg=theme.TEXT_SECONDARY,
                                         font=(theme.FONT_FAMILY, 9, "bold"))
        self.connection_state.pack(side=tk.LEFT, padx=(2, 10))

        self.device_top = tk.Label(self.topbar, text=self.device_name, bg=theme.SURFACE,
                                   fg=theme.TEXT_SECONDARY, font=(theme.FONT_FAMILY, 9))
        self.device_top.pack(side=tk.LEFT, padx=(0, 8))

    def _build_pages(self) -> None:
        self.pages[PAGE_CONNECT] = self._build_connect_page()
        self.pages[PAGE_SEND] = self._build_send_page()
        self.pages[PAGE_TRANSFERS] = self._build_transfers_page()
        self.pages[PAGE_INBOX] = self._build_inbox_page()
        self.pages[PAGE_SETTINGS] = self._build_settings_page()
        for page in self.pages.values():
            page.place(relx=0, rely=0, relwidth=1, relheight=1)

    def _page(self) -> tk.Frame:
        return tk.Frame(self.page_host, bg=theme.BACKGROUND)

    def _section_title(self, parent, title: str, subtitle: str | None = None):
        box = tk.Frame(parent, bg=theme.BACKGROUND)
        tk.Label(box, text=title, bg=theme.BACKGROUND, fg=theme.TEXT_PRIMARY,
                 font=(theme.FONT_FAMILY, 20, "bold"), anchor="e").pack(fill=tk.X)
        if subtitle:
            tk.Label(box, text=subtitle, bg=theme.BACKGROUND, fg=theme.TEXT_SECONDARY,
                     font=(theme.FONT_FAMILY, 9), anchor="e").pack(fill=tk.X, pady=(3, 0))
        return box

    def _surface(self, parent) -> tk.Frame:
        return tk.Frame(parent, bg=theme.SURFACE, highlightthickness=1,
                        highlightbackground=theme.BORDER)

    def _button(self, parent, text: str, command, primary: bool = True,
                danger: bool = False, width: int = 14) -> tk.Button:
        if danger:
            bg, fg, active = theme.SURFACE, theme.ERROR, "#FFF0F1"
            border = theme.ERROR
        elif primary:
            bg, fg, active = theme.PRIMARY, "#FFFFFF", theme.PRIMARY_HOVER
            border = theme.PRIMARY
        else:
            bg, fg, active = theme.SURFACE, theme.PRIMARY, theme.PRIMARY_CONTAINER
            border = theme.BORDER
        return tk.Button(
            parent, text=text, command=command, bd=0, relief="flat",
            bg=bg, fg=fg, activebackground=active, activeforeground=fg,
            font=(theme.FONT_FAMILY, 9, "bold"), cursor="hand2",
            padx=14, pady=9, width=width, highlightthickness=1,
            highlightbackground=border,
        )

    def _build_connect_page(self) -> tk.Frame:
        page = self._page()
        self._section_title(page, "اتصال به گوشی",
                            "گوشی و کامپیوتر را به یک Wi‑Fi وصل کنید؛ اینترنت لازم نیست.").pack(fill=tk.X, pady=(0, 18))

        grid = tk.Frame(page, bg=theme.BACKGROUND)
        grid.pack(fill=tk.BOTH, expand=True)

        info = self._surface(grid)
        info.pack(side=tk.RIGHT, fill=tk.BOTH, expand=True, padx=(10, 0))
        tk.Label(info, text="آماده اتصال", bg=theme.SURFACE, fg=theme.TEXT_PRIMARY,
                 font=(theme.FONT_FAMILY, 15, "bold")).pack(anchor="e", padx=22, pady=(22, 4))
        tk.Label(info, text="اپ اندروید LAN SHARE را باز کنید و Radar را بزنید.",
                 bg=theme.SURFACE, fg=theme.TEXT_SECONDARY,
                 font=(theme.FONT_FAMILY, 9)).pack(anchor="e", padx=22)

        radar = tk.Canvas(info, width=270, height=220, bg=theme.SURFACE, highlightthickness=0)
        radar.pack(pady=18)
        self._draw_radar(radar)

        self.connect_device_label = tk.Label(info, text="هنوز گوشی متصل نیست",
                                             bg=theme.SURFACE, fg=theme.TEXT_SECONDARY,
                                             font=(theme.FONT_FAMILY, 10, "bold"))
        self.connect_device_label.pack(pady=(0, 20))

        details = self._surface(grid)
        details.pack(side=tk.LEFT, fill=tk.Y, padx=(0, 10))
        details.configure(width=340)
        details.pack_propagate(False)
        tk.Label(details, text="اطلاعات اتصال", bg=theme.SURFACE, fg=theme.TEXT_PRIMARY,
                 font=(theme.FONT_FAMILY, 14, "bold")).pack(anchor="e", padx=20, pady=(22, 16))

        ips = get_lan_ipv4s()
        ip_text = ips[0] if ips else "IP پیدا نشد"
        self._detail_row(details, "IP کامپیوتر", f"{ip_text}:{self.config.port}")
        self._detail_row(details, "PIN", self.pin if self.config.pin_enabled else "خاموش")
        self._detail_row(details, "Radar", f"UDP {self.config.discovery_port}")
        self._detail_row(details, "حالت", "شبکه محلی")

        tk.Frame(details, bg=theme.BORDER, height=1).pack(fill=tk.X, padx=20, pady=18)
        tk.Label(details, text="نکته امنیتی", bg=theme.SURFACE, fg=theme.TEXT_PRIMARY,
                 font=(theme.FONT_FAMILY, 10, "bold")).pack(anchor="e", padx=20)
        tk.Label(details, text="PIN فقط برای Pair اولیه است. فایل‌ها مستقیماً داخل LAN منتقل می‌شوند.",
                 wraplength=290, justify="right", bg=theme.SURFACE, fg=theme.TEXT_SECONDARY,
                 font=(theme.FONT_FAMILY, 9)).pack(anchor="e", padx=20, pady=(6, 0))
        return page

    def _draw_radar(self, canvas: tk.Canvas) -> None:
        cx, cy = 135, 105
        for r in (35, 65, 95):
            canvas.create_oval(cx-r, cy-r, cx+r, cy+r, outline="#C9DDF6", width=1)
        canvas.create_oval(cx-5, cy-5, cx+5, cy+5, fill=theme.PRIMARY, outline="")
        canvas.create_line(cx, cy, cx+70, cy-48, fill=theme.PRIMARY, width=2)
        canvas.create_text(cx, 208, text="در حال انتظار برای اتصال گوشی", fill=theme.TEXT_SECONDARY,
                           font=(theme.FONT_FAMILY, 9))

    def _detail_row(self, parent, label: str, value: str) -> None:
        row = tk.Frame(parent, bg=theme.SURFACE)
        row.pack(fill=tk.X, padx=20, pady=6)
        tk.Label(row, text=value, bg=theme.SURFACE, fg=theme.TEXT_PRIMARY,
                 font=(theme.FONT_FAMILY, 9, "bold")).pack(side=tk.LEFT)
        tk.Label(row, text=label, bg=theme.SURFACE, fg=theme.TEXT_SECONDARY,
                 font=(theme.FONT_FAMILY, 9)).pack(side=tk.RIGHT)

    def _build_send_page(self) -> tk.Frame:
        page = self._page()
        header = self._section_title(page, "ارسال فایل",
                                     "یک یا چند فایل را انتخاب کنید و مستقیم به گوشی بفرستید.")
        header.pack(fill=tk.X, pady=(0, 14))

        controls = tk.Frame(page, bg=theme.BACKGROUND)
        controls.pack(fill=tk.X, pady=(0, 12))
        self.devices = ttk.Combobox(controls, state="readonly", style="App.TCombobox", width=30)
        self.devices.pack(side=tk.RIGHT)
        self._button(controls, "انتخاب فایل", self._choose_and_send, True, width=12).pack(side=tk.RIGHT, padx=(0, 8))
        self._button(controls, "پوشه دریافتی", self._open_receive_dir, False, width=12).pack(side=tk.LEFT)

        drop = tk.Canvas(page, height=100, bg="#FBFDFF", highlightthickness=1,
                         highlightbackground="#BDD8F5", cursor="hand2")
        drop.pack(fill=tk.X, pady=(0, 12))
        drop.bind("<Button-1>", lambda _e: self._choose_and_send())
        drop.bind("<Configure>", self._draw_drop_zone)

        browser = self._surface(page)
        browser.pack(fill=tk.BOTH, expand=True)
        bar = tk.Frame(browser, bg=theme.SURFACE)
        bar.pack(fill=tk.X, padx=14, pady=12)
        self.path_var = tk.StringVar(value=str(self.current_dir))
        self._button(bar, "بالا", self._go_up, False, width=5).pack(side=tk.RIGHT)
        path_entry = tk.Entry(bar, textvariable=self.path_var, bd=0, bg=theme.SURFACE_VARIANT,
                              fg=theme.TEXT_PRIMARY, insertbackground=theme.TEXT_PRIMARY,
                              font=(theme.FONT_FAMILY, 9), highlightthickness=1,
                              highlightbackground=theme.BORDER, highlightcolor=theme.PRIMARY)
        path_entry.pack(side=tk.RIGHT, fill=tk.X, expand=True, padx=8, ipady=9)
        self._button(bar, "رفتن", self._go_path, False, width=5).pack(side=tk.RIGHT)
        self._button(bar, "ارسال انتخاب‌شده", self._send_selected_tree_files, True, width=14).pack(side=tk.LEFT)

        self.file_tree = ttk.Treeview(browser, columns=("type", "size"), show="tree headings",
                                      selectmode="extended", style="App.Treeview")
        self.file_tree.heading("#0", text="نام")
        self.file_tree.heading("type", text="نوع")
        self.file_tree.heading("size", text="حجم")
        self.file_tree.column("#0", width=520)
        self.file_tree.column("type", width=110, anchor=tk.CENTER)
        self.file_tree.column("size", width=120, anchor=tk.E)
        self.file_tree.pack(fill=tk.BOTH, expand=True, padx=14, pady=(0, 14))
        self.file_tree.bind("<Double-1>", self._tree_double_click)
        self.file_tree.bind("<Button-3>", self._file_context_menu)
        self._refresh_files()
        return page

    def _draw_drop_zone(self, event) -> None:
        canvas = event.widget
        canvas.delete("all")
        w, h = max(event.width, 50), max(event.height, 50)
        canvas.create_text(w//2, h//2-10, text="برای انتخاب فایل کلیک کنید",
                           fill=theme.TEXT_PRIMARY, font=(theme.FONT_FAMILY, 11, "bold"))
        canvas.create_text(w//2, h//2+16, text="ارسال چند فایل همزمان پشتیبانی می‌شود",
                           fill=theme.TEXT_SECONDARY, font=(theme.FONT_FAMILY, 8))

    def _build_transfers_page(self) -> tk.Frame:
        page = self._page()
        top = self._section_title(page, "انتقال‌ها",
                                  "وضعیت، سرعت و زمان باقی‌مانده ارسال و دریافت فایل‌ها")
        top.pack(fill=tk.X, pady=(0, 14))

        surface = self._surface(page)
        surface.pack(fill=tk.BOTH, expand=True)
        cols = ("direction", "file", "progress", "speed", "eta", "status")
        self.transfer_tree = ttk.Treeview(surface, columns=cols, show="headings", style="App.Treeview")
        labels = {"direction": "جهت", "file": "فایل", "progress": "پیشرفت",
                  "speed": "سرعت", "eta": "زمان باقی‌مانده", "status": "وضعیت"}
        widths = {"direction": 130, "file": 390, "progress": 100,
                  "speed": 120, "eta": 130, "status": 140}
        for c in cols:
            self.transfer_tree.heading(c, text=labels[c])
            self.transfer_tree.column(c, width=widths[c], anchor=tk.W if c == "file" else tk.CENTER,
                                      stretch=(c == "file"))
        self.transfer_tree.pack(fill=tk.BOTH, expand=True, padx=14, pady=14)
        return page

    def _build_inbox_page(self) -> tk.Frame:
        page = self._page()
        title = self._section_title(page, "دریافتی‌ها",
                                    "فایل‌هایی که از گوشی دریافت شده‌اند")
        title.pack(fill=tk.X, pady=(0, 14))

        actions = tk.Frame(page, bg=theme.BACKGROUND)
        actions.pack(fill=tk.X, pady=(0, 10))
        self._button(actions, "باز کردن پوشه", self._open_receive_dir, True, width=12).pack(side=tk.RIGHT)
        self._button(actions, "تازه‌سازی", self._refresh_inbox, False, width=10).pack(side=tk.RIGHT, padx=(0, 8))

        surface = self._surface(page)
        surface.pack(fill=tk.BOTH, expand=True)
        self.inbox_tree = ttk.Treeview(surface, columns=("type", "size", "modified"),
                                       show="tree headings", style="App.Treeview")
        self.inbox_tree.heading("#0", text="نام")
        self.inbox_tree.heading("type", text="نوع")
        self.inbox_tree.heading("size", text="حجم")
        self.inbox_tree.heading("modified", text="آخرین تغییر")
        self.inbox_tree.column("#0", width=520)
        self.inbox_tree.column("type", width=120, anchor=tk.CENTER)
        self.inbox_tree.column("size", width=120, anchor=tk.E)
        self.inbox_tree.column("modified", width=170, anchor=tk.CENTER)
        self.inbox_tree.pack(fill=tk.BOTH, expand=True, padx=14, pady=14)
        self.inbox_tree.bind("<Double-1>", lambda _e: self._open_inbox_selection())
        return page

    def _build_settings_page(self) -> tk.Frame:
        page = self._page()
        self._section_title(page, "تنظیمات",
                            "اطلاعات شبکه و مسیرهای برنامه").pack(fill=tk.X, pady=(0, 14))
        surface = self._surface(page)
        surface.pack(fill=tk.X)
        surface.configure(height=250)
        surface.pack_propagate(False)
        tk.Label(surface, text="LAN SHARE 2.2", bg=theme.SURFACE, fg=theme.TEXT_PRIMARY,
                 font=(theme.FONT_FAMILY, 15, "bold")).pack(anchor="e", padx=22, pady=(22, 16))
        self._detail_row(surface, "TCP", str(self.config.port))
        self._detail_row(surface, "UDP Radar", str(self.config.discovery_port))
        self._detail_row(surface, "پوشه دریافتی", str(self.config.receive_path))
        self._detail_row(surface, "PIN", "فعال" if self.config.pin_enabled else "خاموش")

        actions = tk.Frame(page, bg=theme.BACKGROUND)
        actions.pack(fill=tk.X, pady=12)
        self._button(actions, "باز کردن پوشه دریافتی", self._open_receive_dir, True, width=18).pack(side=tk.RIGHT)
        return page

    def _show_page(self, page: str) -> None:
        self.current_page = page
        titles = {
            PAGE_CONNECT: "اتصال",
            PAGE_SEND: "ارسال فایل",
            PAGE_TRANSFERS: "انتقال‌ها",
            PAGE_INBOX: "دریافتی‌ها",
            PAGE_SETTINGS: "تنظیمات",
        }
        self.page_title.configure(text=titles[page])
        self.pages[page].tkraise()
        for key, (frame, icon, text) in self.nav_items.items():
            selected = key == page
            bg = theme.SIDEBAR_ACTIVE if selected else theme.SIDEBAR
            fg = theme.PRIMARY if selected else theme.TEXT_SECONDARY
            for widget in (frame, icon, text):
                widget.configure(bg=bg)
            icon.configure(fg=fg)
            text.configure(fg=fg, font=(theme.FONT_FAMILY, 10, "bold" if selected else "normal"))
        if page == PAGE_SEND:
            self._refresh_files()
        elif page == PAGE_INBOX:
            self._refresh_inbox()

    def _selected_device_id(self) -> str | None:
        display = self.devices.get() if hasattr(self, "devices") else ""
        for did, text in self.device_map.items():
            if text == display:
                return did
        return None

    def _choose_and_send(self) -> None:
        device_id = self._selected_device_id()
        if not device_id:
            messagebox.showwarning("گوشی متصل نیست", "ابتدا گوشی را به LAN SHARE متصل کنید.")
            self._show_page(PAGE_CONNECT)
            return
        files = filedialog.askopenfilenames(title="انتخاب یک یا چند فایل")
        self._queue_paths(device_id, [Path(p) for p in files])
        if files:
            self._show_page(PAGE_TRANSFERS)

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
            messagebox.showerror("خطا در ارسال", "\n".join(errors[:10]))

    def _refresh_files(self) -> None:
        if not hasattr(self, "file_tree"):
            return
        self.path_var.set(str(self.current_dir))
        for item in self.file_tree.get_children():
            self.file_tree.delete(item)
        try:
            entries = sorted(self.current_dir.iterdir(), key=lambda p: (not p.is_dir(), p.name.lower()))
        except OSError as exc:
            messagebox.showerror("خطا", str(exc))
            return
        for path in entries:
            try:
                size = "" if path.is_dir() else human_bytes(path.stat().st_size)
                kind = "پوشه" if path.is_dir() else (path.suffix.lower().lstrip(".") or "فایل")
                self.file_tree.insert("", tk.END, iid=str(path), text=path.name, values=(kind, size))
            except OSError:
                continue

    def _tree_double_click(self, _event=None) -> None:
        sel = self.file_tree.selection()
        if len(sel) != 1:
            return
        path = Path(sel[0])
        if path.is_dir():
            self.current_dir = path
            self._refresh_files()
        elif path.is_file():
            self._open_path(path)

    def _file_context_menu(self, event) -> None:
        row = self.file_tree.identify_row(event.y)
        if row:
            if row not in self.file_tree.selection():
                self.file_tree.selection_set(row)
            menu = tk.Menu(self.root, tearoff=False, font=(theme.FONT_FAMILY, 9))
            menu.add_command(label="ارسال انتخاب‌شده", command=self._send_selected_tree_files)
            menu.add_command(label="باز کردن", command=self._open_selected_file)
            menu.add_separator()
            menu.add_command(label="کپی مسیر", command=self._copy_selected_path)
            menu.tk_popup(event.x_root, event.y_root)

    def _open_selected_file(self) -> None:
        sel = self.file_tree.selection()
        if not sel:
            return
        self._open_path(Path(sel[0]))

    def _copy_selected_path(self) -> None:
        sel = self.file_tree.selection()
        if not sel:
            return
        self.root.clipboard_clear()
        self.root.clipboard_append(str(sel[0]))

    def _send_selected_tree_files(self) -> None:
        device_id = self._selected_device_id()
        if not device_id:
            messagebox.showwarning("گوشی متصل نیست", "ابتدا گوشی را وصل کنید.")
            return
        paths = [Path(i) for i in self.file_tree.selection() if Path(i).is_file()]
        if not paths:
            messagebox.showinfo("انتخاب فایل", "حداقل یک فایل را انتخاب کنید.")
            return
        self._queue_paths(device_id, paths)
        self._show_page(PAGE_TRANSFERS)

    def _go_up(self) -> None:
        self.current_dir = self.current_dir.parent
        self._refresh_files()

    def _go_path(self) -> None:
        path = Path(self.path_var.get()).expanduser()
        if path.exists() and path.is_dir():
            self.current_dir = path.resolve()
            self._refresh_files()

    def _refresh_inbox(self) -> None:
        if not hasattr(self, "inbox_tree"):
            return
        for item in self.inbox_tree.get_children():
            self.inbox_tree.delete(item)
        folder = self.config.receive_path
        try:
            folder.mkdir(parents=True, exist_ok=True)
            entries = sorted(folder.iterdir(), key=lambda p: p.stat().st_mtime, reverse=True)
        except OSError:
            entries = []
        for path in entries:
            if not path.is_file():
                continue
            try:
                stat = path.stat()
                kind = path.suffix.lower().lstrip(".") or "فایل"
                modified = datetime.fromtimestamp(stat.st_mtime).strftime("%Y-%m-%d  %H:%M")
                self.inbox_tree.insert("", tk.END, iid=str(path), text=path.name,
                                       values=(kind, human_bytes(stat.st_size), modified))
            except OSError:
                continue

    def _open_inbox_selection(self) -> None:
        sel = self.inbox_tree.selection()
        if sel:
            self._open_path(Path(sel[0]))

    def _open_receive_dir(self) -> None:
        self.config.receive_path.mkdir(parents=True, exist_ok=True)
        self._open_path(self.config.receive_path)

    def _open_path(self, path: Path) -> None:
        try:
            if os.name == "nt":
                os.startfile(path)  # type: ignore[attr-defined]
            else:
                import subprocess
                subprocess.Popen(["xdg-open", str(path)])
        except OSError as exc:
            messagebox.showerror("باز کردن فایل", str(exc))

    def _bind_shortcuts(self) -> None:
        self.root.bind("<Control-o>", lambda _e: self._choose_and_send())
        self.root.bind("<F5>", lambda _e: self._refresh_current_page())
        self.root.bind("<Control-1>", lambda _e: self._show_page(PAGE_CONNECT))
        self.root.bind("<Control-2>", lambda _e: self._show_page(PAGE_SEND))
        self.root.bind("<Control-3>", lambda _e: self._show_page(PAGE_TRANSFERS))
        self.root.bind("<Control-4>", lambda _e: self._show_page(PAGE_INBOX))

    def _refresh_current_page(self) -> None:
        if self.current_page == PAGE_SEND:
            self._refresh_files()
        elif self.current_page == PAGE_INBOX:
            self._refresh_inbox()

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
            name = connected[0][1]
            self.connection_state.configure(text=f"متصل: {name}", fg=theme.SUCCESS)
            self.status_dot.itemconfig(self.status_circle, fill=theme.SUCCESS)
            self.connect_device_label.configure(text=f"متصل به {name}", fg=theme.SUCCESS)
        else:
            self.connection_state.configure(text="منتظر گوشی", fg=theme.TEXT_SECONDARY)
            self.status_dot.itemconfig(self.status_circle, fill=theme.TEXT_DISABLED)
            self.connect_device_label.configure(text="هنوز گوشی متصل نیست", fg=theme.TEXT_SECONDARY)

        records = self.store.list()
        if hasattr(self, "transfer_tree"):
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
