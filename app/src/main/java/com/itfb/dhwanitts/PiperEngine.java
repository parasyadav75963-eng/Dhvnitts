package com.itfb.dhwanitts;

public class PiperEngine {
    
    // जब ऐप खुलेगा, तो यह C++ लाइब्रेरी को मेमोरी में लोड कर लेगा
    static {
        System.loadLibrary("piper_wrapper");
    }

    // ये वो फंक्शन हैं जिनका असली कोड Java में नहीं, बल्कि C++ में लिखा है (native)
    public native boolean initEngine(String modelPath, String configPath);
    public native byte[] synthesizeText(String text, float speed);
    public native void destroyEngine();
}
