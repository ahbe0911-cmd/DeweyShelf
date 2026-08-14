from pathlib import Path

path = Path('buildsrc/lan_file_transfer_v1/android/app/src/main/java/ir/local/lantransfer/MainActivity.java')
text = path.read_text(encoding='utf-8')

# V2.3 uses an explicit LayoutParams when adding the Radar button, while the
# QR patch expects the simpler V2.2 form. Normalize only that tiny UI difference.
old = '''            Button radar = secondaryButton("جست‌وجو");
            radar.setOnClickListener(v -> discoverPc(false));
            radarRow.addView(radar, new LinearLayout.LayoutParams(dp(isCompactWidth() ? 92 : 104), dp(44)));
'''
new = '''            Button radar = secondaryButton("جست‌وجو");
            radar.setOnClickListener(v -> discoverPc(false));
            radarRow.addView(radar);
'''
if old not in text:
    raise SystemExit('Responsive Radar marker not found')

path.write_text(text.replace(old, new, 1), encoding='utf-8')
print('Applied QR compatibility patch for responsive Radar row')
