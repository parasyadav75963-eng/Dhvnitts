package com.itfb.dhwanitts;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.*;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class NotificationHelper {
    private static final String CHANNEL_ID = "dhwani_live_updates";
    private static final String ANNOUNCE_URL = "https://raw.githubusercontent.com/parasyadav75963-eng/Dhvnitts/main/announcement.json";

    public static void requestPermissionIfNeeded(Activity act) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(act, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(act, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }
    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Dhwani TTS Alerts", NotificationManager.IMPORTANCE_HIGH);
                Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build();
                ch.setSound(sound, attrs);
                ch.enableVibration(true);
                nm.createNotificationChannel(ch);
            }
        }
    }

    public static void postSystemNotification(Context ctx, String id, String title, String shortMsg, String fullMsg, String link) {
        ensureChannel(ctx);
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("notif_title", title);
        intent.putExtra("notif_full", fullMsg);
        intent.putExtra("notif_link", link);
        int flags = Build.VERSION.SDK_INT >= 23 ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(ctx, id.hashCode(), intent, flags);

        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(shortMsg)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(shortMsg))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(sound)
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(1001, b.build());
    }

    public static void showFullDetailDialog(Context ctx, String title, String fullMsg, String link) {
        AlertDialog.Builder b = new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setMessage(fullMsg)
                .setPositiveButton("Close", null);
        if (link != null && link.startsWith("http")) {
            b.setNeutralButton("Open Link / Download", (d, w) -> {
                ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
            });
        }
        b.show();
    }
    public static void checkForRemoteUpdatesAndAnnouncements(Context ctx) {
        new Thread(() -> {
            try {
                URL url = new URL(ANNOUNCE_URL + "?t=" + System.currentTimeMillis());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setUseCaches(false);
                if (conn.getResponseCode() == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();

                    JSONObject obj = new JSONObject(sb.toString());
                    String id = obj.optString("id", "");
                    String title = obj.optString("title", "Dhwani TTS Alert");
                    String shortMsg = obj.optString("short_msg", "");
                    String fullMsg = obj.optString("full_msg", shortMsg);
                    String link = obj.optString("link", "");
                    String latestVer = obj.optString("latest_version", "").replace("v", "").trim();
                    String apkUrl = obj.optString("apk_url", "");

                    SharedPreferences sp = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
                    sp.edit()
                        .putString("cur_notif_id", id)
                        .putString("cur_notif_title", title)
                        .putString("cur_notif_short", shortMsg)
                        .putString("cur_notif_full", fullMsg)
                        .putString("cur_notif_link", link)
                        .apply();

                    String lastId = sp.getString("last_notif_id", "");
                    if (!id.isEmpty() && !id.equals(lastId) && !shortMsg.isEmpty()) {
                        sp.edit().putString("last_notif_id", id).apply();
                        new Handler(Looper.getMainLooper()).post(() ->
                            postSystemNotification(ctx, id, title, shortMsg, fullMsg, link)
                        );
                    }
                    checkUpdateDialog(ctx, sp, latestVer, apkUrl, obj.optString("update_notes", ""));
                }
                conn.disconnect();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }
    private static void checkUpdateDialog(Context ctx, SharedPreferences sp, String remoteVer, String apkUrl, String notes) {
        try {
            if (remoteVer.isEmpty()) return;
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            String curVer = pi.versionName != null ? pi.versionName.replace("v", "").trim() : "";
            if (!remoteVer.equals(curVer)) {
                String lastPrompt = sp.getString("last_prompted_version", "");
                if (!remoteVer.equals(lastPrompt)) {
                    sp.edit().putString("last_prompted_version", remoteVer).apply();
                    String msg = notes.isEmpty() ? "A new faster version of Dhwani TTS (" + remoteVer + ") is available. Update now for best performance!" : notes;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (ctx instanceof Activity && !((Activity) ctx).isFinishing()) {
                            new AlertDialog.Builder(ctx)
                                .setTitle("New Update Available: v" + remoteVer)
                                .setMessage(msg)
                                .setPositiveButton("Update Now", (d, w) -> {
                                    String target = apkUrl.isEmpty() ? "https://github.com/parasyadav75963-eng/Dhvnitts/releases" : apkUrl;
                                    ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(target)));
                                })
                                .setNegativeButton("Later", null)
                                .show();
                        }
                    });
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void attachHomeBanner(View rootView) {
        if (rootView == null) return;
        Context ctx = rootView.getContext();
        SharedPreferences sp = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
        String title = sp.getString("cur_notif_title", "Welcome to Dhwani TTS");
        String shortMsg = sp.getString("cur_notif_short", "Tap to view latest announcements and voice updates from ITFB.");
        String fullMsg = sp.getString("cur_notif_full", "Welcome to Dhwani TTS by Interested Technology for Blind. Here you will receive live updates, new voice alerts, and event notifications.");
        String link = sp.getString("cur_notif_link", "");

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
        banner.setText("🔔 " + title + ": " + shortMsg);
        banner.setTextSize(15f);
        banner.setPadding(32, 28, 32, 28);
        banner.setBackgroundColor(0xFF1E293B);
        banner.setTextColor(0xFFFFFFFF);
        banner.setFocusable(true);
        banner.setClickable(true);
        banner.setContentDescription("Notification: " + title + ". " + shortMsg + ". Double tap to open full details.");
        banner.setOnClickListener(v -> showFullDetailDialog(ctx, title, fullMsg, link));
        container.addView(banner, 0);
    }
}
