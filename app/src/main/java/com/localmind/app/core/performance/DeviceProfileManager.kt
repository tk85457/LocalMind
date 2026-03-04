package com.localmind.app.core.performance

import android.app.ActivityManager
import android.content.Context
import com.localmind.app.llm.InferenceConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

data class DeviceProfile(
    val totalRamGb: Int,
    val availableRamBytes: Long,
    val cpuCores: Int,
    val lowRamDevice: Boolean,
    val recommendedContextSize: Int,
    val recommendedThreadCount: Int,
    val recommendedMaxTokens: Int,
    val benchmarkMaxTokens: Int,
    val maxSupportedParamsB: Double
)

data class ModelLoadCheck(
    val allowed: Boolean,
    val reason: String? = null,
    val tunedContextSize: Int = 2048,
    val tunedThreadCount: Int = 4,
    val tunedMaxTokens: Int = 512
)

data class ModelCompatibilityCheck(
    val compatible: Boolean,
    val reason: String? = null
)

data class ModelCompatibilityGuidance(
    val compatible: Boolean,
    val reason: String? = null,
    val fixTips: List<String> = emptyList()
)

data class ActivationPlan(
    val allowed: Boolean,
    val reason: String? = null,
    val contextSize: Int,
    val threadCount: Int,
    val maxTokens: Int,
    val forced: Boolean = false
)

@Singleton
class DeviceProfileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun currentProfile(): DeviceProfile {
        val memInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val totalRamGb = ((memInfo.totalMem + (BYTES_IN_GB - 1)) / BYTES_IN_GB).toInt()
        val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val recommendedContextSize = when {
            totalRamGb <= 4 -> 3000
            totalRamGb <= 6 -> 4096
            totalRamGb <= 8 -> 8192
            else -> 16384
        }

        // PERF FIX: Old values (2/3 threads for 4-6GB) were absurdly slow on
        // 8-core phones. 4 threads is the sweet spot for mobile SoCs — uses
        // performance cores without thrashing efficiency cores.
        val performanceThreads = (cpuCores / 2).coerceAtLeast(4)
        val recommendedThreadCount = when {
            totalRamGb <= 4 -> 4  // was 2! On 8-core Vivo only 2 threads were used
            totalRamGb <= 6 -> 4  // was 3
            totalRamGb <= 8 -> min(performanceThreads, 6).coerceAtLeast(4)
            else -> min(performanceThreads, 8).coerceAtLeast(4)
        }

        // BUGFIX: Was 4096 for 4GB devices but checkModelLoad hard-caps that at 192.
        // Align base profile with the actual enforced cap so Settings UI shows
        // a realistic value instead of a misleading 4096.
        val recommendedMaxTokens = when {
            totalRamGb <= 4 -> 1024
            totalRamGb <= 6 -> 2048
            totalRamGb <= 8 -> 4096
            else -> 8192
        }

        val benchmarkMaxTokens = when {
            totalRamGb <= 4 -> 64
            totalRamGb <= 6 -> 96
            totalRamGb <= 8 -> 128
            else -> 192
        }

        val maxSupportedParamsB = when {
            totalRamGb <= 4 -> 4.0
            totalRamGb <= 6 -> 7.0
            else -> 13.0
        }

        return DeviceProfile(
            totalRamGb = totalRamGb,
            availableRamBytes = memInfo.availMem,
            cpuCores = cpuCores,
            lowRamDevice = totalRamGb <= 6,
            recommendedContextSize = recommendedContextSize,
            recommendedThreadCount = recommendedThreadCount,
            recommendedMaxTokens = recommendedMaxTokens,
            benchmarkMaxTokens = benchmarkMaxTokens,
            maxSupportedParamsB = maxSupportedParamsB
        )
    }

    fun tuneInferenceConfig(
        config: InferenceConfig,
        benchmarkMode: Boolean = false
    ): InferenceConfig {
        val profile = currentProfile()
        val tunedContext = config.contextSize
            .coerceAtLeast(EMERGENCY_CONTEXT_SIZE)
            .coerceAtMost(maxContextForRam(profile.totalRamGb))
        val tunedThreads = min(config.threadCount, profile.recommendedThreadCount).coerceAtLeast(1)
        val tunedTokens = if (benchmarkMode) {
             config.maxTokens.coerceAtLeast(48)
        } else {
             config.maxTokens.coerceAtLeast(64)
        }
        // PERF FIX: Removed aggressive topK cap. topK has negligible impact on
        // speed but capping it hurt output quality. Just apply a sane floor.
        val tunedTopK = config.topK.coerceIn(16, 100)

        return config.copy(
            contextSize = tunedContext,
            threadCount = tunedThreads,
            maxTokens = tunedTokens,
            topK = tunedTopK
        )
    }

    fun checkModelLoad(
        modelSizeBytes: Long,
        modelNameHint: String,
        quantizationHint: String?,
        parameterCountHint: String?,
        requestedContextSize: Int
    ): ModelLoadCheck {
        if (modelSizeBytes <= 0L) {
            return ModelLoadCheck(allowed = false, reason = "Invalid model file")
        }

        val compatibility = evaluateModelCompatibility(
            modelSizeBytes = modelSizeBytes,
            modelNameHint = modelNameHint,
            quantizationHint = quantizationHint,
            parameterCountHint = parameterCountHint,
            requestedContextSize = requestedContextSize
        )
        val profile = currentProfile()
        val normalizedQuant = normalizeQuantization(quantizationHint, modelNameHint)
        val warnings = mutableListOf<String>()
        if (!compatibility.compatible) {
            compatibility.reason?.takeIf { it.isNotBlank() }?.let { warnings += it }
        }

        var tunedContext = requestedContextSize
            .coerceAtLeast(EMERGENCY_CONTEXT_SIZE)
            .coerceAtMost(maxContextForRam(profile.totalRamGb))
        var tunedThreads = profile.recommendedThreadCount.coerceAtLeast(1)
        var tunedMaxTokens = profile.recommendedMaxTokens.coerceAtLeast(EMERGENCY_MAX_TOKENS)

        val estimatedRequiredRam = estimateRequiredRamBytes(modelSizeBytes, tunedContext, normalizedQuant)
        val headroomRequired = (estimatedRequiredRam * SAFETY_HEADROOM_FACTOR).toLong()

        if (profile.availableRamBytes < headroomRequired || isMemoryPressureHigh()) {
            warnings += "Low available RAM detected, applying emergency inference settings"
            tunedContext = min(tunedContext, EMERGENCY_CONTEXT_SIZE)
            // PERF FIX: Emergency thread cap was too aggressive (2 for 4GB = extremely slow).
            // Even under memory pressure, 3 threads is a safe minimum that keeps things usable.
            tunedThreads = min(tunedThreads, if (profile.totalRamGb <= 4) 3 else 4).coerceAtLeast(2)
            tunedMaxTokens = min(tunedMaxTokens, EMERGENCY_MAX_TOKENS)
        }

        if (profile.totalRamGb <= 4) {
            tunedMaxTokens = min(tunedMaxTokens, 192).coerceAtLeast(EMERGENCY_MAX_TOKENS)
        }

        return ModelLoadCheck(
            allowed = true,
            reason = warnings.takeIf { it.isNotEmpty() }?.joinToString(". "),
            tunedContextSize = tunedContext,
            tunedThreadCount = tunedThreads,
            tunedMaxTokens = tunedMaxTokens
        )
    }

    fun safePlan(
        modelSizeBytes: Long,
        modelNameHint: String,
        quantizationHint: String?,
        parameterCountHint: String?,
        requestedContextSize: Int
    ): ActivationPlan {
        val check = checkModelLoad(
            modelSizeBytes = modelSizeBytes,
            modelNameHint = modelNameHint,
            quantizationHint = quantizationHint,
            parameterCountHint = parameterCountHint,
            requestedContextSize = requestedContextSize
        )
        return ActivationPlan(
            allowed = check.allowed,
            reason = check.reason,
            contextSize = check.tunedContextSize,
            threadCount = check.tunedThreadCount,
            maxTokens = check.tunedMaxTokens,
            forced = false
        )
    }

    fun forcePlan(
        modelSizeBytes: Long,
        modelNameHint: String,
        quantizationHint: String?,
        parameterCountHint: String?,
        requestedContextSize: Int
    ): ActivationPlan {
        val safe = safePlan(
            modelSizeBytes = modelSizeBytes,
            modelNameHint = modelNameHint,
            quantizationHint = quantizationHint,
            parameterCountHint = parameterCountHint,
            requestedContextSize = requestedContextSize
        )
        if (safe.allowed) {
            return safe
        }
        if (modelSizeBytes <= 0L) {
            return safe.copy(reason = "Invalid model file")
        }

        val profile = currentProfile()
        val forcedContext = when {
            profile.totalRamGb <= 4 -> 768
            profile.totalRamGb <= 6 -> 1024
            else -> 1536
        }
        // PERF FIX: 1 thread was unusably slow. 3 is the minimum for acceptable speed.
        val forcedThreads = when {
            profile.totalRamGb <= 4 -> 3
            profile.totalRamGb <= 6 -> 4
            else -> 4
        }
        val forcedTokens = when {
            profile.totalRamGb <= 4 -> 128
            else -> 256
        }

        val quant = normalizeQuantization(quantizationHint, modelNameHint)
        val forcedRam = estimateRequiredRamBytes(
            modelSizeBytes = modelSizeBytes,
            contextSize = forcedContext,
            quantization = quant
        )
        val requiredWithHeadroom = (forcedRam * FORCE_HEADROOM_FACTOR).toLong()
        if (profile.availableRamBytes < requiredWithHeadroom) {
            return ActivationPlan(
                allowed = false,
                reason = "Even force mode cannot fit in available RAM.",
                contextSize = forcedContext,
                threadCount = forcedThreads,
                maxTokens = forcedTokens,
                forced = true
            )
        }

        return ActivationPlan(
            allowed = true,
            reason = "Force mode enabled. Quality/stability may drop.",
            contextSize = forcedContext.coerceAtMost(requestedContextSize.coerceAtLeast(EMERGENCY_CONTEXT_SIZE)),
            threadCount = forcedThreads,
            maxTokens = forcedTokens,
            forced = true
        )
    }

    fun evaluateModelCompatibility(
        modelSizeBytes: Long,
        modelNameHint: String,
        quantizationHint: String?,
        parameterCountHint: String?,
        requestedContextSize: Int = currentProfile().recommendedContextSize
    ): ModelCompatibilityCheck {
        if (modelSizeBytes <= 0L) {
            return ModelCompatibilityCheck(compatible = false, reason = "Invalid model file")
        }

        val profile = currentProfile()
        val normalizedQuant = normalizeQuantization(quantizationHint, modelNameHint)
        val parameterCountB = parseParameterCountB(parameterCountHint ?: modelNameHint)
        val maxAllowedSizeBytes = when {
            profile.totalRamGb <= 4 -> if (normalizedQuant.contains("Q8") || normalizedQuant.contains("Q6")) 2_576_980_377L else 2_362_232_012L
            profile.totalRamGb <= 6 -> 4_294_967_296L
            profile.totalRamGb <= 8 -> 6_442_450_944L
            else -> 10_737_418_240L
        }

        return when {
            modelSizeBytes > maxAllowedSizeBytes -> {
                ModelCompatibilityCheck(
                    compatible = false,
                    reason = "Model size (${(modelSizeBytes.toDouble() / BYTES_IN_GB).format(1)}GB) exceeds recommended limit for your ${profile.totalRamGb}GB device."
                )
            }
            parameterCountB > profile.maxSupportedParamsB -> {
                ModelCompatibilityCheck(
                    compatible = false,
                    reason = "Model parameters (${parameterCountB}B) exceeds recommended limit (${profile.maxSupportedParamsB}B) for this device."
                )
            }
            else -> ModelCompatibilityCheck(compatible = true)
        }
    }

    fun getCompatibilityGuidance(
        modelSizeBytes: Long,
        modelNameHint: String,
        quantizationHint: String?,
        parameterCountHint: String?,
        requestedContextSize: Int = currentProfile().recommendedContextSize
    ): ModelCompatibilityGuidance {
        val check = evaluateModelCompatibility(
            modelSizeBytes = modelSizeBytes,
            modelNameHint = modelNameHint,
            quantizationHint = quantizationHint,
            parameterCountHint = parameterCountHint,
            requestedContextSize = requestedContextSize
        )

        val tips = mutableListOf<String>()
        if (!check.compatible) {
            val profile = currentProfile()
            if (modelSizeBytes > 3 * BYTES_IN_GB && profile.totalRamGb <= 4) {
                tips += "Try a 'Q4_K_M' or 'Q3_K_L' quantization for better reliability"
                tips += "Consider models under 2.5GB for your 4GB RAM device"
            }
            if (profile.lowRamDevice) {
                tips += "Close other background apps before loading"
            }
        }

        return ModelCompatibilityGuidance(
            compatible = check.compatible,
            reason = check.reason,
            fixTips = tips
        )
    }

    private fun normalizeQuantization(quantization: String?, modelName: String): String {
        val q = quantization?.uppercase(Locale.ROOT) ?: modelName.uppercase(Locale.ROOT)
        return QUANT_REGEX.find(q)?.value ?: "Q4_K_M"
    }

    private fun parseParameterCountB(modelName: String): Double {
        return PARAM_REGEX.find(modelName)?.groupValues?.get(1)?.toDoubleOrNull() ?: 7.0
    }

    private fun maxContextForRam(totalRamGb: Int): Int {
        return when {
            totalRamGb <= 4 -> 4096
            totalRamGb <= 6 -> 8192
            else -> 16384
        }
    }

    private fun estimateRequiredRamBytes(
        modelSizeBytes: Long,
        contextSize: Int,
        quantization: String
    ): Long {
        val kvCacheBytes = contextSize * CONTEXT_TOKEN_BYTES
        return modelSizeBytes + kvCacheBytes
    }

    fun isMemoryPressureHigh(): Boolean {
        val memInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        return memInfo.lowMemory || (memInfo.availMem < (memInfo.totalMem * 0.15))
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    companion object {
        private const val BYTES_IN_GB = 1024 * 1024 * 1024L
        // PERF FIX: Was 131072 (128KB per token!) — completely wrong.
        // Real KV cache per token for Q8_0 = ~512 bytes.
        // Old value caused phantom OOM → emergency slow mode on every device.
        private const val CONTEXT_TOKEN_BYTES = 512L
        private const val SAFETY_HEADROOM_FACTOR = 1.10
        private const val FORCE_HEADROOM_FACTOR = 1.05
        private const val EMERGENCY_CONTEXT_SIZE = 2048 // PERF FIX: Was 1024 — too small
        private const val EMERGENCY_MAX_TOKENS = 512

        private val QUANT_REGEX = Regex("Q\\d+(_[A-Z0-9]+)?")
        private val PARAM_REGEX = Regex("(\\d+(?:\\.\\d+)?)\\s*B")
    }
}
