from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
ci = Path(sys.argv[2]).resolve()

# Dependencies + version
pubspec = root / 'pubspec.yaml'
s = pubspec.read_text()
s = s.replace(
    'description: خبرخوان فارسی کانال‌های عمومی با رابط بومی و اتصال رسمی TDLib.',
    'description: خبرخوان فارسی کانال‌های عمومی تلگرام بدون نیاز به ورود کاربر.',
)
s = s.replace('version: 0.3.1+4', 'version: 0.4.0+5')
needle = '  flutter_localizations:\n    sdk: flutter\n'
if '  http:' not in s:
    s = s.replace(needle, needle + '  http: ^1.6.0\n  html: ^0.15.6\n')
pubspec.write_text(s)

# New public web entry and gateway
(root / 'lib/app.dart').write_text((ci / 'app.dart').read_text())
(root / 'lib/core/config/app_config.dart').write_text((ci / 'app_config.dart').read_text())
gateway_dir = root / 'lib/features/public_web/data'
gateway_dir.mkdir(parents=True, exist_ok=True)
(gateway_dir / 'public_telegram_web_gateway.dart').write_text(
    (ci / 'public_telegram_web_gateway.dart').read_text()
)

# Auto-sync the latest 20 posts/channel on app launch.
home = root / 'lib/features/shell/presentation/home_shell.dart'
s = home.read_text()
if not s.startswith("import 'dart:async';"):
    s = "import 'dart:async';\n\n" + s
old = '''    _feedStore = FeedStore(\n      gateway: widget.gateway,\n      channelStore: _channelStore,\n    );\n'''
new = old + '    unawaited(_feedStore.syncActiveChannels(perChannelLimit: 20));\n'
if 'syncActiveChannels(perChannelLimit: 20)' not in s:
    s = s.replace(old, new)
home.write_text(s)

# User-facing feed text + render remote Telegram photos.
feed = root / 'lib/features/feed/presentation/feed_page.dart'
s = feed.read_text()
s = s.replace(
    "'برای دریافت واقعی، APK متصل TDLib را اجرا کنید.'",
    "'این نسخه مستقیماً از صفحات عمومی تلگرام خبر می‌گیرد.'",
)
s = s.replace(
    "'${PersianDigits.convert(count)} خبر از تلگرام دریافت شد.'",
    "'${PersianDigits.convert(count)} خبر عمومی دریافت شد.'",
)
s = s.replace(
    "'دکمه دریافت خبرهای جدید را بزنید تا پست‌های واقعی تلگرام خوانده شوند.'",
    "'دکمه دریافت خبرهای جدید را بزنید تا پست‌های عمومی کانال‌ها خوانده شوند.'",
)
s = s.replace(
    "const Text('حالت پیش‌نمایش رابط؛ ارتباط تلگرام غیرفعال است.')",
    "const Text('دریافت خبر از صفحه عمومی تلگرام؛ بدون نیاز به ورود.')",
)
local_block = '''    if (media.kind == TelegramMediaKind.photo &&\n        localPath != null &&\n        localPath.isNotEmpty &&\n        File(localPath).existsSync()) {\n      return Padding(\n        padding: const EdgeInsets.only(bottom: 8),\n        child: ClipRRect(\n          borderRadius: BorderRadius.circular(16),\n          child: Image.file(\n            File(localPath),\n            fit: BoxFit.cover,\n            errorBuilder: (_, _, _) => _MediaPlaceholder(media),\n          ),\n        ),\n      );\n    }\n'''
network_block = '''    if (media.kind == TelegramMediaKind.photo &&\n        (media.remoteId.startsWith('https://') ||\n            media.remoteId.startsWith('http://'))) {\n      return Padding(\n        padding: const EdgeInsets.only(bottom: 8),\n        child: ClipRRect(\n          borderRadius: BorderRadius.circular(16),\n          child: Image.network(\n            media.remoteId,\n            fit: BoxFit.cover,\n            headers: const <String, String>{\n              'Referer': 'https://t.me/',\n              'User-Agent':\n                  'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36',\n            },\n            errorBuilder: (_, _, _) => _MediaPlaceholder(media),\n            loadingBuilder: (context, child, progress) {\n              if (progress == null) return child;\n              return const AspectRatio(\n                aspectRatio: 16 / 9,\n                child: Center(child: CircularProgressIndicator()),\n              );\n            },\n          ),\n        ),\n      );\n    }\n'''
if network_block not in s:
    s = s.replace(local_block, local_block + network_block)
feed.write_text(s)

channel_store = root / 'lib/features/channels/application/channel_store.dart'
s = channel_store.read_text().replace(
    'در انتظار بررسی هنگام فعال‌شدن اتصال تلگرام',
    'در انتظار بررسی صفحه عمومی کانال',
)
channel_store.write_text(s)

settings = root / 'lib/features/settings/presentation/settings_page.dart'
s = settings.read_text().replace(
    'خبرخوان مستقل کانال‌های عمومی تلگرام',
    'خبرخوان مستقل کانال‌های عمومی تلگرام، بدون نیاز به ورود',
)
settings.write_text(s)

# Remove TDLib native runtime entirely from this build.
main_activity = root / 'android/app/src/main/kotlin/ir/khabarcafe/app/MainActivity.kt'
main_activity.write_text('''package ir.khabarcafe.app\n\nimport io.flutter.embedding.android.FlutterActivity\n\nclass MainActivity : FlutterActivity()\n''')
for name in ['NativeTdJson.kt', 'TdlibTelegramNativeBridge.kt', 'TelegramNativeBridge.kt']:
    p = main_activity.parent / name
    if p.exists():
        p.unlink()
cpp = root / 'android/app/src/main/cpp'
if cpp.exists():
    shutil.rmtree(cpp)
cmake = root / 'android/app/CMakeLists.txt'
if cmake.exists():
    cmake.unlink()

(root / 'android/app/build.gradle.kts').write_text('''plugins {\n    id("com.android.application")\n    id("dev.flutter.flutter-gradle-plugin")\n}\n\nandroid {\n    namespace = "ir.khabarcafe.app"\n    compileSdk = flutter.compileSdkVersion\n    ndkVersion = flutter.ndkVersion\n\n    compileOptions {\n        sourceCompatibility = JavaVersion.VERSION_17\n        targetCompatibility = JavaVersion.VERSION_17\n    }\n\n    defaultConfig {\n        applicationId = "ir.khabarcafe.app"\n        minSdk = 24\n        targetSdk = flutter.targetSdkVersion\n        versionCode = flutter.versionCode\n        versionName = flutter.versionName\n    }\n\n    buildTypes {\n        release {\n            signingConfig = signingConfigs.getByName("debug")\n        }\n    }\n}\n\nkotlin {\n    compilerOptions {\n        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17\n    }\n}\n\nflutter {\n    source = "../.."\n}\n''')

# Add implementation note.
doc = root / 'docs/PUBLIC_WEB_MODE.md'
doc.write_text('''# Public Web Mode – v0.4.0\n\n`t.me/s/<username>` → HTTP client → HTML parser → Feed.\n\n- بدون شماره، کد ورود، API ID یا API Hash\n- Sync خودکار کانال‌های فعال در شروع برنامه\n- افزودن کانال عمومی با username یا لینک t.me\n- استخراج متن، تاریخ و رسانه‌هایی که صفحه عمومی Telegram ارائه می‌کند\n\nاین روش به HTML عمومی Telegram وابسته است؛ در صورت تغییر markup، parser باید به‌روزرسانی شود.\n''')
