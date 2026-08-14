import json
import os
import queue
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import tkinter as tk
import tkinter.font as tkfont
from tkinter import filedialog, messagebox, ttk

import customtkinter as ctk
from PIL import Image, ImageOps

from converter import ConvertOptions, SUPPORTED_EXTENSIONS, convert_image_to_jpg, human_size

APP_NAME = "HEIC Pro Converter"
VERSION = "5.0 Studio"

# Commercial dark palette inspired by classic desktop media tools.
BG = "#171717"
TITLEBAR = "#1E1E1E"
TOOLBAR = "#242424"
PANEL = "#202020"
PANEL_2 = "#292929"
PANEL_3 = "#303030"
BORDER = "#3A3A3A"
TEXT = "#F2F2F2"
MUTED = "#A7A7A7"
DIM = "#777777"
ACCENT = "#F5A000"
ACCENT_HOVER = "#FFB21A"
ACCENT_DARK = "#B76F00"
BLUE = "#31A7D8"
GREEN = "#65B741"
RED = "#E05252"


class ConverterApp:
    def __init__(self, root):
        self.root = root
        self.root.title("HEIC Pro Converter — Studio")
        self.root.geometry("1260x800")
        self.root.minsize(1040, 680)
        self.root.configure(fg_color=BG)

        self.files = []
        self.row_ids = []
        self.events = queue.Queue()
        self.cancel_event = threading.Event()
        self.worker = None
        self.busy = False
        self.completed = 0
        self.success = 0
        self.failed = 0
        self.input_total = 0
        self.output_total = 0
        self.batch_started = 0.0
        self.preview_image = None
        self.current_preview_index = None

        self.settings_path = self._settings_path()
        self.settings = self._load_settings()
        default_output = str(Path.home() / "Pictures" / "HEIC_Converted")
        self.output_dir = Path(self.settings.get("output_dir", default_output))

        cpu_count = os.cpu_count() or 2
        suggested_workers = min(4, max(2, cpu_count // 2))
        self.target_var = tk.StringVar(value=str(self.settings.get("target_kb", 488)))
        self.workers_var = tk.StringVar(value=str(self.settings.get("workers", suggested_workers)))
        self.preserve_exif_var = tk.BooleanVar(value=bool(self.settings.get("preserve_exif", True)))
        self.preserve_icc_var = tk.BooleanVar(value=bool(self.settings.get("preserve_icc", True)))
        self.output_var = tk.StringVar(value=str(self.output_dir))
        self.progress_text_var = tk.StringVar(value="آماده")
        self.progress_percent_var = tk.StringVar(value="۰٪")
        self.queue_stat_var = tk.StringVar(value="۰ فایل")
        self.preview_name_var = tk.StringVar(value="هنوز عکسی انتخاب نشده")
        self.preview_meta_var = tk.StringVar(value="HEIC / HEIF / JPG / JPEG")
        self.preview_status_var = tk.StringVar(value="برای شروع، فایل یا پوشه اضافه کنید")

        self.body_font_family, self.title_font_family = self._detect_fonts()
        self.font_body = ctk.CTkFont(family=self.body_font_family, size=15)
        self.font_small = ctk.CTkFont(family=self.body_font_family, size=13)
        self.font_body_bold = ctk.CTkFont(family=self.body_font_family, size=15, weight="bold")
        self.font_title = ctk.CTkFont(family=self.title_font_family, size=24)
        self.font_heading = ctk.CTkFont(family=self.title_font_family, size=19)
        self.font_toolbar = ctk.CTkFont(family=self.body_font_family, size=14, weight="bold")

        self._configure_tree_style()
        self._build_ui()
        self._set_controls()
        self._update_stats()

        self.root.protocol("WM_DELETE_WINDOW", self._on_close)
        self.root.after(80, self._poll_events)

        if len(sys.argv) > 1:
            self.root.after(250, lambda: self.add_paths(sys.argv[1:]))

    # ---------- Settings / fonts ----------
    def _settings_path(self):
        base = os.environ.get("APPDATA") or str(Path.home())
        folder = Path(base) / "HEICProConverter"
        try:
            folder.mkdir(parents=True, exist_ok=True)
            return folder / "settings.json"
        except OSError:
            return Path.home() / ".heic_pro_converter_settings.json"

    def _load_settings(self):
        try:
            with open(str(self.settings_path), "r", encoding="utf-8") as handle:
                data = json.load(handle)
                return data if isinstance(data, dict) else {}
        except Exception:
            return {}

    def _save_settings(self):
        data = {
            "output_dir": str(self.output_dir),
            "target_kb": self._target_kb(silent=True),
            "workers": self._workers(silent=True),
            "preserve_exif": bool(self.preserve_exif_var.get()),
            "preserve_icc": bool(self.preserve_icc_var.get()),
        }
        try:
            self.settings_path.parent.mkdir(parents=True, exist_ok=True)
            with open(str(self.settings_path), "w", encoding="utf-8") as handle:
                json.dump(data, handle, ensure_ascii=False, indent=2)
        except Exception:
            pass

    def _detect_fonts(self):
        try:
            families = {name.lower(): name for name in tkfont.families(self.root)}
        except Exception:
            families = {}

        body_candidates = ["Far.Nazanin", "Farsi Nazanin", "B Nazanin", "Nazanin", "Tahoma"]
        title_candidates = ["Far.Titr", "Farsi Titr Bold", "B Titr", "Titr", "Tahoma"]

        def choose(candidates, fallback):
            for name in candidates:
                actual = families.get(name.lower())
                if actual:
                    return actual
            return fallback

        return choose(body_candidates, "Tahoma"), choose(title_candidates, "Tahoma")

    # ---------- Styling ----------
    def _configure_tree_style(self):
        style = ttk.Style(self.root)
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass

        style.configure(
            "Studio.Treeview",
            rowheight=36,
            background="#242424",
            fieldbackground="#242424",
            foreground="#E8E8E8",
            borderwidth=0,
            relief="flat",
            font=(self.body_font_family, 11),
        )
        style.configure(
            "Studio.Treeview.Heading",
            background="#303030",
            foreground="#F5A000",
            borderwidth=0,
            relief="flat",
            padding=(7, 9),
            font=(self.title_font_family, 11),
        )
        style.map(
            "Studio.Treeview",
            background=[("selected", "#3A321F")],
            foreground=[("selected", "#FFFFFF")],
        )
        style.map("Studio.Treeview.Heading", background=[("active", "#383838")])
        style.configure(
            "Studio.Vertical.TScrollbar",
            background="#424242",
            troughcolor="#202020",
            bordercolor="#202020",
            arrowcolor="#A7A7A7",
        )

    # ---------- UI ----------
    def _build_ui(self):
        self.root.grid_columnconfigure(0, weight=1)
        self.root.grid_rowconfigure(0, weight=1)

        shell = ctk.CTkFrame(self.root, fg_color=BG, corner_radius=0)
        shell.grid(row=0, column=0, sticky="nsew")
        shell.grid_columnconfigure(0, weight=1)
        shell.grid_rowconfigure(3, weight=1)

        self._build_titlebar(shell)
        self._build_menubar(shell)
        self._build_toolbar(shell)
        self._build_workspace(shell)
        self._build_bottom_controls(shell)

    def _build_titlebar(self, parent):
        bar = ctk.CTkFrame(parent, height=48, fg_color=TITLEBAR, corner_radius=0)
        bar.grid(row=0, column=0, sticky="ew")
        bar.grid_columnconfigure(1, weight=1)

        logo = ctk.CTkLabel(
            bar,
            text="◉",
            text_color=ACCENT,
            font=ctk.CTkFont(family="Segoe UI", size=25, weight="bold"),
            width=45,
        )
        logo.grid(row=0, column=0, padx=(14, 4))

        title_wrap = ctk.CTkFrame(bar, fg_color="transparent")
        title_wrap.grid(row=0, column=1, sticky="w")
        ctk.CTkLabel(
            title_wrap,
            text="HEIC Pro Converter",
            text_color=TEXT,
            font=ctk.CTkFont(family="Segoe UI", size=17, weight="bold"),
            anchor="w",
        ).pack(side="left")
        ctk.CTkLabel(
            title_wrap,
            text="  STUDIO V5",
            text_color=ACCENT,
            font=ctk.CTkFont(family="Segoe UI", size=10, weight="bold"),
        ).pack(side="left", padx=(8, 0))

        status = ctk.CTkLabel(
            bar,
            textvariable=self.queue_stat_var,
            text_color=MUTED,
            font=self.font_small,
        )
        status.grid(row=0, column=2, padx=18)

    def _build_menubar(self, parent):
        menu = ctk.CTkFrame(parent, height=34, fg_color="#1B1B1B", corner_radius=0)
        menu.grid(row=1, column=0, sticky="ew")

        items = [
            ("فایل", self.pick_files),
            ("پوشه", self.pick_folder),
            ("خروجی", self.pick_output),
            ("تنظیمات", self._show_preferences),
            ("راهنما", self._show_help),
        ]
        for text, command in items:
            ctk.CTkButton(
                menu,
                text=text,
                command=command,
                width=78,
                height=30,
                fg_color="transparent",
                hover_color="#303030",
                text_color="#D0D0D0",
                font=self.font_small,
                corner_radius=3,
            ).pack(side="left", padx=(8 if text == "فایل" else 0, 0))

    def _tool_button(self, parent, symbol, title, command):
        frame = ctk.CTkFrame(parent, fg_color="transparent", width=108, height=86)
        frame.pack_propagate(False)
        btn = ctk.CTkButton(
            frame,
            text=symbol,
            command=command,
            width=62,
            height=46,
            corner_radius=10,
            fg_color="#2D2D2D",
            hover_color="#3A3A3A",
            border_width=1,
            border_color="#414141",
            text_color=ACCENT,
            font=ctk.CTkFont(family="Segoe UI Symbol", size=26, weight="bold"),
        )
        btn.pack(pady=(5, 1))
        ctk.CTkLabel(frame, text=title, text_color="#E0E0E0", font=self.font_small).pack()
        return frame, btn

    def _build_toolbar(self, parent):
        toolbar = ctk.CTkFrame(parent, height=96, fg_color=TOOLBAR, corner_radius=0)
        toolbar.grid(row=2, column=0, sticky="ew")
        toolbar.grid_columnconfigure(6, weight=1)

        tools = [
            ("⊕", "افزودن فایل", self.pick_files, "add_files_btn"),
            ("▣", "افزودن پوشه", self.pick_folder, "add_folder_btn"),
            ("✂", "حذف انتخاب", self.remove_selected, "remove_btn"),
            ("⌫", "پاکسازی", self.clear_files, "clear_btn"),
            ("⚙", "تنظیمات", self._show_preferences, "prefs_btn"),
        ]
        for i, (symbol, title, command, attr) in enumerate(tools):
            wrap, btn = self._tool_button(toolbar, symbol, title, command)
            wrap.grid(row=0, column=i, padx=(12 if i == 0 else 2, 2), pady=5)
            setattr(self, attr, btn)

        info = ctk.CTkFrame(toolbar, fg_color="#202020", corner_radius=10, border_width=1, border_color=BORDER)
        info.grid(row=0, column=6, sticky="e", padx=16, pady=14)
        ctk.CTkLabel(info, text="موتور Turbo", text_color=ACCENT, font=self.font_body_bold).pack(anchor="e", padx=14, pady=(9, 0))
        ctk.CTkLabel(info, text="پردازش موازی + کنترل دقیق ۴۸۸KB", text_color=MUTED, font=self.font_small).pack(anchor="e", padx=14, pady=(0, 9))

    def _build_workspace(self, parent):
        workspace = ctk.CTkFrame(parent, fg_color=BG, corner_radius=0)
        workspace.grid(row=3, column=0, sticky="nsew", padx=14, pady=12)
        workspace.grid_columnconfigure(0, minsize=355)
        workspace.grid_columnconfigure(1, weight=1)
        workspace.grid_rowconfigure(0, weight=1)

        self._build_preview_panel(workspace)
        self._build_queue_panel(workspace)

    def _build_preview_panel(self, parent):
        panel = ctk.CTkFrame(parent, fg_color=PANEL, corner_radius=8, border_width=1, border_color=BORDER)
        panel.grid(row=0, column=0, sticky="nsew", padx=(0, 10))
        panel.grid_columnconfigure(0, weight=1)
        panel.grid_rowconfigure(1, weight=1)

        head = ctk.CTkFrame(panel, fg_color=PANEL_2, corner_radius=0, height=45)
        head.grid(row=0, column=0, sticky="ew")
        ctk.CTkLabel(head, text="پیش‌نمایش", text_color=TEXT, font=self.font_heading, anchor="e").pack(side="right", padx=14)

        self.preview_box = ctk.CTkFrame(panel, fg_color="#111111", corner_radius=5, border_width=1, border_color="#383838")
        self.preview_box.grid(row=1, column=0, sticky="nsew", padx=12, pady=12)
        self.preview_box.grid_columnconfigure(0, weight=1)
        self.preview_box.grid_rowconfigure(0, weight=1)

        self.preview_label = ctk.CTkLabel(
            self.preview_box,
            text="◉\n\nپیش‌نمایش تصویر",
            text_color="#5C5C5C",
            font=self.font_heading,
            justify="center",
        )
        self.preview_label.grid(row=0, column=0, sticky="nsew", padx=10, pady=10)

        meta = ctk.CTkFrame(panel, fg_color=PANEL_2, corner_radius=6)
        meta.grid(row=2, column=0, sticky="ew", padx=12, pady=(0, 12))
        ctk.CTkLabel(meta, textvariable=self.preview_name_var, text_color=TEXT, font=self.font_body_bold, anchor="e").pack(fill="x", padx=12, pady=(10, 1))
        ctk.CTkLabel(meta, textvariable=self.preview_meta_var, text_color=MUTED, font=self.font_small, anchor="e").pack(fill="x", padx=12)
        ctk.CTkLabel(meta, textvariable=self.preview_status_var, text_color=ACCENT, font=self.font_small, anchor="e").pack(fill="x", padx=12, pady=(1, 10))

    def _build_queue_panel(self, parent):
        panel = ctk.CTkFrame(parent, fg_color=PANEL, corner_radius=8, border_width=1, border_color=BORDER)
        panel.grid(row=0, column=1, sticky="nsew")
        panel.grid_columnconfigure(0, weight=1)
        panel.grid_rowconfigure(2, weight=1)

        head = ctk.CTkFrame(panel, fg_color=PANEL_2, corner_radius=0, height=45)
        head.grid(row=0, column=0, sticky="ew")
        head.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(head, text="صف تبدیل", text_color=TEXT, font=self.font_heading, anchor="e").grid(row=0, column=1, padx=14)
        self.summary_label = ctk.CTkLabel(head, text="آماده", text_color=MUTED, font=self.font_small)
        self.summary_label.grid(row=0, column=0, padx=14, sticky="w")

        self.empty_hint = ctk.CTkFrame(panel, fg_color="#242424", corner_radius=8)
        self.empty_hint.grid(row=1, column=0, sticky="ew", padx=12, pady=(12, 8))
        hint_text = "۱) فایل‌ها را اضافه کنید     ۲) حجم هدف را بررسی کنید     ۳) دکمه تبدیل را بزنید"
        ctk.CTkLabel(self.empty_hint, text=hint_text, text_color="#C8C8C8", font=self.font_body, anchor="center").pack(fill="x", padx=12, pady=11)

        table_frame = ctk.CTkFrame(panel, fg_color="#242424", corner_radius=5)
        table_frame.grid(row=2, column=0, sticky="nsew", padx=12, pady=(0, 12))
        table_frame.grid_columnconfigure(0, weight=1)
        table_frame.grid_rowconfigure(0, weight=1)

        columns = ("type", "input", "output", "quality", "status")
        self.tree = ttk.Treeview(
            table_frame,
            columns=columns,
            show="tree headings",
            selectmode="extended",
            style="Studio.Treeview",
        )
        self.tree.heading("#0", text="نام فایل")
        self.tree.heading("type", text="فرمت")
        self.tree.heading("input", text="حجم اصلی")
        self.tree.heading("output", text="حجم خروجی")
        self.tree.heading("quality", text="کیفیت")
        self.tree.heading("status", text="وضعیت")
        self.tree.column("#0", width=260, minwidth=150, stretch=True, anchor="w")
        self.tree.column("type", width=70, minwidth=60, stretch=False, anchor="center")
        self.tree.column("input", width=100, minwidth=85, stretch=False, anchor="center")
        self.tree.column("output", width=105, minwidth=85, stretch=False, anchor="center")
        self.tree.column("quality", width=75, minwidth=65, stretch=False, anchor="center")
        self.tree.column("status", width=190, minwidth=140, stretch=True, anchor="center")
        self.tree.tag_configure("success", foreground="#7DCC5A")
        self.tree.tag_configure("error", foreground="#FF7373")
        self.tree.tag_configure("running", foreground="#FFC24A")

        scroll = ttk.Scrollbar(table_frame, orient="vertical", command=self.tree.yview, style="Studio.Vertical.TScrollbar")
        self.tree.configure(yscrollcommand=scroll.set)
        self.tree.grid(row=0, column=0, sticky="nsew")
        scroll.grid(row=0, column=1, sticky="ns")
        self.tree.bind("<<TreeviewSelect>>", self._on_tree_select)

    def _build_bottom_controls(self, parent):
        bottom = ctk.CTkFrame(parent, fg_color="#242424", corner_radius=0, height=168)
        bottom.grid(row=4, column=0, sticky="ew")
        bottom.grid_columnconfigure(0, weight=1)

        form = ctk.CTkFrame(bottom, fg_color="transparent")
        form.grid(row=0, column=0, sticky="ew", padx=16, pady=(12, 4))
        form.grid_columnconfigure(1, weight=1)

        # First row: output target and performance.
        top = ctk.CTkFrame(form, fg_color="transparent")
        top.grid(row=0, column=0, columnspan=2, sticky="ew")

        ctk.CTkLabel(top, text="حجم هدف:", text_color=TEXT, font=self.font_body_bold).pack(side="left", padx=(0, 7))
        self.target_entry = ctk.CTkEntry(top, textvariable=self.target_var, width=82, height=34, fg_color="#181818", border_color="#4A4A4A", text_color=TEXT, font=self.font_body, justify="center")
        self.target_entry.pack(side="left")
        ctk.CTkLabel(top, text="KB", text_color=MUTED, font=self.font_small).pack(side="left", padx=(5, 18))

        ctk.CTkLabel(top, text="پردازش هم‌زمان:", text_color=TEXT, font=self.font_body_bold).pack(side="left", padx=(0, 7))
        self.workers_menu = ctk.CTkOptionMenu(top, variable=self.workers_var, values=["1", "2", "3", "4", "5", "6"], width=72, height=34, fg_color="#343434", button_color=ACCENT_DARK, button_hover_color=ACCENT, text_color=TEXT, font=self.font_body)
        self.workers_menu.pack(side="left")

        self.exif_switch = ctk.CTkSwitch(top, text="EXIF", variable=self.preserve_exif_var, progress_color=ACCENT, button_color="#F2F2F2", font=self.font_small, text_color=TEXT)
        self.exif_switch.pack(side="left", padx=(22, 10))
        self.icc_switch = ctk.CTkSwitch(top, text="رنگ ICC", variable=self.preserve_icc_var, progress_color=ACCENT, button_color="#F2F2F2", font=self.font_small, text_color=TEXT)
        self.icc_switch.pack(side="left")

        # Second row: output path.
        pathrow = ctk.CTkFrame(form, fg_color="transparent")
        pathrow.grid(row=1, column=0, columnspan=2, sticky="ew", pady=(9, 0))
        pathrow.grid_columnconfigure(1, weight=1)
        ctk.CTkLabel(pathrow, text="مسیر خروجی:", text_color=TEXT, font=self.font_body_bold).grid(row=0, column=0, padx=(0, 8))
        self.output_entry = ctk.CTkEntry(pathrow, textvariable=self.output_var, height=34, fg_color="#181818", border_color="#4A4A4A", text_color="#CFCFCF", font=self.font_small)
        self.output_entry.grid(row=0, column=1, sticky="ew")
        self.output_entry.configure(state="disabled")
        self.output_btn = ctk.CTkButton(pathrow, text="مرور...", command=self.pick_output, width=92, height=34, fg_color="#3A3A3A", hover_color="#4A4A4A", text_color=TEXT, font=self.font_body)
        self.output_btn.grid(row=0, column=2, padx=(8, 5))
        self.open_output_btn = ctk.CTkButton(pathrow, text="باز کردن پوشه", command=self.open_output, width=120, height=34, fg_color="#3A3A3A", hover_color="#4A4A4A", text_color=TEXT, font=self.font_body)
        self.open_output_btn.grid(row=0, column=3)

        # Progress + main action.
        actionbar = ctk.CTkFrame(bottom, fg_color="#1D1D1D", corner_radius=0)
        actionbar.grid(row=1, column=0, sticky="ew", pady=(8, 0))
        actionbar.grid_columnconfigure(0, weight=1)

        progress_wrap = ctk.CTkFrame(actionbar, fg_color="transparent")
        progress_wrap.grid(row=0, column=0, sticky="ew", padx=(18, 12), pady=12)
        progress_wrap.grid_columnconfigure(0, weight=1)
        self.progress = ctk.CTkProgressBar(progress_wrap, height=12, corner_radius=6, fg_color="#3A3A3A", progress_color=ACCENT)
        self.progress.grid(row=0, column=0, sticky="ew")
        self.progress.set(0)
        ctk.CTkLabel(progress_wrap, textvariable=self.progress_text_var, text_color=MUTED, font=self.font_small).grid(row=1, column=0, sticky="w", pady=(5, 0))
        ctk.CTkLabel(progress_wrap, textvariable=self.progress_percent_var, text_color=ACCENT, font=ctk.CTkFont(family="Segoe UI", size=13, weight="bold")).grid(row=0, column=1, padx=(10, 0))

        self.cancel_btn = ctk.CTkButton(actionbar, text="توقف", command=self.cancel_conversion, width=90, height=42, corner_radius=21, fg_color="#4B2929", hover_color="#623333", text_color="#FFB4B4", font=self.font_body_bold)
        self.cancel_btn.grid(row=0, column=1, padx=(0, 10))

        self.convert_btn = ctk.CTkButton(
            actionbar,
            text="▶\nتبدیل",
            command=self.start_conversion,
            width=94,
            height=72,
            corner_radius=36,
            fg_color=ACCENT,
            hover_color=ACCENT_HOVER,
            text_color="#241700",
            font=ctk.CTkFont(family=self.title_font_family, size=18),
            border_width=3,
            border_color="#FFD073",
        )
        self.convert_btn.grid(row=0, column=2, rowspan=2, padx=(0, 18), pady=6)

    # ---------- Dialogs ----------
    def _show_help(self):
        messagebox.showinfo(
            "راهنما",
            "۱) فایل یا پوشه را اضافه کنید.\n"
            "۲) حجم هدف را روی ۴۸۸KB یا مقدار دلخواه بگذارید.\n"
            "۳) در صورت نیاز مسیر خروجی را تغییر دهید.\n"
            "۴) دکمه تبدیل را بزنید.\n\n"
            "JPGهایی که از قبل زیر حجم هدف هستند بدون افت کیفیت کپی می‌شوند.",
        )

    def _show_preferences(self):
        dialog = ctk.CTkToplevel(self.root)
        dialog.title("تنظیمات")
        dialog.geometry("430x330")
        dialog.resizable(False, False)
        dialog.configure(fg_color=BG)
        dialog.transient(self.root)
        dialog.grab_set()

        card = ctk.CTkFrame(dialog, fg_color=PANEL, corner_radius=10, border_width=1, border_color=BORDER)
        card.pack(fill="both", expand=True, padx=18, pady=18)
        ctk.CTkLabel(card, text="تنظیمات پردازش", text_color=ACCENT, font=self.font_heading, anchor="e").pack(fill="x", padx=18, pady=(18, 12))
        ctk.CTkLabel(card, text="فونت متن: %s" % self.body_font_family, text_color=TEXT, font=self.font_body, anchor="e").pack(fill="x", padx=18, pady=3)
        ctk.CTkLabel(card, text="فونت تیتر: %s" % self.title_font_family, text_color=TEXT, font=self.font_body, anchor="e").pack(fill="x", padx=18, pady=3)
        ctk.CTkLabel(card, text="فرمت خروجی: JPG", text_color=TEXT, font=self.font_body, anchor="e").pack(fill="x", padx=18, pady=3)
        ctk.CTkLabel(card, text="موتور: Turbo Parallel", text_color=TEXT, font=self.font_body, anchor="e").pack(fill="x", padx=18, pady=3)
        ctk.CTkLabel(card, text="نسخه: %s" % VERSION, text_color=MUTED, font=self.font_small, anchor="e").pack(fill="x", padx=18, pady=(3, 15))
        ctk.CTkButton(card, text="بستن", command=dialog.destroy, fg_color=ACCENT, hover_color=ACCENT_HOVER, text_color="#241700", font=self.font_body_bold).pack(pady=(0, 15))

    # ---------- File actions ----------
    def _target_kb(self, silent=False):
        try:
            value = int(str(self.target_var.get()).strip())
            if value < 50 or value > 20000:
                raise ValueError()
            return value
        except Exception:
            if not silent:
                messagebox.showerror("حجم نامعتبر", "حجم خروجی باید بین ۵۰ تا ۲۰۰۰۰ KB باشد.")
            return 488

    def _workers(self, silent=False):
        try:
            value = int(str(self.workers_var.get()).strip())
            if value < 1 or value > 6:
                raise ValueError()
            return value
        except Exception:
            if not silent:
                messagebox.showerror("مقدار نامعتبر", "پردازش هم‌زمان باید بین ۱ تا ۶ فایل باشد.")
            return 3

    def pick_files(self):
        files = filedialog.askopenfilenames(
            title="انتخاب عکس‌ها",
            filetypes=[
                ("تصاویر پشتیبانی‌شده", "*.heic *.heif *.jpg *.jpeg *.HEIC *.HEIF *.JPG *.JPEG"),
                ("HEIC / HEIF", "*.heic *.heif *.HEIC *.HEIF"),
                ("JPG / JPEG", "*.jpg *.jpeg *.JPG *.JPEG"),
                ("همه فایل‌ها", "*.*"),
            ],
        )
        if files:
            self.add_paths(files)

    def pick_folder(self):
        folder = filedialog.askdirectory(title="انتخاب پوشه عکس‌ها")
        if folder:
            self.add_paths([folder])

    def pick_output(self):
        folder = filedialog.askdirectory(title="انتخاب پوشه خروجی", initialdir=str(self.output_dir))
        if folder:
            self.output_dir = Path(folder)
            self.output_var.set(str(self.output_dir))
            self._save_settings()

    def open_output(self):
        try:
            self.output_dir.mkdir(parents=True, exist_ok=True)
            if os.name == "nt":
                os.startfile(str(self.output_dir))
            else:
                import subprocess
                subprocess.Popen(["xdg-open", str(self.output_dir)])
        except Exception as exc:
            messagebox.showerror("خطا", "پوشه خروجی باز نشد:\n%s" % exc)

    def add_paths(self, paths):
        if self.busy:
            return

        collected = []
        for raw in paths:
            try:
                p = Path(raw)
                if p.is_dir():
                    for item in p.rglob("*"):
                        try:
                            if item.is_file() and item.suffix.lower() in SUPPORTED_EXTENSIONS:
                                collected.append(item)
                        except OSError:
                            pass
                elif p.is_file() and p.suffix.lower() in SUPPORTED_EXTENSIONS:
                    collected.append(p)
            except Exception:
                pass

        existing = set()
        for p in self.files:
            try:
                existing.add(str(p.resolve()).lower())
            except Exception:
                existing.add(str(p).lower())

        first_new_index = None
        for p in collected:
            try:
                key = str(p.resolve()).lower()
            except Exception:
                key = str(p).lower()
            if key in existing:
                continue
            existing.add(key)
            if first_new_index is None:
                first_new_index = len(self.files)
            self.files.append(p)
            try:
                size_text = human_size(p.stat().st_size)
            except OSError:
                size_text = "—"
            iid = self.tree.insert(
                "",
                "end",
                text=p.name,
                values=(p.suffix.upper().lstrip("."), size_text, "—", "—", "آماده"),
            )
            self.row_ids.append(iid)

        if first_new_index is not None:
            self.tree.selection_set(self.row_ids[first_new_index])
            self.tree.focus(self.row_ids[first_new_index])
            self._show_preview(first_new_index)

        self._update_stats()
        self._set_controls()

    def remove_selected(self):
        if self.busy:
            return
        selected = set(self.tree.selection())
        if not selected:
            return

        new_files = []
        new_rows = []
        for path, iid in zip(self.files, self.row_ids):
            if iid in selected:
                self.tree.delete(iid)
            else:
                new_files.append(path)
                new_rows.append(iid)
        self.files = new_files
        self.row_ids = new_rows
        self._update_stats()
        self._set_controls()
        if self.files:
            self._show_preview(0)
        else:
            self._clear_preview()

    def clear_files(self):
        if self.busy:
            return
        self.files = []
        self.row_ids = []
        for iid in self.tree.get_children():
            self.tree.delete(iid)
        self.completed = self.success = self.failed = 0
        self.input_total = self.output_total = 0
        self.progress.set(0)
        self.progress_percent_var.set("۰٪")
        self.progress_text_var.set("آماده")
        self._clear_preview()
        self._update_stats()
        self._set_controls()

    # ---------- Preview ----------
    def _on_tree_select(self, _event=None):
        selected = self.tree.selection()
        if not selected:
            return
        iid = selected[0]
        try:
            index = self.row_ids.index(iid)
        except ValueError:
            return
        self._show_preview(index)

    def _clear_preview(self):
        self.preview_image = None
        self.current_preview_index = None
        self.preview_label.configure(image=None, text="◉\n\nپیش‌نمایش تصویر", text_color="#5C5C5C")
        self.preview_name_var.set("هنوز عکسی انتخاب نشده")
        self.preview_meta_var.set("HEIC / HEIF / JPG / JPEG")
        self.preview_status_var.set("برای شروع، فایل یا پوشه اضافه کنید")

    def _show_preview(self, index):
        if index < 0 or index >= len(self.files):
            return
        path = self.files[index]
        self.current_preview_index = index
        self.preview_name_var.set(path.name)
        try:
            size_text = human_size(path.stat().st_size)
        except OSError:
            size_text = "—"
        self.preview_meta_var.set("%s  •  %s" % (path.suffix.upper().lstrip("."), size_text))
        self.preview_status_var.set("آماده برای تبدیل")

        try:
            with Image.open(str(path)) as opened:
                image = ImageOps.exif_transpose(opened.copy())
                if image.mode not in ("RGB", "RGBA"):
                    image = image.convert("RGB")
                image.thumbnail((320, 330), Image.LANCZOS)
                self.preview_image = ctk.CTkImage(light_image=image, dark_image=image, size=image.size)
                self.preview_label.configure(image=self.preview_image, text="")
        except Exception:
            self.preview_image = None
            self.preview_label.configure(image=None, text="نمایش پیش‌نمایش\nممکن نیست", text_color=DIM)

    # ---------- Conversion ----------
    def start_conversion(self):
        if self.busy or not self.files:
            if not self.files:
                messagebox.showinfo("فایلی انتخاب نشده", "حداقل یک عکس اضافه کنید.")
            return

        target = self._target_kb()
        workers = self._workers()
        try:
            self.output_dir.mkdir(parents=True, exist_ok=True)
        except Exception as exc:
            messagebox.showerror("خطا", "پوشه خروجی ساخته نشد:\n%s" % exc)
            return

        self._save_settings()
        self.cancel_event.clear()
        self.busy = True
        self.completed = self.success = self.failed = 0
        self.input_total = self.output_total = 0
        self.batch_started = time.time()
        self.progress.set(0)
        self.progress_percent_var.set("۰٪")
        self.progress_text_var.set("در حال آماده‌سازی موتور Turbo...")

        for iid in self.row_ids:
            self.tree.set(iid, "output", "—")
            self.tree.set(iid, "quality", "—")
            self.tree.set(iid, "status", "در صف")
            self.tree.item(iid, tags=())

        self._set_controls()
        self._update_stats()

        options = ConvertOptions(
            target_kb=target,
            preserve_exif=bool(self.preserve_exif_var.get()),
            preserve_icc=bool(self.preserve_icc_var.get()),
        )
        self.worker = threading.Thread(
            target=self._process_batch,
            args=(list(self.files), options, workers),
            daemon=True,
        )
        self.worker.start()

    def _process_batch(self, files, options, workers):
        def run_one(index, path):
            if self.cancel_event.is_set():
                return index, None
            self.events.put(("started", index))
            result = convert_image_to_jpg(
                path,
                self.output_dir,
                options,
                should_cancel=self.cancel_event.is_set,
            )
            return index, result

        try:
            with ThreadPoolExecutor(max_workers=workers) as pool:
                future_map = {pool.submit(run_one, i, path): i for i, path in enumerate(files)}
                for future in as_completed(future_map):
                    if self.cancel_event.is_set():
                        for pending in future_map:
                            pending.cancel()
                    index = future_map[future]
                    try:
                        result_index, result = future.result()
                    except Exception as exc:
                        self.events.put(("worker_error", index, str(exc)))
                        continue
                    if result is not None:
                        self.events.put(("result", result_index, result))
            self.events.put(("finished", bool(self.cancel_event.is_set())))
        except Exception as exc:
            self.events.put(("fatal", str(exc)))

    def cancel_conversion(self):
        if self.busy:
            self.cancel_event.set()
            self.progress_text_var.set("در حال توقف پردازش‌ها...")
            self.cancel_btn.configure(state="disabled")

    def _poll_events(self):
        try:
            while True:
                event = self.events.get_nowait()
                kind = event[0]
                if kind == "started":
                    idx = event[1]
                    if idx < len(self.row_ids):
                        iid = self.row_ids[idx]
                        self.tree.set(iid, "status", "در حال پردازش...")
                        self.tree.item(iid, tags=("running",))
                elif kind == "result":
                    self._handle_result(event[1], event[2])
                elif kind == "worker_error":
                    self._handle_worker_error(event[1], event[2])
                elif kind == "finished":
                    self._finish_batch(event[1])
                elif kind == "fatal":
                    self._fatal_error(event[1])
        except queue.Empty:
            pass
        self.root.after(80, self._poll_events)

    def _handle_result(self, index, result):
        if index >= len(self.row_ids):
            return
        iid = self.row_ids[index]
        self.completed += 1
        self.input_total += int(result.input_bytes or 0)
        self.output_total += int(result.output_bytes or 0)

        if result.ok:
            self.success += 1
            quality = "اصل" if result.copied_original else ("Q%d" % result.quality if result.quality else "—")
            status = "بدون افت کیفیت" if result.copied_original else ("انجام شد" + (" • Resize" if result.resized else ""))
            self.tree.set(iid, "output", human_size(result.output_bytes))
            self.tree.set(iid, "quality", quality)
            self.tree.set(iid, "status", status)
            self.tree.item(iid, tags=("success",))
        else:
            if result.message != "لغو شد":
                self.failed += 1
            self.tree.set(iid, "status", result.message or "ناموفق")
            self.tree.item(iid, tags=("error",))

        if self.current_preview_index == index:
            if result.ok:
                self.preview_status_var.set("خروجی: %s" % human_size(result.output_bytes))
            else:
                self.preview_status_var.set(result.message or "ناموفق")
        self._update_progress()

    def _handle_worker_error(self, index, text):
        self.completed += 1
        self.failed += 1
        if index < len(self.row_ids):
            iid = self.row_ids[index]
            self.tree.set(iid, "status", "خطا: %s" % text)
            self.tree.item(iid, tags=("error",))
        self._update_progress()

    def _update_progress(self):
        total = max(1, len(self.files))
        fraction = min(1.0, self.completed / float(total))
        self.progress.set(fraction)
        self.progress_percent_var.set("%d٪" % int(round(fraction * 100)))
        elapsed = max(0.001, time.time() - self.batch_started)
        rate = self.completed / elapsed
        self.progress_text_var.set("%d از %d فایل  •  %.1f فایل/ثانیه" % (self.completed, len(self.files), rate))
        self._update_stats()

    def _update_stats(self):
        count = len(self.files)
        self.queue_stat_var.set("%d فایل در صف" % count)
        if self.busy:
            self.summary_label.configure(text="%d موفق  •  %d ناموفق" % (self.success, self.failed), text_color=ACCENT)
        elif count:
            self.summary_label.configure(text="%d فایل آماده" % count, text_color=MUTED)
        else:
            self.summary_label.configure(text="آماده", text_color=MUTED)

    def _finish_batch(self, cancelled):
        if not self.busy:
            return
        self.busy = False
        self._set_controls()
        self._update_stats()

        if cancelled:
            self.progress_text_var.set("پردازش متوقف شد")
        else:
            self.progress.set(1)
            self.progress_percent_var.set("۱۰۰٪")
            saved = max(0, self.input_total - self.output_total)
            self.progress_text_var.set("تمام شد  •  %d موفق  •  %d ناموفق  •  %s کاهش حجم" % (self.success, self.failed, human_size(saved)))
            if self.failed == 0:
                messagebox.showinfo("تبدیل کامل شد", "%d فایل با موفقیت پردازش شد." % self.success)
            else:
                messagebox.showwarning("پایان پردازش", "%d موفق و %d ناموفق" % (self.success, self.failed))

    def _fatal_error(self, text):
        self.busy = False
        self._set_controls()
        messagebox.showerror("خطای پردازش", text)

    def _set_controls(self):
        state = "disabled" if self.busy else "normal"
        for widget in [self.add_files_btn, self.add_folder_btn, self.remove_btn, self.clear_btn, self.prefs_btn, self.output_btn]:
            try:
                widget.configure(state=state)
            except Exception:
                pass
        self.target_entry.configure(state=state)
        self.workers_menu.configure(state=state)
        self.exif_switch.configure(state=state)
        self.icc_switch.configure(state=state)
        self.convert_btn.configure(state="normal" if (self.files and not self.busy) else "disabled")
        self.cancel_btn.configure(state="normal" if self.busy else "disabled")

    def _on_close(self):
        if self.busy:
            if not messagebox.askyesno("خروج", "پردازش در حال اجراست. برنامه بسته شود؟"):
                return
            self.cancel_event.set()
        self._save_settings()
        self.root.destroy()


def main():
    ctk.set_appearance_mode("dark")
    ctk.set_default_color_theme("blue")
    root = ctk.CTk()
    ConverterApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
