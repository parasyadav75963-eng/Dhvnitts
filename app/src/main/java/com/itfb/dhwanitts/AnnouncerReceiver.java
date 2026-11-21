package com.itfb.dhwanitts;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AnnouncerReceiver extends BroadcastReceiver {

    public static final String ACTION_TIME_TICK = "com.itfb.dhwanitts.ACTION_TIME_TICK";
    public static final String ACTION_BATTERY_TICK = "com.itfb.dhwanitts.ACTION_BATTERY_TICK";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        SharedPreferences prefs = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            rescheduleAll(ctx);
            return;
        }

        if (ACTION_TIME_TICK.equals(action)) {
            if (!prefs.getBoolean("time_ann_enabled", false)) return;
            scheduleTimeAnnouncer(ctx);
            if (AnnouncerEngine.isInQuietHours(prefs)) return;
            if (AnnouncerEngine.isSilentBlocked(ctx, prefs.getBoolean("time_ann_silent", true))) return;

            String timeStr = new SimpleDateFormat("h:mm a", Locale.US).format(new Date());
            String voiceKey = prefs.getString("time_ann_voice", "");
            int toneIdx = prefs.getInt("time_ann_tone", 1);
            AnnouncerEngine.announce(ctx, "The time is " + timeStr, voiceKey, toneIdx);
            return;
        }

        if (ACTION_BATTERY_TICK.equals(action)) {
            if (!prefs.getBoolean("bat_ann_enabled", false)) return;
            scheduleBatteryAnnouncer(ctx);
            if (AnnouncerEngine.isInQuietHours(prefs)) return;
            if (AnnouncerEngine.isSilentBlocked(ctx, prefs.getBoolean("bat_ann_silent", true))) return;

            int pct = getBatteryPercent(ctx);
            boolean charging = isCharging(ctx);
            String voiceKey = prefs.getString("bat_ann_voice", "");
            int toneIdx = prefs.getInt("bat_ann_tone", 1);

            if (charging && pct >= 100 && prefs.getBoolean("bat_ann_full", true)) {
                AnnouncerEngine.announce(ctx, "Battery is fully charged, 100 percent.", voiceKey, toneIdx);
            } else if (!charging && pct <= 20 && prefs.getBoolean("bat_ann_low", true)) {
                AnnouncerEngine.announce(ctx, "Battery low, " + pct + " percent remaining.", voiceKey, toneIdx);
            } else {
                String msg = "Battery is at " + pct + " percent" + (charging ? ", charging." : ".");
                AnnouncerEngine.announce(ctx, msg, voiceKey, toneIdx);
            }
            return;
        }

        if (Intent.ACTION_POWER_CONNECTED.equals(action) || Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            if (!prefs.getBoolean("bat_ann_enabled", false)) return;
            if (!prefs.getBoolean("bat_ann_charger", true)) return;
            if (AnnouncerEngine.isInQuietHours(prefs)) return;
            if (AnnouncerEngine.isSilentBlocked(ctx, prefs.getBoolean("bat_ann_silent", true))) return;

            int pct = getBatteryPercent(ctx);
            String voiceKey = prefs.getString("bat_ann_voice", "");
            int toneIdx = prefs.getInt("bat_ann_tone", 1);
            String msg = Intent.ACTION_POWER_CONNECTED.equals(action)
                    ? ("Charger connected. Battery is " + pct + " percent.")
                    : ("Charger disconnected. Battery is " + pct + " percent.");
            AnnouncerEngine.announce(ctx, msg, voiceKey, toneIdx);
        }
    }

    public static int getBatteryPercent(Context ctx) {
        Intent bi = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bi == null) return 0;
        int level = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return (level >= 0 && scale > 0) ? (level * 100) / scale : 0;
    }

    public static boolean isCharging(Context ctx) {
        Intent bi = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bi == null) return false;
        int st = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
    }

    public static void rescheduleAll(Context ctx) {
        scheduleTimeAnnouncer(ctx);
        scheduleBatteryAnnouncer(ctx);
    }

    public static void scheduleTimeAnnouncer(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = getPI(ctx, ACTION_TIME_TICK, 501);
        am.cancel(pi);
        if (!prefs.getBoolean("time_ann_enabled", false)) return;

        int[] mins = {15, 30, 45, 60};
        int idx = prefs.getInt("time_ann_interval", 1);
        int intervalMin = (idx >= 0 && idx < mins.length) ? mins[idx] : 30;
        long triggerAt = System.currentTimeMillis() + (intervalMin * 60L * 1000L);
        setAlarm(am, triggerAt, pi);
    }

    public static void scheduleBatteryAnnouncer(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = getPI(ctx, ACTION_BATTERY_TICK, 502);
        am.cancel(pi);
        if (!prefs.getBoolean("bat_ann_enabled", false)) return;

        int[] mins = {5, 15, 30, 60};
        int idx = prefs.getInt("bat_ann_interval", 1);
        int intervalMin = (idx >= 0 && idx < mins.length) ? mins[idx] : 15;
        long triggerAt = System.currentTimeMillis() + (intervalMin * 60L * 1000L);
        setAlarm(am, triggerAt, pi);
    }

    private static PendingIntent getPI(Context ctx, String action, int reqCode) {
        Intent i = new Intent(ctx, AnnouncerReceiver.class);
        i.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(ctx, reqCode, i, flags);
    }

    private static void setAlarm(AlarmManager am, long triggerAt, PendingIntent pi) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (Exception ignored) {}
    }
}
