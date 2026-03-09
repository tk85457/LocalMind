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
// llama-chat.h internal header removed — using public llama_chat_apply_template() API instead
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

// FIX 1: Add draft model global pointers
static llama_model*   g_draft_model   = nullptr;
static llama_context* g_draft_context = nullptr;
static bool           g_draft_loaded  = false;

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
    JNIEnv* env, jobject, jstring path, jboolean use_mlock, jboolean use_mmap, jint context_size, jint thread_count_decode, jint thread_count_prefill, jint gpu_layers,
    jint batch_size, jint physical_batch_size, jboolean flash_attention, jstring key_cache_type, jstring value_cache_type, jfloat defrag_threshold, jboolean kv_unified) {
    // POCKETPAL FIX: kv_unified param now properly received from Kotlin.
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
        ctx_params.n_threads = std::max(1, static_cast<int>(thread_count_decode));
        ctx_params.n_threads_batch = std::max(1, static_cast<int>(thread_count_prefill));
        ctx_params.n_batch = std::max(1, static_cast<int>(batch_size));
        ctx_params.n_ubatch = std::max(1, static_cast<int>(physical_batch_size));

        // POCKETPAL FIX: Android pe flash_attn DISABLED force karo.
        // OpenCL flash attention Android pe crash karta hai.
        // PocketPal bhi Android pe 'off' default rakhta hai.
        ctx_params.flash_attn_type = flash_attention
            ? LLAMA_FLASH_ATTN_TYPE_ENABLED
            : LLAMA_FLASH_ATTN_TYPE_DISABLED;

        // Parse Cache Types
        auto parse_cache_type = [&](jstring j_type) -> ggml_type {
            if (!j_type) return GGML_TYPE_F16;
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
        ctx_params.defrag_thold = defrag_threshold;

        // POCKETPAL FIX: kv_unified — Critical! Unified KV cache pool.
        // This can save up to 7GB RAM on large models and speeds up memory access.
        // Now properly controlled by caller (default = true from Kotlin).
        ctx_params.kv_unified = (bool)kv_unified;

        // Keep small-batch fallbacks effective on low-memory devices.
        ctx_params.n_batch = std::min((uint32_t)std::max(32, (int)batch_size), ctx_params.n_ctx);
        ctx_params.n_ubatch = std::min((uint32_t)std::max(32, (int)physical_batch_size), ctx_params.n_batch);

        LOGI("Native: Context params [ctx=%d, decode_threads=%d, prefill_threads=%d, batch=%d, ubatch=%d, flash=%d, type_k=%d, type_v=%d, kv_unified=%d, defrag=%.2f]",
             ctx_params.n_ctx, ctx_params.n_threads, ctx_params.n_threads_batch, ctx_params.n_batch, ctx_params.n_ubatch,
             ctx_params.flash_attn_type, ctx_params.type_k, ctx_params.type_v, (int)ctx_params.kv_unified, defrag_threshold);

        context->ctx = llama_init_from_model(context->model, ctx_params);
        env->ReleaseStringUTFChars(path, model_path);

        if (!context->ctx) {
            LOGE(
                "Native: llama_init_from_model failed [ctx=%d, threads=%d, batch=%d, ubatch=%d, gpu=%d, mlock=%d, mmap=%d]",
                ctx_params.n_ctx,
                ctx_params.n_threads,
                ctx_params.n_batch,
                ctx_params.n_ubatch,
                model_params.n_gpu_layers,
                model_params.use_mlock ? 1 : 0,
                model_params.use_mmap ? 1 : 0
            );
            llama_model_free(context->model);
            delete context;
            return 0;
        }
        LOGI("Native: Context initialized successfully");
        return reinterpret_cast<jlong>(context);
    } catch (const std::exception& e) { return 0; }
}

JNIEXPORT void JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_generate(
    JNIEnv* env, jobject thiz, jlong context_ptr, jstring prompt, jobjectArray stop_tokens,
    jfloat temperature, jfloat top_p, jint top_k, jfloat repeat_penalty, jint penalty_last_n,
    jint max_tokens, jboolean should_update_cache, jboolean cache_prompt, jfloat defrag_threshold,
    jfloat min_p, jint seed,
    // PocketPal parity: new sampler params
    jfloat xtc_threshold, jfloat xtc_probability,
    jfloat typical_p,
    jfloat penalty_freq, jfloat penalty_present,
    jint mirostat, jfloat mirostat_tau, jfloat mirostat_eta,
    jobject callback) {

    if (context_ptr == 0) return;
    auto* context = reinterpret_cast<LlamaContext*>(context_ptr);

    // TTFT FIX: context->mutex sirf is_generating/should_stop set karne ke liye.
    // Ye fast hai (nanoseconds) — generation loop mutex ke baahar chalta hai.
    {
        std::lock_guard<std::mutex> lock(context->mutex);
        if (context->is_generating) {
            LOGI("Native: generate() called while already generating — ignoring");
            return;
        }
        context->is_generating = true;
        context->should_stop = false;
    }

    try {
        const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
        std::vector<std::string> stop_words;
        if (stop_tokens != nullptr) {
            jsize len = env->GetArrayLength(stop_tokens);
            for (jsize i = 0; i < len; i++) {
                jstring stop_token = (jstring)env->GetObjectArrayElement(stop_tokens, i);
                if (stop_token) {
                    const char* stop_chars = env->GetStringUTFChars(stop_token, nullptr);
                    if (stop_chars) {
                        stop_words.push_back(stop_chars);
                        env->ReleaseStringUTFChars(stop_token, stop_chars);
                    }
                }
            }
        }

        jclass callback_class = env->GetObjectClass(callback);
        jmethodID on_token_method = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V");
        jmethodID on_complete_method = env->GetMethodID(callback_class, "onComplete", "()V");

        std::vector<llama_token> tokens;
        tokens.resize(std::max<size_t>(strlen(prompt_str) + 512, 2048));

        const llama_vocab* vocab = llama_model_get_vocab(context->model);
        int n_tokens = llama_tokenize(vocab, prompt_str, strlen(prompt_str), tokens.data(), tokens.size(), true, false);
        tokens.resize(n_tokens);

        auto start_time = std::chrono::steady_clock::now();
        // Prefix Caching Logic
        int n_past = 0;
        {
            int n_keep = 0;
            if (cache_prompt) {
                while (n_keep < (int)tokens.size() && n_keep < (int)context->last_tokens.size() && tokens[n_keep] == context->last_tokens[n_keep]) {
                    n_keep++;
                }

                if (n_keep > 0) {
                    llama_memory_seq_rm(llama_get_memory(context->ctx), 0, n_keep, -1);
                    n_past = n_keep;
                } else {
                    llama_memory_clear(llama_get_memory(context->ctx), false);
                }
            } else {
                llama_memory_clear(llama_get_memory(context->ctx), false);
            }
            // TTFT DIAGNOSTIC: Token breakdown for profiling
            LOGI("TTFT-DIAG: total_tokens=%d cache_hit=%d new_prefill=%d cache_enabled=%d last_tokens=%zu",
                 n_tokens, n_past, n_tokens - n_past, (int)cache_prompt, context->last_tokens.size());
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
        double prefill_ms = std::chrono::duration<double, std::milli>(prefill_end_time - start_time).count();
        int new_tokens_evaluated = n_tokens - n_past;
        double tpp = (new_tokens_evaluated > 0) ? (prefill_ms / new_tokens_evaluated) : 0.0;
        // llama_perf_context se official prefill stats
        auto perf = llama_perf_context(context->ctx);
        LOGI("TTFT-DIAG: prefill_ms=%.1f new_tokens=%d ms_per_token=%.2f | perf: t_p_eval_ms=%.1f n_p_eval=%d",
             prefill_ms, new_tokens_evaluated, tpp,
             perf.t_p_eval_ms, perf.n_p_eval);

        llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
        llama_sampler* chain = llama_sampler_chain_init(sparams);

        // PocketPal parity: penalties (repeat, freq, present) — same order as llama.cpp server
        llama_sampler_chain_add(chain, llama_sampler_init_penalties(penalty_last_n, repeat_penalty, penalty_freq, penalty_present));

        if (mirostat == 0) {
            // Standard sampling pipeline (PocketPal default)
            llama_sampler_chain_add(chain, llama_sampler_init_top_k(top_k));
            // PocketPal: typical_p (1.0 = disabled)
            if (typical_p > 0.0f && typical_p < 1.0f) {
                llama_sampler_chain_add(chain, llama_sampler_init_typical(typical_p, 1));
            }
            llama_sampler_chain_add(chain, llama_sampler_init_top_p(top_p, 1));
            // PocketPal: Min-P sampler
            if (min_p > 0.0f) {
                llama_sampler_chain_add(chain, llama_sampler_init_min_p(min_p, 1));
            }
            // PocketPal: XTC sampler (xtc_probability=0.0 means disabled)
            if (xtc_probability > 0.0f) {
                llama_sampler_chain_add(chain, llama_sampler_init_xtc(xtc_probability, xtc_threshold, 1, 0));
            }
            llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
        } else if (mirostat == 1) {
            // Mirostat v1
            llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
            llama_sampler_chain_add(chain, llama_sampler_init_mirostat(llama_vocab_n_tokens(llama_model_get_vocab(context->model)),
                0 /*seed handled by dist below*/, mirostat_tau, mirostat_eta, 100));
        } else {
            // Mirostat v2
            llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
            llama_sampler_chain_add(chain, llama_sampler_init_mirostat_v2(0 /*seed*/, mirostat_tau, mirostat_eta));
        }

        // PocketPal: Seed support — -1 = random, else reproducible output
        uint32_t effective_seed = (seed == -1)
            ? static_cast<uint32_t>(std::chrono::steady_clock::now().time_since_epoch().count())
            : static_cast<uint32_t>(seed);
        llama_sampler_chain_add(chain, llama_sampler_init_dist(effective_seed));

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

                // Stop Word Check
                for (const auto& stop_word : stop_words) {
                    if (utf8_buffer.size() >= stop_word.size()) {
                        if (utf8_buffer.compare(utf8_buffer.size() - stop_word.size(), stop_word.size(), stop_word) == 0) {
                            LOGI("Stop token reached: %s", stop_word.c_str());
                            context->should_stop = true;
                            break;
                        }
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

            // Note: Optimization #3 (Manual defrag call) is intentionally removed here
            // because this specific branch of llama.cpp does not expose a public
            // `llama_kv_cache_defrag` equivalent for manual invocation during streaming.

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

// ============================================================
// POCKETPAL PARITY: EOS Token + Chat Template extraction
// PocketPal: ctx.detokenize([eos_token_id]) + ctx.model.metadata
// ============================================================

JNIEXPORT jstring JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_getEosToken(
    JNIEnv* env, jobject, jlong context_ptr) {
    if (context_ptr == 0) return nullptr;
    auto* context = reinterpret_cast<LlamaContext*>(context_ptr);
    if (!context->model) return nullptr;

    const llama_vocab* vocab = llama_model_get_vocab(context->model);
    if (!vocab) return nullptr;

    // Get EOS token ID from vocab metadata
    llama_token eos_id = llama_vocab_eos(vocab);
    if (eos_id < 0) return nullptr;

    // Detokenize to string (PocketPal: ctx.detokenize([eos_token_id]))
    char eos_str[64] = {0};
    int n = llama_token_to_piece(vocab, eos_id, eos_str, sizeof(eos_str) - 1, 0, false);
    if (n <= 0) return nullptr;
    eos_str[n] = '\0';

    LOGI("getEosToken: EOS token_id=%d, str='%s'", eos_id, eos_str);
    return env->NewStringUTF(eos_str);
}

JNIEXPORT jstring JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_getChatTemplate(
    JNIEnv* env, jobject, jlong context_ptr) {
    if (context_ptr == 0) return nullptr;
    auto* context = reinterpret_cast<LlamaContext*>(context_ptr);
    if (!context->model) return nullptr;

    // Read chat template from GGUF metadata (same as PocketPal)
    const char* tmpl = llama_model_chat_template(context->model, /*name=*/nullptr);
    if (!tmpl || strlen(tmpl) == 0) return nullptr;

    return env->NewStringUTF(tmpl);
}

JNIEXPORT void JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_stopGeneration(JNIEnv* env, jobject, jlong context_ptr) {
    if (context_ptr == 0) return;
    auto* context = reinterpret_cast<LlamaContext*>(context_ptr);
    context->should_stop = true; // No mutex lock here to prevent deadlocks during UI thread calls
}

JNIEXPORT void JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_clearPrefixCache(JNIEnv* env, jobject, jlong context_ptr) {
    if (context_ptr == 0) return;
    auto* context = reinterpret_cast<LlamaContext*>(context_ptr);

    std::lock_guard<std::mutex> lock(context->mutex);
    if (context->is_generating || context->ctx == nullptr) {
        return;
    }

    context->last_tokens.clear();
    llama_memory_clear(llama_get_memory(context->ctx), false);
    context->should_stop = false;
    LOGI("Native: Prefix cache cleared");
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
    // VISION: Free projector context before freeing the main model
    if (context->ctx_vision) {
        mtmd_free(context->ctx_vision);
        context->ctx_vision = nullptr;
    }
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

// ============================================================
// VISION / MULTIMODAL SUPPORT
// ============================================================

JNIEXPORT jboolean JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_loadProjector(
    JNIEnv* env, jobject,
    jlong context_ptr,
    jstring projector_path
) {
    if (context_ptr == 0) return JNI_FALSE;
    auto* context = reinterpret_cast<LlamaContext*>(context_ptr);
    if (!context->model) { LOGE("loadProjector: no model loaded"); return JNI_FALSE; }

    const char* path = env->GetStringUTFChars(projector_path, nullptr);
    if (!path) return JNI_FALSE;

    // Free existing projector if any
    if (context->ctx_vision) {
        mtmd_free(context->ctx_vision);
        context->ctx_vision = nullptr;
    }

    mtmd_context_params vparams = mtmd_context_params_default();
    vparams.use_gpu = false; // Safe default for Android OpenCL
    vparams.n_threads = 4;
    vparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;
    vparams.warmup = false;
    vparams.media_marker = mtmd_default_marker();

    context->ctx_vision = mtmd_init_from_file(path, context->model, vparams);
    env->ReleaseStringUTFChars(projector_path, path);

    bool ok = context->ctx_vision != nullptr;
    LOGI("loadProjector: %s", ok ? "SUCCESS" : "FAILED");
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_unloadProjector(
    JNIEnv* env, jobject,
    jlong context_ptr
) {
    if (context_ptr == 0) return;
    auto* context = reinterpret_cast<LlamaContext*>(context_ptr);
    if (context->ctx_vision) {
        mtmd_free(context->ctx_vision);
        context->ctx_vision = nullptr;
        LOGI("unloadProjector: done");
    }
}

JNIEXPORT void JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_generateWithVision(
    JNIEnv* env, jobject,
    jlong context_ptr,
    jstring prompt_js,
    jbyteArray image_bytes,
    jfloat temperature,
    jfloat top_p,
    jint top_k,
    jfloat repeat_penalty,
    jint penalty_last_n,
    jint max_tokens,
    jobject callback
) {
    if (context_ptr == 0) return;
    auto* context = reinterpret_cast<LlamaContext*>(context_ptr);

    jclass callback_class = env->GetObjectClass(callback);
    jmethodID on_token_method   = env->GetMethodID(callback_class, "onToken",    "(Ljava/lang/String;)V");
    jmethodID on_complete_method = env->GetMethodID(callback_class, "onComplete", "()V");
    jmethodID on_error_method    = env->GetMethodID(callback_class, "onError",   "(Ljava/lang/String;)V");

    auto send_error = [&](const char* msg) {
        if (on_error_method) {
            jstring jmsg = env->NewStringUTF(msg);
            env->CallVoidMethod(callback, on_error_method, jmsg);
            if (jmsg) env->DeleteLocalRef(jmsg);
        }
    };

    if (!context->ctx_vision || !context->ctx || !context->model) {
        send_error("Vision projector not loaded — call loadProjector() first");
        return;
    }

    {
        std::lock_guard<std::mutex> lock(context->mutex);
        if (context->is_generating) { send_error("Engine already generating"); return; }
        context->is_generating = true;
        context->should_stop   = false;
    }

    try {
        // Decode JPEG → mtmd_bitmap using the stb_image helper
        jsize img_len  = env->GetArrayLength(image_bytes);
        jbyte* img_ptr = env->GetByteArrayElements(image_bytes, nullptr);
        mtmd_bitmap* bitmap = mtmd_helper_bitmap_init_from_buf(
            context->ctx_vision,
            reinterpret_cast<const unsigned char*>(img_ptr),
            static_cast<size_t>(img_len)
        );
        env->ReleaseByteArrayElements(image_bytes, img_ptr, JNI_ABORT);

        if (!bitmap) {
            LOGE("generateWithVision: failed to decode image");
            send_error("Failed to decode image — unsupported format?");
            std::lock_guard<std::mutex> lock(context->mutex);
            context->is_generating = false;
            return;
        }

        // Build prompt: insert media marker before the existing prompt text.
        // Format: "<marker>\n<prompt>" — vision model sees image then question.
        const char* prompt_str = env->GetStringUTFChars(prompt_js, nullptr);
        std::string full_prompt = std::string(mtmd_default_marker()) + "\n" + std::string(prompt_str ? prompt_str : "");
        if (prompt_str) env->ReleaseStringUTFChars(prompt_js, prompt_str);

        // Tokenize text + image together
        mtmd_input_chunks* chunks = mtmd_input_chunks_init();
        const mtmd_bitmap* bitmaps[] = { bitmap };
        mtmd_input_text input_text;
        input_text.text          = full_prompt.c_str();
        input_text.add_special   = true;
        input_text.parse_special = false;

        int32_t tok_result = mtmd_tokenize(context->ctx_vision, chunks, &input_text, bitmaps, 1);
        mtmd_bitmap_free(bitmap);

        if (tok_result != 0) {
            LOGE("generateWithVision: mtmd_tokenize failed (%d)", tok_result);
            mtmd_input_chunks_free(chunks);
            send_error("Vision tokenisation failed");
            std::lock_guard<std::mutex> lock(context->mutex);
            context->is_generating = false;
            return;
        }

        // Clear KV cache before evaluating
        llama_memory_clear(llama_get_memory(context->ctx), false);

        // Evaluate all chunks (text + image embeddings) in one helper call
        llama_pos n_past = 0;
        int32_t n_batch  = std::max(1, static_cast<int>(llama_n_batch(context->ctx)));
        int32_t eval_res = mtmd_helper_eval_chunks(
            context->ctx_vision,
            context->ctx,
            chunks,
            0,     // n_past start
            0,     // seq_id
            n_batch,
            true,  // logits_last
            &n_past
        );
        mtmd_input_chunks_free(chunks);

        if (eval_res != 0) {
            LOGE("generateWithVision: eval_chunks failed (%d)", eval_res);
            send_error("Vision evaluation failed");
            std::lock_guard<std::mutex> lock(context->mutex);
            context->is_generating = false;
            return;
        }
        LOGI("generateWithVision: chunks evaluated, n_past=%d, starting generation", (int)n_past);

        // Autoregressive token generation (identical to text-only path)
        const llama_vocab* vocab = llama_model_get_vocab(context->model);
        llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
        llama_sampler* chain = llama_sampler_chain_init(sparams);
        llama_sampler_chain_add(chain, llama_sampler_init_penalties(penalty_last_n, repeat_penalty, 0.0f, 0.0f));
        llama_sampler_chain_add(chain, llama_sampler_init_top_k(top_k));
        llama_sampler_chain_add(chain, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(chain, llama_sampler_init_dist(
            static_cast<uint32_t>(std::chrono::steady_clock::now().time_since_epoch().count())));

        llama_batch batch = llama_batch_init(1, 0, 1);
        std::string utf8_buffer;
        int n_generated = 0;

        while (n_generated < max_tokens) {
            if (context->should_stop) break;

            llama_token new_token = llama_sampler_sample(chain, context->ctx, -1);
            if (llama_vocab_is_eog(vocab, new_token)) break;

            char token_str[1024];
            int n = llama_token_to_piece(vocab, new_token, token_str, sizeof(token_str), 0, false);
            if (n > 0) {
                utf8_buffer.append(token_str, token_str + std::min(n, (int)sizeof(token_str)));
                std::string safe_piece = drain_valid_utf8(utf8_buffer);
                if (!safe_piece.empty()) {
                    jstring tok_obj = env->NewStringUTF(safe_piece.c_str());
                    if (tok_obj && !env->ExceptionCheck()) {
                        env->CallVoidMethod(callback, on_token_method, tok_obj);
                        env->DeleteLocalRef(tok_obj);
                    }
                }
            }

            batch.n_tokens    = 1;
            batch.token[0]    = new_token;
            batch.pos[0]      = n_past + n_generated;
            batch.n_seq_id[0] = 1;
            batch.seq_id[0][0] = 0;
            batch.logits[0]   = true;

            if (llama_decode(context->ctx, batch) != 0) {
                LOGE("generateWithVision: llama_decode failed at token %d", n_generated);
                break;
            }
            llama_sampler_accept(chain, new_token);
            n_generated++;
        }

        llama_batch_free(batch);
        llama_sampler_free(chain);
        LOGI("generateWithVision: complete — %d tokens generated", n_generated);
        env->CallVoidMethod(callback, on_complete_method);

    } catch (const std::exception& ex) {
        LOGE("generateWithVision: exception: %s", ex.what());
        send_error(ex.what());
    } catch (...) {
        LOGE("generateWithVision: unknown exception");
        send_error("Unknown native exception");
    }

    {
        std::lock_guard<std::mutex> lock(context->mutex);
        context->is_generating = false;
    }
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
    if (!ensure_backend_initialized("")) {
        LOGE("getModelMetadata: backend initialization failed");
        return nullptr;
    }

    const char* model_path = env->GetStringUTFChars(path, nullptr);
    if (!model_path) return nullptr;

    llama_model_params mparams = llama_model_default_params();
    mparams.vocab_only = true;
    mparams.n_gpu_layers = 0;
    mparams.use_mlock = false;
    mparams.use_mmap = false;

    llama_model* model = llama_model_load_from_file(model_path, mparams);
    env->ReleaseStringUTFChars(path, model_path);
    if (!model) {
        LOGE("getModelMetadata: failed to open model file");
        return nullptr;
    }

    jmethodID ctor = env->GetMethodID(g_ModelMetadataClass, "<init>", "(IIIIIIJJLjava/lang/String;Ljava/util/Map;)V");
    jmethodID mapCtor = env->GetMethodID(g_HashMapClass, "<init>", "()V");
    if (!ctor || !mapCtor || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        llama_model_free(model);
        return nullptr;
    }

    char desc_buf[256] = {0};
    llama_model_desc(model, desc_buf, sizeof(desc_buf));
    jstring model_desc = env->NewStringUTF(desc_buf);
    jobject hparams = env->NewObject(g_HashMapClass, mapCtor);
    if (!model_desc || !hparams || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (model_desc) env->DeleteLocalRef(model_desc);
        if (hparams) env->DeleteLocalRef(hparams);
        llama_model_free(model);
        return nullptr;
    }

    const llama_vocab* vocab = llama_model_get_vocab(model);
    const jint token_count = vocab ? llama_vocab_n_tokens(vocab) : 0;

    jobject result = env->NewObject(
        g_ModelMetadataClass, ctor,
        llama_model_n_layer(model), llama_model_n_embd(model), llama_model_n_head(model), llama_model_n_head_kv(model),
        llama_model_n_ctx_train(model), token_count,
        (jlong)llama_model_n_params(model), (jlong)llama_model_size(model), model_desc, hparams
    );
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        result = nullptr;
    }

    env->DeleteLocalRef(model_desc);
    env->DeleteLocalRef(hparams);
    llama_model_free(model);
    return result;
}

// FIX 1: Implement loadDraftModel JNI body
JNIEXPORT jboolean JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_loadDraftModel(
    JNIEnv* env, jobject,
    jstring modelPath,
    jint nCtx,
    jint nThreads
) {
    // 1. Convert jstring to std::string
    const char* model_path_chars = env->GetStringUTFChars(modelPath, nullptr);
    if (!model_path_chars) return JNI_FALSE;
    std::string path(model_path_chars);
    env->ReleaseStringUTFChars(modelPath, model_path_chars);

    if (!ensure_backend_initialized("")) {
        return JNI_FALSE;
    }

    // 2. Load with llama_load_model_from_file()
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // Use minimal params (small draft model)
    g_draft_model = llama_model_load_from_file(path.c_str(), model_params);

    if (!g_draft_model) return JNI_FALSE;

    // 3. Create context with llama_new_context_with_model()
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = nCtx; // nCtx = nCtx param
    ctx_params.n_threads = nThreads; // n_threads = nThreads param
    ctx_params.n_threads_batch = nThreads;

    g_draft_context = llama_init_from_model(g_draft_model, ctx_params);

    if (!g_draft_context) {
        llama_model_free(g_draft_model);
        g_draft_model = nullptr;
        return JNI_FALSE;
    }

    // 4. Store in g_draft_model, g_draft_context (done)
    // 5. Set g_draft_loaded = true
    g_draft_loaded = true;

    // 6. Return JNI_TRUE on success, JNI_FALSE on fail
    return JNI_TRUE;
}

// FIX 1: Implement unloadDraftModel JNI body
JNIEXPORT void JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_unloadDraftModel(
    JNIEnv* env, jobject
) {
    // Free g_draft_context
    if (g_draft_context) {
        llama_free(g_draft_context);
        g_draft_context = nullptr;
    }
    // Free g_draft_model
    if (g_draft_model) {
        llama_model_free(g_draft_model);
        g_draft_model = nullptr;
    }
    // Set g_draft_loaded = false
    g_draft_loaded = false;
}

// FIX 1: Implement generateSpeculative JNI body
JNIEXPORT void JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_generateSpeculative(
    JNIEnv* env, jobject obj,
    jlong context_ptr,
    jstring prompt,
    jobjectArray stop_tokens,
    jint maxTokens,
    jint nDraft,
    jobject tokenCallback
) {
    auto* context = reinterpret_cast<LlamaContext*>(context_ptr);

    // SAFETY CHECK:
    // If g_draft_loaded == false OR g_draft_context == nullptr:
    if (!g_draft_loaded || !g_draft_context || !context || !context->ctx) {
        // → call normal generate() and return
        // → do NOT crash
        Java_com_localmind_app_llm_nativelib_LlamaCppBridge_generate(
            env, obj, context_ptr, prompt, stop_tokens,
            (jfloat)0.7f,          // temperature
            (jfloat)0.9f,          // top_p
            (jint)40,              // top_k
            (jfloat)1.0f,          // repeat_penalty
            (jint)64,              // penalty_last_n
            (jint)maxTokens,       // max_tokens
            (jboolean)JNI_FALSE,   // should_update_cache
            (jboolean)JNI_TRUE,    // cache_prompt
            (jfloat)0.1f,          // defrag_threshold
            (jfloat)0.05f,         // min_p
            (jint)-1,              // seed
            (jfloat)0.1f,          // xtc_threshold
            (jfloat)0.0f,          // xtc_probability
            (jfloat)1.0f,          // typical_p
            (jfloat)0.0f,          // penalty_freq
            (jfloat)0.0f,          // penalty_present
            (jint)0,               // mirostat
            (jfloat)5.0f,          // mirostat_tau
            (jfloat)0.1f,          // mirostat_eta
            tokenCallback
        );
        return;
    }

    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    jclass callback_class = env->GetObjectClass(tokenCallback);
    jmethodID on_token_method = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V");
    jmethodID on_complete_method = env->GetMethodID(callback_class, "onComplete", "()V");

    std::vector<std::string> stop_words;
    if (stop_tokens != nullptr) {
        jsize len = env->GetArrayLength(stop_tokens);
        for (jsize i = 0; i < len; i++) {
            jstring stop_token = (jstring)env->GetObjectArrayElement(stop_tokens, i);
            if (stop_token) {
                const char* stop_chars = env->GetStringUTFChars(stop_token, nullptr);
                if (stop_chars) {
                    stop_words.push_back(std::string(stop_chars));
                    env->ReleaseStringUTFChars(stop_token, stop_chars);
                }
                env->DeleteLocalRef(stop_token);
            }
        }
    }

    // SPECULATIVE LOOP:
    // 1. Tokenize prompt into token array
    std::vector<llama_token> tokens;
    tokens.resize(strlen(prompt_str) + 512);
    const llama_vocab* vocab = llama_model_get_vocab(context->model);
    int n_tokens = llama_tokenize(vocab, prompt_str, strlen(prompt_str), tokens.data(), tokens.size(), true, false);
    tokens.resize(n_tokens);

    // Initial prompt
    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    for(int i=0; i<n_tokens; i++) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == n_tokens - 1);
    }
    batch.n_tokens = n_tokens;
    llama_decode(context->ctx, batch);
    llama_decode(g_draft_context, batch);
    llama_batch_free(batch);

    std::string utf8_buffer;
    int n_generated = 0;
    int current_pos = n_tokens;

    int n_vocab = llama_vocab_n_tokens(vocab);
    float* logits0 = llama_get_logits_ith(context->ctx, 0); // Note: API Name differs: llama_get_logits_ith
    llama_token current_token = std::max_element(logits0, logits0 + n_vocab) - logits0;

    // 2. While total_generated < maxTokens:
    while (n_generated < maxTokens) {
        if (context->should_stop) break;

        // DRAFT PHASE:
        std::vector<llama_token> draft_tokens;
        draft_tokens.push_back(current_token); // The known good token as base

        llama_batch d_batch = llama_batch_init(1, 0, 1);
        int d_pos = current_pos;
        llama_token d_tok = current_token;

        // - Run g_draft_context for nDraft steps
        for (int i = 0; i < nDraft; i++) {
            d_batch.n_tokens = 1;
            d_batch.token[0] = d_tok;
            d_batch.pos[0] = d_pos;
            d_batch.n_seq_id[0] = 1;
            d_batch.seq_id[0][0] = 0;
            d_batch.logits[0] = true;

            llama_decode(g_draft_context, d_batch);

            float* d_logits = llama_get_logits_ith(g_draft_context, 0);
            d_tok = std::max_element(d_logits, d_logits + n_vocab) - d_logits;

            if (llama_vocab_is_eog(vocab, d_tok)) {
                break; // stop generating draft
            }
            // - Save nDraft candidate tokens in vector
            draft_tokens.push_back(d_tok);
            d_pos++;
        }
        llama_batch_free(d_batch);
        // - Do NOT call callback yet (done)

        // VERIFY PHASE:
        // - Pass ALL nDraft tokens to g_context (main model)
        llama_batch v_batch = llama_batch_init(draft_tokens.size(), 0, 1);
        for (size_t i = 0; i < draft_tokens.size(); i++) {
            v_batch.token[i] = draft_tokens[i];
            v_batch.pos[i] = current_pos + i;
            v_batch.n_seq_id[i] = 1;
            v_batch.seq_id[i][0] = 0;
            v_batch.logits[i] = true;
        }
        v_batch.n_tokens = draft_tokens.size();

        // - Main model does ONE forward pass for all positions
        llama_decode(context->ctx, v_batch);

        // ACCEPT/REJECT:
        int n_accepted = 0;
        bool eos_found = false;

        for (size_t i = 0; i < draft_tokens.size(); i++) {
            // - Get main model logits at each position
            float* logits = llama_get_logits_ith(context->ctx, i);
            llama_token m_token = std::max_element(logits, logits + n_vocab) - logits;

            // CALLBACK:
            // - Call tokenCallback for each accepted token
            char token_str[1024];
            int n = llama_token_to_piece(vocab, draft_tokens[i], token_str, sizeof(token_str), 0, false);
            if (n > 0) {
                utf8_buffer.append(token_str, token_str + std::min(n, (int)sizeof(token_str)));
                std::string safe_piece = drain_valid_utf8(utf8_buffer);
                if (!safe_piece.empty()) {
                    jstring token_obj = env->NewStringUTF(safe_piece.c_str());
                    if (token_obj && !env->ExceptionCheck()) {
                        env->CallVoidMethod(tokenCallback, on_token_method, token_obj);
                        env->DeleteLocalRef(token_obj);
                    }
                }
            }
            n_accepted++;

            // - Stop if EOS token accepted
            if (llama_vocab_is_eog(vocab, draft_tokens[i])) {
                eos_found = true;
                break;
            }

            // - Compare draft token[i] vs main model argmax[i]
            if (i < draft_tokens.size() - 1) {
                // - Accept tokens until first mismatch
                if (m_token != draft_tokens[i+1]) {
                    // - On mismatch: use main model token at that position
                    // - Reject remaining draft tokens after mismatch
                    current_token = m_token;
                    break;
                }
            } else {
                current_token = m_token;
            }
        }

        llama_batch_free(v_batch);
        n_generated += n_accepted;

        if (eos_found) break; // 3. End loop when maxTokens reached or EOS found
            if (llama_vocab_is_eog(vocab, current_token)) {
                // ... (handling EOS)
                break;
            }

            // Stop Word Check (Pre-parsed for performance)
            if (context->should_stop) break;
            for (const auto& sw : stop_words) {
                if (utf8_buffer.size() >= sw.size()) {
                    if (utf8_buffer.compare(utf8_buffer.size() - sw.size(), sw.size(), sw) == 0) {
                        LOGI("Stop token reached (speculative): %s", sw.c_str());
                        context->should_stop = true;
                        break;
                    }
                }
            }
            if (context->should_stop) break;

        // SYNC:
        // - Advance both contexts to accepted position
        current_pos += n_accepted;

        // - Clear draft context beyond accepted point (API Name Note: llama_memory_seq_rm is used)
        llama_memory_seq_rm(llama_get_memory(context->ctx), 0, current_pos, -1);
        llama_memory_seq_rm(llama_get_memory(g_draft_context), 0, current_pos, -1);
    }

    env->CallVoidMethod(tokenCallback, on_complete_method);
    env->ReleaseStringUTFChars(prompt, prompt_str);
}

// ============================================================
// POCKETPAL PARITY: generateWithMessages — Native Chat Template
// ============================================================
// PocketPal: context.completion({ messages: [...], jinja: true })
// llama.rn C++ side mein llama_chat_apply_template() call hota hai.
// Hum bhi same karte hain: Kotlin se structured JSON messages bhejo,
// C++ side pe model ka built-in chat template apply karo,
// phir generated prompt se normal generate() flow chalao.
// Benefits:
//   1. Model-native template — exact same tokens jo GGUF ke andar hain
//   2. Prefix cache stable — template consistent hai across turns
//   3. Stop tokens auto-detect — native EOS/EOT tokens se
//   4. Kotlin side ka PromptTemplateEngine bypass — ek source of truth
// JSON format: [{"role":"system","content":"..."},{"role":"user","content":"..."},...]

JNIEXPORT void JNICALL Java_com_localmind_app_llm_nativelib_LlamaCppBridge_generateWithMessages(
    JNIEnv* env, jobject thiz, jlong context_ptr, jstring messages_json, jobjectArray stop_tokens,
    jfloat temperature, jfloat top_p, jint top_k, jfloat repeat_penalty, jint penalty_last_n,
    jint max_tokens, jboolean should_update_cache, jboolean cache_prompt, jfloat defrag_threshold,
    jfloat min_p, jint seed,
    jfloat xtc_threshold, jfloat xtc_probability,
    jfloat typical_p,
    jfloat penalty_freq, jfloat penalty_present,
    jint mirostat, jfloat mirostat_tau, jfloat mirostat_eta,
    jobject callback) {

    if (context_ptr == 0) return;
    auto* context = reinterpret_cast<LlamaContext*>(context_ptr);

    // ── Step 1: Parse JSON messages ───────────────────────────────────────────
    const char* json_chars = env->GetStringUTFChars(messages_json, nullptr);
    if (!json_chars) return;
    std::string json_str(json_chars);
    env->ReleaseStringUTFChars(messages_json, json_chars);

    // Lightweight JSON parser — no external deps needed.
    // Format: [{"role":"...","content":"..."}, ...]
    struct ChatMsg { std::string role; std::string content; };
    std::vector<ChatMsg> parsed_messages;

    // Parse role/content pairs from JSON array
    size_t pos = 0;
    while (pos < json_str.size()) {
        size_t obj_start = json_str.find('{', pos);
        if (obj_start == std::string::npos) break;
        size_t obj_end = json_str.find('}', obj_start);
        if (obj_end == std::string::npos) break;
        std::string obj = json_str.substr(obj_start, obj_end - obj_start + 1);
        pos = obj_end + 1;

        auto extract = [&](const std::string& key) -> std::string {
            std::string search = "\"" + key + "\"";
            size_t kpos = obj.find(search);
            if (kpos == std::string::npos) return "";
            size_t colon = obj.find(':', kpos + search.size());
            if (colon == std::string::npos) return "";
            size_t val_start = obj.find('"', colon + 1);
            if (val_start == std::string::npos) return "";
            val_start++;
            // Handle escaped quotes in content
            std::string result;
            bool escaped = false;
            for (size_t i = val_start; i < obj.size(); i++) {
                char c = obj[i];
                if (escaped) {
                    if (c == 'n') result += '\n';
                    else if (c == 't') result += '\t';
                    else if (c == 'r') result += '\r';
                    else result += c;
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    break;
                } else {
                    result += c;
                }
            }
            return result;
        };

        ChatMsg msg;
        msg.role = extract("role");
        msg.content = extract("content");
        if (!msg.role.empty() && !msg.content.empty()) {
            parsed_messages.push_back(std::move(msg));
        }
    }

    if (parsed_messages.empty()) {
        LOGE("generateWithMessages: no messages parsed from JSON");
        return;
    }

    // ── Step 2: Build llama_chat_message vector ───────────────────────────────
    // llama_chat_message is defined in llama.h C interface:
    //   typedef struct llama_chat_message { const char* role; const char* content; } llama_chat_message;
    // We use pointers into parsed_messages strings (which remain alive for the duration).
    std::vector<llama_chat_message> chat_msgs;
    chat_msgs.reserve(parsed_messages.size());
    for (const auto& m : parsed_messages) {
        chat_msgs.push_back({ m.role.c_str(), m.content.c_str() });
    }

    // chat_ptrs not needed — public API takes flat array directly

    // ── Step 3: Auto-detect template from model metadata ─────────────────────
    // llama_model_chat_template() reads "tokenizer.chat_template" from GGUF metadata.
    // This is the EXACT same function PocketPal/llama.rn uses with jinja=true.
    const char* raw_tmpl = llama_model_chat_template(context->model, /*name=*/nullptr);
    std::string tmpl_str = raw_tmpl ? raw_tmpl : "";

    // ── Step 4: Apply chat template → final prompt string ────────────────────
    // Public API signature (llama.h):
    //   int32_t llama_chat_apply_template(const char* tmpl,
    //       const llama_chat_message* chat, size_t n_msg,
    //       bool add_ass, char* buf, int32_t length)
    // NOTE: first arg is const char* tmpl (NOT llama_model*)
    // tmpl=nullptr → use built-in chatml default
    std::string final_prompt;
    const char* tmpl_ptr = tmpl_str.empty() ? nullptr : tmpl_str.c_str();

    // Dry-run: buf=nullptr, length=0 → returns required buffer size
    int32_t needed = llama_chat_apply_template(
        tmpl_ptr,
        chat_msgs.data(),
        chat_msgs.size(),
        /*add_ass=*/true,
        nullptr,
        0
    );

    if (needed < 0) {
        LOGE("generateWithMessages: llama_chat_apply_template (size probe) failed (%d), falling back to raw content", needed);
        for (int i = (int)parsed_messages.size() - 1; i >= 0; i--) {
            if (parsed_messages[i].role == "user") {
                final_prompt = parsed_messages[i].content;
                break;
            }
        }
    } else {
        final_prompt.resize(needed + 1); // +1 safety margin
        int32_t result = llama_chat_apply_template(
            tmpl_ptr,
            chat_msgs.data(),
            chat_msgs.size(),
            /*add_ass=*/true,
            &final_prompt[0],
            (int32_t)final_prompt.size()
        );
        if (result < 0) {
            LOGE("generateWithMessages: llama_chat_apply_template failed (%d), falling back to raw content", result);
            final_prompt.clear();
            for (int i = (int)parsed_messages.size() - 1; i >= 0; i--) {
                if (parsed_messages[i].role == "user") {
                    final_prompt = parsed_messages[i].content;
                    break;
                }
            }
        } else {
            final_prompt.resize(result); // trim to actual written length
            LOGI("generateWithMessages: Template applied (%s), prompt_len=%zu",
                 tmpl_str.empty() ? "built-in default" : "from GGUF metadata",
                 final_prompt.size());
        }
    }

    LOGI("generateWithMessages: template applied, prompt_len=%zu", final_prompt.size());

    // ── Step 5: Convert final_prompt to jstring and call existing generate() ──
    // Reuse ALL existing generate() logic — prefill, KV cache, sampling, streaming, stop tokens.
    // This is the cleanest approach: zero code duplication.
    jstring prompt_jstr = env->NewStringUTF(final_prompt.c_str());
    if (!prompt_jstr) {
        LOGE("generateWithMessages: Failed to create jstring for prompt");
        return;
    }

    // Delegate to existing generate() function — same thread, same context, same callback
    Java_com_localmind_app_llm_nativelib_LlamaCppBridge_generate(
        env, thiz, context_ptr, prompt_jstr, stop_tokens,
        temperature, top_p, top_k, repeat_penalty, penalty_last_n,
        max_tokens, should_update_cache, cache_prompt, defrag_threshold,
        min_p, seed,
        xtc_threshold, xtc_probability,
        typical_p,
        penalty_freq, penalty_present,
        mirostat, mirostat_tau, mirostat_eta,
        callback
    );

    env->DeleteLocalRef(prompt_jstr);
}

} // extern "C"
