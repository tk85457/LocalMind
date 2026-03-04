package com.localmind.app.core.performance

import com.localmind.app.core.engine.ModelMetadata
import kotlin.math.roundToLong

/**
 * Advanced Memory Estimator for local LLM execution.
 * Provides accurate RAM requirements by analyzing GGUF metadata.
 */
object MemoryEstimator {

    /**
     * Estimates total RAM consumption in bytes.
     * @param metadata Model metadata from GGUF
     * @param contextSize Requested context window
     * @param quantization Model quantization format (e.g., Q4_K_M)
     */
    fun estimateTotalRAM(
        metadata: ModelMetadata,
        contextSize: Int,
        quantization: String = "Q4_K_M"
    ): Long {
        if (metadata.nParams <= 0) return metadata.modelSize // Fallback

        val weightsSize = estimateWeightsSize(metadata.nParams, quantization)
        val kvCacheSize = estimateKVCacheSize(
            nLayer = metadata.nLayer,
            nCtx = contextSize,
            nHeadKv = metadata.nHeadKv,
            nEmbdHead = if (metadata.nHead > 0) metadata.nEmbd / metadata.nHead else 128
        )

        // Account for context buffers, compute overhead, and system buffers
        val overhead = (metadata.modelSize * 0.05).toLong().coerceAtLeast(100 * 1024 * 1024)

        return weightsSize + kvCacheSize + overhead
    }

    private fun estimateWeightsSize(nParams: Long, quantization: String): Long {
        val bitsPerParam = when {
            quantization.contains("F32", true) -> 32.0
            quantization.contains("F16", true) -> 16.0
            quantization.contains("Q8", true) -> 8.5
            quantization.contains("Q6", true) -> 6.6
            quantization.contains("Q5", true) -> 5.5
            quantization.contains("Q4_K_M", true) -> 4.8
            quantization.contains("Q4", true) -> 4.5
            quantization.contains("Q3", true) -> 3.7
            quantization.contains("Q2", true) -> 2.6
            else -> 4.8 // Default to Q4_K_M equivalent
        }
        return (nParams * bitsPerParam / 8.0).roundToLong()
    }

    private fun estimateKVCacheSize(
        nLayer: Int,
        nCtx: Int,
        nHeadKv: Int,
        nEmbdHead: Int,
        isF16: Boolean = true
    ): Long {
        val bytesPerElement = if (isF16) 2 else 4
        // 2 (K and V) * nLayer * nCtx * nHeadKv * nEmbdHead * bytesPerElement
        return 2L * nLayer * nCtx * nHeadKv * nEmbdHead * bytesPerElement
    }

    /**
     * Format bytes to a human-readable string (GB/MB)
     */
    fun formatBytes(bytes: Long): String {
        return if (bytes >= 1024 * 1024 * 1024) {
            String.format(java.util.Locale.US, "%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
        } else {
            String.format(java.util.Locale.US, "%.0f MB", bytes.toDouble() / (1024 * 1024))
        }
    }
}
