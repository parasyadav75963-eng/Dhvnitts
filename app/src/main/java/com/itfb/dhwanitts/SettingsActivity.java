package com.itfb.dhwanitts;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);

        Switch switchHighContrast = findViewById(R.id.switch_high_contrast);
        Switch switchWifiOnly = findViewById(R.id.switch_wifi_only);
        Switch switchDarkMode = findViewById(R.id.switch_dark_mode);
        Spinner spinnerFontSize = findViewById(R.id.spinner_font_size);

        // फॉन्ट साइज़ ड्रॉपडाउन के लिए विकल्प सेट करना
        String[] fontSizes = {"Small", "Default", "Large", "Extra Large"};
        ArrayAdapter<String> fontAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fontSizes);
        spinnerFontSize.setAdapter(fontAdapter);

        // पुरानी सेव की गई सेटिंग्स को लोड करना (वाई-फाई ओनली को डिफ़ॉल्ट रूप से 'true' रखा है ताकि डेटा खर्च न हो)
        switchHighContrast.setChecked(prefs.getBoolean("high_contrast", false));
        switchWifiOnly.setChecked(prefs.getBoolean("wifi_only", true)); 
        switchDarkMode.setChecked(prefs.getBoolean("dark_mode", false));
        spinnerFontSize.setSelection(prefs.getInt("font_size_pos", 1)); // 1 यानी "Default"

        // वाई-फाई ओनली स्विच का लॉजिक
        switchWifiOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("wifi_only", isChecked).apply();
        });

        // हाई कंट्रास्ट स्विच का लॉजिक
        switchHighContrast.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("high_contrast", isChecked).apply();
        });

        // डार्क मोड स्विच का लॉजिक (इसे दबाते ही पूरी ऐप की थीम तुरंत बदल जाएगी)
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("dark_mode", isChecked).apply();
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        // फॉन्ट साइज़ सेलेक्ट करने पर उसे सेव करना
        spinnerFontSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt("font_size_pos", position).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
}
