package com.itfb.dhwanitts;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingsMoreFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings_more, container, false);

        Button btnAbout = view.findViewById(R.id.btn_about_itfb);
        Button btnTtsSettings = view.findViewById(R.id.btn_open_tts_settings);
        Button btnAppSettings = view.findViewById(R.id.btn_app_settings);

        // About ITFB स्क्रीन खोलना
        btnAbout.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), AboutActivity.class));
        });

        // App Settings स्क्रीन खोलना
        btnAppSettings.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), SettingsActivity.class));
        });

        // फ़ोन की डिफ़ॉल्ट TTS सेटिंग खोलना
        btnTtsSettings.setOnClickListener(v -> {
            Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
            try {
                startActivity(intent);
            } catch (Exception e) {
                // अगर किसी फ़ोन में यह शॉर्टकट काम न करे, तो एक्सेसिबिलिटी सेटिंग खोलें
                startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });

        return view;
    }
}
