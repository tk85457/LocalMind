package com.localmind.app.llm

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.util.Log
import com.localmind.app.core.Constants
import com.localmind.app.core.engine.ModelMetadata
import com.localmind.app.core.performance.DeviceProfileManager
import com.localmind.app.core.storage.ModelStorageType
import com.localmind.app.core.utils.GpuCapabilityChecker
import com.localmind.app.domain.model.InferenceSource
import com.localmind.app.llm.nativelib.GenerationCallback
import com.localmind.app.llm.nativelib.LlamaCppBridge
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val settingsRepository: com.localmind.app.data.repository.SettingsRepository,
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
    // PocketPal mein ye MobX in-memory observable mein store hote hain — zero disk reads.
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

    // FIX #5: LLMEngine ka apna telemetry store — HybridInferenceRouter bypass hone par
    // ChatRepository.getLastInferenceTelemetry() is se read karta hai.
    @Volatile private var lastDirectTelemetry: InferenceTelemetry? = null

    fun getLastTelemetry(): InferenceTelemetry? = lastDirectTelemetry

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val engineMutex = Mutex()
    private var generationJob: kotlinx.coroutines.Job? = null
    @Volatile private var resetPrefixCacheOnNextGenerate: Boolean = false

    // POCKETPAL FIX #3: Auto-detected stop tokens from model's EOS + chat template.
    // PocketPal: updateModelStopTokens() ke baad model.stopWords mein store hote hain.
    // Hum ye generate() calls mein tunedConfig.stopTokens ke saath merge karte hain.
    @Volatile var autoDetectedStopTokens: List<String> = emptyList()
        private set

    // PocketPal ke stops list ke same tokens (from chat.ts)
    private val KNOWN_STOP_TOKENS = setOf(
        "</s>", "<|eot_id|>", "<|end_of_text|>", "<|im_end|>", "<|EOT|>",
        "<|END_OF_TURN_TOKEN|>", "<|end_of_turn|>", "<end_of_turn>", "</end_of_turn>",
        "<start_of_turn>", "</start_of_turn>",
        "<|endoftext|>", "<|end|>", "<|return|>"
    )

    /**
     * POCKETPAL PARITY: Model load ke baad EOS + chat template se stop tokens detect karo.
     * PocketPal: updateModelStopTokens() in ModelStore.
     * Detected tokens ko generate() aur generateWithMessages() calls mein merge karo.
     */
    private fun updateAutoStopTokens(contextPtr: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                val detected = mutableSetOf<String>()

                // Step 1: EOS token from vocab (PocketPal: ctx.detokenize([eos_token_id]))
                val eosToken = runCatching { bridge.getEosToken(contextPtr) }.getOrNull()
                if (!eosToken.isNullOrBlank()) {
                    detected.add(eosToken.trim())
                    Log.i(TAG, "AutoStopTokens: EOS='$eosToken'")
                }

                // Step 2: Chat template se known stop tokens scan karo
                // (PocketPal: stops.filter(stop => template.includes(stop)))
                val chatTemplate = runCatching { bridge.getChatTemplate(contextPtr) }.getOrNull()
                if (!chatTemplate.isNullOrBlank()) {
                    for (token in KNOWN_STOP_TOKENS) {
                        if (chatTemplate.contains(token)) {
                            detected.add(token)
                        }
                    }
                    Log.i(TAG, "AutoStopTokens: template scan found ${detected.size} stop tokens")
                }

                autoDetectedStopTokens = detected.toList()
                Log.i(TAG, "AutoStopTokens: final list=${autoDetectedStopTokens}")
            } catch (e: Exception) {
                Log.w(TAG, "AutoStopTokens detection failed (non-critical)", e)
            }
        }
    }

    private val _memoryWarning = MutableSharedFlow<String>()
    val memoryWarning = _memoryWarning.asSharedFlow()

    private val inferenceChannel = Channel<InferenceRequest>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var draftModelLoaded = false

    init {
        context.registerComponentCallbacks(this)
        scope.launch { processInferenceQueue() }
        startSettingsCache()  // PERF FIX: start in-memory settings cache
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
        val label: String
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
            // Q4_0 aur IQ4_NL quantizations "repackable" hain — llama.cpp inhe
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

            // POCKETPAL FIX: Hardware GPU check — Adreno + i8mm + dotprod teeno chahiye.
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
            val keyCacheType = rawKeyCacheType
            val valueCacheType = rawValueCacheType
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
                    val isSmallModel = modelFileSize < 4.0e9 // Roughly < 3.5B params
                    // RELAXED: 4GB+ RAM is enough for small models offloading
                    if (totalRamGb >= 4 && isSmallModel) 100 else 0
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

                val safeBatchSize = (batchSize / 2).coerceIn(1, batchSize)
                val safePhysicalBatchSize = (physicalBatchSize / 2).coerceIn(1, safeBatchSize)
                val ultraSafeBatchSize = (batchSize / 4).coerceIn(1, safeBatchSize)
                val ultraSafePhysicalBatchSize = (physicalBatchSize / 4).coerceIn(1, ultraSafeBatchSize)
                val preferredPrefillThreads = tunedThreadCount.coerceAtLeast(Constants.MIN_THREADS)
                val safePrefillThreads = (preferredPrefillThreads / 2).coerceAtLeast(Constants.MIN_THREADS)

                val loadAttempts = buildList {
                    gpuLayerCandidates.forEachIndexed { index, candidateGpuLayers ->
                        add(
                            NativeLoadAttempt(
                                gpuLayers = candidateGpuLayers,
                                contextSize = tunedContextSize,
                                useMlock = allowMlock,
                                useMmap = useMmap,
                                batchSize = batchSize,
                                physicalBatchSize = physicalBatchSize,
                                prefillThreads = preferredPrefillThreads,
                                label = if (candidateGpuLayers > 0 && index == 0) "gpu-primary" else "cpu-primary"
                            )
                        )
                    }
                    add(
                        NativeLoadAttempt(
                            gpuLayers = 0,
                            contextSize = tunedContextSize,
                            useMlock = allowMlock,
                            useMmap = useMmap,
                            batchSize = safeBatchSize,
                            physicalBatchSize = safePhysicalBatchSize,
                            prefillThreads = safePrefillThreads,  // decode threads same as prefill in safe mode
                            label = "cpu-safe"
                        )
                    )
                    add(
                        NativeLoadAttempt(
                            gpuLayers = 0,
                            contextSize = tunedContextSize,
                            useMlock = allowMlock,
                            useMmap = useMmap,
                            batchSize = ultraSafeBatchSize,
                            physicalBatchSize = ultraSafePhysicalBatchSize,
                            prefillThreads = (tunedThreadCount / 2).coerceAtLeast(Constants.MIN_THREADS),  // POCKETPAL FIX: was MIN_THREADS=1, too slow
                            label = "cpu-ultra-safe"
                        )
                    )
                    if (allowMlock) {
                        add(
                            NativeLoadAttempt(
                                gpuLayers = 0,
                                contextSize = tunedContextSize,
                                useMlock = false,
                                useMmap = useMmap,
                                batchSize = safeBatchSize,
                                physicalBatchSize = safePhysicalBatchSize,
                                prefillThreads = safePrefillThreads,
                                label = "cpu-safe-no-mlock"
                            )
                        )
                        add(
                            NativeLoadAttempt(
                                gpuLayers = 0,
                                contextSize = tunedContextSize,
                                useMlock = false,
                                useMmap = useMmap,
                                batchSize = ultraSafeBatchSize,
                                physicalBatchSize = ultraSafePhysicalBatchSize,
                                prefillThreads = Constants.MIN_THREADS,
                                label = "cpu-ultra-safe-no-mlock"
                            )
                        )
                    }
                    if (useMmap) {
                        add(
                            NativeLoadAttempt(
                                gpuLayers = 0,
                                contextSize = tunedContextSize,
                                useMlock = allowMlock,
                                useMmap = false,
                                batchSize = safeBatchSize,
                                physicalBatchSize = safePhysicalBatchSize,
                                prefillThreads = safePrefillThreads,
                                label = "cpu-safe-no-mmap"
                            )
                        )
                        add(
                            NativeLoadAttempt(
                                gpuLayers = 0,
                                contextSize = tunedContextSize,
                                useMlock = allowMlock,
                                useMmap = false,
                                batchSize = ultraSafeBatchSize,
                                physicalBatchSize = ultraSafePhysicalBatchSize,
                                prefillThreads = Constants.MIN_THREADS,
                                label = "cpu-ultra-safe-no-mmap"
                            )
                        )
                    }
                }.distinctBy {
                    listOf(
                        it.gpuLayers,
                        it.contextSize,
                        it.useMlock,
                        it.useMmap,
                        it.batchSize,
                        it.physicalBatchSize,
                        it.prefillThreads
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
                                threadCountDecode = tunedThreadCount,  // POCKETPAL FIX: Was hardcoded 2! PocketPal uses 80% of cores for decode too.
                                threadCountPrefill = attempt.prefillThreads,
                                gpuLayers = attempt.gpuLayers,
                                batchSize = attempt.batchSize,
                                physicalBatchSize = attempt.physicalBatchSize,
                                flashAttention = flashAttention,
                                keyCacheType = keyCacheType,
                                valueCacheType = valueCacheType,
                                defragThreshold = defragThreshold,
                                kvUnified = true // POCKETPAL FIX: memory savings critical
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
                    val reason = buildString {
                        append("Native model initialization failed")
                        append(" [requestedCtx=$requestedContextSize, tunedCtx=$tunedContextSize]")
                        append(". Try lower context/batch in Settings or re-download model file.")
                    }
                    return@withContext Result.failure(Exception(reason))
                }

                currentContextPtr = loadedPtr
                loadedModelKey = modelKey
                currentLoadedContextSize = usedContextSize
                openedModelDescriptor = pendingDescriptor
                pendingDescriptor = null

                // POCKETPAL FIX #3: Model load ke baad EOS token + chat template se
                // stop tokens auto-detect karo — same as PocketPal updateModelStopTokens().
                // Ye generics models ka actual EOS token detect karta hai (e.g. Llama's <|eot_id|>,
                // Qwen's <|im_end|>, DeepSeek's </s>) aur InferenceConfig ke stopTokens mein inject karta hai.
                updateAutoStopTokens(loadedPtr)

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
     * Load an MMProj (Vision) model — PocketPal-style projector loading.
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
        // This method is a no-op placeholder — actual image injection happens in generateWithVision call.
        if (currentContextPtr == 0L) {
            return@withContext Result.failure(Exception("No model loaded"))
        }
        Result.success(Unit)
    }

    /**
     * Get real-time performance metrics
     */
    fun getPerfMetrics(): com.localmind.app.core.engine.PerfMetrics? {
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
        historyMessages: List<com.localmind.app.domain.model.Message>,
        currentUserInput: String
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
            val role = if (msg.role == com.localmind.app.domain.model.MessageRole.USER) "user" else "assistant"
            if (msg.content.isNotBlank()) addMsg(role, msg.content)
        }
        addMsg("user", currentUserInput)

        sb.append("]")
        return sb.toString()
    }

    /**
     * POCKETPAL PARITY: Generate using native chat template (messages API).
     * Kotlin builds a structured messages JSON, C++ applies the model's own GGUF
     * chat template via llm_chat_apply_template() — same as PocketPal's jinja=true.
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

        fun releaseLock() { /* No-op: mutex nahi pakdi — C++ is_generating flag se protection */ }

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
        val stopTokens = tunedConfig.stopTokens.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val maxStopTokenLength = stopTokens.maxOfOrNull { it.length } ?: 0
        val flushThreshold = maxStopTokenLength + 1
        val pendingTokenBuffer = StringBuilder()
        var stopReached = false
        var emittedTokenCount = 0

        fun findTrailingStopTokenLength(buffer: String): Int {
            if (stopTokens.isEmpty()) return 0
            for (token in stopTokens) { if (buffer.endsWith(token)) return token.length }
            return 0
        }

        fun emitBufferedPrefix(prefixLength: Int) {
            if (prefixLength <= 0) return
            val chunk = pendingTokenBuffer.substring(0, prefixLength)
            pendingTokenBuffer.delete(0, prefixLength)
            if (chunk.isNotEmpty()) {
                if (!emitResult(GenerationResult.Token(chunk))) {
                    runCatching { bridge.stopGeneration(contextPtr) }
                } else {
                    emittedTokenCount += 1
                }
            }
        }

        fun processCleanText(text: String) {
            if (stopTokens.isEmpty()) {
                if (!emitResult(GenerationResult.Token(text))) runCatching { bridge.stopGeneration(contextPtr) }
                else emittedTokenCount += 1
                return
            }
            pendingTokenBuffer.append(text)
            val trailingStopLen = findTrailingStopTokenLength(pendingTokenBuffer.toString())
            if (trailingStopLen > 0) {
                emitBufferedPrefix((pendingTokenBuffer.length - trailingStopLen).coerceAtLeast(0))
                pendingTokenBuffer.clear()
                stopReached = true
                runCatching { bridge.stopGeneration(contextPtr) }
                return
            }
            if (pendingTokenBuffer.length > flushThreshold) {
                val keepTail = (maxStopTokenLength - 1).coerceAtLeast(0)
                val flushLength = pendingTokenBuffer.length - keepTail
                emitBufferedPrefix(flushLength)
            }
        }

        emitResult(GenerationResult.Started)

        val nativeJob = launch(Dispatchers.Default) {
            try {
                runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO) }
                val penaltyLastN = cachedPenaltyLastN
                val cachePrompt = cachedCachePrompt
                val defragThreshold = cachedDefragThreshold

                bridge.generateWithMessages(
                    contextPtr = contextPtr,
                    messagesJson = messagesJson,
                    stopTokens = stopTokens.toTypedArray(),
                    temperature = tunedConfig.temperature,
                    topP = tunedConfig.topP,
                    topK = tunedConfig.topK,
                    repeatPenalty = tunedConfig.repeatPenalty,
                    penaltyLastN = penaltyLastN,
                    maxTokens = tunedConfig.maxTokens,
                    shouldUpdateCache = shouldUpdateCache,
                    cachePrompt = cachePrompt,
                    defragThreshold = defragThreshold,
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
                    callback = object : GenerationCallback {
                        private val tokenBuffer = StringBuilder()
                        private val IGNORED_TAG_PATTERN = Regex("<\\|[a-zA-Z0-9_]+\\|>")

                        override fun onToken(token: String) {
                            if (stopReached) return
                            directTokenCount++
                            if (firstTokenNs == null) firstTokenNs = System.nanoTime()

                            tokenBuffer.append(token)
                            val bufferStr = tokenBuffer.toString()

                            val rawTrailingStopLen = findTrailingStopTokenLength(bufferStr)
                            if (rawTrailingStopLen > 0) {
                                stopReached = true
                                runCatching { bridge.stopGeneration(contextPtr) }
                                val cleanBeforeStop = IGNORED_TAG_PATTERN.replace(
                                    bufferStr.substring(0, bufferStr.length - rawTrailingStopLen), "")
                                if (cleanBeforeStop.isNotEmpty()) processCleanText(cleanBeforeStop)
                                tokenBuffer.clear()
                                return
                            }

                            val bufLen = tokenBuffer.length
                            if (tokenBuffer[bufLen - 1] == '<') return
                            if (bufLen >= 2 && tokenBuffer[bufLen - 2] == '<' && tokenBuffer[bufLen - 1] == '|') return
                            val lastOpen = bufferStr.lastIndexOf("<|")
                            if (lastOpen != -1 && !bufferStr.substring(lastOpen).contains("|>") && bufLen - lastOpen < 64) return

                            val currentCleanText = IGNORED_TAG_PATTERN.replace(bufferStr, "")
                            if (currentCleanText.isNotEmpty()) processCleanText(currentCleanText)
                            tokenBuffer.clear()
                        }

                        override fun onComplete() {
                            var remaining = IGNORED_TAG_PATTERN.replace(tokenBuffer.toString(), "")
                            if (!stopReached && remaining.isNotEmpty()) processCleanText(remaining)
                            if (!stopReached && pendingTokenBuffer.isNotEmpty()) emitBufferedPrefix(pendingTokenBuffer.length)

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
        // Ye PocketPal ke approach se match karta hai — wahan koi Kotlin-level mutex nahi hai.

        // Agar mutex locked hai (model load ho raha hai), short wait karo
        val modelLoadWaitMs = 30_000L
        val contextPtr: Long
        if (engineMutex.isLocked) {
            // Model abhi load ho raha hai — wait karo phir context read karo
            val waitResult = withTimeoutOrNull(modelLoadWaitMs) {
                engineMutex.lock()
                engineMutex.unlock()  // Turant release — sirf ensure karo load complete hua
                true
            }
            if (waitResult == null) {
                emitResult(GenerationResult.Error("Engine is busy. Please wait for current task to finish"))
                close()
                return@callbackFlow
            }
        }
        contextPtr = currentContextPtr

        // Mutex generation ke dauran nahi pakdi — sirf ek dummy releaseLock() rakhte hain
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
        // PERF FIX: Config is already tuned by ChatViewModel/ModelLifecycleManager.
        // Removed redundant tuneInferenceConfig call that was double-clamping params.
        val tunedConfig = config

        // FIX #5: Telemetry tracking for direct LLMEngine calls (bypass router).
        val generationStartNs = System.nanoTime()
        var firstTokenNs: Long? = null
        var directTokenCount = 0
        // POCKETPAL FIX #3: Auto-detected stop tokens (EOS + chat template) merge karo.
        // PocketPal: model.stopWords already has these after updateModelStopTokens().
        val stopTokens = (tunedConfig.stopTokens + autoDetectedStopTokens)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val maxStopTokenLength = stopTokens.maxOfOrNull { it.length } ?: 0
        // PERF FIX: Flush buffer as soon as safe instead of accumulating 16+ chars.
        // Old threshold caused visible stuttering (tokens appeared in bursts).
        val flushThreshold = maxStopTokenLength + 1
        val pendingTokenBuffer = StringBuilder()
        var stopReached = false
        var tokenCountSinceMemCheck = 0
        var lastMemCheckAtMs = 0L
        var emittedTokenCount = 0

        fun shouldCheckMemoryPressure(): Boolean {
            tokenCountSinceMemCheck += 1
            val now = System.currentTimeMillis()
            val intervalReached = tokenCountSinceMemCheck >= 64
            val timeReached = (now - lastMemCheckAtMs) >= 2000L
            return if (intervalReached || timeReached) {
                tokenCountSinceMemCheck = 0
                lastMemCheckAtMs = now
                true
            } else {
                false
            }
        }

        fun emitBufferedPrefix(prefixLength: Int) {
            if (prefixLength <= 0) return
            val chunk = pendingTokenBuffer.substring(0, prefixLength)
            pendingTokenBuffer.delete(0, prefixLength)
            if (chunk.isNotEmpty()) {
                if (!emitResult(GenerationResult.Token(chunk))) {
                    runCatching { bridge.stopGeneration(contextPtr) }
                } else {
                    emittedTokenCount += 1
                }
            }
        }

        fun findTrailingStopTokenLength(buffer: String): Int {
            if (stopTokens.isEmpty()) return 0
            for (token in stopTokens) {
                if (buffer.endsWith(token)) {
                    return token.length
                }
            }
            return 0
        }

        emitResult(GenerationResult.Started)

        val nativeJob = launch(Dispatchers.Default) {
            try {
                // PERF FIX: THREAD_PRIORITY_MORE_FAVORABLE → THREAD_PRIORITY_URGENT_AUDIO
                // PocketPal mein llama.rn directly native thread use karta hai jo OS ke performance
                // core scheduler se chal raha hai. Hum Android coroutine thread pe hain —
                // URGENT_AUDIO priority = highest non-realtime = efficiency cores se bachke
                // performance cores pe pin ho jaata hai.
                runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO) }

                // PERF FIX: DataStore .first() calls HAR generation pe disk read karte the.
                // Ab in-memory cached values use karo — PocketPal MobX observable ki tarah.
                val penaltyLastN = cachedPenaltyLastN
                val cachePrompt = cachedCachePrompt
                val defragThreshold = cachedDefragThreshold

                // REMOVED withTimeout wrapper. We rely on coroutine cancellation
                // and the native should_stop flag instead.
                bridge.generate(
                    contextPtr = contextPtr,
                    prompt = preparedPrompt,
                    stopTokens = stopTokens.toTypedArray(),
                    temperature = tunedConfig.temperature,
                    topP = tunedConfig.topP,
                    topK = tunedConfig.topK,
                    repeatPenalty = tunedConfig.repeatPenalty,
                    penaltyLastN = penaltyLastN,
                    maxTokens = tunedConfig.maxTokens,
                    shouldUpdateCache = shouldUpdateCache,
                    cachePrompt = cachePrompt,
                    defragThreshold = defragThreshold,
                    minP = tunedConfig.minP,
                    seed = tunedConfig.seed,
                    // PocketPal parity: new sampler params
                    xtcThreshold = tunedConfig.xtcThreshold,
                    xtcProbability = tunedConfig.xtcProbability,
                    typicalP = tunedConfig.typicalP,
                    penaltyFreq = tunedConfig.penaltyFreq,
                    penaltyPresent = tunedConfig.penaltyPresent,
                    mirostat = tunedConfig.mirostat,
                    mirostatTau = tunedConfig.mirostatTau,
                    mirostatEta = tunedConfig.mirostatEta,
                    callback = object : GenerationCallback {
                        private val tokenBuffer = StringBuilder()
                        // Pre-compiled once per generation (negligible cost)
                        private val IGNORED_TAG_PATTERN = Regex("<\\|[a-zA-Z0-9_]+\\|>")

                        private fun processCleanText(text: String) {
                            if (stopTokens.isEmpty()) {
                                if (!emitResult(GenerationResult.Token(text))) {
                                    runCatching { bridge.stopGeneration(contextPtr) }
                                } else {
                                    emittedTokenCount += 1
                                }
                                return
                            }

                            pendingTokenBuffer.append(text)
                            val trailingStopLen = findTrailingStopTokenLength(pendingTokenBuffer.toString())
                            if (trailingStopLen > 0) {
                                emitBufferedPrefix((pendingTokenBuffer.length - trailingStopLen).coerceAtLeast(0))
                                pendingTokenBuffer.clear()
                                stopReached = true
                                runCatching { bridge.stopGeneration(contextPtr) }
                                return
                            }

                            // PERF FIX: Flush as soon as we have enough chars to check
                            // for stop tokens. Old threshold (maxStopTokenLength + 16)
                            // caused tokens to accumulate, making streaming look bursty.
                            if (pendingTokenBuffer.length > flushThreshold) {
                                val keepTail = (maxStopTokenLength - 1).coerceAtLeast(0)
                                val flushLength = pendingTokenBuffer.length - keepTail
                                emitBufferedPrefix(flushLength)
                            }
                        }

                        override fun onToken(token: String) {
                            if (stopReached) return

                            // FIX #5: Telemetry for direct LLMEngine calls
                            directTokenCount++
                            if (firstTokenNs == null) firstTokenNs = System.nanoTime()

                            // Group 2: Memory Pressure Handling
                            if (shouldCheckMemoryPressure() && deviceProfileManager.isMemoryPressureHigh()) {
                                // Keep streaming instead of hard-stopping mid-answer.
                                // Hard stop was causing visible response truncation.
                                Log.w(TAG, "High memory pressure detected during generation; continuing stream")
                            }

                            // STABILITY: Append to buffer first to check for stop tokens
                            // including those within tags like <|eot_id|>
                            tokenBuffer.append(token)
                            val bufferStr = tokenBuffer.toString()

                            // Check for stop tokens in the raw buffer (before tag stripping)
                            val rawTrailingStopLen = findTrailingStopTokenLength(bufferStr)
                            if (rawTrailingStopLen > 0) {
                                // If a stop token is found, we should stop immediately
                                stopReached = true
                                runCatching { bridge.stopGeneration(contextPtr) }

                                // Clean up any preceding text and emit
                                val cleanBeforeStop = IGNORED_TAG_PATTERN.replace(
                                    bufferStr.substring(0, bufferStr.length - rawTrailingStopLen),
                                    ""
                                )
                                if (cleanBeforeStop.isNotEmpty()) {
                                    processCleanText(cleanBeforeStop)
                                }
                                tokenBuffer.clear()
                                return
                            }

                            // Buffer management for potential split tags or stop tokens
                            val bufLen = tokenBuffer.length
                            if (tokenBuffer[bufLen - 1] == '<') return
                            if (bufLen >= 2 && tokenBuffer[bufLen - 2] == '<' && tokenBuffer[bufLen - 1] == '|') return

                            val lastOpen = bufferStr.lastIndexOf("<|")
                            if (lastOpen != -1 && !bufferStr.substring(lastOpen).contains("|>")) {
                                // Wait for a full special token marker.
                                // Old threshold (12) cut valid tags like <|start_header_id|>.
                                if (bufLen - lastOpen < 64) return
                            }

                            // If we're not in a potential tag/stop token state, process the text
                            // Use a small lookahead buffer to ensure we don't cut off a stop token start
                            val currentCleanText = IGNORED_TAG_PATTERN.replace(bufferStr, "")
                            if (currentCleanText.isNotEmpty()) {
                                processCleanText(currentCleanText)
                            }
                            tokenBuffer.clear()
                        }

                        override fun onComplete() {
                            var remaining = tokenBuffer.toString()
                            remaining = IGNORED_TAG_PATTERN.replace(remaining, "")

                            if (!stopReached && remaining.isNotEmpty()) {
                                processCleanText(remaining)
                            }
                            if (!stopReached && pendingTokenBuffer.isNotEmpty()) {
                                emitBufferedPrefix(pendingTokenBuffer.length)
                            }

                            // FIX #5: Save telemetry so ChatRepository.getLastTelemetry() works
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

    data class InferenceRequest(
        val prompt: String,
        val params: InferenceConfig,
        val onToken: (String) -> Unit,
        val onComplete: () -> Unit,
        val onError: (Exception) -> Unit
    )

    private suspend fun processInferenceQueue() {
        for (request in inferenceChannel) {
            try {
                runInference(request)
            } catch (e: Exception) {
                request.onError(e)
            }
        }
    }

    fun generate(
        prompt: String,
        params: InferenceConfig,
        onToken: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        inferenceChannel.trySend(
            InferenceRequest(prompt, params, onToken, onComplete, onError)
        )
    }

    private suspend fun runInference(request: InferenceRequest) = withContext(Dispatchers.IO) {
        if (draftModelLoaded && deviceProfileManager.isSpeculativeDecodingSupported()) {
            bridge.generateSpeculative(
                currentContextPtr,
                request.prompt,
                stopTokens = request.params.stopTokens.toTypedArray(),
                request.params.maxTokens,
                nDraft = 4,
                callback = object : com.localmind.app.llm.nativelib.GenerationCallback {
                    override fun onToken(token: String) {
                        request.onToken(token)
                    }
                    override fun onComplete() {
                        // Will be called in the outer scope
                    }
                    override fun onError(error: String) {
                        request.onError(Exception(error))
                    }
                }
            )
        } else {
            bridge.generate(
            contextPtr = currentContextPtr,
            prompt = request.prompt,
            stopTokens = request.params.stopTokens.toTypedArray(),
            temperature = request.params.temperature,
            topP = request.params.topP,
            topK = request.params.topK,
            repeatPenalty = request.params.repeatPenalty,
            penaltyLastN = cachedPenaltyLastN,  // PERF FIX: cached value
            maxTokens = request.params.maxTokens,
            shouldUpdateCache = true,
            cachePrompt = cachedCachePrompt,  // PERF FIX: cached value
            defragThreshold = cachedDefragThreshold,  // PERF FIX: cached value
                minP = request.params.minP,
                seed = request.params.seed,
                // PocketPal parity: new sampler params
                xtcThreshold = request.params.xtcThreshold,
                xtcProbability = request.params.xtcProbability,
                typicalP = request.params.typicalP,
                penaltyFreq = request.params.penaltyFreq,
                penaltyPresent = request.params.penaltyPresent,
                mirostat = request.params.mirostat,
                mirostatTau = request.params.mirostatTau,
                mirostatEta = request.params.mirostatEta,
                callback = object : com.localmind.app.llm.nativelib.GenerationCallback {
                    override fun onToken(token: String) {
                        request.onToken(token)
                    }
                    override fun onComplete() {
                        // Will be called in the outer scope
                    }
                    override fun onError(error: String) {
                        request.onError(Exception(error))
                    }
                }
            )
        }
        withContext(Dispatchers.Main) {
            request.onComplete()
        }
    }

    /**
     * Stop ongoing generation
     */
    fun stopGeneration() {
        val contextPtr = currentContextPtr
        if (contextPtr != 0L) {
            // NOTE: resetPrefixCacheOnNextGenerate = false intentionally.
            // Agar stop ke baad dobara generate karo toh prefix cache valid rehta hai —
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
     * (generate() Flow ab mutex-free hai — C++ is_generating flag se protected hai.)
     */
    suspend fun waitForMutexRelease(timeoutMs: Long = 3_000L) {
        if (!engineMutex.isLocked) return
        // Model load ho raha hai — wait karo
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
        // SAF models use /proc/self/fd/XX paths — the file is already opened via
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
    data class Token(val text: String) : GenerationResult()
    object Complete : GenerationResult()
    data class Error(val message: String) : GenerationResult()
}


