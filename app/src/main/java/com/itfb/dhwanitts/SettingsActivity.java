package com.itfb.dhwanitts;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);

        Switch switchDark = findViewById(R.id.switch_dark_mode);
        Switch switchWifi = findViewById(R.id.switch_wifi_only);
        Switch switchContrast = findViewById(R.id.switch_high_contrast);
        Spinner spinnerFont = findViewById(R.id.spinner_font_size);
        Button btnBattery = findViewById(R.id.btn_battery_optimization);
        Button btnUpdate = findViewById(R.id.btn_check_update);
        Button btnContact = findViewById(R.id.btn_contact_developer);

        String[] fonts = {"Small", "Medium (Default)", "Large", "Extra Large"};
        spinnerFont.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fonts));

        switchDark.setChecked(prefs.getBoolean("dark_mode", true));
        switchWifi.setChecked(prefs.getBoolean("wifi_only", false));
        switchContrast.setChecked(prefs.getBoolean("high_contrast", true));
        spinnerFont.setSelection(prefs.getInt("font_size_pos", 1));

        switchDark.setOnCheckedChangeListener((b, v) -> prefs.edit().putBoolean("dark_mode", v).apply());
        switchWifi.setOnCheckedChangeListener((b, v) -> prefs.edit().putBoolean("wifi_only", v).apply());
        switchContrast.setOnCheckedChangeListener((b, v) -> prefs.edit().putBoolean("high_contrast", v).apply());

        btnBattery.setOnClickListener(v -> requestBatteryExemption());
        btnUpdate.setOnClickListener(v -> checkForGitHubUpdate());

        btnContact.setOnClickListener(v -> {
            Intent email = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:contact.i.t.f.b@gmail.com"));
            email.putExtra(Intent.EXTRA_SUBJECT, "DhwaniTTS Support");
            try {
                startActivity(Intent.createChooser(email, "Contact Developer"));
            } catch (Exception e) {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void requestBatteryExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }
            } else {
                Toast.makeText(this, "Background running is already enabled!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void checkForGitHubUpdate() {
        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/parasyadav75963-eng/Dhvnitts/commits/main");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(8000);
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject json = new JSONObject(sb.toString());
                String latestSha = json.getString("sha").substring(0, 7);
                JSONObject commitObj = json.getJSONObject("commit");
                String commitMsg = commitObj.getString("message");
                String commitDateStr = commitObj.getJSONObject("committer").getString("date");

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                long commitTimeMs = sdf.parse(commitDateStr).getTime();
                long apkTimeMs = getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime;

                String savedSha = prefs.getString("installed_commit_sha", "");
                boolean isLatest = latestSha.equals(savedSha) || (apkTimeMs >= (commitTimeMs - 120000));
                if (isLatest) {
                    prefs.edit().putString("installed_commit_sha", latestSha).apply();
                }

                runOnUiThread(() -> {
                    if (isLatest) {
                        Toast.makeText(this, "Latest update is already installed!", Toast.LENGTH_LONG).show();
                        new AlertDialog.Builder(this)
                                .setTitle("Up to Date")
                                .setMessage("Latest update is already installed (" + latestSha + ").")
                                .setPositiveButton("OK", null)
                                .show();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("New Update Available (" + latestSha + ")")
                                .setMessage("What's New:\n" + commitMsg + "\n\nDownload the latest APK from GitHub?")
                                .setPositiveButton("Download Latest APK", (d, w) -> {
                                    prefs.edit().putString("installed_commit_sha", latestSha).apply();
                                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/parasyadav75963-eng/Dhvnitts/actions")));
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Update check failed. Check internet connection.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
