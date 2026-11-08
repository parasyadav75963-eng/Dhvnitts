package com.itfb.dhwanitts;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // चेक करें कि क्या यूजर पहले ही ऐप खोल चुका है
        SharedPreferences prefs = getSharedPreferences("DhwaniTTS_Prefs", MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean("isFirstLaunch", true);

        if (!isFirstLaunch) {
            // अगर पहली बार नहीं है, तो सीधे MainActivity पर जाएं
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_welcome);

        Button btnOk = findViewById(R.id.btn_ok_welcome);
        btnOk.setOnClickListener(v -> {
            // पहली बार का स्टेटस सेव करें
            prefs.edit().putBoolean("isFirstLaunch", false).apply();
            
            // MainActivity पर जाएं
            startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
            finish();
        });
    }
}
