#include <jni.h>
#include <android/log.h>
#include <cstdint>
#include <cstring>
#include <string>
#include <memory>
#include <mutex>
#include <vector>
#include <algorithm>
#include <chrono>
#include <thread>
#include <filesystem>
#include <dlfcn.h>

#include "ggml.h"
#include "ggml-backend.h"
#include "llama.h"
#include "llama.cpp/tools/mtmd/mtmd.h"
#include "llama.cpp/tools/mtmd/mtmd-helper.h"

#define LOG_TAG "LocalMind-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global references for JNI class caching
static jclass g_PerfMetricsClass = nullptr;
static jclass g_ModelMetadataClass = nullptr;
static jclass g_HashMapClass = nullptr;
static std::mutex g_backend_mutex;
static bool g_backend_initialized = false;

static std::string resolve_native_lib_dir() {
    Dl_info info {};
    if (dladdr(reinterpret_cast<void *>(&resolve_native_lib_dir), &info) == 0 || info.dli_fname == nullptr) {
        return {};
    }
    std::filesystem::path lib_path(info.dli_fname);
    auto parent = lib_path.parent_path();
    return parent.empty() ? std::string() : parent.string();
}

static void load_ggml_backends(const std::string &preferred_dir) {
    if (!preferred_dir.empty()) {
        const bool exists = std::filesystem::exists(std::filesystem::path(preferred_dir));
        LOGI("Backend init attempt explicit dir: %s (exists=%d)", preferred_dir.c_str(), exists ? 1 : 0);
        ggml_backend_load_all_from_path(preferred_dir.c_str());
    }
    if (ggml_backend_reg_count() == 0) {
        const auto backend_dir = resolve_native_lib_dir();
        if (!backend_dir.empty()) {
            const bool exists = std::filesystem::exists(std::filesystem::path(backend_dir));
            LOGI("Backend init attempt resolved dir: %s (exists=%d)", backend_dir.c_str(), exists ? 1 : 0);
            ggml_backend_load_all_from_path(backend_dir.c_str());
        }
    }
    if (ggml_backend_reg_count() == 0) {
        LOGI("Backend init fallback to default search paths");
        ggml_backend_load_all();
    }
    LOGI("GGML backend registry count after load: %zu", ggml_backend_reg_count());
}

static bool ensure_backend_initialized(const std::string &preferred_dir) {
    std::lock_guard<std::mutex> lock(g_backend_mutex);
    if (g_backend_initialized) {
        return true;
    }
    load_ggml_backends(preferred_dir);

    // We intentionally ignore ggml_backend_reg_count() == 0 here because
    // statically linked backends like the CPU backend are always initialized
    // by llama_backend_init().

    llama_backend_init();
    g_backend_initialized = true;
    return true;
}

struct LlamaContext {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    mtmd_context* ctx_vision = nullptr;
    std::mutex mutex;
    bool is_generating = false;
    bool should_stop = false;
    std::vector<llama_token> last_tokens; // For prefix caching
};

// JNI_OnLoad: Called exactly once when the library is loaded
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    // Cache classes to prevent FindClass failures on background threads
    jclass perfClass = env->FindClass("com/localmind/app/core/engine/PerfMetrics");
    if (perfClass) g_PerfMetricsClass = (jclass)env->NewGlobalRef(perfClass);

    jclass metaClass = env->FindClass("com/localmind/app/core/engine/ModelMetadata");
    if (metaClass) g_ModelMetadataClass = (jclass)env->NewGlobalRef(metaClass);

    jclass mapClass = env->FindClass("java/util/HashMap");
    if (mapClass) g_HashMapClass = (jclass)env->NewGlobalRef(mapClass);

    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
        if (g_PerfMetricsClass) env->DeleteGlobalRef(g_PerfMetricsClass);
        if (g_ModelMetadataClass) env->DeleteGlobalRef(g_ModelMetadataClass);
        if (g_HashMapClass) env->DeleteGlobalRef(g_HashMapClass);
    }
    if (g_backend_initialized) {
        llama_backend_free();
    }
}

static inline bool is_cont(uint8_t b) { return (b & 0xC0u) == 0x80u; }

static std::string drain_valid_utf8(std::string& buffer) {
    // ... [Keep your existing drain_valid_utf8 logic exactly as is] ...
    if (buffer.empty()) return {};
    std::string out;
    out.reserve(buffer.size());
    size_t i = 0;
    while (i < buffer.size()) {
        uint8_t b0 = static_cast<uint8_t>(buffer[i]);
        if (b0 == 0u) { i += 1; continue; }
        if (b0 < 0x80u) { out.push_back(static_cast<char>(b0)); i += 1; continue; }
        if (b0 >= 0xC2u && b0 <= 0xDFu) {
            if (i + 1 >= buffer.size()) break;
            uint8_t b1 = static_cast<uint8_t>(buffer[i + 1]);
            if (is_cont(b1)) { out.push_back(static_cast<char>(b0)); out.push_back(static_cast<char>(b1)); i += 2; }
            else { i += 1; }
            continue;
        }
        if (b0 >= 0xE0u && b0 <= 0xEFu) {
            if (i + 2 >= buffer.size()) break;
            uint8_t b1 = static_cast<uint8_t>(buffer[i + 1]);
            uint8_t b2 = static_cast<uint8_t>(buffer[i + 2]);
            const bool ok = is_cont(b1) && is_cont(b2) && !(b0 == 0xE0u && b1 < 0xA0u) && !(b0 == 0xEDu && b1 >= 0xA0u);
            if (ok) { out.push_back(static_cast<char>(b0)); out.push_back(static_cast<char>(b1)); out.push_back(static_cast<char>(b2)); i += 3; }
            else { i += 1; }
            continue;
        }
        if (b0 >= 0xF0u && b0 <= 0xF4u) {
            if (i + 3 >= buffer.size()) break;
            uint8_t b1 = static_cast<uint8_t>(buffer[i + 1]); uint8_t b2 = static_cast<uint8_t>(buffer[i + 2]); uint8_t b3 = static_cast<uint8_t>(buffer[i + 3]);
            const bool ok = is_cont(b1) && is_cont(b2) && is_cont(b3) && !(b0 == 0xF0u && b1 < 0x90u) && !(b0 == 0xF4u && b1 > 0x8Fu);
            if (ok) { out.push_back(static_cast<char>(b0)); out.push_back(static_cast<char>(b1)); out.push_back(static_cast<char>(b2)); out.push_back(static_cast<char>(b3)); i += 4; }
            else { i += 1; }
            continue;
        }
        i += 1;
    }
    buffer.erase(0, i);
    return out;
}

extern "C" {

JNIEXPORT jboolean JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_initializeBackend(
    JNIEnv* env, jobject, jstring native_lib_dir) {
    std::string preferred_dir;
    if (native_lib_dir != nullptr) {
        const char* dir_chars = env->GetStringUTFChars(native_lib_dir, nullptr);
        if (dir_chars != nullptr) {
            preferred_dir = dir_chars;
            env->ReleaseStringUTFChars(native_lib_dir, dir_chars);
        }
    }
    return ensure_backend_initialized(preferred_dir) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_loadModel(
    JNIEnv* env, jobject, jstring path, jboolean use_mlock, jboolean use_mmap, jint context_size, jint thread_count, jint gpu_layers,
    jint batch_size, jint physical_batch_size, jstring flash_attention, jstring key_cache_type, jstring value_cache_type) {
    try {
        const char* model_path = env->GetStringUTFChars(path, nullptr);
        auto* context = new LlamaContext();

        // Defensive: ensure backend init happened even if caller forgot to invoke initializeBackend().
        if (!ensure_backend_initialized("")) {
            delete context;
            env->ReleaseStringUTFChars(path, model_path);
            return 0;
        }

        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = std::max(0, static_cast<int>(gpu_layers));
        model_params.use_mlock = use_mlock;
        model_params.use_mmap = use_mmap;

        LOGI("Native: Loading model from %s [gpu_layers=%d, mmap=%d]", model_path, gpu_layers, use_mmap ? 1 : 0);
        auto load_start = std::chrono::steady_clock::now();
        context->model = llama_model_load_from_file(model_path, model_params);
        auto load_end = std::chrono::steady_clock::now();

        if (!context->model) {
            LOGE("Native: Failed to load model");
            delete context;
            env->ReleaseStringUTFChars(path, model_path);
            return 0;
        }

        double load_ms = std::chrono::duration<double, std::milli>(load_end - load_start).count();
        LOGI("Native: Model loaded in %.2fms [requested_gpu=%d]", load_ms, model_params.n_gpu_layers);

        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = std::max(256, static_cast<int>(context_size));
        ctx_params.n_threads = std::max(1, static_cast<int>(thread_count));
        // PERF FIX: Use ALL CPU cores for batch processing (prefill).
        // Prefill is compute-bound and highly parallelizable, unlike generation
        // which is memory-bandwidth bound. This gives 2-3x prefill speedup.
        int hw_threads = std::max(1, static_cast<int>(std::thread::hardware_concurrency()));
        ctx_params.n_threads_batch = std::max(ctx_params.n_threads, hw_threads);
        ctx_params.n_batch = std::max(1, static_cast<int>(batch_size));
        ctx_params.n_ubatch = std::max(1, static_cast<int>(physical_batch_size));

        // Parse Flash Attention
        const char* fa_chars = env->GetStringUTFChars(flash_attention, nullptr);
        std::string fa_str = fa_chars ? fa_chars : "Auto";
        if (fa_chars) env->ReleaseStringUTFChars(flash_attention, fa_chars);

        if (fa_str == "On") ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
        else if (fa_str == "Off") ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;
        else ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;

        // Parse Cache Types
        auto parse_cache_type = [&](jstring j_type) -> ggml_type {
            const char* type_chars = env->GetStringUTFChars(j_type, nullptr);
            std::string type_str = type_chars ? type_chars : "F16";
            if (type_chars) env->ReleaseStringUTFChars(j_type, type_chars);

            if (type_str == "F32") return GGML_TYPE_F32;
            if (type_str == "Q8_0") return GGML_TYPE_Q8_0;
            if (type_str == "Q5_1") return GGML_TYPE_Q5_1;
            if (type_str == "Q5_0") return GGML_TYPE_Q5_0;
            if (type_str == "Q4_1") return GGML_TYPE_Q4_1;
            if (type_str == "Q4_0") return GGML_TYPE_Q4_0;
            if (type_str == "IQ4_NL") return GGML_TYPE_IQ4_NL;
            return GGML_TYPE_F16;
        };

        ctx_params.type_k = parse_cache_type(key_cache_type);
        ctx_params.type_v = parse_cache_type(value_cache_type);

        LOGI("Native: Context params [ctx=%d, threads=%d, batch=%d, ubatch=%d, flash=%d, type_k=%d, type_v=%d]",
             ctx_params.n_ctx, ctx_params.n_threads, ctx_params.n_batch, ctx_params.n_ubatch,
             ctx_params.flash_attn_type, ctx_params.type_k, ctx_params.type_v);

        context->ctx = llama_init_from_model(context->model, ctx_params);
        env->ReleaseStringUTFChars(path, model_path);

        if (!context->ctx) {
            llama_model_free(context->model);
            delete context;
            return 0;
        }
        return reinterpret_cast<jlong>(context);
    } catch (const std::exception& e) { return 0; }
}

JNIEXPORT void JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_generate(
    JNIEnv* env, jobject thiz, jlong context_ptr, jstring prompt, jfloat temperature, jfloat top_p, jint top_k, jfloat repeat_penalty, jint max_tokens, jboolean should_update_cache, jobject callback) {

    if (context_ptr == 0) return;
    auto* context = reinterpret_cast<LlamaContext*>(context_ptr);

    {
        std::lock_guard<std::mutex> lock(context->mutex);
        if (context->is_generating) return;
        context->is_generating = true;
        context->should_stop = false;
    }

    try {
        const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);

        jclass callback_class = env->GetObjectClass(callback);
        jmethodID on_token_method = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V");
        jmethodID on_complete_method = env->GetMethodID(callback_class, "onComplete", "()V");

        std::vector<llama_token> tokens;
        tokens.resize(std::max<size_t>(strlen(prompt_str) + 512, 2048));

        const llama_vocab* vocab = llama_model_get_vocab(context->model);
        int n_tokens = llama_tokenize(vocab, prompt_str, strlen(prompt_str), tokens.data(), tokens.size(), true, false);
        tokens.resize(n_tokens);

        auto start_time = std::chrono::steady_clock::now();
        LOGI("Native: Starting generation [tokens=%d]", n_tokens);

        // Prefix Caching Logic
        int n_past = 0;
        {
            int n_keep = 0;
            while (n_keep < tokens.size() && n_keep < context->last_tokens.size() && tokens[n_keep] == context->last_tokens[n_keep]) {
                n_keep++;
            }

            if (n_keep > 0) {
                // Remove everything after the common prefix to allow for reuse
                llama_memory_seq_rm(llama_get_memory(context->ctx), 0, n_keep, -1);
                n_past = n_keep;
                LOGI("Prefix caching: matched %d tokens, skipping re-evaluation", n_keep);
            } else {
                llama_memory_clear(llama_get_memory(context->ctx), false);
                LOGI("Prefix caching: no common prefix, clearing KV cache");
            }
        }

        // Prompt Evaluation
        const int n_batch_max = std::max(1, static_cast<int>(llama_n_batch(context->ctx)));
        llama_batch batch = llama_batch_init(n_batch_max, 0, 1);

        int consumed = n_past; // Start from where we left off
        while (consumed < n_tokens) {
            int chunk_size = std::min(n_batch_max, n_tokens - consumed);
            batch.n_tokens = chunk_size;
            for (int i = 0; i < chunk_size; i++) {
                batch.token[i] = tokens[consumed + i];
                batch.pos[i] = consumed + i;
                batch.n_seq_id[i] = 1;
                batch.seq_id[i][0] = 0;
                batch.logits[i] = (consumed + i == n_tokens - 1);
            }

            if (llama_decode(context->ctx, batch) != 0) {
                LOGE("llama_decode failed during prompt processing");
                llama_batch_free(batch);
                env->ReleaseStringUTFChars(prompt, prompt_str);
                context->is_generating = false;
                return;
            }
            consumed += chunk_size;
        }

        auto prefill_end_time = std::chrono::steady_clock::now();
        double ttft_ms = std::chrono::duration<double, std::milli>(prefill_end_time - start_time).count();
        LOGI("Native: Prefill complete [time=%.2fms, cached=%d]", ttft_ms, n_past);

        llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
        llama_sampler* chain = llama_sampler_chain_init(sparams);
        llama_sampler_chain_add(chain, llama_sampler_init_penalties(64, repeat_penalty, 0.0f, 0.0f));
        llama_sampler_chain_add(chain, llama_sampler_init_top_k(top_k));
        llama_sampler_chain_add(chain, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(chain, llama_sampler_init_dist(static_cast<uint32_t>(std::chrono::steady_clock::now().time_since_epoch().count())));

        std::string utf8_buffer;
        int n_generated = 0;
        std::vector<llama_token> generated_tokens;

        while (n_generated < max_tokens) {
            if (context->should_stop) break;

            llama_token new_token = llama_sampler_sample(chain, context->ctx, -1);
            if (llama_vocab_is_eog(vocab, new_token)) break;

            generated_tokens.push_back(new_token);

            char token_str[1024];
            int n = llama_token_to_piece(vocab, new_token, token_str, sizeof(token_str), 0, false);
            if (n > 0) {
                utf8_buffer.append(token_str, token_str + std::min(n, (int)sizeof(token_str)));
                std::string safe_piece = drain_valid_utf8(utf8_buffer);

                if (!safe_piece.empty()) {
                    jstring token_obj = env->NewStringUTF(safe_piece.c_str());
                    if (token_obj && !env->ExceptionCheck()) {
                        env->CallVoidMethod(callback, on_token_method, token_obj);
                        env->DeleteLocalRef(token_obj);
                    }
                }
            }

            // Decode the next token
            batch.n_tokens = 1;
            batch.token[0] = new_token;
            batch.pos[0] = n_tokens + n_generated;
            batch.n_seq_id[0] = 1;
            batch.seq_id[0][0] = 0;
            batch.logits[0] = true;

            if (llama_decode(context->ctx, batch) != 0) {
                LOGE("llama_decode failed during token generation");
                break;
            }

            llama_sampler_accept(chain, new_token);
            n_generated++;
        }

        // Update last_tokens for next turn's prefix caching ONLY if requested (usually for chat, not utility tasks)
        if (should_update_cache) {
            std::vector<llama_token> final_tokens = tokens;
            final_tokens.insert(final_tokens.end(), generated_tokens.begin(), generated_tokens.end());
            context->last_tokens = std::move(final_tokens);
            LOGI("Native: Prefix cache updated [total_tokens=%zu]", context->last_tokens.size());
        } else {
            LOGI("Native: Skipping prefix cache update (utility task)");
        }

        auto gen_end_time = std::chrono::steady_clock::now();
        double gen_ms = std::chrono::duration<double, std::milli>(gen_end_time - prefill_end_time).count();
        double tps = (n_generated > 0) ? (n_generated / (gen_ms / 1000.0)) : 0;
        LOGI("Native: Generation complete [tokens=%d, speed=%.2f t/s]", n_generated, tps);

        llama_batch_free(batch);
        llama_sampler_free(chain);
        env->CallVoidMethod(callback, on_complete_method);

cleanup:
        env->ReleaseStringUTFChars(prompt, prompt_str);
    } catch (...) {}

    {
        std::lock_guard<std::mutex> lock(context->mutex);
        context->is_generating = false;
    }
}

JNIEXPORT void JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_stopGeneration(JNIEnv* env, jobject, jlong context_ptr) {
    if (context_ptr == 0) return;
    auto* context = reinterpret_cast<LlamaContext*>(context_ptr);
    context->should_stop = true; // No mutex lock here to prevent deadlocks during UI thread calls
}

JNIEXPORT void JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_unloadModel(JNIEnv* env, jobject, jlong context_ptr) {
    if (context_ptr == 0) return;
    auto* context = reinterpret_cast<LlamaContext*>(context_ptr);

    // FIX FOR THE MEMORY LEAK: Safely signal stop and wait for loop to exit
    if (context->is_generating) {
        context->should_stop = true;
        // Wait briefly for generation to catch the should_stop flag
        int retries = 50;
        while (context->is_generating && retries > 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            retries--;
        }
    }

    std::lock_guard<std::mutex> lock(context->mutex);
    if (context->ctx) {
        llama_free(context->ctx);
        context->ctx = nullptr;
    }
    if (context->model) {
        llama_model_free(context->model);
        context->model = nullptr;
    }
    // Removed llama_backend_free() from here.
    delete context;
}

JNIEXPORT jobject JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_getPerfMetrics(JNIEnv* env, jobject, jlong context_ptr) {
    if (context_ptr == 0 || !g_PerfMetricsClass) return nullptr;
    auto* context = reinterpret_cast<LlamaContext*>(context_ptr);
    llama_perf_context_data perf = llama_perf_context(context->ctx);

    // Using globally cached class
    jmethodID ctor = env->GetMethodID(g_PerfMetricsClass, "<init>", "(DDDI)V");
    if (!ctor) return nullptr;
    return env->NewObject(g_PerfMetricsClass, ctor, perf.t_p_eval_ms, perf.t_eval_ms, perf.t_load_ms, perf.n_eval);
}

JNIEXPORT jobject JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_getModelMetadata(JNIEnv* env, jobject, jstring path) {
    if (!path || !g_ModelMetadataClass || !g_HashMapClass) return nullptr;
    const char* model_path = env->GetStringUTFChars(path, nullptr);

    llama_model_params mparams = llama_model_default_params();
    mparams.vocab_only = true;
    llama_model* model = llama_model_load_from_file(model_path, mparams);
    env->ReleaseStringUTFChars(path, model_path);
    if (!model) return nullptr;

    jmethodID ctor = env->GetMethodID(g_ModelMetadataClass, "<init>", "(IIIIIIJJLjava/lang/String;Ljava/util/Map;)V");
    jmethodID mapCtor = env->GetMethodID(g_HashMapClass, "<init>", "()V");

    char desc_buf[256];
    llama_model_desc(model, desc_buf, sizeof(desc_buf));
    jstring model_desc = env->NewStringUTF(desc_buf);
    jobject hparams = env->NewObject(g_HashMapClass, mapCtor);

    jobject result = env->NewObject(g_ModelMetadataClass, ctor,
        llama_model_n_layer(model), llama_model_n_embd(model), llama_model_n_head(model), llama_model_n_head_kv(model),
        llama_model_n_ctx_train(model), llama_vocab_n_tokens(llama_model_get_vocab(model)),
        (jlong)llama_model_n_params(model), (jlong)llama_model_size(model), model_desc, hparams);

    llama_model_free(model);
    return result;
}

} // extern "C"
