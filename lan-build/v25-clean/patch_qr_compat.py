from pathlib import Path
import re

path = Path('buildsrc/lan_file_transfer_v1/android/app/src/main/java/ir/local/lantransfer/MainActivity.java')
text = path.read_text(encoding='utf-8')

# V2.3 uses an explicit LayoutParams when adding the Radar button, while the
# QR patch expects the simpler V2.2 form. Normalize that tiny UI difference.
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
text = text.replace(old, new, 1)

# Keep the page-icon switch deterministic. This guard prevents a duplicate
# case from surviving after stacked V2.3/V2.5 source transforms.
pattern = re.compile(r'''    private int iconForPage\(int page\) \{.*?^    \}\n''', re.S | re.M)
canonical = '''    private int iconForPage(int page) {
        return switch (page) {
            case PAGE_HOME -> R.drawable.ic_home;
            case PAGE_TRANSFERS -> R.drawable.ic_transfer;
            case PAGE_RECEIVED -> R.drawable.ic_folder;
            case PAGE_SETTINGS -> R.drawable.ic_settings;
            default -> R.drawable.ic_home;
        };
    }
'''
text, count = pattern.subn(canonical, text, count=1)
if count != 1:
    raise SystemExit(f'iconForPage normalization failed: {count}')

path.write_text(text, encoding='utf-8')
print('Applied QR compatibility + Android page-switch normalization')
