package com.tk854.localmind.llm.nativelib

/**
 * JNI Bridge to llama.cpp native library
 *
 * This class provides the Kotlin interface to the native C++ layer.
 * All native methods are crash-safe and wrapped with proper error handling.
 */
class LlamaCppBridge {

    companion object {
        init {
            System.loadLibrary("localmind")
        }
    }

    /**
     * Load a GGUF model from file path with configuration
     * @param path Absolute path to the .gguf model file
     * @param useMlock Lock model in RAM (prevent swapping)
     * @param useMmap Use memory mapping (faster load, less RAM copy)
     * @param contextSize Context window size (e.g. 2048, 4096)
     * @param threadCount Number of threads for computation
     * @return Context pointer (handle) to the loaded model, or 0 if failed
     */
    external fun loadModel(
        path: String,
        useMlock: Boolean,
        useMmap: Boolean,
        contextSize: Int,
        threadCountDecode: Int,
        threadCountPrefill: Int,
        gpuLayers: Int,
        batchSize: Int,
        physicalBatchSize: Int,
        flashAttention: Boolean,
        keyCacheType: String,
        valueCacheType: String,
        defragThreshold: Float,
        // POCKETPAL FIX: kv_unified=true saves ~7GB memory on large context windows
        // PocketPal me ye CRITICAL default hai. Ye ek single unified KV cache
        // allocate karta hai baar baar allocations ki jagah.
        kvUnified: Boolean = true
    ): Long

    external fun loadDraftModel(
        modelPath: String,
        nCtx: Int,
        nThreads: Int
    ): Boolean

    external fun unloadDraftModel()

    external fun generateSpeculative(
        contextPtr: Long,
        prompt: String,
        stopTokens: Array<String>?,
        maxTokens: Int,
        nDraft: Int,
        callback: GenerationCallback
    )

    /**
     * Initialize GGML backends from app native library directory.
     * Must succeed before loading models when dynamic backend loading is enabled.
     */
    external fun initializeBackend(nativeLibDir: String): Boolean

    /**
     * Generate tokens from a prompt
     * @param contextPtr Context pointer from loadModel()
     * @param prompt Input prompt text
     * @param callback Callback interface for streaming tokens
     */
    /**
     * Generate tokens from a prompt
     * @param contextPtr Context pointer from loadModel()
     * @param prompt Input prompt text
     * @param callback Callback interface for streaming tokens
     */
    external fun generate(
        contextPtr: Long,
        prompt: String,
        stopTokens: Array<String>?,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        penaltyLastN: Int,
        maxTokens: Int,
        shouldUpdateCache: Boolean,
        cachePrompt: Boolean,
        defragThreshold: Float,
        minP: Float = 0.05f,
        seed: Int = -1,
        // PocketPal parity: new sampler params
        xtcThreshold: Float = 0.1f,
        xtcProbability: Float = 0.0f,
        typicalP: Float = 1.0f,
        penaltyFreq: Float = 0.0f,
        penaltyPresent: Float = 0.0f,
        mirostat: Int = 0,
        mirostatTau: Float = 5.0f,
        mirostatEta: Float = 0.1f,
        suppressThinkToken: Boolean = false,
        callback: GenerationCallback
    )

    /**
     * POCKETPAL PARITY: Generate using native chat template (messages API)
     *
     * Kotlin se structured messages JSON bhejo, C++ side pe model ka built-in
     * GGUF chat template auto-detect hokar apply hota hai  —  exactly like
     * PocketPal's context.completion({ messages: [...], jinja: true }).
     *
     * @param contextPtr Context pointer from loadModel()
     * @param messagesJson JSON array: [{"role":"system","content":"..."},{"role":"user","content":"..."},...]
     * @param callback Callback interface for streaming tokens
     */
    external fun generateWithMessages(
        contextPtr: Long,
        messagesJson: String,
        stopTokens: Array<String>?,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        penaltyLastN: Int,
        maxTokens: Int,
        shouldUpdateCache: Boolean,
        cachePrompt: Boolean,
        defragThreshold: Float,
        minP: Float = 0.05f,
        seed: Int = -1,
        xtcThreshold: Float = 0.1f,
        xtcProbability: Float = 0.0f,
        typicalP: Float = 1.0f,
        penaltyFreq: Float = 0.0f,
        penaltyPresent: Float = 0.0f,
        mirostat: Int = 0,
        mirostatTau: Float = 5.0f,
        mirostatEta: Float = 0.1f,
        suppressThinkToken: Boolean = false,
        // NOTE: jinja aur enableThinking C++ native function mein nahi hain.
        // Pehle ye Kotlin side mein declare the jisse JNI parameter mismatch
        // aur native crash hota tha (generate+740 offset crash).
        // C++ side mein jinja=true aur enableThinking model ke template se
        // auto-detect hota hai -- Kotlin se control nahi hota.
        callback: GenerationCallback
    )

    /**
     * Stop ongoing generation
     * @param contextPtr Context pointer from loadModel()
     */
    external fun stopGeneration(contextPtr: Long)

    /**
     * Clear prefix-cache tokens and KV memory for next prompt prefill.
     * Used after manual stop to prevent stale continuation bleed.
     */
    external fun clearPrefixCache(contextPtr: Long)

    /**
     * Unload model and free native memory
     * @param contextPtr Context pointer from loadModel()
     */
    external fun unloadModel(contextPtr: Long)

    /**
     * Load a vision projector (mmproj) file for multimodal/vision models.
     * Must be called after loadModel() with a vision-capable base model.
     * @param contextPtr Context pointer from loadModel()
     * @param projectorPath Absolute path to the mmproj .gguf file
     * @return true if projector loaded successfully, false otherwise
     */
    external fun loadProjector(contextPtr: Long, projectorPath: String): Boolean

    /**
     * Unload the vision projector and free its memory.
     * @param contextPtr Context pointer from loadModel()
     */
    external fun unloadProjector(contextPtr: Long)

    /**
     * Generate a response for a prompt that includes an image (vision/multimodal).
     * Requires loadProjector() to have been called first.
     * @param contextPtr Context pointer from loadModel()
     * @param prompt Text prompt accompanying the image
     * @param imageBytes Raw image bytes (JPEG/PNG)
     * @param callback Callback interface for streaming tokens
     */
    external fun generateWithVision(
        contextPtr: Long,
        prompt: String,
        imageBytes: ByteArray,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        penaltyLastN: Int,
        maxTokens: Int,
        callback: GenerationCallback
    )

    /**
     * Get high-fidelity performance metrics from the native context
     * @param contextPtr Context pointer from loadModel()
     * @return PerfMetrics object containing timing and token stats
     */
    external fun getPerfMetrics(contextPtr: Long): com.tk854.localmind.core.engine.PerfMetrics?

    /**
     * POCKETPAL PARITY: Get EOS token string from loaded model's GGUF metadata.
     * PocketPal: llama.rn ctx.detokenize([eos_token_id]) ke equivalent.
     * Returns null if no EOS token found or model not loaded.
     */
    external fun getEosToken(contextPtr: Long): String?

    /**
     * POCKETPAL PARITY: Get chat template string from loaded model's GGUF metadata.
     * Used to detect thinking-capable templates and extract stop tokens.
     * Returns null if no chat template found.
     */
    external fun getChatTemplate(contextPtr: Long): String?

    /**
     * AUTO STOP TOKEN DETECTION  —  fully dynamic, zero hardcoding.
     * Extracts stop tokens from 3 layers:
     *   1. Vocab special tokens (EOS + EOT IDs from llama_vocab API)
     *   2. Chat template scan (quoted markers like <|eot_id|>, <|im_end|> etc.)
     *
     * Returns a JSON array string, e.g. ["</s>","<|eot_id|>","<|im_end|>"]
     * Returns empty string (never null) if nothing found.
     *
     * Call this ONCE after model load (in updateAutoStopTokens).
     * All models  —  Llama-3, Qwen, Gemma, DeepSeek, Phi, Mistral, etc.  — 
     * are handled automatically without any hardcoded token lists.
     */
    external fun getStopTokensFromModel(contextPtr: Long): String

    /**
     * Load metadata from a GGUF model file without loading weights.
     * Used for memory estimation and model information.
     * @param path Absolute path to the .gguf model file
     * @return ModelMetadata object or null if failed
     */
    external fun getModelMetadata(path: String): com.tk854.localmind.core.engine.ModelMetadata?
}

/**
 * Callback interface for streaming token generation.
 * PocketPal-style: native layer separates reasoning from answer.
 * onToken = answer tokens (after </think>)
 * onReasoning = thinking tokens (inside <think>...</think>)
 */
interface GenerationCallback {
    /**
     * Called for each answer token (outside <think> blocks)
     * @param token The generated token text
     */
    fun onToken(token: String)

    /**
     * Called for each reasoning token (inside <think>...</think>)
     * Mirrors PocketPal's data.reasoning_content field.
     * Default empty impl for backward compatibility.
     */
    fun onReasoning(token: String) {}

    /**
     * Called when generation is complete
     */
    fun onComplete()

    /**
     * Called if generation fails
     * @param error Error message
     */
    fun onError(error: String) {}
}
