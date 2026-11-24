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
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import androidx.core.app.NotificationCompat;
import java.util.Calendar;

public class AnnouncerService extends Service {
    private static final String CH_ID = "dhwani_announcer_fg";
    private int lastBatteryPct = -1;
    private int lastAnnouncedBatPct = -1;
    private boolean registered = false;
    private final Handler callHandler = new Handler(Looper.getMainLooper());
    private Runnable callRepeatRunnable;
    private volatile boolean isRinging = false;
    private String lastRingingNumber = "";

    public static void sync(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
        boolean need = p.getBoolean("time_ann_enabled", false)
                || p.getBoolean("bat_ann_enabled", false)
                || p.getBoolean("call_ann_enabled", true);
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
            java.io.File[] all = new java.io.File(ctx.getFilesDir(), "voices").listFiles((d, n) -> n.endsWith(".onnx"));
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

    private String lookupContactName(String number) {
        if (number == null || number.trim().isEmpty()) return "";
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            try (Cursor c = getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) return c.getString(0);
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String formatSpacedNumber(String num) {
        if (num == null) return "";
        return num.replaceAll("[^0-9+]", "").replaceAll("(\\d)", "$1 ").trim();
    }

    private String buildCallMessage(String voiceKey, String name, String number, int formatIdx) {
        boolean isHi = voiceKey != null && voiceKey.toLowerCase().startsWith("hi");
        String spacedNum = formatSpacedNumber(number);
        boolean hasName = name != null && !name.trim().isEmpty();
        boolean hasNum = !spacedNum.isEmpty();

        String target;
        if (formatIdx == 1) {
            target = hasName ? name : (hasNum ? spacedNum : (isHi ? "अज्ञात नंबर" : "Unknown number"));
        } else if (formatIdx == 2) {
            target = hasNum ? spacedNum : (isHi ? "अज्ञात नंबर" : "Unknown number");
        } else {
            if (hasName && hasNum) target = name + ", " + spacedNum;
            else if (hasName) target = name;
            else if (hasNum) target = spacedNum;
            else target = isHi ? "अज्ञात नंबर" : "Unknown number";
        }
        return isHi ? ("इनकमिंग कॉल, " + target) : ("Incoming call from " + target);
    }

    private void handleIncomingCall(Context ctx, String stateStr, String incomingNumber) {
        SharedPreferences p = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
        if (TelephonyManager.EXTRA_STATE_RINGING.equals(stateStr)) {
            if (!p.getBoolean("call_ann_enabled", true)) return;
            if (isBlockedBySilent(p, "call_ann_silent")) return;

            // Android 9+ sends 2 broadcasts: first with null number, second with real number
            if (incomingNumber == null) return;
            if (isRinging && incomingNumber.equals(lastRingingNumber)) return;

            isRinging = true;
            lastRingingNumber = incomingNumber;
            String vKey = resolveVoiceKey(ctx, p, "call_ann_voice");
            int formatIdx = p.getInt("call_ann_format", 0);
            int repeatIdx = p.getInt("call_ann_repeat", 1);
            int maxRepeats = (repeatIdx == 0) ? 1 : (repeatIdx == 1) ? 2 : (repeatIdx == 2) ? 3 : 10;
            String name = lookupContactName(incomingNumber);
            String msg = buildCallMessage(vKey, name, incomingNumber, formatIdx);

            if (callRepeatRunnable != null) callHandler.removeCallbacks(callRepeatRunnable);
            final int[] count = {0};
            callRepeatRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!isRinging || count[0] >= maxRepeats) return;
                    count[0]++;
                    AnnouncerEngine.announceCall(ctx, msg, vKey);
                    if (count[0] < maxRepeats) callHandler.postDelayed(this, 4500);
                }
            };
            callHandler.postDelayed(callRepeatRunnable, 600);
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(stateStr) || TelephonyManager.EXTRA_STATE_IDLE.equals(stateStr)) {
            isRinging = false;
            lastRingingNumber = "";
            if (callRepeatRunnable != null) callHandler.removeCallbacks(callRepeatRunnable);
            AnnouncerEngine.stopCurrent();
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            SharedPreferences p = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
            String act = intent.getAction();

            if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(act)) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                String num = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                handleIncomingCall(ctx, state, num);
                return;
            }

            if (Intent.ACTION_TIME_TICK.equals(act)) {
                if (isQuietHours(p)) return;
                if (p.getBoolean("time_ann_enabled", false) && !isBlockedBySilent(p, "time_ann_silent")) {
                    Calendar cal = Calendar.getInstance();
                    int h24 = cal.get(Calendar.HOUR_OF_DAY);
                    int min = cal.get(Calendar.MINUTE);
                    int[] mins = {15, 30, 45, 60};
                    int iv = mins[Math.min(Math.max(p.getInt("time_ann_interval", 1), 0), 3)];
                    boolean match = (iv == 60) ? (min == 0) : (iv == 45 ? ((h24 * 60 + min) % 45 == 0) : (min % iv == 0));
                    if (match) {
                        String vKey = resolveVoiceKey(ctx, p, "time_ann_voice");
                        AnnouncerEngine.announce(ctx, AnnouncerLocale.formatTime(vKey, h24, min), vKey, p.getInt("time_ann_tone", 1));
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
                int[] pctSteps = {5, 10, 15, 20};
                int step = pctSteps[Math.min(Math.max(p.getInt("bat_ann_interval", 1), 0), 3)];

                if (pct == 100 && chg && lastBatteryPct < 100 && p.getBoolean("bat_ann_full", true)) {
                    AnnouncerEngine.announce(ctx, AnnouncerLocale.formatBatteryFull(vKey), vKey, tone);
                    lastAnnouncedBatPct = pct;
                } else if (pct <= 20 && !chg && (lastBatteryPct > 20 || lastBatteryPct == -1) && p.getBoolean("bat_ann_low", true)) {
                    AnnouncerEngine.announce(ctx, AnnouncerLocale.formatBatteryLow(vKey, pct), vKey, tone);
                    lastAnnouncedBatPct = pct;
                } else if (lastBatteryPct != -1 && pct % step == 0 && pct != lastAnnouncedBatPct) {
                    AnnouncerEngine.announce(ctx, AnnouncerLocale.formatBattery(vKey, pct, chg), vKey, tone);
                    lastAnnouncedBatPct = pct;
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
                .setContentText("Monitoring Time, Battery & Calls in background")
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
            f.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(receiver, f);
            }
            registered = true;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRinging = false;
        if (callRepeatRunnable != null) callHandler.removeCallbacks(callRepeatRunnable);
        if (registered) {
            try { unregisterReceiver(receiver); } catch (Exception ignored) {}
            registered = false;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
