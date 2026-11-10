package com.itfb.dhwanitts;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

public class PiperAudioPlayer {
    private static final String TAG = "PiperAudioPlayer";
    private static AudioTrack audioTrack = null;

    public static void playAudio(byte[] pcmData, int sampleRate) {
        if (pcmData == null || pcmData.length == 0) {
            Log.e(TAG, "Audio data is empty, cannot play.");
            return;
        }

        stopAudio();

        try {
            int bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            if (bufferSize < pcmData.length) {
                bufferSize = pcmData.length;
            }

            audioTrack = new AudioTrack.Builder()
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
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();

            audioTrack.write(pcmData, 0, pcmData.length);
            audioTrack.play();
            Log.d(TAG, "Playing preview audio of size: " + pcmData.length);

        } catch (Exception e) {
            Log.e(TAG, "Error playing audio: " + e.getMessage());
        }
    }

    public static void stopAudio() {
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception ignored) {}
            audioTrack = null;
        }
    }
}
