package ir.local.lantransfer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class NotificationHelper {
    public static final String SERVICE_CHANNEL = "transfer_service";
    public static final String RECEIVED_CHANNEL = "received_files";

    private NotificationHelper() {}

    public static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel service = new NotificationChannel(
                SERVICE_CHANNEL, "انتقال فایل", NotificationManager.IMPORTANCE_LOW);
        service.setDescription("وضعیت سرویس انتقال فایل در شبکه محلی");
        NotificationChannel received = new NotificationChannel(
                RECEIVED_CHANNEL, "فایل‌های دریافتی", NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(service);
        nm.createNotificationChannel(received);
    }

    public static Notification serviceNotification(Context c, String text) {
        Intent open = new Intent(c, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(c, SERVICE_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("LAN File Transfer")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    public static void notifyReceived(Context c, String fileName) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification n = new Notification.Builder(c, RECEIVED_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("فایل دریافت شد")
                .setContentText(fileName)
                .setAutoCancel(true)
                .build();
        nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), n);
    }
}
