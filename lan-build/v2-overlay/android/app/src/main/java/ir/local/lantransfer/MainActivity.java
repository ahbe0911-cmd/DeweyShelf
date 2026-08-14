package ir.local.lantransfer;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Zapya-inspired connection-first UI (original branding/assets are not copied). */
public class MainActivity extends Activity {
    private static final int PICK_FILES = 100;
    private static final int BLUE = Color.rgb(30, 136, 245);
    private static final int BLUE_DARK = Color.rgb(17, 118, 232);
    private static final int BLUE_SOFT = Color.rgb(231, 242, 255);
    private static final int BG = Color.rgb(244, 247, 251);
    private static final int TEXT = Color.rgb(30, 38, 52);
    private static final int MUTED = Color.rgb(119, 132, 151);
    private static final int BORDER = Color.rgb(225, 231, 239);
    private static final int GREEN = Color.rgb(38, 185, 129);
    private static final int RED = Color.rgb(238, 82, 83);

    private EditText serverField;
    private EditText pinField;
    private TextView stateText;
    private TextView pcText;
    private TextView receivedText;
    private LinearLayout transferContainer;
    private final Map<String, TransferRow> transferRows = new HashMap<>();

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String kind = intent.getStringExtra("kind");
            if ("state".equals(kind)) {
                String s = intent.getStringExtra("state");
                stateText.setText(s == null ? "" : s);
                if (s != null && s.contains("متصل")) stateText.setTextColor(GREEN);
                else stateText.setTextColor(MUTED);
            } else if ("transfer".equals(kind)) {
                updateTransfer(
                        intent.getStringExtra("id"), intent.getStringExtra("name"),
                        intent.getLongExtra("done", 0), intent.getLongExtra("total", 0),
                        intent.getDoubleExtra("speed", 0), intent.getStringExtra("status"));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BLUE_DARK);
        getWindow().setNavigationBarColor(Color.WHITE);
        LanNetwork.bindProcessToWifi(this);
        setContentView(buildUi());
        requestRuntimePermissions();

        if (Prefs.hasPair(this)) {
            serverField.setText(Prefs.server(this));
            pcText.setText("کامپیوتر ذخیره‌شده: " + Prefs.pcName(this));
            stateText.setText("در حال پیدا کردن دوباره کامپیوتر...");
            discoverPc(true);
        } else {
            stateText.setText("برای شروع دکمه Radar را بزنید");
        }
    }

    private View buildUi() {
        ScrollView outer = new ScrollView(this);
        outer.setFillViewport(true);
        outer.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(BG);
        outer.addView(root, matchWrap());

        // SHAREit-like blue hero area, with original LAN SHARE branding.
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(18), dp(20), dp(26));
        hero.setBackground(headerGradient());
        root.addView(hero, matchWrap());

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        hero.addView(brandRow, matchWrap());

        TextView mark = label("↔", 24, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(circleDrawable(Color.argb(48, 255, 255, 255), Color.argb(100, 255, 255, 255), dp(1)));
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        markLp.setMargins(0, 0, dp(10), 0);
        brandRow.addView(mark, markLp);

        LinearLayout brandTexts = new LinearLayout(this);
        brandTexts.setOrientation(LinearLayout.VERTICAL);
        TextView brand = label("LAN SHARE", 24, Color.WHITE, true);
        TextView subtitle = label("انتقال سریع فایل روی Wi‑Fi محلی", 11, Color.argb(225,255,255,255), false);
        brandTexts.addView(brand, matchWrap());
        brandTexts.addView(subtitle, matchWrap());
        brandRow.addView(brandTexts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView secure = label("LAN", 10, BLUE_DARK, true);
        secure.setGravity(Gravity.CENTER);
        secure.setBackground(roundRect(Color.WHITE, dp(15)));
        brandRow.addView(secure, new LinearLayout.LayoutParams(dp(54), dp(32)));

        TextView heroTip = label("گوشی و کامپیوتر فقط باید به یک Wi‑Fi وصل باشند؛ اینترنت لازم نیست.", 11,
                Color.argb(230,255,255,255), false);
        heroTip.setGravity(Gravity.CENTER);
        heroTip.setPadding(0, dp(18), 0, dp(12));
        hero.addView(heroTip, matchWrap());

        Button send = outlineButton("➤   ارسال فایل");
        send.setOnClickListener(v -> pickFiles());
        hero.addView(send, fixedHeightWithBottom(56, 10));

        Button receive = outlineButton("⇩   فایل‌های دریافتی");
        receive.setOnClickListener(v -> refreshReceivedList());
        hero.addView(receive, fixedHeightWithBottom(56, 0));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(24));
        root.addView(content, matchWrap());

        LinearLayout connectCard = card();
        connectCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        content.addView(connectCard, matchWrapWithBottom(12));

        TextView title = label("اتصال به کامپیوتر", 18, TEXT, true);
        title.setGravity(Gravity.CENTER);
        connectCard.addView(title, matchWrap());

        TextView tip = label("Radar کامپیوتر را در شبکه پیدا می‌کند", 10, MUTED, false);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dp(4), 0, dp(12));
        connectCard.addView(tip, matchWrap());

        TextView radar = label("◎\nRADAR", 16, Color.WHITE, true);
        radar.setGravity(Gravity.CENTER);
        radar.setBackground(circleDrawable(BLUE, Color.rgb(115, 181, 250), dp(4)));
        radar.setOnClickListener(v -> discoverPc(false));
        radar.setElevation(dp(3));
        LinearLayout.LayoutParams radarLp = new LinearLayout.LayoutParams(dp(136), dp(136));
        radarLp.gravity = Gravity.CENTER_HORIZONTAL;
        radarLp.setMargins(0, dp(2), 0, dp(12));
        connectCard.addView(radar, radarLp);

        pcText = label("هنوز کامپیوتری انتخاب نشده", 12, TEXT, true);
        pcText.setGravity(Gravity.CENTER);
        connectCard.addView(pcText, matchWrap());

        stateText = label("آماده", 11, MUTED, true);
        stateText.setGravity(Gravity.CENTER);
        stateText.setPadding(0, dp(5), 0, dp(12));
        connectCard.addView(stateText, matchWrap());

        pinField = edit("PIN شش‌رقمی روی کامپیوتر");
        pinField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        connectCard.addView(pinField, fixedHeightWithBottom(52, 9));

        Button connect = button("اتصال / Pair", BLUE);
        connect.setOnClickListener(v -> pairAndConnect());
        connectCard.addView(connect, fixedHeightWithBottom(52, 12));

        TextView manualTitle = label("اتصال دستی، فقط اگر Radar پیدا نکرد", 10, MUTED, false);
        manualTitle.setPadding(dp(2), 0, dp(2), dp(5));
        connectCard.addView(manualTitle, matchWrap());

        serverField = edit("192.168.1.10:8765");
        connectCard.addView(serverField, fixedHeightWithBottom(52, 0));

        LinearLayout activityCard = card();
        activityCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        content.addView(activityCard, matchWrapWithBottom(12));

        LinearLayout activityHeader = new LinearLayout(this);
        activityHeader.setOrientation(LinearLayout.HORIZONTAL);
        activityHeader.setGravity(Gravity.CENTER_VERTICAL);
        activityCard.addView(activityHeader, matchWrap());

        TextView transfersTitle = label("انتقال‌ها", 17, TEXT, true);
        activityHeader.addView(transfersTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView storageBadge = label("Downloads/FileTransfer", 9, BLUE, true);
        storageBadge.setGravity(Gravity.CENTER);
        storageBadge.setPadding(dp(10), dp(6), dp(10), dp(6));
        storageBadge.setBackground(roundRect(BLUE_SOFT, dp(14)));
        activityHeader.addView(storageBadge, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        receivedText = label("هنوز فایلی دریافت نشده است.", 10, MUTED, false);
        receivedText.setPadding(dp(12), dp(11), dp(12), dp(11));
        receivedText.setBackground(roundRect(Color.rgb(248, 250, 253), dp(12)));
        LinearLayout.LayoutParams receivedLp = matchWrapWithBottom(10);
        receivedLp.setMargins(0, dp(10), 0, dp(10));
        activityCard.addView(receivedText, receivedLp);

        transferContainer = new LinearLayout(this);
        transferContainer.setOrientation(LinearLayout.VERTICAL);
        activityCard.addView(transferContainer, matchWrap());

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER);
        content.addView(tools, matchWrap());

        Button forget = smallButton("فراموش کردن اتصال");
        forget.setOnClickListener(v -> {
            Prefs.clearPair(this);
            stateText.setTextColor(MUTED);
            stateText.setText("اتصال ذخیره‌شده پاک شد");
            pcText.setText("هنوز کامپیوتری انتخاب نشده");
        });
        LinearLayout.LayoutParams toolLp1 = new LinearLayout.LayoutParams(0, dp(46), 1);
        toolLp1.setMargins(dp(4), 0, dp(4), 0);
        tools.addView(forget, toolLp1);

        Button settings = smallButton("تنظیمات برنامه");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        LinearLayout.LayoutParams toolLp2 = new LinearLayout.LayoutParams(0, dp(46), 1);
        toolLp2.setMargins(dp(4), 0, dp(4), 0);
        tools.addView(settings, toolLp2);

        return outer;
    }

    private void discoverPc(boolean autoConnect) {
        stateText.setTextColor(MUTED);
        stateText.setText("Radar: در حال جست‌وجوی کامپیوتر...");
        String expected = Prefs.hasPair(this) ? Prefs.instanceId(this) : "";
        DiscoveryScanner.discover(this, 8765, 8766, expected, new DiscoveryScanner.Callback() {
            @Override
            public void onFound(DiscoveryScanner.Result result) {
                runOnUiThread(() -> {
                    serverField.setText(result.baseUrl);
                    pcText.setText("پیدا شد: " + result.deviceName + "  •  " + result.baseUrl.replace("http://", ""));
                    stateText.setTextColor(GREEN);
                    if (Prefs.hasPair(MainActivity.this) && autoConnect) {
                        Prefs.updateDiscovery(MainActivity.this, result.baseUrl, result.instanceId, result.deviceName);
                        stateText.setText("پیدا شد؛ در حال اتصال خودکار...");
                        startTransferService(TransferService.ACTION_CONNECT, null);
                    } else {
                        stateText.setText("پیدا شد؛ PIN را وارد و اتصال را بزنید");
                    }
                });
            }

            @Override
            public void onNotFound(String message) {
                runOnUiThread(() -> {
                    stateText.setTextColor(RED);
                    stateText.setText(message);
                });
            }
        });
    }

    private void pairAndConnect() {
        String base = ApiClient.normalizeBase(serverField.getText().toString());
        String pin = pinField.getText().toString().trim();
        if (base.isEmpty()) {
            toast("ابتدا Radar را بزنید یا IP کامپیوتر را وارد کنید");
            return;
        }
        stateText.setTextColor(MUTED);
        stateText.setText("در حال Pair...");
        new Thread(() -> {
            try {
                JSONObject result = ApiClient.pair(base, pin, Build.MANUFACTURER + " " + Build.MODEL);
                String deviceId = result.getString("device_id");
                String token = result.getString("token");
                String instanceId = result.optString("instance_id", "");
                String pcName = result.optString("pc_name", "Windows PC");
                Prefs.savePair(this, base, deviceId, token, instanceId, pcName);
                runOnUiThread(() -> {
                    pcText.setText("متصل به: " + pcName);
                    stateText.setTextColor(GREEN);
                    stateText.setText("Pair موفق؛ در حال اتصال...");
                });
                startTransferService(TransferService.ACTION_CONNECT, null);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    stateText.setTextColor(RED);
                    stateText.setText("Pair ناموفق: " + readableNetworkError(e));
                });
            }
        }, "pair-thread").start();
    }

    private String readableNetworkError(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (m.contains("failed to connect") || m.contains("timed out") || m.contains("timeout"))
            return "PC در شبکه قابل دسترسی نیست؛ Firewall یا Wi‑Fi را بررسی کنید";
        return m;
    }

    private void pickFiles() {
        if (!Prefs.hasPair(this)) { toast("ابتدا به کامپیوتر وصل شوید"); return; }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_FILES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FILES || resultCode != RESULT_OK || data == null) return;
        ArrayList<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri();
                uris.add(uri); persistReadPermission(uri, data.getFlags());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData()); persistReadPermission(data.getData(), data.getFlags());
        }
        startTransferService(TransferService.ACTION_UPLOAD, uris);
    }

    private void persistReadPermission(Uri uri, int flags) {
        try {
            int take = flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, take & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
    }

    private void startTransferService(String action, ArrayList<Uri> uris) {
        Intent service = new Intent(this, TransferService.class).setAction(action);
        if (uris != null) service.putParcelableArrayListExtra(TransferService.EXTRA_URIS, uris);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
    }

    private void updateTransfer(String id, String name, long done, long total, double speed, String status) {
        if (id == null) id = "unknown";
        TransferRow row = transferRows.get(id);
        if (row == null) {
            row = new TransferRow(this, name == null ? "file" : name);
            transferRows.put(id, row);
            transferContainer.addView(row.root, 0);
        }
        row.update(done, total, speed, status == null ? "" : status);
    }

    private final class TransferRow {
        final LinearLayout root;
        final TextView title;
        final ProgressBar progress;
        final TextView meta;

        TransferRow(Context c, String name) {
            root = card();
            root.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams lp = matchWrapWithBottom(8);
            root.setLayoutParams(lp);
            title = label(name, 11, TEXT, true);
            root.addView(title, matchWrap());
            progress = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(1000);
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));
            root.addView(progress, matchWrap());
            meta = label("", 9, MUTED, false);
            root.addView(meta, matchWrap());
        }

        void update(long done, long total, double speed, String status) {
            int pct = total <= 0 ? 0 : (int) Math.min(1000, done * 1000L / total);
            progress.setProgress(pct);
            String speedText = speed > 0 ? humanBytes(speed) + "/s" : "—";
            meta.setText(String.format(java.util.Locale.US, "%s  •  %.1f%%  •  %s  •  ETA %s",
                    status, pct / 10.0, speedText, eta(done, total, speed)));
        }
    }

    private void refreshReceivedList() {
        new Thread(() -> {
            java.util.List<String> names = StorageHelper.listReceivedNames(this);
            String text = names.isEmpty() ? "هنوز فایلی دریافت نشده است."
                    : "Downloads/FileTransfer\n• " + String.join("\n• ", names);
            runOnUiThread(() -> receivedText.setText(text));
        }, "received-list").start();
    }

    private void requestRuntimePermissions() {
        ArrayList<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (!permissions.isEmpty()) requestPermissions(permissions.toArray(new String[0]), 501);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(TransferService.EVENT_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
    }

    @Override
    protected void onStop() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        super.onStop();
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(roundRect(Color.WHITE, dp(20)));
        v.setElevation(dp(2));
        return v;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setFontFeatureSettings("kern");
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(155, 166, 181));
        e.setTextColor(TEXT);
        e.setTextSize(12);
        e.setSingleLine(true);
        e.setPadding(dp(13), 0, dp(13), 0);
        GradientDrawable bg = roundRect(Color.rgb(248, 250, 253), dp(13));
        bg.setStroke(dp(1), BORDER);
        e.setBackground(bg);
        e.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        return e;
    }

    private Button button(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackground(roundRect(color, dp(27)));
        b.setElevation(0);
        return b;
    }

    private Button outlineButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable bg = roundRect(Color.argb(18, 255, 255, 255), dp(28));
        bg.setStroke(dp(2), Color.argb(225, 255, 255, 255));
        b.setBackground(bg);
        b.setElevation(0);
        return b;
    }

    private Button smallButton(String text) {
        Button b = button(text, Color.WHITE);
        b.setTextColor(BLUE);
        b.setTextSize(10);
        GradientDrawable bg = roundRect(Color.WHITE, dp(23));
        bg.setStroke(dp(1), BORDER);
        b.setBackground(bg);
        return b;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private GradientDrawable headerGradient() {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(34, 146, 255), Color.rgb(16, 119, 235)});
        d.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return d;
    }

    private GradientDrawable circleDrawable(int fill, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        d.setStroke(strokeWidth, stroke);
        return d;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapWithBottom(int bottomDp) {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, 0, 0, dp(bottomDp));
        return lp;
    }

    private LinearLayout.LayoutParams fixedHeightWithBottom(int heightDp, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp));
        lp.setMargins(0, 0, 0, dp(bottomDp));
        return lp;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private static String humanBytes(double v) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int i = 0;
        while (Math.abs(v) >= 1024 && i < units.length - 1) { v /= 1024; i++; }
        return String.format(java.util.Locale.US, "%.1f %s", v, units[i]);
    }

    private static String eta(long done, long total, double speed) {
        if (speed <= 1 || total <= done) return "—";
        long sec = (long) ((total - done) / speed);
        if (sec < 60) return sec + "s";
        return (sec / 60) + "m " + (sec % 60) + "s";
    }
}
