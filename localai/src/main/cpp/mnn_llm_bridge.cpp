/**
 * MNN LLM Bridge - JNI层桥接代码
 * 
 * 为Flutter Platform Channel提供MNN LLM推理能力
 * 精简自MnnLlmChat的llm_mnn_jni.cpp，仅保留文本生成核心功能
 */

#include <android/log.h>
#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>
#include <chrono>
#include <sstream>
#include <functional>
#include <cerrno>
#include "llm/llm.hpp"

#define LOG_TAG "MnnLlmBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

using MNN::Transformer::Llm;
using MNN::Transformer::ChatMessages;
using MNN::Transformer::LlmContext;

// Inline stream buffer for capturing LLM output token by token
class LlmStreamBuffer : public std::streambuf {
public:
    using CallBack = std::function<void(const char* str, size_t len)>;
    explicit LlmStreamBuffer(CallBack callback) : callback_(std::move(callback)) {}
protected:
    std::streamsize xsputn(const char* s, std::streamsize n) override {
        if (callback_) {
            callback_(s, n);
        }
        return n;
    }
private:
    CallBack callback_ = nullptr;
};

namespace {
    JavaVM* g_jvm = nullptr;
    
    // 全局LLM实例（单例，因为内存有限只能加载一个模型）
    Llm* g_llm = nullptr;
    std::string g_model_config_path; // 保存模型路径用于重建
    std::mutex g_mutex;
    std::atomic<bool> g_stop_requested{false};
    std::atomic<bool> g_generating{false};
}

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    LOGI("MNN LLM Bridge JNI_OnLoad");
    return JNI_VERSION_1_6;
}

/**
 * 加载模型
 * @param configPath 模型config.json所在目录路径
 * @return 加载耗时(ms)，-1表示失败
 */
JNIEXPORT jlong JNICALL
Java_com_fongmi_android_tv_ai_local_LocalMnnEngine_nativeLoadModel(
        JNIEnv *env, jobject thiz, jstring configPath) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    // 如果已有模型，先释放
    if (g_llm != nullptr) {
        LOGI("Releasing existing model before loading new one");
        Llm::destroy(g_llm);
        g_llm = nullptr;
    }
    
    const char *path = env->GetStringUTFChars(configPath, nullptr);
    std::string config_path(path);
    env->ReleaseStringUTFChars(configPath, path);
    
    g_model_config_path = config_path; // 保存路径用于后续重建
    
    LOGI("Loading model from: %s", config_path.c_str());
    
    // === DEBUG: 验证文件是否可从 native 层直接 fopen ===
    {
        // 验证 config.json 本身
        FILE* f_config = fopen(config_path.c_str(), "r");
        if (f_config) {
            LOGI("DEBUG: config.json fopen OK");
            fclose(f_config);
        } else {
            LOGE("DEBUG: config.json fopen FAILED, errno=%d (%s)", errno, strerror(errno));
        }
        
        // 验证 tokenizer.txt
        size_t pos = config_path.find_last_of("/\\");
        std::string dir = (pos != std::string::npos) ? config_path.substr(0, pos + 1) : "./";
        std::string tokenizer_path = dir + "tokenizer.txt";
        LOGI("DEBUG: trying to fopen tokenizer at: %s", tokenizer_path.c_str());
        FILE* f_tok = fopen(tokenizer_path.c_str(), "r");
        if (f_tok) {
            LOGI("DEBUG: tokenizer.txt fopen OK");
            fclose(f_tok);
        } else {
            LOGE("DEBUG: tokenizer.txt fopen FAILED, errno=%d (%s)", errno, strerror(errno));
        }
    }
    // === END DEBUG ===
    
    auto start = std::chrono::high_resolution_clock::now();
    
    g_llm = Llm::createLLM(config_path);
    if (g_llm == nullptr) {
        LOGE("Failed to create LLM instance");
        return -1;
    }
    
    bool loaded = g_llm->load();
    if (!loaded) {
        LOGE("Failed to load model");
        Llm::destroy(g_llm);
        g_llm = nullptr;
        return -1;
    }
    
    // 关闭 thinking 模式（Qwen3.5 默认开启思考链）
    std::string disable_thinking = R"({"jinja":{"context":{"enable_thinking":false}}})";
    g_llm->set_config(disable_thinking);
    LOGI("Thinking mode disabled");

    // 仅加 n-gram 重复惩罚，防止小模型陷入循环（"小兔子上小兔子上..."）
    // 不动 temperature/topK/topP（保持默认，前面调整 sampler 反而失败的教训）
    // 仅在 sampler 链里追加 ngram penalty 作为兜底
    std::string ngram_penalty = R"({
        "ngram": 8,
        "ngram_factor": 1.05
    })";
    g_llm->set_config(ngram_penalty);
    LOGI("Ngram repetition penalty enabled");
    
    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
    
    LOGI("Model loaded successfully in %lld ms", (long long)duration);
    return (jlong)duration;
}

/**
 * 生成文本（流式回调）
 * @param prompt 用户输入的完整prompt
 * @param maxTokens 最大生成token数
 * @param listener 进度回调接口
 * @return 结果HashMap {prompt_len, decode_len, prefill_time_us, decode_time_us}
 */
JNIEXPORT jobject JNICALL
Java_com_fongmi_android_tv_ai_local_LocalMnnEngine_nativeGenerate(
        JNIEnv *env, jobject thiz,
        jstring systemPrompt, jstring userPrompt,
        jint maxTokens, jobject listener) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    // 构建返回的HashMap
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
    jmethodID putMethod = env->GetMethodID(hashMapClass, "put",
                                           "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    jobject resultMap = env->NewObject(hashMapClass, hashMapInit);
    
    if (g_llm == nullptr) {
        env->CallObjectMethod(resultMap, putMethod,
                              env->NewStringUTF("error"),
                              env->NewStringUTF("Model not loaded"));
        return resultMap;
    }
    
    // 获取回调方法
    jclass listenerClass = env->GetObjectClass(listener);
    jmethodID onTokenMethod = env->GetMethodID(listenerClass, "onToken", "(Ljava/lang/String;)Z");
    
    const char *sys_cstr = env->GetStringUTFChars(systemPrompt, nullptr);
    const char *user_cstr = env->GetStringUTFChars(userPrompt, nullptr);
    
    // 构建ChatMessages
    ChatMessages messages;
    messages.push_back({"system", std::string(sys_cstr)});
    messages.push_back({"user", std::string(user_cstr)});
    
    env->ReleaseStringUTFChars(systemPrompt, sys_cstr);
    env->ReleaseStringUTFChars(userPrompt, user_cstr);
    
    LOGI("Starting generation, maxTokens=%d", (int)maxTokens);
    
    g_stop_requested = false;
    g_generating = true;
    
    // 彻底重置：销毁并重建 LLM 实例
    // syncPromptCache 方式不稳定（偶尔仍有 ChatML token leak / repetition loop），
    // 重建实例是唯一 100% 可靠的方式。天玑 9500 上 load 约 1s，可接受。
    {
        // 获取 config_path（从上面已获取）
        Llm::destroy(g_llm);
        g_llm = nullptr;
        
        g_llm = Llm::createLLM(g_model_config_path);
        if (g_llm == nullptr || !g_llm->load()) {
            LOGE("Failed to reload model for clean state");
            if (g_llm) { Llm::destroy(g_llm); g_llm = nullptr; }
            g_generating = false;
            env->CallObjectMethod(resultMap, putMethod,
                                  env->NewStringUTF("error"),
                                  env->NewStringUTF("Model reload failed"));
            return resultMap;
        }
        // 关闭 thinking + 设置 ngram
        std::string gen_config = R"({
            "jinja": {"context": {"enable_thinking": false}},
            "ngram": 8,
            "ngram_factor": 1.05
        })";
        g_llm->set_config(gen_config);
        LOGI("LLM instance recreated for 100%% clean state");
    }
    
    // 使用流式输出
    std::stringstream ss;
    std::string pending_bytes; // 缓存不完整的 UTF-8 字节
    LlmStreamBuffer streambuf([&](const char* str, size_t len) {
        if (g_stop_requested) return;
        
        // 将新数据追加到 pending buffer
        pending_bytes.append(str, len);
        
        // 找到最后一个完整 UTF-8 字符的边界
        size_t valid_end = 0;
        size_t i = 0;
        while (i < pending_bytes.size()) {
            unsigned char c = (unsigned char)pending_bytes[i];
            int char_len = 0;
            if (c < 0x80) char_len = 1;
            else if ((c & 0xE0) == 0xC0) char_len = 2;
            else if ((c & 0xF0) == 0xE0) char_len = 3;
            else if ((c & 0xF8) == 0xF0) char_len = 4;
            else { i++; continue; } // invalid leading byte, skip
            
            if (i + char_len <= pending_bytes.size()) {
                valid_end = i + char_len;
                i += char_len;
            } else {
                break; // incomplete character, wait for more bytes
            }
        }
        
        if (valid_end == 0) return; // no complete characters yet
        
        // 提取完整的 UTF-8 字符串
        std::string token = pending_bytes.substr(0, valid_end);
        pending_bytes = pending_bytes.substr(valid_end);
        
        // 回调到Java层
        jstring jToken = env->NewStringUTF(token.c_str());
        if (jToken == nullptr) {
            // NewStringUTF 失败（理论上不应该到这里了），跳过
            env->ExceptionClear();
            return;
        }
        jboolean shouldStop = env->CallBooleanMethod(listener, onTokenMethod, jToken);
        env->DeleteLocalRef(jToken);
        
        if (shouldStop) {
            g_stop_requested = true;
        }
    });
    std::ostream os(&streambuf);
    
    // ============================================================
    // 关键：照搬 MnnLlmChat 的"步进式生成"模式
    // 直接 g_llm->response(messages, &os, nullptr, maxTokens) 在某些情况下行为异常
    //（字数飘忽 / 元指令泄漏）。MnnLlmChat 的做法是：
    //   1. 先 response() 触发 prefill（第 4 参数 0 表示不限制）
    //   2. 然后循环 generate(1) 一个 token 一个 token 走
    //   3. 直到模型自己输出 EOS、或用户停止、或达到 max_new_tokens
    // 这种方式让模型严格按 chat template 的 EOS 信号自然停止，质量更稳。
    // ============================================================
    
    // 1) 触发 prefill（第 4 参数 0 = 由 generate() 循环控制 token 数）
    g_llm->response(messages, &os, nullptr, 0);
    
    // 2) 循环 generate 直到模型自然结束 / 用户停止 / 达到上限
    int generated = 0;
    while (!g_stop_requested && !g_llm->stoped() && generated < (int)maxTokens) {
        g_llm->generate(1);
        generated++;
    }
    
    g_generating = false;
    
    // 获取性能数据
    const LlmContext* ctx = g_llm->getContext();
    
    jclass longClass = env->FindClass("java/lang/Long");
    jmethodID longInit = env->GetMethodID(longClass, "<init>", "(J)V");
    
    env->CallObjectMethod(resultMap, putMethod,
                          env->NewStringUTF("prompt_len"),
                          env->NewObject(longClass, longInit, (jlong)ctx->prompt_len));
    env->CallObjectMethod(resultMap, putMethod,
                          env->NewStringUTF("decode_len"),
                          env->NewObject(longClass, longInit, (jlong)ctx->gen_seq_len));
    env->CallObjectMethod(resultMap, putMethod,
                          env->NewStringUTF("prefill_time_us"),
                          env->NewObject(longClass, longInit, (jlong)ctx->prefill_us));
    env->CallObjectMethod(resultMap, putMethod,
                          env->NewStringUTF("decode_time_us"),
                          env->NewObject(longClass, longInit, (jlong)ctx->decode_us));
    
    LOGI("Generation complete: prompt_len=%d, decode_len=%d, prefill=%lldus, decode=%lldus",
         ctx->prompt_len, ctx->gen_seq_len,
         (long long)ctx->prefill_us, (long long)ctx->decode_us);
    
    // 生成结束后简单 reset（下次生成时会销毁重建，这里只是安全清理）
    g_llm->reset();
    
    return resultMap;
}

/**
 * 使用完整对话历史生成文本（流式回调）
 * 
 * 用于互动故事续写：传入完整的多轮对话历史，MNN 内部处理 chat template。
 * 不销毁重建实例，依赖 MNN 的 response() 正确处理多轮上下文。
 * 
 * @param historyList List<Pair<String, String>>，每个 Pair 为 (role, content)
 * @param maxTokens 最大生成 token 数
 * @param listener 进度回调接口
 * @return 结果 HashMap
 */
JNIEXPORT jobject JNICALL
Java_com_fongmi_android_tv_ai_local_LocalMnnEngine_nativeGenerateWithHistory(
        JNIEnv *env, jobject thiz,
        jobject historyList, jint maxTokens, jobject listener) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    // 构建返回的 HashMap
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
    jmethodID putMethod = env->GetMethodID(hashMapClass, "put",
                                           "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    jobject resultMap = env->NewObject(hashMapClass, hashMapInit);
    
    if (g_llm == nullptr) {
        env->CallObjectMethod(resultMap, putMethod,
                              env->NewStringUTF("error"),
                              env->NewStringUTF("Model not loaded"));
        return resultMap;
    }
    
    // 解析 Java List<Pair<String, String>> 为 ChatMessages
    ChatMessages messages;
    
    jclass listClass = env->GetObjectClass(historyList);
    jmethodID sizeMethod = env->GetMethodID(listClass, "size", "()I");
    jmethodID getMethod = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");
    jint listSize = env->CallIntMethod(historyList, sizeMethod);
    
    jclass pairClass = env->FindClass("android/util/Pair");
    if (pairClass == nullptr) {
        env->CallObjectMethod(resultMap, putMethod,
                              env->NewStringUTF("error"),
                              env->NewStringUTF("Pair class not found"));
        return resultMap;
    }
    jfieldID firstField = env->GetFieldID(pairClass, "first", "Ljava/lang/Object;");
    jfieldID secondField = env->GetFieldID(pairClass, "second", "Ljava/lang/Object;");
    
    for (jint i = 0; i < listSize; i++) {
        jobject pairObj = env->CallObjectMethod(historyList, getMethod, i);
        if (pairObj == nullptr) continue;
        
        jstring roleStr = (jstring)env->GetObjectField(pairObj, firstField);
        jstring contentStr = (jstring)env->GetObjectField(pairObj, secondField);
        
        const char *role = env->GetStringUTFChars(roleStr, nullptr);
        const char *content = env->GetStringUTFChars(contentStr, nullptr);
        
        messages.push_back({std::string(role), std::string(content)});
        
        env->ReleaseStringUTFChars(roleStr, role);
        env->ReleaseStringUTFChars(contentStr, content);
        env->DeleteLocalRef(roleStr);
        env->DeleteLocalRef(contentStr);
        env->DeleteLocalRef(pairObj);
    }
    
    LOGI("GenerateWithHistory: %d messages, maxTokens=%d", (int)messages.size(), (int)maxTokens);
    
    // 获取回调方法
    jclass listenerClass = env->GetObjectClass(listener);
    jmethodID onTokenMethod = env->GetMethodID(listenerClass, "onToken", "(Ljava/lang/String;)Z");
    
    g_stop_requested = false;
    g_generating = true;
    
    // 不销毁重建！直接用现有实例的 response() 传入完整历史
    // MNN 内部会处理 chat template 拼接和 KV cache
    std::string pending_bytes;
    LlmStreamBuffer streambuf([&](const char* str, size_t len) {
        if (g_stop_requested) return;
        
        pending_bytes.append(str, len);
        
        // UTF-8 完整性检查
        size_t valid_end = 0;
        size_t i = 0;
        while (i < pending_bytes.size()) {
            unsigned char c = (unsigned char)pending_bytes[i];
            int char_len = 0;
            if (c < 0x80) char_len = 1;
            else if ((c & 0xE0) == 0xC0) char_len = 2;
            else if ((c & 0xF0) == 0xE0) char_len = 3;
            else if ((c & 0xF8) == 0xF0) char_len = 4;
            else { i++; continue; }
            
            if (i + char_len <= pending_bytes.size()) {
                valid_end = i + char_len;
                i += char_len;
            } else {
                break;
            }
        }
        
        if (valid_end == 0) return;
        
        std::string token = pending_bytes.substr(0, valid_end);
        pending_bytes = pending_bytes.substr(valid_end);
        
        jstring jToken = env->NewStringUTF(token.c_str());
        if (jToken == nullptr) {
            env->ExceptionClear();
            return;
        }
        jboolean shouldStop = env->CallBooleanMethod(listener, onTokenMethod, jToken);
        env->DeleteLocalRef(jToken);
        
        if (shouldStop) {
            g_stop_requested = true;
        }
    });
    std::ostream os(&streambuf);
    
    // 使用步进式生成（与 nativeGenerate 一致）
    g_llm->response(messages, &os, nullptr, 0);
    
    int generated = 0;
    while (!g_stop_requested && !g_llm->stoped() && generated < (int)maxTokens) {
        g_llm->generate(1);
        generated++;
    }
    
    g_generating = false;
    
    // 获取性能数据
    const LlmContext* ctx = g_llm->getContext();
    
    jclass longClass = env->FindClass("java/lang/Long");
    jmethodID longInit = env->GetMethodID(longClass, "<init>", "(J)V");
    
    env->CallObjectMethod(resultMap, putMethod,
                          env->NewStringUTF("prompt_len"),
                          env->NewObject(longClass, longInit, (jlong)ctx->prompt_len));
    env->CallObjectMethod(resultMap, putMethod,
                          env->NewStringUTF("decode_len"),
                          env->NewObject(longClass, longInit, (jlong)ctx->gen_seq_len));
    env->CallObjectMethod(resultMap, putMethod,
                          env->NewStringUTF("prefill_time_us"),
                          env->NewObject(longClass, longInit, (jlong)ctx->prefill_us));
    env->CallObjectMethod(resultMap, putMethod,
                          env->NewStringUTF("decode_time_us"),
                          env->NewObject(longClass, longInit, (jlong)ctx->decode_us));
    
    LOGI("GenerateWithHistory complete: prompt_len=%d, decode_len=%d",
         ctx->prompt_len, ctx->gen_seq_len);
    
    // 不 reset！保留 KV cache 供下次续写使用
    // 互动故事结束后由调用方显式调用 releaseModel 清理
    
    return resultMap;
}

/**
 * 停止生成
 */
JNIEXPORT void JNICALL
Java_com_fongmi_android_tv_ai_local_LocalMnnEngine_nativeStopGenerate(
        JNIEnv *env, jobject thiz) {
    g_stop_requested = true;
    LOGI("Stop requested");
}

/**
 * 释放模型
 */
JNIEXPORT void JNICALL
Java_com_fongmi_android_tv_ai_local_LocalMnnEngine_nativeReleaseModel(
        JNIEnv *env, jobject thiz) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    if (g_llm != nullptr) {
        LOGI("Releasing model");
        Llm::destroy(g_llm);
        g_llm = nullptr;
    }
}

/**
 * 查询模型是否已加载
 */
JNIEXPORT jboolean JNICALL
Java_com_fongmi_android_tv_ai_local_LocalMnnEngine_nativeIsModelLoaded(
        JNIEnv *env, jobject thiz) {
    return g_llm != nullptr ? JNI_TRUE : JNI_FALSE;
}

/**
 * 查询是否正在生成
 */
JNIEXPORT jboolean JNICALL
Java_com_fongmi_android_tv_ai_local_LocalMnnEngine_nativeIsGenerating(
        JNIEnv *env, jobject thiz) {
    return g_generating ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
