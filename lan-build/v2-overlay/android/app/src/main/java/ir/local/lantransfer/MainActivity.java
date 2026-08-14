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
    private static final int NAVY = Color.rgb(16, 25, 54);
    private static final int CARD = Color.rgb(26, 40, 80);
    private static final int BLUE = Color.rgb(82, 107, 244);
    private static final int CORAL = Color.rgb(255, 91, 101);
    private static final int MUTED = Color.rgb(151, 163, 198);
    private static final int GREEN = Color.rgb(77, 214, 168);

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
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
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
        outer.setBackgroundColor(NAVY);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(NAVY);
        outer.addView(root, matchWrap());

        TextView brand = label("LAN SHARE", 24, Color.WHITE, true);
        brand.setGravity(Gravity.CENTER);
        root.addView(brand, matchWrap());
        TextView subtitle = label("انتقال سریع فایل در شبکه محلی", 12, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(4), 0, dp(16));
        root.addView(subtitle, matchWrap());

        LinearLayout connectCard = card();
        root.addView(connectCard, matchWrapWithBottom(14));
        TextView title = label("اتصال به کامپیوتر", 18, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(16), 0, dp(4));
        connectCard.addView(title, matchWrap());
        TextView tip = label("گوشی و PC روی یک Wi‑Fi باشند؛ اینترنت لازم نیست", 10, MUTED, false);
        tip.setGravity(Gravity.CENTER);
        connectCard.addView(tip, matchWrap());

        TextView radar = label("◎\nRADAR", 18, Color.WHITE, true);
        radar.setGravity(Gravity.CENTER);
        radar.setBackground(circleDrawable(BLUE, Color.rgb(110, 130, 255), dp(3)));
        radar.setOnClickListener(v -> discoverPc(false));
        LinearLayout.LayoutParams radarLp = new LinearLayout.LayoutParams(dp(170), dp(170));
        radarLp.gravity = Gravity.CENTER_HORIZONTAL;
        radarLp.setMargins(0, dp(14), 0, dp(10));
        connectCard.addView(radar, radarLp);

        pcText = label("هنوز کامپیوتری انتخاب نشده", 11, Color.WHITE, true);
        pcText.setGravity(Gravity.CENTER);
        connectCard.addView(pcText, matchWrap());
        stateText = label("آماده", 11, MUTED, true);
        stateText.setGravity(Gravity.CENTER);
        stateText.setPadding(0, dp(5), 0, dp(14));
        connectCard.addView(stateText, matchWrap());

        LinearLayout pairCard = card();
        pairCard.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(pairCard, matchWrapWithBottom(14));
        pinField = edit("PIN شش‌رقمی روی کامپیوتر");
        pinField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        pairCard.addView(pinField, matchWrapWithBottom(8));
        Button connect = button("اتصال / Pair", CORAL);
        connect.setOnClickListener(v -> pairAndConnect());
        pairCard.addView(connect, matchWrapWithBottom(10));

        TextView manualTitle = label("اتصال دستی (در صورت نیاز)", 10, MUTED, false);
        pairCard.addView(manualTitle, matchWrap());
        serverField = edit("مثال: 192.168.1.10:8765");
        pairCard.addView(serverField, matchWrap());

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        root.addView(actionRow, matchWrapWithBottom(14));

        Button send = button("ارسال فایل", CORAL);
        send.setOnClickListener(v -> pickFiles());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(58), 1);
        half.setMargins(dp(4), 0, dp(4), 0);
        actionRow.addView(send, half);

        Button received = button("دریافتی‌ها", BLUE);
        received.setOnClickListener(v -> refreshReceivedList());
        actionRow.addView(received, half);

        receivedText = label("فایل‌های دریافتی: Downloads/FileTransfer", 10, MUTED, false);
        receivedText.setPadding(dp(10), dp(10), dp(10), dp(10));
        receivedText.setBackground(roundRect(Color.rgb(23, 36, 72), dp(12)));
        root.addView(receivedText, matchWrapWithBottom(12));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        Button forget = smallButton("فراموش کردن اتصال");
        forget.setOnClickListener(v -> {
            Prefs.clearPair(this);
            stateText.setText("اتصال ذخیره‌شده پاک شد");
            pcText.setText("هنوز کامپیوتری انتخاب نشده");
        });
        tools.addView(forget, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button settings = smallButton("تنظیمات برنامه");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        tools.addView(settings, new LinearLayout.LayoutParams(0, dp(44), 1));
        root.addView(tools, matchWrapWithBottom(14));

        TextView transfersTitle = label("انتقال‌ها", 16, Color.WHITE, true);
        transfersTitle.setPadding(0, dp(4), 0, dp(8));
        root.addView(transfersTitle, matchWrap());
        transferContainer = new LinearLayout(this);
        transferContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(transferContainer, matchWrap());

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
                        Prefs.updateServer(MainActivity.this, result.baseUrl);
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
                    stateText.setTextColor(CORAL);
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
                    stateText.setTextColor(CORAL);
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
            title = label(name, 11, Color.WHITE, true);
            root.addView(title, matchWrap());
            progress = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(1000);
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(CORAL));
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
        v.setBackground(roundRect(CARD, dp(18)));
        return v;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setHintTextColor(MUTED); e.setTextColor(Color.WHITE);
        e.setSingleLine(true); e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(roundRect(Color.rgb(23, 36, 72), dp(12)));
        e.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        return e;
    }

    private Button button(String text, int color) {
        Button b = new Button(this);
        b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setAllCaps(false);
        b.setBackground(roundRect(color, dp(16)));
        return b;
    }

    private Button smallButton(String text) {
        Button b = button(text, Color.rgb(23, 36, 72));
        b.setTextSize(10);
        return b;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(radius);
        return d;
    }

    private GradientDrawable circleDrawable(int fill, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL); d.setColor(fill); d.setStroke(strokeWidth, stroke);
        return d;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapWithBottom(int bottomDp) {
        LinearLayout.LayoutParams lp = matchWrap();
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
