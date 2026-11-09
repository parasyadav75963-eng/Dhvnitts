package com.itfb.dhwanitts;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
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
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private SharedPreferences prefs;
    private Spinner spinnerLanguage, spinnerVoice, spinnerNumber, spinnerPunctuation;
    private SeekBar seekbarSpeechRate, seekbarPitch;
    private TextView tvSpeechRate, tvPitch;
    private Switch switchForcePitch, switchForceSpeech; // नया स्विच जोड़ा गया

    private List<VoiceModel> allDownloadedVoices = new ArrayList<>();
    private List<String> downloadedLanguages = new ArrayList<>();
    
    private ArrayAdapter<String> langAdapter;
    private ArrayAdapter<String> voiceAdapter;
    
    // असली आवाज़ निकालने के लिए TextToSpeech इंजन
    private TextToSpeech testTts;

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
        switchForceSpeech = view.findViewById(R.id.switch_force_speech); // इसे UI से जोड़ा गया

        Button btnTest = view.findViewById(R.id.btn_test);
        Button btnSave = view.findViewById(R.id.btn_save_settings);
        Button btnReset = view.findViewById(R.id.btn_reset);
        Button btnClear = view.findViewById(R.id.btn_clear_settings);

        // TTS इंजन को स्टार्ट करना (टेस्टिंग के लिए)
        testTts = new TextToSpeech(getContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                testTts.setLanguage(new Locale("hi", "IN")); // डिफ़ॉल्ट हिंदी पर सेट किया
            }
        });

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
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvSpeechRate.setText("Speech Rate: " + progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekbarPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvPitch.setText("Pitch Rate: " + progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnSave.setOnClickListener(v -> saveSettings());
        btnReset.setOnClickListener(v -> resetSettings());
        btnClear.setOnClickListener(v -> clearSettings());
        
        // असली 'Test Voice' बटन का लॉजिक
        btnTest.setOnClickListener(v -> {
            if (allDownloadedVoices.isEmpty()) {
                Toast.makeText(getContext(), "Please download a voice first!", Toast.LENGTH_SHORT).show();
            } else {
                String voiceName = spinnerVoice.getSelectedItem().toString();
                String textToSpeak = "This is a test preview for " + voiceName + " voice.";
                
                // स्लाइडर की वैल्यू से स्पीड और पिच सेट करना (1.0 नॉर्मल होता है)
                float speed = seekbarSpeechRate.getProgress() / 50.0f;
                float pitch = seekbarPitch.getProgress() / 50.0f;
                testTts.setSpeechRate(speed);
                testTts.setPitch(pitch);
                
                // असली आवाज़ प्ले करना
                testTts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null);
                Toast.makeText(getContext(), "Playing preview...", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    private void scanDownloadedVoices() {
        allDownloadedVoices.clear();
        downloadedLanguages.clear();
        File voicesDir = new File(requireContext().getFilesDir(), "voices");
        
        try {
            InputStream is = requireContext().getAssets().open("voices.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            
            JSONArray jsonArray = new JSONArray(new String(buffer, StandardCharsets.UTF_8));
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String lang = obj.getString("language");
                String name = obj.getString("voiceName");
                
                File checkFile = new File(voicesDir, name + ".onnx");
                if (checkFile.exists()) {
                    allDownloadedVoices.add(new VoiceModel(lang, name, "", "", true));
                    if (!downloadedLanguages.contains(lang)) {
                        downloadedLanguages.add(lang);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (downloadedLanguages.isEmpty()) {
            downloadedLanguages.add("No downloaded voices");
        }
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
        List<String> voicesForLang = new ArrayList<>();
        if (selectedLang.equals("No downloaded voices")) {
            voicesForLang.add("No voices available");
        } else {
            for (VoiceModel v : allDownloadedVoices) {
                if (v.getLanguage().equals(selectedLang)) {
                    voicesForLang.add(v.getVoiceName());
                }
            }
        }
        voiceAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, voicesForLang);
        spinnerVoice.setAdapter(voiceAdapter);
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("speech_rate", seekbarSpeechRate.getProgress());
        editor.putInt("pitch_rate", seekbarPitch.getProgress());
        editor.putBoolean("force_pitch", switchForcePitch.isChecked());
        editor.putBoolean("force_speech", switchForceSpeech.isChecked()); // नया टॉगल सेव किया
        
        if (spinnerLanguage.getSelectedItem() != null && spinnerVoice.getSelectedItem() != null) {
            editor.putString("saved_lang", spinnerLanguage.getSelectedItem().toString());
            editor.putString("saved_voice", spinnerVoice.getSelectedItem().toString());
        }
        
        editor.putInt("num_pos", spinnerNumber.getSelectedItemPosition());
        editor.putInt("punc_pos", spinnerPunctuation.getSelectedItemPosition());
        editor.apply();
        Toast.makeText(getContext(), "Settings Saved as Default!", Toast.LENGTH_SHORT).show();
    }

    private void loadSettings() {
        seekbarSpeechRate.setProgress(prefs.getInt("speech_rate", 50));
        seekbarPitch.setProgress(prefs.getInt("pitch_rate", 50));
        tvSpeechRate.setText("Speech Rate: " + seekbarSpeechRate.getProgress() + "%");
        tvPitch.setText("Pitch Rate: " + seekbarPitch.getProgress() + "%");
        
        switchForcePitch.setChecked(prefs.getBoolean("force_pitch", false));
        switchForceSpeech.setChecked(prefs.getBoolean("force_speech", false)); // नया टॉगल लोड किया
        
        spinnerNumber.setSelection(prefs.getInt("num_pos", 0));
        spinnerPunctuation.setSelection(prefs.getInt("punc_pos", 0));
        
        String savedLang = prefs.getString("saved_lang", "");
        if (downloadedLanguages.contains(savedLang)) {
            spinnerLanguage.setSelection(downloadedLanguages.indexOf(savedLang));
        }
    }

    private void resetSettings() {
        seekbarSpeechRate.setProgress(50);
        seekbarPitch.setProgress(50);
        switchForcePitch.setChecked(false);
        switchForceSpeech.setChecked(false); // नया टॉगल रीसेट किया
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
    
    @Override
    public void onDestroy() {
        if (testTts != null) {
            testTts.stop();
            testTts.shutdown();
        }
        super.onDestroy();
    }
}
