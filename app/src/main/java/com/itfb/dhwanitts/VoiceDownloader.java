package com.itfb.dhwanitts;

import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VoiceDownloader {
    public interface DownloadListener {
        void onProgress(int percentage);
        void onSuccess();
        void onError(String message);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void downloadFile(String fileUrl, String destinationPath, DownloadListener listener) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String currentUrl = fileUrl;
                for (int i = 0; i < 6; i++) {
                    URL url = new URL(currentUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setInstanceFollowRedirects(true);
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                    connection.setRequestProperty("User-Agent", "DhwaniTTS-Updater");
                    connection.connect();
                    int code = connection.getResponseCode();
                    if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308) {
                        String loc = connection.getHeaderField("Location");
                        connection.disconnect();
                        if (loc != null && !loc.isEmpty()) {
                            currentUrl = loc;
                            continue;
                        }
                    }
                    break;
                }

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    final String errorMsg = "Server returned HTTP " + responseCode;
                    mainHandler.post(() -> listener.onError(errorMsg));
                    return;
                }

                long fileLength = connection.getContentLengthLong();
                if (fileLength <= 0) {
                    fileLength = 25L * 1024L * 1024L; // 25MB fallback for smooth progress
                }

                File destFile = new File(destinationPath);
                if (destFile.getParentFile() != null && !destFile.getParentFile().exists()) {
                    destFile.getParentFile().mkdirs();
                }

                InputStream input = connection.getInputStream();
                OutputStream output = new FileOutputStream(destFile);

                byte[] data = new byte[8192];
                long total = 0;
                int count;
                int lastProg = -1;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    output.write(data, 0, count);
                    int progress = (int) ((total * 100L) / fileLength);
                    if (progress > 99) progress = 99;
                    if (progress != lastProg) {
                        lastProg = progress;
                        final int p = progress;
                        mainHandler.post(() -> listener.onProgress(p));
                    }
                }
                output.flush();
                output.close();
                input.close();

                mainHandler.post(() -> {
                    listener.onProgress(100);
                    listener.onSuccess();
                });
            } catch (Exception e) {
                final String exMsg = e.getMessage() != null ? e.getMessage() : "Unknown network error";
                mainHandler.post(() -> listener.onError("Error: " + exMsg));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
