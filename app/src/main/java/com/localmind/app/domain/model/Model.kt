package com.localmind.app.domain.model

data class Model(
    val id: String,
    val name: String,
    val filePath: String?,
    val sizeBytes: Long,
    val quantization: String,
    val contextLength: Int,
    val parameterCount: String,
    val installDate: Long,
    val isActive: Boolean,
    val lastUsed: Long?,
    val storageType: String,
    val storageUri: String?,
    val fileName: String,
    val templateId: String,
    val stopTokensJson: String,
    val recommendedTemperature: Float,
    val recommendedTopP: Float,
    val recommendedTopK: Int,
    val recommendedRepeatPenalty: Float,
    val recommendedSystemPrompt: String?,
    val supportsVision: Boolean = false,
    val supportsDocument: Boolean = false,
    val bosEnabled: Boolean = true,
    val eosEnabled: Boolean = true,
    val addGenPrompt: Boolean = true
) {
    val sizeMB: Double
        get() = sizeBytes / (1024.0 * 1024.0)

    val sizeGB: Double
        get() = sizeBytes / (1024.0 * 1024.0 * 1024.0)

    val formattedSize: String
        get() = when {
            sizeGB >= 1.0 -> String.format("%.2f GB", sizeGB)
            else -> String.format("%.2f MB", sizeMB)
        }
}
