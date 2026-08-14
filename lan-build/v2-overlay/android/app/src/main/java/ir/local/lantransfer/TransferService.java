package ir.local.lantransfer;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class TransferService extends Service {
    public static final String ACTION_CONNECT = "ir.local.lantransfer.CONNECT";
    public static final String ACTION_UPLOAD = "ir.local.lantransfer.UPLOAD";
    public static final String EVENT_ACTION = "ir.local.lantransfer.EVENT";
    public static final String EXTRA_URIS = "uris";

    private final ExecutorService transfers = Executors.newFixedThreadPool(3);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final AtomicBoolean rediscoveryRunning = new AtomicBoolean(false);
    private OkHttpClient wsClient;
    private volatile WebSocket socket;
    private volatile boolean destroyed = false;

    @Override
    public void onCreate() {
        super.onCreate();
        LanNetwork.bindProcessToWifi(this);
        NotificationHelper.ensureChannels(this);
        startForeground(1001, NotificationHelper.serviceNotification(this, "آماده اتصال به PC"));
        wsClient = new OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Prefs.hasPair(this)) rediscoverAndConnect();
        if (intent != null && ACTION_UPLOAD.equals(intent.getAction())) {
            ArrayList<Uri> uris;
            if (Build.VERSION.SDK_INT >= 33) {
                uris = intent.getParcelableArrayListExtra(EXTRA_URIS, Uri.class);
            } else {
                //noinspection deprecation
                uris = intent.getParcelableArrayListExtra(EXTRA_URIS);
            }
            if (uris != null) {
                for (Uri uri : uris) transfers.execute(() -> uploadOne(uri));
            }
        }
        return START_STICKY;
    }

    private synchronized void connectWebSocket() {
        if (destroyed || !Prefs.hasPair(this)) return;
        if (socket != null) return;
        String base = Prefs.server(this);
        String wsUrl = base.startsWith("https://")
                ? "wss://" + base.substring("https://".length()) + "/ws"
                : "ws://" + base.substring("http://".length()) + "/ws";
        Request request = new Request.Builder().url(wsUrl).build();
        socket = wsClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                reconnectScheduled.set(false);
                try {
                    JSONObject auth = new JSONObject();
                    auth.put("type", "auth");
                    auth.put("device_id", Prefs.deviceId(TransferService.this));
                    auth.put("token", Prefs.token(TransferService.this));
                    webSocket.send(auth.toString());
                    emitState("در حال احراز هویت...");
                } catch (Exception e) {
                    emitState("خطای احراز هویت: " + e.getMessage());
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleSocketMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                clearAndReconnect(webSocket, "اتصال بسته شد");
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                clearAndReconnect(webSocket, "اتصال قطع شد؛ تلاش مجدد...");
            }
        });
    }

    private synchronized void clearAndReconnect(WebSocket webSocket, String message) {
        if (socket == webSocket) socket = null;
        emitState(message);
        if (!destroyed && reconnectScheduled.compareAndSet(false, true)) {
            scheduler.schedule(() -> {
                reconnectScheduled.set(false);
                rediscoverAndConnect();
            }, 2, TimeUnit.SECONDS);
        }
    }

    /** Re-find the same paired PC by instance id before reconnecting.
     * This survives DHCP/IP changes and behaves like a nearby-device radar. */
    private void rediscoverAndConnect() {
        if (destroyed || !Prefs.hasPair(this)) return;
        if (!rediscoveryRunning.compareAndSet(false, true)) return;
        emitState("در حال پیدا کردن دوباره PC...");
        DiscoveryScanner.discover(this, 8765, 8766, Prefs.instanceId(this), new DiscoveryScanner.Callback() {
            @Override
            public void onFound(DiscoveryScanner.Result result) {
                Prefs.updateServer(TransferService.this, result.baseUrl);
                rediscoveryRunning.set(false);
                emitState("PC پیدا شد؛ اتصال...");
                connectWebSocket();
            }

            @Override
            public void onNotFound(String message) {
                rediscoveryRunning.set(false);
                emitState("PC پیدا نشد؛ تلاش مجدد...");
                if (!destroyed && reconnectScheduled.compareAndSet(false, true)) {
                    scheduler.schedule(() -> {
                        reconnectScheduled.set(false);
                        rediscoverAndConnect();
                    }, 4, TimeUnit.SECONDS);
                }
            }
        });
    }

    private void handleSocketMessage(String text) {
        try {
            JSONObject obj = new JSONObject(text);
            String type = obj.optString("type");
            if ("auth_ok".equals(type)) {
                emitState("متصل به PC");
            } else if ("download_offer".equals(type)) {
                String id = obj.getString("transfer_id");
                String name = StorageHelper.sanitizeFileName(obj.getString("name"));
                long size = obj.getLong("size");
                String path = obj.getString("url");
                transfers.execute(() -> downloadWithResume(id, name, size, path));
            }
        } catch (Exception e) {
            emitState("پیام نامعتبر از PC: " + e.getMessage());
        }
    }

    private void downloadWithResume(String id, String name, long total, String relativeUrl) {
        File dir = new File(getFilesDir(), "incoming");
        if (!dir.exists()) dir.mkdirs();
        File part = new File(dir, id + ".part");
        int attempts = 0;
        while (!destroyed && attempts < 20) {
            long offset = part.exists() ? part.length() : 0;
            if (offset > total) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                offset = 0;
            }
            if (offset == total) {
                try {
                    if (!part.exists() && total == 0) {
                        //noinspection ResultOfMethodCallIgnored
                        part.createNewFile();
                    }
                    StorageHelper.publishReceived(this, part, name);
                    //noinspection ResultOfMethodCallIgnored
                    part.delete();
                    sendProgress(id, total, total, "done", null);
                    NotificationHelper.notifyReceived(this, name);
                    emitTransfer(id, name, total, total, 0, "دریافت شد");
                } catch (Exception e) {
                    sendProgress(id, offset, total, "error", e.getMessage());
                    emitTransfer(id, name, offset, total, 0, "خطا: " + e.getMessage());
                }
                return;
            }

            HttpURLConnection c = null;
            try {
                String url = Prefs.server(this) + relativeUrl;
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(15000);
                c.setRequestProperty("Authorization", "Bearer " + Prefs.token(this));
                c.setRequestProperty("X-Device-Id", Prefs.deviceId(this));
                if (offset > 0) c.setRequestProperty("Range", "bytes=" + offset + "-");
                int code = c.getResponseCode();
                if (offset > 0 && code == HttpURLConnection.HTTP_OK) {
                    try (FileOutputStream ignored = new FileOutputStream(part, false)) {}
                    attempts++;
                    continue;
                }
                if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                    throw new IOException("HTTP " + code);
                }
                long done = offset;
                long sampleBytes = done;
                long sampleTime = System.nanoTime();
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part, true)) {
                    byte[] buf = new byte[256 * 1024];
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        out.write(buf, 0, n);
                        done += n;
                        long now = System.nanoTime();
                        if (now - sampleTime >= 500_000_000L || done == total) {
                            double speed = (done - sampleBytes) / ((now - sampleTime) / 1_000_000_000.0);
                            sampleBytes = done;
                            sampleTime = now;
                            sendProgress(id, done, total, "transferring", null);
                            emitTransfer(id, name, done, total, speed, "دریافت");
                        }
                    }
                }
                attempts = 0;
            } catch (Exception e) {
                attempts++;
                long done = part.exists() ? part.length() : 0;
                sendProgress(id, done, total, "paused", null);
                emitTransfer(id, name, done, total, 0, "قطع شد؛ Resume...");
                sleepQuiet(2000);
            } finally {
                if (c != null) c.disconnect();
            }
        }
        long done = part.exists() ? part.length() : 0;
        sendProgress(id, done, total, "error", "Retry limit reached");
        emitTransfer(id, name, done, total, 0, "خطا؛ دوباره تلاش کنید");
    }

    private void uploadOne(Uri uri) {
        String transferId = null;
        String name = "file.bin";
        long total = 0;
        try {
            StorageHelper.Meta meta = StorageHelper.queryMeta(this, uri);
            name = meta.name;
            total = meta.size;
            JSONObject init = ApiClient.uploadInit(Prefs.server(this), Prefs.deviceId(this), Prefs.token(this), name, total);
            transferId = init.getString("transfer_id");
            long offset = init.optLong("offset", 0);
            int chunkSize = init.optInt("chunk_size", 1024 * 1024);
            chunkSize = Math.max(64 * 1024, Math.min(chunkSize, 2 * 1024 * 1024));
            byte[] chunk = new byte[chunkSize];

            InputStream input = StorageHelper.openAt(this, uri, offset);
            long sampleBytes = offset;
            long sampleTime = System.nanoTime();
            try {
                while (offset < total && !destroyed) {
                    int wanted = (int) Math.min(chunk.length, total - offset);
                    int n = readChunk(input, chunk, wanted);
                    if (n <= 0) throw new IOException("پایان غیرمنتظره فایل");
                    int attempts = 0;
                    while (true) {
                        try {
                            JSONObject resp = ApiClient.putChunk(
                                    Prefs.server(this), Prefs.deviceId(this), Prefs.token(this), transferId,
                                    offset, total, chunk, n);
                            offset = resp.optLong("offset", offset + n);
                            break;
                        } catch (ApiClient.OffsetMismatchException mismatch) {
                            long expected = mismatch.expectedOffset;
                            if (expected == offset + n) {
                                offset = expected;
                                break;
                            }
                            input.close();
                            offset = expected;
                            input = StorageHelper.openAt(this, uri, offset);
                            break;
                        } catch (Exception e) {
                            attempts++;
                            if (attempts >= 20) throw e;
                            emitTransfer(transferId, name, offset, total, 0, "قطع شد؛ Resume...");
                            sleepQuiet(1500);
                            try {
                                JSONObject status = ApiClient.uploadStatus(
                                        Prefs.server(this), Prefs.deviceId(this), Prefs.token(this), transferId);
                                long expected = status.optLong("offset", offset);
                                if (expected == offset + n) {
                                    offset = expected;
                                    break;
                                }
                                if (expected != offset) {
                                    input.close();
                                    offset = expected;
                                    input = StorageHelper.openAt(this, uri, offset);
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    long now = System.nanoTime();
                    if (now - sampleTime >= 500_000_000L || offset >= total) {
                        double speed = (offset - sampleBytes) / ((now - sampleTime) / 1_000_000_000.0);
                        sampleBytes = offset;
                        sampleTime = now;
                        emitTransfer(transferId, name, offset, total, speed, "ارسال");
                    }
                }
            } finally {
                input.close();
            }
            emitTransfer(transferId, name, total, total, 0, "ارسال شد");
        } catch (Exception e) {
            emitTransfer(transferId == null ? "upload" + System.nanoTime() : transferId,
                    name, 0, total, 0, "خطا: " + e.getMessage());
        }
    }

    private static int readChunk(InputStream in, byte[] buf, int wanted) throws IOException {
        int total = 0;
        while (total < wanted) {
            int n = in.read(buf, total, wanted - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }

    private void sendProgress(String id, long done, long total, String status, String error) {
        WebSocket ws = socket;
        if (ws == null) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "transfer_progress");
            obj.put("transfer_id", id);
            obj.put("bytes_done", done);
            obj.put("total", total);
            obj.put("status", status);
            if (error != null) obj.put("error", error);
            ws.send(obj.toString());
        } catch (Exception ignored) {}
    }

    private void emitState(String state) {
        Intent i = new Intent(EVENT_ACTION).setPackage(getPackageName());
        i.putExtra("kind", "state");
        i.putExtra("state", state);
        sendBroadcast(i);
    }

    private void emitTransfer(String id, String name, long done, long total, double speed, String status) {
        Intent i = new Intent(EVENT_ACTION).setPackage(getPackageName());
        i.putExtra("kind", "transfer");
        i.putExtra("id", id);
        i.putExtra("name", name);
        i.putExtra("done", done);
        i.putExtra("total", total);
        i.putExtra("speed", speed);
        i.putExtra("status", status);
        sendBroadcast(i);
    }

    private static void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        WebSocket ws = socket;
        if (ws != null) ws.close(1000, "service stopped");
        socket = null;
        transfers.shutdownNow();
        scheduler.shutdownNow();
        wsClient.dispatcher().executorService().shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
