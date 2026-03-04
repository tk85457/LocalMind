package com.localmind.app.llm

data class InferenceConfig(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val repeatPenalty: Float = 1.1f,
    val maxTokens: Int = 1024, // Sane default — large values slow down prefill
    val contextSize: Int = 2048, // Match Constants.DEFAULT_CONTEXT_SIZE
    val threadCount: Int = 4, // CAP at 4 to prevent efficiency core hopping
    val topK: Int = 40,
    val gpuLayers: Int = -1, // PERF FIX: -1 = Auto GPU. Was 0 (CPU only) = 5-10x slower
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
