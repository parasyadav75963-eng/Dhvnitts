package com.itfb.dhwanitts;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.text.TextUtils;
import android.util.Log;
import java.io.File;

public class TtsService extends TextToSpeechService {
    private static final String TAG = "DhwaniTTS_Service";
    private PiperEngine piperEngine;
    private SharedPreferences prefs;
    private String currentLoadedVoiceKey = ""; // मॉडल को बार-बार लोड होने से रोकने के लिए

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
        piperEngine = new PiperEngine();
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected String[] onGetLanguage() {
        return new String[]{"hin", "IND", ""};
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "TalkBack stopped speaking");
        // यहाँ इंजन को डिस्ट्रॉय न करें, वर्ना अगला वाक्य नहीं बोलेगा
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        CharSequence textCharSequence = request.getCharSequenceText();
        if (TextUtils.isEmpty(textCharSequence)) {
            callback.done();
            return;
        }
        String text = textCharSequence.toString();

        String savedVoiceKey = prefs.getString("saved_voice_key", "");
        if (savedVoiceKey.isEmpty()) {
            callback.error();
            return;
        }

        File voicesDir = new File(getFilesDir(), "voices");
        File modelFile = new File(voicesDir, savedVoiceKey + ".onnx");
        File configFile = new File(voicesDir, savedVoiceKey + ".onnx.json");

        if (!modelFile.exists() || !configFile.exists()) {
            Log.e(TAG, "Model or Config missing!");
            callback.error();
            return;
        }

        // फिक्स: मॉडल केवल तभी लोड होगा जब वॉइस बदली गई हो (TalkBack स्पीड फिक्स)
        if (!savedVoiceKey.equals(currentLoadedVoiceKey)) {
            boolean isInit = piperEngine.initEngine(modelFile.getAbsolutePath(), configFile.getAbsolutePath(), PiperEngine.setupEspeakData(this));
            if (isInit) {
                currentLoadedVoiceKey = savedVoiceKey;
            } else {
                Log.e(TAG, "Piper Engine init failed!");
                callback.error();
                return;
            }
        }

        boolean forceSpeech = prefs.getBoolean("force_speech", false);
        float speed = forceSpeech ? (prefs.getInt("speech_rate", 50) / 50.0f) : (request.getSpeechRate() / 100.0f);

        // Piper C++ 22050Hz PCM जनरेट करता है
        callback.start(22050, AudioFormat.ENCODING_PCM_16BIT, 1);
        
        byte[] audioData = piperEngine.synthesizeText(text, speed);
        
        if (audioData != null && audioData.length > 0) {
            int maxBufferSize = callback.getMaxBufferSize();
            int offset = 0;
            while (offset < audioData.length) {
                int bytesToWrite = Math.min(maxBufferSize, audioData.length - offset);
                callback.audioAvailable(audioData, offset, bytesToWrite);
                offset += bytesToWrite;
            }
            callback.done();
        } else {
            Log.e(TAG, "Synthesis failed (Audio empty) - Espeak-ng data might be missing in C++!");
            callback.error();
        }
    }
    
    @Override
    public void onDestroy() {
        if (piperEngine != null) {
            piperEngine.destroyEngine();
            currentLoadedVoiceKey = "";
        }
        super.onDestroy();
    }
}
