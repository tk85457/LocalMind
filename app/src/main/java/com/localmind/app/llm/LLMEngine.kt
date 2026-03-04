package com.localmind.app.llm

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.localmind.app.core.Constants
import com.localmind.app.core.engine.ModelMetadata
import com.localmind.app.core.performance.DeviceProfileManager
import com.localmind.app.core.storage.ModelStorageType
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.min
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
    private val deviceProfileManager: DeviceProfileManager
) {
    @Volatile private var currentContextPtr: Long = 0
    @Volatile private var isGenerating = false
    @Volatile private var loadedModelKey: String? = null
    @Volatile private var openedModelDescriptor: ParcelFileDescriptor? = null
    @Volatile private var gpuInitFailedInSession = false
    @Volatile private var nativeBackendInitialized = false

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val engineMutex = Mutex()
    private val metadataMutex = Mutex() // Safety lock for native metadata reads
    private var generationJob: kotlinx.coroutines.Job? = null

    companion object {
        private const val TAG = "LLMEngine"
        private const val ENGINE_LOCK_TIMEOUT_MS = 20_000L
        private const val MODEL_LOAD_TIMEOUT_MS = 90_000L
        private const val GPU_LOAD_ATTEMPT_TIMEOUT_MS = 6_000L
    }

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
            val useMmap = memoryMapping != "None"
            val configuredContextSize = settingsRepository.contextSize.first().coerceIn(256, 131072)
            val maxDeviceThreads = Runtime.getRuntime().availableProcessors().coerceAtLeast(Constants.MIN_THREADS)
            val configuredThreadCount = settingsRepository.threadCount.first()
                .coerceIn(Constants.MIN_THREADS, maxDeviceThreads)

            // PERF FIX: Force minimum batch sizes for prefill speed.
            // Old installs may have 512/256 saved in DataStore — that causes 5s+ prefill
            // for just 32 tokens. Competitors use 2048+.
            val batchSize = settingsRepository.batchSize.first().coerceAtLeast(2048)
            val physicalBatchSize = settingsRepository.physicalBatchSize.first().coerceAtLeast(512)
            val flashAttentionRaw = settingsRepository.flashAttention.first()
            // PERF FIX: Flash attention dramatically speeds up prefill and generation.
            // Enable on ALL devices — the memory overhead is negligible for small models.
            val flashAttention = if (flashAttentionRaw == "Auto") {
                "On"
            } else flashAttentionRaw
            // PERF FIX: Auto-upgrade KV cache from F16 to Q8_0.
            // Q8_0 uses 50% less memory with negligible quality loss,
            // dramatically reducing memory bandwidth during generation.
            val rawKeyCacheType = settingsRepository.keyCacheType.first()
            val rawValueCacheType = settingsRepository.valueCacheType.first()
            val keyCacheType = if (rawKeyCacheType == "F16") "Q8_0" else rawKeyCacheType
            val valueCacheType = if (rawValueCacheType == "F16") "Q8_0" else rawValueCacheType

            val loadCheck = deviceProfileManager.checkModelLoad(
                modelSizeBytes = modelFileSize,
                modelNameHint = File(modelPath).name,
                quantizationHint = quantizationHint,
                parameterCountHint = parameterCountHint,
                requestedContextSize = configuredContextSize
            )

            val tunedContextSize = if (loadCheck.allowed) {
                loadCheck.tunedContextSize
            } else {
                configuredContextSize.coerceIn(256, 4096)
            }
            val tunedThreadCount = if (loadCheck.allowed) {
                min(configuredThreadCount, loadCheck.tunedThreadCount).coerceAtLeast(1)
            } else {
                val totalRamGb = deviceProfileManager.currentProfile().totalRamGb
                // PERF FIX: Emergency cap was 2 for 4GB — way too slow on 8-core phones
                val emergencyCap = if (totalRamGb <= 4) 3 else 4
                min(configuredThreadCount, emergencyCap).coerceAtLeast(2)
            }
            val allowMlock = false
            val configuredGpuLayers = settingsRepository.gpuLayers.first()
            val skipGpuAfterFail = settingsRepository.skipGpuAfterFail.first()
            val shouldAttemptGpu = (configuredGpuLayers > 0 || configuredGpuLayers == -1) && !skipGpuAfterFail

            val requestedGpuLayers = if (shouldAttemptGpu) {
                if (configuredGpuLayers == -1) {
                    val totalRamGb = deviceProfileManager.currentProfile().totalRamGb
                    val isSmallModel = modelFileSize < 4.0e9 // Roughly < 3.5B params
                    // RELAXED: 4GB+ RAM is enough for small models offloading
                    if (totalRamGb >= 4 && isSmallModel) 100 else 0
                } else {
                    configuredGpuLayers
                }
            } else 0

            val gpuLayerCandidates = if (requestedGpuLayers > 0) {
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

                var loadedPtr = 0L
                var usedGpuLayers = requestedGpuLayers
                for ((index, candidateGpuLayers) in gpuLayerCandidates.withIndex()) {
                    val timeoutMs = if (candidateGpuLayers > 0 && gpuLayerCandidates.size > 1) {
                        GPU_LOAD_ATTEMPT_TIMEOUT_MS
                    } else {
                        MODEL_LOAD_TIMEOUT_MS
                    }
                    val candidatePtr = runCatching {
                        withTimeout(timeoutMs) {
                            bridge.loadModel(
                                path = nativePath,
                                useMlock = allowMlock,
                                useMmap = useMmap,
                                contextSize = tunedContextSize,
                                threadCount = tunedThreadCount,
                                gpuLayers = candidateGpuLayers,
                                batchSize = batchSize,
                                physicalBatchSize = physicalBatchSize,
                                flashAttention = flashAttention,
                                keyCacheType = keyCacheType,
                                valueCacheType = valueCacheType
                            )
                        }
                    }.getOrElse { throwable ->
                        if (candidateGpuLayers > 0 && index == 0 && gpuLayerCandidates.size > 1) {
                            Log.w(TAG, "GPU load attempt failed: ${throwable.message}")
                            gpuInitFailedInSession = true
                            runCatching { settingsRepository.setSkipGpuAfterFail(true) }
                            0L
                        } else {
                            throw throwable
                        }
                    }
                    if (candidatePtr != 0L) {
                        loadedPtr = candidatePtr
                        usedGpuLayers = candidateGpuLayers
                        break
                    }
                    if (index == 0 && candidateGpuLayers > 0) {
                        Log.w(TAG, "GPU load attempt failed, retrying on CPU")
                    }
                }

                if (loadedPtr == 0L) {
                    return@withContext Result.failure(Exception("Native model initialization failed"))
                }

                currentContextPtr = loadedPtr
                loadedModelKey = modelKey
                currentLoadedContextSize = tunedContextSize
                openedModelDescriptor = pendingDescriptor
                pendingDescriptor = null
                if (usedGpuLayers > 0) {
                    gpuInitFailedInSession = false
                    runCatching { settingsRepository.setSkipGpuAfterFail(false) }
                } else if (gpuInitFailedInSession) {
                    runCatching { settingsRepository.setSkipGpuAfterFail(true) }
                }
                Log.i(TAG, "Model loaded successfully [gpu=$usedGpuLayers]")
                Result.success(Unit)
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
     * Load an MMProj (Vision) model
     */
    suspend fun loadProjector(projectorPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("Projector loading is not available in this build"))
    }

    /**
     * Process an image and inject its embeddings into the prompt context
     */
    suspend fun processImage(@Suppress("UNUSED_PARAMETER") imageBytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("Image processing is not available in this build"))
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
        metadataMutex.withLock {
            try {
                if (!isValidGgufFile(modelPath)) {
                    return@withLock Result.failure(Exception("Invalid or corrupted GGUF file"))
                }
                val metadata = bridge.getModelMetadata(modelPath)
                if (metadata != null) {
                    Result.success(metadata)
                } else {
                    Result.failure(Exception("Failed to extract model metadata"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting model metadata", e)
                Result.failure(e)
            }
        }
    }

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

        if (!engineMutex.tryLock()) {
            emitResult(GenerationResult.Error("Engine is busy. Please wait for current task to finish"))
            close()
            return@callbackFlow
        }

        var lockReleased = false
        fun releaseLock() {
            if (!lockReleased) {
                lockReleased = true
                runCatching { engineMutex.unlock() }
            }
        }

        val contextPtr = currentContextPtr
        if (contextPtr == 0L) {
            emitResult(GenerationResult.Error("No model loaded"))
            releaseLock()
            close()
            return@callbackFlow
        }

        isGenerating = true
        // PERF FIX: Config is already tuned by ChatViewModel/ModelLifecycleManager.
        // Removed redundant tuneInferenceConfig call that was double-clamping params.
        val tunedConfig = config
        val stopTokens = tunedConfig.stopTokens
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
                // REMOVED withTimeout wrapper. We rely on coroutine cancellation
                // and the native should_stop flag instead.
                bridge.generate(
                    contextPtr = contextPtr,
                    prompt = preparedPrompt,
                    temperature = tunedConfig.temperature,
                    topP = tunedConfig.topP,
                    topK = tunedConfig.topK,
                    repeatPenalty = tunedConfig.repeatPenalty,
                    maxTokens = tunedConfig.maxTokens,
                    shouldUpdateCache = shouldUpdateCache,
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

                            // PERF: Fast path — most tokens don't contain '<'.
                            // Skip buffering, regex, and tag detection entirely.
                            if (!token.contains('<') && tokenBuffer.isEmpty()) {
                                processCleanText(token)
                                return
                            }

                            tokenBuffer.append(token)
                            val bufLen = tokenBuffer.length

                            if (tokenBuffer[bufLen - 1] == '<') return
                            if (bufLen >= 2 && tokenBuffer[bufLen - 2] == '<' && tokenBuffer[bufLen - 1] == '|') return

                            val bufferStr = tokenBuffer.toString()
                            val lastOpen = bufferStr.lastIndexOf("<|")
                            if (lastOpen != -1 && !bufferStr.substring(lastOpen).contains("|>")) {
                                if (bufLen - lastOpen < 12) return
                            }

                            var textToEmit = IGNORED_TAG_PATTERN.replace(bufferStr, "")

                            if (textToEmit.isNotEmpty()) {
                                processCleanText(textToEmit)
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
            runCatching { bridge.stopGeneration(contextPtr) }
            generationJob?.cancel()
            isGenerating = false
            Log.i(TAG, "Generation stop requested")
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
