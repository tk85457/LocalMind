package com.localmind.app.llm

/**
 * Inference configuration — PocketPal completionSettingsVersions.ts v3 ke saath fully synced.
 * Saare parameters same defaults use karte hain jaise PocketPal AI.
 */
data class InferenceConfig(
    // ── Core sampling params ──
    val temperature: Float = 0.7f,          // PocketPal: temperature = 0.7
    val topK: Int = 40,                     // PocketPal: top_k = 40
    val topP: Float = 0.95f,               // PocketPal: top_p = 0.95 (was 0.9)
    val minP: Float = 0.05f,               // PocketPal: min_p = 0.05
    val xtcThreshold: Float = 0.1f,        // PocketPal: xtc_threshold = 0.1
    val xtcProbability: Float = 0.0f,      // PocketPal: xtc_probability = 0.0 (disabled)
    val typicalP: Float = 1.0f,            // PocketPal: typical_p = 1.0 (disabled)
    // ── Repetition penalty params ──
    val repeatPenalty: Float = 1.0f,       // PocketPal: penalty_repeat = 1.0 (was 1.1)
    val penaltyLastN: Int = 64,            // PocketPal: penalty_last_n = 64
    val penaltyFreq: Float = 0.0f,         // PocketPal: penalty_freq = 0.0
    val penaltyPresent: Float = 0.0f,      // PocketPal: penalty_present = 0.0
    // ── Mirostat params ──
    val mirostat: Int = 0,                 // PocketPal: mirostat = 0 (off)
    val mirostatTau: Float = 5.0f,         // PocketPal: mirostat_tau = 5.0
    val mirostatEta: Float = 0.1f,         // PocketPal: mirostat_eta = 0.1
    // ── Generation control ──
    val maxTokens: Int = 1024,             // PocketPal: n_predict = 1024
    val nProbs: Int = 0,                   // PocketPal: n_probs = 0
    val seed: Int = -1,                    // PocketPal: seed = -1 (random)
    // ── Template / thinking ──
    val jinja: Boolean = true,             // PocketPal: jinja = true
    val enableThinking: Boolean = true,    // PocketPal: enable_thinking = true
    val includeThinkingInContext: Boolean = true, // PocketPal: include_thinking_in_context = true
    // ── Context / hardware params ──
    val contextSize: Int = 2048,
    val threadCount: Int = 4,
    val gpuLayers: Int = -1,               // -1 = Auto GPU
    // ── Misc ──
    val stopTokens: List<String> = emptyList(),
    val documentBytes: ByteArray? = null,
    val documentUri: String? = null
) {
    companion object {
        val DEFAULT = InferenceConfig()

        fun forDevice(ramGB: Int): InferenceConfig {
            return when {
                // PERF: 4 threads minimum on ALL devices — matches DeviceProfileManager.
                // Old value of 2 was halving speed on 8-core SoCs.
                ramGB <= 3 -> DEFAULT.copy(contextSize = 512,  threadCount = 4, maxTokens = 512,  topK = 40)
                ramGB <= 4 -> DEFAULT.copy(contextSize = 2048, threadCount = 4, maxTokens = 1024, topK = 40)
                ramGB <= 6 -> DEFAULT.copy(contextSize = 4096, threadCount = 4, maxTokens = 2048, topK = 40)
                // PERF FIX: Cap threads at 4 even on high RAM to keep work on Big cores.
                // maxTokens 8192 on 12GB is fine but keep contextSize reasonable.
                ramGB >= 12 -> DEFAULT.copy(contextSize = 8192, threadCount = 4, maxTokens = 4096, topK = 48)
                else        -> DEFAULT.copy(contextSize = 4096, threadCount = 4, maxTokens = 2048, topK = 40)
            }
        }
    }
}
