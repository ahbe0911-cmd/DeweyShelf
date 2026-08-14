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
VERSION = "6.0 Silver Studio"

# Light commercial desktop palette inspired by classic converter applications.
BG = "#D7D7D7"
CHROME = "#8B8B8B"
CHROME_DARK = "#6F6F6F"
TOOLBAR = "#2F3235"
TOOLBAR_HOVER = "#3B3F43"
SURFACE = "#F7F7F7"
SURFACE_2 = "#ECECEC"
SURFACE_3 = "#E2E2E2"
BORDER = "#B8B8B8"
BORDER_DARK = "#8E8E8E"
TEXT = "#202020"
MUTED = "#666666"
BLUE = "#2388C5"
BLUE_HOVER = "#1E78AE"
BLUE_SOFT = "#D7EFFB"
GREEN = "#2E8B57"
RED = "#C64545"
ORANGE = "#E4931B"


class ConverterApp:
    def __init__(self, root):
        self.root = root
        self.root.title("HEIC Pro Converter — Silver Studio")
        self.root.geometry("1280x820")
        self.root.minsize(1060, 700)
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
        self.progress_text_var = tk.StringVar(value="آماده برای تبدیل")
        self.progress_percent_var = tk.StringVar(value="۰٪")
        self.queue_stat_var = tk.StringVar(value="۰ فایل")
        self.preview_name_var = tk.StringVar(value="فایلی انتخاب نشده")
        self.preview_meta_var = tk.StringVar(value="HEIC / HEIF / JPG / JPEG")
        self.preview_status_var = tk.StringVar(value="فایل یا پوشه‌ای به صف اضافه کنید")
        self.profile_var = tk.StringVar(value="JPG • حداکثر %s KB" % self.target_var.get())

        self.body_font_family, self.title_font_family = self._detect_fonts()
        self.font_body = ctk.CTkFont(family=self.body_font_family, size=15)
        self.font_small = ctk.CTkFont(family=self.body_font_family, size=13)
        self.font_body_bold = ctk.CTkFont(family=self.body_font_family, size=15, weight="bold")
        self.font_title = ctk.CTkFont(family=self.title_font_family, size=21)
        self.font_heading = ctk.CTkFont(family=self.title_font_family, size=18)
        self.font_toolbar = ctk.CTkFont(family=self.body_font_family, size=13, weight="bold")

        self._configure_tree_style()
        self._build_native_menu()
        self._build_ui()
        self._set_controls()
        self._update_stats()

        self.target_var.trace_add("write", self._target_changed)
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
            return 3

    def _target_changed(self, *_args):
        value = str(self.target_var.get()).strip() or "488"
        self.profile_var.set("JPG • حداکثر %s KB" % value)

    # ---------- Native menu / style ----------
    def _build_native_menu(self):
        menu = tk.Menu(self.root)

        file_menu = tk.Menu(menu, tearoff=False)
        file_menu.add_command(label="افزودن فایل‌ها", command=self.pick_files)
        file_menu.add_command(label="افزودن پوشه", command=self.pick_folder)
        file_menu.add_separator()
        file_menu.add_command(label="انتخاب پوشه خروجی", command=self.pick_output)
        file_menu.add_command(label="باز کردن پوشه خروجی", command=self.open_output)
        file_menu.add_separator()
        file_menu.add_command(label="خروج", command=self._on_close)
        menu.add_cascade(label="فایل", menu=file_menu)

        edit_menu = tk.Menu(menu, tearoff=False)
        edit_menu.add_command(label="حذف انتخاب‌شده", command=self.remove_selected)
        edit_menu.add_command(label="پاک کردن همه", command=self.clear_files)
        menu.add_cascade(label="ویرایش", menu=edit_menu)

        tools_menu = tk.Menu(menu, tearoff=False)
        tools_menu.add_command(label="تنظیمات خروجی", command=self._focus_settings)
        tools_menu.add_command(label="مشخصات فایل انتخاب‌شده", command=self.show_selected_properties)
        menu.add_cascade(label="ابزارها", menu=tools_menu)

        help_menu = tk.Menu(menu, tearoff=False)
        help_menu.add_command(label="راهنما", command=self._show_help)
        help_menu.add_command(label="درباره برنامه", command=self._show_about)
        menu.add_cascade(label="راهنما", menu=help_menu)

        try:
            self.root.config(menu=menu)
        except Exception:
            pass

    def _configure_tree_style(self):
        style = ttk.Style(self.root)
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass

        style.configure(
            "Silver.Treeview",
            rowheight=40,
            background="#FFFFFF",
            fieldbackground="#FFFFFF",
            foreground=TEXT,
            borderwidth=0,
            relief="flat",
            font=(self.body_font_family, 11),
        )
        style.configure(
            "Silver.Treeview.Heading",
            background="#DCECF5",
            foreground="#2E4D5E",
            borderwidth=1,
            bordercolor="#BDD1DD",
            relief="flat",
            padding=(7, 10),
            font=(self.title_font_family, 11),
        )
        style.map(
            "Silver.Treeview",
            background=[("selected", "#B9DDF1")],
            foreground=[("selected", "#173A4E")],
        )
        style.map("Silver.Treeview.Heading", background=[("active", "#CFE3EE")])
        style.configure(
            "Silver.Vertical.TScrollbar",
            background="#C9C9C9",
            troughcolor="#ECECEC",
            bordercolor="#AFAFAF",
            arrowcolor="#555555",
        )

    # ---------- UI ----------
    def _build_ui(self):
        self.root.grid_columnconfigure(0, weight=1)
        self.root.grid_rowconfigure(0, weight=1)

        shell = ctk.CTkFrame(self.root, fg_color=BG, corner_radius=0)
        shell.grid(row=0, column=0, sticky="nsew")
        shell.grid_columnconfigure(0, weight=1)
        shell.grid_rowconfigure(2, weight=1)

        self._build_top_strip(shell)
        self._build_toolbar(shell)
        self._build_workspace(shell)
        self._build_bottom_controls(shell)

    def _build_top_strip(self, parent):
        strip = ctk.CTkFrame(parent, height=43, fg_color=CHROME, corner_radius=0)
        strip.grid(row=0, column=0, sticky="ew")
        strip.grid_columnconfigure(1, weight=1)

        ctk.CTkLabel(
            strip,
            text="HEIC Pro Converter",
            text_color="white",
            font=ctk.CTkFont(family="Segoe UI", size=16, weight="bold"),
        ).grid(row=0, column=0, padx=(16, 8), pady=7, sticky="w")
        ctk.CTkLabel(
            strip,
            text="SILVER STUDIO V6",
            text_color="#E9F5FD",
            font=ctk.CTkFont(family="Segoe UI", size=10, weight="bold"),
        ).grid(row=0, column=1, padx=6, sticky="w")
        ctk.CTkLabel(
            strip,
            textvariable=self.queue_stat_var,
            text_color="#F2F2F2",
            font=self.font_small,
        ).grid(row=0, column=2, padx=16, sticky="e")

    def _tool_button(self, parent, symbol, title, command, accent=BLUE):
        wrap = ctk.CTkFrame(parent, fg_color="transparent", width=112, height=92)
        wrap.pack_propagate(False)
        button = ctk.CTkButton(
            wrap,
            text=symbol,
            command=command,
            width=66,
            height=50,
            corner_radius=8,
            fg_color="#3A3D40",
            hover_color=TOOLBAR_HOVER,
            border_width=1,
            border_color="#565A5E",
            text_color=accent,
            font=ctk.CTkFont(family="Segoe UI Symbol", size=27, weight="bold"),
        )
        button.pack(pady=(6, 2))
        ctk.CTkLabel(wrap, text=title, text_color="#F0F0F0", font=self.font_toolbar).pack()
        return wrap, button

    def _build_toolbar(self, parent):
        toolbar = ctk.CTkFrame(parent, height=102, fg_color=TOOLBAR, corner_radius=0)
        toolbar.grid(row=1, column=0, sticky="ew")
        toolbar.grid_columnconfigure(6, weight=1)

        tools = [
            ("⊞", "افزودن فایل", self.pick_files, "add_files_btn", "#4AA7E0"),
            ("▣", "افزودن پوشه", self.pick_folder, "add_folder_btn", "#4AA7E0"),
            ("✕", "حذف", self.remove_selected, "remove_btn", "#E77070"),
            ("▤", "پاک کردن همه", self.clear_files, "clear_btn", "#E4A840"),
            ("⚙", "تنظیمات", self._focus_settings, "settings_btn", "#76B6DC"),
        ]
        for i, (symbol, title, command, attr, accent) in enumerate(tools):
            wrap, btn = self._tool_button(toolbar, symbol, title, command, accent)
            wrap.grid(row=0, column=i, padx=(12 if i == 0 else 2, 2), pady=5)
            setattr(self, attr, btn)

        badge = ctk.CTkFrame(toolbar, fg_color="#25282B", corner_radius=8, border_width=1, border_color="#474B4F")
        badge.grid(row=0, column=6, sticky="e", padx=16, pady=18)
        ctk.CTkLabel(badge, text="Turbo Engine", text_color="#79C7F4", font=ctk.CTkFont(family="Segoe UI", size=13, weight="bold")).pack(anchor="e", padx=13, pady=(8, 0))
        ctk.CTkLabel(badge, text="HEIC / HEIF / JPG / JPEG", text_color="#BFC5CA", font=ctk.CTkFont(family="Segoe UI", size=10)).pack(anchor="e", padx=13, pady=(1, 8))

    def _build_workspace(self, parent):
        workspace = ctk.CTkFrame(parent, fg_color=BG, corner_radius=0)
        workspace.grid(row=2, column=0, sticky="nsew", padx=12, pady=10)
        workspace.grid_columnconfigure(0, weight=3)
        workspace.grid_columnconfigure(1, weight=2, minsize=330)
        workspace.grid_rowconfigure(0, weight=1)

        self._build_queue_panel(workspace)
        self._build_preview_panel(workspace)

    def _build_queue_panel(self, parent):
        panel = ctk.CTkFrame(parent, fg_color=SURFACE_2, corner_radius=5, border_width=1, border_color=BORDER_DARK)
        panel.grid(row=0, column=0, sticky="nsew", padx=(0, 8))
        panel.grid_columnconfigure(0, weight=1)
        panel.grid_rowconfigure(1, weight=1)

        head = ctk.CTkFrame(panel, fg_color="#D7D7D7", corner_radius=0, height=42)
        head.grid(row=0, column=0, sticky="ew")
        head.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(head, text="فهرست فایل‌ها", text_color=TEXT, font=self.font_heading, anchor="e").grid(row=0, column=1, padx=12)
        self.summary_label = ctk.CTkLabel(head, text="آماده", text_color=MUTED, font=self.font_small)
        self.summary_label.grid(row=0, column=0, padx=12, sticky="w")

        table_frame = ctk.CTkFrame(panel, fg_color="#FFFFFF", corner_radius=0)
        table_frame.grid(row=1, column=0, sticky="nsew", padx=7, pady=(7, 0))
        table_frame.grid_columnconfigure(0, weight=1)
        table_frame.grid_rowconfigure(0, weight=1)

        columns = ("type", "input", "output", "quality", "status")
        self.tree = ttk.Treeview(
            table_frame,
            columns=columns,
            show="tree headings",
            selectmode="extended",
            style="Silver.Treeview",
        )
        self.tree.heading("#0", text="نام فایل")
        self.tree.heading("type", text="فرمت")
        self.tree.heading("input", text="حجم اصلی")
        self.tree.heading("output", text="حجم خروجی")
        self.tree.heading("quality", text="کیفیت")
        self.tree.heading("status", text="وضعیت")
        self.tree.column("#0", width=260, minwidth=150, stretch=True, anchor="w")
        self.tree.column("type", width=68, minwidth=60, stretch=False, anchor="center")
        self.tree.column("input", width=95, minwidth=85, stretch=False, anchor="center")
        self.tree.column("output", width=100, minwidth=85, stretch=False, anchor="center")
        self.tree.column("quality", width=72, minwidth=62, stretch=False, anchor="center")
        self.tree.column("status", width=170, minwidth=125, stretch=True, anchor="center")
        self.tree.tag_configure("success", foreground="#237445")
        self.tree.tag_configure("error", foreground="#B43F3F")
        self.tree.tag_configure("running", foreground="#B06D00")

        scroll = ttk.Scrollbar(table_frame, orient="vertical", command=self.tree.yview, style="Silver.Vertical.TScrollbar")
        self.tree.configure(yscrollcommand=scroll.set)
        self.tree.grid(row=0, column=0, sticky="nsew")
        scroll.grid(row=0, column=1, sticky="ns")
        self.tree.bind("<<TreeviewSelect>>", self._on_tree_select)

        manage = ctk.CTkFrame(panel, fg_color="#DADADA", corner_radius=0, height=48)
        manage.grid(row=2, column=0, sticky="ew", padx=7, pady=(0, 7))
        self.properties_btn = ctk.CTkButton(manage, text="مشخصات", command=self.show_selected_properties, width=92, height=30, fg_color="#EDEDED", hover_color="#DDDDDD", border_width=1, border_color="#AAAAAA", text_color=TEXT, font=self.font_small)
        self.properties_btn.pack(side="left", padx=(8, 4), pady=8)
        self.move_up_btn = ctk.CTkButton(manage, text="↑", command=lambda: self.move_selected(-1), width=36, height=30, fg_color="#EDEDED", hover_color="#DDDDDD", border_width=1, border_color="#AAAAAA", text_color=BLUE, font=ctk.CTkFont(family="Segoe UI", size=17, weight="bold"))
        self.move_up_btn.pack(side="right", padx=(4, 8), pady=8)
        self.move_down_btn = ctk.CTkButton(manage, text="↓", command=lambda: self.move_selected(1), width=36, height=30, fg_color="#EDEDED", hover_color="#DDDDDD", border_width=1, border_color="#AAAAAA", text_color=BLUE, font=ctk.CTkFont(family="Segoe UI", size=17, weight="bold"))
        self.move_down_btn.pack(side="right", padx=4, pady=8)

    def _build_preview_panel(self, parent):
        panel = ctk.CTkFrame(parent, fg_color=SURFACE_2, corner_radius=5, border_width=1, border_color=BORDER_DARK)
        panel.grid(row=0, column=1, sticky="nsew")
        panel.grid_columnconfigure(0, weight=1)
        panel.grid_rowconfigure(1, weight=1)

        head = ctk.CTkFrame(panel, fg_color="#D7D7D7", corner_radius=0, height=42)
        head.grid(row=0, column=0, sticky="ew")
        ctk.CTkLabel(head, text="پیش‌نمایش", text_color=TEXT, font=self.font_heading, anchor="e").pack(side="right", padx=12)

        self.preview_box = ctk.CTkFrame(panel, fg_color="#FFFFFF", corner_radius=2, border_width=1, border_color="#C5C5C5")
        self.preview_box.grid(row=1, column=0, sticky="nsew", padx=8, pady=8)
        self.preview_box.grid_columnconfigure(0, weight=1)
        self.preview_box.grid_rowconfigure(0, weight=1)
        self.preview_label = ctk.CTkLabel(
            self.preview_box,
            text="◉\n\nپیش‌نمایش تصویر",
            text_color="#A0A0A0",
            font=self.font_heading,
            justify="center",
        )
        self.preview_label.grid(row=0, column=0, sticky="nsew", padx=10, pady=10)

        info = ctk.CTkFrame(panel, fg_color="#F2F2F2", corner_radius=3, border_width=1, border_color="#CCCCCC")
        info.grid(row=2, column=0, sticky="ew", padx=8, pady=(0, 8))
        ctk.CTkLabel(info, textvariable=self.preview_name_var, text_color=TEXT, font=self.font_body_bold, anchor="e").pack(fill="x", padx=10, pady=(8, 1))
        ctk.CTkLabel(info, textvariable=self.preview_meta_var, text_color=MUTED, font=self.font_small, anchor="e").pack(fill="x", padx=10)
        ctk.CTkLabel(info, textvariable=self.preview_status_var, text_color=BLUE, font=self.font_small, anchor="e").pack(fill="x", padx=10, pady=(1, 7))

        preview_actions = ctk.CTkFrame(panel, fg_color="#DADADA", corner_radius=0, height=46)
        preview_actions.grid(row=3, column=0, sticky="ew", padx=8, pady=(0, 8))
        self.open_source_btn = ctk.CTkButton(preview_actions, text="باز کردن فایل", command=self.open_selected_source, width=105, height=30, fg_color="#EFEFEF", hover_color="#DEDEDE", border_width=1, border_color="#AFAFAF", text_color=TEXT, font=self.font_small)
        self.open_source_btn.pack(side="left", padx=(8, 4), pady=8)
        self.source_folder_btn = ctk.CTkButton(preview_actions, text="پوشه فایل", command=self.open_selected_folder, width=92, height=30, fg_color="#EFEFEF", hover_color="#DEDEDE", border_width=1, border_color="#AFAFAF", text_color=TEXT, font=self.font_small)
        self.source_folder_btn.pack(side="left", padx=4, pady=8)

    def _build_bottom_controls(self, parent):
        bottom = ctk.CTkFrame(parent, fg_color="#CFCFCF", corner_radius=0, height=185, border_width=1, border_color="#A9A9A9")
        bottom.grid(row=3, column=0, sticky="ew")
        bottom.grid_columnconfigure(0, weight=1)

        form = ctk.CTkFrame(bottom, fg_color="transparent")
        form.grid(row=0, column=0, sticky="ew", padx=16, pady=(10, 5))
        form.grid_columnconfigure(1, weight=1)

        ctk.CTkLabel(form, text="پروفایل:", text_color=TEXT, font=self.font_body_bold).grid(row=0, column=0, padx=(0, 7), pady=4, sticky="e")
        profile = ctk.CTkEntry(form, textvariable=self.profile_var, height=34, fg_color="#F8F8F8", border_color="#A4A4A4", text_color="#3D3D3D", font=self.font_body)
        profile.grid(row=0, column=1, sticky="ew", pady=4)
        profile.configure(state="disabled")

        settings_box = ctk.CTkFrame(form, fg_color="transparent")
        settings_box.grid(row=0, column=2, padx=(10, 0), pady=4)
        ctk.CTkLabel(settings_box, text="هدف:", text_color=TEXT, font=self.font_small).pack(side="left", padx=(0, 4))
        self.target_entry = ctk.CTkEntry(settings_box, textvariable=self.target_var, width=76, height=34, fg_color="#F8F8F8", border_color="#A4A4A4", text_color=TEXT, font=self.font_body, justify="center")
        self.target_entry.pack(side="left")
        ctk.CTkLabel(settings_box, text="KB", text_color=MUTED, font=self.font_small).pack(side="left", padx=(4, 9))
        ctk.CTkLabel(settings_box, text="هم‌زمان:", text_color=TEXT, font=self.font_small).pack(side="left", padx=(0, 4))
        self.workers_menu = ctk.CTkOptionMenu(settings_box, variable=self.workers_var, values=["1", "2", "3", "4", "5", "6"], width=66, height=34, fg_color="#E8E8E8", button_color="#AEB4B8", button_hover_color="#9DA4A8", text_color=TEXT, font=self.font_body)
        self.workers_menu.pack(side="left")

        ctk.CTkLabel(form, text="مسیر خروجی:", text_color=TEXT, font=self.font_body_bold).grid(row=1, column=0, padx=(0, 7), pady=4, sticky="e")
        self.output_entry = ctk.CTkEntry(form, textvariable=self.output_var, height=34, fg_color="#F8F8F8", border_color="#A4A4A4", text_color="#444444", font=self.font_small)
        self.output_entry.grid(row=1, column=1, sticky="ew", pady=4)
        self.output_entry.configure(state="disabled")
        path_buttons = ctk.CTkFrame(form, fg_color="transparent")
        path_buttons.grid(row=1, column=2, padx=(10, 0), pady=4)
        self.output_btn = ctk.CTkButton(path_buttons, text="مرور...", command=self.pick_output, width=84, height=34, fg_color="#EEEEEE", hover_color="#DDDDDD", border_width=1, border_color="#A5A5A5", text_color=TEXT, font=self.font_body)
        self.output_btn.pack(side="left", padx=(0, 5))
        self.open_output_btn = ctk.CTkButton(path_buttons, text="باز کردن پوشه", command=self.open_output, width=118, height=34, fg_color="#EEEEEE", hover_color="#DDDDDD", border_width=1, border_color="#A5A5A5", text_color=TEXT, font=self.font_body)
        self.open_output_btn.pack(side="left")

        options_row = ctk.CTkFrame(form, fg_color="transparent")
        options_row.grid(row=2, column=1, columnspan=2, sticky="w", pady=(4, 0))
        self.exif_switch = ctk.CTkSwitch(options_row, text="حفظ EXIF", variable=self.preserve_exif_var, progress_color=BLUE, button_color="#FFFFFF", text_color=TEXT, font=self.font_small)
        self.exif_switch.pack(side="left", padx=(0, 14))
        self.icc_switch = ctk.CTkSwitch(options_row, text="حفظ پروفایل رنگ", variable=self.preserve_icc_var, progress_color=BLUE, button_color="#FFFFFF", text_color=TEXT, font=self.font_small)
        self.icc_switch.pack(side="left")

        action = ctk.CTkFrame(bottom, fg_color="#BEBEBE", corner_radius=0, border_width=1, border_color="#A3A3A3")
        action.grid(row=1, column=0, sticky="ew", pady=(4, 0))
        action.grid_columnconfigure(0, weight=1)

        progress_wrap = ctk.CTkFrame(action, fg_color="transparent")
        progress_wrap.grid(row=0, column=0, sticky="ew", padx=(18, 12), pady=10)
        progress_wrap.grid_columnconfigure(0, weight=1)
        self.progress = ctk.CTkProgressBar(progress_wrap, height=12, corner_radius=5, fg_color="#979797", progress_color=BLUE)
        self.progress.grid(row=0, column=0, sticky="ew")
        self.progress.set(0)
        ctk.CTkLabel(progress_wrap, textvariable=self.progress_text_var, text_color="#4E4E4E", font=self.font_small).grid(row=1, column=0, sticky="w", pady=(5, 0))
        ctk.CTkLabel(progress_wrap, textvariable=self.progress_percent_var, text_color="#146B9C", font=ctk.CTkFont(family="Segoe UI", size=13, weight="bold")).grid(row=0, column=1, padx=(10, 0))

        self.cancel_btn = ctk.CTkButton(action, text="توقف", command=self.cancel_conversion, width=86, height=42, corner_radius=6, fg_color="#D6B5B5", hover_color="#C99B9B", border_width=1, border_color="#A96C6C", text_color="#7C2424", font=self.font_body_bold)
        self.cancel_btn.grid(row=0, column=1, padx=(0, 12), pady=10)

        self.convert_btn = ctk.CTkButton(
            action,
            text="↻\nتبدیل",
            command=self.start_conversion,
            width=118,
            height=88,
            corner_radius=10,
            fg_color=BLUE,
            hover_color=BLUE_HOVER,
            border_width=3,
            border_color="#8FD0F3",
            text_color="white",
            font=ctk.CTkFont(family=self.title_font_family, size=19),
        )
        self.convert_btn.grid(row=0, column=2, rowspan=2, padx=(0, 18), pady=8)

    # ---------- User actions ----------
    def pick_files(self):
        files = filedialog.askopenfilenames(
            title="انتخاب تصاویر",
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
        folder = filedialog.askdirectory(title="انتخاب پوشه تصاویر")
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

        first_new = None
        for p in collected:
            try:
                key = str(p.resolve()).lower()
            except Exception:
                key = str(p).lower()
            if key in existing:
                continue
            existing.add(key)
            if first_new is None:
                first_new = len(self.files)
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

        if first_new is not None:
            iid = self.row_ids[first_new]
            self.tree.selection_set(iid)
            self.tree.focus(iid)
            self._show_preview(first_new)

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
        if self.files:
            self.tree.selection_set(self.row_ids[0])
            self._show_preview(0)
        else:
            self._clear_preview()
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
        self.progress_percent_var.set("۰٪")
        self.progress_text_var.set("آماده برای تبدیل")
        self._clear_preview()
        self._update_stats()
        self._set_controls()

    def move_selected(self, direction):
        if self.busy:
            return
        selected = self.tree.selection()
        if len(selected) != 1:
            return
        iid = selected[0]
        try:
            index = self.row_ids.index(iid)
        except ValueError:
            return
        new_index = index + int(direction)
        if new_index < 0 or new_index >= len(self.files):
            return
        self.files[index], self.files[new_index] = self.files[new_index], self.files[index]
        self.row_ids[index], self.row_ids[new_index] = self.row_ids[new_index], self.row_ids[index]
        self.tree.move(iid, "", new_index)
        self.tree.selection_set(iid)
        self._show_preview(new_index)

    def _selected_index(self):
        selected = self.tree.selection()
        if not selected:
            return None
        try:
            return self.row_ids.index(selected[0])
        except ValueError:
            return None

    def show_selected_properties(self):
        index = self._selected_index()
        if index is None:
            messagebox.showinfo("مشخصات", "ابتدا یک فایل را انتخاب کنید.")
            return
        path = self.files[index]
        try:
            stat = path.stat()
            size_text = human_size(stat.st_size)
        except OSError:
            size_text = "—"
        dimensions = "—"
        try:
            with Image.open(str(path)) as opened:
                dimensions = "%d × %d" % opened.size
        except Exception:
            pass
        messagebox.showinfo(
            "مشخصات فایل",
            "نام: %s\nفرمت: %s\nحجم: %s\nابعاد: %s\nمسیر: %s"
            % (path.name, path.suffix.upper().lstrip("."), size_text, dimensions, str(path)),
        )

    def open_selected_source(self):
        index = self._selected_index()
        if index is None:
            return
        try:
            if os.name == "nt":
                os.startfile(str(self.files[index]))
        except Exception as exc:
            messagebox.showerror("خطا", "فایل باز نشد:\n%s" % exc)

    def open_selected_folder(self):
        index = self._selected_index()
        if index is None:
            return
        try:
            folder = self.files[index].parent
            if os.name == "nt":
                os.startfile(str(folder))
        except Exception as exc:
            messagebox.showerror("خطا", "پوشه باز نشد:\n%s" % exc)

    def _focus_settings(self):
        try:
            self.target_entry.focus_set()
            old = self.target_entry.cget("border_color")
            self.target_entry.configure(border_color=BLUE)
            self.root.after(1200, lambda: self.target_entry.configure(border_color=old))
        except Exception:
            pass

    def _show_help(self):
        messagebox.showinfo(
            "راهنما",
            "۱) فایل یا پوشه را اضافه کنید.\n"
            "۲) حجم هدف را تعیین کنید؛ مقدار پیش‌فرض ۴۸۸KB است.\n"
            "۳) پوشه خروجی را انتخاب کنید.\n"
            "۴) روی دکمه تبدیل بزنید.\n\n"
            "JPGهای زیر حجم هدف بدون فشرده‌سازی مجدد کپی می‌شوند.",
        )

    def _show_about(self):
        messagebox.showinfo(
            "درباره برنامه",
            "%s\nنسخه %s\nمبدل گروهی HEIC / HEIF / JPG / JPEG با موتور Turbo."
            % (APP_NAME, VERSION),
        )

    # ---------- Preview ----------
    def _on_tree_select(self, _event=None):
        index = self._selected_index()
        if index is not None:
            self._show_preview(index)

    def _clear_preview(self):
        self.preview_image = None
        self.current_preview_index = None
        self.preview_label.configure(image=None, text="◉\n\nپیش‌نمایش تصویر", text_color="#A0A0A0")
        self.preview_name_var.set("فایلی انتخاب نشده")
        self.preview_meta_var.set("HEIC / HEIF / JPG / JPEG")
        self.preview_status_var.set("فایل یا پوشه‌ای به صف اضافه کنید")

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

        dimensions = "—"
        try:
            with Image.open(str(path)) as opened:
                image = ImageOps.exif_transpose(opened.copy())
                dimensions = "%d × %d" % image.size
                if image.mode not in ("RGB", "RGBA"):
                    image = image.convert("RGB")
                image.thumbnail((360, 350), Image.LANCZOS)
                self.preview_image = ctk.CTkImage(light_image=image, dark_image=image, size=image.size)
                self.preview_label.configure(image=self.preview_image, text="")
        except Exception:
            self.preview_image = None
            self.preview_label.configure(image=None, text="پیش‌نمایش این فایل\nدر دسترس نیست", text_color="#888888")

        self.preview_meta_var.set("%s  •  %s  •  %s" % (path.suffix.upper().lstrip("."), size_text, dimensions))
        self.preview_status_var.set("آماده برای تبدیل به JPG")

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
        self.queue_stat_var.set("%d فایل" % count)
        if self.busy:
            self.summary_label.configure(text="%d موفق  •  %d ناموفق" % (self.success, self.failed), text_color=BLUE)
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
            self.progress_text_var.set(
                "تمام شد  •  %d موفق  •  %d ناموفق  •  %s کاهش حجم"
                % (self.success, self.failed, human_size(saved))
            )
            if self.failed == 0:
                messagebox.showinfo("تبدیل کامل شد", "%d فایل با موفقیت پردازش شد." % self.success)
            else:
                messagebox.showwarning("پایان پردازش", "%d موفق و %d ناموفق" % (self.success, self.failed))

    def _fatal_error(self, text):
        self.busy = False
        self._set_controls()
        messagebox.showerror("خطای پردازش", text)

    def _set_controls(self):
        idle_state = "disabled" if self.busy else "normal"
        widgets = [
            self.add_files_btn,
            self.add_folder_btn,
            self.remove_btn,
            self.clear_btn,
            self.settings_btn,
            self.output_btn,
            self.properties_btn,
            self.move_up_btn,
            self.move_down_btn,
            self.open_source_btn,
            self.source_folder_btn,
        ]
        for widget in widgets:
            try:
                widget.configure(state=idle_state)
            except Exception:
                pass
        self.target_entry.configure(state=idle_state)
        self.workers_menu.configure(state=idle_state)
        self.exif_switch.configure(state=idle_state)
        self.icc_switch.configure(state=idle_state)
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
    ctk.set_appearance_mode("light")
    ctk.set_default_color_theme("blue")
    root = ctk.CTk()
    ConverterApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
