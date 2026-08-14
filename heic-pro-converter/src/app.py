import json
import os
import queue
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import tkinter as tk
from tkinter import filedialog, messagebox, ttk

from converter import ConvertOptions, SUPPORTED_EXTENSIONS, convert_image_to_jpg, human_size

APP_NAME = "HEIC Pro Converter"
VERSION = "3.0 Turbo"
BG = "#F4F7FB"
NAVY = "#111827"
TEXT = "#162033"
MUTED = "#667085"
BLUE = "#2563EB"
BLUE_DARK = "#1D4ED8"
GREEN = "#16A34A"
RED = "#DC2626"
CARD = "#FFFFFF"
BORDER = "#E6EAF0"


class ConverterApp:
    def __init__(self, root):
        self.root = root
        self.root.title("HEIC Pro Converter — Turbo")
        self.root.geometry("1180x790")
        self.root.minsize(980, 650)
        self.root.configure(bg=BG)

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
        self.workers_var = tk.IntVar(value=int(self.settings.get("workers", suggested_workers)))
        self.preserve_exif_var = tk.BooleanVar(value=bool(self.settings.get("preserve_exif", True)))
        self.preserve_icc_var = tk.BooleanVar(value=bool(self.settings.get("preserve_icc", True)))
        self.output_var = tk.StringVar()
        self.progress_text_var = tk.StringVar(value="آماده برای پردازش")
        self.total_stat_var = tk.StringVar(value="0")
        self.done_stat_var = tk.StringVar(value="0")
        self.saved_stat_var = tk.StringVar(value="0 KB")
        self.speed_stat_var = tk.StringVar(value="—")

        self._configure_styles()
        self._build_ui()
        self._update_output_label()
        self._update_stats()

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

    def _configure_styles(self):
        style = ttk.Style(self.root)
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass
        self.root.option_add("*Font", "Tahoma 10")
        style.configure("Treeview", rowheight=34, background=CARD, fieldbackground=CARD,
                        foreground=TEXT, borderwidth=0, relief="flat")
        style.configure("Treeview.Heading", font=("Tahoma", 9, "bold"), background="#EEF2F7",
                        foreground="#344054", relief="flat", padding=(6, 8))
        style.map("Treeview", background=[("selected", "#E7EEFF")], foreground=[("selected", TEXT)])
        style.configure("Turbo.Horizontal.TProgressbar", troughcolor="#E8EDF5", background=BLUE,
                        bordercolor="#E8EDF5", lightcolor=BLUE, darkcolor=BLUE, thickness=15)
        style.configure("TCheckbutton", background=CARD, foreground=TEXT)
        style.configure("TSpinbox", padding=(5, 5))

    def _button(self, parent, text, command, kind="secondary", width=None):
        colors = {
            "primary": (BLUE, BLUE_DARK, "white"),
            "danger": ("#FEE2E2", "#FECACA", RED),
            "secondary": ("#F8FAFC", "#EEF2F7", TEXT),
            "success": ("#DCFCE7", "#BBF7D0", GREEN),
        }
        normal, hover, fg = colors[kind]
        btn = tk.Button(parent, text=text, command=command, bg=normal, fg=fg,
                        activebackground=hover, activeforeground=fg, relief="flat",
                        bd=0, padx=16, pady=9, cursor="hand2", font=("Tahoma", 9, "bold"))
        if width:
            btn.configure(width=width)
        btn.bind("<Enter>", lambda _e: btn.configure(bg=hover))
        btn.bind("<Leave>", lambda _e: btn.configure(bg=normal))
        return btn

    def _card(self, parent, bg=CARD):
        return tk.Frame(parent, bg=bg, highlightthickness=1, highlightbackground=BORDER, bd=0)

    def _build_ui(self):
        header = tk.Frame(self.root, bg=NAVY, height=104)
        header.pack(fill="x")
        header.pack_propagate(False)

        head_inner = tk.Frame(header, bg=NAVY)
        head_inner.pack(fill="both", expand=True, padx=28, pady=18)
        title_col = tk.Frame(head_inner, bg=NAVY)
        title_col.pack(side="left", fill="both", expand=True)
        tk.Label(title_col, text="HEIC Pro Converter", bg=NAVY, fg="white",
                 font=("Segoe UI", 23, "bold")).pack(anchor="w")
        tk.Label(title_col, text="تبدیل و کاهش حجم گروهی با موتور Turbo",
                 bg=NAVY, fg="#AAB6C8", font=("Tahoma", 9)).pack(anchor="w", pady=(4, 0))
        badge = tk.Label(head_inner, text="  V3  TURBO  ", bg="#1E3A8A", fg="#BFDBFE",
                         font=("Segoe UI", 9, "bold"), padx=10, pady=7)
        badge.pack(side="right", pady=10)

        body = tk.Frame(self.root, bg=BG)
        body.pack(fill="both", expand=True, padx=24, pady=18)

        stats = tk.Frame(body, bg=BG)
        stats.pack(fill="x", pady=(0, 12))
        stat_defs = [
            ("کل فایل‌ها", self.total_stat_var, "#EFF6FF", "#1D4ED8"),
            ("انجام‌شده", self.done_stat_var, "#F0FDF4", "#15803D"),
            ("کاهش حجم", self.saved_stat_var, "#FAF5FF", "#7E22CE"),
            ("سرعت", self.speed_stat_var, "#FFF7ED", "#C2410C"),
        ]
        for i, (label, var, bg, fg) in enumerate(stat_defs):
            card = tk.Frame(stats, bg=bg, highlightthickness=1, highlightbackground=BORDER)
            card.grid(row=0, column=i, sticky="nsew", padx=(0 if i == 0 else 5, 0 if i == 3 else 5))
            tk.Label(card, text=label, bg=bg, fg=MUTED, font=("Tahoma", 8)).pack(anchor="w", padx=14, pady=(10, 2))
            tk.Label(card, textvariable=var, bg=bg, fg=fg, font=("Segoe UI", 16, "bold")).pack(anchor="w", padx=14, pady=(0, 10))
            stats.columnconfigure(i, weight=1)

        chooser = self._card(body)
        chooser.pack(fill="x", pady=(0, 12))
        left = tk.Frame(chooser, bg=CARD)
        left.pack(side="left", fill="x", expand=True, padx=16, pady=14)
        tk.Label(left, text="عکس‌ها را برای پردازش اضافه کنید", bg=CARD, fg=TEXT,
                 font=("Tahoma", 11, "bold")).pack(anchor="w")
        tk.Label(left, text="HEIC / HEIF / JPG / JPEG — JPGهای زیر حجم هدف بدون افت کیفیت کپی می‌شوند",
                 bg=CARD, fg=MUTED, font=("Tahoma", 8)).pack(anchor="w", pady=(4, 0))
        right = tk.Frame(chooser, bg=CARD)
        right.pack(side="right", padx=14, pady=12)
        self.add_files_btn = self._button(right, "+  انتخاب فایل‌ها", self.pick_files, "primary")
        self.add_files_btn.pack(side="left", padx=(0, 8))
        self.add_folder_btn = self._button(right, "انتخاب پوشه", self.pick_folder)
        self.add_folder_btn.pack(side="left")

        options = self._card(body)
        options.pack(fill="x", pady=(0, 12))
        opt = tk.Frame(options, bg=CARD)
        opt.pack(fill="x", padx=14, pady=12)
        tk.Label(opt, text="حجم خروجی", bg=CARD, fg=MUTED, font=("Tahoma", 8)).pack(side="left", padx=(0, 5))
        self.target_spin = ttk.Spinbox(opt, from_=50, to=20000, textvariable=self.target_var, width=7, justify="center")
        self.target_spin.pack(side="left")
        tk.Label(opt, text="KB", bg=CARD, fg=TEXT).pack(side="left", padx=(4, 18))
        tk.Label(opt, text="پردازش هم‌زمان", bg=CARD, fg=MUTED, font=("Tahoma", 8)).pack(side="left", padx=(0, 5))
        self.workers_spin = ttk.Spinbox(opt, from_=1, to=6, textvariable=self.workers_var, width=4, justify="center")
        self.workers_spin.pack(side="left")
        tk.Label(opt, text="فایل", bg=CARD, fg=TEXT).pack(side="left", padx=(4, 18))
        self.exif_check = ttk.Checkbutton(opt, text="حفظ EXIF", variable=self.preserve_exif_var)
        self.exif_check.pack(side="left", padx=(0, 10))
        self.icc_check = ttk.Checkbutton(opt, text="حفظ رنگ", variable=self.preserve_icc_var)
        self.icc_check.pack(side="left")
        self.output_btn = self._button(opt, "تغییر پوشه خروجی", self.pick_output)
        self.output_btn.pack(side="right")

        pathbar = tk.Frame(body, bg="#EEF4FF", highlightthickness=1, highlightbackground="#D7E3FF")
        pathbar.pack(fill="x", pady=(0, 10))
        tk.Label(pathbar, text="خروجی:", bg="#EEF4FF", fg=BLUE_DARK, font=("Tahoma", 8, "bold")).pack(side="left", padx=(12, 6), pady=8)
        tk.Label(pathbar, textvariable=self.output_var, bg="#EEF4FF", fg="#475467",
                 font=("Tahoma", 8), anchor="w").pack(side="left", fill="x", expand=True, pady=8)
        self.open_output_btn = self._button(pathbar, "باز کردن", self.open_output, "success")
        self.open_output_btn.pack(side="right", padx=8, pady=5)

        table_card = self._card(body)
        table_card.pack(fill="both", expand=True, pady=(0, 10))
        columns = ("type", "input", "output", "quality", "status")
        self.tree = ttk.Treeview(table_card, columns=columns, show="tree headings", selectmode="extended")
        self.tree.heading("#0", text="نام فایل")
        self.tree.heading("type", text="فرمت")
        self.tree.heading("input", text="حجم اولیه")
        self.tree.heading("output", text="حجم نهایی")
        self.tree.heading("quality", text="کیفیت")
        self.tree.heading("status", text="وضعیت")
        self.tree.column("#0", width=340, minwidth=190, stretch=True, anchor="w")
        self.tree.column("type", width=70, minwidth=60, stretch=False, anchor="center")
        self.tree.column("input", width=100, minwidth=90, stretch=False, anchor="center")
        self.tree.column("output", width=100, minwidth=90, stretch=False, anchor="center")
        self.tree.column("quality", width=75, minwidth=65, stretch=False, anchor="center")
        self.tree.column("status", width=270, minwidth=180, stretch=True, anchor="center")
        self.tree.tag_configure("success", foreground="#137333")
        self.tree.tag_configure("error", foreground="#B42318")
        self.tree.tag_configure("running", foreground=BLUE_DARK)
        scroll_y = ttk.Scrollbar(table_card, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=scroll_y.set)
        self.tree.grid(row=0, column=0, sticky="nsew", padx=(1, 0), pady=1)
        scroll_y.grid(row=0, column=1, sticky="ns")
        table_card.rowconfigure(0, weight=1)
        table_card.columnconfigure(0, weight=1)

        progress_card = tk.Frame(body, bg=BG)
        progress_card.pack(fill="x", pady=(0, 9))
        self.progress = ttk.Progressbar(progress_card, orient="horizontal", mode="determinate",
                                        maximum=100, style="Turbo.Horizontal.TProgressbar")
        self.progress.pack(side="left", fill="x", expand=True)
        tk.Label(progress_card, textvariable=self.progress_text_var, bg=BG, fg=MUTED,
                 font=("Tahoma", 8)).pack(side="right", padx=(12, 0))

        actions = tk.Frame(body, bg=BG)
        actions.pack(fill="x")
        self.convert_btn = self._button(actions, "⚡  شروع پردازش Turbo", self.start_conversion, "primary")
        self.convert_btn.pack(side="left")
        self.cancel_btn = self._button(actions, "توقف", self.cancel_conversion, "danger")
        self.cancel_btn.pack(side="left", padx=(8, 0))
        self.cancel_btn.configure(state="disabled")
        self.clear_btn = self._button(actions, "پاک کردن لیست", self.clear_files)
        self.clear_btn.pack(side="right")
        self.remove_btn = self._button(actions, "حذف انتخاب‌شده", self.remove_selected)
        self.remove_btn.pack(side="right", padx=(0, 8))

        tk.Label(body, text="V3 Turbo — بدون Qt | پردازش موازی | سازگارتر با ویندوزهای قدیمی",
                 bg=BG, fg="#98A2B3", font=("Tahoma", 7)).pack(anchor="e", pady=(8, 0))

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
            value = int(self.workers_var.get())
            if value < 1 or value > 6:
                raise ValueError()
            return value
        except Exception:
            if not silent:
                messagebox.showerror("مقدار نامعتبر", "پردازش هم‌زمان باید بین 1 تا 6 فایل باشد.")
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
            iid = self.tree.insert("", "end", text=p.name,
                                   values=(p.suffix.upper().lstrip("."), size_text, "—", "—", "آماده"))
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
        self.progress.configure(value=0)
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
        self.progress.configure(value=0)
        self.progress_text_var.set("شروع موتور Turbo…")
        for iid in self.row_ids:
            self.tree.set(iid, "output", "—")
            self.tree.set(iid, "quality", "—")
            self.tree.set(iid, "status", "در صف")
            self.tree.item(iid, tags=())
        self._set_controls()
        self._update_stats()

        options = ConvertOptions(target_kb=target,
                                 preserve_exif=bool(self.preserve_exif_var.get()),
                                 preserve_icc=bool(self.preserve_icc_var.get()))
        self.worker = threading.Thread(target=self._process_batch,
                                       args=(list(self.files), options, workers), daemon=True)
        self.worker.start()

    def _process_batch(self, files, options, workers):
        def run_one(index, path):
            if self.cancel_event.is_set():
                return index, None
            self.events.put(("started", index))
            result = convert_image_to_jpg(path, self.output_dir, options,
                                          should_cancel=self.cancel_event.is_set)
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
        percent = min(100.0, (self.completed * 100.0) / total)
        self.progress.configure(value=percent)
        elapsed = max(0.001, time.time() - self.batch_started)
        rate = self.completed / elapsed
        self.progress_text_var.set("%d از %d  •  %.1f فایل/ثانیه" % (self.completed, len(self.files), rate))
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
            self.progress.configure(value=100)
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
        self.add_files_btn.configure(state=disabled if self.busy else normal)
        self.add_folder_btn.configure(state=disabled if self.busy else normal)
        self.output_btn.configure(state=disabled if self.busy else normal)
        self.remove_btn.configure(state=disabled if self.busy else normal)
        self.clear_btn.configure(state=disabled if self.busy else normal)
        self.convert_btn.configure(state=normal if (self.files and not self.busy) else disabled)
        self.cancel_btn.configure(state=normal if self.busy else disabled)
        self.target_spin.configure(state=disabled if self.busy else normal)
        self.workers_spin.configure(state=disabled if self.busy else normal)

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
    root = tk.Tk()
    ConverterApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
