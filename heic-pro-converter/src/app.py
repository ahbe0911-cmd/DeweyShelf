import ctypes
import json
import os
import queue
import sys
import threading
from pathlib import Path

import tkinter as tk
from tkinter import filedialog, messagebox, ttk

from converter import (
    ConvertOptions,
    SUPPORTED_EXTENSIONS,
    convert_image_to_jpg,
    human_size,
)

APP_NAME = "HEIC Pro Converter"
VERSION = "2.0 Legacy Compatible"


class ConverterApp:
    def __init__(self, root):
        self.root = root
        self.root.title("HEIC Pro Converter — تبدیل و کاهش حجم عکس")
        self.root.geometry("1050x720")
        self.root.minsize(880, 610)
        self.root.configure(bg="#f4f6fa")

        self.files = []
        self.row_ids = []
        self.events = queue.Queue()
        self.cancel_event = threading.Event()
        self.worker = None
        self.busy = False

        self.settings_path = self._settings_path()
        self.settings = self._load_settings()
        default_output = str(Path.home() / "Pictures" / "HEIC_Converted")
        self.output_dir = Path(self.settings.get("output_dir", default_output))

        self.target_var = tk.StringVar(value=str(self.settings.get("target_kb", 488)))
        self.preserve_exif_var = tk.BooleanVar(value=bool(self.settings.get("preserve_exif", True)))
        self.preserve_icc_var = tk.BooleanVar(value=bool(self.settings.get("preserve_icc", True)))
        self.summary_var = tk.StringVar(value="0 فایل")
        self.output_var = tk.StringVar()
        self.progress_text_var = tk.StringVar(value="آماده")

        self._configure_styles()
        self._build_ui()
        self._update_output_label()
        self._update_summary()

        self.root.protocol("WM_DELETE_WINDOW", self._on_close)
        self.root.after(100, self._poll_events)

        # Windows lets users drop files/folders on the EXE icon itself. Those
        # paths arrive as command line arguments, so preload them here.
        if len(sys.argv) > 1:
            self.root.after(250, lambda: self.add_paths(sys.argv[1:]))

    def _settings_path(self):
        base = os.environ.get("APPDATA") or str(Path.home())
        folder = Path(base) / "HEICProConverter"
        try:
            folder.mkdir(parents=True, exist_ok=True)
        except OSError:
            return Path.home() / ".heic_pro_converter_settings.json"
        return folder / "settings.json"

    def _load_settings(self):
        try:
            with open(str(self.settings_path), "r", encoding="utf-8") as handle:
                data = json.load(handle)
                if isinstance(data, dict):
                    return data
        except Exception:
            pass
        return {}

    def _save_settings(self):
        data = {
            "output_dir": str(self.output_dir),
            "target_kb": self._target_kb(silent=True),
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
        style.configure("TFrame", background="#f4f6fa")
        style.configure("Card.TFrame", background="#ffffff")
        style.configure("Title.TLabel", background="#f4f6fa", foreground="#182033", font=("Tahoma", 20, "bold"))
        style.configure("Subtitle.TLabel", background="#f4f6fa", foreground="#667085", font=("Tahoma", 9))
        style.configure("Card.TLabel", background="#ffffff", foreground="#202939")
        style.configure("MutedCard.TLabel", background="#ffffff", foreground="#697386")
        style.configure("Summary.TLabel", background="#e8edff", foreground="#2f4cc8", padding=(10, 6), font=("Tahoma", 9, "bold"))
        style.configure("Primary.TButton", font=("Tahoma", 10, "bold"), padding=(15, 9))
        style.configure("Secondary.TButton", padding=(12, 8))
        style.configure("Danger.TButton", padding=(12, 8))
        style.configure("Treeview", rowheight=30, background="#ffffff", fieldbackground="#ffffff", foreground="#202939", borderwidth=0)
        style.configure("Treeview.Heading", font=("Tahoma", 9, "bold"), background="#edf1f7", foreground="#344054", relief="flat")
        style.map("Treeview", background=[("selected", "#dfe7ff")], foreground=[("selected", "#182033")])
        style.configure("Horizontal.TProgressbar", troughcolor="#e5e9f1", background="#4263eb", thickness=14)

    def _build_ui(self):
        outer = ttk.Frame(self.root, padding=(22, 18, 22, 18))
        outer.pack(fill="both", expand=True)

        header = ttk.Frame(outer)
        header.pack(fill="x", pady=(0, 12))
        title_box = ttk.Frame(header)
        title_box.pack(side="left", fill="x", expand=True)
        ttk.Label(title_box, text="HEIC Pro Converter", style="Title.TLabel").pack(anchor="w")
        ttk.Label(
            title_box,
            text="HEIC / HEIF / JPG / JPEG  →  JPG  |  بیشترین کیفیت با سقف حجم هوشمند",
            style="Subtitle.TLabel",
        ).pack(anchor="w", pady=(3, 0))
        ttk.Label(header, textvariable=self.summary_var, style="Summary.TLabel").pack(side="right", padx=(10, 0))

        chooser = ttk.Frame(outer, style="Card.TFrame", padding=(16, 14))
        chooser.pack(fill="x", pady=(0, 12))

        choose_text = ttk.Frame(chooser, style="Card.TFrame")
        choose_text.pack(side="left", fill="x", expand=True)
        ttk.Label(
            choose_text,
            text="عکس‌ها را اضافه کنید",
            style="Card.TLabel",
            font=("Tahoma", 11, "bold"),
        ).pack(anchor="w")
        ttk.Label(
            choose_text,
            text="فایل JPG هم بدون خطا پذیرفته می‌شود؛ اگر زیر حجم هدف باشد دوباره فشرده نمی‌شود.",
            style="MutedCard.TLabel",
        ).pack(anchor="w", pady=(4, 0))

        button_box = ttk.Frame(chooser, style="Card.TFrame")
        button_box.pack(side="right")
        self.add_files_btn = ttk.Button(button_box, text="انتخاب فایل‌ها", style="Primary.TButton", command=self.pick_files)
        self.add_files_btn.pack(side="left", padx=(0, 7))
        self.add_folder_btn = ttk.Button(button_box, text="انتخاب پوشه", style="Secondary.TButton", command=self.pick_folder)
        self.add_folder_btn.pack(side="left")

        options = ttk.Frame(outer, style="Card.TFrame", padding=(16, 12))
        options.pack(fill="x", pady=(0, 10))

        ttk.Label(options, text="حداکثر حجم خروجی:", style="Card.TLabel").pack(side="left", padx=(0, 7))
        self.target_spin = ttk.Spinbox(options, from_=50, to=20000, textvariable=self.target_var, width=8, justify="center")
        self.target_spin.pack(side="left")
        ttk.Label(options, text="KB", style="Card.TLabel").pack(side="left", padx=(4, 18))

        self.exif_check = ttk.Checkbutton(options, text="حفظ EXIF", variable=self.preserve_exif_var)
        self.exif_check.pack(side="left", padx=(0, 10))
        self.icc_check = ttk.Checkbutton(options, text="حفظ پروفایل رنگ", variable=self.preserve_icc_var)
        self.icc_check.pack(side="left")

        self.output_btn = ttk.Button(options, text="پوشه خروجی", style="Secondary.TButton", command=self.pick_output)
        self.output_btn.pack(side="right")

        path_card = ttk.Frame(outer, style="Card.TFrame", padding=(12, 8))
        path_card.pack(fill="x", pady=(0, 10))
        ttk.Label(path_card, textvariable=self.output_var, style="MutedCard.TLabel").pack(side="left", fill="x", expand=True)
        self.open_output_btn = ttk.Button(path_card, text="باز کردن", style="Secondary.TButton", command=self.open_output)
        self.open_output_btn.pack(side="right")

        table_card = ttk.Frame(outer, style="Card.TFrame")
        table_card.pack(fill="both", expand=True, pady=(0, 10))

        columns = ("type", "input", "output", "quality", "status")
        self.tree = ttk.Treeview(table_card, columns=columns, show="tree headings", selectmode="extended")
        self.tree.heading("#0", text="نام فایل")
        self.tree.heading("type", text="فرمت")
        self.tree.heading("input", text="حجم اولیه")
        self.tree.heading("output", text="حجم نهایی")
        self.tree.heading("quality", text="کیفیت")
        self.tree.heading("status", text="وضعیت")
        self.tree.column("#0", width=300, minwidth=180, stretch=True, anchor="w")
        self.tree.column("type", width=70, minwidth=60, stretch=False, anchor="center")
        self.tree.column("input", width=105, minwidth=90, stretch=False, anchor="center")
        self.tree.column("output", width=105, minwidth=90, stretch=False, anchor="center")
        self.tree.column("quality", width=75, minwidth=65, stretch=False, anchor="center")
        self.tree.column("status", width=250, minwidth=170, stretch=True, anchor="center")

        scroll_y = ttk.Scrollbar(table_card, orient="vertical", command=self.tree.yview)
        scroll_x = ttk.Scrollbar(table_card, orient="horizontal", command=self.tree.xview)
        self.tree.configure(yscrollcommand=scroll_y.set, xscrollcommand=scroll_x.set)
        self.tree.grid(row=0, column=0, sticky="nsew")
        scroll_y.grid(row=0, column=1, sticky="ns")
        scroll_x.grid(row=1, column=0, sticky="ew")
        table_card.rowconfigure(0, weight=1)
        table_card.columnconfigure(0, weight=1)

        progress_card = ttk.Frame(outer)
        progress_card.pack(fill="x", pady=(0, 10))
        self.progress = ttk.Progressbar(progress_card, orient="horizontal", mode="determinate", maximum=100)
        self.progress.pack(side="left", fill="x", expand=True)
        ttk.Label(progress_card, textvariable=self.progress_text_var, style="Subtitle.TLabel").pack(side="right", padx=(12, 0))

        actions = ttk.Frame(outer)
        actions.pack(fill="x")
        self.convert_btn = ttk.Button(actions, text="شروع تبدیل", style="Primary.TButton", command=self.start_conversion)
        self.convert_btn.pack(side="left")
        self.cancel_btn = ttk.Button(actions, text="توقف", style="Danger.TButton", command=self.cancel_conversion, state="disabled")
        self.cancel_btn.pack(side="left", padx=(8, 0))
        self.remove_btn = ttk.Button(actions, text="حذف انتخاب‌شده", style="Secondary.TButton", command=self.remove_selected)
        self.remove_btn.pack(side="right")
        self.clear_btn = ttk.Button(actions, text="پاک کردن لیست", style="Secondary.TButton", command=self.clear_files)
        self.clear_btn.pack(side="right", padx=(0, 8))

        footer = ttk.Label(
            outer,
            text="نسخه %s — بدون Qt، مناسب‌تر برای ویندوزهای قدیمی" % VERSION,
            style="Subtitle.TLabel",
        )
        footer.pack(anchor="e", pady=(8, 0))

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

        added = 0
        for p in collected:
            try:
                key = str(p.resolve()).lower()
            except Exception:
                key = str(p).lower()
            if key in existing:
                continue
            existing.add(key)
            self.files.append(p)
            added += 1

            try:
                size_text = human_size(p.stat().st_size)
            except OSError:
                size_text = "—"
            ext_text = p.suffix.upper().lstrip(".")
            iid = self.tree.insert(
                "",
                "end",
                text=p.name,
                values=(ext_text, size_text, "—", "—", "آماده"),
            )
            self.row_ids.append(iid)

        self._update_summary()
        if collected and added == 0:
            self.progress_text_var.set("فایل جدیدی اضافه نشد")
        elif added:
            self.progress_text_var.set("%d فایل اضافه شد" % added)

    def remove_selected(self):
        if self.busy:
            return
        selected = set(self.tree.selection())
        if not selected:
            return
        new_files = []
        new_rows = []
        for p, iid in zip(self.files, self.row_ids):
            if iid in selected:
                try:
                    self.tree.delete(iid)
                except tk.TclError:
                    pass
            else:
                new_files.append(p)
                new_rows.append(iid)
        self.files = new_files
        self.row_ids = new_rows
        self._update_summary()

    def clear_files(self):
        if self.busy:
            return
        for iid in self.row_ids:
            try:
                self.tree.delete(iid)
            except tk.TclError:
                pass
        self.files = []
        self.row_ids = []
        self.progress["value"] = 0
        self.progress_text_var.set("آماده")
        self._update_summary()

    def _update_summary(self):
        self.summary_var.set("%d فایل" % len(self.files))
        if not self.busy:
            self.convert_btn.configure(state=("normal" if self.files else "disabled"))

    def _update_output_label(self):
        self.output_var.set("خروجی: %s" % self.output_dir)

    def _set_busy(self, busy):
        self.busy = bool(busy)
        normal_or_disabled = "disabled" if busy else "normal"
        for widget in (self.add_files_btn, self.add_folder_btn, self.output_btn, self.clear_btn, self.remove_btn):
            widget.configure(state=normal_or_disabled)
        self.target_spin.configure(state=normal_or_disabled)
        self.exif_check.configure(state=normal_or_disabled)
        self.icc_check.configure(state=normal_or_disabled)
        self.convert_btn.configure(state="disabled" if busy or not self.files else "normal")
        self.cancel_btn.configure(state="normal" if busy else "disabled")

    def start_conversion(self):
        if self.busy:
            return
        if not self.files:
            messagebox.showinfo("فایلی انتخاب نشده", "حداقل یک عکس اضافه کنید.")
            return

        target_kb = self._target_kb()
        try:
            self.output_dir.mkdir(parents=True, exist_ok=True)
        except Exception as exc:
            messagebox.showerror("خطا", "پوشه خروجی ساخته نشد:\n%s" % exc)
            return

        options = ConvertOptions(
            target_kb=target_kb,
            preserve_exif=bool(self.preserve_exif_var.get()),
            preserve_icc=bool(self.preserve_icc_var.get()),
        )
        self._save_settings()

        for iid in self.row_ids:
            old = list(self.tree.item(iid, "values"))
            while len(old) < 5:
                old.append("—")
            old[2] = "—"
            old[3] = "—"
            old[4] = "در صف"
            self.tree.item(iid, values=old)

        self.cancel_event.clear()
        self.progress["value"] = 0
        self.progress_text_var.set("شروع پردازش…")
        self._set_busy(True)
        files_snapshot = list(self.files)
        self.worker = threading.Thread(
            target=self._worker_loop,
            args=(files_snapshot, self.output_dir, options),
            name="image-converter",
        )
        self.worker.daemon = True
        self.worker.start()

    def _worker_loop(self, files, output_dir, options):
        ok_count = 0
        fail_count = 0
        total = len(files)
        for index, path in enumerate(files):
            if self.cancel_event.is_set():
                break
            self.events.put(("start", index))
            result = convert_image_to_jpg(
                path,
                output_dir,
                options,
                should_cancel=self.cancel_event.is_set,
            )
            if result.ok:
                ok_count += 1
            elif result.message != "لغو شد":
                fail_count += 1
            self.events.put(("finish", index, result))
            self.events.put(("progress", index + 1, total))

        self.events.put(("done", ok_count, fail_count, self.cancel_event.is_set()))

    def cancel_conversion(self):
        if self.busy:
            self.cancel_event.set()
            self.cancel_btn.configure(state="disabled")
            self.progress_text_var.set("در حال توقف…")

    def _poll_events(self):
        try:
            while True:
                event = self.events.get_nowait()
                kind = event[0]
                if kind == "start":
                    self._on_start(event[1])
                elif kind == "finish":
                    self._on_finish(event[1], event[2])
                elif kind == "progress":
                    self._on_progress(event[1], event[2])
                elif kind == "done":
                    self._on_done(event[1], event[2], event[3])
        except queue.Empty:
            pass
        self.root.after(100, self._poll_events)

    def _on_start(self, index):
        if index >= len(self.row_ids):
            return
        iid = self.row_ids[index]
        values = list(self.tree.item(iid, "values"))
        if len(values) >= 5:
            values[4] = "در حال پردازش…"
            self.tree.item(iid, values=values)
            self.tree.see(iid)

    def _on_finish(self, index, result):
        if index >= len(self.row_ids):
            return
        iid = self.row_ids[index]
        values = list(self.tree.item(iid, "values"))
        while len(values) < 5:
            values.append("—")

        if result.ok:
            values[2] = human_size(result.output_bytes)
            values[3] = "اصل" if result.copied_original else ("Q%d" % result.quality)
            if result.copied_original:
                values[4] = "بدون افت کیفیت"
            elif result.resized:
                values[4] = "انجام شد • ابعاد کمی کاهش یافت"
            else:
                values[4] = "انجام شد"
        else:
            values[2] = "—"
            values[3] = "—"
            values[4] = result.message or "ناموفق"
        self.tree.item(iid, values=values)

    def _on_progress(self, done, total):
        percent = int(round((done * 100.0) / max(1, total)))
        self.progress["value"] = percent
        self.progress_text_var.set("%d از %d  •  %d%%" % (done, total, percent))

    def _on_done(self, ok_count, fail_count, cancelled):
        self.worker = None
        self._set_busy(False)
        self._update_summary()
        if cancelled:
            self.progress_text_var.set("متوقف شد")
            return

        self.progress["value"] = 100
        self.progress_text_var.set("پایان • %d موفق • %d ناموفق" % (ok_count, fail_count))
        if fail_count:
            messagebox.showwarning(
                "پردازش تمام شد",
                "%d فایل با موفقیت پردازش شد و %d فایل خطا داشت.\nجزئیات در ستون وضعیت نوشته شده است." % (ok_count, fail_count),
            )
        else:
            messagebox.showinfo(
                "انجام شد",
                "هر %d فایل با موفقیت پردازش شد.\n\nپوشه خروجی:\n%s" % (ok_count, self.output_dir),
            )

    def _on_close(self):
        if self.busy:
            if not messagebox.askyesno("خروج", "پردازش در حال انجام است. برنامه بسته شود؟"):
                return
            self.cancel_event.set()
        self._save_settings()
        self.root.destroy()


def enable_windows_dpi_awareness():
    if os.name != "nt":
        return
    try:
        ctypes.windll.shcore.SetProcessDpiAwareness(1)
        return
    except Exception:
        pass
    try:
        ctypes.windll.user32.SetProcessDPIAware()
    except Exception:
        pass


def main():
    enable_windows_dpi_awareness()
    root = tk.Tk()
    ConverterApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
