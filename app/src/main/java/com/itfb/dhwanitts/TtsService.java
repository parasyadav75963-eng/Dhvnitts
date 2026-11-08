package com.itfb.dhwanitts;

import android.content.Context;
import android.content.SharedPreferences;
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

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Dhwani TTS Service Created");
        // सेटिंग्स पढ़ने के लिए SharedPreferences लोड करना
        prefs = getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
        // C++ इंजन को इनिशियलाइज़ (चालू) करना
        piperEngine = new PiperEngine();
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected String[] onGetLanguage() {
        String savedLang = prefs.getString("saved_lang", "eng");
        return new String[]{savedLang, "IND", ""};
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "TalkBack stopped speaking");
        // जब आप स्क्रीन टच करके TalkBack को चुप कराते हैं, 
        // तो यहाँ से हम C++ इंजन को भी रुकने का सिग्नल देंगे (भविष्य में)
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        // 1. TalkBack से टेक्स्ट प्राप्त करना
        CharSequence textCharSequence = request.getCharSequenceText();
        if (TextUtils.isEmpty(textCharSequence)) {
            callback.done();
            return;
        }
        String text = textCharSequence.toString();

        // 2. यूज़र की सेट की गई वॉइस का नाम पता करना
        String savedVoice = prefs.getString("saved_voice", "");
        if (savedVoice.isEmpty()) {
            Log.e(TAG, "No voice selected by user!");
            callback.error();
            return;
        }

        // 3. डाउनलोड की गई मॉडल फाइल (.onnx) का रास्ता (path) ढूँढना
        File voicesDir = new File(getFilesDir(), "voices");
        File modelFile = new File(voicesDir, savedVoice + ".onnx");
        File configFile = new File(voicesDir, savedVoice + ".onnx.json");

        if (!modelFile.exists()) {
            Log.e(TAG, "Model file is missing: " + modelFile.getAbsolutePath());
            callback.error();
            return;
        }

        // 4. C++ इंजन को वह मॉडल फाइल देकर रेडी करना
        piperEngine.initEngine(modelFile.getAbsolutePath(), configFile.getAbsolutePath());

        // 5. स्पीच रेट सेट करना (अगर 'Force Speech' ऑन है तो ऐप की सेटिंग यूज़ होगी, वरना TalkBack की)
        boolean forceSpeech = prefs.getBoolean("force_speech", false);
        float speed = 1.0f;
        if (forceSpeech) {
            speed = prefs.getInt("speech_rate", 50) / 50.0f;
        } else {
            speed = request.getSpeechRate() / 100.0f; // TalkBack की डिफ़ॉल्ट स्पीड 100 होती है
        }

        // 6. ऑडियो फॉर्मेट सेट करना (16000 Hz, 16-bit PCM, Mono)
        callback.start(16000, android.media.AudioFormat.ENCODING_PCM_16BIT, 1);

        // 7. C++ (Piper) से टेक्स्ट को आवाज़ (Bytes) में बदलवाना
        byte[] audioData = piperEngine.synthesizeText(text, speed);
        
        // 8. आवाज़ को टुकड़ों (chunks) में TalkBack को भेजना ताकि वह तुरंत बोलना शुरू कर दे
        if (audioData != null && audioData.length > 0) {
            int maxBufferSize = callback.getMaxBufferSize();
            int offset = 0;
            while (offset < audioData.length) {
                int bytesToWrite = Math.min(maxBufferSize, audioData.length - offset);
                callback.audioAvailable(audioData, offset, bytesToWrite);
                offset += bytesToWrite;
            }
        }
        
        // बोलना ख़त्म हुआ
        callback.done();
    }
    
    @Override
    public void onDestroy() {
        if (piperEngine != null) {
            piperEngine.destroyEngine(); // मेमोरी साफ़ करना
        }
        super.onDestroy();
    }
}
