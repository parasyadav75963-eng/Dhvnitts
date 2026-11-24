package com.itfb.dhwanitts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

public class AnnouncerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            AnnouncerService.sync(ctx);
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

    public static void rescheduleAll(Context ctx) { AnnouncerService.sync(ctx); }
    public static void scheduleTimeAnnouncer(Context ctx) { AnnouncerService.sync(ctx); }
    public static void scheduleBatteryAnnouncer(Context ctx) { AnnouncerService.sync(ctx); }
}
