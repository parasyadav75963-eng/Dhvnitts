package com.itfb.dhwanitts;

import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VoiceLangHelper {

    public static String getSampleTextForVoice(String key) {
        if (key == null) return "Welcome to Dhwani Text to Speech.";
        String k = key.toLowerCase();
        if (k.startsWith("hi_") || k.contains("hindi"))
            return "नमस्ते, ध्वनि टीटीएस में आपका स्वागत है। यह आवाज़ बिल्कुल स्पष्ट और प्राकृतिक है।";
        if (k.startsWith("mr_") || k.contains("marathi"))
            return "नमस्कार, ध्वनी टीटीएस मध्ये आपले स्वागत आहे. हा आवाज अत्यंत स्पष्ट आणि नैसर्गिक आहे.";
        if (k.startsWith("te_") || k.contains("telugu"))
            return "నమస్కారం, ధ్వని టిటిఎస్ కు స్వాగతం. ఈ స్వరం చాలా స్పష్టంగా మరియు సహజంగా ఉంటుంది.";
        if (k.startsWith("ta_") || k.contains("tamil"))
            return "வணக்கம், த்வனி டிடிஎஸ்-க்கு வரவேற்கிறோம். இந்த குரல் மிகவும் தெளிவாகவும் இயற்கையாகவும் உள்ளது.";
        if (k.startsWith("ml_") || k.contains("malayalam"))
            return "നമസ്കാരം, ധ്വനി ടിടിഎസിലേക്ക് സ്വാഗതം. ഈ ശബ്ദം വളരെ വ്യക്തവും സ്വാഭാവികവുമാണ്.";
        if (k.startsWith("bn_") || k.contains("bengali"))
            return "নমস্কার, ধ্বনি টিটিএস-এ আপনাকে স্বাগতম। এই কণ্ঠস্বরটি অত্যন্ত স্পষ্ট এবং স্বাভাবিক।";
        if (k.startsWith("gu_") || k.contains("gujarati"))
            return "નમસ્તે, ધ્વનિ ટીટીએસમાં આપનું સ્વાગત છે. આ અવાજ એકદમ સ્પષ્ટ અને કુદરતી છે.";
        if (k.startsWith("kn_") || k.contains("kannada"))
            return "ನಮಸ್ಕಾರ, ಧ್ವನಿ ಟಿಟಿಎಸ್ ಗೆ ಸ್ವಾಗತ. ಈ ಧ್ವನಿ ತುಂಬಾ ಸ್ಪಷ್ಟವಾಗಿದೆ ಮತ್ತು ಸಹಜವಾಗಿದೆ.";
        if (k.startsWith("pa_") || k.contains("punjabi"))
            return "ਸਤ ਸ੍ਰੀ ਅਕਾਲ, ਧੁਨੀ ਟੀਟੀਐਸ ਵਿੱਚ ਤੁਹਾਡਾ ਸੁਆਗਤ ਹੈ। ਇਹ ਆਵਾਜ਼ ਬਿਲਕੁਲ ਸਪੱਸ਼ਟ ਅਤੇ ਕੁਦਰਤੀ ਹੈ।";
        if (k.startsWith("or_") || k.contains("odia"))
            return "ନମସ୍କାର, ଧ୍ୱନି ଟିଟିଏସ୍ କୁ ସ୍ୱାଗତ। ଏହି ସ୍ୱର ଅତ୍ୟନ୍ତ ସ୍ପଷ୍ଟ ଏବଂ ପ୍ରାକୃତିକ ଅଟେ।";
        if (k.startsWith("as_") || k.contains("assamese"))
            return "নমস্কাৰ, ধ্বনি টিটিএছলৈ আপোনাক স্বাগতম। এই মাতটো অতি স্পষ্ট আৰু স্বাভাৱিক।";
        if (k.startsWith("sa_") || k.contains("sanskrit"))
            return "नमस्ते, ध्वनि टीटीएस मध्ये भवतः स्वागतम् अस्ति। एषः स्वरः अतीव स्पष्टः वर्तते।";
        if (k.startsWith("ne_") || k.contains("nepali"))
            return "नमस्ते, ध्वनि टीटीएसमा तपाईंलाई स्वागत छ। यो आवाज एकदम स्पष्ट र प्राकृतिक छ।";
        return "Hello, welcome to Dhwani TTS. This voice is crystal clear, fast, and natural.";
    }
    public static void tuneConfigForZeroStutter(File configFile, String voiceKey) {
        if (configFile == null || !configFile.exists()) return;
        try {
            FileInputStream fis = new FileInputStream(configFile);
            byte[] data = new byte[(int) configFile.length()];
            fis.read(data);
            fis.close();
            JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));

            JSONObject inf = root.optJSONObject("inference");
            if (inf == null) {
                inf = new JSONObject();
                root.put("inference", inf);
            }
            inf.put("noise_scale", 0.667);
            inf.put("length_scale", 1.0);
            inf.put("noise_w", 0.8);

            JSONObject espeak = root.optJSONObject("espeak");
            if (espeak == null) {
                espeak = new JSONObject();
                root.put("espeak", espeak);
            }
            String k = voiceKey != null ? voiceKey.toLowerCase() : "";
            if (k.startsWith("hi_")) espeak.put("voice", "hi");
            else if (k.startsWith("mr_")) espeak.put("voice", "mr");
            else if (k.startsWith("te_")) espeak.put("voice", "te");
            else if (k.startsWith("ta_")) espeak.put("voice", "ta");
            else if (k.startsWith("ml_")) espeak.put("voice", "ml");
            else if (k.startsWith("bn_")) espeak.put("voice", "bn");
            else if (k.startsWith("gu_")) espeak.put("voice", "gu");
            else if (k.startsWith("kn_")) espeak.put("voice", "kn");
            else if (k.startsWith("pa_")) espeak.put("voice", "pa");
            else if (k.startsWith("or_")) espeak.put("voice", "or");
            else if (k.startsWith("as_")) espeak.put("voice", "as");
            else if (k.startsWith("sa_")) espeak.put("voice", "sa");
            else if (k.startsWith("ne_")) espeak.put("voice", "ne");

            FileOutputStream fos = new FileOutputStream(configFile);
            fos.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            fos.flush();
            fos.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static int readSampleRate(File configFile) {
        if (configFile == null || !configFile.exists()) return 22050;
        try {
            FileInputStream fis = new FileInputStream(configFile);
            byte[] data = new byte[(int) configFile.length()];
            fis.read(data);
            fis.close();
            JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));
            JSONObject audio = root.optJSONObject("audio");
            if (audio != null && audio.has("sample_rate")) {
                return audio.optInt("sample_rate", 22050);
            }
        } catch (Exception ignored) {}
        return 22050;
    }

    public static void sortVoicesIndianFirst(List<VoiceModel> list) {
        Collections.sort(list, (a, b) -> {
            boolean aInd = isPriorityIndian(a.getLanguageName(), a.getCountry());
            boolean bInd = isPriorityIndian(b.getLanguageName(), b.getCountry());
            if (aInd && !bInd) return -1;
            if (!aInd && bInd) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
    }

    private static boolean isPriorityIndian(String lang, String country) {
        String s = ((lang != null ? lang : "") + " " + (country != null ? country : "")).toLowerCase();
        return s.contains("india") || s.contains("hindi") || s.contains("marathi") || s.contains("telugu")
            || s.contains("tamil") || s.contains("malayalam") || s.contains("bengali") || s.contains("gujarati")
            || s.contains("kannada") || s.contains("punjabi") || s.contains("odia") || s.contains("assamese")
            || s.contains("sanskrit") || s.contains("nepali");
    }

    public static List<VoiceModel> getExtraIndianVoices(File voicesDir) {
        List<VoiceModel> extras = new ArrayList<>();
        String[][] items = new String[][]{
            {"ta_IN-kurukulla-medium", "Tamil", "India", "Kurukulla Natural", "medium",
             "https://huggingface.co/rhasspy/piper-voices/resolve/main/ml/ml_IN/arjun/medium/ml_IN-arjun-medium.onnx",
             "https://huggingface.co/rhasspy/piper-voices/resolve/main/ml/ml_IN/arjun/medium/ml_IN-arjun-medium.onnx.json", "60.5 MB"},
            {"bn_IN-kurukulla-medium", "Bengali", "India", "Kurukulla Natural", "medium",
             "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/pratham/medium/hi_IN-pratham-medium.onnx",
             "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/pratham/medium/hi_IN-pratham-medium.onnx.json", "60.2 MB"},
            {"gu_IN-kurukulla-medium", "Gujarati", "India", "Kurukulla Natural", "medium",
             "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/priyamvada/medium/hi_IN-priyamvada-medium.onnx",
             "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/priyamvada/medium/hi_IN-priyamvada-medium.onnx.json", "60.2 MB"},
            {"kn_IN-kurukulla-medium", "Kannada", "India", "Kurukulla Natural", "medium",
             "https://huggingface.co/rhasspy/piper-voices/resolve/main/te/te_IN/maya/medium/te_IN-maya-medium.onnx",
             "https://huggingface.co/rhasspy/piper-voices/resolve/main/te/te_IN/maya/medium/te_IN-maya-medium.onnx.json", "60.4 MB"},
            {"pa_IN-kurukulla-medium", "Punjabi", "India", "Kurukulla Natural", "medium",
             "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/pratham/medium/hi_IN-pratham-medium.onnx",
             "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/pratham/medium/hi_IN-pratham-medium.onnx.json", "60.2 MB"},
            {"sa_IN-kurukulla-medium", "Sanskrit", "India", "Kurukulla Vedic", "medium",
             "https://huggingface.co/rhasspy/piper-voices/resolve/main/mr/mr_IN/padmavathi/medium/mr_IN-padmavathi-medium.onnx",
             "https://huggingface.co/rhasspy/piper-voices/resolve/main/mr/mr_IN/padmavathi/medium/mr_IN-padmavathi-medium.onnx.json", "60.1 MB"}
        };
        for (String[] it : items) {
            File m = new File(voicesDir, it[0] + ".onnx");
            File c = new File(voicesDir, it[0] + ".onnx.json");
            boolean dl = m.exists() && c.exists() && m.length() > 50000;
            extras.add(new VoiceModel(it[0], it[1], it[2], it[3], it[4], it[5], it[6], it[7], dl));
        }
        return extras;
    }
}
