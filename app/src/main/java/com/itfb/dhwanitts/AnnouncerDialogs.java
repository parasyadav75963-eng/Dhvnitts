package com.itfb.dhwanitts;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class AnnouncerDialogs {

    private static ArrayAdapter<String> makeAdapter(Context ctx, List<String> items) {
        ArrayAdapter<String> ad = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, items);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return ad;
    }

    private static ArrayAdapter<String> makeAdapter(Context ctx, String[] items) {
        ArrayAdapter<String> ad = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, items);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return ad;
    }

    private static TextView addInfo(Context ctx, LinearLayout box, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(14f);
        tv.setPadding(0, 18, 0, 6);
        box.addView(tv);
        return tv;
    }

    private static Switch addToggle(Context ctx, LinearLayout box, String title, String desc, boolean checked) {
        addInfo(ctx, box, desc);
        Switch sw = new Switch(ctx);
        sw.setText(title);
        sw.setContentDescription(title + ". " + desc);
        sw.setChecked(checked);
        sw.setPadding(0, 8, 0, 12);
        box.addView(sw);
        return sw;
    }

    private static Spinner addDropdown(Context ctx, LinearLayout box, String desc, String[] items, int sel) {
        addInfo(ctx, box, desc);
        Spinner sp = new Spinner(ctx);
        sp.setContentDescription(desc);
        sp.setAdapter(makeAdapter(ctx, items));
        if (sel >= 0 && sel < items.length) sp.setSelection(sel);
        box.addView(sp);
        return sp;
    }

    private static void addBatteryPermBtn(Context ctx, LinearLayout box) {
        addInfo(ctx, box, "Background Permission: Allow DhwaniTTS to ignore battery restrictions so announcements work reliably.");
        Button btn = new Button(ctx);
        btn.setText("Allow Background Running (Battery Permission)");
        btn.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(ctx.getPackageName())) {
                    try { ctx.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.getPackageName()))); }
                    catch (Exception e) { ctx.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
                } else {
                    Toast.makeText(ctx, "Background running permission is already granted!", Toast.LENGTH_SHORT).show();
                }
            }
        });
        box.addView(btn);
    }

    public static void requestCallPermissions(Context ctx) {
        if (!(ctx instanceof Activity)) return;
        String[] perms = {Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS};
        List<String> needed = new ArrayList<>();
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(ctx, p) != PackageManager.PERMISSION_GRANTED) needed.add(p);
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions((Activity) ctx, needed.toArray(new String[0]), 202);
        } else {
            Toast.makeText(ctx, "Phone & Contacts permissions already granted!", Toast.LENGTH_SHORT).show();
        }
    }

    private static void attachLangVoicePickers(Context ctx, LinearLayout box, List<VoiceModel> allVoices, String savedKey, Spinner[] outVoiceSp, List<VoiceModel> outFiltered) {
        LinkedHashSet<String> langSet = new LinkedHashSet<>();
        String savedLang = "";
        for (VoiceModel vm : allVoices) {
            langSet.add(vm.getLanguageName());
            if (vm.getKey().equals(savedKey)) savedLang = vm.getLanguageName();
        }
        List<String> langs = new ArrayList<>(langSet);
        if (langs.isEmpty()) langs.add("No Downloaded Voices");

        addInfo(ctx, box, "Select Announcement Language:");
        Spinner spLang = new Spinner(ctx);
        spLang.setAdapter(makeAdapter(ctx, langs));
        box.addView(spLang);

        addInfo(ctx, box, "Select Announcement Voice:");
        Spinner spVoice = new Spinner(ctx);
        box.addView(spVoice);
        outVoiceSp[0] = spVoice;

        Runnable refreshVoices = () -> {
            outFiltered.clear();
            List<String> vNames = new ArrayList<>();
            int selIdx = 0;
            String selLang = spLang.getSelectedItem() != null ? spLang.getSelectedItem().toString() : "";
            for (VoiceModel vm : allVoices) {
                if (vm.getLanguageName().equals(selLang)) {
                    outFiltered.add(vm);
                    vNames.add(vm.getVoiceName());
                    if (vm.getKey().equals(savedKey)) selIdx = vNames.size() - 1;
                }
            }
            if (vNames.isEmpty()) vNames.add("No Voice Available");
            spVoice.setAdapter(makeAdapter(ctx, vNames));
            if (selIdx < vNames.size()) spVoice.setSelection(selIdx, false);
        };

        int langIdx = !savedLang.isEmpty() ? langs.indexOf(savedLang) : 0;
        if (langIdx >= 0) spLang.setSelection(langIdx, false);
        refreshVoices.run();

        spLang.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { refreshVoices.run(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    public static void showTimeAnnouncerDialog(Context ctx, List<VoiceModel> voices) {
        SharedPreferences prefs = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
        ScrollView sv = new ScrollView(ctx);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(42, 24, 42, 24);

        Switch swEnable = addToggle(ctx, box, "Enable Smart Time Announcer", "Automatically speak the current time at your chosen interval.", prefs.getBoolean("time_ann_enabled", false));
        Switch swSilent = addToggle(ctx, box, "Respect Silent & Vibrate Mode", "Pause time alerts on Silent or Vibrate.", prefs.getBoolean("time_ann_silent", true));
        String[] intervals = {"Every 15 Minutes", "Every 30 Minutes", "Every 45 Minutes", "Every 1 Hour"};
        Spinner spInt = addDropdown(ctx, box, "Announcement Interval:", intervals, prefs.getInt("time_ann_interval", 1));
        Spinner spTone = addDropdown(ctx, box, "Alert Tone:", AnnouncerEngine.TONE_NAMES, prefs.getInt("time_ann_tone", 1));

        Spinner[] spVoiceHolder = new Spinner[1];
        List<VoiceModel> filteredVoices = new ArrayList<>();
        attachLangVoicePickers(ctx, box, voices, prefs.getString("time_ann_voice", ""), spVoiceHolder, filteredVoices);
        addBatteryPermBtn(ctx, box);

        Button btnTest = new Button(ctx);
        btnTest.setText("Test Time Announcement");
        btnTest.setOnClickListener(v -> {
            int pos = spVoiceHolder[0].getSelectedItemPosition();
            String vKey = (pos >= 0 && pos < filteredVoices.size()) ? filteredVoices.get(pos).getKey() : "";
            String effKey = !vKey.isEmpty() ? vKey : prefs.getString("saved_voice_key", "");
            java.util.Calendar cNow = java.util.Calendar.getInstance();
            AnnouncerEngine.announce(ctx, AnnouncerLocale.formatTime(effKey, cNow.get(java.util.Calendar.HOUR_OF_DAY), cNow.get(java.util.Calendar.MINUTE)), vKey, spTone.getSelectedItemPosition());
        });
        box.addView(btnTest);

        sv.addView(box);
        new AlertDialog.Builder(ctx).setTitle("Smart Time Announcer").setView(sv)
            .setPositiveButton("Save Settings", (d, w) -> {
                int pos = spVoiceHolder[0].getSelectedItemPosition();
                String vKey = (pos >= 0 && pos < filteredVoices.size()) ? filteredVoices.get(pos).getKey() : "";
                prefs.edit().putBoolean("time_ann_enabled", swEnable.isChecked()).putBoolean("time_ann_silent", swSilent.isChecked())
                    .putInt("time_ann_interval", spInt.getSelectedItemPosition()).putInt("time_ann_tone", spTone.getSelectedItemPosition())
                    .putString("time_ann_voice", vKey).apply();
                AnnouncerService.sync(ctx);
                Toast.makeText(ctx, "Smart Time Announcer Saved!", Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Cancel", null).show();
    }

    public static void showBatteryAnnouncerDialog(Context ctx, List<VoiceModel> voices) {
        SharedPreferences prefs = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
        ScrollView sv = new ScrollView(ctx);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(42, 24, 42, 24);

        Switch swEnable = addToggle(ctx, box, "Enable Smart Battery & Power Announcer", "Speak battery updates on percentage change and power alerts.", prefs.getBoolean("bat_ann_enabled", false));
        Switch swSilent = addToggle(ctx, box, "Respect Silent & Vibrate Mode", "Pause battery alerts on Silent or Vibrate.", prefs.getBoolean("bat_ann_silent", true));
        Switch swFull = addToggle(ctx, box, "Announce Charging Complete (100%)", "Speaks when battery reaches 100%.", prefs.getBoolean("bat_ann_full", true));
        Switch swLow = addToggle(ctx, box, "Announce Battery Low (20% or below)", "Speaks warning when battery is low.", prefs.getBoolean("bat_ann_low", true));
        Switch swCharger = addToggle(ctx, box, "Announce Charger Connect / Disconnect", "Speaks when charger is plugged/unplugged.", prefs.getBoolean("bat_ann_charger", true));

        String[] pctIntervals = {"Every 5%", "Every 10%", "Every 15%", "Every 20%"};
        Spinner spInt = addDropdown(ctx, box, "Battery Percentage Interval:", pctIntervals, prefs.getInt("bat_ann_interval", 1));
        Spinner spTone = addDropdown(ctx, box, "Alert Tone:", AnnouncerEngine.TONE_NAMES, prefs.getInt("bat_ann_tone", 1));

        Spinner[] spVoiceHolder = new Spinner[1];
        List<VoiceModel> filteredVoices = new ArrayList<>();
        attachLangVoicePickers(ctx, box, voices, prefs.getString("bat_ann_voice", ""), spVoiceHolder, filteredVoices);
        addBatteryPermBtn(ctx, box);

        Button btnTest = new Button(ctx);
        btnTest.setText("Test Battery Announcement");
        btnTest.setOnClickListener(v -> {
            int pos = spVoiceHolder[0].getSelectedItemPosition();
            String vKey = (pos >= 0 && pos < filteredVoices.size()) ? filteredVoices.get(pos).getKey() : "";
            String effKey = !vKey.isEmpty() ? vKey : prefs.getString("saved_voice_key", "");
            AnnouncerEngine.announce(ctx, AnnouncerLocale.formatBattery(effKey, AnnouncerReceiver.getBatteryPercent(ctx), AnnouncerReceiver.isCharging(ctx)), vKey, spTone.getSelectedItemPosition());
        });
        box.addView(btnTest);

        sv.addView(box);
        new AlertDialog.Builder(ctx).setTitle("Smart Battery & Power Announcer").setView(sv)
            .setPositiveButton("Save Settings", (d, w) -> {
                int pos = spVoiceHolder[0].getSelectedItemPosition();
                String vKey = (pos >= 0 && pos < filteredVoices.size()) ? filteredVoices.get(pos).getKey() : "";
                prefs.edit().putBoolean("bat_ann_enabled", swEnable.isChecked()).putBoolean("bat_ann_silent", swSilent.isChecked())
                    .putBoolean("bat_ann_full", swFull.isChecked()).putBoolean("bat_ann_low", swLow.isChecked())
                    .putBoolean("bat_ann_charger", swCharger.isChecked()).putInt("bat_ann_interval", spInt.getSelectedItemPosition())
                    .putInt("bat_ann_tone", spTone.getSelectedItemPosition()).putString("bat_ann_voice", vKey).apply();
                AnnouncerService.sync(ctx);
                Toast.makeText(ctx, "Smart Battery Announcer Saved!", Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Cancel", null).show();
    }

    public static void showCallAnnouncerDialog(Context ctx, List<VoiceModel> voices) {
        SharedPreferences prefs = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
        ScrollView sv = new ScrollView(ctx);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(42, 24, 42, 24);

        Switch swEnable = addToggle(ctx, box, "Enable Smart Call Announcer", "Detect incoming calls and announce caller name/number.", prefs.getBoolean("call_ann_enabled", true));
        Switch swSilent = addToggle(ctx, box, "Respect Silent & Vibrate Mode", "Do not announce calls when phone is Silent or Vibrate.", prefs.getBoolean("call_ann_silent", true));

        String[] formats = {"Name & Number", "Name Only", "Number Only"};
        Spinner spFormat = addDropdown(ctx, box, "Call Announce Style:", formats, prefs.getInt("call_ann_format", 0));

        String[] repeats = {"Once (1 Time)", "Twice (2 Times)", "3 Times", "Continuous (Until Answered)"};
        Spinner spRepeat = addDropdown(ctx, box, "Repeat Count:", repeats, prefs.getInt("call_ann_repeat", 1));

        Spinner[] spVoiceHolder = new Spinner[1];
        List<VoiceModel> filteredVoices = new ArrayList<>();
        attachLangVoicePickers(ctx, box, voices, prefs.getString("call_ann_voice", ""), spVoiceHolder, filteredVoices);

        Button btnPerm = new Button(ctx);
        btnPerm.setText("Grant Phone & Contacts Permissions");
        btnPerm.setOnClickListener(v -> requestCallPermissions(ctx));
        box.addView(btnPerm);

        Button btnTest = new Button(ctx);
        btnTest.setText("Test Call Announcement");
        btnTest.setOnClickListener(v -> {
            int pos = spVoiceHolder[0].getSelectedItemPosition();
            String vKey = (pos >= 0 && pos < filteredVoices.size()) ? filteredVoices.get(pos).getKey() : "";
            String effKey = !vKey.isEmpty() ? vKey : prefs.getString("saved_voice_key", "");
            boolean isHi = effKey.toLowerCase().startsWith("hi");
            String sample = isHi ? "इनकमिंग कॉल, राहुल शर्मा, 9 8 7 6 5" : "Incoming call from Rahul Sharma, 9 8 7 6 5";
            AnnouncerEngine.announce(ctx, sample, vKey, 0);
        });
        box.addView(btnTest);

        sv.addView(box);
        new AlertDialog.Builder(ctx).setTitle("Smart Call Announcer").setView(sv)
            .setPositiveButton("Save Settings", (d, w) -> {
                int pos = spVoiceHolder[0].getSelectedItemPosition();
                String vKey = (pos >= 0 && pos < filteredVoices.size()) ? filteredVoices.get(pos).getKey() : "";
                prefs.edit().putBoolean("call_ann_enabled", swEnable.isChecked()).putBoolean("call_ann_silent", swSilent.isChecked())
                    .putInt("call_ann_format", spFormat.getSelectedItemPosition()).putInt("call_ann_repeat", spRepeat.getSelectedItemPosition())
                    .putString("call_ann_voice", vKey).apply();
                if (swEnable.isChecked()) requestCallPermissions(ctx);
                AnnouncerService.sync(ctx);
                Toast.makeText(ctx, "Smart Call Announcer Saved!", Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Cancel", null).show();
    }

    public static void showQuietHoursDialog(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
        ScrollView sv = new ScrollView(ctx);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(42, 24, 42, 24);

        Switch swQuiet = addToggle(ctx, box, "Enable Quiet Hours (Do Not Disturb)", "Pause Time & Battery alerts during sleep hours.", prefs.getBoolean("quiet_hours_enabled", false));
        String[] hours = {"12:00", "1:00", "2:00", "3:00", "4:00", "5:00", "6:00", "7:00", "8:00", "9:00", "10:00", "11:00"};
        int s24 = prefs.getInt("quiet_start_24h", 23), e24 = prefs.getInt("quiet_end_24h", 6);
        Spinner spStart = addDropdown(ctx, box, "Quiet Hours Start Time:", hours, s24 % 12);
        Switch swStartPm = addToggle(ctx, box, "Start Time PM", "Toggle AM or PM.", s24 >= 12);
        Spinner spEnd = addDropdown(ctx, box, "Quiet Hours End Time:", hours, e24 % 12);
        Switch swEndPm = addToggle(ctx, box, "End Time PM", "Toggle AM or PM.", e24 >= 12);

        sv.addView(box);
        new AlertDialog.Builder(ctx).setTitle("Quiet Hours Settings").setView(sv)
            .setPositiveButton("Save Quiet Hours", (d, w) -> {
                int startH = (spStart.getSelectedItemPosition() % 12) + (swStartPm.isChecked() ? 12 : 0);
                int endH = (spEnd.getSelectedItemPosition() % 12) + (swEndPm.isChecked() ? 12 : 0);
                prefs.edit().putBoolean("quiet_hours_enabled", swQuiet.isChecked()).putInt("quiet_start_24h", startH).putInt("quiet_end_24h", endH).apply();
                Toast.makeText(ctx, "Quiet Hours Saved!", Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Cancel", null).show();
    }
}
