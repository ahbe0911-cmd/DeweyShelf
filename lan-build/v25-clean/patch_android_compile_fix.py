from pathlib import Path

p = Path('buildsrc/lan_file_transfer_v1/android/app/src/main/java/ir/local/lantransfer/MainActivity.java')
s = p.read_text(encoding='utf-8')

signature = 'private int iconForPage(int page)'
pos = s.find(signature)
if pos < 0:
    raise SystemExit('iconForPage signature not found')

start = s.rfind('\n', 0, pos) + 1
open_brace = s.find('{', pos)
if open_brace < 0:
    raise SystemExit('iconForPage opening brace not found')

depth = 0
end = -1
for i in range(open_brace, len(s)):
    ch = s[i]
    if ch == '{':
        depth += 1
    elif ch == '}':
        depth -= 1
        if depth == 0:
            end = i + 1
            break
if end < 0:
    raise SystemExit('iconForPage closing brace not found')
if end < len(s) and s[end] == '\r':
    end += 1
if end < len(s) and s[end] == '\n':
    end += 1

replacement = '''    private int iconForPage(int page) {
        return switch (page) {
            case PAGE_HOME -> R.drawable.ic_home;
            case PAGE_TRANSFERS -> R.drawable.ic_transfer;
            case PAGE_RECEIVED -> R.drawable.ic_folder;
            case PAGE_SETTINGS -> R.drawable.ic_settings;
            default -> R.drawable.ic_home;
        };
    }
'''

p.write_text(s[:start] + replacement + s[end:], encoding='utf-8')
print('Normalized Android page icon switch with brace-aware guard')
