from pathlib import Path
import re

p = Path('buildsrc/lan_file_transfer_v1/android/app/src/main/java/ir/local/lantransfer/MainActivity.java')
s = p.read_text(encoding='utf-8')
pattern = re.compile(r'''    private int iconForPage\(int page\) \{.*?^    \}\n''', re.S | re.M)
replacement = '''    private int iconForPage(int page) {\n        return switch (page) {\n            case PAGE_HOME -> R.drawable.ic_home;\n            case PAGE_TRANSFERS -> R.drawable.ic_transfer;\n            case PAGE_RECEIVED -> R.drawable.ic_folder;\n            case PAGE_SETTINGS -> R.drawable.ic_settings;\n            default -> R.drawable.ic_home;\n        };\n    }\n'''
new, count = pattern.subn(replacement, s, count=1)
if count != 1:
    raise SystemExit(f'iconForPage normalization failed: {count}')
p.write_text(new, encoding='utf-8')
print('Normalized Android page icon switch')
