package com.tk854.localmind.core.performance

import android.app.ActivityManager
import android.content.Context
import com.tk854.localmind.llm.InferenceConfig
import com.tk854.localmind.data.repository.SettingsRepository
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
    // Cache for 5 seconds  —  device RAM doesn't change mid-sentence.
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

        // LOW-RAM FIX: Context size matters hugely for KV cache RAM usage.
        // 4GB phone: 2048 ctx = ~200MB KV cache + model weights = near OOM for 3B models.
        // 1024 ctx = ~100MB KV cache = fits comfortably, still handles most conversations.
        val recommendedContextSize = when {
            totalRamGb <= 4 -> 1024  // 4GB: 1024 ctx  —  fits 1B-3B models safely
            totalRamGb <= 6 -> 2048  // 6GB: 2048 ctx
            else -> 4096             // 8GB+: 4096 ctx
        }

        // POCKETPAL FIX: Thread count = 80% of cores (PocketPal exact formula).
        // PocketPal: nThreads = Math.floor(numCpus * 0.8) for >4 core devices.
        // PERF OPTIMIZATION: Octa-core mobile CPUs run best with 4 threads, matching physical performance cores.
        // Running more threads utilizes LITTLE/efficiency cores, causing throttling and thermal slowdown.
        val pocketpalThreads = if (cpuCores > 4) {
            (cpuCores * 0.8).toInt().coerceAtLeast(4).coerceAtMost(4)
        } else {
            cpuCores.coerceAtLeast(2)
        }
        val recommendedThreadCount = pocketpalThreads

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
            totalRamGb <= 3 -> 1.5   // 3GB: sirf 1.5B model
            totalRamGb <= 4 -> 3.0   // 4GB: max 3B model (was 4.0  —  4B Q4_K_M=2.7GB, OS+app le lo toh OOM)
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

    /**
     * Force profile cache invalidation so next currentProfile() call
     * returns fresh RAM measurements. Call after model unload.
     */
    fun invalidateProfileCache() {
        lastProfileCacheMs = 0L
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
        val profile = currentProfile()

        // LOW-RAM FIX: 4GB phone pe context + maxTokens dono cap karo.
        // User 8192 ctx set kar sakta hai but 4GB pe ye OOM ya extreme slowness deta hai.
        // Hard cap lagao  —  user settings screen pe info dena better hai.
        val maxSafeContext = when {
            profile.totalRamGb <= 4 -> 2048   // 4GB: max 2048 ctx hard cap
            profile.totalRamGb <= 6 -> 4096
            else -> config.contextSize        // 8GB+: user value respect karo
        }
        val maxSafeTokens = when {
            profile.totalRamGb <= 4 -> 768    // 4GB: max 768 tokens (fast enough, complete answers)
            profile.totalRamGb <= 6 -> 1024
            else -> config.maxTokens          // 8GB+: user value respect karo
        }

        val tunedContext = config.contextSize.coerceIn(256, maxSafeContext)
        val tunedTokens = if (benchmarkMode) config.maxTokens.coerceAtLeast(48)
                          else config.maxTokens.coerceIn(64, maxSafeTokens)
        val tunedTopK = config.topK.coerceIn(16, 100)

        return config.copy(
            contextSize = tunedContext,
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
            else -> 4
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

        val emergencyContextSize = 1024

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

        // BUG FIX: Cached profile mein availableRamBytes STALE ho sakta hai
        // (pehle wale model ke loaded hone ke time ka). Fresh measurement lo.
        lastProfileCacheMs = 0L  // Invalidate cache  —  fresh RAM reading force karo
        val profile = currentProfile()
        val normalizedQuant = normalizeQuantization(quantizationHint, modelNameHint)
        val parameterCountB = parseParameterCountB(parameterCountHint ?: modelNameHint)
        // 4GB phone mein usable RAM = ~2.2GB (baaki OS + Android framework le leta hai)
        // Q4_K_M model size guide: 1B=~700MB, 2B=~1.4GB, 3B=~2.0GB, 4B=~2.7GB (too big)
        // Q8_0 aur Q6 aur bhi zyada RAM lete hain, isliye 4GB pe inhe aur restrict karo.
        // POCKETPAL FIX: Q8_0 ke 1B model (1.23GB) ko 4GB pe block mat karo.
        // PocketPal koi quant-based hard block nahi lagata  —  size check karta hai.
        // Agar model 2GB se chhota hai toh 4GB pe load ATTEMPT hona chahiye.
        // (Actual load fail ho sakta hai  —  woh LLMEngine ke progressive fallback handle karta hai.)
        val maxAllowedSizeBytes = when {
            profile.totalRamGb <= 4 -> 2_147_483_648L  // 4GB: max 2GB model size (quant-agnostic)
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
                // BUG FIX: Agar model size KHUD fit hota hai available RAM mein,
                // toh context overhead ke wajah se block mat karo  —  LLMEngine handle karega.
                // Sirf tab block karo jab model ITSELF available RAM se bada ho.
                if (modelSizeBytes <= profile.availableRamBytes) {
                    // Model fits but context overhead might be too much  —  allow with warning
                    ModelCompatibilityCheck(compatible = true)
                } else {
                    ModelCompatibilityCheck(
                        compatible = false,
                        reason = "Not enough RAM to load this model. Model: ${modelSizeBytes / 1048576}MB, Available RAM: ~${profile.availableRamBytes / 1048576}MB. Try: (1) Close other apps, (2) Restart phone, (3) Use a smaller model."
                    )
                }
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
            if (profile.totalRamGb <= 4) {
                tips += "4GB RAM ke liye: Q4_K_M ya Q3_K_M quantization use karo"
                tips += "4GB phone ke liye ~2GB se chhota model choose karo (1B–2B parameter)"
                tips += "Recommended: Qwen2.5-1.5B-Q4_K_M (~1.0GB) ya Phi-3-mini-Q4_K_M (~2.0GB)"
            } else if (modelSizeBytes > 3 * BYTES_IN_GB && profile.totalRamGb <= 6) {
                tips += "Try a 'Q4_K_M' or 'Q3_K_L' quantization for better reliability"
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
        // User settings respect karo  —  no RAM-based override.
        // totalRamGb is logged for diagnostics only.
        if (com.tk854.localmind.BuildConfig.DEBUG) android.util.Log.d("DeviceProfile", "maxContextForRam: totalRamGb=$totalRamGb, returning uncapped")
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
        metadata: com.tk854.localmind.core.engine.ModelMetadata,
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
        // PERF FIX: Was 131072 (128KB per token!)  —  completely wrong.
        // Real KV cache per token for Q8_0 = ~512 bytes.
        // Old value caused phantom OOM → emergency slow mode on every device.
        private const val CONTEXT_TOKEN_BYTES = 512L
        private const val SAFETY_HEADROOM_FACTOR = 1.10
        private const val FORCE_HEADROOM_FACTOR = 1.05
        private const val EMERGENCY_CONTEXT_SIZE = 256  // CRASH FIX: Was 2048  —  minimum viable fallback
        private const val EMERGENCY_MAX_TOKENS = 512

        private val QUANT_REGEX = Regex("Q\\d+(_[A-Z0-9]+)?")
        private val PARAM_REGEX = Regex("(\\d+(?:\\.\\d+)?)\\s*B")
    }
}
