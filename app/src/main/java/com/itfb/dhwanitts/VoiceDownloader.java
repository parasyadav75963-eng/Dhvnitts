package com.itfb.dhwanitts;

import android.os.Handler;
import android.os.Looper;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VoiceDownloader {
    public interface DownloadListener {
        void onProgress(int progress);
        void onSuccess();
        void onError(String error);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void downloadVoice(String fileUrl, String destinationPath, DownloadListener listener) {
        executor.execute(() -> {
            try {
                URL url = new URL(fileUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    // रिस्पॉन्स कोड को पहले एक स्ट्रिंग में सेव कर रहे हैं ताकि Lambda में एरर न आए
                    final String errorMsg = "Server returned HTTP " + responseCode;
                    mainHandler.post(() -> listener.onError(errorMsg));
                    return;
                }

                int fileLength = connection.getContentLength();
                InputStream input = connection.getInputStream();
                OutputStream output = new FileOutputStream(destinationPath);

                byte[] data = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        mainHandler.post(() -> listener.onProgress(progress));
                    }
                    output.write(data, 0, count);
                }
                output.flush();
                output.close();
                input.close();

                mainHandler.post(() -> listener.onSuccess());

            } catch (Exception e) {
                final String exMsg = e.getMessage() != null ? e.getMessage() : "Unknown network error";
                mainHandler.post(() -> listener.onError("Error: " + exMsg));
            }
        });
    }
}
