#include <jni.h>
#include <string>
#include <vector>
#include <map>
#include <fstream>
#include <sstream>
#include <mutex>
#include <sys/stat.h>
#include <android/log.h>
#include <onnxruntime_cxx_api.h>
#include <espeak-ng/speak_lib.h>

extern "C" const char* espeak_TextToPhonemesWithTerminator(const void** textptr, int textmode, int phonememode, int* terminator);

#define CLAUSE_PERIOD 0x00000028
#define CLAUSE_COMMA  0x00000010
#define CLAUSE_QUESTION 0x00000030
#define CLAUSE_EXCLAMATION 0x00000038
#define CLAUSE_COLON  0x00000018
#define CLAUSE_SEMICOLON 0x00000020
#define CLAUSE_TYPE_MASK 0x00000038

Ort::Env* ortEnv = nullptr;
Ort::Session* ortSession = nullptr;
std::string currentModelPath = "";
std::string currentVoiceLang = "en-us";
float modelNoiseScale = 0.667f, modelLengthScale = 1.0f, modelNoiseW = 0.8f;
std::map<std::string, int64_t> phonemeIdMap;
bool isEspeakInitialized = false;
std::mutex engineMutex;

bool dirExists(const std::string& p) {
    struct stat st; return (stat(p.c_str(), &st) == 0 && S_ISDIR(st.st_mode));
}

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

void parseModelConfig(const char* configPath) {
    phonemeIdMap.clear();
    currentVoiceLang = "en-us";
    std::ifstream f(configPath);
    if (!f.is_open()) return;
    std::stringstream b; b << f.rdbuf();
    std::string j = b.str();

    size_t espPos = j.find("\"espeak\"");
    if (espPos != std::string::npos) {
        size_t vPos = j.find("\"voice\"", espPos);
        if (vPos != std::string::npos) {
            size_t q1 = j.find("\"", vPos + 7);
            size_t q2 = j.find("\"", q1 + 1);
            if (q1 != std::string::npos && q2 != std::string::npos)
                currentVoiceLang = j.substr(q1 + 1, q2 - q1 - 1);
        }
    }

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

std::string cleanEspeakFlags(const std::string& rawIpa) {
    std::string out; bool inP = false;
    for (char c : rawIpa) {
        if (c == '(') { inP = true; continue; }
        if (c == ')') { inP = false; continue; }
        if (!inP) out += c;
    }
    return out;
}

std::string normalizeInputText(const std::string& s) {
    std::string out;
    for (uint32_t cp : toCps(s)) {
        if (cp == 0x0964 || cp == 0x0965) out += ". ";
        else out += fromCp(cp);
    }
    return out;
}

std::vector<std::string> phonemizeToClauses(const std::string& rawText) {
    std::vector<std::string> clauses;
    if (!isEspeakInitialized) return clauses;
    std::string normText = normalizeInputText(rawText);

    bool hasDeva = false;
    for (uint32_t cp : toCps(normText)) {
        if (cp >= 0x0900 && cp <= 0x097F) { hasDeva = true; break; }
    }
    if (hasDeva && currentVoiceLang.find("en") == 0) espeak_SetVoiceByName("hi");
    else if (espeak_SetVoiceByName(currentVoiceLang.c_str()) != EE_OK) espeak_SetVoiceByName("en-us");

    const char* inputPtr = normText.c_str();
    int mode = espeakCHARS_UTF8 | (1 << 1);
    while (inputPtr != nullptr && *inputPtr != '\0') {
        int term = 0;
        const char* ph = espeak_TextToPhonemesWithTerminator((const void**)&inputPtr, espeakCHARS_UTF8, mode, &term);
        std::string ipa = (ph && *ph) ? cleanEspeakFlags(std::string(ph)) : "";
        int pt = term & CLAUSE_TYPE_MASK;
        if (pt == CLAUSE_PERIOD) ipa += ".";
        else if (pt == CLAUSE_QUESTION) ipa += "?";
        else if (pt == CLAUSE_EXCLAMATION) ipa += "!";
        else if (pt == CLAUSE_COMMA) ipa += ", ";
        else if (pt == CLAUSE_COLON) ipa += ": ";
        else if (pt == CLAUSE_SEMICOLON) ipa += "; ";
        if (!ipa.empty()) clauses.push_back(ipa);
    }
    espeak_SetVoiceByName(currentVoiceLang.c_str());
    return clauses;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_itfb_dhwanitts_PiperEngine_nativeInit(JNIEnv* env, jclass, jstring mPath, jstring cPath, jstring dPath) {
    std::lock_guard<std::mutex> lk(engineMutex);
    const char* m = env->GetStringUTFChars(mPath, nullptr);
    const char* c = env->GetStringUTFChars(cPath, nullptr);
    const char* d = env->GetStringUTFChars(dPath, nullptr);

    if (!isEspeakInitialized && d != nullptr && strlen(d) > 0) {
        std::string root(d);
        if (!dirExists(root + "/espeak-ng-data") && dirExists(root + "/phontab")) {
            size_t sl = root.find_last_of('/');
            if (sl != std::string::npos) root = root.substr(0, sl);
        }
        if (espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS, 0, root.c_str(), 0) > 0) isEspeakInitialized = true;
    }

    if (ortSession && currentModelPath == m) {
        if (isEspeakInitialized) espeak_SetVoiceByName(currentVoiceLang.c_str());
        env->ReleaseStringUTFChars(mPath, m); env->ReleaseStringUTFChars(cPath, c); env->ReleaseStringUTFChars(dPath, d);
        return JNI_TRUE;
    }

    try {
        if (!ortEnv) ortEnv = new Ort::Env(ORT_LOGGING_LEVEL_WARNING, "DhwaniTTS");
        delete ortSession; ortSession = nullptr;
        Ort::SessionOptions opts; opts.SetIntraOpNumThreads(4);
        opts.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
        ortSession = new Ort::Session(*ortEnv, m, opts);
        currentModelPath = m;
        parseModelConfig(c);
        if (isEspeakInitialized) espeak_SetVoiceByName(currentVoiceLang.c_str());
    } catch (...) {
        env->ReleaseStringUTFChars(mPath, m); env->ReleaseStringUTFChars(cPath, c); env->ReleaseStringUTFChars(dPath, d);
        return JNI_FALSE;
    }
    env->ReleaseStringUTFChars(mPath, m); env->ReleaseStringUTFChars(cPath, c); env->ReleaseStringUTFChars(dPath, d);
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
        std::vector<std::string> clauses = phonemizeToClauses(inStr);
        if (clauses.empty()) return env->NewByteArray(0);
        int64_t bos = phonemeIdMap.count("^") ? phonemeIdMap["^"] : 1;
        int64_t eos = phonemeIdMap.count("$") ? phonemeIdMap["$"] : 2;
        int64_t pad = phonemeIdMap.count("_") ? phonemeIdMap["_"] : 0;

        std::vector<int64_t> ids = {bos, pad};
        for (size_t cIdx = 0; cIdx < clauses.size(); ++cIdx) {
            for (uint32_t cp : toCps(clauses[cIdx])) {
                std::string s = fromCp(cp);
                if (phonemeIdMap.count(s)) { ids.push_back(phonemeIdMap[s]); ids.push_back(pad); }
            }
            if (cIdx + 1 < clauses.size() && phonemeIdMap.count(" ")) {
                ids.push_back(phonemeIdMap[" "]); ids.push_back(pad);
            }
        }
        ids.push_back(eos);
        if (ids.size() <= 3) return env->NewByteArray(0);

        auto mem = Ort::MemoryInfo::CreateCpu(OrtDeviceAllocator, OrtMemTypeCPU);
        int64_t inShape[] = {1, (int64_t)ids.size()}, lenData[] = {(int64_t)ids.size()}, lenShape[] = {1}, scShape[] = {3};
        float scData[] = {modelNoiseScale, modelLengthScale / (speed > 0.1f ? speed : 1.0f), modelNoiseW};
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
Java_com_itfb_dhwanitts_PiperEngine_nativeDestroy(JNIEnv*, jclass) {}
