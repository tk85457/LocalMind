package com.localmind.app.core.performance

import android.app.ActivityManager
import android.content.Context
import com.localmind.app.llm.InferenceConfig
import com.localmind.app.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    // FIX #4: Cache DeviceProfile to avoid repeated Binder IPC calls.
    // activityManager.getMemoryInfo() is a Binder IPC (~1-3ms each).
    // Cache for 5 seconds — device RAM doesn't change mid-sentence.
    @Volatile private var cachedProfileValue: DeviceProfile? = null
    @Volatile private var lastProfileCacheMs: Long = 0L
    private val PROFILE_CACHE_TTL_MS = 5_000L

    fun currentProfile(): DeviceProfile {
        val now = System.currentTimeMillis()
        cachedProfileValue?.let { cached ->
            if (now - lastProfileCacheMs < PROFILE_CACHE_TTL_MS) return cached
        }
        val memInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val totalRamGb = ((memInfo.totalMem + (BYTES_IN_GB - 1)) / BYTES_IN_GB).toInt()
        val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val recommendedContextSize = when {
            totalRamGb <= 4 -> 2048
            totalRamGb <= 6 -> 4096
            totalRamGb <= 8 -> 8192
            else -> 16384
        }

        // POCKETPAL FIX: Thread count = 80% of cores (PocketPal exact formula).
        // PocketPal: nThreads = Math.floor(numCpus * 0.8) for >4 core devices.
        val pocketpalThreads = if (cpuCores > 4) {
            (cpuCores * 0.8).toInt().coerceAtLeast(4)
        } else {
            cpuCores.coerceAtLeast(2)
        }
        val recommendedThreadCount = when {
            totalRamGb <= 4 -> pocketpalThreads.coerceAtMost(6)
            totalRamGb <= 6 -> pocketpalThreads.coerceAtMost(6)
            totalRamGb <= 8 -> pocketpalThreads.coerceAtMost(8)
            else -> pocketpalThreads
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

        val profile = DeviceProfile(
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
        cachedProfileValue = profile
        lastProfileCacheMs = System.currentTimeMillis()
        return profile
    }

    fun isSpeculativeDecodingSupported(): Boolean {
        // Speculative decoding requires at least 5GB of AVAILABLE RAM
        val requiredRamBytes = 5L * 1024 * 1024 * 1024L
        return currentProfile().availableRamBytes > requiredRamBytes
    }

    fun tuneInferenceConfig(
        config: InferenceConfig,
        benchmarkMode: Boolean = false
    ): InferenceConfig {
        // USER SETTING RESPECT: Context size bilkul touch nahi karo.
        // User ne jo settings mein set kiya wahi use hoga — RAM ke hisaab se koi override nahi.
        val tunedTokens = if (benchmarkMode) config.maxTokens.coerceAtLeast(48)
                          else config.maxTokens.coerceAtLeast(64)
        val tunedTopK = config.topK.coerceIn(16, 100)

        return config.copy(
            maxTokens = tunedTokens,
            topK = tunedTopK
            // contextSize aur threadCount user ki value se hi aate hain
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
        val warnings = mutableListOf<String>()
        if (!compatibility.compatible) {
            compatibility.reason?.takeIf { it.isNotBlank() }?.let { warnings += it }
        }

        // USER SETTING RESPECT: requestedContextSize ko as-is use karo
        var tunedContext = requestedContextSize.coerceAtLeast(256)
        var tunedThreads = profile.recommendedThreadCount.coerceAtLeast(1)
        var tunedMaxTokens = profile.recommendedMaxTokens.coerceAtLeast(EMERGENCY_MAX_TOKENS)

        val estimatedRequiredRam = estimateRequiredRamBytes(modelSizeBytes, tunedContext)
        val headroomRequired = (estimatedRequiredRam * SAFETY_HEADROOM_FACTOR).toLong()

        if (profile.availableRamBytes < headroomRequired || isMemoryPressureHigh()) {
            warnings += "Low available RAM detected, but preserving user inference settings"
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
            profile.totalRamGb <= 4 -> 1024
            profile.totalRamGb <= 6 -> 1024
            else -> 1536
        }
        // PERF FIX: 1 thread was unusably slow. 3 is the minimum for acceptable speed.
        val forcedThreads = when {
            profile.totalRamGb <= 4 -> 3
            profile.totalRamGb <= 6 -> 4
            else -> 6
        }
        val forcedTokens = when {
            profile.totalRamGb <= 4 -> 128
            else -> 256
        }

        val forcedRam = estimateRequiredRamBytes(
            modelSizeBytes = modelSizeBytes,
            contextSize = forcedContext
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

        val emergencyContextSize = runCatching { runBlocking { settingsRepository.minNCtx.first() } }.getOrDefault(1024)

        return ActivationPlan(
            allowed = true,
            reason = "Force mode enabled. Quality/stability may drop.",
            contextSize = forcedContext.coerceAtMost(requestedContextSize.coerceAtLeast(emergencyContextSize)),
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
        val safeRequestedContext = requestedContextSize
            .coerceAtLeast(EMERGENCY_CONTEXT_SIZE)
            .coerceAtMost(maxContextForRam(profile.totalRamGb))
        val estimatedRequiredRam = estimateRequiredRamBytes(
            modelSizeBytes = modelSizeBytes,
            contextSize = safeRequestedContext
        )
        val requiredWithHeadroom = (estimatedRequiredRam * SAFETY_HEADROOM_FACTOR).toLong()

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
            profile.availableRamBytes < requiredWithHeadroom -> {
                ModelCompatibilityCheck(
                    compatible = false,
                    reason = "Requested context $safeRequestedContext may exceed available RAM on this ${profile.totalRamGb}GB device."
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
        // User settings respect karo — no RAM-based override.
        // totalRamGb is logged for diagnostics only.
        android.util.Log.d("DeviceProfile", "maxContextForRam: totalRamGb=$totalRamGb, returning uncapped")
        return 131072
    }

    private fun estimateRequiredRamBytes(
        modelSizeBytes: Long,
        contextSize: Int
    ): Long {
        val kvCacheBytes = contextSize * CONTEXT_TOKEN_BYTES
        return modelSizeBytes + kvCacheBytes
    }

    // POCKETPAL FIX: Accurate memory estimation using GGUF metadata
    // PocketPal memoryEstimator.ts ka exact formula:
    // total = (weights + kvCache + computeBuffer) * 1.1
    fun estimateRequiredRamWithMetadata(
        metadata: com.localmind.app.core.engine.ModelMetadata,
        contextSize: Int,
        bytesPerK: Int = 2, // F16 = 2 bytes
        bytesPerV: Int = 2
    ): Long {
        val keyCacheSize = metadata.nLayer.toLong() * contextSize * (metadata.nEmbd / metadata.nHead.coerceAtLeast(1)) * metadata.nHeadKv.coerceAtLeast(1) * bytesPerK
        val valueCacheSize = metadata.nLayer.toLong() * contextSize * (metadata.nEmbd / metadata.nHead.coerceAtLeast(1)) * metadata.nHeadKv.coerceAtLeast(1) * bytesPerV
        val kvCache = keyCacheSize + valueCacheSize
        val computeBuffer = (metadata.modelSize * 0.1).toLong().coerceAtLeast(256 * 1024 * 1024L)
        return ((metadata.modelSize + kvCache + computeBuffer) * 1.1).toLong()
    }

    fun isMemoryPressureHigh(): Boolean {
        val memInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val isHigh = memInfo.lowMemory || (memInfo.availMem < (memInfo.totalMem * 0.15))
        // FIX #4: If memory pressure is high, invalidate the cached profile so
        // next call to currentProfile() gets fresh availableRamBytes.
        if (isHigh) lastProfileCacheMs = 0L
        return isHigh
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
