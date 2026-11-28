package com.itfb.dhwanitts;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private SharedPreferences prefs;
    private Spinner spinnerLanguage, spinnerVoice, spinnerNumber, spinnerPunctuation;
    private SeekBar seekbarSpeechRate, seekbarPitch;
    private TextView tvSpeechRate, tvPitch, tvActiveConfigSummary;
    private Switch switchForceSpeech, switchForcePitch;

    private final ArrayList<VoiceModel> allDownloadedVoices = new ArrayList<>();
    private final ArrayList<String> downloadedLanguages = new ArrayList<>();
    private final ArrayList<VoiceModel> currentVoicesForLang = new ArrayList<>();

    private ArrayAdapter<String> makeVisibleAdapter(List<String> items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, items) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    TextView tv = (TextView) v;
                    tv.setTextColor(0xFFFFFFFF);
                    tv.setTextSize(15f);
                    tv.setPadding(16, 16, 16, 16);
                }
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private ArrayAdapter<String> makeVisibleAdapter(String[] items) {
        ArrayList<String> list = new ArrayList<>();
        for (String s : items) list.add(s);
        return makeVisibleAdapter(list);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        NotificationHelper.attachHomeBanner(view);
        attachTtsDrawerButton(view);
        prefs = requireActivity().getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);

        spinnerLanguage = view.findViewById(R.id.spinner_language);
        spinnerVoice = view.findViewById(R.id.spinner_voice);
        spinnerNumber = view.findViewById(R.id.spinner_number_reading);
        spinnerPunctuation = view.findViewById(R.id.spinner_punctuation);

        seekbarSpeechRate = view.findViewById(R.id.seekbar_speech_rate);
        seekbarPitch = view.findViewById(R.id.seekbar_pitch);
        tvSpeechRate = view.findViewById(R.id.tv_speech_rate_label);
        tvPitch = view.findViewById(R.id.tv_pitch_label);
        tvActiveConfigSummary = view.findViewById(R.id.tv_active_config_summary);

        switchForcePitch = view.findViewById(R.id.switch_force_pitch);
        switchForceSpeech = view.findViewById(R.id.switch_force_speech);

        spinnerNumber.setAdapter(makeVisibleAdapter(new String[]{"Read Full Number Words", "Read Digit by Digit"}));
        spinnerPunctuation.setAdapter(makeVisibleAdapter(new String[]{"None", "Some", "Most", "All"}));

        seekbarSpeechRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) { tvSpeechRate.setText("Speech Rate: " + p + "%"); }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        seekbarPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) { tvPitch.setText("Pitch Rate: " + p + "%"); }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos >= 0 && pos < downloadedLanguages.size()) {
                    updateVoiceSpinners(downloadedLanguages.get(pos), prefs.getString("saved_voice_key", ""));
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        view.findViewById(R.id.btn_test).setOnClickListener(v -> testVoicePreview());
        view.findViewById(R.id.btn_save_settings).setOnClickListener(v -> { saveSettings(); updateActiveConfigDisplay(); });
        view.findViewById(R.id.btn_reset).setOnClickListener(v -> { resetSettings(); updateActiveConfigDisplay(); });

        refreshAllData();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAllData();
    }

    private void refreshAllData() {
        scanDownloadedVoices();
        loadSavedSettings();
        updateActiveConfigDisplay();
    }

    private void scanDownloadedVoices() {
        allDownloadedVoices.clear();
        downloadedLanguages.clear();
        File voicesDir = new File(requireContext().getFilesDir(), "voices");
        JSONObject root = new JSONObject();

        try {
            File cachedJson = new File(requireContext().getFilesDir(), "cached_voices.json");
            if (cachedJson.exists()) {
                BufferedReader br = new BufferedReader(new InputStreamReader(requireContext().openFileInput("cached_voices.json")));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                root = new JSONObject(sb.toString());
            }
        } catch (Exception ignored) {}

        File[] files = voicesDir.listFiles((dir, name) -> name.endsWith(".onnx"));
        if (files != null) {
            for (File file : files) {
                String rawKey = file.getName().substring(0, file.getName().length() - 5);
                String modeTag = rawKey.endsWith("__turbo") ? " [Turbo]" : (rawKey.endsWith("__crisp") ? " [Crisp]" : (rawKey.endsWith("__sonic") ? " [Sonic]" : " [Natural]"));
                String key = rawKey.replace("__turbo", "").replace("__crisp", "").replace("__sonic", "");
                String langName = "Language", voiceName = key, quality = "medium";

                if (root.has(key)) {
                    try {
                        JSONObject vObj = root.getJSONObject(key);
                        JSONObject langObj = vObj.optJSONObject("language");
                        if (langObj != null) langName = langObj.optString("name_english", "Language");
                        voiceName = vObj.optString("name", key);
                        quality = vObj.optString("quality", "medium");
                    } catch (Exception ignored) {}
                } else if (key.contains("-")) {
                    String[] parts = key.split("-");
                    String c = parts[0].toLowerCase();
                    if (c.startsWith("hi")) langName = "Hindi";
                    else if (c.startsWith("en")) langName = "English";
                    else if (c.startsWith("ne")) langName = "Nepali";
                    else if (c.startsWith("mr")) langName = "Marathi";
                    else if (c.startsWith("gu")) langName = "Gujarati";
                    else if (c.startsWith("bn")) langName = "Bengali";
                    else if (c.startsWith("ta")) langName = "Tamil";
                    else if (c.startsWith("te")) langName = "Telugu";
                    else if (c.startsWith("kn")) langName = "Kannada";
                    else if (c.startsWith("ml")) langName = "Malayalam";
                    else langName = parts[0].toUpperCase();
                    if (parts.length > 1) voiceName = parts[1];
                }

                String mPath = file.getAbsolutePath(), cPath = mPath + ".json";
                if (new File(cPath).exists()) {
                    allDownloadedVoices.add(new VoiceModel(rawKey, langName, "", voiceName + modeTag + " (" + quality + ")", quality, mPath, cPath, "", true));
                    if (!downloadedLanguages.contains(langName)) downloadedLanguages.add(langName);
                }
            }
        }

        for (VoiceModel evv : EloquenceBridge.getBuiltInVoices()) {
            allDownloadedVoices.add(evv);
            if (!downloadedLanguages.contains(evv.getLanguageName())) downloadedLanguages.add(evv.getLanguageName());
        }
        if (downloadedLanguages.isEmpty()) downloadedLanguages.add("Open Voice Library to download");
        spinnerLanguage.setAdapter(makeVisibleAdapter(downloadedLanguages));
    }

    private void updateVoiceSpinners(String lang, String targetKey) {
        currentVoicesForLang.clear();
        ArrayList<String> vNames = new ArrayList<>();
        int targetIdx = 0;
        for (VoiceModel vm : allDownloadedVoices) {
            if (vm.getLanguageName().equals(lang)) {
                currentVoicesForLang.add(vm);
                vNames.add(vm.getVoiceName());
                if (!targetKey.isEmpty() && vm.getKey().equals(targetKey)) targetIdx = vNames.size() - 1;
            }
        }
        if (vNames.isEmpty()) vNames.add("No Voice Available");
        spinnerVoice.setAdapter(makeVisibleAdapter(vNames));
        if (!vNames.isEmpty() && targetIdx < vNames.size()) spinnerVoice.setSelection(targetIdx, false);
    }

    private void loadSavedSettings() {
        int rate = prefs.getInt("speech_rate", 50), pitch = prefs.getInt("pitch_rate", 50);
        seekbarSpeechRate.setProgress(rate);
        seekbarPitch.setProgress(pitch);
        tvSpeechRate.setText("Speech Rate: " + rate + "%");
        tvPitch.setText("Pitch Rate: " + pitch + "%");

        switchForceSpeech.setChecked(prefs.getBoolean("force_speech", false));
        switchForcePitch.setChecked(prefs.getBoolean("force_pitch", false));
        spinnerNumber.setSelection(prefs.getInt("num_pos", 0), false);
        spinnerPunctuation.setSelection(prefs.getInt("punc_pos", 0), false);

        String savedKey = prefs.getString("saved_voice_key", "");
        String savedLang = "";
        for (VoiceModel vm : allDownloadedVoices) {
            if (vm.getKey().equals(savedKey)) { savedLang = vm.getLanguageName(); break; }
        }

        if (!savedLang.isEmpty()) {
            int langIdx = downloadedLanguages.indexOf(savedLang);
            if (langIdx >= 0) { spinnerLanguage.setSelection(langIdx, false); updateVoiceSpinners(savedLang, savedKey); }
        } else if (!allDownloadedVoices.isEmpty()) {
            spinnerLanguage.setSelection(0, false);
            updateVoiceSpinners(downloadedLanguages.get(0), allDownloadedVoices.get(0).getKey());
        } else {
            updateVoiceSpinners("", "");
        }
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("speech_rate", seekbarSpeechRate.getProgress());
        editor.putInt("pitch_rate", seekbarPitch.getProgress());
        editor.putInt("volume_boost", 100);
        editor.putBoolean("force_speech", switchForceSpeech.isChecked());
        editor.putBoolean("force_pitch", switchForcePitch.isChecked());
        editor.putInt("num_pos", spinnerNumber.getSelectedItemPosition());
        editor.putInt("punc_pos", spinnerPunctuation.getSelectedItemPosition());

        int vPos = spinnerVoice.getSelectedItemPosition();
        if (vPos >= 0 && vPos < currentVoicesForLang.size()) {
            VoiceModel v = currentVoicesForLang.get(vPos);
            editor.putString("saved_voice_key", v.getKey());
            editor.putString("saved_model_path", v.getModelPath());
            editor.putString("saved_config_path", v.getConfigPath());
            editor.putString("saved_voice_display", v.getLanguageName() + " - " + v.getVoiceName());
        }
        editor.commit();
        Toast.makeText(getContext(), "Settings Saved Successfully!", Toast.LENGTH_SHORT).show();
    }

    private void updateActiveConfigDisplay() {
        String priDisplay = prefs.getString("saved_voice_display", "Not Saved Yet");
        if (priDisplay.equals("Not Saved Yet") && !allDownloadedVoices.isEmpty()) {
            VoiceModel first = allDownloadedVoices.get(0);
            priDisplay = first.getLanguageName() + " - " + first.getVoiceName();
        }
        tvActiveConfigSummary.setText("Active Voice: " + priDisplay + "\nSpeech Rate: " + prefs.getInt("speech_rate", 50) + "% | Pitch: " + prefs.getInt("pitch_rate", 50) + "% | Volume: 100%");
    }

    private void testVoicePreview() {
        int vPos = spinnerVoice.getSelectedItemPosition();
        if (vPos < 0 || vPos >= currentVoicesForLang.size()) { Toast.makeText(getContext(), "Please select a downloaded voice first!", Toast.LENGTH_SHORT).show(); return; }
        final VoiceModel testVm = currentVoicesForLang.get(vPos);
        final float speed = Math.max(0.25f, seekbarSpeechRate.getProgress() / 50.0f);
        final float pitchFactor = 0.5f + (seekbarPitch.getProgress() / 100.0f);
        if (EloquenceBridge.isEloquenceKey(testVm.getKey())) {
            final int ratePct = Math.max(20, Math.min(400, seekbarSpeechRate.getProgress() * 2));
            final int pitchPct = Math.max(25, Math.min(200, seekbarPitch.getProgress() * 2));
            final Context appCtxEvv = requireContext().getApplicationContext();
            Toast.makeText(getContext(), "Playing Eloquence Preview...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                String sample = PronunciationDictionary.apply(appCtxEvv, VoiceLangHelper.getSampleTextForVoice(testVm.getKey().replace(EloquenceBridge.KEY_PREFIX, "")));
                byte[] pcm = EloquenceBridge.synthesizePcm(testVm.getKey(), sample, ratePct, pitchPct);
                if (pcm != null && pcm.length > 0) PiperAudioPlayer.playAudio(pcm, 22050);
            }).start();
            return;
        }
        final int playRate = Math.max(8000, Math.min(48000, (int) (VoiceLangHelper.readSampleRate(new File(testVm.getConfigPath())) * pitchFactor)));
        final Context appCtx = requireContext().getApplicationContext();

        Toast.makeText(getContext(), "Playing Preview...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            synchronized (PiperEngine.class) {
                try {
                    PiperEngine engine = new PiperEngine();
                    String espeak = PiperEngine.setupEspeakData(appCtx);
                    String cleanKey = testVm.getKey().replace("__turbo","").replace("__crisp","").replace("__sonic","");
                    VoiceLangHelper.tuneConfigForZeroStutter(new File(testVm.getConfigPath()), cleanKey);
                    PiperEngine.applyModeForPath(testVm.getModelPath());
                    if (engine.initEngine(testVm.getModelPath(), testVm.getConfigPath(), espeak)) {
                        String sample = PronunciationDictionary.apply(appCtx, VoiceLangHelper.getSampleTextForVoice(cleanKey));
                        byte[] pcm = engine.synthesizeText(sample, speed / pitchFactor);
                        if (pcm != null && pcm.length > 0) PiperAudioPlayer.playAudio(pcm, playRate);
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void resetSettings() {
        seekbarSpeechRate.setProgress(50);
        seekbarPitch.setProgress(50);
        switchForcePitch.setChecked(false);
        switchForceSpeech.setChecked(false);
        if (spinnerLanguage.getCount() > 0 && !downloadedLanguages.isEmpty()) {
            spinnerLanguage.setSelection(0, false);
            updateVoiceSpinners(downloadedLanguages.get(0), "");
        }
        spinnerNumber.setSelection(0, false);
        spinnerPunctuation.setSelection(0, false);
        saveSettings();
        Toast.makeText(getContext(), "Reset to Default!", Toast.LENGTH_SHORT).show();
    }

    private void attachTtsDrawerButton(View rootView) {
        android.widget.LinearLayout container = null;
        if (rootView instanceof android.widget.LinearLayout) container = (android.widget.LinearLayout) rootView;
        else if (rootView instanceof ViewGroup && ((ViewGroup) rootView).getChildCount() > 0 && ((ViewGroup) rootView).getChildAt(0) instanceof android.widget.LinearLayout) {
            container = (android.widget.LinearLayout) ((ViewGroup) rootView).getChildAt(0);
        }
        if (container == null || container.findViewWithTag("tts_drawer_btn") != null) return;
        android.widget.LinearLayout bar = new android.widget.LinearLayout(requireContext());
        bar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.Button btn = new android.widget.Button(requireContext());
        btn.setTag("tts_drawer_btn");
        btn.setText("☰ TTS Menu");
        btn.setContentDescription("Open TTS Navigation Drawer");
        btn.setOnClickListener(v -> openTtsDrawerMenu());
        bar.addView(btn);
        container.addView(bar, 0);
    }

    private void openTtsDrawerMenu() {
        String[] items = {
            "Active Mix Languages (Real-Time Toggles)",
            "Smart Time Announcer",
            "Smart Battery & Power Announcer",
            "Smart Call Announcer",
            "Quiet Hours Settings",
            "Watch Official Tutorial"
        };
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("TTS Navigation Menu")
                .setItems(items, (d, which) -> {
                    if (which == 0) showMixLanguageTogglesDialog();
                    else if (which == 1) AnnouncerDialogs.showTimeAnnouncerDialog(requireContext(), allDownloadedVoices);
                    else if (which == 2) AnnouncerDialogs.showBatteryAnnouncerDialog(requireContext(), allDownloadedVoices);
                    else if (which == 3) AnnouncerDialogs.showCallAnnouncerDialog(requireContext(), allDownloadedVoices);
                    else if (which == 4) AnnouncerDialogs.showQuietHoursDialog(requireContext());
                    else if (which == 5) startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://youtu.be/pt9vaF64-EY?si=fxSYcQs0OgGwDSHW")));
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showMixLanguageTogglesDialog() {
        java.util.LinkedHashMap<String, String> langMap = new java.util.LinkedHashMap<>();
        for (VoiceModel vm : allDownloadedVoices) {
            String k = vm.getKey().toLowerCase();
            String iso = k.length() >= 2 ? k.substring(0, 2) : "en";
            if (!langMap.containsKey(iso)) langMap.put(iso, vm.getLanguageName());
        }
        if (langMap.isEmpty()) { Toast.makeText(getContext(), "No downloaded voices found!", Toast.LENGTH_SHORT).show(); return; }
        android.widget.ScrollView sv = new android.widget.ScrollView(requireContext());
        android.widget.LinearLayout list = new android.widget.LinearLayout(requireContext());
        list.setOrientation(android.widget.LinearLayout.VERTICAL);
        list.setPadding(48, 24, 48, 24);
        for (java.util.Map.Entry<String, String> e : langMap.entrySet()) {
            final String iso = e.getKey(), name = e.getValue();
            Switch sw = new Switch(requireContext());
            sw.setText(name + " (" + iso.toUpperCase() + ")");
            sw.setTextSize(16f);
            sw.setPadding(0, 18, 0, 18);
            sw.setChecked(prefs.getBoolean("mix_lang_enabled_" + iso, true));
            sw.setOnCheckedChangeListener((b, isChecked) -> prefs.edit().putBoolean("mix_lang_enabled_" + iso, isChecked).apply());
            list.addView(sw);
        }
        sv.addView(list);
        new android.app.AlertDialog.Builder(requireContext()).setTitle("Active Mix Languages").setView(sv).setPositiveButton("Done", null).show();
    }
}
