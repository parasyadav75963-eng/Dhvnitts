package com.itfb.dhwanitts;

public class AnnouncerLocale {

    private static String getLang(String voiceKey) {
        if (voiceKey == null || voiceKey.isEmpty()) return "en";
        String k = voiceKey.toLowerCase();
        if (k.startsWith("hi") || k.contains("_hi") || k.contains("-hi") || k.contains("hindi") || k.contains("pratham") || k.contains("priyamvada") || k.contains("rohan")) return "hi";
        if (k.startsWith("bn") || k.contains("_bn") || k.contains("-bn") || k.contains("bengali") || k.contains("bangla")) return "bn";
        if (k.startsWith("te") || k.contains("_te") || k.contains("-te") || k.contains("telugu")) return "te";
        if (k.startsWith("mr") || k.contains("_mr") || k.contains("-mr") || k.contains("marathi")) return "mr";
        if (k.startsWith("ta") || k.contains("_ta") || k.contains("-ta") || k.contains("tamil")) return "ta";
        if (k.startsWith("gu") || k.contains("_gu") || k.contains("-gu") || k.contains("gujarati")) return "gu";
        if (k.startsWith("kn") || k.contains("_kn") || k.contains("-kn") || k.contains("kannada")) return "kn";
        if (k.startsWith("ml") || k.contains("_ml") || k.contains("-ml") || k.contains("malayalam") || k.contains("meera") || k.contains("arjun")) return "ml";
        if (k.startsWith("pa") || k.contains("_pa") || k.contains("-pa") || k.contains("punjabi")) return "pa";
        if (k.startsWith("or") || k.contains("_or") || k.contains("-or") || k.contains("odia") || k.contains("oriya")) return "or";
        if (k.startsWith("ur") || k.contains("_ur") || k.contains("-ur") || k.contains("urdu")) return "ur";
        return "en";
    }

    public static String formatTime(String voiceKey, int hour24, int min) {
        String lang = getLang(voiceKey);
        int h12 = hour24 % 12;
        if (h12 == 0) h12 = 12;
        boolean pm = hour24 >= 12;
        String minStr = (min == 0) ? "" : String.valueOf(min);

        switch (lang) {
            case "hi": {
                String p = (hour24 < 5) ? "रात के " : (hour24 < 12) ? "सुबह के " : (hour24 < 17) ? "दोपहर के " : (hour24 < 20) ? "शाम के " : "रात के ";
                return (min == 0) ? ("अभी " + p + h12 + " बजे हैं।") : ("अभी " + p + h12 + " बजकर " + min + " मिनट हुए हैं।");
            }
            case "bn":
                return (min == 0) ? ("এখন " + h12 + "টা বাজে।") : ("এখন " + h12 + "টা বেজে " + min + " মিনিট।");
            case "te":
                return (min == 0) ? ("ఇప్పుడు సమయం " + h12 + " గంటలు.") : ("ఇప్పుడు సమయం " + h12 + " గంటల " + min + " నిమిషాలు.");
            case "mr":
                return (min == 0) ? ("आता " + h12 + " वाजले आहेत.") : ("आता " + h12 + " वाजून " + min + " मिनिटे झाली आहेत.");
            case "ta":
                return (min == 0) ? ("இப்போது நேரம் " + h12 + " மணி.") : ("இப்போது நேரம் " + h12 + " மணி " + min + " நிமிடங்கள்.");
            case "gu":
                return (min == 0) ? ("અત્યારે " + h12 + " વાગ્યા છે.") : ("અત્યારે " + h12 + " વાગીને " + min + " મિનિટ થઈ છે.");
            case "kn":
                return (min == 0) ? ("ಈಗ ಸಮಯ " + h12 + " ಗಂಟೆ.") : ("ಈಗ ಸಮಯ " + h12 + " ಗಂಟೆ " + min + " ನಿಮಿಷ.");
            case "ml":
                return (min == 0) ? ("ഇപ്പോൾ സമയം " + h12 + " മണി.") : ("ഇപ്പോൾ സമയം " + h12 + " മണി " + min + " മിനിറ്റ്.");
            case "pa":
                return (min == 0) ? ("ਹੁਣ " + h12 + " ਵਜੇ ਹਨ।") : ("ਹੁਣ " + h12 + " ਵੱਜ ਕੇ " + min + " ਮਿੰਟ ਹੋਏ ਹਨ।");
            case "or":
                return (min == 0) ? ("ବର୍ତ୍ତମାନ ସମୟ " + h12 + " ଟା।") : ("ବର୍ତ୍ତମାନ ସମୟ " + h12 + " ଟା " + min + " ମିନିଟ୍।");
            case "ur":
                return (min == 0) ? ("ابھی " + h12 + " بجے ہیں۔") : ("ابھی " + h12 + " بج کر " + min + " منٹ ہوئے ہیں۔");
            default: {
                String ampm = pm ? "PM" : "AM";
                return (min == 0) ? ("The time is " + h12 + " " + ampm) : ("The time is " + h12 + " " + (min < 10 ? "oh " + minStr : minStr) + " " + ampm);
            }
        }
    }

    public static String formatBattery(String voiceKey, int pct, boolean charging) {
        switch (getLang(voiceKey)) {
            case "hi": return "बैटरी " + pct + " प्रतिशत है" + (charging ? ", चार्ज हो रही है।" : "।");
            case "bn": return "ব্যাটারি " + pct + " শতাংশ" + (charging ? ", চার্জ হচ্ছে।" : "।");
            case "te": return "బ్యాటరీ " + pct + " శాతం ఉంది" + (charging ? ", ఛార్జ్ అవుతోంది." : ".");
            case "mr": return "बॅटरी " + pct + " टक्के आहे" + (charging ? ", चार्ज होत आहे." : ".");
            case "ta": return "பேட்டரி " + pct + " சதவீதம்" + (charging ? ", சார்ஜ் ஆகிறது." : ".");
            case "gu": return "બેટરી " + pct + " ટકા છે" + (charging ? ", ચાર્જ થઈ રહી છે." : ".");
            case "kn": return "ಬ್ಯಾಟರಿ " + pct + " ಶೇಕಡಾ ಇದೆ" + (charging ? ", ಚಾರ್ಜ್ ಆಗುತ್ತಿದೆ." : ".");
            case "ml": return "ബാറ്ററി " + pct + " ശതമാനം" + (charging ? ", ചാർജ് ചെയ്യുന്നു." : ".");
            case "pa": return "ਬੈਟਰੀ " + pct + " ਪ੍ਰਤੀਸ਼ਤ ਹੈ" + (charging ? ", ਚਾਰਜ ਹੋ ਰਹੀ ਹੈ।" : "।");
            case "or": return "ବ୍ୟାଟେରୀ " + pct + " ପ୍ରତିଶତ ଅଛି" + (charging ? ", ଚାର୍ଜ ହେଉଛି।" : "।");
            case "ur": return "بیٹری " + pct + " فیصد ہے" + (charging ? "، چارج ہو رہی ہے۔" : "۔");
            default: return "Battery is at " + pct + " percent" + (charging ? ", charging." : ".");
        }
    }

    public static String formatBatteryFull(String voiceKey) {
        switch (getLang(voiceKey)) {
            case "hi": return "बैटरी पूरी तरह चार्ज हो गई है, 100 प्रतिशत।";
            case "bn": return "ব্যাটারি সম্পূর্ণ চার্জ হয়েছে, ১০০ শতাংশ।";
            case "te": return "బ్యాటరీ పూర్తిగా ఛార్జ్ అయింది, 100 శాతం.";
            case "mr": return "बॅटरी पूर्ण चार्ज झाली आहे, 100 टक्के.";
            case "ta": return "பேட்டரி முழுமையாக சார்ஜ் ஆகிவிட்டது, 100 சதவீதம்.";
            case "gu": return "બેટરી સંપૂર્ણ ચાર્જ થઈ ગઈ છે, 100 ટકા.";
            case "kn": return "ಬ್ಯಾಟರಿ ಸಂಪೂರ್ಣವಾಗಿ ಚಾರ್ಜ್ ಆಗಿದೆ, 100 ಶೇಕಡಾ.";
            case "ml": return "ബാറ്ററി പൂർണ്ണമായും ചാർജ് ആയി, 100 ശതമാനം.";
            case "pa": return "ਬੈਟਰੀ ਪੂਰੀ ਤਰ੍ਹਾਂ ਚਾਰਜ ਹੋ ਗਈ ਹੈ, 100 ਪ੍ਰਤੀਸ਼ਤ।";
            case "or": return "ବ୍ୟାଟେରୀ ସମ୍ପୂର୍ଣ୍ଣ ଚାର୍ଜ ହୋଇଯାଇଛି, 100 ପ୍ରତିଶତ।";
            case "ur": return "بیٹری مکمل چارج ہو گئی ہے، 100 فیصد۔";
            default: return "Battery is fully charged, 100 percent.";
        }
    }

    public static String formatBatteryLow(String voiceKey, int pct) {
        switch (getLang(voiceKey)) {
            case "hi": return "बैटरी कम है, केवल " + pct + " प्रतिशत बची है।";
            case "bn": return "ব্যাটারি কম, মাত্র " + pct + " শতাংশ বাকি আছে।";
            case "te": return "బ్యాటరీ తక్కువగా ఉంది, " + pct + " శాతం మిగిలి ఉంది.";
            case "mr": return "बॅटरी कमी आहे, फक्त " + pct + " टक्के शिल्लक आहे.";
            case "ta": return "பேட்டரி குறைவாக உள்ளது, " + pct + " சதவீதம் மட்டுமே உள்ளது.";
            case "gu": return "બેટરી ઓછી છે, માત્ર " + pct + " ટકા બાકી છે.";
            case "kn": return "ಬ್ಯಾಟರಿ ಕಡಿಮೆ ಇದೆ, " + pct + " ಶೇಕಡಾ ಮಾತ್ರ ಉಳಿದಿದೆ.";
            case "ml": return "ബാറ്ററി കുറവാണ്, " + pct + " ശതമാനം മാത്രം ബാക്കി.";
            case "pa": return "ਬੈਟਰੀ ਘੱਟ ਹੈ, ਸਿਰਫ਼ " + pct + " ਪ੍ਰਤੀਸ਼ਤ ਬਾਕੀ ਹੈ।";
            case "or": return "ବ୍ୟାଟେରୀ କମ୍ ଅଛି, କେବଳ " + pct + " ପ୍ରତିଶତ ବାକି ଅଛି।";
            case "ur": return "بیٹری کم ہے، صرف " + pct + " فیصد باقی ہے۔";
            default: return "Battery low, " + pct + " percent remaining.";
        }
    }

    public static String formatCharger(String voiceKey, boolean connected, int pct) {
        switch (getLang(voiceKey)) {
            case "hi": return (connected ? "चार्जर जुड़ गया है। " : "चार्जर हटा दिया गया है। ") + "बैटरी " + pct + " प्रतिशत है।";
            case "bn": return (connected ? "চার্জার সংযুক্ত হয়েছে। " : "চার্জার বিচ্ছিন্ন হয়েছে। ") + "ব্যাটারি " + pct + " শতাংশ।";
            case "te": return (connected ? "ఛార్జర్ కనెక్ట్ అయింది. " : "ఛార్జర్ డిస్‌కనెక్ట్ అయింది. ") + "బ్యాటరీ " + pct + " శాతం.";
            case "mr": return (connected ? "चार्जर जोडला आहे. " : "चार्जर काढला आहे. ") + "बॅटरी " + pct + " टक्के आहे.";
            case "ta": return (connected ? "சார்ஜர் இணைக்கப்பட்டது. " : "சார்ஜர் துண்டிக்கப்பட்டது. ") + "பேட்டரி " + pct + " சதவீதம்.";
            case "gu": return (connected ? "ચાર્જર જોડાયું છે. " : "ચાર્જર દૂર થયું છે. ") + "બેટરી " + pct + " ટકા છે.";
            case "kn": return (connected ? "ಚಾರ್ಜರ್ ಸಂಪರ್ಕಗೊಂಡಿದೆ. " : "ಚಾರ್ಜರ್ ತೆಗೆಯಲಾಗಿದೆ. ") + "ಬ್ಯಾಟರಿ " + pct + " ಶೇಕಡಾ.";
            case "ml": return (connected ? "ചാർജർ ബന്ധിപ്പിച്ചു. " : "ചാർജർ വിച്ഛേദിച്ചു. ") + "ബാറ്ററി " + pct + " ശതമാനം.";
            case "pa": return (connected ? "ਚਾਰਜਰ ਜੁੜ ਗਿਆ ਹੈ। " : "ਚਾਰਜਰ ਹਟਾ ਦਿੱਤਾ ਗਿਆ ਹੈ। ") + "ਬੈਟਰੀ " + pct + " ਪ੍ਰਤੀਸ਼ਤ ਹੈ।";
            case "or": return (connected ? "ଚାର୍ଜର୍ ସଂଯୁକ୍ତ ହୋଇଛି। " : "ଚାର୍ଜର୍ ବିଚ୍ଛିନ୍ନ ହୋଇଛି। ") + "ବ୍ୟାଟେରୀ " + pct + " ପ୍ରତିଶତ।";
            case "ur": return (connected ? "چارجر جڑ گیا ہے۔ " : "چارجر ہٹا دیا گیا ہے۔ ") + "بیٹری " + pct + " فیصد ہے۔";
            default: return (connected ? "Charger connected. " : "Charger disconnected. ") + "Battery is " + pct + " percent.";
        }
    }
}
