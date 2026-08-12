import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:khabar_cafe/features/feed/domain/post_model.dart';
import 'package:khabar_cafe/features/public_web/data/public_telegram_web_gateway.dart';

void main() {
  test('parses public Telegram message text, date and photo', () async {
    final client = MockClient((request) async {
      expect(request.url.toString(), 'https://t.me/s/sample_channel');
      return http.Response('''
        <html><body>
          <div class="tgme_channel_info">
            <div class="tgme_channel_info_header_title">کانال نمونه</div>
          </div>
          <div class="tgme_widget_message" data-post="sample_channel/42">
            <div class="tgme_widget_message_text">سلام از کانال عمومی</div>
            <a class="tgme_widget_message_photo_wrap"
               style="background-image:url('https://cdn.example/photo.jpg')"></a>
            <time datetime="2026-08-12T10:20:00+00:00"></time>
          </div>
        </body></html>
      ''', 200, headers: {'content-type': 'text/html; charset=utf-8'});
    });

    final gateway = PublicTelegramWebGateway(client: client);
    final posts = await gateway.syncChannel('sample_channel');

    expect(posts, hasLength(1));
    expect(posts.single.telegramMessageId, 42);
    expect(posts.single.text, 'سلام از کانال عمومی');
    expect(posts.single.media, hasLength(1));
    expect(posts.single.media.single.kind, TelegramMediaKind.photo);
    expect(posts.single.media.single.remoteId, 'https://cdn.example/photo.jpg');
    gateway.dispose();
  });
}
