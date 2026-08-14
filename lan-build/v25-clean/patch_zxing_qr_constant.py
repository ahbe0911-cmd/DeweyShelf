from pathlib import Path

path = Path("buildsrc/lan_file_transfer_v1/android/app/src/main/java/ir/local/lantransfer/MainActivity.java")
text = path.read_text(encoding="utf-8")
old = "IntentIntegrator.QR_CODE_TYPES"
new = "IntentIntegrator.QR_CODE"
if old in text:
    text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
    print("Patched ZXing QR constant: QR_CODE_TYPES -> QR_CODE")
elif new in text:
    print("ZXing QR constant already correct")
else:
    raise SystemExit("ZXing QR constant marker not found")
