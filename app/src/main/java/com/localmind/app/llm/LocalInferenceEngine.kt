package com.localmind.app.llm

import com.localmind.app.domain.model.InferenceSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalInferenceEngine @Inject constructor(
    private val llmEngine: LLMEngine
) : ChatInferenceEngine {
    override val source: InferenceSource = InferenceSource.LOCAL

    override fun generate(
        prompt: String,
        config: InferenceConfig,
        shouldUpdateCache: Boolean,
        remoteModelOverride: String?
    ): Flow<GenerationResult> {
        return llmEngine.generate(prompt, config, shouldUpdateCache)
    }
}
