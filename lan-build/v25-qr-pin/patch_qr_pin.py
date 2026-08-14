from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else 'buildsrc/lan_file_transfer_v1').resolve()


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding='utf-8')


def write(rel: str, text: str) -> None:
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding='utf-8')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f'patch marker missing: {label}')
    return text.replace(old, new, 1)


# ---------- PC config: persisted custom PIN ----------
rel = 'pc/lantransfer/config.py'
s = read(rel)
if 'custom_pin: str = ""' not in s:
    s = replace_once(
        s,
        '    tls_keyfile: str | None = None\n',
        '    tls_keyfile: str | None = None\n    custom_pin: str = ""\n',
        'config custom_pin',
    )
write(rel, s)


# ---------- PC startup: mutable AppContext PIN + persistence callback ----------
rel = 'pc/app.py'
s = read(rel)
if 'from dataclasses import asdict' not in s:
    s = s.replace('import uuid\n', 'import uuid\nfrom dataclasses import asdict\n', 1)

s = replace_once(
    s,
    '    config = AppConfig.load(base / "config.json")\n    pin = generate_pin()\n',
    '    config_path = base / "config.json"\n    config = AppConfig.load(config_path)\n'
    '    saved_pin = str(getattr(config, "custom_pin", "") or "").strip()\n'
    '    pin = saved_pin if saved_pin.isdigit() and 4 <= len(saved_pin) <= 8 else generate_pin()\n',
    'startup custom pin',
)

old_ctx = '''    app = create_app(AppContext(config=config, pin=pin, auth=auth, store=store, manager=manager,\n                                instance_id=instance_id, device_name=device_name))\n'''
new_ctx = '''    ctx = AppContext(config=config, pin=pin, auth=auth, store=store, manager=manager,\n                     instance_id=instance_id, device_name=device_name)\n    app = create_app(ctx)\n\n    def update_pin(new_pin: str) -> tuple[bool, str]:\n        value = str(new_pin or "").strip()\n        if not value.isdigit() or not (4 <= len(value) <= 8):\n            return False, "PIN باید فقط عدد و بین ۴ تا ۸ رقم باشد."\n        ctx.pin = value\n        config.custom_pin = value\n        config_path.write_text(json.dumps(asdict(config), ensure_ascii=False, indent=2), encoding="utf-8")\n        return True, value\n'''
s = replace_once(s, old_ctx, new_ctx, 'mutable app context')

s = replace_once(
    s,
    '    DesktopGui(root, config, pin, store, manager, device_name=device_name)\n',
    '    DesktopGui(root, config, pin, store, manager, device_name=device_name,\n'
    '               instance_id=instance_id, on_pin_change=update_pin)\n',
    'desktop gui callback',
)
write(rel, s)


# ---------- PC GUI: QR dialog + custom PIN ----------
rel = 'pc/lantransfer/gui.py'
s = read(rel)
if 'import json\n' not in s:
    s = s.replace('import ctypes\n', 'import ctypes\nimport json\n', 1)
if 'import qrcode\n' not in s:
    s = s.replace('import tkinter as tk\n', 'import tkinter as tk\nimport qrcode\nfrom PIL import ImageTk\n', 1)

# Constructor supports runtime PIN changes and dynamic QR payload.
s = re.sub(
    r'def __init__\(self, root: tk\.Tk, config: AppConfig, pin: str, store: TransferStore,\n\s+manager: ConnectionManager, device_name: str = "Windows PC"\):',
    'def __init__(self, root: tk.Tk, config: AppConfig, pin: str, store: TransferStore,\n'
    '                 manager: ConnectionManager, device_name: str = "Windows PC",\n'
    '                 instance_id: str = "", on_pin_change=None):',
    s,
    count=1,
)
if 'self.instance_id = instance_id' not in s:
    s = replace_once(
        s,
        '        self.device_name = device_name\n',
        '        self.device_name = device_name\n'
        '        self.instance_id = instance_id\n'
        '        self.on_pin_change = on_pin_change\n'
        '        self._qr_dialog = None\n'
        '        self._qr_photo = None\n'
        '        self._qr_payload_cache = ""\n'
        '        self.pin_value_label = None\n'
        '        self.pin_entry = None\n',
        'gui state',
    )

# Add QR button to top bar after device name label when possible.
marker = '        self.device_top.pack(side=tk.LEFT, padx=(0, 8))\n'
if 'QR اتصال' not in s:
    s = replace_once(
        s,
        marker,
        marker +
        '        self._button(self.topbar, "QR اتصال", self._show_qr_dialog, False, width=9).pack(side=tk.LEFT, padx=(0, 8), pady=10)\n',
        'topbar qr button',
    )

# Track the PIN value label instead of a static detail row.
s = s.replace(
    '        self._detail_row(details, "PIN", self.pin if self.config.pin_enabled else "خاموش")\n',
    '        self.pin_value_label = self._detail_row(details, "PIN", self.pin if self.config.pin_enabled else "خاموش")\n',
    1,
)

# Add PIN editor on connect page.
pin_anchor = '        self._detail_row(details, "حالت", "شبکه محلی")\n'
if 'ثبت PIN دلخواه' not in s:
    pin_ui = '''        self._detail_row(details, "حالت", "شبکه محلی")\n\n        pin_box = tk.Frame(details, bg=theme.SURFACE)\n        pin_box.pack(fill=tk.X, padx=20, pady=(14, 0))\n        tk.Label(pin_box, text="PIN دلخواه (۴ تا ۸ رقم)", bg=theme.SURFACE, fg=theme.TEXT_SECONDARY,\n                 font=(theme.FONT_FAMILY, 9)).pack(anchor="e", pady=(0, 6))\n        pin_controls = tk.Frame(pin_box, bg=theme.SURFACE)\n        pin_controls.pack(fill=tk.X)\n        self.pin_entry = tk.Entry(pin_controls, bd=0, justify="center", bg=theme.SURFACE_VARIANT,\n                                  fg=theme.TEXT_PRIMARY, insertbackground=theme.TEXT_PRIMARY,\n                                  font=(theme.FONT_FAMILY, 11), highlightthickness=1,\n                                  highlightbackground=theme.BORDER, highlightcolor=theme.PRIMARY)\n        self.pin_entry.insert(0, self.pin)\n        self.pin_entry.pack(side=tk.RIGHT, fill=tk.X, expand=True, ipady=8)\n        self._button(pin_controls, "ثبت PIN دلخواه", self._apply_custom_pin, True, width=12).pack(side=tk.LEFT, padx=(8, 0))\n'''
    s = replace_once(s, pin_anchor, pin_ui, 'pin editor')

# Make _detail_row return the value label.
old_detail = '''        tk.Label(row, text=value, bg=theme.SURFACE, fg=theme.TEXT_PRIMARY,\n                 font=(theme.FONT_FAMILY, 9, "bold")).pack(side=tk.LEFT)\n        tk.Label(row, text=label, bg=theme.SURFACE, fg=theme.TEXT_SECONDARY,\n                 font=(theme.FONT_FAMILY, 9)).pack(side=tk.RIGHT)\n'''
new_detail = '''        value_label = tk.Label(row, text=value, bg=theme.SURFACE, fg=theme.TEXT_PRIMARY,\n                               font=(theme.FONT_FAMILY, 9, "bold"))\n        value_label.pack(side=tk.LEFT)\n        tk.Label(row, text=label, bg=theme.SURFACE, fg=theme.TEXT_SECONDARY,\n                 font=(theme.FONT_FAMILY, 9)).pack(side=tk.RIGHT)\n        return value_label\n'''
if 'return value_label' not in s:
    s = replace_once(s, old_detail, new_detail, 'detail row return')

# Add methods before radar drawing.
method_anchor = '    def _draw_radar(self, canvas: tk.Canvas) -> None:\n'
if 'def _show_qr_dialog' not in s:
    methods = '''    def _apply_custom_pin(self) -> None:\n        if self.pin_entry is None:\n            return\n        value = self.pin_entry.get().strip()\n        if self.on_pin_change is None:\n            messagebox.showerror("PIN", "امکان ذخیره PIN در این نسخه فعال نیست.")\n            return\n        ok, result = self.on_pin_change(value)\n        if not ok:\n            messagebox.showwarning("PIN نامعتبر", result)\n            return\n        self.pin = result\n        if self.pin_value_label is not None:\n            self.pin_value_label.configure(text=self.pin)\n        self._qr_payload_cache = ""\n        self._refresh_qr_dialog()\n        messagebox.showinfo("PIN", "PIN جدید ذخیره شد و QR اتصال به‌روزرسانی شد.")\n\n    def _qr_payload(self) -> str:\n        ips = get_lan_ipv4s()\n        ip = ips[0] if ips else "127.0.0.1"\n        scheme = "https" if self.config.tls_enabled else "http"\n        payload = {\n            "v": 1,\n            "service": "LANShare",\n            "base": f"{scheme}://{ip}:{self.config.port}",\n            "pin": self.pin if self.config.pin_enabled else "",\n            "instance_id": self.instance_id,\n            "pc_name": self.device_name,\n        }\n        return json.dumps(payload, ensure_ascii=False, separators=(",", ":"))\n\n    def _show_qr_dialog(self) -> None:\n        if self._qr_dialog is not None and self._qr_dialog.winfo_exists():\n            self._qr_dialog.lift()\n            self._refresh_qr_dialog()\n            return\n        dlg = tk.Toplevel(self.root)\n        dlg.title("QR اتصال — LAN SHARE")\n        dlg.geometry("390x520")\n        dlg.resizable(False, False)\n        dlg.configure(bg=theme.SURFACE)\n        dlg.transient(self.root)\n        self._qr_dialog = dlg\n        tk.Label(dlg, text="اتصال سریع با QR", bg=theme.SURFACE, fg=theme.TEXT_PRIMARY,\n                 font=(theme.FONT_FAMILY, 16, "bold")).pack(pady=(22, 4))\n        tk.Label(dlg, text="در گوشی روی «اسکن QR» بزنید و این کد را اسکن کنید.",\n                 bg=theme.SURFACE, fg=theme.TEXT_SECONDARY, font=(theme.FONT_FAMILY, 9)).pack()\n        self._qr_image_label = tk.Label(dlg, bg="white")\n        self._qr_image_label.pack(pady=18)\n        self._qr_meta_label = tk.Label(dlg, text="", justify="center", bg=theme.SURFACE,\n                                       fg=theme.TEXT_SECONDARY, font=(theme.FONT_FAMILY, 9))\n        self._qr_meta_label.pack(padx=20, pady=(0, 12))\n        self._button(dlg, "بستن", dlg.destroy, False, width=12).pack(pady=(4, 16))\n        self._refresh_qr_dialog()\n\n    def _refresh_qr_dialog(self) -> None:\n        dlg = self._qr_dialog\n        if dlg is None or not dlg.winfo_exists():\n            return\n        payload = self._qr_payload()\n        if payload == self._qr_payload_cache and self._qr_photo is not None:\n            return\n        qr = qrcode.QRCode(version=None, box_size=7, border=4, error_correction=qrcode.constants.ERROR_CORRECT_M)\n        qr.add_data(payload)\n        qr.make(fit=True)\n        img = qr.make_image(fill_color="#111827", back_color="white").convert("RGB")\n        img.thumbnail((250, 250))\n        self._qr_photo = ImageTk.PhotoImage(img)\n        self._qr_image_label.configure(image=self._qr_photo)\n        data = json.loads(payload)\n        self._qr_meta_label.configure(text=f"{data['base']}\\nPIN: {data['pin'] or 'خاموش'}")\n        self._qr_payload_cache = payload\n\n'''
    s = replace_once(s, method_anchor, methods + method_anchor, 'qr methods')

# Refresh open QR if IP changes.
if 'self._refresh_qr_dialog()\n        self.root.after(500, self._poll)' not in s:
    s = s.replace(
        '        self.root.after(500, self._poll)\n',
        '        self._refresh_qr_dialog()\n        self.root.after(500, self._poll)\n',
        1,
    )
write(rel, s)


# ---------- Android Gradle + camera permission ----------
rel = 'android/app/build.gradle.kts'
s = read(rel)
if 'com.journeyapps:zxing-android-embedded:4.3.0' not in s:
    s = s.replace(
        '    implementation("com.squareup.okhttp3:okhttp:5.4.0")\n',
        '    implementation("com.squareup.okhttp3:okhttp:5.4.0")\n'
        '    implementation("com.journeyapps:zxing-android-embedded:4.3.0")\n',
        1,
    )
s = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 7', s, count=1)
s = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "2.5.0"', s, count=1)
write(rel, s)

rel = 'android/app/src/main/AndroidManifest.xml'
s = read(rel)
if 'android.permission.CAMERA' not in s:
    s = s.replace(
        '    <uses-permission android:name="android.permission.INTERNET" />\n',
        '    <uses-permission android:name="android.permission.INTERNET" />\n'
        '    <uses-permission android:name="android.permission.CAMERA" />\n',
        1,
    )
write(rel, s)


# ---------- Android Activity: QR scanner + auto pairing ----------
rel = 'android/app/src/main/java/ir/local/lantransfer/MainActivity.java'
s = read(rel)
if 'com.google.zxing.integration.android.IntentIntegrator' not in s:
    s = s.replace(
        'import org.json.JSONObject;\n',
        'import org.json.JSONObject;\n\n'
        'import com.google.zxing.integration.android.IntentIntegrator;\n'
        'import com.google.zxing.integration.android.IntentResult;\n',
        1,
    )

# Add scan control in top bar.
if 'اسکن QR' not in s:
    top_anchor = '''        topStatusText = text(Prefs.hasPair(this) ? "در حال اتصال" : "آماده", 10, Color.WHITE, true);\n'''
    scan_ui = '''        TextView scanQr = text("اسکن QR", 10, Color.WHITE, true);\n        scanQr.setGravity(Gravity.CENTER);\n        scanQr.setPadding(dp(10), dp(8), dp(10), dp(8));\n        scanQr.setBackground(roundRect(Color.argb(38,255,255,255), dp(18)));\n        scanQr.setClickable(true);\n        scanQr.setFocusable(true);\n        scanQr.setContentDescription("اسکن QR اتصال ویندوز");\n        scanQr.setOnClickListener(v -> scanConnectionQr());\n        LinearLayout.LayoutParams scanLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,\n                LinearLayout.LayoutParams.WRAP_CONTENT);\n        scanLp.setMargins(dp(8), 0, 0, 0);\n        bar.addView(scanQr, scanLp);\n\n'''
    s = replace_once(s, top_anchor, scan_ui + top_anchor, 'android topbar scan')

# Add QR action next to Radar for first pair.
radar_button = '''            Button radar = secondaryButton("جست‌وجو");\n            radar.setOnClickListener(v -> discoverPc(false));\n            radarRow.addView(radar);\n'''
if 'اسکن QR ویندوز' not in s:
    qr_action = radar_button + '''            Button scan = secondaryButton("اسکن QR ویندوز");\n            scan.setOnClickListener(v -> scanConnectionQr());\n            connection.addView(scan, fixedBottom(48, 10));\n'''
    s = replace_once(s, radar_button, qr_action, 'android qr action')

# Update PIN hint.
s = s.replace('PIN شش‌رقمی روی کامپیوتر', 'PIN اتصال روی کامپیوتر')

# Add scanner methods before pairAndConnect.
anchor = '    private void pairAndConnect() {\n'
if 'private void scanConnectionQr()' not in s:
    methods = '''    private void scanConnectionQr() {\n        IntentIntegrator integrator = new IntentIntegrator(this);\n        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);\n        integrator.setPrompt("QR نمایش‌داده‌شده در LAN SHARE ویندوز را اسکن کنید");\n        integrator.setBeepEnabled(false);\n        integrator.setOrientationLocked(false);\n        integrator.initiateScan();\n    }\n\n    private void applyScannedConnection(String raw) {\n        try {\n            JSONObject obj = new JSONObject(raw);\n            if (!"LANShare".equals(obj.optString("service"))) {\n                toast("این QR متعلق به LAN SHARE نیست");\n                return;\n            }\n            String base = ApiClient.normalizeBase(obj.optString("base"));\n            String pin = obj.optString("pin", "").trim();\n            String name = obj.optString("pc_name", "Windows PC");\n            if (base.isEmpty()) {\n                toast("آدرس اتصال داخل QR معتبر نیست");\n                return;\n            }\n            if (Prefs.hasPair(this)) Prefs.clearPair(this);\n            currentServer = base;\n            pcDisplay = name;\n            showPage(PAGE_HOME);\n            if (serverField != null) serverField.setText(base);\n            if (pinField != null) pinField.setText(pin);\n            setConnectionState("QR خوانده شد؛ در حال اتصال...", DesignTokens.SUCCESS);\n            if (!pin.isEmpty()) pairAndConnect();\n            else toast("QR خوانده شد؛ PIN را وارد کنید");\n        } catch (Exception e) {\n            toast("QR اتصال معتبر نیست");\n        }\n    }\n\n'''
    s = replace_once(s, anchor, methods + anchor, 'android scanner methods')

# Scanner result must be handled before file picker results.
activity_anchor = '''    @Override\n    protected void onActivityResult(int requestCode, int resultCode, Intent data) {\n        super.onActivityResult(requestCode, resultCode, data);\n'''
if 'IntentIntegrator.parseActivityResult' not in s:
    activity_new = activity_anchor + '''        IntentResult scan = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);\n        if (scan != null) {\n            if (scan.getContents() != null) applyScannedConnection(scan.getContents());\n            return;\n        }\n'''
    s = replace_once(s, activity_anchor, activity_new, 'android scan activity result')
write(rel, s)

print('V2.5 QR + custom PIN patch applied successfully')
