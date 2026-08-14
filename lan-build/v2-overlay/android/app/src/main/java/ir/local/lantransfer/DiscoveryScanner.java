package ir.local.lantransfer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Zapya-style "radar" discovery for the local Wi-Fi.
 * V2 first uses a UDP broadcast handshake (fast and subnet independent), then falls back
 * to the old /24 TCP scan for routers that block broadcasts.
 */
public final class DiscoveryScanner {
    private static final byte[] MAGIC = "LANFT_DISCOVER_V2".getBytes(StandardCharsets.UTF_8);

    public interface Callback {
        void onFound(Result result);
        void onNotFound(String message);
    }

    public static final class Result {
        public final String baseUrl;
        public final String instanceId;
        public final String deviceName;

        Result(String baseUrl, String instanceId, String deviceName) {
            this.baseUrl = baseUrl;
            this.instanceId = instanceId == null ? "" : instanceId;
            this.deviceName = deviceName == null || deviceName.isEmpty() ? "Windows PC" : deviceName;
        }
    }

    private DiscoveryScanner() {}

    public static void discover(Context context, int tcpPort, int udpPort,
                                String expectedInstanceId, Callback callback) {
        new Thread(() -> {
            try {
                Result result = discoverUdp(context, udpPort, expectedInstanceId);
                if (result == null) result = scanFallback(context, tcpPort, expectedInstanceId);
                if (result != null) callback.onFound(result);
                else callback.onNotFound("کامپیوتر پیدا نشد؛ Wi‑Fi و Windows Firewall را بررسی کنید");
            } catch (Exception e) {
                callback.onNotFound("خطای Radar: " + e.getMessage());
            }
        }, "lan-radar").start();
    }

    private static Result discoverUdp(Context context, int udpPort, String expectedId) throws Exception {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = LanNetwork.findWifi(context);
        LinkProperties lp = network == null ? null : cm.getLinkProperties(network);
        if (lp == null) return null;

        DatagramSocket socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(0));
        if (network != null) { try { network.bindSocket(socket); } catch (Exception ignored) {} }
        socket.setBroadcast(true);
        socket.setSoTimeout(350);

        ArrayList<InetAddress> targets = new ArrayList<>();
        targets.add(InetAddress.getByName("255.255.255.255"));
        for (LinkAddress la : lp.getLinkAddresses()) {
            if (la.getAddress() instanceof Inet4Address) {
                InetAddress broadcast = broadcastAddress((Inet4Address) la.getAddress(), la.getPrefixLength());
                if (broadcast != null) targets.add(broadcast);
            }
        }
        for (InetAddress target : targets) {
            try { socket.send(new DatagramPacket(MAGIC, MAGIC.length, target, udpPort)); }
            catch (Exception ignored) {}
        }

        long deadline = System.currentTimeMillis() + 1800;
        byte[] buf = new byte[2048];
        while (System.currentTimeMillis() < deadline) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
            } catch (SocketTimeoutException e) {
                for (InetAddress target : targets) {
                    try { socket.send(new DatagramPacket(MAGIC, MAGIC.length, target, udpPort)); }
                    catch (Exception ignored) {}
                }
                continue;
            }
            String text = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
            try {
                JSONObject obj = new JSONObject(text);
                if (!"LAN File Transfer".equals(obj.optString("service"))) continue;
                String instance = obj.optString("instance_id", "");
                if (expectedId != null && !expectedId.isEmpty() && !expectedId.equals(instance)) continue;
                String scheme = obj.optString("scheme", "http");
                int port = obj.optInt("port", 8765);
                String base = scheme + "://" + packet.getAddress().getHostAddress() + ":" + port;
                if (ping(base, expectedId) != null) {
                    socket.close();
                    return new Result(base, instance, obj.optString("device_name", "Windows PC"));
                }
            } catch (Exception ignored) {}
        }
        socket.close();
        return null;
    }

    private static InetAddress broadcastAddress(Inet4Address address, int prefixLength) {
        if (prefixLength < 0 || prefixLength > 32) return null;
        byte[] raw = address.getAddress();
        int ip = ((raw[0] & 255) << 24) | ((raw[1] & 255) << 16) | ((raw[2] & 255) << 8) | (raw[3] & 255);
        int mask = prefixLength == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - prefixLength));
        int broadcast = ip | ~mask;
        byte[] b = new byte[] {
                (byte) (broadcast >>> 24), (byte) (broadcast >>> 16),
                (byte) (broadcast >>> 8), (byte) broadcast
        };
        try { return InetAddress.getByAddress(b); } catch (Exception e) { return null; }
    }

    private static Result scanFallback(Context context, int port, String expectedId) throws Exception {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = LanNetwork.findWifi(context);
        LinkProperties lp = network == null ? null : cm.getLinkProperties(network);
        if (lp == null) return null;
        Inet4Address local = null;
        List<LinkAddress> addresses = lp.getLinkAddresses();
        for (LinkAddress la : addresses) {
            InetAddress a = la.getAddress();
            if (a instanceof Inet4Address && !a.isLoopbackAddress()) { local = (Inet4Address) a; break; }
        }
        if (local == null) return null;
        byte[] raw = local.getAddress();
        String prefix = (raw[0] & 255) + "." + (raw[1] & 255) + "." + (raw[2] & 255) + ".";
        AtomicReference<Result> found = new AtomicReference<>(null);
        ExecutorService pool = Executors.newFixedThreadPool(40);
        CountDownLatch latch = new CountDownLatch(254);
        for (int i = 1; i <= 254; i++) {
            final int host = i;
            pool.execute(() -> {
                try {
                    if (found.get() == null) {
                        String base = "http://" + prefix + host + ":" + port;
                        Result r = ping(base, expectedId);
                        if (r != null) found.compareAndSet(null, r);
                    }
                } finally { latch.countDown(); }
            });
        }
        latch.await(5, TimeUnit.SECONDS);
        pool.shutdownNow();
        return found.get();
    }

    private static Result ping(String base, String expectedId) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(base + "/api/ping").openConnection();
            c.setConnectTimeout(500);
            c.setReadTimeout(700);
            if (c.getResponseCode() != 200) return null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                JSONObject obj = new JSONObject(sb.toString());
                if (!"LAN File Transfer".equals(obj.optString("service"))) return null;
                String instance = obj.optString("instance_id", "");
                if (expectedId != null && !expectedId.isEmpty() && !expectedId.equals(instance)) return null;
                return new Result(base, instance, obj.optString("device_name", "Windows PC"));
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
