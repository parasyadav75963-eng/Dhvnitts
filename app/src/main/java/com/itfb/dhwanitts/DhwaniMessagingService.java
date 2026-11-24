package com.itfb.dhwanitts;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public class DhwaniMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "dhwani_fcm_soft_v1";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        String title = "Dhwani TTS Update";
        String body = "";
        String link = "";

        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) title = remoteMessage.getNotification().getTitle();
            if (remoteMessage.getNotification().getBody() != null) body = remoteMessage.getNotification().getBody();
        }
        Map<String, String> data = remoteMessage.getData();
        if (data != null && !data.isEmpty()) {
            if (data.containsKey("title")) title = data.get("title");
            if (data.containsKey("body")) body = data.get("body");
            if (data.containsKey("link")) link = data.get("link");
        }
        if (body == null || body.isEmpty()) return;

        String id = "fcm_" + System.currentTimeMillis();
        NotificationHelper.addOrSaveToInbox(this, id, title, body, body, link);
        getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE).edit()
                .putString("cur_notif_id", id)
                .putString("cur_notif_title", title)
                .putString("cur_notif_short", body)
                .putString("cur_notif_full", body)
                .putString("cur_notif_link", link)
                .putString("last_notif_id", id)
                .putBoolean("notif_unread_" + id, true)
                .apply();
        showSoftNotification(title, body, link);
    }

    private void showSoftNotification(String title, String body, String link) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.dhwani_soft_notif);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "Dhwani TTS Announcements", NotificationManager.IMPORTANCE_HIGH);
                AudioAttributes aa = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                ch.setSound(soundUri, aa);
                ch.enableVibration(true);
                nm.createNotificationChannel(ch);
            }

            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra("notif_title", title);
            intent.putExtra("notif_full", body);
            intent.putExtra("notif_link", link);

            PendingIntent pi = PendingIntent.getActivity(
                    this, (int) (System.currentTimeMillis() % 10000), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

            NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setSound(soundUri)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pi);

            nm.notify((int) (System.currentTimeMillis() % 100000), b.build());
        } catch (Exception ignored) {}
    }
}
