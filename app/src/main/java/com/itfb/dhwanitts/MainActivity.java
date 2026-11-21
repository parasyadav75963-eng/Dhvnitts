package com.itfb.dhwanitts;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

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