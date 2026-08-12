import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'core/config/app_config.dart';
import 'core/theme/app_theme.dart';
import 'features/public_web/data/public_telegram_web_gateway.dart';
import 'features/shell/presentation/home_shell.dart';

final class KhabarCafeApp extends StatefulWidget {
  const KhabarCafeApp({super.key});

  @override
  State<KhabarCafeApp> createState() => _KhabarCafeAppState();
}

final class _KhabarCafeAppState extends State<KhabarCafeApp> {
  final PublicTelegramWebGateway _gateway = PublicTelegramWebGateway();
  ThemeMode _themeMode = ThemeMode.system;

  @override
  void dispose() {
    _gateway.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: AppConfig.appName,
      locale: const Locale('fa', 'IR'),
      supportedLocales: const <Locale>[Locale('fa', 'IR')],
      localizationsDelegates: const <LocalizationsDelegate<dynamic>>[
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: _themeMode,
      builder: (context, child) => Directionality(
        textDirection: TextDirection.rtl,
        child: child ?? const SizedBox.shrink(),
      ),
      home: HomeShell(
        gateway: _gateway,
        isPreviewMode: false,
        themeMode: _themeMode,
        onThemeModeChanged: (value) => setState(() => _themeMode = value),
      ),
    );
  }
}
