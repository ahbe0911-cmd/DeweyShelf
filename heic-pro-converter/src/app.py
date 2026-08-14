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

from converter import ConvertOptions, SUPPORTED_EXTENSIONS, convert_image_to_jpg, human_size

APP_NAME = "HEIC Pro Converter"
VERSION = "4.0 Commercial"

BG = "#F5F7FB"
SURFACE = "#FFFFFF"
SURFACE_SOFT = "#F9FAFC"
NAV = "#111827"
NAV_HOVER = "#1F2937"
TEXT = "#172033"
MUTED = "#667085"
BORDER = "#E4E9F1"
PRIMARY = "#365CF5"
PRIMARY_HOVER = "#2F4FE0"
PRIMARY_SOFT = "#EEF2FF"
SUCCESS = "#16A34A"
SUCCESS_SOFT = "#ECFDF3"
DANGER = "#D92D20"
DANGER_SOFT = "#FEF3F2"
WARNING = "#B54708"
WARNING_SOFT = "#FFF7ED"
PURPLE = "#7C3AED"
PURPLE_SOFT = "#F5F3FF"


class ConverterApp:
    def __init__(self, root):
        self.root = root
        self.root.title("HEIC Pro Converter — Commercial")
        self.root.geometry("1280x820")
        self.root.minsize(1080, 700)
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
        self.output_var = tk.StringVar()
        self.progress_text_var = tk.StringVar(value="آماده برای پردازش")
        self.progress_percent_var = tk.StringVar(value="0٪")
        self.total_stat_var = tk.StringVar(value="۰")
        self.done_stat_var = tk.StringVar(value="۰")
        self.saved_stat_var = tk.StringVar(value="0 KB")
        self.speed_stat_var = tk.StringVar(value="—")
        self.font_info_var = tk.StringVar(value="")

        self.body_font_family, self.title_font_family = self._detect_fonts()
        self.font_body = ctk.CTkFont(family=self.body_font_family, size=15)
        self.font_body_small = ctk.CTkFont(family=self.body_font_family, size=13)
        self.font_body_bold = ctk.CTkFont(family=self.body_font_family, size=15, weight="bold")
        self.font_title = ctk.CTkFont(family=self.title_font_family, size=28)
        self.font_heading = ctk.CTkFont(family=self.title_font_family, size=20)
        self.font_stat = ctk.CTkFont(family="Segoe UI", size=22, weight="bold")

        self._configure_tree_style()
        self._build_ui()
        self._update_output_label()
        self._update_stats()
        self._set_controls()

        self.root.protocol("WM_DELETE_WINDOW", self._on_close)
        self.root.after(80, self._poll_events)

        if len(sys.argv) > 1:
            self.root.after(250, lambda: self.add_paths(sys.argv[1:]))

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

    def _configure_tree_style(self):
        style = ttk.Style(self.root)
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass
        style.configure(
            "Commercial.Treeview",
            rowheight=38,
            background=SURFACE,
            fieldbackground=SURFACE,
            foreground=TEXT,
            borderwidth=0,
            relief="flat",
            font=(self.body_font_family, 11),
        )
        style.configure(
            "Commercial.Treeview.Heading",
            background="#F2F4F8",
            foreground="#344054",
            borderwidth=0,
            relief="flat",
            padding=(8, 10),
            font=(self.title_font_family, 11),
        )
        style.map("Commercial.Treeview", background=[("selected", "#EAF0FF")], foreground=[("selected", TEXT)])
        style.map("Commercial.Treeview.Heading", background=[("active", "#E9EDF4")])

    def _build_ui(self):
        self.root.grid_columnconfigure(0, weight=1)
        self.root.grid_rowconfigure(0, weight=1)

        shell = ctk.CTkFrame(self.root, fg_color=BG, corner_radius=0)
        shell.grid(row=0, column=0, sticky="nsew")
        shell.grid_columnconfigure(0, weight=1)
        shell.grid_columnconfigure(1, minsize=230)
        shell.grid_rowconfigure(0, weight=1)

        self.main = ctk.CTkFrame(shell, fg_color=BG, corner_radius=0)
        self.main.grid(row=0, column=0, sticky="nsew")
        self.main.grid_columnconfigure(0, weight=1)
        self.main.grid_rowconfigure(1, weight=1)

        self.sidebar = ctk.CTkFrame(shell, fg_color=NAV, corner_radius=0, width=230)
        self.sidebar.grid(row=0, column=1, sticky="nsew")
        self.sidebar.grid_propagate(False)
        self.sidebar.grid_columnconfigure(0, weight=1)
        self.sidebar.grid_rowconfigure(5, weight=1)

        self._build_sidebar()
        self._build_header()
        self._build_pages()
        self.show_page("converter")

    def _build_sidebar(self):
        brand = ctk.CTkFrame(self.sidebar, fg_color="transparent")
        brand.grid(row=0, column=0, sticky="ew", padx=18, pady=(24, 22))
        ctk.CTkLabel(
            brand,
            text="HEIC PRO",
            text_color="white",
            font=ctk.CTkFont(family="Segoe UI", size=20, weight="bold"),
            anchor="e",
        ).pack(fill="x")
        ctk.CTkLabel(
            brand,
            text="مبدل حرفه‌ای تصویر",
            text_color="#9CA9BA",
            font=self.font_body_small,
            anchor="e",
        ).pack(fill="x", pady=(3, 0))

        self.nav_converter = self._nav_button("تبدیل تصاویر", lambda: self.show_page("converter"))
        self.nav_converter.grid(row=1, column=0, sticky="ew", padx=12, pady=4)
        self.nav_settings = self._nav_button("تنظیمات", lambda: self.show_page("settings"))
        self.nav_settings.grid(row=2, column=0, sticky="ew", padx=12, pady=4)

        divider = ctk.CTkFrame(self.sidebar, height=1, fg_color="#253246", corner_radius=0)
        divider.grid(row=3, column=0, sticky="ew", padx=18, pady=18)

        info = ctk.CTkFrame(self.sidebar, fg_color="#182235", corner_radius=12)
        info.grid(row=4, column=0, sticky="ew", padx=14)
        ctk.CTkLabel(info, text="موتور Turbo", text_color="#DCE5F2", font=self.font_body_bold, anchor="e").pack(fill="x", padx=12, pady=(12, 2))
        ctk.CTkLabel(info, text="پردازش موازی تا ۶ فایل", text_color="#8FA0B7", font=self.font_body_small, anchor="e").pack(fill="x", padx=12, pady=(0, 12))

        footer = ctk.CTkFrame(self.sidebar, fg_color="transparent")
        footer.grid(row=6, column=0, sticky="sew", padx=16, pady=18)
        ctk.CTkLabel(footer, text="V4 COMMERCIAL", text_color="#8190A6", font=ctk.CTkFont(family="Segoe UI", size=11, weight="bold")).pack(anchor="e")
        ctk.CTkLabel(footer, text="HEIC • HEIF • JPG • JPEG", text_color="#66758A", font=ctk.CTkFont(family="Segoe UI", size=9)).pack(anchor="e", pady=(2, 0))

    def _nav_button(self, text, command):
        return ctk.CTkButton(
            self.sidebar,
            text=text,
            command=command,
            height=44,
            corner_radius=10,
            fg_color="transparent",
            hover_color=NAV_HOVER,
            text_color="#D7DFEA",
            font=self.font_body_bold,
            anchor="e",
            border_width=0,
        )

    def _build_header(self):
        header = ctk.CTkFrame(self.main, fg_color=BG, corner_radius=0, height=90)
        header.grid(row=0, column=0, sticky="ew", padx=26, pady=(18, 8))
        header.grid_columnconfigure(0, weight=1)
        header.grid_propagate(False)

        right = ctk.CTkFrame(header, fg_color="transparent")
        right.grid(row=0, column=0, sticky="nsew")
        ctk.CTkLabel(right, text="مدیریت تبدیل تصاویر", text_color=TEXT, font=self.font_title, anchor="e").pack(fill="x")
        ctk.CTkLabel(
            right,
            text="تبدیل HEIC/HEIF و بهینه‌سازی JPG با بیشترین کیفیت ممکن",
            text_color=MUTED,
            font=self.font_body_small,
            anchor="e",
        ).pack(fill="x", pady=(4, 0))

    def _build_pages(self):
        self.page_container = ctk.CTkFrame(self.main, fg_color=BG, corner_radius=0)
        self.page_container.grid(row=1, column=0, sticky="nsew", padx=26, pady=(0, 24))
        self.page_container.grid_columnconfigure(0, weight=1)
        self.page_container.grid_rowconfigure(0, weight=1)

        self.converter_page = ctk.CTkFrame(self.page_container, fg_color=BG, corner_radius=0)
        self.settings_page = ctk.CTkFrame(self.page_container, fg_color=BG, corner_radius=0)
        for page in (self.converter_page, self.settings_page):
            page.grid(row=0, column=0, sticky="nsew")

        self._build_converter_page()
        self._build_settings_page()

    def show_page(self, name):
        if name == "settings":
            self.settings_page.tkraise()
            self.nav_settings.configure(fg_color="#26354B", text_color="white")
            self.nav_converter.configure(fg_color="transparent", text_color="#D7DFEA")
        else:
            self.converter_page.tkraise()
            self.nav_converter.configure(fg_color="#26354B", text_color="white")
            self.nav_settings.configure(fg_color="transparent", text_color="#D7DFEA")

    def _build_converter_page(self):
        page = self.converter_page
        page.grid_columnconfigure(0, weight=1)
        page.grid_rowconfigure(3, weight=1)

        toolbar = ctk.CTkFrame(page, fg_color=SURFACE, corner_radius=16, border_width=1, border_color=BORDER)
        toolbar.grid(row=0, column=0, sticky="ew", pady=(0, 12))
        toolbar.grid_columnconfigure(0, weight=1)

        action_box = ctk.CTkFrame(toolbar, fg_color="transparent")
        action_box.grid(row=0, column=0, sticky="w", padx=14, pady=14)
        self.add_files_btn = ctk.CTkButton(
            action_box, text="افزودن فایل", command=self.pick_files,
            width=122, height=40, corner_radius=10,
            fg_color=PRIMARY, hover_color=PRIMARY_HOVER,
            font=self.font_body_bold,
        )
        self.add_files_btn.pack(side="left", padx=(0, 8))
        self.add_folder_btn = ctk.CTkButton(
            action_box, text="افزودن پوشه", command=self.pick_folder,
            width=122, height=40, corner_radius=10,
            fg_color=PRIMARY_SOFT, hover_color="#E0E7FF", text_color=PRIMARY,
            font=self.font_body_bold,
        )
        self.add_folder_btn.pack(side="left")

        quick = ctk.CTkFrame(toolbar, fg_color="transparent")
        quick.grid(row=0, column=1, sticky="e", padx=14, pady=14)
        ctk.CTkLabel(quick, text="سقف خروجی", text_color=MUTED, font=self.font_body_small).pack(side="right", padx=(6, 0))
        self.target_entry = ctk.CTkEntry(
            quick, textvariable=self.target_var, width=88, height=38, corner_radius=9,
            justify="center", border_color=BORDER, fg_color=SURFACE_SOFT,
            font=ctk.CTkFont(family="Segoe UI", size=13, weight="bold"),
        )
        self.target_entry.pack(side="right")
        ctk.CTkLabel(quick, text="KB", text_color=MUTED, font=ctk.CTkFont(family="Segoe UI", size=11)).pack(side="right", padx=(0, 6))

        stats = ctk.CTkFrame(page, fg_color="transparent")
        stats.grid(row=1, column=0, sticky="ew", pady=(0, 12))
        for i in range(4):
            stats.grid_columnconfigure(i, weight=1)
        self._stat_card(stats, 0, "کل فایل‌ها", self.total_stat_var, PRIMARY)
        self._stat_card(stats, 1, "موفق / ناموفق", self.done_stat_var, SUCCESS)
        self._stat_card(stats, 2, "کاهش حجم", self.saved_stat_var, PURPLE)
        self._stat_card(stats, 3, "سرعت", self.speed_stat_var, WARNING)

        output_card = ctk.CTkFrame(page, fg_color=SURFACE, corner_radius=14, border_width=1, border_color=BORDER)
        output_card.grid(row=2, column=0, sticky="ew", pady=(0, 12))
        output_card.grid_columnconfigure(1, weight=1)
        ctk.CTkLabel(output_card, text="پوشه خروجی", text_color=TEXT, font=self.font_body_bold).grid(row=0, column=2, padx=(8, 16), pady=12)
        self.output_label = ctk.CTkLabel(output_card, textvariable=self.output_var, text_color=MUTED, font=self.font_body_small, anchor="w")
        self.output_label.grid(row=0, column=1, sticky="ew", padx=8, pady=12)
        self.open_output_btn = ctk.CTkButton(
            output_card, text="باز کردن", command=self.open_output,
            width=92, height=34, corner_radius=9,
            fg_color="#F2F4F7", hover_color="#E9EDF3", text_color=TEXT,
            font=self.font_body_small,
        )
        self.open_output_btn.grid(row=0, column=0, padx=(14, 6), pady=8)

        table_card = ctk.CTkFrame(page, fg_color=SURFACE, corner_radius=16, border_width=1, border_color=BORDER)
        table_card.grid(row=3, column=0, sticky="nsew", pady=(0, 12))
        table_card.grid_columnconfigure(0, weight=1)
        table_card.grid_rowconfigure(1, weight=1)

        table_head = ctk.CTkFrame(table_card, fg_color="transparent")
        table_head.grid(row=0, column=0, sticky="ew", padx=14, pady=(12, 8))
        ctk.CTkLabel(table_head, text="صف پردازش", text_color=TEXT, font=self.font_heading, anchor="e").pack(side="right")
        self.remove_btn = ctk.CTkButton(
            table_head, text="حذف انتخاب‌شده", command=self.remove_selected,
            width=120, height=34, corner_radius=8,
            fg_color="#F2F4F7", hover_color="#E9EDF3", text_color=TEXT,
            font=self.font_body_small,
        )
        self.remove_btn.pack(side="left", padx=(0, 6))
        self.clear_btn = ctk.CTkButton(
            table_head, text="پاک کردن", command=self.clear_files,
            width=90, height=34, corner_radius=8,
            fg_color=DANGER_SOFT, hover_color="#FEE4E2", text_color=DANGER,
            font=self.font_body_small,
        )
        self.clear_btn.pack(side="left")

        tree_wrap = ctk.CTkFrame(table_card, fg_color=SURFACE, corner_radius=0)
        tree_wrap.grid(row=1, column=0, sticky="nsew", padx=12, pady=(0, 10))
        tree_wrap.grid_columnconfigure(0, weight=1)
        tree_wrap.grid_rowconfigure(0, weight=1)

        columns = ("type", "input", "output", "quality", "status")
        self.tree = ttk.Treeview(tree_wrap, columns=columns, show="tree headings", selectmode="extended", style="Commercial.Treeview")
        self.tree.heading("#0", text="نام فایل")
        self.tree.heading("type", text="فرمت")
        self.tree.heading("input", text="حجم اولیه")
        self.tree.heading("output", text="حجم نهایی")
        self.tree.heading("quality", text="کیفیت")
        self.tree.heading("status", text="وضعیت")
        self.tree.column("#0", width=340, minwidth=190, stretch=True, anchor="w")
        self.tree.column("type", width=78, minwidth=65, stretch=False, anchor="center")
        self.tree.column("input", width=104, minwidth=90, stretch=False, anchor="center")
        self.tree.column("output", width=104, minwidth=90, stretch=False, anchor="center")
        self.tree.column("quality", width=80, minwidth=70, stretch=False, anchor="center")
        self.tree.column("status", width=250, minwidth=175, stretch=True, anchor="center")
        self.tree.tag_configure("success", foreground="#137333")
        self.tree.tag_configure("error", foreground="#B42318")
        self.tree.tag_configure("running", foreground="#1D4ED8")
        self.tree.grid(row=0, column=0, sticky="nsew")

        scroll = ctk.CTkScrollbar(tree_wrap, orientation="vertical", command=self.tree.yview, width=14)
        scroll.grid(row=0, column=1, sticky="ns", padx=(5, 0))
        self.tree.configure(yscrollcommand=scroll.set)

        process = ctk.CTkFrame(page, fg_color=SURFACE, corner_radius=16, border_width=1, border_color=BORDER)
        process.grid(row=4, column=0, sticky="ew")
        process.grid_columnconfigure(1, weight=1)

        self.cancel_btn = ctk.CTkButton(
            process, text="توقف", command=self.cancel_conversion,
            width=92, height=42, corner_radius=10,
            fg_color=DANGER_SOFT, hover_color="#FEE4E2", text_color=DANGER,
            font=self.font_body_bold,
        )
        self.cancel_btn.grid(row=0, column=0, rowspan=2, padx=(14, 8), pady=12)

        progress_box = ctk.CTkFrame(process, fg_color="transparent")
        progress_box.grid(row=0, column=1, rowspan=2, sticky="ew", padx=8, pady=12)
        progress_box.grid_columnconfigure(0, weight=1)
        status_line = ctk.CTkFrame(progress_box, fg_color="transparent")
        status_line.grid(row=0, column=0, sticky="ew", pady=(0, 6))
        status_line.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(status_line, textvariable=self.progress_text_var, text_color=MUTED, font=self.font_body_small, anchor="w").grid(row=0, column=0, sticky="w")
        ctk.CTkLabel(status_line, textvariable=self.progress_percent_var, text_color=PRIMARY, font=ctk.CTkFont(family="Segoe UI", size=12, weight="bold")).grid(row=0, column=1, sticky="e")
        self.progress = ctk.CTkProgressBar(progress_box, height=10, corner_radius=8, fg_color="#E9EDF4", progress_color=PRIMARY)
        self.progress.grid(row=1, column=0, sticky="ew")
        self.progress.set(0)

        self.convert_btn = ctk.CTkButton(
            process, text="شروع پردازش", command=self.start_conversion,
            width=150, height=44, corner_radius=11,
            fg_color=PRIMARY, hover_color=PRIMARY_HOVER,
            font=self.font_body_bold,
        )
        self.convert_btn.grid(row=0, column=2, rowspan=2, padx=(8, 14), pady=12)

    def _stat_card(self, parent, col, label, variable, accent):
        card = ctk.CTkFrame(parent, fg_color=SURFACE, corner_radius=14, border_width=1, border_color=BORDER)
        card.grid(row=0, column=col, sticky="ew", padx=(0 if col == 0 else 5, 0 if col == 3 else 5))
        top = ctk.CTkFrame(card, fg_color="transparent")
        top.pack(fill="x", padx=14, pady=(12, 3))
        dot = ctk.CTkFrame(top, width=10, height=10, fg_color=accent, corner_radius=5)
        dot.pack(side="right", padx=(6, 0))
        dot.pack_propagate(False)
        ctk.CTkLabel(top, text=label, text_color=MUTED, font=self.font_body_small, anchor="e").pack(side="right")
        ctk.CTkLabel(card, textvariable=variable, text_color=TEXT, font=self.font_stat, anchor="e").pack(fill="x", padx=14, pady=(0, 13))

    def _build_settings_page(self):
        page = self.settings_page
        page.grid_columnconfigure(0, weight=1)

        title = ctk.CTkLabel(page, text="تنظیمات برنامه", text_color=TEXT, font=self.font_title, anchor="e")
        title.grid(row=0, column=0, sticky="e", pady=(4, 14))

        settings_card = ctk.CTkFrame(page, fg_color=SURFACE, corner_radius=16, border_width=1, border_color=BORDER)
        settings_card.grid(row=1, column=0, sticky="ew", pady=(0, 12))
        settings_card.grid_columnconfigure(0, weight=1)

        self._setting_section_title(settings_card, 0, "پردازش")
        row = ctk.CTkFrame(settings_card, fg_color="transparent")
        row.grid(row=1, column=0, sticky="ew", padx=18, pady=(0, 16))
        row.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(row, text="تعداد پردازش هم‌زمان", text_color=TEXT, font=self.font_body_bold, anchor="e").grid(row=0, column=1, sticky="e")
        ctk.CTkLabel(row, text="برای سیستم‌های معمولی ۴ پیشنهاد می‌شود.", text_color=MUTED, font=self.font_body_small, anchor="e").grid(row=1, column=1, sticky="e", pady=(2, 0))
        self.workers_menu = ctk.CTkOptionMenu(
            row, variable=self.workers_var, values=["1", "2", "3", "4", "5", "6"],
            width=100, height=38, corner_radius=9,
            fg_color=SURFACE_SOFT, button_color="#E9EDF4", button_hover_color="#DDE3EC",
            text_color=TEXT, dropdown_fg_color=SURFACE, dropdown_text_color=TEXT,
            font=ctk.CTkFont(family="Segoe UI", size=12),
        )
        self.workers_menu.grid(row=0, column=0, rowspan=2, sticky="w")

        divider1 = ctk.CTkFrame(settings_card, height=1, fg_color=BORDER, corner_radius=0)
        divider1.grid(row=2, column=0, sticky="ew", padx=18)

        self._setting_section_title(settings_card, 3, "اطلاعات تصویر")
        toggles = ctk.CTkFrame(settings_card, fg_color="transparent")
        toggles.grid(row=4, column=0, sticky="ew", padx=18, pady=(0, 16))
        self.exif_switch = ctk.CTkSwitch(
            toggles, text="حفظ EXIF و تاریخ عکس", variable=self.preserve_exif_var,
            onvalue=True, offvalue=False, progress_color=PRIMARY,
            font=self.font_body, text_color=TEXT,
        )
        self.exif_switch.pack(anchor="e", pady=5)
        self.icc_switch = ctk.CTkSwitch(
            toggles, text="حفظ پروفایل رنگ (ICC)", variable=self.preserve_icc_var,
            onvalue=True, offvalue=False, progress_color=PRIMARY,
            font=self.font_body, text_color=TEXT,
        )
        self.icc_switch.pack(anchor="e", pady=5)

        divider2 = ctk.CTkFrame(settings_card, height=1, fg_color=BORDER, corner_radius=0)
        divider2.grid(row=5, column=0, sticky="ew", padx=18)

        self._setting_section_title(settings_card, 6, "پوشه خروجی")
        output_row = ctk.CTkFrame(settings_card, fg_color="transparent")
        output_row.grid(row=7, column=0, sticky="ew", padx=18, pady=(0, 18))
        output_row.grid_columnconfigure(1, weight=1)
        ctk.CTkButton(
            output_row, text="انتخاب پوشه", command=self.pick_output,
            width=110, height=38, corner_radius=9,
            fg_color=PRIMARY_SOFT, hover_color="#E0E7FF", text_color=PRIMARY,
            font=self.font_body_small,
        ).grid(row=0, column=0, padx=(0, 10))
        ctk.CTkLabel(output_row, textvariable=self.output_var, text_color=MUTED, font=self.font_body_small, anchor="e").grid(row=0, column=1, sticky="ew")

        font_card = ctk.CTkFrame(page, fg_color=SURFACE, corner_radius=16, border_width=1, border_color=BORDER)
        font_card.grid(row=2, column=0, sticky="ew")
        font_card.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(font_card, text="فونت رابط", text_color=TEXT, font=self.font_heading, anchor="e").grid(row=0, column=0, sticky="e", padx=18, pady=(16, 4))
        body_ok = self.body_font_family.lower() != "tahoma"
        title_ok = self.title_font_family.lower() != "tahoma"
        body_text = self.body_font_family if body_ok else "Tahoma (جایگزین؛ Far.Nazanin نصب نیست)"
        title_text = self.title_font_family if title_ok else "Tahoma (جایگزین؛ Far.Titr نصب نیست)"
        self.font_info_var.set("متن: %s    |    تیتر: %s" % (body_text, title_text))
        ctk.CTkLabel(font_card, textvariable=self.font_info_var, text_color=MUTED, font=self.font_body_small, anchor="e").grid(row=1, column=0, sticky="e", padx=18, pady=(0, 16))

    def _setting_section_title(self, parent, row, text):
        ctk.CTkLabel(parent, text=text, text_color=TEXT, font=self.font_heading, anchor="e").grid(row=row, column=0, sticky="e", padx=18, pady=(16, 12))

    def _target_kb(self, silent=False):
        try:
            value = int(str(self.target_var.get()).strip())
            if value < 50 or value > 20000:
                raise ValueError()
            return value
        except Exception:
            if not silent:
                messagebox.showerror("حجم نامعتبر", "حجم خروجی باید بین 50 تا 20000 KB باشد.")
            return 488

    def _workers(self, silent=False):
        try:
            value = int(str(self.workers_var.get()).strip())
            if value < 1 or value > 6:
                raise ValueError()
            return value
        except Exception:
            if not silent:
                messagebox.showerror("مقدار نامعتبر", "پردازش هم‌زمان باید بین 1 تا 6 فایل باشد.")
            return 4

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
            self._update_output_label()
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
                        if item.is_file() and item.suffix.lower() in SUPPORTED_EXTENSIONS:
                            collected.append(item)
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

        for p in collected:
            try:
                key = str(p.resolve()).lower()
            except Exception:
                key = str(p).lower()
            if key in existing:
                continue
            existing.add(key)
            self.files.append(p)
            try:
                size_text = human_size(p.stat().st_size)
            except OSError:
                size_text = "—"
            iid = self.tree.insert("", "end", text=p.name, values=(p.suffix.upper().lstrip("."), size_text, "—", "—", "آماده"))
            self.row_ids.append(iid)
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
        self.progress_percent_var.set("0٪")
        self.progress_text_var.set("آماده برای پردازش")
        self._update_stats()
        self._set_controls()

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
        self.progress_percent_var.set("0٪")
        self.progress_text_var.set("در حال آماده‌سازی موتور Turbo…")
        for iid in self.row_ids:
            self.tree.set(iid, "output", "—")
            self.tree.set(iid, "quality", "—")
            self.tree.set(iid, "status", "در صف")
            self.tree.item(iid, tags=())
        self._set_controls()
        self._update_stats()

        options = ConvertOptions(target_kb=target, preserve_exif=bool(self.preserve_exif_var.get()), preserve_icc=bool(self.preserve_icc_var.get()))
        self.worker = threading.Thread(target=self._process_batch, args=(list(self.files), options, workers), daemon=True)
        self.worker.start()

    def _process_batch(self, files, options, workers):
        def run_one(index, path):
            if self.cancel_event.is_set():
                return index, None
            self.events.put(("started", index))
            result = convert_image_to_jpg(path, self.output_dir, options, should_cancel=self.cancel_event.is_set)
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
            self.progress_text_var.set("در حال توقف پردازش‌ها…")
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
                        self.tree.set(iid, "status", "در حال پردازش…")
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
        ratio = min(1.0, self.completed / float(total))
        self.progress.set(ratio)
        self.progress_percent_var.set("%d٪" % int(round(ratio * 100)))
        elapsed = max(0.001, time.time() - self.batch_started)
        rate = self.completed / elapsed
        self.progress_text_var.set("%d از %d فایل • %.1f فایل/ثانیه" % (self.completed, len(self.files), rate))
        self._update_stats()

    def _update_stats(self):
        self.total_stat_var.set(str(len(self.files)))
        self.done_stat_var.set("%d / %d" % (self.success, self.failed) if self.completed else "0")
        saved = max(0, self.input_total - self.output_total)
        self.saved_stat_var.set(human_size(saved))
        if self.completed and self.batch_started:
            elapsed = max(0.001, time.time() - self.batch_started)
            self.speed_stat_var.set("%.1f فایل/s" % (self.completed / elapsed))
        else:
            self.speed_stat_var.set("—")

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
            self.progress_percent_var.set("100٪")
            self.progress_text_var.set("تمام شد • %d موفق • %d ناموفق" % (self.success, self.failed))
            if self.failed == 0:
                messagebox.showinfo("انجام شد", "%d فایل با موفقیت پردازش شد." % self.success)
            else:
                messagebox.showwarning("پایان پردازش", "%d موفق و %d ناموفق" % (self.success, self.failed))

    def _fatal_error(self, text):
        self.busy = False
        self._set_controls()
        messagebox.showerror("خطای پردازش", text)

    def _set_controls(self):
        normal = "normal"
        disabled = "disabled"
        for control in (self.add_files_btn, self.add_folder_btn, self.remove_btn, self.clear_btn):
            control.configure(state=disabled if self.busy else normal)
        self.convert_btn.configure(state=normal if (self.files and not self.busy) else disabled)
        self.cancel_btn.configure(state=normal if self.busy else disabled)
        self.target_entry.configure(state=disabled if self.busy else normal)
        self.workers_menu.configure(state=disabled if self.busy else normal)
        self.exif_switch.configure(state=disabled if self.busy else normal)
        self.icc_switch.configure(state=disabled if self.busy else normal)

    def _update_output_label(self):
        self.output_var.set(str(self.output_dir))

    def _on_close(self):
        if self.busy:
            if not messagebox.askyesno("خروج", "پردازش در حال اجراست. برنامه بسته شود؟"):
                return
            self.cancel_event.set()
        self._save_settings()
        self.root.destroy()


def main():
    ctk.set_appearance_mode("Light")
    ctk.set_default_color_theme("blue")
    root = ctk.CTk()
    ConverterApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
