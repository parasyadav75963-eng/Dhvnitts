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
    private static final String TAG = "PiperEngine";
    private static boolean isLibraryLoaded = false;

    static {
        try {
            System.loadLibrary("onnxruntime");
            System.loadLibrary("piper_wrapper");
            isLibraryLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native libraries: " + e.getMessage());
        }
    }

    public static boolean isLoaded() { return isLibraryLoaded; }

    public static String setupEspeakData(Context context) {
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

    public static boolean init(Context context, String mPath, String cPath) {
        if (!isLibraryLoaded) return false;
        return nativeInit(mPath, cPath, setupEspeakData(context));
    }

    public static byte[] synthesize(String text, float speed) {
        if (!isLibraryLoaded || text == null || text.trim().isEmpty()) return new byte[0];
        return nativeSynthesize(text, speed);
    }

    public static native boolean nativeInit(String mPath, String cPath, String dPath);
    public static native byte[] nativeSynthesize(String text, float speed);
    public static native void nativeDestroy();
}
