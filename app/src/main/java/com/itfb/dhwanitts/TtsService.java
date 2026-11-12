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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TtsService extends TextToSpeechService {
    private static final String TAG = "DhwaniTTS_Service";
    private PiperEngine piperEngine;
    private SharedPreferences prefs;
    private String currentLoadedModel = "";

    private static class Segment {
        String text;
        int type; // 0 = Primary, 1 = Secondary (English), 2 = Number
        Segment(String t, int ty) { text = t; type = ty; }
    }

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

    private String applyNumberAndPunctuationRules(String input, int numMode, int puncMode) {
        String out = input;
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
                     .replace("/", " slash ").replace("_", " underscore ");
        }
        return out;
    }

    private List<Segment> splitSegments(String text, int engineMode, boolean hasSecVoice, boolean hasNumVoice) {
        List<Segment> list = new ArrayList<>();
        if (text.isEmpty()) return list;
        if (engineMode == 0 && !hasNumVoice) {
            list.add(new Segment(text, 0));
            return list;
        }
        int curType = -1;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            int charType = curType;
            if (Character.isDigit(cp) || (cp >= 0x0966 && cp <= 0x096F)) {
                charType = hasNumVoice ? 2 : 0;
            } else if ((cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z')) {
                charType = (engineMode >= 1 && hasSecVoice) ? 1 : 0;
            } else if (Character.isLetter(cp)) {
                charType = 0;
            } else {
                if (curType == -1) charType = 0;
            }

            if (curType != -1 && charType != curType) {
                list.add(new Segment(sb.toString(), curType));
                sb.setLength(0);
            }
            curType = charType;
            sb.appendCodePoint(cp);
        }
        if (sb.length() > 0) list.add(new Segment(sb.toString(), curType == -1 ? 0 : curType));
        return list;
    }

    private byte[] applyPitchShift(byte[] pcmBytes, float pitchFactor) {
        if (pcmBytes == null || pcmBytes.length < 4 || Math.abs(pitchFactor - 1.0f) < 0.04f) {
            return pcmBytes;
        }
        int totalSamples = pcmBytes.length / 2;
        int outSamples = Math.max(1, (int) (totalSamples / pitchFactor));
        byte[] outBytes = new byte[outSamples * 2];
        for (int i = 0; i < outSamples; i++) {
            int srcIdx = Math.min(totalSamples - 1, (int) (i * pitchFactor));
            outBytes[i * 2] = pcmBytes[srcIdx * 2];
            outBytes[i * 2 + 1] = pcmBytes[srcIdx * 2 + 1];
        }
        return outBytes;
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
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

            String secModel = resolveModelPath(prefs.getString("sec_voice_key", prefs.getString("sec_model_path", "")));
            String numModel = resolveModelPath(prefs.getString("num_voice_key", prefs.getString("num_model_path", "")));
            int engineMode = prefs.getInt("engine_mode", 0);
            boolean hasSec = !secModel.isEmpty() && !secModel.equals(priModel);
            boolean hasNum = !numModel.isEmpty() && !numModel.equals(priModel);

            int numMode = prefs.getInt("num_pos", 0);
            int puncMode = prefs.getInt("punc_pos", 0);
            String processedText = applyNumberAndPunctuationRules(cs.toString(), numMode, puncMode);

            boolean forceSpeech = prefs.getBoolean("force_speech", false);
            float baseSpeed = forceSpeech ? (prefs.getInt("speech_rate", 50) / 50.0f) : (request.getSpeechRate() / 100.0f);
            if (baseSpeed < 0.2f) baseSpeed = 0.2f;
            if (baseSpeed > 4.0f) baseSpeed = 4.0f;

            boolean forcePitch = prefs.getBoolean("force_pitch", false);
            float pitchFactor = forcePitch ? (0.5f + (prefs.getInt("pitch_rate", 50) / 100.0f)) : (request.getPitch() / 100.0f);
            if (pitchFactor < 0.5f) pitchFactor = 0.5f;
            if (pitchFactor > 2.0f) pitchFactor = 2.0f;

            float speedForPiper = baseSpeed / pitchFactor;
            String espeakDataPath = PiperEngine.setupEspeakData(this);

            List<Segment> segments = splitSegments(processedText, engineMode, hasSec, hasNum);
            callback.start(22050, AudioFormat.ENCODING_PCM_16BIT, 1);

            ByteArrayOutputStream fullAudio = new ByteArrayOutputStream();
            for (Segment seg : segments) {
                if (seg.text.trim().isEmpty()) continue;
                String targetModel = priModel;
                if (seg.type == 1 && hasSec) targetModel = secModel;
                else if (seg.type == 2 && hasNum) targetModel = numModel;
                String targetConfig = targetModel + ".json";

                if (!currentLoadedModel.equals(targetModel)) {
                    if (piperEngine.initEngine(targetModel, targetConfig, espeakDataPath)) {
                        currentLoadedModel = targetModel;
                    } else if (!targetModel.equals(priModel) && piperEngine.initEngine(priModel, priModel + ".json", espeakDataPath)) {
                        currentLoadedModel = priModel;
                    } else {
                        continue;
                    }
                }
                byte[] chunk = piperEngine.synthesizeText(seg.text, speedForPiper);
                if (chunk != null && chunk.length > 0) {
                    byte[] pitched = applyPitchShift(chunk, pitchFactor);
                    try { fullAudio.write(pitched); } catch (Exception ignored) {}
                }
            }

            byte[] audioData = fullAudio.toByteArray();
            if (audioData.length == 0) {
                audioData = new byte[440];
            }
            int maxBufferSize = callback.getMaxBufferSize();
            int offset = 0;
            while (offset < audioData.length) {
                int bytesToWrite = Math.min(maxBufferSize, audioData.length - offset);
                callback.audioAvailable(audioData, offset, bytesToWrite);
                offset += bytesToWrite;
            }
            callback.done();
        }
    }

    @Override
    public void onDestroy() {
        if (piperEngine != null) {
            piperEngine.destroyEngine();
            currentLoadedModel = "";
        }
        super.onDestroy();
    }
}
