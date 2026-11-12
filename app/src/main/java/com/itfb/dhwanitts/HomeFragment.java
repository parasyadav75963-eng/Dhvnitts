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
    private Spinner spinnerEngineMode, spinnerLanguage, spinnerVoice, spinnerSecondaryVoice, spinnerNumberVoice, spinnerNumber, spinnerPunctuation;
    private SeekBar seekbarSpeechRate, seekbarPitch;
    private TextView tvSpeechRate, tvPitch, tvActiveConfigSummary;
    private Switch switchForceSpeech, switchForcePitch;

    private ArrayList<VoiceModel> allDownloadedVoices = new ArrayList<>();
    private ArrayList<String> downloadedLanguages = new ArrayList<>();
    private ArrayList<VoiceModel> currentVoicesForLang = new ArrayList<>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean userInteractingWithLang = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        prefs = requireActivity().getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);

        spinnerEngineMode = view.findViewById(R.id.spinner_engine_mode);
        spinnerLanguage = view.findViewById(R.id.spinner_language);
        spinnerVoice = view.findViewById(R.id.spinner_voice);
        spinnerSecondaryVoice = view.findViewById(R.id.spinner_secondary_voice);
        spinnerNumberVoice = view.findViewById(R.id.spinner_number_voice);
        spinnerNumber = view.findViewById(R.id.spinner_number_reading);
        spinnerPunctuation = view.findViewById(R.id.spinner_punctuation);

        seekbarSpeechRate = view.findViewById(R.id.seekbar_speech_rate);
        seekbarPitch = view.findViewById(R.id.seekbar_pitch);
        tvSpeechRate = view.findViewById(R.id.tv_speech_rate_label);
        tvPitch = view.findViewById(R.id.tv_pitch_label);
        tvActiveConfigSummary = view.findViewById(R.id.tv_active_config_summary);

        switchForcePitch = view.findViewById(R.id.switch_force_pitch);
        switchForceSpeech = view.findViewById(R.id.switch_force_speech);

        String[] modes = {"Single Engine Mode", "Dual Engine Mode", "Mix Mode (Auto-TTS Style)"};
        spinnerEngineMode.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, modes));

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

        spinnerLanguage.setOnTouchListener((v, event) -> {
            userInteractingWithLang = true;
            return false;
        });

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (userInteractingWithLang && pos >= 0 && pos < downloadedLanguages.size()) {
                    userInteractingWithLang = false;
                    updateVoiceSpinners(downloadedLanguages.get(pos), "");
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
        userInteractingWithLang = false;
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

        File[] downloadedFiles = voicesDir.listFiles((dir, name) -> name.endsWith(".onnx"));
        if (downloadedFiles != null) {
            for (File file : downloadedFiles) {
                String fileName = file.getName();
                String key = fileName.substring(0, fileName.length() - 5);
                String langName = "Language";
                String voiceName = key;
                String quality = "medium";

                if (root.has(key)) {
                    try {
                        JSONObject vObj = root.getJSONObject(key);
                        JSONObject langObj = vObj.optJSONObject("language");
                        if (langObj != null) {
                            langName = langObj.optString("name_english", langObj.optString("name_native", "Language"));
                        }
                        voiceName = vObj.optString("name", key);
                        quality = vObj.optString("quality", "medium");
                    } catch (Exception ignored) {}
                } else if (key.contains("-")) {
                    String[] parts = key.split("-");
                    langName = parts[0].toUpperCase();
                    if (parts.length > 1) voiceName = parts[1];
                }

                String mPath = file.getAbsolutePath();
                String cPath = mPath + ".json";
                if (new File(cPath).exists()) {
                    allDownloadedVoices.add(new VoiceModel(key, langName, "", voiceName + " (" + quality + ")", quality, mPath, cPath, "", true));
                    if (!downloadedLanguages.contains(langName)) {
                        downloadedLanguages.add(langName);
                    }
                }
            }
        }

        if (downloadedLanguages.isEmpty()) {
            downloadedLanguages.add("Open Voice Library to download");
        }
        spinnerLanguage.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, downloadedLanguages));

        ArrayList<String> extraNames = new ArrayList<>();
        extraNames.add("Same as Primary Voice");
        for (VoiceModel vm : allDownloadedVoices) {
            extraNames.add(vm.getLanguageName() + " - " + vm.getVoiceName());
        }
        ArrayAdapter<String> extraAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, extraNames);
        spinnerSecondaryVoice.setAdapter(extraAdapter);
        spinnerNumberVoice.setAdapter(extraAdapter);
    }

    private void updateVoiceSpinners(String lang, String targetVoiceKey) {
        currentVoicesForLang.clear();
        ArrayList<String> vNames = new ArrayList<>();
        int targetIdx = 0;
        for (int i = 0; i < allDownloadedVoices.size(); i++) {
            VoiceModel vm = allDownloadedVoices.get(i);
            if (vm.getLanguageName().equals(lang)) {
                currentVoicesForLang.add(vm);
                vNames.add(vm.getVoiceName());
                if (!targetVoiceKey.isEmpty() && vm.getKey().equals(targetVoiceKey)) {
                    targetIdx = vNames.size() - 1;
                }
            }
        }
        if (vNames.isEmpty()) vNames.add("No Voice Available");
        spinnerVoice.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, vNames));
        if (!vNames.isEmpty() && targetIdx < vNames.size()) {
            spinnerVoice.setSelection(targetIdx, false);
        }
    }

    private void loadSavedSettings() {
        int mode = prefs.getInt("engine_mode", 0);
        if (mode >= 0 && mode < spinnerEngineMode.getCount()) spinnerEngineMode.setSelection(mode, false);

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
            String firstLang = downloadedLanguages.get(0);
            spinnerLanguage.setSelection(0, false);
            updateVoiceSpinners(firstLang, allDownloadedVoices.get(0).getKey());
        } else {
            updateVoiceSpinners("", "");
        }

        String secKey = prefs.getString("sec_voice_key", "");
        int secIdx = 0;
        for (int i = 0; i < allDownloadedVoices.size(); i++) {
            if (allDownloadedVoices.get(i).getKey().equals(secKey)) {
                secIdx = i + 1;
                break;
            }
        }
        if (secIdx < spinnerSecondaryVoice.getCount()) spinnerSecondaryVoice.setSelection(secIdx, false);

        String numKey = prefs.getString("num_voice_key", "");
        int numIdx = 0;
        for (int i = 0; i < allDownloadedVoices.size(); i++) {
            if (allDownloadedVoices.get(i).getKey().equals(numKey)) {
                numIdx = i + 1;
                break;
            }
        }
        if (numIdx < spinnerNumberVoice.getCount()) spinnerNumberVoice.setSelection(numIdx, false);
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("engine_mode", spinnerEngineMode.getSelectedItemPosition());
        editor.putInt("speech_rate", seekbarSpeechRate.getProgress());
        editor.putInt("pitch_rate", seekbarPitch.getProgress());
        editor.putInt("volume_boost", 100);
        editor.putBoolean("force_speech", switchForceSpeech.isChecked());
        editor.putBoolean("force_pitch", switchForcePitch.isChecked());
        editor.putInt("num_pos", spinnerNumber.getSelectedItemPosition());
        editor.putInt("punc_pos", spinnerPunctuation.getSelectedItemPosition());

        int vPos = spinnerVoice.getSelectedItemPosition();
        if (vPos >= 0 && vPos < currentVoicesForLang.size()) {
            VoiceModel selectedVoice = currentVoicesForLang.get(vPos);
            editor.putString("saved_voice_key", selectedVoice.getKey());
            editor.putString("saved_model_path", selectedVoice.getModelPath());
            editor.putString("saved_config_path", selectedVoice.getConfigPath());
            editor.putString("saved_voice_display", selectedVoice.getLanguageName() + " - " + selectedVoice.getVoiceName());
        }

        int secPos = spinnerSecondaryVoice.getSelectedItemPosition();
        editor.putInt("sec_spinner_pos", secPos);
        if (secPos > 0 && (secPos - 1) < allDownloadedVoices.size()) {
            VoiceModel secVm = allDownloadedVoices.get(secPos - 1);
            editor.putString("sec_voice_key", secVm.getKey());
            editor.putString("sec_model_path", secVm.getModelPath());
            editor.putString("sec_voice_display", secVm.getLanguageName() + " - " + secVm.getVoiceName());
        } else {
            editor.putString("sec_voice_key", "");
            editor.putString("sec_model_path", "");
            editor.putString("sec_voice_display", "Same as Primary Voice");
        }

        int numVoicePos = spinnerNumberVoice.getSelectedItemPosition();
        editor.putInt("num_voice_spinner_pos", numVoicePos);
        if (numVoicePos > 0 && (numVoicePos - 1) < allDownloadedVoices.size()) {
            VoiceModel numVm = allDownloadedVoices.get(numVoicePos - 1);
            editor.putString("num_voice_key", numVm.getKey());
            editor.putString("num_model_path", numVm.getModelPath());
            editor.putString("num_voice_display", numVm.getLanguageName() + " - " + numVm.getVoiceName());
        } else {
            editor.putString("num_voice_key", "");
            editor.putString("num_model_path", "");
            editor.putString("num_voice_display", "Same as Primary Voice");
        }

        editor.commit();
        Toast.makeText(getContext(), "Settings Saved as Default!", Toast.LENGTH_SHORT).show();
    }

    private void updateActiveConfigDisplay() {
        String[] modes = {"Single Engine Mode", "Dual Engine Mode", "Mix Mode (Auto-TTS Style)"};
        int savedMode = prefs.getInt("engine_mode", 0);
        String modeName = (savedMode >= 0 && savedMode < modes.length) ? modes[savedMode] : modes[0];

        String priDisplay = prefs.getString("saved_voice_display", "Not Saved Yet");
        if (priDisplay.equals("Not Saved Yet") && !allDownloadedVoices.isEmpty()) {
            VoiceModel first = allDownloadedVoices.get(0);
            priDisplay = first.getLanguageName() + " - " + first.getVoiceName() + " (Auto)";
        }
        String secDisplay = prefs.getString("sec_voice_display", "Same as Primary Voice");
        String numDisplay = prefs.getString("num_voice_display", "Same as Primary Voice");
        int rate = prefs.getInt("speech_rate", 50);
        int pitch = prefs.getInt("pitch_rate", 50);

        String summary = "Active Engine Mode: " + modeName
                + "\nPrimary Voice: " + priDisplay
                + "\nSecondary Voice: " + secDisplay
                + "\nNumber Voice: " + numDisplay
                + "\nSpeech Rate: " + rate + "% | Pitch: " + pitch + "% | Volume: 100%";
        tvActiveConfigSummary.setText(summary);
    }

    private void testVoicePreview() {
        int vPos = spinnerVoice.getSelectedItemPosition();
        if (vPos < 0 || vPos >= currentVoicesForLang.size()) {
            Toast.makeText(getContext(), "Please download and select a voice first!", Toast.LENGTH_SHORT).show();
            return;
        }
        final VoiceModel testVm = currentVoicesForLang.get(vPos);
        final float speed = Math.max(0.2f, seekbarSpeechRate.getProgress() / 50.0f);
        final Context appCtx = requireContext().getApplicationContext();
        final String savedKey = prefs.getString("saved_voice_key", "");

        Toast.makeText(getContext(), "Playing Test Preview...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            synchronized (PiperEngine.class) {
                try {
                    PiperEngine previewEngine = new PiperEngine();
                    String espeakPath = PiperEngine.setupEspeakData(appCtx);
                    boolean ok = previewEngine.initEngine(testVm.getModelPath(), testVm.getConfigPath(), espeakPath);
                    if (ok) {
                        byte[] pcm = previewEngine.synthesizeText("नमस्ते, Welcome to Dhwani TTS. 1 2 3.", speed);
                        if (pcm != null && pcm.length > 0) {
                            PiperAudioPlayer.playAudio(pcm, 22050);
                        } else {
                            mainHandler.post(() -> Toast.makeText(appCtx, "Audio empty!", Toast.LENGTH_SHORT).show());
                        }
                        if (!savedKey.isEmpty() && !savedKey.equals(testVm.getKey())) {
                            File voicesDir = new File(appCtx.getFilesDir(), "voices");
                            File defModel = new File(voicesDir, savedKey + ".onnx");
                            File defConf = new File(voicesDir, savedKey + ".onnx.json");
                            if (defModel.exists() && defConf.exists()) {
                                previewEngine.initEngine(defModel.getAbsolutePath(), defConf.getAbsolutePath(), espeakPath);
                            }
                        }
                    } else {
                        mainHandler.post(() -> Toast.makeText(appCtx, "Engine initialization failed!", Toast.LENGTH_SHORT).show());
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> Toast.makeText(appCtx, "Preview error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    private void resetSettings() {
        spinnerEngineMode.setSelection(0, false);
        seekbarSpeechRate.setProgress(50);
        seekbarPitch.setProgress(50);
        switchForcePitch.setChecked(false);
        switchForceSpeech.setChecked(false);
        if (spinnerLanguage.getCount() > 0 && !downloadedLanguages.isEmpty()) {
            spinnerLanguage.setSelection(0, false);
            updateVoiceSpinners(downloadedLanguages.get(0), "");
        }
        if (spinnerSecondaryVoice.getCount() > 0) spinnerSecondaryVoice.setSelection(0, false);
        if (spinnerNumberVoice.getCount() > 0) spinnerNumberVoice.setSelection(0, false);
        spinnerNumber.setSelection(0, false);
        spinnerPunctuation.setSelection(0, false);
        saveSettings();
        Toast.makeText(getContext(), "Reset to Default!", Toast.LENGTH_SHORT).show();
    }
}
