package com.localmind.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey
    val id: String, // UUID
    val name: String,
    val filePath: String?,
    val sizeBytes: Long,
    val quantization: String, // Q4, Q5, Q8
    val contextLength: Int,
    val parameterCount: String, // e.g., "7B", "13B"
    val installDate: Long, // timestamp
    val isActive: Boolean = false,
    val lastUsed: Long? = null,
    val storageType: String,
    val storageUri: String?,
    val fileName: String,
    val templateId: String = "chatml_default",
    val stopTokensJson: String = "[]",
    val recommendedTemperature: Float = 0.7f,
    val recommendedTopP: Float = 0.9f,
    val recommendedTopK: Int = 40,
    val recommendedRepeatPenalty: Float = 1.1f,
    val recommendedSystemPrompt: String? = null,
    val supportsVision: Boolean = false,
    val bosEnabled: Boolean = true,
    val eosEnabled: Boolean = true,
    val addGenPrompt: Boolean = true
)
