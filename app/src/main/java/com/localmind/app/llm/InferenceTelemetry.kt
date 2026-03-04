package com.localmind.app.llm

import com.localmind.app.domain.model.InferenceSource

data class InferenceTelemetry(
    val source: InferenceSource,
    val ttftMs: Long? = null,
    val totalTimeMs: Long = 0L,
    val tokensGenerated: Int = 0,
    val tokensPerSecond: Float = 0f
)
