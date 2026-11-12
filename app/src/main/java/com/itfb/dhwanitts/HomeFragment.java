package com.itfb.dhwanitts;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
    private SeekBar seekbarSpeechRate, seekbarPitch, seekbarVolumeBoost;
    private TextView tvSpeechRate, tvPitch, tvVolumeBoost;
    private Switch switchForceSpeech, switchForcePitch;

    private ArrayList<VoiceModel> allDownloadedVoices = new ArrayList<>();
    private ArrayList<String> downloadedLanguages = new ArrayList<>();
    private ArrayList<VoiceModel> currentVoicesForLang = new ArrayList<>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

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
        seekbarVolumeBoost = view.findViewById(R.id.seekbar_volume_boost);

        tvSpeechRate = view.findViewById(R.id.tv_speech_rate_label);
        tvPitch = view.findViewById(R.id.tv_pitch_label);
        tvVolumeBoost = view.findViewById(R.id.tv_volume_boost_label);

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

        seekbarVolumeBoost.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) { tvVolumeBoost.setText("Volume Amplification: " + p + "%"); }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        scanDownloadedVoices();
        loadSavedSettings();

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos >= 0 && pos < downloadedLanguages.size()) {
                    updateVoiceSpinners(downloadedLanguages.get(pos));
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        view.findViewById(R.id.btn_test).setOnClickListener(v -> testVoicePreview());
        view.findViewById(R.id.btn_save_settings).setOnClickListener(v -> saveSettings());
        view.findViewById(R.id.btn_reset).setOnClickListener(v -> resetSettings());
        view.findViewById(R.id.btn_clear_settings).setOnClickListener(v -> clearSettings());

        return view;
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
                String langName = "Downloaded Voice";
                String country = "";
                String voiceName = key;
                String quality = "medium";

                if (root.has(key)) {
                    try {
                        JSONObject vObj = root.getJSONObject(key);
                        JSONObject langObj = vObj.optJSONObject("language");
                        if (langObj != null) {
                            langName = langObj.optString("name_english", langObj.optString("name_native", "Language"));
                            country = langObj.optString("country_english", "");
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
                    allDownloadedVoices.add(new VoiceModel(key, langName, country, voiceName + " (" + quality + ")", quality, mPath, cPath, "", true));
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

    private void updateVoiceSpinners(String lang) {
        currentVoicesForLang.clear();
        ArrayList<String> vNames = new ArrayList<>();
        for (VoiceModel vm : allDownloadedVoices) {
            if (vm.getLanguageName().equals(lang)) {
                currentVoicesForLang.add(vm);
                vNames.add(vm.getVoiceName());
            }
        }
        if (vNames.isEmpty()) vNames.add("No Voice Available");
        spinnerVoice.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, vNames));

        String savedKey = prefs.getString("saved_voice_key", "");
        for (int i = 0; i < currentVoicesForLang.size(); i++) {
            if (currentVoicesForLang.get(i).getKey().equals(savedKey)) {
                spinnerVoice.setSelection(i);
                break;
            }
        }
    }

    private void loadSavedSettings() {
        spinnerEngineMode.setSelection(prefs.getInt("engine_mode", 0));
        seekbarSpeechRate.setProgress(prefs.getInt("speech_rate", 50));
        seekbarPitch.setProgress(prefs.getInt("pitch_rate", 50));
        seekbarVolumeBoost.setProgress(prefs.getInt("volume_boost", 100));
        switchForceSpeech.setChecked(prefs.getBoolean("force_speech", false));
        switchForcePitch.setChecked(prefs.getBoolean("force_pitch", false));
        spinnerNumber.setSelection(prefs.getInt("num_pos", 0));
        spinnerPunctuation.setSelection(prefs.getInt("punc_pos", 0));

        String savedKey = prefs.getString("saved_voice_key", "");
        if (!savedKey.isEmpty()) {
            for (VoiceModel vm : allDownloadedVoices) {
                if (vm.getKey().equals(savedKey)) {
                    int langIdx = downloadedLanguages.indexOf(vm.getLanguageName());
                    if (langIdx >= 0) spinnerLanguage.setSelection(langIdx);
                    break;
                }
            }
        }

        int secPos = prefs.getInt("sec_spinner_pos", 0);
        if (secPos < spinnerSecondaryVoice.getCount()) spinnerSecondaryVoice.setSelection(secPos);
        int numVoicePos = prefs.getInt("num_voice_spinner_pos", 0);
        if (numVoicePos < spinnerNumberVoice.getCount()) spinnerNumberVoice.setSelection(numVoicePos);
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("engine_mode", spinnerEngineMode.getSelectedItemPosition());
        editor.putInt("speech_rate", seekbarSpeechRate.getProgress());
        editor.putInt("pitch_rate", seekbarPitch.getProgress());
        editor.putInt("volume_boost", seekbarVolumeBoost.getProgress());
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
        }

        int secPos = spinnerSecondaryVoice.getSelectedItemPosition();
        editor.putInt("sec_spinner_pos", secPos);
        if (secPos > 0 && (secPos - 1) < allDownloadedVoices.size()) {
            VoiceModel secVm = allDownloadedVoices.get(secPos - 1);
            editor.putString("sec_voice_key", secVm.getKey());
            editor.putString("sec_model_path", secVm.getModelPath());
        } else {
            editor.putString("sec_voice_key", "");
            editor.putString("sec_model_path", "");
        }

        int numVoicePos = spinnerNumberVoice.getSelectedItemPosition();
        editor.putInt("num_voice_spinner_pos", numVoicePos);
        if (numVoicePos > 0 && (numVoicePos - 1) < allDownloadedVoices.size()) {
            VoiceModel numVm = allDownloadedVoices.get(numVoicePos - 1);
            editor.putString("num_voice_key", numVm.getKey());
            editor.putString("num_model_path", numVm.getModelPath());
        } else {
            editor.putString("num_voice_key", "");
            editor.putString("num_model_path", "");
        }

        editor.apply();
        Toast.makeText(getContext(), "Settings Saved as Default!", Toast.LENGTH_SHORT).show();
    }

    private void testVoicePreview() {
        int vPos = spinnerVoice.getSelectedItemPosition();
        if (vPos < 0 || vPos >= currentVoicesForLang.size()) {
            Toast.makeText(getContext(), "Please download and select a voice first!", Toast.LENGTH_SHORT).show();
            return;
        }
        VoiceModel testVm = currentVoicesForLang.get(vPos);
        float speed = Math.max(0.2f, seekbarSpeechRate.getProgress() / 50.0f);
        String savedDefaultModel = prefs.getString("saved_model_path", "");
        String dataDir = requireContext().getFilesDir().getAbsolutePath();

        Toast.makeText(getContext(), "Playing Test Preview...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                PiperEngine engine = new PiperEngine();
                if (engine.initEngine(testVm.getModelPath(), testVm.getConfigPath(), dataDir)) {
                    byte[] pcm = engine.synthesizeText("नमस्ते, Welcome to Dhwani TTS by Interested Technology for Blind. 1 2 3.", speed);
                    if (pcm != null && pcm.length > 0) {
                        AudioTrack track = new AudioTrack.Builder()
                                .setAudioAttributes(new AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                        .build())
                                .setAudioFormat(new AudioFormat.Builder()
                                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                        .setSampleRate(22050)
                                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                        .build())
                                .setBufferSizeInBytes(pcm.length)
                                .setTransferMode(AudioTrack.MODE_STATIC)
                                .build();
                        track.write(pcm, 0, pcm.length);
                        track.play();
                        Thread.sleep((pcm.length / 44) + 300);
                        track.release();
                    }
                    // Restore saved default voice so preview does not overwrite active TalkBack voice
                    if (!savedDefaultModel.isEmpty() && new File(savedDefaultModel).exists()) {
                        engine.initEngine(savedDefaultModel, savedDefaultModel + ".json", dataDir);
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(getContext(), "Preview failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void resetSettings() {
        spinnerEngineMode.setSelection(0);
        seekbarSpeechRate.setProgress(50);
        seekbarPitch.setProgress(50);
        seekbarVolumeBoost.setProgress(100);
        switchForcePitch.setChecked(false);
        switchForceSpeech.setChecked(false);
        if (spinnerLanguage.getCount() > 0) spinnerLanguage.setSelection(0);
        if (spinnerSecondaryVoice.getCount() > 0) spinnerSecondaryVoice.setSelection(0);
        if (spinnerNumberVoice.getCount() > 0) spinnerNumberVoice.setSelection(0);
        spinnerNumber.setSelection(0);
        spinnerPunctuation.setSelection(0);
        saveSettings();
        Toast.makeText(getContext(), "Reset to Default", Toast.LENGTH_SHORT).show();
    }

    private void clearSettings() {
        prefs.edit().clear().apply();
        resetSettings();
        Toast.makeText(getContext(), "All Settings Cleared!", Toast.LENGTH_SHORT).show();
    }
}
