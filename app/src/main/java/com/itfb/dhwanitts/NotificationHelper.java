package com.itfb.dhwanitts;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class NotificationHelper {
    private static final String CHANNEL_ID = "dhwani_live_alerts_v2";
    private static final String ANNOUNCEMENT_URL = "https://raw.githubusercontent.com/parasyadav75963-eng/Dhvnitts/main/announcement.json";
    private static long lastBgCheckTime = 0;

    public static void checkInBackground(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - lastBgCheckTime < 5 * 60 * 1000L) return; // Check every 5 minutes in background
        lastBgCheckTime = now;
        fetchAndProcess(ctx.getApplicationContext(), null);
    }

    public static void checkForRemoteNotifications(Activity activity, View rootView) {
        attachHomeBanner(activity, rootView);
        fetchAndProcess(activity, rootView);
    }

    private static void fetchAndProcess(Context ctx, View rootView) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                URL url = new URL(ANNOUNCEMENT_URL + "?t=" + System.currentTimeMillis());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Cache-Control", "no-cache");
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject json = new JSONObject(sb.toString());
                    String id = json.optString("id", "");
                    String title = json.optString("title", "Dhwani TTS Update");
                    String shortMsg = json.optString("short_msg", "");
                    String fullMsg = json.optString("full_msg", shortMsg);
                    String link = json.optString("link", "");

                    SharedPreferences sp = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
                    String lastId = sp.getString("last_notif_id", "");

                    boolean isNew = !id.isEmpty() && !id.equals(lastId);
                    SharedPreferences.Editor ed = sp.edit()
                            .putString("cur_notif_id", id)
                            .putString("cur_notif_title", title)
                            .putString("cur_notif_short", shortMsg)
                            .putString("cur_notif_full", fullMsg)
                            .putString("cur_notif_link", link);

                    if (isNew) {
                        ed.putString("last_notif_id", id);
                        ed.putBoolean("notif_unread_" + id, true);
                    }
                    ed.apply();

                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (isNew) {
                            showSystemPopupNotification(ctx, title, shortMsg);
                        }
                        if (ctx instanceof Activity && rootView != null) {
                            refreshHomeBanner((Activity) ctx, rootView);
                        }
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    public static void showSystemPopupNotification(Context ctx, String title, String message) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "Dhwani TTS Live Alerts", NotificationManager.IMPORTANCE_HIGH);
                ch.enableVibration(true);
                nm.createNotificationChannel(ch);
            }
            Intent intent = new Intent(ctx, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(
                    ctx, 101, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("[UNREAD] " + title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setAutoCancel(true)
                    .setContentIntent(pi);

            nm.notify(2026, b.build());
        } catch (Exception ignored) {}
    }

    public static void refreshHomeBanner(Context ctx, View rootView) {
        if (rootView == null) return;
        View old = rootView.findViewWithTag("dhwani_notif_banner");
        if (old != null && old.getParent() instanceof ViewGroup) {
            ((ViewGroup) old.getParent()).removeView(old);
        }
        attachHomeBanner(ctx, rootView);
    }

    public static void attachHomeBanner(Context ctx, View rootView) {
        if (rootView == null) return;
        SharedPreferences sp = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
        String id = sp.getString("cur_notif_id", "default_welcome");
        String title = sp.getString("cur_notif_title", "Welcome to Dhwani TTS");
        String shortMsg = sp.getString("cur_notif_short", "Tap to view latest announcements and voice updates from ITFB.");
        String fullMsg = sp.getString("cur_notif_full", "Welcome to Dhwani TTS by Interested Technology for Blind. Here you will receive live updates, new voice alerts, and event notifications.");
        String link = sp.getString("cur_notif_link", "");
        boolean isUnread = sp.getBoolean("notif_unread_" + id, true);

        LinearLayout container = null;
        if (rootView instanceof LinearLayout) {
            container = (LinearLayout) rootView;
        } else if (rootView instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) rootView;
            if (vg.getChildCount() > 0 && vg.getChildAt(0) instanceof LinearLayout) {
                container = (LinearLayout) vg.getChildAt(0);
            }
        }
        if (container == null || container.findViewWithTag("dhwani_notif_banner") != null) return;

        TextView banner = new TextView(ctx);
        banner.setTag("dhwani_notif_banner");
        String unreadPrefix = isUnread ? "[UNREAD] " : "";
        banner.setText(unreadPrefix + "🔔 " + title + ": " + shortMsg);
        banner.setTextSize(15f);
        banner.setPadding(32, 28, 32, 28);
        banner.setBackgroundColor(isUnread ? 0xFF1E3A8A : 0xFF1E293B);
        banner.setTextColor(0xFFFFFFFF);
        banner.setFocusable(true);
        banner.setClickable(true);
        banner.setContentDescription((isUnread ? "Unread Notification: " : "Notification: ") + title + ". " + shortMsg + ". Double tap to open full details.");

        banner.setOnClickListener(v -> {
            sp.edit().putBoolean("notif_unread_" + id, false).apply();
            banner.setText("🔔 " + title + ": " + shortMsg);
            banner.setBackgroundColor(0xFF1E293B);
            banner.setContentDescription("Notification: " + title + ". " + shortMsg + ". Double tap to open full details.");
            showFullDetailDialog(ctx, title, fullMsg, link);
        });
        container.addView(banner, 0);
    }

    public static void showFullDetailDialog(Context ctx, String title, String fullMsg, String link) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setMessage(fullMsg)
                .setPositiveButton("Close", null);
        if (link != null && !link.trim().isEmpty()) {
            builder.setNeutralButton("Open Link / Download", (d, w) -> {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(link.trim()));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                } catch (Exception ignored) {}
            });
        }
        builder.show();
    }
}
