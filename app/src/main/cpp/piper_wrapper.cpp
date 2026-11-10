#include <jni.h>
#include <string>
#include <vector>
#include <map>
#include <fstream>
#include <sstream>
#include <mutex>
#include <cctype>
#include <android/log.h>
#include <onnxruntime_cxx_api.h>

#define LOG_TAG "DhwaniTTS_PiperJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

Ort::Env* ortEnv = nullptr;
Ort::Session* ortSession = nullptr;
std::string currentModelPath = "";
std::map<std::string, int64_t> phonemeIdMap;
std::mutex engineMutex;

std::vector<uint32_t> toCps(const std::string& s) {
    std::vector<uint32_t> cps;
    for (size_t i = 0; i < s.size();) {
        uint32_t cp = 0; unsigned char c = s[i];
        if ((c & 0x80) == 0) { cp = c; i += 1; }
        else if ((c & 0xE0) == 0xC0 && i + 1 < s.size()) { cp = ((c & 0x1F) << 6) | (s[i+1] & 0x3F); i += 2; }
        else if ((c & 0xF0) == 0xE0 && i + 2 < s.size()) { cp = ((c & 0x0F) << 12) | ((s[i+1] & 0x3F) << 6) | (s[i+2] & 0x3F); i += 3; }
        else if ((c & 0xF8) == 0xF0 && i + 3 < s.size()) { cp = ((c & 0x07) << 18) | ((s[i+1] & 0x3F) << 12) | ((s[i+2] & 0x3F) << 6) | (s[i+3] & 0x3F); i += 4; }
        else { i += 1; continue; }
        cps.push_back(cp);
    }
    return cps;
}

std::string fromCp(uint32_t cp) {
    std::string o;
    if (cp <= 0x7F) o += (char)cp;
    else if (cp <= 0x7FF) { o += (char)(0xC0 | ((cp >> 6) & 0x1F)); o += (char)(0x80 | (cp & 0x3F)); }
    else if (cp <= 0xFFFF) { o += (char)(0xE0 | ((cp >> 12) & 0x0F)); o += (char)(0x80 | ((cp >> 6) & 0x3F)); o += (char)(0x80 | (cp & 0x3F)); }
    else { o += (char)(0xF0 | ((cp >> 18) & 0x07)); o += (char)(0x80 | ((cp >> 12) & 0x3F)); o += (char)(0x80 | ((cp >> 6) & 0x3F)); o += (char)(0x80 | (cp & 0x3F)); }
    return o;
}

std::string unescapeJson(const std::string& r) {
    std::string o;
    for (size_t i = 0; i < r.size(); ++i) {
        if (r[i] == '\\' && i + 1 < r.size()) {
            char n = r[i + 1];
            if (n == '"' || n == '\\' || n == '/') { o += n; i++; }
            else if (n == 'u' && i + 5 < r.size()) {
                try { o += fromCp(std::stoul(r.substr(i + 2, 4), nullptr, 16)); i += 5; }
                catch (...) { o += r[i]; }
            } else o += r[i];
        } else o += r[i];
    }
    return o;
}

void loadPhonemeIdMap(const char* configPath) {
    phonemeIdMap.clear();
    std::ifstream f(configPath);
    if (!f.is_open()) return;
    std::stringstream b; b << f.rdbuf();
    std::string j = b.str();
    size_t mp = j.find("\"phoneme_id_map\"");
    if (mp == std::string::npos) return;
    size_t sb = j.find("{", mp);
    if (sb == std::string::npos) return;
    int bc = 1; size_t p = sb + 1;
    while (p < j.size() && bc > 0) {
        if (j[p] == '"') {
            size_t eq = p + 1;
            while (eq < j.size() && !(j[eq] == '"' && j[eq - 1] != '\\')) eq++;
            std::string key = unescapeJson(j.substr(p + 1, eq - p - 1));
            size_t bs = j.find("[", eq), be = j.find("]", bs);
            if (bs != std::string::npos && be != std::string::npos) {
                try { phonemeIdMap[key] = std::stoll(j.substr(bs + 1, be - bs - 1)); } catch (...) {}
                p = be + 1; continue;
            }
        } else if (j[p] == '{') bc++;
        else if (j[p] == '}') bc--;
        p++;
    }
}

bool isIndic(uint32_t cp) { return (cp >= 0x0900 && cp <= 0x0D7F); }

std::string textToIpa(const std::string& in) {
    static const char* kCons[37] = {
        "k","kʰ","ɡ","ɡʱ","ŋ","tʃ","tʃʰ","dʒ","dʒʱ","ɲ",
        "ʈ","ʈʰ","ɖ","ɖʱ","ɳ","t̪","t̪ʰ","d̪","d̪ʱ","n",
        "n","p","pʰ","b","bʱ","m","j","ɾ","ɾ","l",
        "l","l","ʋ","ʃ","ʃ","s","ɦ"
    };
    std::vector<uint32_t> cps = toCps(in);
    std::string ipa = ""; size_t n = cps.size();
    for (size_t i = 0; i < n; ++i) {
        uint32_t cp = cps[i];
        if (isIndic(cp)) {
            uint32_t off = cp & 0x7F;
            if (off == 0x01 || off == 0x02) { ipa += "n"; continue; }
            if (off == 0x03) { ipa += "h"; continue; }
            if (off == 0x05) { ipa += "ə"; continue; }
            if (off == 0x06 || off == 0x3E) { ipa += "aː"; continue; }
            if (off == 0x07 || off == 0x3F) { ipa += "ɪ"; continue; }
            if (off == 0x08 || off == 0x40) { ipa += "iː"; continue; }
            if (off == 0x09 || off == 0x41) { ipa += "ʊ"; continue; }
            if (off == 0x0A || off == 0x42) { ipa += "uː"; continue; }
            if (off == 0x0B || off == 0x43) { ipa += "ɾɪ"; continue; }
            if ((off >= 0x0D && off <= 0x0F) || (off >= 0x45 && off <= 0x47)) { ipa += "eː"; continue; }
            if (off == 0x10 || off == 0x48) { ipa += "ɛː"; continue; }
            if ((off >= 0x11 && off <= 0x13) || (off >= 0x49 && off <= 0x4B)) { ipa += "oː"; continue; }
            if (off == 0x14 || off == 0x4C) { ipa += "ɔː"; continue; }
            if (off >= 0x15 && off <= 0x39) {
                std::string cStr = kCons[off - 0x15];
                if (i + 1 < n && isIndic(cps[i + 1]) && ((cps[i + 1] & 0x7F) == 0x3C)) {
                    if (off == 0x1C) cStr = "z"; else if (off == 0x2B) cStr = "f"; else if (off == 0x21) cStr = "ɽ";
                    i++;
                }
                ipa += cStr;
                if (i + 1 < n && isIndic(cps[i + 1])) {
                    uint32_t no = cps[i + 1] & 0x7F;
                    if (no == 0x4D) i++;
                    else if (no < 0x3E || no > 0x4C) ipa += "ə";
                }
                continue;
            }
            continue;
        }
        if (cp < 128) {
            char c = std::tolower((unsigned char)cp);
            char nx = (i + 1 < n && cps[i + 1] < 128) ? std::tolower((unsigned char)cps[i + 1]) : 0;
            if (c == 's' && nx == 'h') { ipa += "ʃ"; i++; continue; }
            if (c == 'c' && nx == 'h') { ipa += "tʃ"; i++; continue; }
            if (c == 't' && nx == 'h') { ipa += "ð"; i++; continue; }
            if (c == 'p' && nx == 'h') { ipa += "f"; i++; continue; }
            if (c == 'e' && nx == 'e') { ipa += "iː"; i++; continue; }
            if (c == 'o' && nx == 'o') { ipa += "uː"; i++; continue; }
            if (c == 'a') ipa += "ə"; else if (c == 'e') ipa += "ɛ"; else if (c == 'i') ipa += "ɪ";
            else if (c == 'o') ipa += "oː"; else if (c == 'u') ipa += "ʊ"; else if (c == 'r') ipa += "ɹ";
            else if (c == 'c') ipa += (nx == 'e' || nx == 'i') ? "s" : "k";
            else if (c == 'j') ipa += "dʒ"; else if (c == 'y') ipa += "j"; else if (c == 'g') ipa += "ɡ";
            else if ((c >= 'a' && c <= 'z') || c == ' ') ipa += c;
            continue;
        }
        ipa += fromCp(cp);
    }
    return ipa;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_itfb_dhwanitts_PiperEngine_nativeInit(JNIEnv* env, jclass, jstring mPath, jstring cPath, jstring) {
    std::lock_guard<std::mutex> lk(engineMutex);
    const char* m = env->GetStringUTFChars(mPath, nullptr);
    const char* c = env->GetStringUTFChars(cPath, nullptr);
    if (ortSession && currentModelPath == m) {
        env->ReleaseStringUTFChars(mPath, m); env->ReleaseStringUTFChars(cPath, c);
        return JNI_TRUE;
    }
    try {
        if (!ortEnv) ortEnv = new Ort::Env(ORT_LOGGING_LEVEL_WARNING, "DhwaniTTS");
        delete ortSession; ortSession = nullptr;
        Ort::SessionOptions opts; opts.SetIntraOpNumThreads(2);
        opts.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_BASIC);
        ortSession = new Ort::Session(*ortEnv, m, opts);
        currentModelPath = m;
        loadPhonemeIdMap(c);
    } catch (...) {
        env->ReleaseStringUTFChars(mPath, m); env->ReleaseStringUTFChars(cPath, c);
        return JNI_FALSE;
    }
    env->ReleaseStringUTFChars(mPath, m); env->ReleaseStringUTFChars(cPath, c);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_itfb_dhwanitts_PiperEngine_nativeSynthesize(JNIEnv* env, jclass, jstring text, jfloat speed) {
    std::lock_guard<std::mutex> lk(engineMutex);
    const char* nt = env->GetStringUTFChars(text, nullptr);
    std::string inStr(nt ? nt : "");
    env->ReleaseStringUTFChars(text, nt);
    if (!ortSession || inStr.empty()) return env->NewByteArray(0);
    try {
        std::vector<uint32_t> cps = toCps(textToIpa(inStr));
        int64_t bos = phonemeIdMap.count("^") ? phonemeIdMap["^"] : 1;
        int64_t eos = phonemeIdMap.count("$") ? phonemeIdMap["$"] : 2;
        int64_t pad = phonemeIdMap.count("_") ? phonemeIdMap["_"] : 0;
        int64_t spc = phonemeIdMap.count(" ") ? phonemeIdMap[" "] : 3;
        std::vector<int64_t> ids = {bos, pad};
        for (uint32_t cp : cps) {
            std::string s = fromCp(cp);
            if (phonemeIdMap.count(s)) { ids.push_back(phonemeIdMap[s]); ids.push_back(pad); }
            else if (s == " ") { ids.push_back(spc); ids.push_back(pad); }
            else if (s == "ɡ" && phonemeIdMap.count("g")) { ids.push_back(phonemeIdMap["g"]); ids.push_back(pad); }
            else if ((s == "ɾ" || s == "ɹ") && phonemeIdMap.count("r")) { ids.push_back(phonemeIdMap["r"]); ids.push_back(pad); }
            else if (s == "ʋ" && phonemeIdMap.count("v")) { ids.push_back(phonemeIdMap["v"]); ids.push_back(pad); }
            else if (s == "ɦ" && phonemeIdMap.count("h")) { ids.push_back(phonemeIdMap["h"]); ids.push_back(pad); }
        }
        ids.push_back(eos);
        auto mem = Ort::MemoryInfo::CreateCpu(OrtDeviceAllocator, OrtMemTypeCPU);
        int64_t inShape[] = {1, (int64_t)ids.size()}, lenData[] = {(int64_t)ids.size()}, lenShape[] = {1}, scShape[] = {3};
        float scData[] = {0.667f, 1.0f / (speed > 0.1f ? speed : 1.0f), 0.8f};
        std::vector<const char*> inNames = {"input", "input_lengths", "scales"};
        std::vector<Ort::Value> inTensors;
        inTensors.push_back(Ort::Value::CreateTensor<int64_t>(mem, ids.data(), ids.size(), inShape, 2));
        inTensors.push_back(Ort::Value::CreateTensor<int64_t>(mem, lenData, 1, lenShape, 1));
        inTensors.push_back(Ort::Value::CreateTensor<float>(mem, scData, 3, scShape, 1));
        int64_t sid[] = {0}, sidShape[] = {1};
        if (ortSession->GetInputCount() >= 4) {
            inNames.push_back("sid");
            inTensors.push_back(Ort::Value::CreateTensor<int64_t>(mem, sid, 1, sidShape, 1));
        }
        const char* outNames[] = {"output"};
        auto out = ortSession->Run(Ort::RunOptions{nullptr}, inNames.data(), inTensors.data(), inTensors.size(), outNames, 1);
        float* fa = out.front().GetTensorMutableData<float>();
        size_t cnt = out.front().GetTensorTypeAndShapeInfo().GetElementCount();
        jbyteArray res = env->NewByteArray(cnt * 2);
        jbyte* buf = env->GetByteArrayElements(res, nullptr);
        int16_t* pcm = reinterpret_cast<int16_t*>(buf);
        for (size_t i = 0; i < cnt; i++) {
            float v = fa[i] > 1.0f ? 1.0f : (fa[i] < -1.0f ? -1.0f : fa[i]);
            pcm[i] = (int16_t)(v * 32767.0f);
        }
        env->ReleaseByteArrayElements(res, buf, 0);
        return res;
    } catch (...) { return env->NewByteArray(0); }
}

extern "C" JNIEXPORT void JNICALL
Java_com_itfb_dhwanitts_PiperEngine_nativeDestroy(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lk(engineMutex);
    delete ortSession; ortSession = nullptr; currentModelPath = "";
}
