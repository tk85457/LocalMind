package com.localmind.app.data.mapper

import com.localmind.app.data.local.entity.ModelEntity
import com.localmind.app.domain.model.Model
import java.io.File

fun ModelEntity.toDomain(): Model {
    val normalizedFileName = if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
        filePath?.let { File(it).name } ?: fileName
    } else {
        fileName
    }

    return Model(
        id = id,
        name = name,
        filePath = filePath,
        sizeBytes = sizeBytes,
        quantization = quantization,
        contextLength = contextLength,
        parameterCount = parameterCount,
        installDate = installDate,
        isActive = isActive,
        lastUsed = lastUsed,
        storageType = storageType,
        storageUri = storageUri,
        fileName = normalizedFileName.ifBlank { "model.gguf" },
        templateId = templateId,
        stopTokensJson = stopTokensJson,
        recommendedTemperature = recommendedTemperature,
        recommendedTopP = recommendedTopP,
        recommendedTopK = recommendedTopK,
        recommendedRepeatPenalty = recommendedRepeatPenalty,
        recommendedSystemPrompt = recommendedSystemPrompt,
        supportsVision = supportsVision,
        bosEnabled = bosEnabled,
        eosEnabled = eosEnabled,
        addGenPrompt = addGenPrompt
    )
}

fun Model.toEntity(): ModelEntity {
    return ModelEntity(
        id = id,
        name = name,
        filePath = filePath,
        sizeBytes = sizeBytes,
        quantization = quantization,
        contextLength = contextLength,
        parameterCount = parameterCount,
        installDate = installDate,
        isActive = isActive,
        lastUsed = lastUsed,
        storageType = storageType,
        storageUri = storageUri,
        fileName = fileName,
        templateId = templateId,
        stopTokensJson = stopTokensJson,
        recommendedTemperature = recommendedTemperature,
        recommendedTopP = recommendedTopP,
        recommendedTopK = recommendedTopK,
        recommendedRepeatPenalty = recommendedRepeatPenalty,
        recommendedSystemPrompt = recommendedSystemPrompt,
        supportsVision = supportsVision,
        bosEnabled = bosEnabled,
        eosEnabled = eosEnabled,
        addGenPrompt = addGenPrompt
    )
}
