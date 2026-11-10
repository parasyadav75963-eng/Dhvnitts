package com.itfb.dhwanitts;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private SharedPreferences prefs;
    private Spinner spinnerLanguage, spinnerVoice, spinnerNumber, spinnerPunctuation;
    private SeekBar seekbarSpeechRate, seekbarPitch;
    private TextView tvSpeechRate, tvPitch;
    private Switch switchForcePitch, switchForceSpeech;

    private List<VoiceModel> allDownloadedVoices = new ArrayList<>();
    private List<String> downloadedLanguages = new ArrayList<>();
    private List<VoiceModel> currentVoicesForLang = new ArrayList<>();
    
    private ArrayAdapter<String> langAdapter;
    private ArrayAdapter<String> voiceAdapter;
    private List<String> voiceDisplayNames = new ArrayList<>();
    
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

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
        switchForcePitch = view.findViewById(R.id.switch_force_pitch);
        switchForceSpeech = view.findViewById(R.id.switch_force_speech);

        Button btnTest = view.findViewById(R.id.btn_test);
        Button btnSave = view.findViewById(R.id.btn_save_settings);
        Button btnReset = view.findViewById(R.id.btn_reset);
        Button btnClear = view.findViewById(R.id.btn_clear_settings);

        scanDownloadedVoices();
        setupSpinners();

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateVoiceSpinner(downloadedLanguages.get(position));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        loadSettings();

        seekbarSpeechRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { tvSpeechRate.setText("Speech Rate: " + progress + "%"); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekbarPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { tvPitch.setText("Pitch Rate: " + progress + "%"); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnSave.setOnClickListener(v -> saveSettings());
        btnReset.setOnClickListener(v -> resetSettings());
        btnClear.setOnClickListener(v -> clearSettings());
        
        btnTest.setOnClickListener(v -> {
            int pos = spinnerVoice.getSelectedItemPosition();
            if (currentVoicesForLang.isEmpty() || pos < 0) {
                Toast.makeText(getContext(), "Please download a voice first!", Toast.LENGTH_SHORT).show();
            } else {
                VoiceModel selectedVoice = currentVoicesForLang.get(pos);
                float speed = seekbarSpeechRate.getProgress() / 50.0f;
                Toast.makeText(getContext(), "Playing Piper preview...", Toast.LENGTH_SHORT).show();
                String previewText = "Hello, this is a test preview.";
                if (selectedVoice.getLanguageName().equalsIgnoreCase("Hindi") || selectedVoice.getLanguageName().equalsIgnoreCase("Nepali")) {
                    previewText = "नमस्ते, यह ध्वनि टीटीएस की आवाज़ का परीक्षण है।";
                }
                playRealPreview(selectedVoice.getKey(), previewText, speed);
            }
        });

        return view;
    }

    private void playRealPreview(String voiceKey, String text, float speed) {
        executorService.execute(() -> {
            try {
                File voicesDir = new File(requireContext().getFilesDir(), "voices");
                File modelFile = new File(voicesDir, voiceKey + ".onnx");
                File configFile = new File(voicesDir, voiceKey + ".onnx.json");

                if (modelFile.exists() && configFile.exists()) {
                    PiperEngine engine = new PiperEngine();
                    if (engine.initEngine(modelFile.getAbsolutePath(), configFile.getAbsolutePath())) {
                        byte[] audioData = engine.synthesizeText(text, speed);
                        if (audioData != null && audioData.length > 0) {
                            int minBuf = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuf, audioData.length), AudioTrack.MODE_STREAM);
                            audioTrack.play();
                            audioTrack.write(audioData, 0, audioData.length);
                            audioTrack.stop();
                            audioTrack.release();
                        } else {
                            mainHandler.post(() -> Toast.makeText(getContext(), "Synthesis failed (Audio empty)", Toast.LENGTH_SHORT).show());
                        }
                        engine.destroyEngine();
                    } else {
                        mainHandler.post(() -> Toast.makeText(getContext(), "Engine Init Failed! File corrupt.", Toast.LENGTH_LONG).show());
                    }
                } else {
                    mainHandler.post(() -> Toast.makeText(getContext(), "Voice files missing! Please redownload.", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void scanDownloadedVoices() {
        allDownloadedVoices.clear();
        downloadedLanguages.clear();
        File voicesDir = new File(requireContext().getFilesDir(), "voices");
        
        try {
            File cachedJson = new File(requireContext().getFilesDir(), "cached_voices.json");
            if (!cachedJson.exists()) {
                downloadedLanguages.add("Open Voice Library to sync");
                return;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(requireContext().openFileInput("cached_voices.json")));
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONObject root = new JSONObject(sb.toString());
            File[] downloadedFiles = voicesDir.listFiles((dir, name) -> name.endsWith(".onnx"));
            
            if (downloadedFiles != null) {
                for (File file : downloadedFiles) {
                    String key = file.getName().replace(".onnx", "");
                    if (root.has(key)) {
                        JSONObject voiceObj = root.getJSONObject(key);
                        String langEnglish = voiceObj.getJSONObject("language").optString("name_english", "Unknown");
                        String voiceName = voiceObj.optString("name", "Unknown");
                        String quality = voiceObj.optString("quality", "");
                        
                        allDownloadedVoices.add(new VoiceModel(key, langEnglish, "", voiceName, quality, "", "", "", true));
                        if (!downloadedLanguages.contains(langEnglish)) {
                            downloadedLanguages.add(langEnglish);
                        }
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }

        if (downloadedLanguages.isEmpty()) { downloadedLanguages.add("No downloaded voices"); }
    }

    private void setupSpinners() {
        langAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, downloadedLanguages);
        spinnerLanguage.setAdapter(langAdapter);
        String[] numReading = {"Default", "Digits", "Words"};
        spinnerNumber.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, numReading));
        String[] punctuation = {"None", "Some", "Most", "All"};
        spinnerPunctuation.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, punctuation));
    }

    private void updateVoiceSpinner(String selectedLang) {
        currentVoicesForLang.clear();
        voiceDisplayNames.clear();
        
        if (selectedLang.equals("No downloaded voices") || selectedLang.equals("Open Voice Library to sync")) {
            voiceDisplayNames.add("No voices available");
        } else {
            for (VoiceModel v : allDownloadedVoices) {
                if (v.getLanguageName().equals(selectedLang)) {
                    currentVoicesForLang.add(v);
                    voiceDisplayNames.add(v.getVoiceName() + " [" + v.getQuality() + "]");
                }
            }
        }
        voiceAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, voiceDisplayNames);
        spinnerVoice.setAdapter(voiceAdapter);
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("speech_rate", seekbarSpeechRate.getProgress());
        editor.putInt("pitch_rate", seekbarPitch.getProgress());
        editor.putBoolean("force_pitch", switchForcePitch.isChecked());
        editor.putBoolean("force_speech", switchForceSpeech.isChecked());
        
        int selectedVoicePos = spinnerVoice.getSelectedItemPosition();
        if (selectedVoicePos >= 0 && currentVoicesForLang.size() > selectedVoicePos) {
            VoiceModel selectedVoice = currentVoicesForLang.get(selectedVoicePos);
            // TTS service ke liye asali model key save kar rahe hain
            editor.putString("saved_voice_key", selectedVoice.getKey());
        }
        
        editor.putInt("num_pos", spinnerNumber.getSelectedItemPosition());
        editor.putInt("punc_pos", spinnerPunctuation.getSelectedItemPosition());
        editor.apply();
        Toast.makeText(getContext(), "Settings Saved as Default!", Toast.LENGTH_SHORT).show();
    }

    private void loadSettings() {
        seekbarSpeechRate.setProgress(prefs.getInt("speech_rate", 50));
        seekbarPitch.setProgress(prefs.getInt("pitch_rate", 50));
        switchForcePitch.setChecked(prefs.getBoolean("force_pitch", false));
        switchForceSpeech.setChecked(prefs.getBoolean("force_speech", false));
        spinnerNumber.setSelection(prefs.getInt("num_pos", 0));
        spinnerPunctuation.setSelection(prefs.getInt("punc_pos", 0));
    }

    private void resetSettings() {
        seekbarSpeechRate.setProgress(50);
        seekbarPitch.setProgress(50);
        switchForcePitch.setChecked(false);
        switchForceSpeech.setChecked(false);
        if (spinnerLanguage.getCount() > 0) spinnerLanguage.setSelection(0);
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
