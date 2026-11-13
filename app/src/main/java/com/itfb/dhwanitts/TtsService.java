package com.itfb.dhwanitts;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.text.TextUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TtsService extends TextToSpeechService {
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

    private String cleanAndNormalizeText(String input, int numMode, int puncMode) {
        if (input == null || input.isEmpty()) return "";
        String out = input;

        // Split joined CamelCase words cleanly for screen readers
        out = out.replaceAll("([a-z])([A-Z])", "$1 $2");
        out = out.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
        out = out.replace("_", " ");

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

        return out.replaceAll("\\s+", " ").trim();
    }

    private List<String> splitIntoFastChunks(String text) {
        List<String> chunks = new ArrayList<>();
        if (text.isEmpty()) return chunks;

        String[] rawParts = text.split("(?<=[.!?।\\n;:—])\\s+");
        for (String part : rawParts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.length() <= 160) {
                chunks.add(trimmed);
            } else {
                String[] words = trimmed.split("\\s+");
                StringBuilder sb = new StringBuilder();
                for (String w : words) {
                    if (sb.length() + w.length() > 120 && sb.length() > 0) {
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

    // Full Loudness + Crisp Consonant Clarity without Sibilant Distortion
    public static byte[] enhanceHdClarityAndLoudness(byte[] pcm) {
        if (pcm == null || pcm.length < 4) return pcm;
        int n = pcm.length / 2;

        // Pass 1: Apply subtle High-Frequency Presence (Crisp S, Sh, T, K) and find true peak
        float[] processed = new float[n];
        float prev = 0f;
        float maxPeak = 1.0f;

        for (int i = 0; i < n; i++) {
            short s = (short) ((pcm[i * 2] & 0xFF) | (pcm[i * 2 + 1] << 8));
            // 18% high-frequency clarity boost restores sharp consonants without lisping
            float crisp = s + 0.18f * (s - prev);
            prev = s;
            processed[i] = crisp;
            float abs = crisp < 0 ? -crisp : crisp;
            if (abs > maxPeak) maxPeak = abs;
        }

        if (maxPeak < 100.0f) return pcm;

        // Pass 2: True Linear Peak Normalization to 31,500 (96% Full Volume) with ZERO wave-crushing
        float gain = 31500.0f / maxPeak;
        if (gain < 1.35f) gain = 1.35f;
        if (gain > 2.80f) gain = 2.80f;

        byte[] out = new byte[pcm.length];
        for (int i = 0; i < n; i++) {
            float val = processed[i] * gain;
            if (val > 32600.0f) val = 32600.0f;
            if (val < -32600.0f) val = -32600.0f;
            short outSample = (short) val;
            out[i * 2] = (byte) (outSample & 0xFF);
            out[i * 2 + 1] = (byte) ((outSample >> 8) & 0xFF);
        }
        return out;
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

            int numMode = prefs.getInt("num_pos", 0);
            int puncMode = prefs.getInt("punc_pos", 0);

            String normalizedText = cleanAndNormalizeText(cs.toString(), numMode, puncMode);
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

            int sampleRate = Math.max(8000, Math.min(48000, (int) (22050 * pitchFactor)));
            float engineSpeed = baseSpeed / pitchFactor;

            callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1);
            int maxBufferSize = callback.getMaxBufferSize();

            List<String> chunks = splitIntoFastChunks(normalizedText);
            boolean wroteAnyAudio = false;

            for (String chunk : chunks) {
                if (isStopped) break;
                byte[] rawPcm = piperEngine.synthesizeText(chunk, engineSpeed);
                if (rawPcm != null && rawPcm.length > 0) {
                    byte[] pcm = enhanceHdClarityAndLoudness(rawPcm);
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
