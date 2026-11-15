package com.itfb.dhwanitts;
import android.content.*;
import android.media.AudioFormat;
import android.os.Build;
import android.speech.tts.*;
import android.text.TextUtils;
import java.io.File;
import java.util.*;

public class TtsService extends TextToSpeechService {
    private PiperEngine priEng, secEng;
    private SharedPreferences prefs;
    private String priMod = "", secMod = "";
    private volatile boolean isStopped = false;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
        priEng = new PiperEngine(); secEng = new PiperEngine();
    }
    @Override protected int onIsLanguageAvailable(String l, String c, String v) { return TextToSpeech.LANG_COUNTRY_AVAILABLE; }
    @Override protected String[] onGetLanguage() { return new String[]{"hin", "IND", ""}; }
    @Override protected int onLoadLanguage(String l, String c, String v) { return TextToSpeech.LANG_COUNTRY_AVAILABLE; }
    @Override protected void onStop() { isStopped = true; }
    @Override public List<Voice> onGetVoices() {
        List<Voice> list = new ArrayList<>();
        File[] files = new File(getFilesDir(), "voices").listFiles((d, n) -> n.endsWith(".onnx"));
        if (files != null) {
            for (File f : files) {
                String k = f.getName().replace(".onnx", "");
                String[] p = k.split("-")[0].split("_");
                Locale loc = p.length >= 2 ? new Locale(p[0], p[1]) : new Locale(p[0]);
                list.add(new Voice(k, loc, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_VERY_LOW, false, new HashSet<>()));
            }
        }
        if (list.isEmpty()) list.add(new Voice("default_dhwani", new Locale("hi", "IN"), 500, 100, false, new HashSet<>()));
        return list;
    }
    @Override public int onIsValidVoiceName(String n) {
        if (n == null || n.isEmpty()) return TextToSpeech.ERROR;
        if ("default_dhwani".equals(n)) return TextToSpeech.SUCCESS;
        String p = resolveModelPath(n);
        return (!p.isEmpty() && new File(p).exists()) ? TextToSpeech.SUCCESS : TextToSpeech.ERROR;
    }
    @Override public int onLoadVoice(String n) { return onIsValidVoiceName(n); }
    @Override public String onGetDefaultVoiceNameFor(String l, String c, String v) {
        String s = prefs.getString("saved_voice_key", "");
        if (!s.isEmpty()) return s.replace(".onnx", "");
        File[] f = new File(getFilesDir(), "voices").listFiles((d, n) -> n.endsWith(".onnx"));
        return (f != null && f.length > 0) ? f[0].getName().replace(".onnx", "") : "default_dhwani";
    }
    private String resolveModelPath(String k) {
        if (k == null || k.isEmpty()) return "";
        File d = new File(k);
        if (d.exists()) return d.getAbsolutePath();
        File v = new File(new File(getFilesDir(), "voices"), k.endsWith(".onnx") ? k : k + ".onnx");
        return v.exists() ? v.getAbsolutePath() : "";
    }
    private String getFallbackModel() {
        File[] f = new File(getFilesDir(), "voices").listFiles((d, n) -> n.endsWith(".onnx"));
        return (f != null && f.length > 0) ? f[0].getAbsolutePath() : "";
    }
    private String findModelForScript(String pfx, String defPath) {
        if (pfx == null || pfx.isEmpty()) return defPath;
        String defName = new File(defPath).getName();
        if (defName.startsWith(pfx)) return defPath;
        if ("hi_".equals(pfx) && (defName.startsWith("mr_") || defName.startsWith("ne_"))) return defPath;
        String pref = prefs.getString("preferred_voice_" + pfx, "");
        if (!pref.isEmpty()) {
            String r = resolveModelPath(pref);
            if (!r.isEmpty()) return r;
        }
        File[] files = new File(getFilesDir(), "voices").listFiles((d, n) -> n.endsWith(".onnx"));
        if (files != null) {
            for (File f : files) if (f.getName().startsWith(pfx)) return f.getAbsolutePath();
        }
        return defPath;
    }
    private String detectCharLang(char c) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(c);
        if (b == Character.UnicodeBlock.DEVANAGARI || b == Character.UnicodeBlock.DEVANAGARI_EXTENDED) return "hi_";
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) return "en_";
        if (b == Character.UnicodeBlock.TELUGU) return "te_";
        if (b == Character.UnicodeBlock.MALAYALAM) return "ml_";
        if (b == Character.UnicodeBlock.TAMIL) return "ta_";
        if (b == Character.UnicodeBlock.BENGALI) return "bn_";
        if (b == Character.UnicodeBlock.GUJARATI) return "gu_";
        if (b == Character.UnicodeBlock.KANNADA) return "kn_";
        return "";
    }
    private List<String[]> segmentByScript(String text, String defPfx) {
        List<String[]> segs = new ArrayList<>();
        if (text == null || text.isEmpty()) return segs;
        StringBuilder sb = new StringBuilder();
        String cur = "";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String det = detectCharLang(c);
            if (det.isEmpty() || cur.isEmpty() || cur.equals(det)) {
                if (!det.isEmpty() && cur.isEmpty()) cur = det;
                sb.append(c);
            } else {
                String s = sb.toString().trim();
                if (!s.isEmpty()) segs.add(new String[]{s, cur});
                sb.setLength(0); sb.append(c); cur = det;
            }
        }
        String rem = sb.toString().trim();
        if (!rem.isEmpty()) segs.add(new String[]{rem, cur.isEmpty() ? defPfx : cur});
        return segs;
    }
    private String cleanAndNormalizeText(String in, int numMode, int puncMode) {
        if (in == null || in.isEmpty()) return "";
        String o = in.replaceAll("([a-z])([A-Z])", "$1 $2").replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2").replace("_", " ");
        if (numMode == 1) o = o.replaceAll("(\\d)", "$1 ");
        if (puncMode >= 2) {
            o = o.replace("@", " at ").replace("#", " hash ").replace("$", " dollar ").replace("%", " percent ").replace("&", " and ").replace("*", " star ").replace("+", " plus ").replace("=", " equals ").replace("/", " slash ");
        }
        if (puncMode == 3) {
            o = o.replace(".", " dot ").replace(",", " comma ").replace("?", " question mark ").replace("!", " exclamation ").replace(":", " colon ").replace(";", " semicolon ").replace("-", " minus ");
        }
        return o.replaceAll("\\s+", " ").trim();
    }
    private List<String> splitIntoFastChunks(String t) {
        List<String> list = new ArrayList<>();
        if (t.isEmpty()) return list;
        String[] parts = t.split("(?<=[.!?।,\\n;:—])\\s+");
        boolean first = true;
        for (String p : parts) {
            String tr = p.trim();
            if (tr.isEmpty()) continue;
            int max = first ? 40 : 120;
            if (tr.length() <= max) { list.add(tr); first = false; }
            else {
                StringBuilder sb = new StringBuilder();
                for (String w : tr.split("\\s+")) {
                    if (sb.length() + w.length() > (first ? 38 : 100) && sb.length() > 0) {
                        list.add(sb.toString().trim()); sb.setLength(0); first = false;
                    }
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(w);
                }
                if (sb.length() > 0) { list.add(sb.toString().trim()); first = false; }
            }
        }
        if (list.isEmpty()) list.add(t);
        return list;
    }
    public static byte[] enhanceHdClarityAndLoudness(byte[] pcm) {
        if (pcm == null || pcm.length < 4) return pcm;
        int n = pcm.length / 2;
        float[] proc = new float[n];
        float prev = 0f, maxP = 1f;
        for (int i = 0; i < n; i++) {
            short s = (short) ((pcm[i * 2] & 0xFF) | (pcm[i * 2 + 1] << 8));
            float c = s + 0.16f * (s - prev);
            prev = s; proc[i] = c;
            float a = c < 0 ? -c : c;
            if (a > maxP) maxP = a;
        }
        if (maxP < 100f) return pcm;
        float g = Math.max(1.35f, Math.min(2.75f, 31500f / maxP));
        byte[] out = new byte[pcm.length];
        for (int i = 0; i < n; i++) {
            short v = (short) Math.max(-32600f, Math.min(32600f, proc[i] * g));
            out[i * 2] = (byte) (v & 0xFF);
            out[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return out;
    }
    private byte[] synthDual(String chunk, String mod, String esp, float spd) {
        if (mod.equals(priMod)) return priEng.synthesizeText(chunk, spd);
        if (mod.equals(secMod)) return secEng.synthesizeText(chunk, spd);
        if (priMod.isEmpty()) {
            if (priEng.initEngine(mod, mod + ".json", esp)) { priMod = mod; return priEng.synthesizeText(chunk, spd); }
        } else {
            if (secEng.initEngine(mod, mod + ".json", esp)) { secMod = mod; return secEng.synthesizeText(chunk, spd); }
        }
        return null;
    }
    @Override protected void onSynthesizeText(SynthesisRequest req, SynthesisCallback cb) {
        isStopped = false;
        CharSequence cs = req.getCharSequenceText();
        if (TextUtils.isEmpty(cs)) { cb.done(); return; }
        synchronized (PiperEngine.class) {
            String reqV = (Build.VERSION.SDK_INT >= 21 && req.getVoiceName() != null) ? req.getVoiceName() : "";
            String pri = (!reqV.isEmpty() && !"default_dhwani".equals(reqV)) ? resolveModelPath(reqV) : "";
            if (pri.isEmpty()) pri = resolveModelPath(prefs.getString("saved_voice_key", ""));
            if (pri.isEmpty()) pri = resolveModelPath(prefs.getString("saved_model_path", ""));
            if (pri.isEmpty()) pri = getFallbackModel();
            if (pri.isEmpty() || !new File(pri).exists()) { cb.error(); return; }
            String esp = PiperEngine.setupEspeakData(this);
            if (!priMod.equals(pri) && !secMod.equals(pri)) {
                if (priEng.initEngine(pri, pri + ".json", esp)) priMod = pri;
                else { cb.error(); return; }
            }
            String norm = cleanAndNormalizeText(cs.toString(), prefs.getInt("num_pos", 0), prefs.getInt("punc_pos", 0));
            if (norm.isEmpty()) { cb.start(22050, AudioFormat.ENCODING_PCM_16BIT, 1); cb.done(); return; }
            float spd = prefs.getBoolean("force_speech", false) ? (prefs.getInt("speech_rate", 50) / 50f) : (req.getSpeechRate() / 100f);
            spd = Math.max(0.25f, Math.min(4.0f, spd));
            float pitch = prefs.getBoolean("force_pitch", false) ? (0.5f + (prefs.getInt("pitch_rate", 50) / 100f)) : (req.getPitch() / 100f);
            pitch = Math.max(0.5f, Math.min(2.0f, pitch));
            int sr = Math.max(8000, Math.min(48000, (int) (22050 * pitch)));
            cb.start(sr, AudioFormat.ENCODING_PCM_16BIT, 1);
            int maxB = cb.getMaxBufferSize();
            String defPfx = new File(pri).getName().substring(0, Math.min(3, new File(pri).getName().length()));
            List<String[]> segs = segmentByScript(norm, defPfx);
            boolean wrote = false;
            for (String[] seg : segs) {
                if (isStopped) break;
                String target = findModelForScript(seg[1], pri);
                for (String ch : splitIntoFastChunks(seg[0])) {
                    if (isStopped) break;
                    byte[] raw = synthDual(ch, target, esp, spd / pitch);
                    if (raw != null && raw.length > 0) {
                        byte[] pcm = enhanceHdClarityAndLoudness(raw);
                        wrote = true;
                        for (int off = 0; off < pcm.length && !isStopped; ) {
                            int len = Math.min(maxB, pcm.length - off);
                            cb.audioAvailable(pcm, off, len);
                            off += len;
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
