package ir.local.lantransfer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** Selects the physical Wi-Fi network, avoiding accidental routing through a VPN. */
public final class LanNetwork {
    private LanNetwork() {}

    public static Network findWifi(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return n;
        }
        return cm.getActiveNetwork();
    }

    public static boolean bindProcessToWifi(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network wifi = findWifi(context);
            return wifi != null && cm.bindProcessToNetwork(wifi);
        } catch (Exception e) {
            return false;
        }
    }
}
