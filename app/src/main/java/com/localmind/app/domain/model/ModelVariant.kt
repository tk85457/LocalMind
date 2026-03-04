package com.localmind.app.domain.model

enum class ModelVariantSizeSource {
    EXACT,
    ESTIMATED
}

data class ModelVariant(
    val filename: String,
    val quantization: String,
    val sizeBytes: Long,
    val sizeSource: ModelVariantSizeSource = ModelVariantSizeSource.EXACT,
    val estimatedRamBytes: Long,
    val memoryTier: ModelMemoryTier = ModelMemoryTier.GOOD,
    val compatible: Boolean,
    val compatReason: String? = null,
    val compatFixTips: List<String> = emptyList(),
    val rank: Int = 0
)
