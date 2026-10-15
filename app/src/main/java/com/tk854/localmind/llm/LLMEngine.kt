package com.tk854.localmind.llm

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.util.Log
import com.tk854.localmind.core.Constants
import com.tk854.localmind.core.engine.ModelMetadata
import com.tk854.localmind.core.performance.DeviceProfileManager
import com.tk854.localmind.core.storage.ModelStorageType
import com.tk854.localmind.core.utils.GpuCapabilityChecker
import com.tk854.localmind.domain.model.InferenceSource
import com.tk854.localmind.llm.nativelib.GenerationCallback
import com.tk854.localmind.llm.nativelib.LlamaCppBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core LLM Engine - Handles model loading, inference, and memory management
 *
 * This is the main interface for LLM operations. It provides:
 * - Thread-safe model loading/unloading
 * - RAM validation before model load
 * - Streaming token generation
 * - Stop generation capability
 * - Automatic memory cleanup
 */
@Singleton
class LLMEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bridge: LlamaCppBridge,
    private val settingsRepository: com.tk854.localmind.data.repository.SettingsRepository,
    private val deviceProfileManager: DeviceProfileManager,
    private val gpuCapabilityChecker: GpuCapabilityChecker
) : ComponentCallbacks2 {
    @Volatile private var currentContextPtr: Long = 0
    @Volatile private var isGenerating = false
    @Volatile private var loadedModelKey: String? = null
    @Volatile private var openedModelDescriptor: ParcelFileDescriptor? = null
    @Volatile private var gpuInitFailedInSession = false
    @Volatile private var gpuHardwareSupported: Boolean? = null  // null = not yet checked
    @Volatile private var nativeBackendInitialized = false

    // PERF FIX: cachePrompt aur penaltyLastN ko in-memory cache karo.
    // Pehle ye HAR generation call pe DataStore se read hote the (disk I/O).
    // PocketPal mein ye MobX in-memory observable mein store hote hain  —  zero disk reads.
    // Ab ek baar read karo, phir in-memory value use karo.
    @Volatile private var cachedCachePrompt: Boolean = true
    @Volatile private var cachedPenaltyLastN: Int = 64
    @Volatile private var cachedDefragThreshold: Float = 0.1f
    private var settingsCacheJob: kotlinx.coroutines.Job? = null

    private fun startSettingsCache() {
        settingsCacheJob?.cancel()
        settingsCacheJob = scope.launch {
            kotlinx.coroutines.flow.combine(
                settingsRepository.cachePrompt,
                settingsRepository.penaltyLastN,
                settingsRepository.defragThreshold
            ) { cp, pln, dt -> Triple(cp, pln, dt) }
            .collect { (cp, pln, dt) ->
                cachedCachePrompt = cp
                cachedPenaltyLastN = pln
                cachedDefragThreshold = dt
            }
        }
    }

    // FIX #5: LLMEngine ka apna telemetry store  —  HybridInferenceRouter bypass hone par
    // ChatRepository.getLastInferenceTelemetry() is se read karta hai.
    @Volatile private var lastDirectTelemetry: InferenceTelemetry? = null

    fun getLastTelemetry(): InferenceTelemetry? = lastDirectTelemetry

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val engineMutex = Mutex()
    private var generationJob: kotlinx.coroutines.Job? = null
    @Volatile private var resetPrefixCacheOnNextGenerate: Boolean = false

    // AUTO-DETECTED stop tokens from model's vocab + chat template.
    // Populated once after model load via getStopTokensFromModel() JNI call.
    // Used in generate() and generateWithMessages() calls  —  merged with config.stopTokens.
    // Zero hardcoding  —  works for ANY model: Llama-3, Qwen, Gemma, DeepSeek, Phi, Mistral, etc.
    @Volatile var autoDetectedStopTokens: List<String> = emptyList()
        private set

    // PERF FIX: Pre-computed merged stop token list.
    // Previously, every generate() call did (config.stopTokens + autoDetected).map{}.filter{}.distinct()
    // creating new list objects on the hot path. Now auto-detected tokens are pre-cleaned
    // and cached  —  only merged with config.stopTokens at call time (cheap concat + distinct).
    @Volatile private var cleanedAutoStopTokens: List<String> = emptyList()

    /**
     * Fully dynamic stop token detection  —  no hardcoded lists.
     * Calls getStopTokensFromModel() JNI which does 2-layer detection:
     *   Layer 1: EOS + EOT vocab IDs (llama_vocab_eos / llama_vocab_eot)
     *   Layer 2: Chat template scan for quoted markers (<|eot_id|>, <|im_end|>, etc.)
     *
     * Result is stored in autoDetectedStopTokens and merged at generation time.
     */
    private fun updateAutoStopTokens(contextPtr: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                val raw = runCatching { bridge.getStopTokensFromModel(contextPtr) }.getOrElse { "" }
                val detected = parseAutoStopTokens(raw)
                autoDetectedStopTokens = detected
                // PERF FIX: Pre-clean auto-detected tokens once  —  avoid map/filter/trim per generation call
                cleanedAutoStopTokens = detected.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                Log.i(TAG, "AutoStopTokens: detected ${detected.size} tokens: $detected")
            } catch (e: Exception) {
                Log.w(TAG, "AutoStopTokens detection failed (non-critical)", e)
                autoDetectedStopTokens = emptyList()
            }
        }
    }

    private fun parseAutoStopTokens(raw: String): List<String> {
        val payload = raw.trim()
        if (payload.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(payload)
            buildList {
                for (i in 0 until array.length()) {
                    val value = array.optString(i).trim()
                    if (value.isNotBlank()) add(value)
                }
            }.distinct()
        }.getOrElse {
            // Backward-compat: older native builds returned a pipe-separated string.
            Regex("""<\|.*?\|>|</?[^>]+>|[^|]+""")
                .findAll(payload)
                .map { it.value.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
        }
    }

    private val _memoryWarning = MutableSharedFlow<String>()
    val memoryWarning = _memoryWarning.asSharedFlow()

    // PocketPal-style: model load ke baad thinking support detect karo
    // ChatViewModel is collect karta hai aur model DB mein update karta hai
    private val _thinkingSupported = MutableSharedFlow<Boolean>(replay = 1)
    val thinkingSupported = _thinkingSupported.asSharedFlow()

    // PocketPal THINKING_TOKENS (thinkingCapabilityDetection.ts se same)
    private val THINKING_TOKENS = setOf(
        "<think>", "</think>", "assistant_thoughts", "reasoning_format",
        "<|start_thinking|>", "<|end_thinking|>", "<|thinking|>",
        "start_thinking", "end_thinking"
    )

    /**
     * PocketPal-style: Model load ke baad chat template scan karo → <think> token hai toh thinking supported.
     * Same logic jaise PocketPal ka thinkingCapabilityDetection.ts + updateModelThinkingCapabilities().
     * Result _thinkingSupported flow mein emit hota hai → ChatViewModel collect karta hai aur DB update karta hai.
     */
    private fun detectThinkingSupport(contextPtr: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                val chatTemplate = runCatching { bridge.getChatTemplate(contextPtr) }.getOrNull()
                val templateLower = chatTemplate?.lowercase() ?: ""

                val supported = when {
                    // Chat template mein thinking tokens dhundo (PocketPal: THINKING_TOKENS check)
                    THINKING_TOKENS.any { templateLower.contains(it.lowercase()) } -> true
                    // DeepSeek-R1 pattern
                    templateLower.contains("deepseek") && (templateLower.contains("<think>") || templateLower.contains("thinking")) -> true
                    // Qwen3 pattern
                    templateLower.contains("qwen") && templateLower.contains("thinking") -> true
                    // Model name se fallback detect (agar template available nahi)
                    else -> false
                }

                Log.i(TAG, "detectThinkingSupport: supported=$supported (template_len=${templateLower.length})")
                _thinkingSupported.emit(supported)
            } catch (e: Exception) {
                Log.w(TAG, "detectThinkingSupport failed (non-critical)", e)
                _thinkingSupported.emit(false)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        context.registerComponentCallbacks(this)
        startSettingsCache()  // start in-memory settings cache
    }

    companion object {
        private const val TAG = "LLMEngine"
        private const val ENGINE_LOCK_TIMEOUT_MS = 20_000L
        private const val MODEL_LOAD_TIMEOUT_MS = 90_000L
        private const val GPU_LOAD_ATTEMPT_TIMEOUT_MS = 6_000L
    }

    private data class NativeLoadAttempt(
        val gpuLayers: Int,
        val contextSize: Int,
        val useMlock: Boolean,
        val useMmap: Boolean,
        val batchSize: Int,
        val physicalBatchSize: Int,
        val prefillThreads: Int,
        val label: String,
        // POCKETPAL FIX: Per-attempt KV cache type override.
        // Vision models (SmolVLM2 etc.) need F16  —  Q8_0 se llama_init_from_model fail hota hai.
        // Normal text models ke liye null = user settings follow karo.
        val keyCacheTypeOverride: String? = null,
        val valueCacheTypeOverride: String? = null
    )

    /**
     * Load a model from file path with RAM validation
     */
    suspend fun loadModel(
        modelPath: String,
        quantizationHint: String? = null,
        parameterCountHint: String? = null,
        storageType: String = ModelStorageType.INTERNAL,
        storageUri: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val modelFileSize: Long
            var nativePath = modelPath
            val modelKey: String
            var pendingDescriptor: ParcelFileDescriptor? = null

            if (storageType == ModelStorageType.SAF) {
                if (storageUri.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("Model storage URI missing"))
                }
                val uri = runCatching { Uri.parse(storageUri) }.getOrNull()
                    ?: return@withContext Result.failure(Exception("Invalid model storage URI"))
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: return@withContext Result.failure(Exception("Unable to open model from linked storage"))
                pendingDescriptor = descriptor
                nativePath = "/proc/self/fd/${descriptor.fd}"
                modelFileSize = descriptor.statSize.coerceAtLeast(0L)
                modelKey = "saf:$storageUri"
            } else {
                val modelFile = File(modelPath)
                if (!modelFile.exists()) {
                    return@withContext Result.failure(Exception("Model file not found: $modelPath"))
                }
                if (!modelFile.isFile || !modelFile.canRead()) {
                    return@withContext Result.failure(Exception("Model file is not readable"))
                }
                modelFileSize = modelFile.length()
                modelKey = "path:${modelFile.absolutePath}"
            }

            if (!isValidGgufFile(nativePath)) {
                pendingDescriptor?.closeQuietly()
                return@withContext Result.failure(Exception("Invalid or corrupted GGUF file"))
            }

            if (!nativeBackendInitialized) {
                val nativeLibDir = context.applicationInfo.nativeLibraryDir.orEmpty()
                Log.i(TAG, "Initializing native backend [dir=$nativeLibDir]")
                val initialized = runCatching {
                    bridge.initializeBackend(nativeLibDir)
                }.getOrElse { error ->
                    Log.e(TAG, "Native backend init call failed", error)
                    false
                }
                Log.i(TAG, "Native backend init result=$initialized")
                if (!initialized) {
                    pendingDescriptor?.closeQuietly()
                    return@withContext Result.failure(Exception("Native backend initialization failed"))
                }
                nativeBackendInitialized = true
                // PERF FIX: Reset skipGpuAfterFail on every fresh backend init.
                // Old sessions could permanently disable GPU from a one-time fluke.
                gpuInitFailedInSession = false
                runCatching { settingsRepository.setSkipGpuAfterFail(false) }
            }

            if (!acquireEngineLock()) {
                pendingDescriptor?.closeQuietly()
                return@withContext Result.failure(Exception("Engine is busy. Try again."))
            }

            val memoryMapping = settingsRepository.memoryMapping.first()
            val useMmapSetting = settingsRepository.useMmap.first()

            // POCKETPAL FIX: Smart MMAP Auto-Toggle
            // Q4_0 aur IQ4_NL quantizations "repackable" hain  —  llama.cpp inhe
            // MMAP se load karke internally repack karta hai. Is process mein
            // MMAP file + repacked buffer dono RAM mein hote hain = 2x memory.
            // Solution: aise models ke liye MMAP disable karo (direct load karo).
            // Baaki sab quantizations (Q4_K_M, Q8_0, etc.) ke liye MMAP safe hai.
            val isRepackableQuant = quantizationHint?.uppercase()?.let { q ->
                q.startsWith("Q4_0") || q.startsWith("IQ4_NL") || q == "Q4_0" || q == "IQ4_NL"
            } ?: run {
                // Filename se quantization detect karo agar hint nahi mila
                val fname = (if (storageType == ModelStorageType.SAF) storageUri else modelPath)
                    ?.lowercase() ?: ""
                fname.contains("q4_0") || fname.contains("iq4_nl")
            }
            // memoryMapping = "None" pe sab disabled, "Smart" pe quantization-aware, baaki manual
            val useMmap = when {
                storageType == ModelStorageType.SAF || nativePath.startsWith("/proc/self/fd/") -> {
                    Log.i(TAG, "SAF/proc/self/fd model detected - disabling mmap since virtual file descriptors do not support memory mapping")
                    false
                }
                memoryMapping == "None" -> false
                isRepackableQuant -> {
                    Log.i(TAG, "Smart MMAP: Repackable quant detected ($quantizationHint) — disabling mmap to save RAM")
                    false  // Q4_0/IQ4_NL: mmap disable
                }
                else -> useMmapSetting  // Q4_K_M, Q8_0, etc: user setting follow karo
            }
            val requestedContextSize = settingsRepository.contextSize.first().coerceAtLeast(1)
            val maxDeviceThreads = Runtime.getRuntime().availableProcessors().coerceAtLeast(Constants.MIN_THREADS)
            val configuredThreadCount = settingsRepository.threadCount.first()
                .coerceIn(Constants.MIN_THREADS, maxDeviceThreads)

            val batchSize = settingsRepository.batchSize.first().coerceAtLeast(1)
            val physicalBatchSize = settingsRepository.physicalBatchSize.first().coerceAtLeast(1)
            val flashAttention = settingsRepository.flashAttention.first()

            // POCKETPAL FIX: Hardware GPU check  —  Adreno + i8mm + dotprod teeno chahiye.
            // Ek baar check karo aur cache karo (EGL call expensive hai).
            if (gpuHardwareSupported == null) {
                val capability = gpuCapabilityChecker.checkGpuSupport()
                gpuHardwareSupported = capability.isSupported
                if (!capability.isSupported) {
                    Log.i(TAG, "GPU not supported by hardware: ${capability.toLogString()} — forcing CPU-only")
                }
            }

            val rawKeyCacheType = settingsRepository.keyCacheType.first()
            val rawValueCacheType = settingsRepository.valueCacheType.first()

            // SMOLVLM2 FIX: Vision/multimodal models (VLM) Q8_0 KV cache support nahi karte.
            // llama.cpp mein cross-attention layers quantized KV cache ke saath
            // llama_init_from_model fail karta hai  —  chahe ctx 512 kyun na ho.
            // Fix: VLM models ke liye F16 force karo. Text-only models ke liye user setting raho.
            // VLM detection: filename mein VLM/Vision/SmolVLM/LLaVA/Qwen-VL etc. check karo.
            val modelFileName = File(modelPath).name.lowercase()
            val isVisionModel = modelFileName.contains("vlm") ||
                modelFileName.contains("vision") ||
                modelFileName.contains("llava") ||
                modelFileName.contains("qwen-vl") ||
                modelFileName.contains("minicpm-v") ||
                modelFileName.contains("moondream") ||
                modelFileName.contains("idefics") ||
                modelFileName.contains("cogvlm") ||
                modelFileName.contains("internvl")

            // Vision models: F16 KV cache force karo (Q8_0/Q4 se init fail hota hai)
            // Text models: user ki setting follow karo
            val keyCacheType = if (isVisionModel) {
                Log.i(TAG, "Vision model detected ($modelFileName) — forcing F16 KV cache")
                "F16"
            } else rawKeyCacheType
            val valueCacheType = if (isVisionModel) "F16" else rawValueCacheType
            val defragThreshold = settingsRepository.defragThreshold.first()

            val loadCheck = deviceProfileManager.checkModelLoad(
                modelSizeBytes = modelFileSize,
                modelNameHint = File(modelPath).name,
                quantizationHint = quantizationHint,
                parameterCountHint = parameterCountHint,
                requestedContextSize = requestedContextSize
            )

            val tunedContextSize = loadCheck.tunedContextSize
            val tunedThreadCount = configuredThreadCount
            val allowMlock = settingsRepository.useMlock.first()
            val configuredGpuLayers = settingsRepository.gpuLayers.first()
            val skipGpuAfterFail = settingsRepository.skipGpuAfterFail.first()
            // POCKETPAL FIX: Hardware-validated GPU check.
            // gpuHardwareSupported = null means not yet checked (fallback = allow).
            val hardwareGpuAllowed = gpuHardwareSupported ?: true
            val shouldAttemptGpu = (configuredGpuLayers > 0 || configuredGpuLayers == -1) && !skipGpuAfterFail && hardwareGpuAllowed

            // POCKETPAL FIX: Hardware check fail hone par GPU layers = 0 force karo.
            // Ye V2135 jaise non-Adreno phones pe crash prevent karta hai.
            val hardwareAllowsGpu = gpuHardwareSupported ?: false
            val effectiveShouldAttemptGpu = shouldAttemptGpu && hardwareAllowsGpu

            val requestedGpuLayers = if (effectiveShouldAttemptGpu) {
                if (configuredGpuLayers == -1) {
                    val totalRamGb = deviceProfileManager.currentProfile().totalRamGb
                    // LOW-RAM FIX: 4GB pe GPU offload karo  —  ye CPU-only se 3-5x faster hai.
                    // GPU VRAM shared hoti hai system RAM se (Adreno architecture).
                    // Small models (< 2GB) = fully offload. Larger = partial offload.
                    val modelSizeGb = modelFileSize.toDouble() / 1e9
                    when {
                        totalRamGb <= 4 && modelSizeGb < 1.5 -> 100  // 4GB + 1B model: full GPU
                        totalRamGb <= 4 && modelSizeGb < 3.0 -> 100  // 4GB + 2-3B: try full GPU
                        totalRamGb <= 4 -> 32                         // 4GB + larger: partial GPU
                        modelSizeGb < 4.0 -> 100                      // 6GB+, small model: full GPU
                        else -> 0
                    }
                } else {
                    configuredGpuLayers
                }
            } else 0

            val gpuLayerCandidates = if (requestedGpuLayers > 0 && hardwareAllowsGpu) {
                listOf(requestedGpuLayers, 0)
            } else {
                listOf(0)
            }

            try {
                if (loadedModelKey == modelKey && currentContextPtr != 0L) {
                    pendingDescriptor?.closeQuietly()
                    return@withContext Result.success(Unit)
                }

                stopGenerationInternal()

                if (currentContextPtr != 0L) {
                    runCatching { bridge.unloadModel(currentContextPtr) }
                    currentContextPtr = 0L
                    loadedModelKey = null
                }

                openedModelDescriptor?.closeQuietly()
                openedModelDescriptor = null

                if (storageType == ModelStorageType.SAF && pendingDescriptor == null) {
                    return@withContext Result.failure(Exception("Failed to prepare SAF descriptor"))
                }

                Log.i(
                    TAG,
                    "Loading model: $nativePath [mlock=$allowMlock, mmap=$useMmap, ctx=$tunedContextSize, threads=$tunedThreadCount, gpu=$requestedGpuLayers]"
                )
                if (configuredGpuLayers > 0 && !shouldAttemptGpu) {
                    Log.i(TAG, "Skipping GPU attempt due to previous GPU initialization failure")
                }

                val preferredPrefillThreads = tunedThreadCount.coerceAtLeast(Constants.MIN_THREADS)
                val safePrefillThreads = (preferredPrefillThreads / 2).coerceAtLeast(Constants.MIN_THREADS)

                val vlmCacheK = if (isVisionModel) "F16" else keyCacheType
                val vlmCacheV = if (isVisionModel) "F16" else valueCacheType

                val loadAttempts = buildList {
                    // Attempt 1: Target GPU configuration if hardware supported and requested
                    if (requestedGpuLayers > 0 && hardwareAllowsGpu) {
                        add(
                            NativeLoadAttempt(
                                gpuLayers = requestedGpuLayers,
                                contextSize = tunedContextSize,
                                useMlock = allowMlock,
                                useMmap = useMmap,
                                batchSize = batchSize,
                                physicalBatchSize = physicalBatchSize,
                                prefillThreads = preferredPrefillThreads,
                                label = "gpu-primary",
                                keyCacheTypeOverride = vlmCacheK,
                                valueCacheTypeOverride = vlmCacheV
                            )
                        )
                    }
                    // Primary CPU attempt
                    add(
                        NativeLoadAttempt(
                            gpuLayers = 0,
                            contextSize = tunedContextSize,
                            useMlock = allowMlock,
                            useMmap = useMmap,
                            batchSize = batchSize,
                            physicalBatchSize = physicalBatchSize,
                            prefillThreads = preferredPrefillThreads,
                            label = "cpu-primary",
                            keyCacheTypeOverride = vlmCacheK,
                            valueCacheTypeOverride = vlmCacheV
                        )
                    )
                    // Safe Fallback: conservative context & batch for constrained devices
                    add(
                        NativeLoadAttempt(
                            gpuLayers = 0,
                            contextSize = tunedContextSize.coerceAtMost(1024).coerceAtLeast(256),
                            useMlock = false,
                            useMmap = useMmap,
                            batchSize = 256,
                            physicalBatchSize = 256,
                            prefillThreads = safePrefillThreads,
                            label = "cpu-safe",
                            keyCacheTypeOverride = vlmCacheK,
                            valueCacheTypeOverride = vlmCacheV
                        )
                    )
                }.distinctBy {
                    listOf(
                        it.gpuLayers,
                        it.contextSize,
                        it.useMlock,
                        it.useMmap,
                        it.batchSize,
                        it.physicalBatchSize,
                        it.prefillThreads,
                        it.keyCacheTypeOverride,
                        it.valueCacheTypeOverride
                    )
                }

                var loadedPtr = 0L
                var usedGpuLayers = requestedGpuLayers
                var usedContextSize = tunedContextSize
                var successfulAttempt: NativeLoadAttempt? = null
                for ((index, attempt) in loadAttempts.withIndex()) {
                    val timeoutMs = if (attempt.gpuLayers > 0 && index == 0) {
                        GPU_LOAD_ATTEMPT_TIMEOUT_MS
                    } else {
                        MODEL_LOAD_TIMEOUT_MS
                    }
                    Log.i(
                        TAG,
                        "Native load attempt [${attempt.label}] ctx=${attempt.contextSize}, gpu=${attempt.gpuLayers}, " +
                            "mlock=${attempt.useMlock}, mmap=${attempt.useMmap}, batch=${attempt.batchSize}, ubatch=${attempt.physicalBatchSize}, " +
                            "prefillThreads=${attempt.prefillThreads}"
                    )
                    val candidatePtr = runCatching {
                        withTimeout(timeoutMs) {
                            bridge.loadModel(
                                path = nativePath,
                                useMlock = attempt.useMlock,
                                useMmap = attempt.useMmap,
                                contextSize = attempt.contextSize,
                                threadCountDecode = tunedThreadCount,
                                threadCountPrefill = attempt.prefillThreads,
                                gpuLayers = attempt.gpuLayers,
                                batchSize = attempt.batchSize,
                                physicalBatchSize = attempt.physicalBatchSize,
                                flashAttention = flashAttention,
                                // POCKETPAL FIX: Per-attempt KV cache override use karo.
                                // Vision models ka override F16 hoga, text models ka user setting.
                                keyCacheType = attempt.keyCacheTypeOverride ?: keyCacheType,
                                valueCacheType = attempt.valueCacheTypeOverride ?: valueCacheType,
                                defragThreshold = defragThreshold,
                                kvUnified = true
                            )
                        }
                    }.getOrElse { throwable ->
                        if (attempt.gpuLayers > 0) {
                            Log.w(TAG, "GPU load attempt failed: ${throwable.message}")
                            gpuInitFailedInSession = true
                            runCatching { settingsRepository.setSkipGpuAfterFail(true) }
                            0L
                        } else {
                            Log.w(TAG, "Load attempt ${attempt.label} threw: ${throwable.message}")
                            0L
                        }
                    }
                    if (candidatePtr != 0L) {
                        loadedPtr = candidatePtr
                        usedGpuLayers = attempt.gpuLayers
                        usedContextSize = attempt.contextSize
                        successfulAttempt = attempt
                        break
                    }
                }

                if (loadedPtr == 0L) {
                    // CRASH FIX: Agar sab attempts fail hue toh RAM issue hai.
                    // User ko clear actionable message do.
                    // BUG FIX: Fresh RAM measurement lo  —  cached value stale hoti hai
                    val freshMemInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
                    val availRamMb = freshMemInfo.availMem / (1024 * 1024)
                    val modelSizeMb = modelFileSize / (1024 * 1024)
                    val reason = buildString {
                        append("Not enough RAM to load this model. ")
                        append("Model: ${modelSizeMb}MB, Available RAM: ~${availRamMb}MB. ")
                        append("Try: (1) Close other apps, (2) Restart phone, (3) Use a smaller model (1B Q4_K_M).")
                    }
                    return@withContext Result.failure(Exception(reason))
                }

                currentContextPtr = loadedPtr
                loadedModelKey = modelKey
                currentLoadedContextSize = usedContextSize
                openedModelDescriptor = pendingDescriptor
                pendingDescriptor = null

                // POCKETPAL FIX #3: Model load ke baad EOS token + chat template se
                // stop tokens auto-detect karo  —  same as PocketPal updateModelStopTokens().
                // Ye generics models ka actual EOS token detect karta hai (e.g. Llama's <|eot_id|>,
                // Qwen's <|im_end|>, DeepSeek's </s>) aur InferenceConfig ke stopTokens mein inject karta hai.
                updateAutoStopTokens(loadedPtr)
                detectThinkingSupport(loadedPtr)

                successfulAttempt?.let { attempt ->
                    persistEffectiveRuntimeSettingsIfNeeded(
                        requestedContextSize = requestedContextSize,
                        requestedBatchSize = batchSize,
                        requestedPhysicalBatchSize = physicalBatchSize,
                        requestedUseMlock = allowMlock,
                        requestedUseMmap = useMmap,
                        effectiveAttempt = attempt
                    )
                }

                if (usedGpuLayers > 0) {
                    gpuInitFailedInSession = false
                    runCatching { settingsRepository.setSkipGpuAfterFail(false) }
                } else if (gpuInitFailedInSession) {
                    runCatching { settingsRepository.setSkipGpuAfterFail(true) }
                }
                Log.i(TAG, "Model loaded successfully [gpu=$usedGpuLayers]")
                withContext(Dispatchers.Main) {
                    Result.success(Unit)
                }
            } finally {
                pendingDescriptor?.closeQuietly()
                releaseEngineLockIfHeld()
            }
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM while loading model", oom)
            Result.failure(IllegalStateException("Out of memory while loading model"))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Model load timed out", e)
            Result.failure(IllegalStateException("Model loading timed out"))
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native bridge error while loading model", e)
            Result.failure(IllegalStateException("Native engine not available"))
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            Result.failure(e)
        }
    }

    /**
     * Load an MMProj (Vision) model  —  PocketPal-style projector loading.
     * Requires main model to be loaded first (currentContextPtr != 0).
     */
    suspend fun loadProjector(projectorPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ptr = currentContextPtr
            if (ptr == 0L) {
                return@withContext Result.failure(Exception("No model loaded. Load a vision-capable model first."))
            }
            val file = File(projectorPath)
            if (!file.exists() || !file.canRead()) {
                return@withContext Result.failure(Exception("Projector file not found: $projectorPath"))
            }
            Log.i(TAG, "Loading vision projector: $projectorPath")
            val success = bridge.loadProjector(ptr, projectorPath)
            if (success) {
                Log.i(TAG, "Vision projector loaded successfully")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to load vision projector")
                Result.failure(Exception("Failed to load vision projector. Check model compatibility."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading projector", e)
            Result.failure(e)
        }
    }

    /**
     * Unload vision projector and free memory
     */
    suspend fun unloadProjector(): Unit = withContext(Dispatchers.IO) {
        val ptr = currentContextPtr
        if (ptr != 0L) {
            runCatching { bridge.unloadProjector(ptr) }
                .onSuccess { Log.i(TAG, "Vision projector unloaded") }
                .onFailure { Log.w(TAG, "Failed to unload projector", it) }
        }
    }

    /**
     * Process an image and generate response with vision model.
     * PocketPal-style: imageBytes directly pass karo native layer ko.
     */
    suspend fun processImage(@Suppress("UNUSED_PARAMETER") imageBytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        // Image processing is handled inline in generateWithVision (via JNI bridge).
        // This method is a no-op placeholder  —  actual image injection happens in generateWithVision call.
        if (currentContextPtr == 0L) {
            return@withContext Result.failure(Exception("No model loaded"))
        }
        Result.success(Unit)
    }

    /**
     * Get real-time performance metrics
     */
    fun getPerfMetrics(): com.tk854.localmind.core.engine.PerfMetrics? {
        val ptr = currentContextPtr
        return if (ptr != 0L) bridge.getPerfMetrics(ptr) else null
    }

    /**
     * Extract metadata from a GGUF model file without loading weights
     */
    suspend fun getModelMetadata(modelPath: String): Result<ModelMetadata> = withContext(Dispatchers.IO) {
        if (!isValidGgufFile(modelPath)) {
            return@withContext Result.failure(Exception("Invalid or corrupted GGUF file"))
        }
        Log.w(TAG, "Model metadata extraction is disabled on Android for stability")
        Result.failure(Exception("Model metadata extraction is disabled on this build for stability"))
    }

    /**
     * POCKETPAL PARITY: Build a JSON messages string for the native chat template API.
     * Format: [{"role":"system","content":"..."},{"role":"user","content":"..."},...]
     * Escapes special JSON characters in content strings.
     */
    fun buildMessagesJson(
        systemPrompt: String?,
        historyMessages: List<com.tk854.localmind.domain.model.Message>,
        currentUserInput: String,
        partialAssistantResponse: String? = null
    ): String {
        fun escJson(s: String) = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        val sb = StringBuilder("[")
        var first = true

        fun addMsg(role: String, content: String) {
            if (!first) sb.append(",")
            first = false
            sb.append("{\"role\":\"${escJson(role)}\",\"content\":\"${escJson(content)}\"}") 
        }

        if (!systemPrompt.isNullOrBlank()) addMsg("system", systemPrompt)
        for (msg in historyMessages) {
            val role = if (msg.role == com.tk854.localmind.domain.model.MessageRole.USER) "user" else "assistant"
            // FIX: Assistant messages se <think> blocks HAMESHA strip karo (for past history).
            // Reasoning text sirf UI (ThinkingBubble) ke liye hai  —  model ko wapas nahi milna chahiye.
            // Lekin agar hum CONTINUATION (refill) kar rahe hain, toh latest partial response wapas dena hoga.
            val cleanContent = if (role == "assistant") {
                msg.content
                    .replace(Regex("<think>[\\s\\S]*?</think>"), "")
                    .replace(Regex("<thought>[\\s\\S]*?</thought>"), "")
                    .replace(Regex("<thinking>[\\s\\S]*?</thinking>"), "")
                    .replace(Regex("<reasoning>[\\s\\S]*?</reasoning>"), "")
                    .replace(Regex("<\\|start_thinking\\|>[\\s\\S]*?<\\|end_thinking\\|>"), "")
                    .trim()
            } else {
                msg.content
            }
            if (cleanContent.isNotBlank()) addMsg(role, cleanContent)
        }
        addMsg("user", currentUserInput)
        
        // CONTINUATION FIX: Agar partial response hai, toh usse Assistant message ki tarah add karo
        // Lekin isse STRIP MAT karo (kyunki model ko continuation chahiye).
        if (!partialAssistantResponse.isNullOrBlank()) {
            addMsg("assistant", partialAssistantResponse)
        }

        sb.append("]")
        return sb.toString()
    }

    /**
     * POCKETPAL PARITY: Generate using native chat template (messages API).
     * Kotlin builds a structured messages JSON, C++ applies the model's own GGUF
     * chat template via llm_chat_apply_template()  —  same as PocketPal's jinja=true.
     */
    fun generateWithMessages(
        messagesJson: String,
        config: InferenceConfig = InferenceConfig.DEFAULT,
        shouldUpdateCache: Boolean = true
    ): Flow<GenerationResult> = callbackFlow {
        fun emitResult(result: GenerationResult): Boolean {
            val sendResult = trySend(result)
            if (sendResult.isFailure) {
                Log.w(TAG, "Dropping generation event (messages): $result")
                return false
            }
            return true
        }

        if (messagesJson.isBlank() || messagesJson == "[]") {
            emitResult(GenerationResult.Error("Messages cannot be empty"))
            close()
            return@callbackFlow
        }

        // TTFT FIX v2: engineMutex generation ke dauran HOLD NAHI karo.
        // Pehla problem: generateWithMessages() Flow engineMutex lock karta tha POORI generation ke dauran.
        // Iska matlab: model load hone ke baad bhi generate() 9s wait karta tha mutex ke liye.
        // New approach: agar mutex locked hai (model load ho raha hai) toh short wait karo,
        // phir turant release karo. Generation ke dauran C++ is_generating flag protection deta hai.
        val contextPtr: Long
        if (engineMutex.isLocked) {
            val waitResult = withTimeoutOrNull(30_000L) {
                engineMutex.lock()
                engineMutex.unlock()  // Turant release
                true
            }
            if (waitResult == null) {
                emitResult(GenerationResult.Error("Engine is busy. Please wait for current task to finish"))
                close()
                return@callbackFlow
            }
        }
        contextPtr = currentContextPtr

        fun releaseLock() { /* No-op: mutex nahi pakdi  —  C++ is_generating flag se protection */ }

        if (contextPtr == 0L) {
            emitResult(GenerationResult.Error("No model loaded"))
            close()
            return@callbackFlow
        }

        if (resetPrefixCacheOnNextGenerate) {
            runCatching { bridge.clearPrefixCache(contextPtr) }
                .onFailure { Log.w(TAG, "Failed to clear prefix cache", it) }
            resetPrefixCacheOnNextGenerate = false
        }

        isGenerating = true
        val tunedConfig = config
        val generationStartNs = System.nanoTime()
        var firstTokenNs: Long? = null
        var directTokenCount = 0
        val stopTokens = (tunedConfig.stopTokens + cleanedAutoStopTokens).distinct()

        // PocketPal-style: two simple accumulators, no Kotlin-side parser needed.
        // C++ ThinkingStateMachine already splits <think> vs answer before callbacks.
        val accumulatedText = StringBuilder()
        val accumulatedReasoning = StringBuilder()

        emitResult(GenerationResult.Started)

        val nativeJob = launch(Dispatchers.Default) {
            try {
                runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO) }

                bridge.generateWithMessages(
                    contextPtr = contextPtr,
                    messagesJson = messagesJson,
                    stopTokens = stopTokens.toTypedArray(),
                    temperature = tunedConfig.temperature,
                    topP = tunedConfig.topP,
                    topK = tunedConfig.topK,
                    repeatPenalty = tunedConfig.repeatPenalty,
                    penaltyLastN = cachedPenaltyLastN,
                    maxTokens = tunedConfig.maxTokens,
                    shouldUpdateCache = shouldUpdateCache,
                    cachePrompt = cachedCachePrompt,
                    defragThreshold = cachedDefragThreshold,
                    minP = tunedConfig.minP,
                    seed = tunedConfig.seed,
                    xtcThreshold = tunedConfig.xtcThreshold,
                    xtcProbability = tunedConfig.xtcProbability,
                    typicalP = tunedConfig.typicalP,
                    penaltyFreq = tunedConfig.penaltyFreq,
                    penaltyPresent = tunedConfig.penaltyPresent,
                    mirostat = tunedConfig.mirostat,
                    mirostatTau = tunedConfig.mirostatTau,
                    mirostatEta = tunedConfig.mirostatEta,
                    suppressThinkToken = !tunedConfig.enableThinking,
                    callback = object : GenerationCallback {
                        // C++ ThinkingStateMachine already routes:
                        //   onToken    = answer text (outside <think>)
                        //   onReasoning = reasoning text (inside <think>)
                        override fun onToken(token: String) {
                            directTokenCount++
                            if (firstTokenNs == null) firstTokenNs = System.nanoTime()
                            if (token.isNotEmpty()) {
                                accumulatedText.append(token)
                                if (!emitResult(GenerationResult.Token(token))) {
                                    runCatching { bridge.stopGeneration(contextPtr) }
                                }
                            }
                        }

                        override fun onReasoning(token: String) {
                            if (firstTokenNs == null) firstTokenNs = System.nanoTime()
                            if (token.isNotEmpty()) {
                                accumulatedReasoning.append(token)
                                emitResult(GenerationResult.Reasoning(token))
                            }
                        }

                        override fun onComplete() {
                            val totalMs = ((System.nanoTime() - generationStartNs) / 1_000_000L).coerceAtLeast(1L)
                            val ttftMs = firstTokenNs?.let { ((it - generationStartNs) / 1_000_000L).coerceAtLeast(0L) }
                            val tps = if (directTokenCount > 0) directTokenCount.toFloat() * 1000f / totalMs else 0f
                            lastDirectTelemetry = InferenceTelemetry(
                                source = InferenceSource.LOCAL,
                                ttftMs = ttftMs,
                                totalTimeMs = totalMs,
                                tokensGenerated = directTokenCount,
                                tokensPerSecond = tps
                            )
                            emitResult(GenerationResult.Complete)
                            close()
                        }

                        override fun onError(error: String) {
                            emitResult(GenerationResult.Error(mapNativeGenerationError(error)))
                            close()
                        }
                    }
                )
            } catch (cancel: CancellationException) {
                runCatching { bridge.stopGeneration(contextPtr) }
                throw cancel
            } catch (e: Exception) {
                Log.e(TAG, "generateWithMessages error", e)
                emitResult(GenerationResult.Error(mapNativeGenerationError(e.message)))
                close()
            }
        }

        generationJob = nativeJob
        nativeJob.invokeOnCompletion {
            generationJob = null
            isGenerating = false
            releaseLock()
        }

        awaitClose {
            if (nativeJob.isActive) {
                runCatching { bridge.stopGeneration(contextPtr) }
                nativeJob.cancel()
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Generate response with streaming tokens
     */
    fun generate(
        prompt: String,
        config: InferenceConfig = InferenceConfig.DEFAULT,
        shouldUpdateCache: Boolean = true
    ): Flow<GenerationResult> = callbackFlow {
        fun emitResult(result: GenerationResult): Boolean {
            val sendResult = trySend(result)
            if (sendResult.isFailure) {
                Log.w(TAG, "Dropping generation event because channel is closed: $result")
                return false
            }
            return true
        }

        val preparedPrompt = prompt.trim()
        if (preparedPrompt.isBlank()) {
            emitResult(GenerationResult.Error("Prompt cannot be empty"))
            close()
            return@callbackFlow
        }

        // TTFT FIX v2: engineMutex generation ke dauran HOLD NAHI karo.
        // Pehla problem: generate() Flow engineMutex lock karta tha POORI generation ke dauran.
        // Iska matlab: agar pehli generation chal rahi ho toh doosri 9s wait karti thi mutex ke liye.
        // New approach: sirf contextPtr safely read karo, phir mutex release karo.
        // Generation ke dauran isGenerating flag aur should_stop C++ flag protection dete hain.
        // Ye PocketPal ke approach se match karta hai  —  wahan koi Kotlin-level mutex nahi hai.

        // Agar mutex locked hai (model load ho raha hai), short wait karo
        val modelLoadWaitMs = 30_000L
        val contextPtr: Long
        if (engineMutex.isLocked) {
            // Model abhi load ho raha hai  —  wait karo phir context read karo
            val waitResult = withTimeoutOrNull(modelLoadWaitMs) {
                engineMutex.lock()
                engineMutex.unlock()  // Turant release  —  sirf ensure karo load complete hua
                true
            }
            if (waitResult == null) {
                emitResult(GenerationResult.Error("Engine is busy. Please wait for current task to finish"))
                close()
                return@callbackFlow
            }
        }
        contextPtr = currentContextPtr

        // Mutex generation ke dauran nahi pakdi  —  sirf ek dummy releaseLock() rakhte hain
        // compatibility ke liye (invokeOnCompletion mein call hoga)
        fun releaseLock() { /* No-op: mutex nahi pakdi */ }

        if (contextPtr == 0L) {
            emitResult(GenerationResult.Error("No model loaded"))
            close()
            return@callbackFlow
        }

        if (resetPrefixCacheOnNextGenerate) {
            runCatching { bridge.clearPrefixCache(contextPtr) }
                .onFailure { Log.w(TAG, "Failed to clear prefix cache before next generation", it) }
            resetPrefixCacheOnNextGenerate = false
        }

        isGenerating = true
        val tunedConfig = config
        val generationStartNs = System.nanoTime()
        var firstTokenNs: Long? = null
        var directTokenCount = 0
        val stopTokens = (tunedConfig.stopTokens + cleanedAutoStopTokens).distinct()

        // PocketPal-style: two simple accumulators.
        // C++ ThinkingStateMachine routes <think> -> onReasoning, rest -> onToken.
        val accumulatedText = StringBuilder()
        val accumulatedReasoning = StringBuilder()

        emitResult(GenerationResult.Started)

        val nativeJob = launch(Dispatchers.Default) {
            try {
                runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO) }

                bridge.generate(
                    contextPtr = contextPtr,
                    prompt = preparedPrompt,
                    stopTokens = stopTokens.toTypedArray(),
                    temperature = tunedConfig.temperature,
                    topP = tunedConfig.topP,
                    topK = tunedConfig.topK,
                    repeatPenalty = tunedConfig.repeatPenalty,
                    penaltyLastN = cachedPenaltyLastN,
                    maxTokens = tunedConfig.maxTokens,
                    shouldUpdateCache = shouldUpdateCache,
                    cachePrompt = cachedCachePrompt,
                    defragThreshold = cachedDefragThreshold,
                    minP = tunedConfig.minP,
                    seed = tunedConfig.seed,
                    xtcThreshold = tunedConfig.xtcThreshold,
                    xtcProbability = tunedConfig.xtcProbability,
                    typicalP = tunedConfig.typicalP,
                    penaltyFreq = tunedConfig.penaltyFreq,
                    penaltyPresent = tunedConfig.penaltyPresent,
                    mirostat = tunedConfig.mirostat,
                    mirostatTau = tunedConfig.mirostatTau,
                    mirostatEta = tunedConfig.mirostatEta,
                    suppressThinkToken = !tunedConfig.enableThinking,
                    callback = object : GenerationCallback {
                        override fun onToken(token: String) {
                            directTokenCount++
                            if (firstTokenNs == null) firstTokenNs = System.nanoTime()
                            if (token.isNotEmpty()) {
                                accumulatedText.append(token)
                                if (!emitResult(GenerationResult.Token(token))) {
                                    runCatching { bridge.stopGeneration(contextPtr) }
                                }
                            }
                        }

                        override fun onReasoning(token: String) {
                            if (firstTokenNs == null) firstTokenNs = System.nanoTime()
                            if (token.isNotEmpty()) {
                                accumulatedReasoning.append(token)
                                emitResult(GenerationResult.Reasoning(token))
                            }
                        }

                        override fun onComplete() {
                            val totalMs = ((System.nanoTime() - generationStartNs) / 1_000_000L).coerceAtLeast(1L)
                            val ttftMs = firstTokenNs?.let { ((it - generationStartNs) / 1_000_000L).coerceAtLeast(0L) }
                            val tps = if (directTokenCount > 0) directTokenCount.toFloat() * 1000f / totalMs else 0f
                            lastDirectTelemetry = InferenceTelemetry(
                                source = InferenceSource.LOCAL,
                                ttftMs = ttftMs,
                                totalTimeMs = totalMs,
                                tokensGenerated = directTokenCount,
                                tokensPerSecond = tps
                            )
                            emitResult(GenerationResult.Complete)
                            close()
                        }

                        override fun onError(error: String) {
                            emitResult(GenerationResult.Error(mapNativeGenerationError(error)))
                            close()
                        }
                    }
                )
            } catch (oom: OutOfMemoryError) {
                emitResult(GenerationResult.Error("Out of memory during generation"))
                close()
            } catch (cancel: CancellationException) {
                runCatching { bridge.stopGeneration(contextPtr) }
                throw cancel
            } catch (e: Exception) {
                Log.e(TAG, "Generation error", e)
                emitResult(GenerationResult.Error(mapNativeGenerationError(e.message)))
                close()
            }
        }

        generationJob = nativeJob
        nativeJob.invokeOnCompletion {
            generationJob = null
            isGenerating = false
            releaseLock()
        }

        awaitClose {
            if (nativeJob.isActive) {
                runCatching { bridge.stopGeneration(contextPtr) }
                nativeJob.cancel()
            }
            // The lock will be released in invokeOnCompletion of nativeJob
            // ensuring the native thread has fully returned before next operation.
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Stop ongoing generation
     */
    fun stopGeneration() {
        val contextPtr = currentContextPtr
        if (contextPtr != 0L) {
            // NOTE: resetPrefixCacheOnNextGenerate = false intentionally.
            // Agar stop ke baad dobara generate karo toh prefix cache valid rehta hai  — 
            // sirf context switch (naya conversation) pe cache clear karo.
            // Purana bug: har stop pe cache clear hota tha = har send pe full reprefill = high TTFT.
            runCatching { bridge.stopGeneration(contextPtr) }
            generationJob?.cancel()
            isGenerating = false
            Log.i(TAG, "Generation stop requested")
        }
    }

    /**
     * TTFT FIX v2: Mutex ab generation ke dauran hold nahi hoti.
     * Ye function sirf model LOAD ke dauran wait karta hai.
     * (generate() Flow ab mutex-free hai  —  C++ is_generating flag se protected hai.)
     */
    fun isMutexLocked(): Boolean = engineMutex.isLocked

    suspend fun waitForMutexRelease(timeoutMs: Long = 3_000L) {
        if (!engineMutex.isLocked) return
        // Model load ho raha hai  —  wait karo
        kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            engineMutex.lock()
            engineMutex.unlock()
        }
    }

    /**
     * Unload current model and free memory
     */
    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        if (!acquireEngineLock()) {
            Log.w(TAG, "Skipping unload because engine is busy")
            return@withContext
        }

        try {
            stopGenerationInternal()
            if (currentContextPtr != 0L) {
                try {
                    Log.i(TAG, "Unloading model")
                    bridge.unloadModel(currentContextPtr)
                    currentContextPtr = 0L
                    loadedModelKey = null
                    Log.i(TAG, "Model unloaded")
                } catch (e: Exception) {
                    Log.e(TAG, "Error unloading model", e)
                }
            }
            openedModelDescriptor?.closeQuietly()
            openedModelDescriptor = null
        } finally {
            releaseEngineLockIfHeld()
        }
    }

    /**
     * Check if model is currently loaded
     */
    fun isModelLoaded(): Boolean = currentContextPtr != 0L

    /**
     * Check if generation is in progress
     */
    fun isGenerating(): Boolean = isGenerating

    /**
     * Get available RAM in GB
     */
    fun getAvailableRAMGB(): Int {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.availMem / (1024 * 1024 * 1024)).toInt()
    }

    private var currentLoadedContextSize: Int = 0

    /**
     * Get details of the currently loaded model
     */
    fun getLoadedModelDetails(): Pair<String?, Int> {
        return Pair(loadedModelKey, currentLoadedContextSize)
    }

    private suspend fun acquireEngineLock(): Boolean {
        return withTimeoutOrNull(ENGINE_LOCK_TIMEOUT_MS) {
            engineMutex.lock()
            true
        } ?: false
    }

    private fun releaseEngineLockIfHeld() {
        if (engineMutex.isLocked) {
            runCatching { engineMutex.unlock() }
        }
    }

    private suspend fun stopGenerationInternal() {
        val contextPtr = currentContextPtr
        if (contextPtr != 0L && (isGenerating || generationJob?.isActive == true)) {
            runCatching { bridge.stopGeneration(contextPtr) }
        }
        generationJob?.let { job ->
            withTimeoutOrNull(3_000L) {
                job.join()
            }
            if (job.isActive) {
                job.cancel()
            }
        }
        generationJob = null
        isGenerating = false
    }

    private fun sanitizePrompt(prompt: String): String {
        return prompt
    }

    private suspend fun persistEffectiveRuntimeSettingsIfNeeded(
        requestedContextSize: Int,
        requestedBatchSize: Int,
        requestedPhysicalBatchSize: Int,
        requestedUseMlock: Boolean,
        requestedUseMmap: Boolean,
        effectiveAttempt: NativeLoadAttempt
    ) {
        if (effectiveAttempt.contextSize != requestedContextSize) {
            runCatching { settingsRepository.updateContextSize(effectiveAttempt.contextSize) }
                .onSuccess {
                    Log.i(TAG, "Auto-adjusted context size to stable value: ${effectiveAttempt.contextSize}")
                }
        }
        if (effectiveAttempt.batchSize != requestedBatchSize) {
            runCatching { settingsRepository.updateBatchSize(effectiveAttempt.batchSize) }
                .onSuccess {
                    Log.i(TAG, "Auto-adjusted batch size to stable value: ${effectiveAttempt.batchSize}")
                }
        }
        if (effectiveAttempt.physicalBatchSize != requestedPhysicalBatchSize) {
            runCatching { settingsRepository.updatePhysicalBatchSize(effectiveAttempt.physicalBatchSize) }
                .onSuccess {
                    Log.i(TAG, "Auto-adjusted physical batch size to stable value: ${effectiveAttempt.physicalBatchSize}")
                }
        }
        if (effectiveAttempt.useMlock != requestedUseMlock) {
            runCatching { settingsRepository.updateUseMlock(effectiveAttempt.useMlock) }
                .onSuccess {
                    Log.i(TAG, "Auto-adjusted mlock to stable value: ${effectiveAttempt.useMlock}")
                }
        }
        if (effectiveAttempt.useMmap != requestedUseMmap) {
            runCatching { settingsRepository.updateUseMmap(effectiveAttempt.useMmap) }
                .onSuccess {
                    Log.i(TAG, "Auto-adjusted mmap flag to stable value: ${effectiveAttempt.useMmap}")
                }
        }
    }

    private fun mapNativeGenerationError(rawMessage: String?): String {
        val message = rawMessage.orEmpty()
        return when {
            message.contains("Prompt too long for context", ignoreCase = true) ||
                message.contains("Failed to evaluate prompt", ignoreCase = true) -> {
                "Prompt too long for current context. Try shorter input or lower context size."
            }
            message.contains("Tokenization failed", ignoreCase = true) -> {
                "Prompt could not be tokenized. Try simpler input."
            }
            message.isBlank() -> "Generation failed"
            else -> message
        }
    }

    private fun isValidGgufFile(path: String): Boolean {
        // SAF models use /proc/self/fd/XX paths  —  the file is already opened via
        // a valid ParcelFileDescriptor, so skip the magic-number check.
        if (path.startsWith("/proc/self/fd/")) return true
        if (!path.endsWith(Constants.GGUF_EXTENSION, ignoreCase = true)) return false
        val file = File(path)
        if (!file.exists() || file.length() < 1024) return false // Too small to be valid

        // Check magic number "GGUF"
        return try {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                if (input.read(magic) != 4) return false
                // GGUF magic is 'G', 'G', 'U', 'F' (0x46554747 in LE)
                magic[0] == 'G'.code.toByte() &&
                magic[1] == 'G'.code.toByte() &&
                magic[2] == 'U'.code.toByte() &&
                magic[3] == 'F'.code.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                cancelCurrentInference()
                scope.launch { _memoryWarning.emit("Memory low detected. Keeping loaded model resident with current runtime settings.") }
            }
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                cancelCurrentInference()
                scope.launch { _memoryWarning.emit("System memory pressure detected. Model kept in memory per configuration.") }
            }
        }
    }

    override fun onConfigurationChanged(config: Configuration) {
    }

    override fun onLowMemory() {
        onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }

    fun destroy() {
        context.unregisterComponentCallbacks(this)
        // BUG FIX: scope cancel karo warna coroutines leak hote hain.
        // @Singleton hai isliye app lifetime tak rahega, lekin test/cleanup ke liye zaroori hai.
        scope.cancel()
    }

    private fun cancelCurrentInference() {
        stopGeneration()
    }

}

private fun ParcelFileDescriptor.closeQuietly() {
    runCatching { close() }
}

/**
 * Generation result sealed class for streaming
 */
sealed class GenerationResult {
    object Started : GenerationResult()
    /** Raw answer token  —  reasoning tags already stripped */
    data class Token(val text: String) : GenerationResult()
    /** Reasoning/thinking token  —  extracted from <think>...</think> and variants */
    data class Reasoning(val text: String) : GenerationResult()
    object Complete : GenerationResult()
    data class Error(val message: String) : GenerationResult()
}


