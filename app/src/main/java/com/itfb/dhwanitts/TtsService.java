package com.itfb.dhwanitts;

import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.os.Build;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.speech.tts.Voice;
import android.text.TextUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TtsService extends TextToSpeechService {
    private PiperEngine priEng, secEng;
    private String priMod = "", secMod = "";
    private volatile boolean isStopped = false;
    private SharedPreferences prefs;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("DhwaniTTS_Prefs", MODE_PRIVATE);
        priEng = new PiperEngine();
        secEng = new PiperEngine();
        NotificationHelper.checkInBackground(this);
        try { AnnouncerService.sync(this); } catch (Exception ignored) {}
    }

    @Override protected void onStop() { isStopped = true; }

    private Locale getCleanLocaleForFilename(String f) {
        String l = f.toLowerCase(Locale.ROOT);
        if (l.startsWith("en_in") || l.contains("-en_in")) return new Locale("en", "IN");
        if (l.startsWith("en_gb") || l.contains("-en_gb")) return new Locale("en", "GB");
        if (l.startsWith("en_") || l.startsWith("en-")) return new Locale("en", "US");
        String[] codes = {"hi","mr","te","ta","ml","bn","gu","kn","pa","or","as","sa","ur"};
        for (String c : codes) if (l.startsWith(c + "_") || l.startsWith(c + "-")) return new Locale(c, "IN");
        if (l.startsWith("ne_") || l.startsWith("ne-")) return new Locale("ne", "NP");
        String[] p = f.split("[-_]");
        if (p.length >= 2 && p[0].length() == 2 && p[1].length() == 2) return new Locale(p[0].toLowerCase(Locale.ROOT), p[1].toUpperCase(Locale.ROOT));
        return new Locale("en", "US");
    }

    private List<File> getInstalledModelFiles() {
        List<File> m = new ArrayList<>();
        File[] fs = new File(getFilesDir(), "voices").listFiles();
        if (fs != null) for (File f : fs) if (f.isFile() && f.getName().endsWith(".onnx") && f.length() > 100000) m.add(f);
        Collections.sort(m, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return m;
    }

    @Override public List<Voice> onGetVoices() {
        List<Voice> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 21) {
            Set<String> feat = new HashSet<>();
            for (File f : getInstalledModelFiles()) {
                String k = f.getName().replace(".onnx", "");
                list.add(new Voice(k, getCleanLocaleForFilename(k), 500, 100, false, feat));
            }
            if (list.isEmpty()) list.add(new Voice("en_US-lessac-medium", new Locale("en", "US"), 500, 100, false, feat));
        }
        return list;
    }

    @Override public int onIsValidVoiceName(String n) {
        if (TextUtils.isEmpty(n)) return TextToSpeech.ERROR;
        String p = resolveModelPath(n);
        if (!p.isEmpty() && new File(p).exists()) return TextToSpeech.SUCCESS;
        return !getInstalledModelFiles().isEmpty() ? TextToSpeech.SUCCESS : TextToSpeech.ERROR;
    }

    @Override public int onLoadVoice(String n) { return onIsValidVoiceName(n); }

    @Override public String onGetDefaultVoiceNameFor(String l, String c, String v) {
        String sv = prefs != null ? prefs.getString("saved_voice_key", "") : "";
        if (!sv.isEmpty() && !resolveModelPath(sv).isEmpty()) return sv;
        String iso = normalizeLangToIso2(l);
        for (File f : getInstalledModelFiles()) {
            String k = f.getName().replace(".onnx", "");
            if (getCleanLocaleForFilename(k).getLanguage().equalsIgnoreCase(iso)) return k;
        }
        List<File> all = getInstalledModelFiles();
        return all.isEmpty() ? "en_US-lessac-medium" : all.get(0).getName().replace(".onnx", "");
    }

    private String normalizeLangToIso2(String l) {
        if (l == null || l.isEmpty()) return "en";
        String s = l.toLowerCase(Locale.ROOT).trim();
        String[][] map = {{"eng","en"},{"hin","hi"},{"mar","mr"},{"tel","te"},{"tam","ta"},{"mal","ml"},{"ben","bn"},{"guj","gu"},{"kan","kn"},{"pan","pa"},{"ori","or"},{"ory","or"},{"asm","as"},{"san","sa"},{"nep","ne"},{"urd","ur"}};
        for (String[] m : map) if (s.startsWith(m[0]) || s.equals(m[1])) return m[1];
        return s.length() >= 2 ? s.substring(0, 2) : "en";
    }

    @Override protected String[] onGetLanguage() {
        String sv = prefs != null ? prefs.getString("saved_voice_key", "") : "";
        Locale loc = !sv.isEmpty() ? getCleanLocaleForFilename(sv) : Locale.getDefault();
        return new String[]{loc.getISO3Language(), loc.getISO3Country(), loc.getVariant()};
    }

    @Override protected int onIsLanguageAvailable(String l, String c, String v) {
        String iso = normalizeLangToIso2(l);
        for (File f : getInstalledModelFiles()) if (getCleanLocaleForFilename(f.getName()).getLanguage().equalsIgnoreCase(iso)) return TextToSpeech.LANG_COUNTRY_AVAILABLE;
        return !getInstalledModelFiles().isEmpty() ? TextToSpeech.LANG_AVAILABLE : TextToSpeech.LANG_MISSING_DATA;
    }

    @Override protected int onLoadLanguage(String l, String c, String v) { return onIsLanguageAvailable(l, c, v); }

    private String resolveModelPath(String k) {
        if (TextUtils.isEmpty(k)) return "";
        File d = new File(k);
        if (d.exists() && d.isFile()) return d.getAbsolutePath();
        File f = new File(new File(getFilesDir(), "voices"), k.endsWith(".onnx") ? k : k + ".onnx");
        return f.exists() ? f.getAbsolutePath() : "";
    }

    private String getFallbackModel() {
        List<File> list = getInstalledModelFiles();
        for (File f : list) if (getCleanLocaleForFilename(f.getName()).getLanguage().equals("en")) return f.getAbsolutePath();
        return list.isEmpty() ? "" : list.get(0).getAbsolutePath();
    }

    private String findModelForScript(String langCode, String defPath) {
        if (TextUtils.isEmpty(langCode)) return defPath;
        String iso = normalizeLangToIso2(langCode);
        if (prefs != null && !prefs.getBoolean("mix_lang_enabled_" + iso, true)) return defPath;
        if (getCleanLocaleForFilename(new File(defPath).getName()).getLanguage().equalsIgnoreCase(iso)) return defPath;
        String suf = defPath.contains("__turbo") ? "__turbo.onnx" : (defPath.contains("__crisp") ? "__crisp.onnx" : (defPath.contains("__sonic") ? "__sonic.onnx" : ".onnx"));
        for (File f : getInstalledModelFiles()) {
            String fn = f.getName();
            if (getCleanLocaleForFilename(fn).getLanguage().equalsIgnoreCase(iso) && fn.endsWith(suf)) return f.getAbsolutePath();
        }
        for (File f : getInstalledModelFiles()) if (getCleanLocaleForFilename(f.getName()).getLanguage().equalsIgnoreCase(iso)) return f.getAbsolutePath();
        return defPath;
    }

    private String detectCharLang(char c) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(c);
        if (b == Character.UnicodeBlock.DEVANAGARI || b == Character.UnicodeBlock.DEVANAGARI_EXTENDED) return "hi";
        if (b == Character.UnicodeBlock.BENGALI) return "bn";
        if (b == Character.UnicodeBlock.GURMUKHI) return "pa";
        if (b == Character.UnicodeBlock.GUJARATI) return "gu";
        if (b == Character.UnicodeBlock.ORIYA) return "or";
        if (b == Character.UnicodeBlock.TAMIL) return "ta";
        if (b == Character.UnicodeBlock.TELUGU) return "te";
        if (b == Character.UnicodeBlock.KANNADA) return "kn";
        if (b == Character.UnicodeBlock.MALAYALAM) return "ml";
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) return "en";
        return "";
    }

    private List<String[]> segmentByScript(String text, String defaultLang) {
        List<String[]> segs = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return segs;
        StringBuilder sb = new StringBuilder();
        String cur = "";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String det = detectCharLang(c);
            if (det.isEmpty()) sb.append(c);
            else if (cur.isEmpty() || cur.equals(det)) { cur = det; sb.append(c); }
            else {
                String s = sb.toString().trim();
                if (!s.isEmpty()) segs.add(new String[]{s, cur});
                sb.setLength(0); sb.append(c); cur = det;
            }
        }
        String rem = sb.toString().trim();
        if (!rem.isEmpty()) segs.add(new String[]{rem, cur.isEmpty() ? defaultLang : cur});
        return segs;
    }

    private List<String> splitIntoFastChunks(String text) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return out;
        for (String p : text.split("(?<=[.!?।\\n,;:])\\s+")) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        if (out.isEmpty() && !text.trim().isEmpty()) out.add(text.trim());
        return out;
    }

    private String cleanAndNormalizeText(String in, int numMode, int puncMode) {
        if (TextUtils.isEmpty(in)) return "";
        String o = PronunciationDictionary.apply(this, in);
        o = o.replaceAll("([a-z])([A-Z])", "$1 $2").replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2").replace("_", " ").replace("-", " ");
        o = o.replaceAll("(?i)\\btts\\b", "T T S").replaceAll("(?i)\\bapk\\b", "A P K").replaceAll("(?i)\\bui\\b", "U I")
             .replaceAll("(?i)\\bwifi\\b", "Wi-Fi").replaceAll("(?i)\\bbtn\\b", "button").replaceAll("(?i)\\bmb\\b", "megabytes")
             .replaceAll("(?i)\\bgb\\b", "gigabytes").replaceAll("(?i)\\bkb\\b", "kilobytes").replaceAll("(?i)\\bitfb\\b", "I T F B");
        if (numMode == 1) o = o.replaceAll("(\\d)", " $1 ");
        if (puncMode == 0) o = o.replaceAll("[*#~^`|<>{}\\[\\]\\\\]", " ");
        return o.replaceAll("\\s+", " ").trim();
    }

    private byte[] synthDual(String chunk, String mod, String esp, float spd) {
        PiperEngine.applyModeForPath(mod);
        if (mod.equals(priMod)) return priEng.synthesizeText(chunk, spd);
        if (mod.equals(secMod)) return secEng.synthesizeText(chunk, spd);
        if (priMod.isEmpty()) {
            if (priEng.initEngine(mod, mod + ".json", esp)) { priMod = mod; return priEng.synthesizeText(chunk, spd); }
        } else {
            VoiceLangHelper.tuneConfigForZeroStutter(new File(mod + ".json"), new File(mod).getName().replace(".onnx", "").replace("__turbo","").replace("__crisp","").replace("__sonic",""));
            if (secEng.initEngine(mod, mod + ".json", esp)) { secMod = mod; return secEng.synthesizeText(chunk, spd); }
        }
        return null;
    }

    private byte[] resampleAndEnhance(byte[] rawPcm, int inSr, int outSr, float pitch) {
        byte[] clean = enhanceHdClarityAndLoudness(rawPcm);
        if (clean == null || clean.length < 4) return clean;
        double step = ((double) inSr / (double) outSr) * pitch;
        if (Math.abs(step - 1.0) < 0.02) return clean;
        int inSamples = clean.length / 2, outSamples = Math.max(1, (int) (inSamples / step));
        byte[] out = new byte[outSamples * 2];
        for (int i = 0; i < outSamples; i++) {
            double srcIdx = i * step;
            int idx0 = (int) srcIdx, idx1 = Math.min(idx0 + 1, inSamples - 1);
            double frac = srcIdx - idx0;
            short s0 = (short) (((clean[idx0 * 2 + 1]) << 8) | (clean[idx0 * 2] & 0xFF));
            short s1 = (short) (((clean[idx1 * 2 + 1]) << 8) | (clean[idx1 * 2] & 0xFF));
            int val = (int) (s0 + frac * (s1 - s0));
            out[i * 2] = (byte) (val & 0xFF);
            out[i * 2 + 1] = (byte) ((val >> 8) & 0xFF);
        }
        return out;
    }

    private byte[] enhanceHdClarityAndLoudness(byte[] rawPcm) {
        if (rawPcm == null || rawPcm.length < 4) return rawPcm;
        int n = rawPcm.length / 2;
        short[] s = new short[n];
        for (int i = 0; i < n; i++) s[i] = (short) (((rawPcm[i * 2 + 1]) << 8) | (rawPcm[i * 2] & 0xFF));
        int st = 0, en = n - 1, thr = 180;
        while (st < n - 160 && Math.abs(s[st]) < thr) st++;
        st = Math.max(0, st - 64);
        while (en > st + 320 && Math.abs(s[en]) < thr) en--;
        en = Math.min(n - 1, en + 160);
        int len = en - st + 1;
        if (len <= 0) return new byte[0];
        float[] proc = new float[len];
        float prev = 0f;
        for (int i = 0; i < len; i++) { float cur = s[st + i]; proc[i] = cur - (0.18f * prev); prev = cur; }
        int boost = prefs != null ? prefs.getInt("volume_boost", 100) : 100;
        double sumSq = 0;
        for (int i = 0; i < len; i++) sumSq += proc[i] * proc[i];
        float rms = (float) Math.sqrt(sumSq / Math.max(1, len));
        float gain = Math.max(1.3f, Math.min(3.5f, ((rms > 100f) ? (9500.0f / rms) : 2.0f) * Math.max(1.0f, boost / 100.0f)));
        byte[] out = new byte[len * 2];
        for (int i = 0; i < len; i++) {
            float v = proc[i] * gain;
            if (v > 28000f) v = 28000f + (float) Math.tanh((v - 28000f) / 8000f) * 4700f;
            else if (v < -28000f) v = -28000f - (float) Math.tanh((-v - 28000f) / 8000f) * 4700f;
            int pcm = Math.max(-32760, Math.min(32760, Math.round(v)));
            out[i * 2] = (byte) (pcm & 0xFF);
            out[i * 2 + 1] = (byte) ((pcm >> 8) & 0xFF);
        }
        return out;
    }

    @Override protected void onSynthesizeText(SynthesisRequest req, SynthesisCallback cb) {
        isStopped = false;
        NotificationHelper.checkInBackground(this);
        CharSequence cs = req.getCharSequenceText();
        if (TextUtils.isEmpty(cs)) { cb.start(22050, AudioFormat.ENCODING_PCM_16BIT, 1); cb.done(); return; }
        synchronized (PiperEngine.class) {
            String userDef = prefs != null ? resolveModelPath(prefs.getString("saved_voice_key", "")) : "";
            if (userDef.isEmpty() && prefs != null) userDef = resolveModelPath(prefs.getString("saved_model_path", ""));
            if (userDef.isEmpty()) userDef = getFallbackModel();
            if (userDef.isEmpty() || !new File(userDef).exists()) { cb.error(); return; }

            String esp = PiperEngine.setupEspeakData(this);
            if (!priMod.equals(userDef)) {
                VoiceLangHelper.tuneConfigForZeroStutter(new File(userDef + ".json"), new File(userDef).getName().replace(".onnx", ""));
                if (priEng.initEngine(userDef, userDef + ".json", esp)) priMod = userDef;
            }
            int rate = prefs != null ? prefs.getInt("speech_rate", 50) : 50;
            int sysRate = req.getSpeechRate();
            boolean forceSpd = prefs != null && prefs.getBoolean("force_speech", false);
            float homeSpd = Math.max(0.5f, Math.min(2.0f, rate / 50f));
            float tbSpd = (sysRate > 0) ? Math.max(0.5f, Math.min(2.5f, sysRate / 100f)) : 1.0f;
            float spd = forceSpd ? homeSpd : Math.max(0.45f, Math.min(2.5f, tbSpd * homeSpd));

            int numM = prefs != null ? prefs.getInt("num_pos", 0) : 0;
            int puncM = prefs != null ? prefs.getInt("punc_pos", 0) : 0;
            String norm = cleanAndNormalizeText(cs.toString(), numM, puncM);

            cb.start(22050, AudioFormat.ENCODING_PCM_16BIT, 1);
            int maxB = cb.getMaxBufferSize() > 0 ? cb.getMaxBufferSize() : 4096;
            String baseIso = getCleanLocaleForFilename(new File(userDef).getName()).getLanguage();
            boolean wrote = false;

            for (String[] seg : segmentByScript(norm, baseIso)) {
                if (isStopped) break;
                String target = findModelForScript(seg[1], userDef);
                for (String ch : splitIntoFastChunks(seg[0])) {
                    if (isStopped) break;
                    byte[] raw = synthDual(ch, target, esp, spd);
                    if (raw != null && raw.length > 0) {
                        int modSr = VoiceLangHelper.readSampleRate(new File(target + ".json"));
                        int pitchPref = prefs != null ? prefs.getInt("pitch_rate", 50) : 50;
                        float pitchFactor = 0.5f + (pitchPref / 100.0f);
                        byte[] pcm = resampleAndEnhance(raw, modSr, 22050, pitchFactor);
                        if (pcm.length > 0) {
                            wrote = true;
                            for (int off = 0; off < pcm.length && !isStopped; ) {
                                int len = Math.min(maxB, pcm.length - off);
                                cb.audioAvailable(pcm, off, len);
                                off += len;
                            }
                        }
                    }
                }
            }
            if (!wrote && !isStopped) { byte[] sil = new byte[320]; cb.audioAvailable(sil, 0, sil.length); }
            cb.done();
        }
    }

    @Override public void onDestroy() {
        isStopped = true;
        if (priEng != null) { priEng.destroyEngine(); priMod = ""; }
        if (secEng != null) { secEng.destroyEngine(); secMod = ""; }
        super.onDestroy();
    }
}
