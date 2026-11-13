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
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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
        btnUpdate.setOnClickListener(v -> checkForGitHubUpdate(this));

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

    public static void checkForGitHubUpdate(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
        Toast.makeText(ctx, "Checking for updates...", Toast.LENGTH_SHORT).show();
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
                long apkTimeMs = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).lastUpdateTime;

                String savedSha = prefs.getString("installed_commit_sha", "");
                boolean isLatest = latestSha.equals(savedSha) || (apkTimeMs >= (commitTimeMs - 120000));
                if (isLatest) {
                    prefs.edit().putString("installed_commit_sha", latestSha).apply();
                }

                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (isLatest) {
                        Toast.makeText(ctx, "Latest update is already installed!", Toast.LENGTH_LONG).show();
                        new AlertDialog.Builder(ctx)
                                .setTitle("Up to Date")
                                .setMessage("Latest update is already installed (" + latestSha + ").")
                                .setPositiveButton("OK", null)
                                .show();
                    } else {
                        new AlertDialog.Builder(ctx)
                                .setTitle("New Update Available (" + latestSha + ")")
                                .setMessage("What's New:\n" + commitMsg + "\n\nTap below to download and install the update directly inside the app.")
                                .setPositiveButton("Download & Install Update", (d, w) -> {
                                    downloadAndInstallApk(ctx, latestSha);
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                });
            } catch (Exception e) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        Toast.makeText(ctx, "Update check failed. Check internet connection.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    public static void downloadAndInstallApk(Context ctx, String targetSha) {
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        AlertDialog progressDialog = new AlertDialog.Builder(ctx)
                .setTitle("Downloading Update...")
                .setMessage("Starting download, please wait...")
                .setCancelable(false)
                .create();
        progressDialog.show();

        new Thread(() -> {
            try {
                String apkUrl = "https://github.com/parasyadav75963-eng/Dhvnitts/releases/download/latest/DhwaniTTS-latest.apk";
                HttpURLConnection conn = (HttpURLConnection) new URL(apkUrl).openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.connect();

                int totalLength = conn.getContentLength();
                InputStream in = conn.getInputStream();
                File outFile = new File(ctx.getExternalFilesDir(null), "DhwaniTTS-update.apk");
                if (outFile.exists()) outFile.delete();
                FileOutputStream out = new FileOutputStream(outFile);

                byte[] buf = new byte[8192];
                int count;
                long downloaded = 0;
                int lastPercent = -1;

                while ((count = in.read(buf)) != -1) {
                    downloaded += count;
                    out.write(buf, 0, count);
                    if (totalLength > 0) {
                        int percent = (int) ((downloaded * 100) / totalLength);
                        if (percent != lastPercent && percent % 10 == 0) {
                            lastPercent = percent;
                            final int p = percent;
                            mainHandler.post(() -> progressDialog.setMessage("Downloading update: " + p + "%"));
                        }
                    }
                }
                out.flush();
                out.close();
                in.close();

                ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE)
                        .edit().putString("installed_commit_sha", targetSha).apply();

                mainHandler.post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(ctx, "Download complete! Opening installer...", Toast.LENGTH_LONG).show();
                    installApkFile(ctx, outFile);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(ctx, "Direct download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static void installApkFile(Context ctx, File apkFile) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!ctx.getPackageManager().canRequestPackageInstalls()) {
                    Toast.makeText(ctx, "Please allow 'Install unknown apps' for DhwaniTTS first.", Toast.LENGTH_LONG).show();
                    Intent permIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + ctx.getPackageName()));
                    permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(permIntent);
                    return;
                }
            }
            Uri apkUri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", apkFile);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(install);
        } catch (Exception e) {
            Toast.makeText(ctx, "Installer error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
