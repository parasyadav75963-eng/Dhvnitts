package com.itfb.dhwanitts;

import android.os.Handler;
import android.os.Looper;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VoiceDownloader {

    public interface DownloadListener {
        void onProgress(int progress);
        void onComplete(String filePath);
        void onError(String error);
    }

    // बैकग्राउंड में काम करने के लिए Executor सर्विस
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void downloadVoice(String fileUrl, String destinationPath, DownloadListener listener) {
        executor.execute(() -> {
            try {
                URL url = new URL(fileUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    mainHandler.post(() -> listener.onError("Server returned HTTP " + connection.getResponseCode()));
                    return;
                }

                int fileLength = connection.getContentLength();
                InputStream input = connection.getInputStream();
                FileOutputStream output = new FileOutputStream(destinationPath);

                byte[] data = new byte[4096];
                long total = 0;
                int count;
                
                // डाउनलोडिंग लूप
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        // UI को अपडेट करने के लिए प्रोग्रेस भेजना
                        mainHandler.post(() -> listener.onProgress(progress));
                    }
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();

                // डाउनलोड पूरा होने पर UI को बताना
                mainHandler.post(() -> listener.onComplete(destinationPath));

            } catch (Exception e) {
                mainHandler.post(() -> listener.onError(e.getMessage()));
            }
        });
    }
}
