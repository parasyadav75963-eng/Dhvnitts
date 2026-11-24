package com.itfb.dhwanitts;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {

    private AudioTrack welcomeMusicTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("DhwaniTTS_Prefs", MODE_PRIVATE);
        if (prefs.getBoolean("welcomed_v2", false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_welcome);

        TextView tvNotice = findViewById(R.id.tv_welcome_notice);
        Button btnStart = findViewById(R.id.btn_lets_start);

        String welcomeMessage = "Welcome to DhwaniTTS — A Real Game-Changer for Visually Impaired Users!\n\n"
                + "About the Application:\n"
                + "DhwaniTTS is a high-speed, ultra-responsive, offline neural Text-to-Speech engine specifically engineered for screen reader users (TalkBack, Commentary Screen Reader, etc.). It delivers crystal-clear pronunciation across multiple languages with zero-latency audio streaming, customizable speech rate, pitch controls, punctuation reading levels, and number reading modes.\n\n"
                + "About Our Community (ITFB):\n"
                + "Interested Technology for Blind (ITFB) is a dedicated educational initiative and community platform empowering visually impaired individuals across India with accessible technology, digital skills, computer training, Android development, and innovative software solutions.\n\n"
                + "Developer Information:\n"
                + "• Lead Developer & Founder: Paras Kumar Yadav (ITFB)\n"
                + "• Email: contact.i.t.f.b@gmail.com\n\n"
                + "Credits & Open-Source Acknowledgments:\n"
                + "• Powered by Piper Neural TTS Engine (Rhasspy), ONNX Runtime, and eSpeak-NG phonemizer.\n"
                + "• Special thanks to all open-source voice contributors and the entire ITFB community for continuous testing and support.\n\n"
                + "Tap 'OK, Let's Start' below to open DhwaniTTS and configure your voice!";

        tvNotice.setText(welcomeMessage);

        startSoftWelcomeMusic();

        btnStart.setOnClickListener(v -> {
            stopSoftWelcomeMusic();
            prefs.edit().putBoolean("welcomed_v2", true).apply();
            startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
            finish();
        });
    }

    private void startSoftWelcomeMusic() {
        new Thread(() -> {
            try {
                int sr = 22050;
                int durSec = 6;
                int totalSamples = sr * durSec;
                byte[] pcm = new byte[totalSamples * 2];

                // Soft, calming ambient chord progression (low volume so TalkBack stays clear)
                double[][] chords = {
                    {261.63, 329.63, 392.00, 523.25}, // C Major
                    {220.00, 261.63, 329.63, 440.00}  // A Minor
                };
                int half = totalSamples / 2;

                for (int i = 0; i < totalSamples; i++) {
                    double[] chord = (i < half) ? chords[0] : chords[1];
                    int localIdx = (i < half) ? i : (i - half);
                    double env = Math.sin(Math.PI * localIdx / half);
                    double val = 0.0;
                    for (double f : chord) {
                        val += Math.sin(2.0 * Math.PI * f * i / sr);
                    }
                    // Gentle low amplitude (approx 14% volume)
                    short sample = (short) ((val / chord.length) * 4200.0 * env);
                    pcm[i * 2] = (byte) (sample & 0xFF);
                    pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
                }

                AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sr)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(pcm.length)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();

                track.write(pcm, 0, pcm.length);
                track.setLoopPoints(0, totalSamples, -1);

                synchronized (this) {
                    if (isFinishing() || isDestroyed()) {
                        track.release();
                        return;
                    }
                    welcomeMusicTrack = track;
                    welcomeMusicTrack.play();
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private synchronized void stopSoftWelcomeMusic() {
        if (welcomeMusicTrack != null) {
            try {
                if (welcomeMusicTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    welcomeMusicTrack.stop();
                }
                welcomeMusicTrack.release();
            } catch (Exception ignored) {}
            welcomeMusicTrack = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSoftWelcomeMusic();
    }

    @Override
    protected void onDestroy() {
        stopSoftWelcomeMusic();
        super.onDestroy();
    }
}
