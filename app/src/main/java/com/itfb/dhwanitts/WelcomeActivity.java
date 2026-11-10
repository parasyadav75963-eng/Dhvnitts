package com.itfb.dhwanitts;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        SharedPreferences prefs = getSharedPreferences("DhwaniPrefs", MODE_PRIVATE);
        if (prefs.getBoolean("welcomed", false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_welcome);

        TextView tvNotice = findViewById(R.id.tv_welcome_notice);
        Button btnStart = findViewById(R.id.btn_lets_start);

        if (tvNotice != null) {
            tvNotice.setText("Welcome to DhwaniTTS!\n\nPlease note that our neural text-to-speech engine currently supports Hindi and English languages. The app is currently in developing mode, so you may encounter some technical issues or unusual pronunciations. If you face any technical problems, please feel free to contact the developer.\n\nThank you for your patience and support!");
        }

        if (btnStart != null) {
            btnStart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.edit().putBoolean("welcomed", true).apply();
                    startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
                    finish();
                }
            });
        }
    }
}
