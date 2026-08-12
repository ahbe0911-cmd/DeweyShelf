abstract final class AppConfig {
  static const String appName = 'خبر کافی‌نت';
  static const String versionLabel = 'نسخه عمومی بدون ورود ۰.۴.۰';
  static const String publicTelegramBaseUrl = 'https://t.me/s';

  // Compatibility constants for legacy TDLib source files that remain in the
  // source tree but are not used by the public-web application entry point.
  static const bool tdlibEnabled = false;
  static const String tdlibChannelName = 'ir.khabarcafe.app/tdlib';
}
