package com.itfb.dhwanitts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import java.util.Calendar;

public class AnnouncerService extends Service {
    private static final String CH_ID = "dhwani_announcer_fg";
    private int lastBatteryPct = -1;
    private boolean registered = false;

    public static void sync(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
        boolean need = p.getBoolean("time_ann_enabled", false) || p.getBoolean("bat_ann_enabled", false);
        Intent i = new Intent(ctx, AnnouncerService.class);
        try {
            if (need) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i);
                else ctx.startService(i);
            } else {
                ctx.stopService(i);
            }
        } catch (Exception ignored) {}
    }

    private String resolveVoiceKey(Context ctx, SharedPreferences p, String prefKey) {
        String k = p.getString(prefKey, "");
        if (k == null || k.isEmpty()) k = p.getString("saved_voice_key", "");
        if (k == null || k.isEmpty()) {
            java.io.File dir = new java.io.File(ctx.getFilesDir(), "voices");
            java.io.File[] all = dir.listFiles((d, n) -> n.endsWith(".onnx"));
            if (all != null && all.length > 0) k = all[0].getName().replace(".onnx", "");
        }
        return k == null ? "" : k;
    }

    private boolean isQuietHours(SharedPreferences p) {
        if (!p.getBoolean("quiet_hours_enabled", false)) return false;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int start = p.getInt("quiet_start_24h", 23);
        int end = p.getInt("quiet_end_24h", 6);
        return (start <= end) ? (h >= start && h < end) : (h >= start || h < end);
    }

    private boolean isBlockedBySilent(SharedPreferences p, String key) {
        if (!p.getBoolean(key, true)) return false;
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        return am != null && am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            SharedPreferences p = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
            String act = intent.getAction();

            if (Intent.ACTION_TIME_TICK.equals(act)) {
                if (isQuietHours(p)) return;
                Calendar cal = Calendar.getInstance();
                int h24 = cal.get(Calendar.HOUR_OF_DAY);
                int min = cal.get(Calendar.MINUTE);

                if (p.getBoolean("time_ann_enabled", false) && !isBlockedBySilent(p, "time_ann_silent")) {
                    int[] mins = {15, 30, 45, 60};
                    int iv = mins[Math.min(Math.max(p.getInt("time_ann_interval", 1), 0), 3)];
                    boolean match = (iv == 60) ? (min == 0) : (iv == 45 ? ((h24 * 60 + min) % 45 == 0) : (min % iv == 0));
                    if (match) {
                        String vKey = resolveVoiceKey(ctx, p, "time_ann_voice");
                        AnnouncerEngine.announce(ctx, AnnouncerLocale.formatTime(vKey, h24, min), vKey, p.getInt("time_ann_tone", 1));
                    }
                }

                if (p.getBoolean("bat_ann_enabled", false) && !isBlockedBySilent(p, "bat_ann_silent")) {
                    int[] bMins = {5, 15, 30, 60};
                    int biv = bMins[Math.min(Math.max(p.getInt("bat_ann_interval", 1), 0), 3)];
                    boolean bMatch = (biv == 60) ? (min == 0) : (min % biv == 0);
                    if (bMatch) {
                        String vKey = resolveVoiceKey(ctx, p, "bat_ann_voice");
                        int pct = AnnouncerReceiver.getBatteryPercent(ctx);
                        boolean chg = AnnouncerReceiver.isCharging(ctx);
                        AnnouncerEngine.announce(ctx, AnnouncerLocale.formatBattery(vKey, pct, chg), vKey, p.getInt("bat_ann_tone", 1));
                    }
                }
            } else if (Intent.ACTION_POWER_CONNECTED.equals(act) || Intent.ACTION_POWER_DISCONNECTED.equals(act)) {
                if (!p.getBoolean("bat_ann_enabled", false) || !p.getBoolean("bat_ann_charger", true)) return;
                if (isQuietHours(p) || isBlockedBySilent(p, "bat_ann_silent")) return;
                boolean connected = Intent.ACTION_POWER_CONNECTED.equals(act);
                String vKey = resolveVoiceKey(ctx, p, "bat_ann_voice");
                int pct = AnnouncerReceiver.getBatteryPercent(ctx);
                AnnouncerEngine.announce(ctx, AnnouncerLocale.formatCharger(vKey, connected, pct), vKey, p.getInt("bat_ann_tone", 1));
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(act)) {
                if (!p.getBoolean("bat_ann_enabled", false) || isQuietHours(p) || isBlockedBySilent(p, "bat_ann_silent")) return;
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int pct = (level >= 0 && scale > 0) ? (level * 100 / scale) : -1;
                boolean chg = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
                if (pct < 0 || pct == lastBatteryPct) return;
                String vKey = resolveVoiceKey(ctx, p, "bat_ann_voice");
                int tone = p.getInt("bat_ann_tone", 1);
                if (pct == 100 && chg && lastBatteryPct < 100 && p.getBoolean("bat_ann_full", true)) {
                    AnnouncerEngine.announce(ctx, AnnouncerLocale.formatBatteryFull(vKey), vKey, tone);
                } else if (pct <= 20 && !chg && (lastBatteryPct > 20 || lastBatteryPct == -1) && p.getBoolean("bat_ann_low", true)) {
                    AnnouncerEngine.announce(ctx, AnnouncerLocale.formatBatteryLow(vKey, pct), vKey, tone);
                }
                lastBatteryPct = pct;
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CH_ID, "Dhwani Smart Announcer", NotificationManager.IMPORTANCE_LOW);
            ch.setSound(null, null);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Notification n = new NotificationCompat.Builder(this, CH_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("DhwaniTTS Smart Announcer Active")
                .setContentText("Monitoring Time & Battery alerts in background")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(9001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(9001, n);
        } catch (Exception e) {
            startForeground(9001, n);
        }
        if (!registered) {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_TIME_TICK);
            f.addAction(Intent.ACTION_POWER_CONNECTED);
            f.addAction(Intent.ACTION_POWER_DISCONNECTED);
            f.addAction(Intent.ACTION_BATTERY_CHANGED);
            registerReceiver(receiver, f);
            registered = true;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (registered) {
            try { unregisterReceiver(receiver); } catch (Exception ignored) {}
            registered = false;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
