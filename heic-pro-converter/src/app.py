from __future__ import annotations

import sys
from pathlib import Path

from PySide6.QtCore import QSettings, Qt, QThread, Signal, QUrl
from PySide6.QtGui import QDesktopServices, QDragEnterEvent, QDropEvent, QFont
from PySide6.QtWidgets import (
    QApplication, QCheckBox, QFileDialog, QFrame, QHBoxLayout, QHeaderView,
    QLabel, QMainWindow, QMessageBox, QProgressBar, QPushButton, QSpinBox,
    QTableWidget, QTableWidgetItem, QVBoxLayout, QWidget,
)

from converter import ConvertOptions, SUPPORTED_EXTENSIONS, convert_heic_to_jpg, human_size

APP_NAME = "HEIC Pro Converter"
ORG_NAME = "LocalTools"


class DropFrame(QFrame):
    filesDropped = Signal(list)

    def __init__(self):
        super().__init__()
        self.setAcceptDrops(True)
        self.setObjectName("dropFrame")
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 24, 24, 24)
        title = QLabel("فایل‌های HEIC / HEIF را اینجا رها کنید")
        title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        title.setObjectName("dropTitle")
        sub = QLabel("یا از دکمه‌های انتخاب فایل و پوشه استفاده کنید")
        sub.setAlignment(Qt.AlignmentFlag.AlignCenter)
        sub.setObjectName("muted")
        layout.addWidget(title)
        layout.addWidget(sub)

    def dragEnterEvent(self, event: QDragEnterEvent):
        if event.mimeData().hasUrls():
            event.acceptProposedAction()

    def dropEvent(self, event: QDropEvent):
        paths = [u.toLocalFile() for u in event.mimeData().urls() if u.isLocalFile()]
        if paths:
            self.filesDropped.emit(paths)
        event.acceptProposedAction()


class ConvertThread(QThread):
    fileStarted = Signal(int, str)
    fileFinished = Signal(int, object)
    progressChanged = Signal(int, int)
    allFinished = Signal(int, int, bool)

    def __init__(self, files: list[Path], output_dir: Path, options: ConvertOptions):
        super().__init__()
        self.files = files
        self.output_dir = output_dir
        self.options = options
        self._cancel = False

    def cancel(self):
        self._cancel = True

    def run(self):
        ok_count = 0
        fail_count = 0
        total = len(self.files)
        for i, path in enumerate(self.files):
            if self._cancel:
                break
            self.fileStarted.emit(i, str(path))
            result = convert_heic_to_jpg(
                path, self.output_dir, self.options,
                should_cancel=lambda: self._cancel,
            )
            if result.ok:
                ok_count += 1
            elif result.message != "لغو شد":
                fail_count += 1
            self.fileFinished.emit(i, result)
            self.progressChanged.emit(i + 1, total)
        self.allFinished.emit(ok_count, fail_count, self._cancel)


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.settings = QSettings(ORG_NAME, APP_NAME)
        self.files: list[Path] = []
        self.worker: ConvertThread | None = None
        self.output_dir = Path(
            self.settings.value(
                "output_dir",
                str(Path.home() / "Pictures" / "HEIC_Converted"),
            )
        )
        self.setWindowTitle("HEIC Pro Converter — تبدیل گروهی به JPG")
        self.setMinimumSize(980, 680)
        self.resize(1120, 760)
        self.setAcceptDrops(True)
        self._build_ui()
        self._apply_style()
        self._update_output_label()
        self._update_summary()

    def _build_ui(self):
        root = QWidget()
        self.setCentralWidget(root)
        main = QVBoxLayout(root)
        main.setContentsMargins(24, 20, 24, 20)
        main.setSpacing(14)

        head = QHBoxLayout()
        title_box = QVBoxLayout()
        title = QLabel("HEIC Pro Converter")
        title.setObjectName("appTitle")
        subtitle = QLabel("تبدیل گروهی HEIC/HEIF به JPG با سقف حجم هوشمند")
        subtitle.setObjectName("muted")
        title_box.addWidget(title)
        title_box.addWidget(subtitle)
        head.addLayout(title_box)
        head.addStretch(1)
        self.summary = QLabel("۰ فایل")
        self.summary.setObjectName("pill")
        head.addWidget(self.summary)
        main.addLayout(head)

        self.drop = DropFrame()
        self.drop.filesDropped.connect(self.add_paths)
        main.addWidget(self.drop)

        controls = QHBoxLayout()
        self.add_files_btn = QPushButton("+ انتخاب فایل‌ها")
        self.add_files_btn.clicked.connect(self.pick_files)
        self.add_folder_btn = QPushButton("انتخاب پوشه")
        self.add_folder_btn.clicked.connect(self.pick_folder)
        self.clear_btn = QPushButton("پاک‌کردن لیست")
        self.clear_btn.setObjectName("secondary")
        self.clear_btn.clicked.connect(self.clear_files)
        controls.addWidget(self.add_files_btn)
        controls.addWidget(self.add_folder_btn)
        controls.addWidget(self.clear_btn)
        controls.addStretch(1)
        main.addLayout(controls)

        options = QFrame()
        options.setObjectName("panel")
        opt = QHBoxLayout(options)
        opt.setContentsMargins(16, 12, 16, 12)
        opt.addWidget(QLabel("حداکثر حجم هر JPG:"))
        self.target_spin = QSpinBox()
        self.target_spin.setRange(50, 20000)
        self.target_spin.setValue(int(self.settings.value("target_kb", 488)))
        self.target_spin.setSuffix(" KB")
        opt.addWidget(self.target_spin)
        self.exif_check = QCheckBox("حفظ EXIF")
        self.exif_check.setChecked(self.settings.value("preserve_exif", True, type=bool))
        self.icc_check = QCheckBox("حفظ پروفایل رنگ")
        self.icc_check.setChecked(self.settings.value("preserve_icc", True, type=bool))
        opt.addWidget(self.exif_check)
        opt.addWidget(self.icc_check)
        opt.addStretch(1)
        self.output_btn = QPushButton("پوشه خروجی")
        self.output_btn.setObjectName("secondary")
        self.output_btn.clicked.connect(self.pick_output)
        opt.addWidget(self.output_btn)
        main.addWidget(options)

        self.output_label = QLabel()
        self.output_label.setObjectName("muted")
        self.output_label.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        main.addWidget(self.output_label)

        self.table = QTableWidget(0, 5)
        self.table.setHorizontalHeaderLabels(["فایل", "حجم اولیه", "حجم نهایی", "کیفیت", "وضعیت"])
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.table.verticalHeader().setVisible(False)
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
        for c in (1, 2, 3, 4):
            self.table.horizontalHeader().setSectionResizeMode(c, QHeaderView.ResizeMode.ResizeToContents)
        main.addWidget(self.table, 1)

        self.progress = QProgressBar()
        self.progress.setRange(0, 100)
        self.progress.setValue(0)
        self.progress.setFormat("آماده")
        main.addWidget(self.progress)

        actions = QHBoxLayout()
        self.convert_btn = QPushButton("شروع تبدیل")
        self.convert_btn.setObjectName("primary")
        self.convert_btn.clicked.connect(self.start_conversion)
        self.cancel_btn = QPushButton("توقف")
        self.cancel_btn.setObjectName("danger")
        self.cancel_btn.setEnabled(False)
        self.cancel_btn.clicked.connect(self.cancel_conversion)
        self.open_output_btn = QPushButton("بازکردن پوشه خروجی")
        self.open_output_btn.setObjectName("secondary")
        self.open_output_btn.clicked.connect(self.open_output)
        actions.addWidget(self.convert_btn)
        actions.addWidget(self.cancel_btn)
        actions.addStretch(1)
        actions.addWidget(self.open_output_btn)
        main.addLayout(actions)

    def _apply_style(self):
        self.setStyleSheet("""
            QWidget { font-family: "Segoe UI", "Tahoma"; font-size: 10.5pt; }
            QMainWindow, QWidget { background: #f6f7fb; color: #20242c; }
            QLabel#appTitle { font-size: 24pt; font-weight: 750; }
            QLabel#muted { color: #687080; }
            QLabel#pill { background: #e9edff; color: #3049b8; padding: 7px 12px; border-radius: 12px; font-weight: 650; }
            QFrame#dropFrame { background: #ffffff; border: 2px dashed #b4bdd6; border-radius: 14px; min-height: 92px; }
            QLabel#dropTitle { font-size: 13pt; font-weight: 700; color: #374151; }
            QFrame#panel { background: #ffffff; border: 1px solid #e5e7eb; border-radius: 12px; }
            QPushButton { background: #ffffff; border: 1px solid #d7dce7; border-radius: 9px; padding: 9px 14px; font-weight: 600; }
            QPushButton:hover { background: #f1f4fb; }
            QPushButton#primary { background: #3559e0; color: white; border: none; padding: 11px 22px; }
            QPushButton#primary:hover { background: #2948c7; }
            QPushButton#danger { background: #fff0f0; color: #b42318; border: 1px solid #ffc9c5; }
            QPushButton#secondary { background: #f8f9fc; }
            QPushButton:disabled { color: #9aa1ad; background: #eef0f4; }
            QSpinBox { background: white; border: 1px solid #d7dce7; border-radius: 7px; padding: 6px 8px; min-width: 95px; }
            QTableWidget { background: white; border: 1px solid #e2e6ef; border-radius: 10px; gridline-color: #eef0f4; selection-background-color: #e9edff; selection-color: #20242c; }
            QHeaderView::section { background: #f1f3f8; border: none; border-bottom: 1px solid #dde2ec; padding: 8px; font-weight: 700; }
            QProgressBar { background: #e8ebf1; border: none; border-radius: 7px; height: 14px; text-align: center; }
            QProgressBar::chunk { background: #3559e0; border-radius: 7px; }
        """)

    def dragEnterEvent(self, event: QDragEnterEvent):
        if event.mimeData().hasUrls():
            event.acceptProposedAction()

    def dropEvent(self, event: QDropEvent):
        self.add_paths([u.toLocalFile() for u in event.mimeData().urls() if u.isLocalFile()])
        event.acceptProposedAction()

    def pick_files(self):
        files, _ = QFileDialog.getOpenFileNames(
            self, "انتخاب عکس‌ها", "", "HEIC/HEIF (*.heic *.HEIC *.heif *.HEIF)"
        )
        self.add_paths(files)

    def pick_folder(self):
        folder = QFileDialog.getExistingDirectory(self, "انتخاب پوشه دارای عکس‌های HEIC")
        if folder:
            self.add_paths([folder])

    def pick_output(self):
        folder = QFileDialog.getExistingDirectory(self, "انتخاب پوشه خروجی", str(self.output_dir))
        if folder:
            self.output_dir = Path(folder)
            self.settings.setValue("output_dir", str(self.output_dir))
            self._update_output_label()

    def open_output(self):
        self.output_dir.mkdir(parents=True, exist_ok=True)
        QDesktopServices.openUrl(QUrl.fromLocalFile(str(self.output_dir)))

    def add_paths(self, paths):
        collected: list[Path] = []
        for raw in paths:
            p = Path(raw)
            if p.is_dir():
                try:
                    collected.extend(
                        x for x in p.rglob("*")
                        if x.is_file() and x.suffix.lower() in SUPPORTED_EXTENSIONS
                    )
                except OSError:
                    pass
            elif p.is_file() and p.suffix.lower() in SUPPORTED_EXTENSIONS:
                collected.append(p)

        existing = {str(p.resolve()).lower() for p in self.files}
        new_items: list[Path] = []
        for p in collected:
            try:
                key = str(p.resolve()).lower()
            except OSError:
                key = str(p).lower()
            if key not in existing:
                existing.add(key)
                new_items.append(p)

        self.files.extend(new_items)
        for p in new_items:
            row = self.table.rowCount()
            self.table.insertRow(row)
            name_item = QTableWidgetItem(p.name)
            name_item.setToolTip(str(p))
            try:
                size = human_size(p.stat().st_size)
            except OSError:
                size = "—"
            self.table.setItem(row, 0, name_item)
            self.table.setItem(row, 1, QTableWidgetItem(size))
            self.table.setItem(row, 2, QTableWidgetItem("—"))
            self.table.setItem(row, 3, QTableWidgetItem("—"))
            self.table.setItem(row, 4, QTableWidgetItem("آماده"))
        self._update_summary()

    def clear_files(self):
        if self.worker and self.worker.isRunning():
            return
        self.files.clear()
        self.table.setRowCount(0)
        self.progress.setValue(0)
        self.progress.setFormat("آماده")
        self._update_summary()

    def _update_summary(self):
        self.summary.setText(f"{len(self.files)} فایل")
        busy = bool(self.worker and self.worker.isRunning())
        self.convert_btn.setEnabled(bool(self.files) and not busy)

    def _update_output_label(self):
        self.output_label.setText(f"خروجی: {self.output_dir}")

    def start_conversion(self):
        if not self.files:
            QMessageBox.information(self, "فایلی انتخاب نشده", "حداقل یک فایل HEIC یا HEIF اضافه کنید.")
            return
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.settings.setValue("target_kb", self.target_spin.value())
        self.settings.setValue("preserve_exif", self.exif_check.isChecked())
        self.settings.setValue("preserve_icc", self.icc_check.isChecked())
        options = ConvertOptions(
            target_kb=self.target_spin.value(),
            preserve_exif=self.exif_check.isChecked(),
            preserve_icc=self.icc_check.isChecked(),
        )
        for row in range(self.table.rowCount()):
            self.table.item(row, 2).setText("—")
            self.table.item(row, 3).setText("—")
            self.table.item(row, 4).setText("در صف")

        self.worker = ConvertThread(self.files.copy(), self.output_dir, options)
        self.worker.fileStarted.connect(self.on_file_started)
        self.worker.fileFinished.connect(self.on_file_finished)
        self.worker.progressChanged.connect(self.on_progress)
        self.worker.allFinished.connect(self.on_all_finished)
        self._set_busy(True)
        self.progress.setValue(0)
        self.progress.setFormat("شروع پردازش…")
        self.worker.start()

    def cancel_conversion(self):
        if self.worker and self.worker.isRunning():
            self.worker.cancel()
            self.cancel_btn.setEnabled(False)
            self.progress.setFormat("در حال توقف…")

    def on_file_started(self, index: int, _path: str):
        if index < self.table.rowCount():
            self.table.item(index, 4).setText("در حال تبدیل…")
            self.table.scrollToItem(self.table.item(index, 0))

    def on_file_finished(self, index: int, result):
        if index >= self.table.rowCount():
            return
        if result.ok:
            self.table.item(index, 2).setText(human_size(result.output_bytes))
            suffix = " + Resize" if result.resized else ""
            self.table.item(index, 3).setText(f"Q{result.quality}{suffix}")
            self.table.item(index, 4).setText("✓ انجام شد")
        else:
            self.table.item(index, 4).setText(f"✕ {result.message}")

    def on_progress(self, done: int, total: int):
        pct = int(done * 100 / max(1, total))
        self.progress.setValue(pct)
        self.progress.setFormat(f"{done} از {total} — {pct}%")

    def on_all_finished(self, ok_count: int, fail_count: int, cancelled: bool):
        self._set_busy(False)
        if cancelled:
            self.progress.setFormat(f"متوقف شد — {ok_count} فایل تکمیل شد")
        elif fail_count:
            self.progress.setFormat(f"تمام شد — {ok_count} موفق، {fail_count} ناموفق")
            QMessageBox.warning(
                self, "پایان پردازش",
                f"{ok_count} فایل با موفقیت تبدیل شد و {fail_count} فایل خطا داشت."
            )
        else:
            self.progress.setValue(100)
            self.progress.setFormat(f"تمام شد — {ok_count} فایل با موفقیت تبدیل شد")
            QMessageBox.information(
                self, "انجام شد",
                f"{ok_count} فایل با موفقیت به JPG تبدیل شد.\n\nخروجی:\n{self.output_dir}"
            )

    def _set_busy(self, busy: bool):
        self.convert_btn.setEnabled(not busy and bool(self.files))
        self.cancel_btn.setEnabled(busy)
        self.add_files_btn.setEnabled(not busy)
        self.add_folder_btn.setEnabled(not busy)
        self.clear_btn.setEnabled(not busy)
        self.target_spin.setEnabled(not busy)
        self.exif_check.setEnabled(not busy)
        self.icc_check.setEnabled(not busy)
        self.output_btn.setEnabled(not busy)

    def closeEvent(self, event):
        if self.worker and self.worker.isRunning():
            answer = QMessageBox.question(
                self, "خروج از برنامه",
                "تبدیل هنوز در حال انجام است. پردازش متوقف و برنامه بسته شود؟",
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
                QMessageBox.StandardButton.No,
            )
            if answer != QMessageBox.StandardButton.Yes:
                event.ignore()
                return
            self.worker.cancel()
            self.worker.wait(4000)
        event.accept()


def main():
    app = QApplication(sys.argv)
    app.setApplicationName(APP_NAME)
    app.setOrganizationName(ORG_NAME)
    app.setLayoutDirection(Qt.LayoutDirection.RightToLeft)
    app.setFont(QFont("Segoe UI", 10))
    window = MainWindow()
    window.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
