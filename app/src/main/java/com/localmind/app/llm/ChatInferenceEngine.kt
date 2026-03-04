package com.localmind.app.llm

import com.localmind.app.domain.model.InferenceSource
import kotlinx.coroutines.flow.Flow

interface ChatInferenceEngine {
    val source: InferenceSource
    fun generate(
        prompt: String,
        config: InferenceConfig,
        shouldUpdateCache: Boolean = true,
        remoteModelOverride: String? = null
    ): Flow<GenerationResult>
}
