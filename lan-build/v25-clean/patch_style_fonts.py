from pathlib import Path

root = Path('buildsrc/lan_file_transfer_v1')

# ---------------- Android styling + embedded fonts ----------------
main = root / 'android/app/src/main/java/ir/local/lantransfer/MainActivity.java'
text = main.read_text(encoding='utf-8')

def rep(old, new, label):
    global text
    if old not in text:
        raise SystemExit('Android marker missing: ' + label)
    text = text.replace(old, new, 1)

rep('''    private FrameLayout pageHost;\n    private int currentPage = PAGE_HOME;''', '''    private FrameLayout pageHost;\n    private Typeface bodyTypeface;\n    private Typeface titleTypeface;\n    private int currentPage = PAGE_HOME;''', 'typeface fields')

rep('''        super.onCreate(savedInstanceState);\n        getWindow().setStatusBarColor(DesignTokens.PRIMARY_DARK);''', '''        super.onCreate(savedInstanceState);\n        bodyTypeface = getResources().getFont(R.font.far_nazanin);\n        titleTypeface = getResources().getFont(R.font.far_titr_bold);\n        getWindow().setStatusBarColor(DesignTokens.PRIMARY_DARK);''', 'load fonts')

rep('''        bar.setBackgroundColor(DesignTokens.PRIMARY);''', '''        GradientDrawable topGradient = new GradientDrawable(\n                GradientDrawable.Orientation.LEFT_RIGHT,\n                new int[]{DesignTokens.PRIMARY, DesignTokens.PRIMARY_DARK});\n        bar.setBackground(topGradient);''', 'top gradient')

# Main page headings use Far Titr Bold.
text = text.replace('TextView pageTitle = text("انتقال فایل", isCompactWidth() ? 20 : 22, DesignTokens.TEXT_PRIMARY, true);',
                    'TextView pageTitle = titleText("انتقال فایل", isCompactWidth() ? 20 : 22, DesignTokens.TEXT_PRIMARY);')
text = text.replace('content.addView(text("انتقال‌ها", 23, DesignTokens.TEXT_PRIMARY, true));',
                    'content.addView(titleText("انتقال‌ها", 22, DesignTokens.TEXT_PRIMARY));')
text = text.replace('content.addView(text("فایل‌های دریافتی", 23, DesignTokens.TEXT_PRIMARY, true));',
                    'content.addView(titleText("فایل‌های دریافتی", 22, DesignTokens.TEXT_PRIMARY));')
text = text.replace('content.addView(text("تنظیمات", 23, DesignTokens.TEXT_PRIMARY, true));',
                    'content.addView(titleText("تنظیمات", 22, DesignTokens.TEXT_PRIMARY));')

# LAN Drop-like soft action tiles.
rep('''        LinearLayout tile = surface();\n        tile.setPadding(dp(isCompactWidth() ? 10 : 13), dp(isCompactWidth() ? 10 : 13),\n                dp(isCompactWidth() ? 10 : 13), dp(isCompactWidth() ? 10 : 13));''', '''        LinearLayout tile = surface();\n        GradientDrawable tileBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,\n                new int[]{Color.WHITE, DesignTokens.PRIMARY_CONTAINER});\n        tileBg.setCornerRadius(dp(18));\n        tileBg.setStroke(dp(1), Color.rgb(210, 228, 250));\n        tile.setBackground(tileBg);\n        tile.setElevation(dp(2));\n        tile.setPadding(dp(isCompactWidth() ? 10 : 13), dp(isCompactWidth() ? 10 : 13),\n                dp(isCompactWidth() ? 10 : 13), dp(isCompactWidth() ? 10 : 13));''', 'action tile style')

# Body font helper + title helper.
rep('''    private TextView text(String value, int sp, int color, boolean bold) {\n        TextView v = new TextView(this);\n        v.setText(value);\n        v.setTextSize(sp);\n        v.setTextColor(color);\n        v.setGravity(Gravity.START);\n        if (bold) v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));\n        else v.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));\n        return v;\n    }''', '''    private TextView text(String value, int sp, int color, boolean bold) {\n        TextView v = new TextView(this);\n        v.setText(value);\n        v.setTextSize(sp);\n        v.setTextColor(color);\n        v.setGravity(Gravity.START);\n        v.setTypeface(bodyTypeface, bold ? Typeface.BOLD : Typeface.NORMAL);\n        return v;\n    }\n\n    private TextView titleText(String value, int sp, int color) {\n        TextView v = new TextView(this);\n        v.setText(value);\n        v.setTextSize(sp);\n        v.setTextColor(color);\n        v.setGravity(Gravity.START);\n        v.setTypeface(titleTypeface, Typeface.NORMAL);\n        return v;\n    }''', 'text helper')

rep('''        e.setTextSize(12);\n        e.setSingleLine(true);''', '''        e.setTextSize(12);\n        e.setTypeface(bodyTypeface, Typeface.NORMAL);\n        e.setSingleLine(true);''', 'input font')

rep('''        b.setTextColor(textColor);\n        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);''', '''        b.setTextColor(textColor);\n        b.setTypeface(bodyTypeface, Typeface.BOLD);''', 'button font')

# Use font in bottom nav labels as well through text() and preserve responsive fit.
main.write_text(text, encoding='utf-8')

# ---------------- Windows styling + private fonts ----------------
theme = root / 'pc/lantransfer/theme.py'
t = theme.read_text(encoding='utf-8')
t = t.replace('FONT_FAMILY = "Segoe UI"', 'FONT_FAMILY = "Segoe UI"\nFONT_TITLE = "Segoe UI"')
theme.write_text(t, encoding='utf-8')

gui = root / 'pc/lantransfer/gui.py'
g = gui.read_text(encoding='utf-8')

def grep(old, new, label):
    global g
    if old not in g:
        raise SystemExit('Windows marker missing: ' + label)
    g = g.replace(old, new, 1)

if 'import tkinter.font as tkfont' not in g:
    g = g.replace('import tkinter as tk\n', 'import tkinter as tk\nimport tkinter.font as tkfont\n', 1)

grep('''        self.root.configure(bg=theme.BACKGROUND)\n        self._set_window_identity()''', '''        self.root.configure(bg=theme.BACKGROUND)\n        self._register_private_fonts()\n        self._set_window_identity()''', 'register call')

anchor = '''    def _set_window_identity(self) -> None:\n'''
font_method = '''    def _register_private_fonts(self) -> None:\n        """Register bundled Far fonts for this process only; no system install required."""\n        if os.name == "nt":\n            try:\n                FR_PRIVATE = 0x10\n                for rel in ("assets/fonts/Far_Nazanin.ttf", "assets/fonts/Far_TitrBd.ttf"):\n                    path = self._resource_path(rel)\n                    ctypes.windll.gdi32.AddFontResourceExW(path, FR_PRIVATE, 0)\n            except Exception:\n                pass\n        try:\n            families = list(tkfont.families(self.root))\n            nazanin = next((f for f in families if "nazanin" in f.lower()), None)\n            titr = next((f for f in families if "titr" in f.lower()), None)\n            if nazanin:\n                theme.FONT_FAMILY = nazanin\n            if titr:\n                theme.FONT_TITLE = titr\n            else:\n                theme.FONT_TITLE = theme.FONT_FAMILY\n        except Exception:\n            theme.FONT_TITLE = theme.FONT_FAMILY\n\n'''
if 'def _register_private_fonts' not in g:
    grep(anchor, font_method + anchor, 'font method')

# Blue LAN Drop-like top bar.
g = g.replace('self.topbar = tk.Frame(workspace, bg=theme.SURFACE, height=64,',
              'self.topbar = tk.Frame(workspace, bg=theme.PRIMARY, height=72,', 1)
g = g.replace('highlightthickness=1, highlightbackground=theme.BORDER)',
              'highlightthickness=0, highlightbackground=theme.PRIMARY)', 1)

g = g.replace('self.page_title = tk.Label(self.topbar, text="", bg=theme.SURFACE,\n                                   fg=theme.TEXT_PRIMARY, font=(theme.FONT_FAMILY, 15, "bold"))',
              'self.page_title = tk.Label(self.topbar, text="", bg=theme.PRIMARY,\n                                   fg="#FFFFFF", font=(theme.FONT_TITLE, 15))', 1)
g = g.replace('self.device_top = tk.Label(self.topbar, text=self.device_name, bg=theme.SURFACE,\n                                   fg=theme.TEXT_SECONDARY, font=(theme.FONT_FAMILY, 9))',
              'self.device_top = tk.Label(self.topbar, text=self.device_name, bg=theme.PRIMARY,\n                                   fg="#EAF3FF", font=(theme.FONT_FAMILY, 9))', 1)

# Connection pill becomes blue-on-blue and readable.
g = g.replace('self.connection_pill = tk.Frame(self.topbar, bg=theme.SURFACE_VARIANT)',
              'self.connection_pill = tk.Frame(self.topbar, bg=theme.PRIMARY_HOVER)', 1)
g = g.replace('bg=theme.SURFACE_VARIANT, highlightthickness=0)',
              'bg=theme.PRIMARY_HOVER, highlightthickness=0)', 1)
g = g.replace('bg=theme.SURFACE_VARIANT, fg=theme.TEXT_SECONDARY,\n                                         font=(theme.FONT_FAMILY, 9, "bold"))',
              'bg=theme.PRIMARY_HOVER, fg="#FFFFFF",\n                                         font=(theme.FONT_FAMILY, 9, "bold"))', 1)

# Far Titr for major Persian headings.
g = g.replace('font=(theme.FONT_FAMILY, 20, "bold"), anchor="e")',
              'font=(theme.FONT_TITLE, 20), anchor="e")', 1)
g = g.replace('font=(theme.FONT_FAMILY, 16, "bold")).pack(anchor="e")',
              'font=(theme.FONT_TITLE, 16)).pack(anchor="e")', 1)

# Version label.
g = g.replace('text="LAN SHARE 2.2"', 'text="LAN SHARE 2.5"')

gui.write_text(g, encoding='utf-8')

print('Applied clean LAN Drop style + Far fonts patch')
