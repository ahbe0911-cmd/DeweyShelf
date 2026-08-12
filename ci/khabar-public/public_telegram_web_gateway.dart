import 'dart:async';
import 'dart:convert';

import 'package:html/dom.dart';
import 'package:html/parser.dart' as html_parser;
import 'package:http/http.dart' as http;

import '../../auth/data/tdlib_gateway.dart';
import '../../auth/domain/authorization_state.dart';
import '../../channels/domain/channel_model.dart';
import '../../feed/domain/post_model.dart';

final class PublicTelegramWebGateway implements TdlibGateway {
  PublicTelegramWebGateway({http.Client? client})
    : _client = client ?? http.Client();

  final http.Client _client;

  static const Duration _timeout = Duration(seconds: 18);
  static const Map<String, String> _headers = <String, String>{
    'User-Agent':
        'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 '
        '(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36',
    'Accept':
        'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,'
        'image/webp,*/*;q=0.8',
    'Accept-Language': 'fa-IR,fa;q=0.9,en;q=0.7',
  };

  void dispose() => _client.close();

  @override
  Stream<AuthorizationSnapshot> get authorizationChanges =>
      const Stream<AuthorizationSnapshot>.empty();

  @override
  Future<TdlibReadiness> getReadiness() async => const TdlibReadiness(
    available: true,
    nativeLibraryLoaded: false,
    credentialsConfigured: true,
    reason: 'اتصال از صفحه عمومی تلگرام انجام می‌شود و ورود لازم نیست.',
  );

  @override
  Future<AuthorizationSnapshot> getAuthorizationState() async =>
      const AuthorizationSnapshot(status: AuthorizationStatus.ready);

  @override
  Future<TdlibReadiness> setCredentials(int apiId, String apiHash) =>
      getReadiness();

  @override
  Future<void> setPhoneNumber(String phoneNumber) async {
    throw UnsupportedError('این نسخه به ورود با شماره تلفن نیاز ندارد.');
  }

  @override
  Future<void> checkCode(String code) async {
    throw UnsupportedError('این نسخه به کد ورود تلگرام نیاز ندارد.');
  }

  @override
  Future<void> checkPassword(String password) async {
    throw UnsupportedError('این نسخه به رمز تلگرام نیاز ندارد.');
  }

  @override
  Future<void> logOut() async {}

  @override
  Future<ChannelModel> resolvePublicChannel(String username) async {
    final page = await _fetchPage(username);
    final title = _firstNonEmpty(<String?>[
      page.querySelector('.tgme_channel_info_header_title')?.text,
      page.querySelector('meta[property="og:title"]')?.attributes['content'],
      '@$username',
    ]);
    final description = _firstNonEmpty(<String?>[
      page.querySelector('.tgme_channel_info_description')?.text,
      page
          .querySelector('meta[property="og:description"]')
          ?.attributes['content'],
    ]);

    final hasChannelMarkers =
        page.querySelector('.tgme_channel_info') != null ||
        page.querySelector('.tgme_widget_message') != null;
    if (!hasChannelMarkers) {
      throw StateError('کانال عمومی پیدا نشد یا نمایش وب آن در دسترس نیست.');
    }

    return ChannelModel(
      username: username,
      title: title ?? '@$username',
      description: description,
      isDefault: false,
      syncStatus: ChannelSyncStatus.ready,
    );
  }

  @override
  Future<List<TelegramPost>> syncChannel(
    String username, {
    int limit = 50,
  }) async {
    final page = await _fetchPage(username);
    final elements = page.querySelectorAll('.tgme_widget_message');
    if (elements.isEmpty) {
      final hasChannelMarkers = page.querySelector('.tgme_channel_info') != null;
      if (!hasChannelMarkers) {
        throw StateError('صفحه عمومی کانال در دسترس نیست.');
      }
      return const <TelegramPost>[];
    }

    final posts = <TelegramPost>[];
    for (final element in elements.reversed) {
      final post = _parsePost(username, element);
      if (post != null) posts.add(post);
      if (posts.length >= limit) break;
    }
    return posts;
  }

  Future<Document> _fetchPage(String username) async {
    final clean = username.trim().replaceFirst(RegExp(r'^@'), '');
    if (!RegExp(r'^[A-Za-z0-9_]{5,}$').hasMatch(clean)) {
      throw ArgumentError('نام کاربری کانال معتبر نیست.');
    }
    final uri = Uri.https('t.me', '/s/$clean');
    final response = await _client.get(uri, headers: _headers).timeout(_timeout);
    if (response.statusCode != 200) {
      throw StateError('تلگرام پاسخ ${response.statusCode} برگرداند.');
    }
    final body = utf8.decode(response.bodyBytes, allowMalformed: true);
    if (body.trim().isEmpty) {
      throw StateError('پاسخ تلگرام خالی بود.');
    }
    return html_parser.parse(body);
  }

  TelegramPost? _parsePost(String username, Element element) {
    final dataPost = element.attributes['data-post'];
    if (dataPost == null || dataPost.isEmpty) return null;
    final messageId = int.tryParse(dataPost.split('/').last);
    if (messageId == null) return null;

    final timeElement = element.querySelector('time');
    final rawDate = timeElement?.attributes['datetime'];
    final published = rawDate == null
        ? DateTime.now().toUtc()
        : (DateTime.tryParse(rawDate)?.toUtc() ?? DateTime.now().toUtc());

    final text = _cleanText(
      element.querySelector('.tgme_widget_message_text')?.text,
    );
    final media = _parseMedia(element, dataPost);

    if ((text == null || text.isEmpty) && media.isEmpty) return null;

    return TelegramPost(
      channelUsername: username,
      telegramMessageId: messageId,
      publishedAtUtc: published,
      receivedAtUtc: DateTime.now().toUtc(),
      text: text,
      media: media,
    );
  }

  List<TelegramMedia> _parseMedia(Element element, String dataPost) {
    final result = <TelegramMedia>[];
    final seen = <String>{};

    void add(TelegramMedia media) {
      if (media.remoteId.isEmpty || !seen.add(media.remoteId)) return;
      result.add(media);
    }

    var index = 0;
    for (final photo in element.querySelectorAll(
      '.tgme_widget_message_photo_wrap',
    )) {
      final url = _backgroundImageUrl(photo.attributes['style']) ??
          photo.querySelector('img')?.attributes['src'];
      if (url != null && url.isNotEmpty) {
        add(
          TelegramMedia(
            kind: TelegramMediaKind.photo,
            remoteId: url,
          ),
        );
      }
      index += 1;
    }

    for (final image in element.querySelectorAll(
      'img.tgme_widget_message_sticker',
    )) {
      final url = image.attributes['src'];
      if (url != null && url.isNotEmpty) {
        add(
          TelegramMedia(
            kind: TelegramMediaKind.sticker,
            remoteId: url,
          ),
        );
      }
    }

    for (final video in element.querySelectorAll('video')) {
      final url = video.attributes['src'] ??
          video.querySelector('source')?.attributes['src'];
      final poster = video.attributes['poster'];
      if (url != null && url.isNotEmpty) {
        add(
          TelegramMedia(
            kind: TelegramMediaKind.video,
            remoteId: url,
            thumbnailPath: poster,
          ),
        );
      } else if (poster != null && poster.isNotEmpty) {
        add(
          TelegramMedia(
            kind: TelegramMediaKind.video,
            remoteId: '$dataPost:video:$index',
            thumbnailPath: poster,
          ),
        );
      }
      index += 1;
    }

    for (final document in element.querySelectorAll(
      'a.tgme_widget_message_document',
    )) {
      final href = document.attributes['href'];
      if (href != null && href.isNotEmpty) {
        add(
          TelegramMedia(
            kind: TelegramMediaKind.document,
            remoteId: href,
          ),
        );
      }
    }

    return List<TelegramMedia>.unmodifiable(result);
  }

  static String? _backgroundImageUrl(String? style) {
    if (style == null) return null;
    final match = RegExp(
      r'''background-image\s*:\s*url\((?:'|")?([^'"\)]+)(?:'|")?\)''',
      caseSensitive: false,
    ).firstMatch(style);
    return match?.group(1)?.trim();
  }

  static String? _cleanText(String? value) {
    if (value == null) return null;
    final cleaned = value
        .replaceAll('\u00a0', ' ')
        .replaceAll(RegExp(r'[ \t]+\n'), '\n')
        .replaceAll(RegExp(r'\n{3,}'), '\n\n')
        .trim();
    return cleaned.isEmpty ? null : cleaned;
  }

  static String? _firstNonEmpty(Iterable<String?> values) {
    for (final value in values) {
      final cleaned = _cleanText(value);
      if (cleaned != null) return cleaned;
    }
    return null;
  }
}
