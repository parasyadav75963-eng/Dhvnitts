package com.itfb.dhwanitts;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String TUTORIAL_URL = "https://youtu.be/pt9vaF64-EY?si=HdE93h66DI-jTwLU";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NotificationHelper.requestPermissionIfNeeded(this);
        NotificationHelper.checkInBackground(this);
        AnnouncerService.sync(this);
        try { com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("all_users"); } catch (Exception ignored) {}
        handleNotificationIntent(getIntent());

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.nav_voice_library) {
                selectedFragment = new VoiceLibraryFragment();
            } else if (itemId == R.id.nav_settings) {
                selectedFragment = new SettingsMoreFragment();
            }
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }
            return true;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
        }

        showSetupTutorialDialogIfNeeded();
    }

    private void showSetupTutorialDialogIfNeeded() {
        SharedPreferences prefs = getSharedPreferences("DhwaniTTS_Prefs", MODE_PRIVATE);
        if (prefs.getBoolean("tutorial_dialog_dismissed", false)) return;

        String msg = "Watch our official step-by-step setup guide to get the best experience from DhwaniTTS:\n\n"
                + "• Download Hindi, English & regional voices from the Voice Library\n"
                + "• Choose between 4 Engine Modes (Natural, Sonic, Crisp & Turbo)\n"
                + "• Configure DhwaniTTS as your default engine for TalkBack\n"
                + "• Use automatic Mix Language switching & TTS Navigation Menu\n\n"
                + "Would you like to watch the tutorial video now?";

        new AlertDialog.Builder(this)
                .setTitle("Quick Setup Guide - DhwaniTTS")
                .setMessage(msg)
                .setCancelable(true)
                .setPositiveButton("Watch Tutorial", (d, w) -> {
                    prefs.edit().putBoolean("tutorial_dialog_dismissed", true).apply();
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TUTORIAL_URL)));
                    } catch (Exception e) {
                        Toast.makeText(this, "Unable to open YouTube link", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Later", null)
                .setNegativeButton("Don't Show Again", (d, w) -> {
                    prefs.edit().putBoolean("tutorial_dialog_dismissed", true).apply();
                })
                .show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent != null && intent.hasExtra("notif_full")) {
            String title = intent.getStringExtra("notif_title");
            String full = intent.getStringExtra("notif_full");
            String link = intent.getStringExtra("notif_link");
            intent.removeExtra("notif_full");
            NotificationHelper.showFullDetailDialog(this,
                    title != null ? title : "Dhwani TTS Alert",
                    full != null ? full : "",
                    link != null ? link : "");
        }
    }

    @Override
    public void onBackPressed() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav != null && nav.getSelectedItemId() != R.id.nav_home) {
            nav.setSelectedItemId(R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }
}
