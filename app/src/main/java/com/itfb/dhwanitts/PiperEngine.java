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
    private String myModelPath = null;
    private String myConfigPath = null;
    private String myDataPath = null;
    private static final String TAG = "PiperEngine";
    private static boolean isLibraryLoaded = false;
    private Context appContext;

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
        if (context != null) {
            this.appContext = context.getApplicationContext();
            setupEspeakData(this.appContext);
        }
    }

    public static boolean isLoaded() {
        return isLibraryLoaded;
    }

    public static String setupEspeakData(Context context) {
        if (context == null) return "";
        File filesDir = context.getFilesDir();
        File espeakDir = new File(filesDir, "espeak-ng-data");
        File phontab = new File(espeakDir, "phontab");
        File hiDict = new File(espeakDir, "hi_dict");
        File enDict = new File(espeakDir, "en_dict");
        File langDir = new File(espeakDir, "lang");

        if (!phontab.exists() || !hiDict.exists() || !enDict.exists() || !langDir.exists()) {
            copyAssetFolder(context.getAssets(), "espeak-ng-data", espeakDir.getAbsolutePath());
        }
        return filesDir.getAbsolutePath();
    }

    private static boolean copyAssetFolder(AssetManager am, String src, String dst) {
        try {
            String[] files = am.list(src);
            if (files == null) return false;
            new File(dst).mkdirs();
            boolean res = true;
            for (String f : files) {
                String sp = src + "/" + f, dp = dst + "/" + f;
                String[] sub = am.list(sp);
                if (sub != null && sub.length > 0) res &= copyAssetFolder(am, sp, dp);
                else res &= copyAsset(am, sp, dp);
            }
            return res;
        } catch (Exception e) { return false; }
    }

    private static boolean copyAsset(AssetManager am, String src, String dst) {
        try (InputStream in = am.open(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192]; int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            out.flush(); return true;
        } catch (IOException e) { return false; }
    }

    // Support both static and instance calls used across TtsService, PiperAudioPlayer, and MainActivity
    public static boolean init(Context context, String mPath, String cPath) {
        if (!isLibraryLoaded) return false;
        synchronized (PiperEngine.class) {
            
            return nativeInit(mPath, cPath, setupEspeakData(context));
        }
    }

    public boolean initEngine(String mPath, String cPath) {
        if (!isLibraryLoaded) return false;
        String dPath = (appContext != null) ? setupEspeakData(appContext) : "";
        return nativeInit(mPath, cPath, dPath);
    }

    public boolean initEngine(Context context, String mPath, String cPath) {
        if (!isLibraryLoaded) return false;
        return nativeInit(mPath, cPath, setupEspeakData(context));
    }

    public boolean initEngine(String mPath, String cPath, String dPath) {
        if (!isLibraryLoaded) return false;
        synchronized (PiperEngine.class) {
            this.myModelPath = mPath; this.myConfigPath = cPath; this.myDataPath = dPath;
            applyModeForPath(mPath);
            return nativeInit(mPath, cPath, dPath);
        }
    }

    public static byte[] synthesize(String text, float speed) {
        if (!isLibraryLoaded || text == null || text.trim().isEmpty()) return new byte[0];
        synchronized (PiperEngine.class) {
            
            return nativeSynthesize(text, speed);
        }
    }

    public byte[] synthesizeText(String text, float speed) {
        if (!isLibraryLoaded || text == null || text.trim().isEmpty()) return new byte[0];
        synchronized (PiperEngine.class) {
            if (this.myModelPath != null && this.myConfigPath != null && this.myDataPath != null) {
                applyModeForPath(this.myModelPath);
                nativeInit(this.myModelPath, this.myConfigPath, this.myDataPath);
            }
            return nativeSynthesize(text, speed);
        }
    }

    public byte[] synthesizeText(String text) {
        return synthesizeText(text, 1.0f);
    }

    public void destroyEngine() {
        if (isLibraryLoaded) nativeDestroy();
    }

    public static void destroy() {
        if (isLibraryLoaded) nativeDestroy();
    }

    public static native boolean nativeInit(String mPath, String cPath, String dPath);
    public static native byte[] nativeSynthesize(String text, float speed);
    public static native void nativeDestroy();

    public static int detectModeFromPath(String path) {
        if (path == null) return 0;
        if (path.contains("__turbo")) return 3;
        if (path.contains("__crisp")) return 2;
        if (path.contains("__sonic")) return 1;
        return 0;
    }

    public static void applyModeForPath(String path) {
        try { nativeSetSynthMode(detectModeFromPath(path)); } catch (Throwable ignored) {}
    }

    private static native void nativeSetSynthMode(int mode);
}
