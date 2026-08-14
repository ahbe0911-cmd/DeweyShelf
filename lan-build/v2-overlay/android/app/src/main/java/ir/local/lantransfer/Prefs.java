package ir.local.lantransfer;

import android.content.Context;
import android.content.SharedPreferences;

/** App-private pairing state. V2 also remembers the PC instance id so IP changes are safe. */
public final class Prefs {
    private static final String NAME = "lan_transfer";
    private static final String SERVER = "server";
    private static final String DEVICE_ID = "device_id";
    private static final String TOKEN = "token";
    private static final String INSTANCE_ID = "instance_id";
    private static final String PC_NAME = "pc_name";

    private Prefs() {}

    public static void savePair(Context c, String server, String deviceId, String token,
                                String instanceId, String pcName) {
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
                .putString(SERVER, server)
                .putString(DEVICE_ID, deviceId)
                .putString(TOKEN, token)
                .putString(INSTANCE_ID, instanceId == null ? "" : instanceId)
                .putString(PC_NAME, pcName == null ? "Windows PC" : pcName)
                .apply();
    }

    public static void updateDiscovery(Context c, String server, String instanceId, String pcName) {
        SharedPreferences.Editor e = c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
                .putString(SERVER, server);
        if (instanceId != null && !instanceId.isEmpty()) e.putString(INSTANCE_ID, instanceId);
        if (pcName != null && !pcName.isEmpty()) e.putString(PC_NAME, pcName);
        e.apply();
    }

    public static String server(Context c) { return prefs(c).getString(SERVER, ""); }
    public static String deviceId(Context c) { return prefs(c).getString(DEVICE_ID, ""); }
    public static String token(Context c) { return prefs(c).getString(TOKEN, ""); }
    public static String instanceId(Context c) { return prefs(c).getString(INSTANCE_ID, ""); }
    public static String pcName(Context c) { return prefs(c).getString(PC_NAME, "Windows PC"); }

    public static boolean hasPair(Context c) {
        return !server(c).isEmpty() && !deviceId(c).isEmpty() && !token(c).isEmpty();
    }

    public static void clearPair(Context c) {
        prefs(c).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }
}
