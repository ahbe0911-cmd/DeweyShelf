package ir.local.lantransfer;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LAN SHARE Android shell.
 * Business/network logic remains in ApiClient, DiscoveryScanner and TransferService.
 * This activity owns only presentation, navigation and user interaction.
 */
public class MainActivity extends Activity {
    private static final int PICK_FILES = 100;
    private static final int OPEN_RECEIVED_FOLDER = 101;

    private static final int PAGE_HOME = 0;
    private static final int PAGE_TRANSFERS = 1;
    private static final int PAGE_RECEIVED = 2;
    private static final int PAGE_SETTINGS = 3;

    private FrameLayout pageHost;
    private int currentPage = PAGE_HOME;
    private final Map<Integer, NavRef> navRefs = new LinkedHashMap<>();

    private EditText serverField;
    private EditText pinField;
    private TextView stateText;
    private TextView pcText;
    private TextView topStatusText;
    private LinearLayout transferContainer;
    private LinearLayout recentTransferContainer;
    private LinearLayout receivedContainer;

    private String currentServer = "";
    private String connectionState = "آماده";
    private int connectionStateColor = DesignTokens.TEXT_SECONDARY;
    private String pcDisplay = "هنوز کامپیوتری انتخاب نشده";

    private final Map<String, TransferSnapshot> transferSnapshots = new LinkedHashMap<>();
    private final Map<String, TransferRow> transferRows = new LinkedHashMap<>();

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String kind = intent.getStringExtra("kind");
            if ("state".equals(kind)) {
                String value = intent.getStringExtra("state");
                if (value != null) {
                    connectionState = value;
                    if (value.contains("متصل")) connectionStateColor = DesignTokens.SUCCESS;
                    else if (value.contains("ناموفق") || value.contains("قطع") || value.contains("خطا"))
                        connectionStateColor = DesignTokens.ERROR;
                    else connectionStateColor = DesignTokens.TEXT_SECONDARY;
                    refreshConnectionViews();
                }
            } else if ("transfer".equals(kind)) {
                updateTransfer(
                        intent.getStringExtra("id"),
                        intent.getStringExtra("name"),
                        intent.getLongExtra("done", 0),
                        intent.getLongExtra("total", 0),
                        intent.getDoubleExtra("speed", 0),
                        intent.getStringExtra("status"));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(DesignTokens.PRIMARY_DARK);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LanNetwork.bindProcessToWifi(this);

        if (Prefs.hasPair(this)) {
            currentServer = Prefs.server(this);
            pcDisplay = Prefs.pcName(this);
            connectionState = "در حال پیدا کردن دوباره کامپیوتر...";
        }

        setContentView(buildShell());
        requestRuntimePermissions();
        showPage(PAGE_HOME);

        if (Prefs.hasPair(this)) discoverPc(true);
    }

    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(DesignTokens.BACKGROUND);

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(66)));

        pageHost = new FrameLayout(this);
        root.addView(pageHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(buildBottomNavigation(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(68)));
        return root;
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), 0, dp(16), 0);
        bar.setBackgroundColor(DesignTokens.PRIMARY);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        ImageView logo = iconView(R.drawable.ic_transfer, Color.WHITE, "LAN SHARE");
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        logoLp.setMargins(dp(10), 0, 0, 0);
        bar.addView(logo, logoLp);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView brand = text("LAN SHARE", 19, Color.WHITE, true);
        TextView sub = text("انتقال فایل در Wi‑Fi محلی", 10, Color.argb(220,255,255,255), false);
        titles.addView(brand);
        titles.addView(sub);
        bar.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        topStatusText = text(Prefs.hasPair(this) ? "در حال اتصال" : "آماده", 10, Color.WHITE, true);
        topStatusText.setGravity(Gravity.CENTER);
        topStatusText.setPadding(dp(10), dp(7), dp(10), dp(7));
        topStatusText.setBackground(roundRect(Color.argb(38,255,255,255), dp(20)));
        bar.addView(topStatusText);
        return bar;
    }

    private View buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(8));
        nav.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        addNav(nav, PAGE_HOME, R.drawable.ic_home, "خانه");
        addNav(nav, PAGE_TRANSFERS, R.drawable.ic_transfer, "انتقال‌ها");
        addNav(nav, PAGE_RECEIVED, R.drawable.ic_folder, "دریافتی‌ها");
        addNav(nav, PAGE_SETTINGS, R.drawable.ic_settings, "تنظیمات");
        return nav;
    }

    private void addNav(LinearLayout parent, int page, int iconRes, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(4), dp(6), dp(4), dp(4));
        item.setOnClickListener(v -> showPage(page));
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(label);

        ImageView icon = iconView(iconRes, DesignTokens.TEXT_SECONDARY, label);
        item.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView text = text(label, 10, DesignTokens.TEXT_SECONDARY, false);
        text.setGravity(Gravity.CENTER);
        item.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        parent.addView(item, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        navRefs.put(page, new NavRef(icon, text));
    }

    private void showPage(int page) {
        currentPage = page;
        transferContainer = null;
        recentTransferContainer = null;
        receivedContainer = null;
        stateText = null;
        pcText = null;
        serverField = null;
        pinField = null;
        transferRows.clear();

        pageHost.removeAllViews();
        View pageView;
        if (page == PAGE_TRANSFERS) pageView = buildTransfersPage();
        else if (page == PAGE_RECEIVED) pageView = buildReceivedPage();
        else if (page == PAGE_SETTINGS) pageView = buildSettingsPage();
        else pageView = buildHomePage();
        pageHost.addView(pageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        updateNavSelection();
    }

    private void updateNavSelection() {
        for (Map.Entry<Integer, NavRef> entry : navRefs.entrySet()) {
            boolean selected = entry.getKey() == currentPage;
            int color = selected ? DesignTokens.PRIMARY : DesignTokens.TEXT_SECONDARY;
            entry.getValue().icon.setColorFilter(color);
            entry.getValue().label.setTextColor(color);
            entry.getValue().label.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private View buildHomePage() {
        ScrollView scroll = pageScroll();
        LinearLayout content = pageContent(scroll);

        TextView pageTitle = text("انتقال فایل", 23, DesignTokens.TEXT_PRIMARY, true);
        pageTitle.setPadding(0, 0, 0, dp(4));
        content.addView(pageTitle);
        TextView description = text("ارسال و دریافت مستقیم، بدون اینترنت و سرور واسط", 11,
                DesignTokens.TEXT_SECONDARY, false);
        content.addView(description, matchBottom(18));

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(Gravity.CENTER);
        quick.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.addView(quick, matchBottom(16));
        quick.addView(actionTile(R.drawable.ic_send, "ارسال فایل", "انتخاب چند فایل", v -> pickFiles()), weightedTile(8));
        quick.addView(actionTile(R.drawable.ic_folder, "دریافتی‌ها", "مشاهده فایل‌ها", v -> showPage(PAGE_RECEIVED)), weightedTile(0));

        LinearLayout connection = surface();
        connection.setPadding(dp(16), dp(16), dp(16), dp(16));
        content.addView(connection, matchBottom(16));

        LinearLayout connectionHeader = row();
        connection.addView(connectionHeader, match());
        TextView connTitle = text("اتصال به کامپیوتر", 16, DesignTokens.TEXT_PRIMARY, true);
        connectionHeader.addView(connTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView lanBadge = badge("LAN", DesignTokens.PRIMARY_CONTAINER, DesignTokens.PRIMARY);
        connectionHeader.addView(lanBadge);

        pcText = text(pcDisplay, 13, DesignTokens.TEXT_PRIMARY, true);
        pcText.setPadding(0, dp(12), 0, 0);
        connection.addView(pcText, match());
        stateText = text(connectionState, 11, connectionStateColor, true);
        stateText.setPadding(0, dp(4), 0, dp(12));
        connection.addView(stateText, match());

        if (Prefs.hasPair(this)) {
            TextView address = text("آدرس: " + displayServer(Prefs.server(this)), 10, DesignTokens.TEXT_SECONDARY, false);
            connection.addView(address, matchBottom(12));

            LinearLayout actions = row();
            Button rediscover = secondaryButton("جست‌وجوی دوباره");
            rediscover.setOnClickListener(v -> discoverPc(true));
            actions.addView(rediscover, weightedButton(8));
            Button change = ghostButton("تغییر کامپیوتر");
            change.setOnClickListener(v -> forgetPairAndRefresh());
            actions.addView(change, weightedButton(0));
            connection.addView(actions, match());
        } else {
            LinearLayout radarRow = row();
            ImageView radarIcon = iconView(R.drawable.ic_radar, DesignTokens.PRIMARY, "Radar");
            radarRow.addView(radarIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));
            TextView radarHelp = text("Radar دستگاه‌های LAN SHARE روی همین Wi‑Fi را پیدا می‌کند.", 10,
                    DesignTokens.TEXT_SECONDARY, false);
            radarHelp.setPadding(dp(10), 0, 0, 0);
            radarRow.addView(radarHelp, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            Button radar = secondaryButton("جست‌وجو");
            radar.setOnClickListener(v -> discoverPc(false));
            radarRow.addView(radar);
            connection.addView(radarRow, matchBottom(12));

            pinField = input("PIN شش‌رقمی روی کامپیوتر", true);
            connection.addView(pinField, fixedBottom(52, 10));
            Button pair = primaryButton("اتصال امن");
            pair.setOnClickListener(v -> pairAndConnect());
            connection.addView(pair, fixedBottom(52, 12));

            TextView manual = text("اگر Radar نتیجه نداد، IP کامپیوتر را دستی وارد کنید.", 9,
                    DesignTokens.TEXT_SECONDARY, false);
            connection.addView(manual, matchBottom(6));
            serverField = input("192.168.1.10:8765", false);
            if (!currentServer.isEmpty()) serverField.setText(currentServer);
            connection.addView(serverField, fixedBottom(52, 0));
        }

        TextView recentTitle = text("آخرین فعالیت", 15, DesignTokens.TEXT_PRIMARY, true);
        content.addView(recentTitle, matchBottom(8));
        recentTransferContainer = new LinearLayout(this);
        recentTransferContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(recentTransferContainer, match());
        renderRecentTransfers();
        return scroll;
    }

    private View buildTransfersPage() {
        ScrollView scroll = pageScroll();
        LinearLayout content = pageContent(scroll);
        content.addView(text("انتقال‌ها", 23, DesignTokens.TEXT_PRIMARY, true));
        content.addView(text("پیشرفت، سرعت و زمان باقی‌مانده انتقال‌های این نشست", 11,
                DesignTokens.TEXT_SECONDARY, false), matchBottom(16));

        transferContainer = new LinearLayout(this);
        transferContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(transferContainer, match());
        if (transferSnapshots.isEmpty()) {
            transferContainer.addView(emptyState(R.drawable.ic_transfer, "هنوز انتقالی ندارید",
                    "از صفحه خانه یک یا چند فایل انتخاب کنید."));
        } else {
            for (TransferSnapshot snapshot : transferSnapshots.values()) ensureTransferRow(snapshot);
        }
        return scroll;
    }

    private View buildReceivedPage() {
        ScrollView scroll = pageScroll();
        LinearLayout content = pageContent(scroll);
        content.addView(text("فایل‌های دریافتی", 23, DesignTokens.TEXT_PRIMARY, true));
        content.addView(text("فایل‌ها در Downloads/FileTransfer ذخیره می‌شوند.", 11,
                DesignTokens.TEXT_SECONDARY, false), matchBottom(14));

        Button openFolder = primaryButton("باز کردن پوشه در فایل‌منیجر");
        openFolder.setOnClickListener(v -> openReceivedFolder());
        content.addView(openFolder, fixedBottom(52, 10));

        Button refresh = secondaryButton("تازه‌سازی لیست");
        refresh.setOnClickListener(v -> loadReceivedFiles());
        content.addView(refresh, fixedBottom(48, 18));

        receivedContainer = new LinearLayout(this);
        receivedContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(receivedContainer, match());
        loadReceivedFiles();
        return scroll;
    }

    private View buildSettingsPage() {
        ScrollView scroll = pageScroll();
        LinearLayout content = pageContent(scroll);
        content.addView(text("تنظیمات", 23, DesignTokens.TEXT_PRIMARY, true));
        content.addView(text("تنظیمات اتصال و دسترسی‌های برنامه", 11,
                DesignTokens.TEXT_SECONDARY, false), matchBottom(16));

        LinearLayout connection = surface();
        connection.setPadding(dp(16), dp(16), dp(16), dp(16));
        content.addView(connection, matchBottom(12));
        connection.addView(text("اتصال ذخیره‌شده", 15, DesignTokens.TEXT_PRIMARY, true), matchBottom(10));
        connection.addView(settingLine("کامپیوتر", Prefs.hasPair(this) ? Prefs.pcName(this) : "ثبت نشده"));
        connection.addView(settingLine("آدرس", Prefs.hasPair(this) ? displayServer(Prefs.server(this)) : "—"));
        connection.addView(settingLine("حالت", "شبکه محلی / بدون اینترنت"));

        Button forget = dangerGhostButton("فراموش کردن اتصال");
        forget.setOnClickListener(v -> forgetPairAndRefresh());
        content.addView(forget, fixedBottom(50, 10));

        Button appSettings = secondaryButton("مجوزها و تنظیمات Android");
        appSettings.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        content.addView(appSettings, fixedBottom(50, 12));

        LinearLayout about = surface();
        about.setPadding(dp(16), dp(16), dp(16), dp(16));
        about.addView(text("LAN SHARE 2.2", 14, DesignTokens.TEXT_PRIMARY, true));
        about.addView(text("انتقال مستقیم فایل بین Android و Windows روی Wi‑Fi محلی", 10,
                DesignTokens.TEXT_SECONDARY, false));
        content.addView(about, match());
        return scroll;
    }

    private View actionTile(int iconRes, String title, String subtitle, View.OnClickListener listener) {
        LinearLayout tile = surface();
        tile.setPadding(dp(14), dp(14), dp(14), dp(14));
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setOnClickListener(listener);
        tile.setClickable(true);
        tile.setFocusable(true);

        ImageView icon = iconView(iconRes, DesignTokens.PRIMARY, title);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView t = text(title, 13, DesignTokens.TEXT_PRIMARY, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(8), 0, 0);
        tile.addView(t, match());
        TextView s = text(subtitle, 9, DesignTokens.TEXT_SECONDARY, false);
        s.setGravity(Gravity.CENTER);
        tile.addView(s, match());
        return tile;
    }

    private View settingLine(String label, String value) {
        LinearLayout row = row();
        row.setPadding(0, dp(5), 0, dp(5));
        TextView l = text(label, 10, DesignTokens.TEXT_SECONDARY, false);
        row.addView(l, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView v = text(value, 10, DesignTokens.TEXT_PRIMARY, true);
        v.setGravity(Gravity.END);
        row.addView(v);
        return row;
    }

    private void discoverPc(boolean autoConnect) {
        setConnectionState("در حال جست‌وجوی کامپیوتر...", DesignTokens.TEXT_SECONDARY);
        String expected = Prefs.hasPair(this) ? Prefs.instanceId(this) : "";
        DiscoveryScanner.discover(this, 8765, 8766, expected, new DiscoveryScanner.Callback() {
            @Override
            public void onFound(DiscoveryScanner.Result result) {
                runOnUiThread(() -> {
                    currentServer = result.baseUrl;
                    if (serverField != null) serverField.setText(result.baseUrl);
                    pcDisplay = result.deviceName;
                    if (Prefs.hasPair(MainActivity.this) && autoConnect) {
                        Prefs.updateDiscovery(MainActivity.this, result.baseUrl, result.instanceId, result.deviceName);
                        setConnectionState("پیدا شد؛ در حال اتصال...", DesignTokens.SUCCESS);
                        startTransferService(TransferService.ACTION_CONNECT, null);
                    } else {
                        setConnectionState("کامپیوتر پیدا شد؛ PIN را وارد کنید", DesignTokens.SUCCESS);
                    }
                    refreshConnectionViews();
                });
            }

            @Override
            public void onNotFound(String message) {
                runOnUiThread(() -> setConnectionState(message, DesignTokens.ERROR));
            }
        });
    }

    private void pairAndConnect() {
        String rawBase = serverField != null ? serverField.getText().toString() : currentServer;
        String base = ApiClient.normalizeBase(rawBase);
        String pin = pinField == null ? "" : pinField.getText().toString().trim();
        if (base.isEmpty()) {
            toast("ابتدا جست‌وجو را بزنید یا IP کامپیوتر را وارد کنید");
            return;
        }
        if (pin.isEmpty()) {
            toast("PIN نمایش‌داده‌شده روی کامپیوتر را وارد کنید");
            return;
        }
        setConnectionState("در حال اتصال امن...", DesignTokens.TEXT_SECONDARY);
        new Thread(() -> {
            try {
                JSONObject result = ApiClient.pair(base, pin, Build.MANUFACTURER + " " + Build.MODEL);
                String deviceId = result.getString("device_id");
                String token = result.getString("token");
                String instanceId = result.optString("instance_id", "");
                String pcName = result.optString("pc_name", "Windows PC");
                Prefs.savePair(this, base, deviceId, token, instanceId, pcName);
                currentServer = base;
                pcDisplay = pcName;
                runOnUiThread(() -> {
                    setConnectionState("اتصال ثبت شد؛ در حال آماده‌سازی...", DesignTokens.SUCCESS);
                    showPage(PAGE_HOME);
                });
                startTransferService(TransferService.ACTION_CONNECT, null);
            } catch (Exception e) {
                runOnUiThread(() -> setConnectionState("اتصال ناموفق: " + readableNetworkError(e), DesignTokens.ERROR));
            }
        }, "pair-thread").start();
    }

    private void forgetPairAndRefresh() {
        Prefs.clearPair(this);
        currentServer = "";
        pcDisplay = "هنوز کامپیوتری انتخاب نشده";
        setConnectionState("اتصال ذخیره‌شده پاک شد", DesignTokens.TEXT_SECONDARY);
        showPage(PAGE_HOME);
    }

    private String readableNetworkError(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (m.contains("failed to connect") || m.contains("timed out") || m.contains("timeout"))
            return "کامپیوتر در شبکه قابل دسترسی نیست؛ Wi‑Fi و Firewall را بررسی کنید";
        return m;
    }

    private void pickFiles() {
        if (!Prefs.hasPair(this)) {
            toast("ابتدا به کامپیوتر وصل شوید");
            showPage(PAGE_HOME);
            return;
        }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_FILES);
    }

    private void openReceivedFolder() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            Uri initial = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", "primary:Download/FileTransfer");
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            startActivityForResult(intent, OPEN_RECEIVED_FOLDER);
        } catch (Exception primary) {
            try {
                Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(fallback, OPEN_RECEIVED_FOLDER);
            } catch (Exception ignored) {
                toast("فایل‌منیجر سازگار روی دستگاه پیدا نشد");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_RECEIVED_FOLDER) return;
        if (requestCode != PICK_FILES || resultCode != RESULT_OK || data == null) return;
        ArrayList<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri();
                uris.add(uri);
                persistReadPermission(uri, data.getFlags());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
            persistReadPermission(data.getData(), data.getFlags());
        }
        if (!uris.isEmpty()) {
            startTransferService(TransferService.ACTION_UPLOAD, uris);
            showPage(PAGE_TRANSFERS);
        }
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
        TransferSnapshot snapshot = transferSnapshots.get(id);
        if (snapshot == null) {
            snapshot = new TransferSnapshot(id, name == null ? "file" : name);
            transferSnapshots.put(id, snapshot);
        }
        snapshot.done = done;
        snapshot.total = total;
        snapshot.speed = speed;
        snapshot.status = status == null ? "" : status;

        if (currentPage == PAGE_TRANSFERS && transferContainer != null) {
            TransferRow row = ensureTransferRow(snapshot);
            row.update(snapshot);
        } else if (currentPage == PAGE_HOME && recentTransferContainer != null) {
            renderRecentTransfers();
        }
    }

    private TransferRow ensureTransferRow(TransferSnapshot snapshot) {
        TransferRow row = transferRows.get(snapshot.id);
        if (row == null && transferContainer != null) {
            row = new TransferRow(this, snapshot.name);
            transferRows.put(snapshot.id, row);
            transferContainer.addView(row.root, 0, matchBottom(8));
        }
        if (row != null) row.update(snapshot);
        return row;
    }

    private void renderRecentTransfers() {
        if (recentTransferContainer == null) return;
        recentTransferContainer.removeAllViews();
        if (transferSnapshots.isEmpty()) {
            recentTransferContainer.addView(emptyState(R.drawable.ic_transfer, "هنوز فعالیتی ثبت نشده",
                    "پس از ارسال یا دریافت، وضعیت انتقال اینجا نمایش داده می‌شود."));
            return;
        }
        List<TransferSnapshot> list = new ArrayList<>(transferSnapshots.values());
        int start = Math.max(0, list.size() - 3);
        for (int i = list.size() - 1; i >= start; i--) {
            TransferSnapshot s = list.get(i);
            LinearLayout item = surface();
            item.setPadding(dp(12), dp(10), dp(12), dp(10));
            TextView title = text(s.name, 11, DesignTokens.TEXT_PRIMARY, true);
            item.addView(title, match());
            int pct = s.total <= 0 ? 0 : (int)Math.min(100, s.done * 100L / s.total);
            item.addView(text(s.status + "  •  " + pct + "%", 9, DesignTokens.TEXT_SECONDARY, false), match());
            recentTransferContainer.addView(item, matchBottom(8));
        }
    }

    private void loadReceivedFiles() {
        if (receivedContainer == null) return;
        receivedContainer.removeAllViews();
        receivedContainer.addView(text("در حال خواندن فایل‌ها...", 10, DesignTokens.TEXT_SECONDARY, false));
        new Thread(() -> {
            List<String> names = StorageHelper.listReceivedNames(this);
            runOnUiThread(() -> {
                if (receivedContainer == null || currentPage != PAGE_RECEIVED) return;
                receivedContainer.removeAllViews();
                if (names.isEmpty()) {
                    receivedContainer.addView(emptyState(R.drawable.ic_folder, "پوشه دریافتی خالی است",
                            "فایل‌هایی که از کامپیوتر دریافت می‌کنید اینجا ظاهر می‌شوند."));
                } else {
                    for (String name : names) {
                        LinearLayout item = surface();
                        item.setPadding(dp(12), dp(12), dp(12), dp(12));
                        LinearLayout row = row();
                        ImageView icon = iconView(R.drawable.ic_file, DesignTokens.PRIMARY, "فایل");
                        row.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));
                        TextView fileName = text(name, 11, DesignTokens.TEXT_PRIMARY, true);
                        fileName.setPadding(dp(10), 0, 0, 0);
                        row.addView(fileName, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                        item.addView(row, match());
                        receivedContainer.addView(item, matchBottom(8));
                    }
                }
            });
        }, "received-list").start();
    }

    private void refreshConnectionViews() {
        if (stateText != null) {
            stateText.setText(connectionState);
            stateText.setTextColor(connectionStateColor);
        }
        if (pcText != null) pcText.setText(pcDisplay);
        if (topStatusText != null) {
            boolean connected = connectionState.contains("متصل");
            topStatusText.setText(connected ? "متصل" : (Prefs.hasPair(this) ? "در حال اتصال" : "آماده"));
        }
    }

    private void setConnectionState(String text, int color) {
        connectionState = text;
        connectionStateColor = color;
        refreshConnectionViews();
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

    @Override
    public void onBackPressed() {
        if (currentPage != PAGE_HOME) showPage(PAGE_HOME);
        else super.onBackPressed();
    }

    private ScrollView pageScroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(DesignTokens.BACKGROUND);
        return scroll;
    }

    private LinearLayout pageContent(ScrollView scroll) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.setPadding(dp(16), dp(18), dp(16), dp(24));
        scroll.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return content;
    }

    private LinearLayout surface() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = roundRect(DesignTokens.SURFACE, dp(DesignTokens.RADIUS_MEDIUM));
        bg.setStroke(dp(1), DesignTokens.BORDER);
        v.setBackground(bg);
        v.setElevation(dp(1));
        return v;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return row;
    }

    private TextView emptyState(int iconRes, String title, String subtitle) {
        TextView v = text(title + "\n" + subtitle, 11, DesignTokens.TEXT_SECONDARY, false);
        v.setGravity(Gravity.CENTER);
        v.setCompoundDrawablesWithIntrinsicBounds(0, iconRes, 0, 0);
        v.setCompoundDrawableTintList(ColorStateList.valueOf(DesignTokens.TEXT_DISABLED));
        v.setCompoundDrawablePadding(dp(10));
        v.setPadding(dp(16), dp(26), dp(16), dp(26));
        return v;
    }

    private TextView badge(String label, int bgColor, int textColor) {
        TextView badge = text(label, 9, textColor, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setBackground(roundRect(bgColor, dp(DesignTokens.RADIUS_FULL)));
        return badge;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.START);
        if (bold) v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        else v.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return v;
    }

    private ImageView iconView(int res, int color, String description) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(res);
        icon.setColorFilter(color);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setContentDescription(description);
        return icon;
    }

    private EditText input(String hint, boolean numeric) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(DesignTokens.TEXT_DISABLED);
        e.setTextColor(DesignTokens.TEXT_PRIMARY);
        e.setTextSize(12);
        e.setSingleLine(true);
        e.setPadding(dp(13), 0, dp(13), 0);
        GradientDrawable bg = roundRect(DesignTokens.SURFACE_VARIANT, dp(DesignTokens.RADIUS_SMALL));
        bg.setStroke(dp(1), DesignTokens.BORDER);
        e.setBackground(bg);
        e.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        if (numeric) e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    private Button primaryButton(String label) {
        return styledButton(label, DesignTokens.PRIMARY, Color.WHITE, DesignTokens.PRIMARY, 0);
    }

    private Button secondaryButton(String label) {
        return styledButton(label, DesignTokens.PRIMARY_CONTAINER, DesignTokens.PRIMARY,
                DesignTokens.PRIMARY_CONTAINER, DesignTokens.BORDER);
    }

    private Button ghostButton(String label) {
        return styledButton(label, Color.WHITE, DesignTokens.TEXT_SECONDARY, Color.WHITE, DesignTokens.BORDER);
    }

    private Button dangerGhostButton(String label) {
        return styledButton(label, Color.WHITE, DesignTokens.ERROR, Color.WHITE, DesignTokens.ERROR);
    }

    private Button styledButton(String label, int bgColor, int textColor, int pressedColor, int borderColor) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setTextColor(textColor);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(48));
        b.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable bg = roundRect(bgColor, dp(12));
        if (borderColor != 0) bg.setStroke(dp(1), borderColor);
        b.setBackground(bg);
        b.setElevation(0);
        return b;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchBottom(int bottomDp) {
        LinearLayout.LayoutParams lp = match();
        lp.setMargins(0, 0, 0, dp(bottomDp));
        return lp;
    }

    private LinearLayout.LayoutParams fixedBottom(int heightDp, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp));
        lp.setMargins(0, 0, 0, dp(bottomDp));
        return lp;
    }

    private LinearLayout.LayoutParams weightedTile(int trailingDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(122), 1);
        lp.setMargins(0, 0, dp(trailingDp), 0);
        return lp;
    }

    private LinearLayout.LayoutParams weightedButton(int trailingDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
        lp.setMargins(0, 0, dp(trailingDp), 0);
        return lp;
    }

    private String displayServer(String raw) {
        if (raw == null || raw.isEmpty()) return "—";
        return raw.replace("http://", "").replace("https://", "");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private static String humanBytes(double v) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int i = 0;
        while (Math.abs(v) >= 1024 && i < units.length - 1) { v /= 1024; i++; }
        return String.format(java.util.Locale.US, "%.1f %s", v, units[i]);
    }

    private static String eta(long done, long total, double speed) {
        if (speed <= 1 || total <= done) return "—";
        long sec = (long) ((total - done) / speed);
        if (sec < 60) return sec + " ثانیه";
        return (sec / 60) + " دقیقه " + (sec % 60) + " ثانیه";
    }

    private static final class NavRef {
        final ImageView icon;
        final TextView label;
        NavRef(ImageView icon, TextView label) { this.icon = icon; this.label = label; }
    }

    private static final class TransferSnapshot {
        final String id;
        final String name;
        long done;
        long total;
        double speed;
        String status = "";
        TransferSnapshot(String id, String name) { this.id = id; this.name = name; }
    }

    private final class TransferRow {
        final LinearLayout root;
        final TextView title;
        final ProgressBar progress;
        final TextView meta;

        TransferRow(Context c, String name) {
            root = surface();
            root.setPadding(dp(12), dp(12), dp(12), dp(12));
            title = text(name, 11, DesignTokens.TEXT_PRIMARY, true);
            root.addView(title, matchBottom(8));
            progress = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(1000);
            progress.setProgressTintList(ColorStateList.valueOf(DesignTokens.PRIMARY));
            progress.setProgressBackgroundTintList(ColorStateList.valueOf(DesignTokens.SURFACE_VARIANT));
            root.addView(progress, fixedBottom(8, 8));
            meta = text("", 9, DesignTokens.TEXT_SECONDARY, false);
            root.addView(meta, match());
        }

        void update(TransferSnapshot s) {
            int pct = s.total <= 0 ? 0 : (int)Math.min(1000, s.done * 1000L / s.total);
            progress.setProgress(pct);
            String speedText = s.speed > 0 ? humanBytes(s.speed) + "/s" : "—";
            meta.setText(String.format(java.util.Locale.US, "%s  •  %.1f%%  •  %s  •  %s",
                    s.status, pct / 10.0, speedText, eta(s.done, s.total, s.speed)));
        }
    }
}
