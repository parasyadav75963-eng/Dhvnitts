package com.itfb.dhwanitts;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class PiperEngine {
    private static final String TAG = "DhwaniTTS_PiperEngine";
    private static boolean isLibraryLoaded = false;
    private static String espeakDataPath = "";

    static {
        try {
            System.loadLibrary("onnxruntime");
            System.loadLibrary("piper_wrapper");
            isLibraryLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native libraries: " + e.getMessage());
        }
    }

    public PiperEngine() {}

    public PiperEngine(Context context) {
        if (context != null && espeakDataPath.isEmpty()) {
            File dataDir = new File(context.getFilesDir(), "espeak-ng-data");
            if (!new File(dataDir, "phontab").exists()) {
                copyAssetFolder(context.getAssets(), "espeak-ng-data", dataDir.getAbsolutePath());
            }
            espeakDataPath = dataDir.getAbsolutePath();
        }
    }

    public boolean initialize(String modelPath, String configPath) {
        if (!isLibraryLoaded) return false;
        synchronized (PiperEngine.class) {
            return nativeInit(modelPath, configPath, espeakDataPath);
        }
    }

    public boolean init(String modelPath, String configPath) {
        return initialize(modelPath, configPath);
    }

    public boolean initEngine(String modelPath, String configPath, String dataPath) {
        if (!isLibraryLoaded) return false;
        synchronized (PiperEngine.class) {
            return nativeInit(modelPath, configPath, dataPath != null ? dataPath : espeakDataPath);
        }
    }

    public byte[] synthesize(String text, float speed) {
        if (!isLibraryLoaded || text == null || text.trim().isEmpty()) return new byte[0];
        synchronized (PiperEngine.class) {
            return nativeSynthesize(text, speed);
        }
    }

    public byte[] synthesizeText(String text, float speed) {
        return synthesize(text, speed);
    }

    public void destroy() {
        // Session को जीवित रखा गया है ताकि TalkBack और Preview में कभी टकराव न हो
    }

    public void destroyEngine() {
        // No-op to prevent killing active TalkBack session during preview
    }

    public static synchronized native boolean nativeInit(String modelPath, String configPath, String dataPath);
    public static synchronized native byte[] nativeSynthesize(String text, float speed);
    public static synchronized native void nativeDestroy();

    private void copyAssetFolder(AssetManager assetManager, String fromAssetPath, String toPath) {
        try {
            String[] files = assetManager.list(fromAssetPath);
            if (files == null) return;
            new File(toPath).mkdirs();
            for (String file : files) {
                String[] subFiles = assetManager.list(fromAssetPath + "/" + file);
                if (subFiles != null && subFiles.length > 0) {
                    copyAssetFolder(assetManager, fromAssetPath + "/" + file, toPath + "/" + file);
                } else {
                    copyAsset(assetManager, fromAssetPath + "/" + file, toPath + "/" + file);
                }
            }
        } catch (Exception ignored) {}
    }

    private void copyAsset(AssetManager assetManager, String fromAssetPath, String toPath) {
        try (InputStream in = assetManager.open(fromAssetPath);
             OutputStream out = new FileOutputStream(toPath)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } catch (IOException ignored) {}
    }
}
