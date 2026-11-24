package com.itfb.dhwanitts;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import java.io.File;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnnouncerEngine {
    private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor();
    private static volatile AudioTrack activeTrack = null;

    public static final String[] TONE_NAMES = {
        "None (No Alert Tone)", "Crystal Chime", "Soft Bell",
        "Digital Pulse", "Gentle Ding", "Classic Double Beep"
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
        return (startH < endH) ? (nowH >= startH && nowH < endH) : (nowH >= startH || nowH < endH);
    }

    public static void stopCurrent() {
        try {
            if (activeTrack != null) {
                if (activeTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) activeTrack.stop();
                activeTrack.release();
                activeTrack = null;
            }
        } catch (Exception ignored) {}
    }

    public static void playToneSync(int toneIdx) {
        if (toneIdx <= 0) return;
        int sr = 22050;
        double[] freqs = (toneIdx == 1) ? new double[]{880, 1320} : (toneIdx == 2) ? new double[]{660, 990} :
                         (toneIdx == 3) ? new double[]{1000, 1400} : (toneIdx == 4) ? new double[]{1175} : new double[]{900, 0, 900};
        int durMs = (toneIdx == 2) ? 240 : (toneIdx == 4) ? 220 : (toneIdx == 1) ? 180 : 125;
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
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(pcm.length, 4096))
                    .setTransferMode(AudioTrack.MODE_STATIC).build();
            track.write(pcm, 0, pcm.length);
            track.play();
            Thread.sleep((long) durMs * freqs.length + 80);
            track.stop();
            track.release();
        } catch (Exception ignored) {}
    }

    private static void playPcmSync(byte[] pcm, int sr) {
        if (pcm == null || pcm.length == 0) return;
        try {
            int minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(minBuf * 2, 8192))
                    .setTransferMode(AudioTrack.MODE_STREAM).build();
            activeTrack = track;
            track.play();
            int off = 0;
            while (off < pcm.length && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                int written = track.write(pcm, off, Math.min(4096, pcm.length - off));
                if (written <= 0) break;
                off += written;
            }
            Thread.sleep(250);
            track.stop();
            track.release();
            if (activeTrack == track) activeTrack = null;
        } catch (Exception ignored) {}
    }

    public static void announce(Context ctx, String text, String voiceKey, int toneIdx) {
        if (ctx == null || text == null || text.trim().isEmpty()) return;
        final Context appCtx = ctx.getApplicationContext();
        QUEUE.execute(() -> {
            synchronized (PiperEngine.class) {
                try {
                    SharedPreferences prefs = appCtx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
                    String key = (voiceKey != null && !voiceKey.isEmpty()) ? voiceKey : prefs.getString("saved_voice_key", "");
                    File voicesDir = new File(appCtx.getFilesDir(), "voices");
                    File mFile = new File(voicesDir, key + ".onnx");
                    if (!mFile.exists()) {
                        File[] all = voicesDir.listFiles((d, n) -> n.endsWith(".onnx"));
                        if (all != null && all.length > 0) mFile = all[0];
                    }
                    if (!mFile.exists()) return;
                    File cFile = new File(mFile.getAbsolutePath() + ".json");
                    if (!cFile.exists()) return;

                    PiperEngine engine = new PiperEngine();
                    String esp = PiperEngine.setupEspeakData(appCtx);
                    PiperEngine.applyModeForPath(mFile.getAbsolutePath());
                    if (engine.initEngine(mFile.getAbsolutePath(), cFile.getAbsolutePath(), esp)) {
                        float spd = Math.max(0.5f, prefs.getInt("speech_rate", 50) / 50.0f);
                        String cleanText = PronunciationDictionary.apply(appCtx, text);
                        byte[] pcm = engine.synthesizeText(cleanText, spd);
                        if (pcm != null && pcm.length > 0) {
                            int sr = VoiceLangHelper.readSampleRate(cFile);
                            playToneSync(toneIdx);
                            playPcmSync(pcm, sr);
                        }
                    }
                } catch (Exception ignored) {}
            }
        });
    }
}
