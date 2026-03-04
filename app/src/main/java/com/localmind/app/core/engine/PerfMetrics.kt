package com.localmind.app.core.engine

/**
 * High-fidelity performance metrics from llama.cpp
 */
data class PerfMetrics(
    val promptEvalTimeMs: Double,
    val generationTimeMs: Double,
    val modelLoadTimeMs: Double,
    val tokensGenerated: Int
) {
    val tokensPerSecond: Double
        get() = if (generationTimeMs > 0) (tokensGenerated / (generationTimeMs / 1000.0)) else 0.0

    val timeToFirstTokenMs: Double
        get() = promptEvalTimeMs
}
