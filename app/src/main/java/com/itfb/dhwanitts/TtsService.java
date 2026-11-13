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
import java.util.ArrayList;
import java.util.List;

public class TtsService extends TextToSpeechService {
    private static final String TAG = "DhwaniTTS_Service";
    private PiperEngine piperEngine;
    private SharedPreferences prefs;
    private String currentLoadedModel = "";
    private volatile boolean isStopped = false;

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
        isStopped = true;
        Log.d(TAG, "TalkBack stopped speaking");
    }

    private String resolveModelPath(String keyOrPath) {
        if (keyOrPath == null || keyOrPath.isEmpty()) return "";
        File direct = new File(keyOrPath);
        if (direct.exists()) return direct.getAbsolutePath();
        File inVoices = new File(new File(getFilesDir(), "voices"), keyOrPath.endsWith(".onnx") ? keyOrPath : keyOrPath + ".onnx");
        if (inVoices.exists()) return inVoices.getAbsolutePath();
        return "";
    }

    private String getFallbackModel() {
        File voicesDir = new File(getFilesDir(), "voices");
        File[] files = voicesDir.listFiles((dir, name) -> name.endsWith(".onnx"));
        if (files != null && files.length > 0) return files[0].getAbsolutePath();
        return "";
    }

    private String cleanAndNormalizeText(String input, boolean isIndicVoice, int numMode, int puncMode) {
        if (input == null || input.isEmpty()) return "";
        String out = input;

        // Split CamelCase words like TalkBack -> Talk Back, WhatsApp -> Whats App, YouTube -> You Tube
        out = out.replaceAll("([a-z])([A-Z])", "$1 $2");
        out = out.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
        out = out.replace("_", " ");

        // Fix common UI abbreviations for crystal clear reading in all languages
        out = out.replaceAll("(?i)\\bnav\\b", "navigate")
                 .replaceAll("(?i)\\bbtn\\b", "button")
                 .replaceAll("(?i)\\bmsg\\b", "message")
                 .replaceAll("(?i)\\binfo\\b", "information")
                 .replaceAll("(?i)\\bsec\\b", "seconds")
                 .replaceAll("(?i)\\bmin\\b", "minutes")
                 .replaceAll("(?i)\\bvol\\b", "volume")
                 .replaceAll("(?i)\\bconfig\\b", "configuration");

        // Vocalizer-style phonetic mapping when a Hindi/Devanagari voice reads common English UI words
        if (isIndicVoice) {
            out = out.replaceAll("(?i)\\bnavigate up\\b", "नेविगेट अप")
                     .replaceAll("(?i)\\bnavigate\\b", "नेविगेट")
                     .replaceAll("(?i)\\bdouble tap to activate\\b", "डबल टैप टू एक्टिवेट")
                     .replaceAll("(?i)\\bdouble tap\\b", "डबल टैप")
                     .replaceAll("(?i)\\bbutton\\b", "बटन")
                     .replaceAll("(?i)\\bchecked\\b", "चेक्ड")
                     .replaceAll("(?i)\\bunchecked\\b", "अनचेक्ड")
                     .replaceAll("(?i)\\bnot checked\\b", "नॉट चेक्ड")
                     .replaceAll("(?i)\\bswitch\\b", "स्विच")
                     .replaceAll("(?i)\\bsettings\\b", "सेटिंग्स")
                     .replaceAll("(?i)\\bsearch\\b", "सर्च")
                     .replaceAll("(?i)\\bmore options\\b", "मोर ऑप्शंस")
                     .replaceAll("(?i)\\bnotification\\b", "नोटिफिकेशन")
                     .replaceAll("(?i)\\bmessage\\b", "मैसेज")
                     .replaceAll("(?i)\\bwhatsapp\\b", "व्हाट्सऐप")
                     .replaceAll("(?i)\\byoutube\\b", "यूट्यूब")
                     .replaceAll("(?i)\\btelegram\\b", "टेलीग्राम")
                     .replaceAll("(?i)\\bgoogle\\b", "गूगल")
                     .replaceAll("(?i)\\bphone\\b", "फोन")
                     .replaceAll("(?i)\\bcamera\\b", "कैमरा")
                     .replaceAll("(?i)\\bgallery\\b", "गैलरी")
                     .replaceAll("(?i)\\bhome\\b", "होम")
                     .replaceAll("(?i)\\bback\\b", "बैक")
                     .replaceAll("(?i)\\boverview\\b", "ओवरव्यू")
                     .replaceAll("(?i)\\brecent apps\\b", "रीसेंट ऐप्स")
                     .replaceAll("(?i)\\bselected\\b", "सिलेक्टेड")
                     .replaceAll("(?i)\\bdisabled\\b", "डिसेबल्ड")
                     .replaceAll("(?i)\\bedit box\\b", "एडिट बॉक्स")
                     .replaceAll("(?i)\\bshowing\\b", "शोइंग")
                     .replaceAll("(?i)\\bkeyboard\\b", "कीबोर्ड")
                     .replaceAll("(?i)\\bwindow\\b", "विंडो")
                     .replaceAll("(?i)\\bscreen\\b", "स्क्रीन")
                     .replaceAll("(?i)\\bbattery\\b", "बैटरी")
                     .replaceAll("(?i)\\bcharging\\b", "चार्जिंग")
                     .replaceAll("(?i)\\bdownload\\b", "डाउनलोड")
                     .replaceAll("(?i)\\bupdate\\b", "अपडेट")
                     .replaceAll("(?i)\\bvoice\\b", "वॉइस")
                     .replaceAll("(?i)\\blanguage\\b", "लैंग्वेज")
                     .replaceAll("(?i)\\bdefault\\b", "डिफ़ॉल्ट")
                     .replaceAll("(?i)\\bactive\\b", "एक्टिव")
                     .replaceAll("(?i)\\bsave\\b", "सेव")
                     .replaceAll("(?i)\\breset\\b", "रीसेट")
                     .replaceAll("(?i)\\bcancel\\b", "कैंसिल")
                     .replaceAll("(?i)\\bdelete\\b", "डिलीट")
                     .replaceAll("(?i)\\bshare\\b", "शेयर")
                     .replaceAll("(?i)\\bcopy\\b", "कॉपी")
                     .replaceAll("(?i)\\bpaste\\b", "पेस्ट");
        }

        if (numMode == 1) {
            out = out.replaceAll("(\\d)", "$1 ");
        }

        if (puncMode == 2) {
            out = out.replace("@", " at ").replace("#", " hash ").replace("$", " dollar ")
                     .replace("%", " percent ").replace("&", " and ").replace("*", " star ")
                     .replace("+", " plus ").replace("=", " equals ").replace("/", " slash ");
        } else if (puncMode == 3) {
            out = out.replace(".", " dot ").replace(",", " comma ").replace("?", " question mark ")
                     .replace("!", " exclamation ").replace(":", " colon ").replace(";", " semicolon ")
                     .replace("@", " at ").replace("#", " hash ").replace("$", " dollar ")
                     .replace("%", " percent ").replace("&", " and ").replace("*", " star ")
                     .replace("+", " plus ").replace("-", " minus ").replace("=", " equals ")
                     .replace("/", " slash ");
        }
        return out.trim();
    }

    private List<String> splitIntoFastChunks(String text) {
        List<String> chunks = new ArrayList<>();
        if (text.isEmpty()) return chunks;

        // Split by sentence punctuation or newlines for instant first-chunk playback
        String[] rawParts = text.split("(?<=[.!?।,\\n;:—])\\s+");
        for (String part : rawParts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            // If a single clause is still very long (> 120 chars), break by words every ~90 chars
            if (trimmed.length() <= 120) {
                chunks.add(trimmed);
            } else {
                String[] words = trimmed.split("\\s+");
                StringBuilder sb = new StringBuilder();
                for (String w : words) {
                    if (sb.length() + w.length() > 90 && sb.length() > 0) {
                        chunks.add(sb.toString().trim());
                        sb.setLength(0);
                    }
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(w);
                }
                if (sb.length() > 0) chunks.add(sb.toString().trim());
            }
        }
        if (chunks.isEmpty()) chunks.add(text);
        return chunks;
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        isStopped = false;
        CharSequence cs = request.getCharSequenceText();
        if (TextUtils.isEmpty(cs)) {
            callback.done();
            return;
        }

        synchronized (PiperEngine.class) {
            String savedVoiceKey = prefs.getString("saved_voice_key", "");
            String priModel = resolveModelPath(savedVoiceKey);
            if (priModel.isEmpty()) priModel = resolveModelPath(prefs.getString("saved_model_path", ""));
            if (priModel.isEmpty()) priModel = getFallbackModel();

            if (priModel.isEmpty() || !new File(priModel).exists()) {
                callback.error();
                return;
            }

            String espeakDataPath = PiperEngine.setupEspeakData(this);
            if (!currentLoadedModel.equals(priModel)) {
                if (piperEngine.initEngine(priModel, priModel + ".json", espeakDataPath)) {
                    currentLoadedModel = priModel;
                } else {
                    callback.error();
                    return;
                }
            }

            boolean isIndic = priModel.contains("hi_") || priModel.contains("mr_") || priModel.contains("ne_") || priModel.contains("bn_") || priModel.contains("gu_") || priModel.contains("ta_") || priModel.contains("te_") || priModel.contains("kn_") || priModel.contains("ml_");
            int numMode = prefs.getInt("num_pos", 0);
            int puncMode = prefs.getInt("punc_pos", 0);

            String normalizedText = cleanAndNormalizeText(cs.toString(), isIndic, numMode, puncMode);
            if (normalizedText.isEmpty()) {
                callback.start(22050, AudioFormat.ENCODING_PCM_16BIT, 1);
                callback.done();
                return;
            }

            boolean forceSpeech = prefs.getBoolean("force_speech", false);
            float baseSpeed = forceSpeech ? (prefs.getInt("speech_rate", 50) / 50.0f) : (request.getSpeechRate() / 100.0f);
            if (baseSpeed < 0.25f) baseSpeed = 0.25f;
            if (baseSpeed > 4.0f) baseSpeed = 4.0f;

            boolean forcePitch = prefs.getBoolean("force_pitch", false);
            float pitchFactor = forcePitch ? (0.5f + (prefs.getInt("pitch_rate", 50) / 100.0f)) : (request.getPitch() / 100.0f);
            if (pitchFactor < 0.5f) pitchFactor = 0.5f;
            if (pitchFactor > 2.0f) pitchFactor = 2.0f;

            // Use hardware sample rate for pitch so audio remains 100% distortion-free and crystal clear
            int sampleRate = Math.max(8000, Math.min(48000, (int) (22050 * pitchFactor)));
            float engineSpeed = baseSpeed / pitchFactor;

            callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1);
            int maxBufferSize = callback.getMaxBufferSize();

            List<String> chunks = splitIntoFastChunks(normalizedText);
            boolean wroteAnyAudio = false;

            for (String chunk : chunks) {
                if (isStopped) break;
                byte[] pcm = piperEngine.synthesizeText(chunk, engineSpeed);
                if (pcm != null && pcm.length > 0) {
                    wroteAnyAudio = true;
                    int offset = 0;
                    while (offset < pcm.length && !isStopped) {
                        int bytesToWrite = Math.min(maxBufferSize, pcm.length - offset);
                        callback.audioAvailable(pcm, offset, bytesToWrite);
                        offset += bytesToWrite;
                    }
                }
            }

            if (!wroteAnyAudio && !isStopped) {
                byte[] silence = new byte[320];
                callback.audioAvailable(silence, 0, silence.length);
            }
            callback.done();
        }
    }

    @Override
    public void onDestroy() {
        isStopped = true;
        if (piperEngine != null) {
            piperEngine.destroyEngine();
            currentLoadedModel = "";
        }
        super.onDestroy();
    }
}
