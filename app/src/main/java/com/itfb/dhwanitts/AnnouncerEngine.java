package com.itfb.dhwanitts;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import java.io.File;
import java.util.Calendar;

public class AnnouncerEngine {

    public static final String[] TONE_NAMES = {
        "None (No Alert Tone)",
        "Crystal Chime",
        "Soft Bell",
        "Digital Pulse",
        "Gentle Ding",
        "Classic Double Beep"
    };

    public static boolean isSilentBlocked(Context ctx, boolean respectSilent) {
        if (!respectSilent) return false;
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return false;
        int mode = am.getRingerMode();
        return mode == AudioManager.RINGER_MODE_SILENT || mode == AudioManager.RINGER_MODE_VIBRATE;
    }

    public static boolean isInQuietHours(SharedPreferences prefs) {
        if (!prefs.getBoolean("quiet_hours_enabled", false)) return false;
        int startH = prefs.getInt("quiet_start_24h", 23);
        int endH = prefs.getInt("quiet_end_24h", 6);
        int nowH = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (startH == endH) return false;
        if (startH < endH) return nowH >= startH && nowH < endH;
        return nowH >= startH || nowH < endH;
    }

    public static void playToneSync(int toneIdx) {
        if (toneIdx <= 0) return;
        int sr = 22050;
        double[] freqs;
        int durMs;
        if (toneIdx == 1) { freqs = new double[]{880, 1320}; durMs = 180; }
        else if (toneIdx == 2) { freqs = new double[]{660, 990}; durMs = 240; }
        else if (toneIdx == 3) { freqs = new double[]{1000, 1400}; durMs = 120; }
        else if (toneIdx == 4) { freqs = new double[]{1175}; durMs = 220; }
        else { freqs = new double[]{900, 0, 900}; durMs = 130; }

        try {
            int samplesPerNote = (sr * durMs) / 1000;
            byte[] pcm = new byte[samplesPerNote * freqs.length * 2];
            int idx = 0;
            for (double f : freqs) {
                for (int i = 0; i < samplesPerNote; i++) {
                    double env = Math.sin(Math.PI * i / samplesPerNote);
                    short s = (f > 0) ? (short) (Math.sin(2.0 * Math.PI * f * i / sr) * 14000 * env) : 0;
                    pcm[idx++] = (byte) (s & 0xFF);
                    pcm[idx++] = (byte) ((s >> 8) & 0xFF);
                }
            }
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sr)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(pcm.length, 4096))
                    .setTransferMode(AudioTrack.MODE_STATIC).build();
            track.write(pcm, 0, pcm.length);
            track.play();
            Thread.sleep((long) durMs * freqs.length + 80);
            track.stop();
            track.release();
        } catch (Exception ignored) {}
    }

    public static void announce(Context ctx, String text, String voiceKey, int toneIdx) {
        new Thread(() -> {
            synchronized (PiperEngine.class) {
                try {
                    SharedPreferences prefs = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
                    String key = (voiceKey != null && !voiceKey.isEmpty()) ? voiceKey : prefs.getString("saved_voice_key", "");
                    File voicesDir = new File(ctx.getFilesDir(), "voices");
                    File mFile = new File(voicesDir, key + ".onnx");
                    if (!mFile.exists()) {
                        File[] all = voicesDir.listFiles((d, n) -> n.endsWith(".onnx"));
                        if (all != null && all.length > 0) mFile = all[0];
                    }
                    if (!mFile.exists()) return;
                    File cFile = new File(mFile.getAbsolutePath() + ".json");
                    if (!cFile.exists()) return;

                    

                    PiperEngine engine = new PiperEngine();
                    String esp = PiperEngine.setupEspeakData(ctx.getApplicationContext());
                    PiperEngine.applyModeForPath(mFile.getAbsolutePath());
                    if (engine.initEngine(mFile.getAbsolutePath(), cFile.getAbsolutePath(), esp)) {
                        float spd = Math.max(0.5f, prefs.getInt("speech_rate", 50) / 50.0f);
                        byte[] pcm = engine.synthesizeText(text, spd);
                        if (pcm != null && pcm.length > 0) {
                            int sr = VoiceLangHelper.readSampleRate(cFile);
                            playToneSync(toneIdx);
                PiperAudioPlayer.playAudio(pcm, sr);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }
}
