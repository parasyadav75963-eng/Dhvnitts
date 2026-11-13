package com.itfb.dhwanitts;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

public class HomeFragment extends Fragment {

    private SharedPreferences prefs;
    private Spinner spinnerLanguage, spinnerVoice, spinnerNumber, spinnerPunctuation;
    private SeekBar seekbarSpeechRate, seekbarPitch;
    private TextView tvSpeechRate, tvPitch, tvActiveConfigSummary;
    private Switch switchForceSpeech, switchForcePitch;

    private ArrayList<VoiceModel> allDownloadedVoices = new ArrayList<>();
    private ArrayList<String> downloadedLanguages = new ArrayList<>();
    private ArrayList<VoiceModel> currentVoicesForLang = new ArrayList<>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean userTouchedLang = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
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

        String[] numOpts = {"Read Full Number Words", "Read Digit by Digit"};
        spinnerNumber.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, numOpts));

        String[] puncOpts = {"None", "Some", "Most", "All"};
        spinnerPunctuation.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, puncOpts));

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
                    String selectedLang = downloadedLanguages.get(pos);
                    String savedKey = prefs.getString("saved_voice_key", "");
                    updateVoiceSpinners(selectedLang, savedKey);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        view.findViewById(R.id.btn_test).setOnClickListener(v -> testVoicePreview());
        view.findViewById(R.id.btn_save_settings).setOnClickListener(v -> {
            saveSettings();
            updateActiveConfigDisplay();
        });
        view.findViewById(R.id.btn_reset).setOnClickListener(v -> {
            resetSettings();
            updateActiveConfigDisplay();
        });

        refreshAllData();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAllData();
    }

    private void refreshAllData() {
        userTouchedLang = false;
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
                String fname = file.getName();
                String key = fname.substring(0, fname.length() - 5);
                String langName = "Language";
                String voiceName = key;
                String quality = "medium";

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
                    String codePart = parts[0].toLowerCase();
                    if (codePart.startsWith("hi")) langName = "Hindi";
                    else if (codePart.startsWith("en")) langName = "English";
                    else if (codePart.startsWith("ne")) langName = "Nepali";
                    else if (codePart.startsWith("mr")) langName = "Marathi";
                    else if (codePart.startsWith("gu")) langName = "Gujarati";
                    else if (codePart.startsWith("bn")) langName = "Bengali";
                    else if (codePart.startsWith("ta")) langName = "Tamil";
                    else if (codePart.startsWith("te")) langName = "Telugu";
                    else if (codePart.startsWith("kn")) langName = "Kannada";
                    else if (codePart.startsWith("ml")) langName = "Malayalam";
                    else langName = parts[0].toUpperCase();
                    if (parts.length > 1) voiceName = parts[1];
                }

                String mPath = file.getAbsolutePath();
                String cPath = mPath + ".json";
                if (new File(cPath).exists()) {
                    allDownloadedVoices.add(new VoiceModel(key, langName, "", voiceName + " (" + quality + ")", quality, mPath, cPath, "", true));
                    if (!downloadedLanguages.contains(langName)) downloadedLanguages.add(langName);
                }
            }
        }

        if (downloadedLanguages.isEmpty()) downloadedLanguages.add("Open Voice Library to download");
        spinnerLanguage.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, downloadedLanguages));
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
        spinnerVoice.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, vNames));
        if (!vNames.isEmpty() && targetIdx < vNames.size()) spinnerVoice.setSelection(targetIdx, false);
    }

    private void loadSavedSettings() {
        int rate = prefs.getInt("speech_rate", 50);
        int pitch = prefs.getInt("pitch_rate", 50);
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
            if (vm.getKey().equals(savedKey)) {
                savedLang = vm.getLanguageName();
                break;
            }
        }

        if (!savedLang.isEmpty()) {
            int langIdx = downloadedLanguages.indexOf(savedLang);
            if (langIdx >= 0) {
                spinnerLanguage.setSelection(langIdx, false);
                updateVoiceSpinners(savedLang, savedKey);
            }
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
        int rate = prefs.getInt("speech_rate", 50);
        int pitch = prefs.getInt("pitch_rate", 50);
        tvActiveConfigSummary.setText("Active Voice: " + priDisplay + "\nSpeech Rate: " + rate + "% | Pitch: " + pitch + "% | Volume: 100%");
    }

    private void testVoicePreview() {
        int vPos = spinnerVoice.getSelectedItemPosition();
        if (vPos < 0 || vPos >= currentVoicesForLang.size()) {
            Toast.makeText(getContext(), "Please select a downloaded voice first!", Toast.LENGTH_SHORT).show();
            return;
        }
        final VoiceModel testVm = currentVoicesForLang.get(vPos);
        final float speed = Math.max(0.25f, seekbarSpeechRate.getProgress() / 50.0f);
        final float pitchFactor = 0.5f + (seekbarPitch.getProgress() / 100.0f);
        final int playRate = Math.max(8000, Math.min(48000, (int) (22050 * pitchFactor)));
        final Context appCtx = requireContext().getApplicationContext();

        Toast.makeText(getContext(), "Playing Preview...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            synchronized (PiperEngine.class) {
                try {
                    PiperEngine engine = new PiperEngine();
                    String espeak = PiperEngine.setupEspeakData(appCtx);
                    if (engine.initEngine(testVm.getModelPath(), testVm.getConfigPath(), espeak)) {
                        byte[] pcm = engine.synthesizeText("Welcome to Dhwani Text to Speech. Navigate up button, double tap to activate.", speed / pitchFactor);
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
}
