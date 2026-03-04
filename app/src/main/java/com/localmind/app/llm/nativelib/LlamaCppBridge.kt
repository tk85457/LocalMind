package com.localmind.app.llm.nativelib

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
        threadCount: Int,
        gpuLayers: Int,
        batchSize: Int,
        physicalBatchSize: Int,
        flashAttention: String,
        keyCacheType: String,
        valueCacheType: String
    ): Long

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
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        maxTokens: Int,
        shouldUpdateCache: Boolean,
        callback: GenerationCallback
    )

    /**
     * Stop ongoing generation
     * @param contextPtr Context pointer from loadModel()
     */
    external fun stopGeneration(contextPtr: Long)

    /**
     * Unload model and free native memory
     * @param contextPtr Context pointer from loadModel()
     */
    external fun unloadModel(contextPtr: Long)



    /**
     * Get high-fidelity performance metrics from the native context
     * @param contextPtr Context pointer from loadModel()
     * @return PerfMetrics object containing timing and token stats
     */
    external fun getPerfMetrics(contextPtr: Long): com.localmind.app.core.engine.PerfMetrics?

    /**
     * Load metadata from a GGUF model file without loading weights.
     * Used for memory estimation and model information.
     * @param path Absolute path to the .gguf model file
     * @return ModelMetadata object or null if failed
     */
    external fun getModelMetadata(path: String): com.localmind.app.core.engine.ModelMetadata?
}

/**
 * Callback interface for streaming token generation
 */
interface GenerationCallback {
    /**
     * Called for each generated token
     * @param token The generated token text
     */
    fun onToken(token: String)

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
