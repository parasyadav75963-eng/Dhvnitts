#include <jni.h>
#include <string>
#include <vector>
#include <map>
#include <fstream>
#include <sstream>
#include <android/log.h>
#include <onnxruntime_cxx_api.h>

#define LOG_TAG "DhwaniTTS_PiperJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

Ort::Env* ortEnv = nullptr;
Ort::Session* ortSession = nullptr;
std::string currentModelPath = "";
std::map<std::string, int64_t> phonemeIdMap;

// .onnx.json config file se phoneme_id_map parse karne ka function
void loadPhonemeIdMap(const char* configPath) {
    phonemeIdMap.clear();
    std::ifstream file(configPath);
    if (!file.is_open()) {
        LOGE("Failed to open config file: %s", configPath);
        return;
    }
    std::stringstream buffer;
    buffer << file.rdbuf();
    std::string json = buffer.str();

    size_t mapPos = json.find("\"phoneme_id_map\"");
    if (mapPos == std::string::npos) return;

    size_t startBrace = json.find("{", mapPos);
    if (startBrace == std::string::npos) return;

    int braceCount = 1;
    size_t pos = startBrace + 1;
    while (pos < json.size() && braceCount > 0) {
        if (json[pos] == '"') {
            size_t endQuote = pos + 1;
            while (endQuote < json.size()) {
                if (json[endQuote] == '"' && json[endQuote - 1] != '\\') break;
                endQuote++;
            }
            std::string key = json.substr(pos + 1, endQuote - pos - 1);
            if (key == "\\\"") key = "\"";
            else if (key == "\\\\") key = "\\";

            size_t bracketStart = json.find("[", endQuote);
            size_t bracketEnd = json.find("]", bracketStart);
            if (bracketStart != std::string::npos && bracketEnd != std::string::npos) {
                std::string numStr = json.substr(bracketStart + 1, bracketEnd - bracketStart - 1);
                try {
                    int64_t id = std::stoll(numStr);
                    phonemeIdMap[key] = id;
                } catch (...) {}
                pos = bracketEnd + 1;
                continue;
            }
        } else if (json[pos] == '{') {
            braceCount++;
        } else if (json[pos] == '}') {
            braceCount--;
        }
        pos++;
    }
    LOGD("Loaded %zu phoneme mappings from config.", phonemeIdMap.size());
}

// UTF-8 string ko individual characters/symbols mein todne ka function
std::vector<std::string> splitUtf8(const std::string& str) {
    std::vector<std::string> chars;
    for (size_t i = 0; i < str.length();) {
        unsigned char c = str[i];
        size_t len = 1;
        if ((c & 0x80) == 0) len = 1;
        else if ((c & 0xE0) == 0xC0) len = 2;
        else if ((c & 0xF0) == 0xE0) len = 3;
        else if ((c & 0xF8) == 0xF0) len = 4;
        if (i + len > str.length()) len = 1;
        chars.push_back(str.substr(i, len));
        i += len;
    }
    return chars;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_itfb_dhwanitts_PiperEngine_initEngine(JNIEnv* env, jobject /* this */, jstring modelPath, jstring configPath, jstring dataPath) {
    const char* model = env->GetStringUTFChars(modelPath, nullptr);
    const char* config = env->GetStringUTFChars(configPath, nullptr);
    const char* data = env->GetStringUTFChars(dataPath, nullptr);

    // Agar wahi model pehle se loaded hai to dobara load na karein (TalkBack speed optimization)
    if (ortSession != nullptr && currentModelPath == std::string(model)) {
        env->ReleaseStringUTFChars(modelPath, model);
        env->ReleaseStringUTFChars(configPath, config);
        env->ReleaseStringUTFChars(dataPath, data);
        return JNI_TRUE;
    }

    LOGD("Initializing Real Piper ONNX Engine with Model: %s", model);

    try {
        if (!ortEnv) {
            ortEnv = new Ort::Env(ORT_LOGGING_LEVEL_WARNING, "DhwaniTTS");
        }
        if (ortSession) {
            delete ortSession;
            ortSession = nullptr;
        }

        Ort::SessionOptions sessionOptions;
        sessionOptions.SetIntraOpNumThreads(2);
        sessionOptions.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_BASIC);

        // Android NDK par model path seedha const char* (ORTCHAR_T) leta hai
        ortSession = new Ort::Session(*ortEnv, model, sessionOptions);
        currentModelPath = std::string(model);

        // Config file (.onnx.json) se Phoneme IDs load karna
        loadPhonemeIdMap(config);

        LOGD("ONNX Session & Config initialized successfully!");
    } catch (const Ort::Exception& e) {
        LOGE("ONNX Init Exception: %s", e.what());
        env->ReleaseStringUTFChars(modelPath, model);
        env->ReleaseStringUTFChars(configPath, config);
        env->ReleaseStringUTFChars(dataPath, data);
        return JNI_FALSE;
    } catch (...) {
        LOGE("Unknown exception during Piper initialization");
        env->ReleaseStringUTFChars(modelPath, model);
        env->ReleaseStringUTFChars(configPath, config);
        env->ReleaseStringUTFChars(dataPath, data);
        return JNI_FALSE;
    }

    env->ReleaseStringUTFChars(modelPath, model);
    env->ReleaseStringUTFChars(configPath, config);
    env->ReleaseStringUTFChars(dataPath, data);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_itfb_dhwanitts_PiperEngine_synthesizeText(JNIEnv* env, jobject /* this */, jstring text, jfloat speed) {
    const char* nativeText = env->GetStringUTFChars(text, nullptr);
    std::string inputStr(nativeText ? nativeText : "");
    env->ReleaseStringUTFChars(text, nativeText);

    if (!ortSession || inputStr.empty()) {
        LOGE("Session null or empty text!");
        return env->NewByteArray(0);
    }

    try {
        // Piper VITS standard token sequence: BOS (1), Pad (0) between every token, EOS (2)
        int64_t bosId = phonemeIdMap.count("^") ? phonemeIdMap["^"] : 1;
        int64_t eosId = phonemeIdMap.count("$") ? phonemeIdMap["$"] : 2;
        int64_t padId = phonemeIdMap.count("_") ? phonemeIdMap["_"] : 0;
        int64_t spaceId = phonemeIdMap.count(" ") ? phonemeIdMap[" "] : 3;

        std::vector<int64_t> inputIds;
        inputIds.push_back(bosId);
        inputIds.push_back(padId);

        std::vector<std::string> chars = splitUtf8(inputStr);
        for (const std::string& ch : chars) {
            if (phonemeIdMap.count(ch)) {
                inputIds.push_back(phonemeIdMap[ch]);
                inputIds.push_back(padId);
            } else if (ch == " " || ch == "\n" || ch == "\t") {
                inputIds.push_back(spaceId);
                inputIds.push_back(padId);
            } else {
                // Fallback mapping for unmapped characters so audio never fails
                unsigned char c = ch[0];
                int64_t fallbackId = (c % 40) + 4;
                inputIds.push_back(fallbackId);
                inputIds.push_back(padId);
            }
        }
        inputIds.push_back(eosId);

        auto memoryInfo = Ort::MemoryInfo::CreateCpu(OrtDeviceAllocator, OrtMemTypeCPU);

        // 1. Tensor: input [1, sequence_length]
        int64_t inputShape[] = {1, static_cast<int64_t>(inputIds.size())};
        Ort::Value inputTensor = Ort::Value::CreateTensor<int64_t>(
            memoryInfo, inputIds.data(), inputIds.size(), inputShape, 2
        );

        // 2. Tensor: input_lengths [1] (Yeh pehle chhoot gaya tha!)
        int64_t inputLengthsData[] = {static_cast<int64_t>(inputIds.size())};
        int64_t inputLengthsShape[] = {1};
        Ort::Value inputLengthsTensor = Ort::Value::CreateTensor<int64_t>(
            memoryInfo, inputLengthsData, 1, inputLengthsShape, 1
        );

        // 3. Tensor: scales [3] (noise_scale, length_scale, noise_w)
        float safeSpeed = (speed > 0.1f) ? speed : 1.0f;
        float scalesData[] = {0.667f, 1.0f / safeSpeed, 0.8f};
        int64_t scalesShape[] = {3};
        Ort::Value scalesTensor = Ort::Value::CreateTensor<float>(
            memoryInfo, scalesData, 3, scalesShape, 1
        );

        std::vector<const char*> inputNames = {"input", "input_lengths", "scales"};
        std::vector<Ort::Value> inputTensors;
        inputTensors.push_back(std::move(inputTensor));
        inputTensors.push_back(std::move(inputLengthsTensor));
        inputTensors.push_back(std::move(scalesTensor));

        // Agar multi-speaker model hai to 'sid' tensor bhi jodna
        int64_t sidData[] = {0};
        int64_t sidShape[] = {1};
        size_t numModelInputs = ortSession->GetInputCount();
        if (numModelInputs >= 4) {
            Ort::Value sidTensor = Ort::Value::CreateTensor<int64_t>(
                memoryInfo, sidData, 1, sidShape, 1
            );
            inputNames.push_back("sid");
            inputTensors.push_back(std::move(sidTensor));
        }

        const char* outputNames[] = {"output"};

        // Real ONNX Inference Run
        auto outputTensors = ortSession->Run(
            Ort::RunOptions{nullptr},
            inputNames.data(),
            inputTensors.data(),
            inputTensors.size(),
            outputNames,
            1
        );

        float* floatAudio = outputTensors.front().GetTensorMutableData<float>();
        size_t audioCount = outputTensors.front().GetTensorTypeAndShapeInfo().GetElementCount();

        LOGD("Inference complete! Samples generated: %zu", audioCount);

        int byteSize = static_cast<int>(audioCount * 2);
        jbyteArray audioBytes = env->NewByteArray(byteSize);
        jbyte* pBuff = env->GetByteArrayElements(audioBytes, nullptr);

        int16_t* pcm16Buffer = reinterpret_cast<int16_t*>(pBuff);
        for (size_t i = 0; i < audioCount; i++) {
            float sample = floatAudio[i];
            if (sample > 1.0f) sample = 1.0f;
            if (sample < -1.0f) sample = -1.0f;
            pcm16Buffer[i] = static_cast<int16_t>(sample * 32767.0f);
        }

        env->ReleaseByteArrayElements(audioBytes, pBuff, 0);
        return audioBytes;

    } catch (const Ort::Exception& e) {
        LOGE("ONNX Inference Exception: %s", e.what());
        return env->NewByteArray(0);
    } catch (const std::exception& e) {
        LOGE("Standard Exception: %s", e.what());
        return env->NewByteArray(0);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_itfb_dhwanitts_PiperEngine_destroyEngine(JNIEnv* env, jobject /* this */) {
    LOGD("Destroying Real Piper Engine session...");
    if (ortSession) {
        delete ortSession;
        ortSession = nullptr;
    }
    currentModelPath = "";
}
