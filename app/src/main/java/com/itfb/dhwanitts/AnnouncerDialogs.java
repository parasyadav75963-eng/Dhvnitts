package com.itfb.dhwanitts;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class AnnouncerDialogs {

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
        sp.setAdapter(new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_dropdown_item, items));
        if (sel >= 0 && sel < items.length) sp.setSelection(sel);
        box.addView(sp);
        return sp;
    }

    private static void addBatteryPermBtn(Context ctx, LinearLayout box) {
        addInfo(ctx, box, "Background Permission: Allow DhwaniTTS to ignore battery restrictions so announcements work reliably even when the screen is off.");
        Button btn = new Button(ctx);
        btn.setText("Allow Background Running (Battery Permission)");
        btn.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(ctx.getPackageName())) {
                    try {
                        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.getPackageName()));
                        ctx.startActivity(i);
                    } catch (Exception e) {
                        ctx.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    }
                } else {
                    Toast.makeText(ctx, "Background running permission is already granted!", Toast.LENGTH_SHORT).show();
                }
            }
        });
        box.addView(btn);
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

        addInfo(ctx, box, "Select Announcement Language: Choose the language for this announcer.");
        Spinner spLang = new Spinner(ctx);
        spLang.setContentDescription("Select Announcement Language");
        spLang.setAdapter(new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_dropdown_item, langs));
        box.addView(spLang);

        addInfo(ctx, box, "Select Announcement Voice: Choose a downloaded voice for the selected language.");
        Spinner spVoice = new Spinner(ctx);
        spVoice.setContentDescription("Select Announcement Voice");
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
            spVoice.setAdapter(new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_dropdown_item, vNames));
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

        Switch swEnable = addToggle(ctx, box, "Enable Smart Time Announcer",
                "Turn on to automatically speak the current time at your chosen interval.",
                prefs.getBoolean("time_ann_enabled", false));
        Switch swSilent = addToggle(ctx, box, "Respect Silent & Vibrate Mode",
                "When enabled, time will not be announced while your phone is on Silent or Vibrate.",
                prefs.getBoolean("time_ann_silent", true));

        String[] intervals = {"Every 15 Minutes", "Every 30 Minutes", "Every 45 Minutes", "Every 1 Hour"};
        Spinner spInt = addDropdown(ctx, box, "Announcement Interval: Choose how often to announce the time.",
                intervals, prefs.getInt("time_ann_interval", 1));
        Spinner spTone = addDropdown(ctx, box, "Alert Tone: Select a chime tone to play before speaking the time.",
                AnnouncerEngine.TONE_NAMES, prefs.getInt("time_ann_tone", 1));

        Spinner[] spVoiceHolder = new Spinner[1];
        List<VoiceModel> filteredVoices = new ArrayList<>();
        attachLangVoicePickers(ctx, box, voices, prefs.getString("time_ann_voice", ""), spVoiceHolder, filteredVoices);
        addBatteryPermBtn(ctx, box);

        Button btnTest = new Button(ctx);
        btnTest.setText("Test Time Announcement");
        btnTest.setOnClickListener(v -> {
            int pos = spVoiceHolder[0].getSelectedItemPosition();
            String vKey = (pos >= 0 && pos < filteredVoices.size()) ? filteredVoices.get(pos).getKey() : "";
            String now = new SimpleDateFormat("h:mm a", Locale.US).format(new Date());
            Toast.makeText(ctx, "Testing Time Announcement...", Toast.LENGTH_SHORT).show();
            String effKey = (vKey != null && !vKey.isEmpty()) ? vKey : ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE).getString("saved_voice_key", "");
            java.util.Calendar cNow = java.util.Calendar.getInstance();
            AnnouncerEngine.announce(ctx, AnnouncerLocale.formatTime(effKey, cNow.get(java.util.Calendar.HOUR_OF_DAY), cNow.get(java.util.Calendar.MINUTE)), vKey, spTone.getSelectedItemPosition());
        });
        box.addView(btnTest);

        sv.addView(box);
        new AlertDialog.Builder(ctx)
                .setTitle("Smart Time Announcer")
                .setView(sv)
                .setPositiveButton("Save Settings", (d, w) -> {
                    int pos = spVoiceHolder[0].getSelectedItemPosition();
                    String vKey = (pos >= 0 && pos < filteredVoices.size()) ? filteredVoices.get(pos).getKey() : "";
                    prefs.edit()
                            .putBoolean("time_ann_enabled", swEnable.isChecked())
                            .putBoolean("time_ann_silent", swSilent.isChecked())
                            .putInt("time_ann_interval", spInt.getSelectedItemPosition())
                            .putInt("time_ann_tone", spTone.getSelectedItemPosition())
                            .putString("time_ann_voice", vKey)
                            .apply();
                    AnnouncerReceiver.scheduleTimeAnnouncer(ctx);
                    Toast.makeText(ctx, "Smart Time Announcer Saved!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void showBatteryAnnouncerDialog(Context ctx, List<VoiceModel> voices) {
        SharedPreferences prefs = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
        ScrollView sv = new ScrollView(ctx);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(42, 24, 42, 24);

        Switch swEnable = addToggle(ctx, box, "Enable Smart Battery & Power Announcer",
                "Turn on to hear periodic battery level updates and power alerts.",
                prefs.getBoolean("bat_ann_enabled", false));
        Switch swSilent = addToggle(ctx, box, "Respect Silent & Vibrate Mode",
                "When enabled, battery alerts will pause while your phone is on Silent or Vibrate.",
                prefs.getBoolean("bat_ann_silent", true));
        Switch swFull = addToggle(ctx, box, "Announce Charging Complete (100%)",
                "Speaks an alert when your battery reaches 100 percent.",
                prefs.getBoolean("bat_ann_full", true));
        Switch swLow = addToggle(ctx, box, "Announce Battery Low (20% or below)",
                "Speaks a warning when battery drops to 20 percent or lower.",
                prefs.getBoolean("bat_ann_low", true));
        Switch swCharger = addToggle(ctx, box, "Announce Charger Connect / Disconnect",
                "Speaks immediately when you plug in or unplug the charger.",
                prefs.getBoolean("bat_ann_charger", true));

        String[] intervals = {"Every 5 Minutes", "Every 15 Minutes", "Every 30 Minutes", "Every 1 Hour"};
        Spinner spInt = addDropdown(ctx, box, "Battery Check Interval: Choose how often to announce battery status.",
                intervals, prefs.getInt("bat_ann_interval", 1));
        Spinner spTone = addDropdown(ctx, box, "Alert Tone: Select a chime tone to play before battery alerts.",
                AnnouncerEngine.TONE_NAMES, prefs.getInt("bat_ann_tone", 1));

        Spinner[] spVoiceHolder = new Spinner[1];
        List<VoiceModel> filteredVoices = new ArrayList<>();
        attachLangVoicePickers(ctx, box, voices, prefs.getString("bat_ann_voice", ""), spVoiceHolder, filteredVoices);
        addBatteryPermBtn(ctx, box);

        Button btnTest = new Button(ctx);
        btnTest.setText("Test Battery Announcement");
        btnTest.setOnClickListener(v -> {
            int pos = spVoiceHolder[0].getSelectedItemPosition();
            String vKey = (pos >= 0 && pos < filteredVoices.size()) ? filteredVoices.get(pos).getKey() : "";
            int pct = AnnouncerReceiver.getBatteryPercent(ctx);
            boolean chg = AnnouncerReceiver.isCharging(ctx);
            Toast.makeText(ctx, "Testing Battery Announcement...", Toast.LENGTH_SHORT).show();
            String effKey = (vKey != null && !vKey.isEmpty()) ? vKey : ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE).getString("saved_voice_key", "");
            AnnouncerEngine.announce(ctx, AnnouncerLocale.formatBattery(effKey, pct, chg), vKey, spTone.getSelectedItemPosition());
        });
        box.addView(btnTest);

        sv.addView(box);
        new AlertDialog.Builder(ctx)
                .setTitle("Smart Battery & Power Announcer")
                .setView(sv)
                .setPositiveButton("Save Settings", (d, w) -> {
                    int pos = spVoiceHolder[0].getSelectedItemPosition();
                    String vKey = (pos >= 0 && pos < filteredVoices.size()) ? filteredVoices.get(pos).getKey() : "";
                    prefs.edit()
                            .putBoolean("bat_ann_enabled", swEnable.isChecked())
                            .putBoolean("bat_ann_silent", swSilent.isChecked())
                            .putBoolean("bat_ann_full", swFull.isChecked())
                            .putBoolean("bat_ann_low", swLow.isChecked())
                            .putBoolean("bat_ann_charger", swCharger.isChecked())
                            .putInt("bat_ann_interval", spInt.getSelectedItemPosition())
                            .putInt("bat_ann_tone", spTone.getSelectedItemPosition())
                            .putString("bat_ann_voice", vKey)
                            .apply();
                    AnnouncerReceiver.scheduleBatteryAnnouncer(ctx);
                    Toast.makeText(ctx, "Smart Battery Announcer Saved!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void showQuietHoursDialog(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
        ScrollView sv = new ScrollView(ctx);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(42, 24, 42, 24);

        Switch swQuiet = addToggle(ctx, box, "Enable Quiet Hours (Do Not Disturb)",
                "Automatically pause Time and Battery announcements during your sleep hours.",
                prefs.getBoolean("quiet_hours_enabled", false));

        String[] hours = {"12:00", "1:00", "2:00", "3:00", "4:00", "5:00", "6:00", "7:00", "8:00", "9:00", "10:00", "11:00"};
        int s24 = prefs.getInt("quiet_start_24h", 23);
        int e24 = prefs.getInt("quiet_end_24h", 6);

        Spinner spStart = addDropdown(ctx, box, "Quiet Hours Start Time: Choose the hour when announcements should pause.", hours, s24 % 12);
        Switch swStartPm = addToggle(ctx, box, "Start Time PM (Off = AM, On = PM)", "Toggle AM or PM for Start Time.", s24 >= 12);

        Spinner spEnd = addDropdown(ctx, box, "Quiet Hours End Time: Choose the hour when announcements should resume.", hours, e24 % 12);
        Switch swEndPm = addToggle(ctx, box, "End Time PM (Off = AM, On = PM)", "Toggle AM or PM for End Time.", e24 >= 12);

        sv.addView(box);
        new AlertDialog.Builder(ctx)
                .setTitle("Quiet Hours Settings")
                .setView(sv)
                .setPositiveButton("Save Quiet Hours", (d, w) -> {
                    int startH = (spStart.getSelectedItemPosition() % 12) + (swStartPm.isChecked() ? 12 : 0);
                    int endH = (spEnd.getSelectedItemPosition() % 12) + (swEndPm.isChecked() ? 12 : 0);
                    prefs.edit()
                            .putBoolean("quiet_hours_enabled", swQuiet.isChecked())
                            .putInt("quiet_start_24h", startH)
                            .putInt("quiet_end_24h", endH)
                            .apply();
                    Toast.makeText(ctx, "Quiet Hours Saved!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
