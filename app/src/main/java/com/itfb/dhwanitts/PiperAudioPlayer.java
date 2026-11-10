package com.itfb.dhwanitts;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

public class PiperAudioPlayer {
    private static final String TAG = "PiperAudioPlayer";
    private static AudioTrack currentTrack = null;

    public static synchronized void playAudio(final byte[] pcmData, final int sampleRate) {
        if (pcmData == null || pcmData.length == 0) {
            Log.e(TAG, "PCM Data is empty, cannot play preview.");
            return;
        }

        stopAudio();

        new Thread(() -> {
            try {
                int minBufSize = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                );
                int bufferSize = Math.max(minBufSize * 2, 8192);

                AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();

                synchronized (PiperAudioPlayer.class) {
                    currentTrack = track;
                }

                track.play();
                int offset = 0;
                while (offset < pcmData.length && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    int toWrite = Math.min(4096, pcmData.length - offset);
                    int written = track.write(pcmData, offset, toWrite);
                    if (written <= 0) break;
                    offset += written;
                }

                Thread.sleep(200);
                track.stop();
                track.release();
            } catch (Exception e) {
                Log.e(TAG, "Preview playback error: " + e.getMessage());
            }
        }).start();
    }

    public static synchronized void stopAudio() {
        if (currentTrack != null) {
            try {
                if (currentTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    currentTrack.stop();
                }
                currentTrack.release();
            } catch (Exception ignored) {}
            currentTrack = null;
        }
    }
}
