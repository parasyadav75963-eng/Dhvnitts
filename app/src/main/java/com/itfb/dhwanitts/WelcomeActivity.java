package com.itfb.dhwanitts;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {

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

        btnStart.setOnClickListener(v -> {
            prefs.edit().putBoolean("welcomed_v2", true).apply();
            startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
            finish();
        });
    }
}
