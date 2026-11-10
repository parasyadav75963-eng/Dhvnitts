package com.itfb.dhwanitts;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class PiperEngine {
    private static final String TAG = "PiperEngine";

    static {
        System.loadLibrary("piper_wrapper");
    }

    // C++ को अब model, config और espeak-ng-data तीनों का रास्ता भेजा जाएगा
    public native boolean initEngine(String modelPath, String configPath, String dataPath);
    public native byte[] synthesizeText(String text, float speed);
    public native void destroyEngine();

    // Assets से espeak-ng-data को Internal Storage में कॉपी करने का मेथड
    public static String setupEspeakData(Context context) {
        File espeakDataDir = new File(context.getFilesDir(), "espeak-ng-data");
        
        // अगर डिक्शनरी पहले से कॉपी नहीं है, तो उसे कॉपी करें
        if (!espeakDataDir.exists()) {
            Log.d(TAG, "espeak-ng-data कॉपी किया जा रहा है...");
            copyAssetFolder(context, "espeak-ng-data", espeakDataDir.getAbsolutePath());
            Log.d(TAG, "कॉपी पूरा हुआ!");
        }
        return espeakDataDir.getAbsolutePath();
    }

    private static void copyAssetFolder(Context context, String srcName, String dstName) {
        try {
            String[] fileList = context.getAssets().list(srcName);
            if (fileList == null) return;

            if (fileList.length == 0) {
                // यह एक फाइल है, इसे कॉपी करें
                try (InputStream in = context.getAssets().open(srcName);
                     OutputStream out = new FileOutputStream(dstName)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
            } else {
                // यह एक फोल्डर है, इसे बनाएं और अंदर की फाइलें कॉपी करें
                File dir = new File(dstName);
                if (!dir.exists()) dir.mkdirs();
                for (String filename : fileList) {
                    copyAssetFolder(context, srcName + "/" + filename, dstName + "/" + filename);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Asset कॉपी करने में एरर: " + srcName, e);
        }
    }
}
