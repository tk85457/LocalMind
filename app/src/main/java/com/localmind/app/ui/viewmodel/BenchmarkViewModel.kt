package com.localmind.app.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmind.app.core.performance.DeviceProfileManager
import com.localmind.app.core.rollout.FeatureRolloutConfig
import com.localmind.app.data.repository.ModelRepository
import com.localmind.app.llm.LLMEngine
import com.localmind.app.llm.GenerationResult
import com.localmind.app.llm.InferenceConfig
import com.localmind.app.llm.ModelLifecycleManager
import com.localmind.app.llm.ActivationOptions
import com.localmind.app.llm.ActivationSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class BenchmarkResult(
    val tokensPerSecond: Float = 0f,
    val totalTokens: Int = 0,
    val timeMs: Long = 0,
    val memoryUsedMB: Long = 0,
    val promptEvalTimeMs: Double = 0.0,
    val generationTimeMs: Double = 0.0,
    val modelLoadTimeMs: Double = 0.0,
    val contextSize: Int = 0,
    val threadCount: Int = 0,
    // NEW: Time to First Token (TTFT) — how fast model responds
    val timeToFirstTokenMs: Long = 0,
    // NEW: Peak RAM used during benchmark
    val peakMemoryMB: Long = 0,
    // NEW: CPU cores used
    val cpuCores: Int = 0,
    // NEW: Total RAM of device
    val totalRamGb: Double = 0.0,
    // NEW: GPU layers used
    val gpuLayers: Int = 0,
    // NEW: Prompt tokens (input)
    val promptTokens: Int = 0
) {
    /** Performance rating based on TPS for mobile LLM inference */
    val performanceRating: String get() = when {
        tokensPerSecond >= 25f -> "🚀 Excellent"
        tokensPerSecond >= 15f -> "⚡ Good"
        tokensPerSecond >= 8f  -> "✅ Acceptable"
        tokensPerSecond >= 3f  -> "⚠️ Slow"
        else                   -> "🐢 Very Slow"
    }

    val performanceColor: String get() = when {
        tokensPerSecond >= 25f -> "excellent"
        tokensPerSecond >= 15f -> "good"
        tokensPerSecond >= 8f  -> "ok"
        tokensPerSecond >= 3f  -> "slow"
        else                   -> "bad"
    }
}

sealed class BenchmarkState {
    object Idle : BenchmarkState()
    data class Running(
        val progress: Float,
        val currentTps: Float = 0f,
        val tokensGenerated: Int = 0,
        val elapsedMs: Long = 0
    ) : BenchmarkState()
    data class Complete(val result: BenchmarkResult) : BenchmarkState()
    data class Error(val message: String) : BenchmarkState()
}

@HiltViewModel
class BenchmarkViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val llmEngine: LLMEngine,
    private val hardwareMonitor: com.localmind.app.core.utils.HardwareMonitor,
    private val deviceProfileManager: DeviceProfileManager,
    private val modelLifecycleManager: ModelLifecycleManager,
    private val featureRolloutConfig: FeatureRolloutConfig,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val modelId: String = savedStateHandle.get<String>("modelId")?.let(Uri::decode) ?: ""

    private val _benchmarkState = MutableStateFlow<BenchmarkState>(BenchmarkState.Idle)
    val benchmarkState: StateFlow<BenchmarkState> = _benchmarkState.asStateFlow()

    private val _hardwareStats = MutableStateFlow<com.localmind.app.core.utils.HardwareStats?>(null)
    val hardwareStats: StateFlow<com.localmind.app.core.utils.HardwareStats?> = _hardwareStats.asStateFlow()

    private val _activeModel = MutableStateFlow<com.localmind.app.domain.model.Model?>(null)
    val activeModel: StateFlow<com.localmind.app.domain.model.Model?> = _activeModel.asStateFlow()

    private var benchmarkJob: Job? = null

    init {
        viewModelScope.launch {
            hardwareMonitor.getStatsFlow().collect { stats ->
                _hardwareStats.value = stats
            }
        }
        viewModelScope.launch {
            modelRepository.getActiveModelFlow().collect { model ->
                if (model != null) _activeModel.value = model
            }
        }
        viewModelScope.launch {
            val m = modelRepository.getActiveModel()
            if (m != null) _activeModel.value = m
        }
    }

    fun startBenchmark() {
        if (benchmarkJob?.isActive == true) return
        benchmarkJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                _benchmarkState.value = BenchmarkState.Running(0f)
                val reliabilityMode = featureRolloutConfig.snapshot().benchmarkReliabilityMode

                val candidates = resolveBenchmarkCandidates()
                if (candidates.isEmpty()) {
                    _benchmarkState.value = BenchmarkState.Error(
                        "No model available. Open Model Library and activate one model."
                    )
                    return@launch
                }

                var selectedModel: com.localmind.app.domain.model.Model? = null
                var selectedPlan: com.localmind.app.llm.ModelLifecyclePlan? = null
                var loadError: String? = null

                for (candidate in candidates) {
                    if (!modelRepository.modelFileExists(candidate)) continue
                    val safeActivation = modelLifecycleManager.activateModelSafely(
                        modelId = candidate.id,
                        options = ActivationOptions(source = ActivationSource.BENCHMARK)
                    )
                    val finalActivation = if (safeActivation.isSuccess) safeActivation
                    else modelLifecycleManager.activateModelSafely(
                        modelId = candidate.id,
                        options = ActivationOptions(forceLoad = true, source = ActivationSource.BENCHMARK)
                    )

                    if (finalActivation.isSuccess) {
                        selectedModel = candidate
                        selectedPlan = finalActivation.getOrNull()
                        _activeModel.value = candidate
                        break
                    }
                    loadError = finalActivation.exceptionOrNull()?.message
                        ?: safeActivation.exceptionOrNull()?.message
                        ?: "Failed to load model"
                }

                if (selectedModel == null || selectedPlan == null) {
                    _benchmarkState.value = BenchmarkState.Error(
                        loadError ?: "No loadable model available for benchmark."
                    )
                    return@launch
                }

                val profile = deviceProfileManager.currentProfile()

                // FIX: Use generateWithMessages (chat template API) instead of raw generate().
                // Raw generate() skips the model's chat template → many models produce 0 tokens.
                // generateWithMessages applies the model's actual GGUF jinja template,
                // which is what the chat screen uses — guaranteed to produce output.
                val benchmarkSystemPrompt = "You are a helpful AI assistant. Answer concisely."
                val benchmarkUserPrompt = if (profile.totalRamGb <= 4) {
                    "What is 2+2? Answer in one sentence."
                } else {
                    "List 3 benefits of offline AI in bullet points."
                }
                val messagesJson = llmEngine.buildMessagesJson(
                    systemPrompt = benchmarkSystemPrompt,
                    historyMessages = emptyList(),
                    currentUserInput = benchmarkUserPrompt
                )

                val targetTokens = if (reliabilityMode) profile.benchmarkMaxTokens else 64
                val baseConfig = InferenceConfig(
                    temperature = 0.1f,   // Low temp = deterministic, faster benchmark
                    topP = 0.9f,
                    repeatPenalty = 1.05f,
                    maxTokens = targetTokens,
                    contextSize = selectedPlan.contextSize,
                    threadCount = selectedPlan.threadCount,
                    topK = 40
                )
                val tunedConfig = modelLifecycleManager.tuneInferenceConfig(baseConfig, benchmarkMode = true)

                var tokenCount = 0
                var firstTokenMs: Long = 0
                val startNs = System.nanoTime()

                // Snapshot RAM before generation starts
                val runtime = Runtime.getRuntime()
                val memBefore = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

                withTimeout(if (reliabilityMode) 45_000L else 90_000L) {
                    llmEngine.generateWithMessages(messagesJson, tunedConfig).collect { result ->
                        when (result) {
                            is GenerationResult.Started -> Unit
                            is GenerationResult.Token -> {
                                tokenCount++
                                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)

                                // Record time to first token
                                if (tokenCount == 1) firstTokenMs = elapsedMs

                                // Live TPS update every token (realtime)
                                val currentTps = (tokenCount.toFloat() * 1000f) / elapsedMs.toFloat()
                                val progress = (tokenCount.toFloat() / tunedConfig.maxTokens).coerceIn(0f, 0.98f)
                                _benchmarkState.value = BenchmarkState.Running(
                                    progress = progress,
                                    currentTps = currentTps,
                                    tokensGenerated = tokenCount,
                                    elapsedMs = elapsedMs
                                )
                            }
                            is GenerationResult.Complete -> {
                                _benchmarkState.value = _benchmarkState.value.let { s ->
                                    if (s is BenchmarkState.Running) s.copy(progress = 1f) else s
                                }
                            }
                            is GenerationResult.Error -> {
                                throw IllegalStateException(result.message)
                            }
                        }
                    }
                }

                if (tokenCount <= 0) {
                    _benchmarkState.value = BenchmarkState.Error(
                        "Model generated 0 tokens.\n\nPossible causes:\n• Model not fully loaded\n• Context size too small\n• Try reloading the model from Settings"
                    )
                    return@launch
                }

                // Pull real native perf metrics from llama.cpp
                val nativeMetrics = llmEngine.getPerfMetrics()
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                val memAfter = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

                // Use native metrics if available (more accurate), else fallback to Kotlin timing
                val finalTps = if (nativeMetrics != null && nativeMetrics.tokensGenerated > 0) {
                    (nativeMetrics.tokensGenerated.toFloat() * 1000f / nativeMetrics.generationTimeMs.toFloat())
                } else {
                    tokenCount.toFloat() * 1000f / elapsedMs.toFloat()
                }
                val finalTokens = if ((nativeMetrics?.tokensGenerated ?: 0) > 0)
                    nativeMetrics!!.tokensGenerated else tokenCount

                val (loadedKey, loadedCtx) = llmEngine.getLoadedModelDetails()
                val actualContextSize = if (loadedCtx > 0) loadedCtx else tunedConfig.contextSize

                val result = BenchmarkResult(
                    tokensPerSecond = finalTps,
                    totalTokens = finalTokens,
                    timeMs = elapsedMs,
                    memoryUsedMB = (memAfter - memBefore).coerceAtLeast(0),
                    promptEvalTimeMs = nativeMetrics?.promptEvalTimeMs ?: 0.0,
                    generationTimeMs = nativeMetrics?.generationTimeMs ?: (elapsedMs - firstTokenMs).toDouble(),
                    modelLoadTimeMs = nativeMetrics?.modelLoadTimeMs ?: 0.0,
                    contextSize = actualContextSize,
                    threadCount = tunedConfig.threadCount,
                    timeToFirstTokenMs = firstTokenMs,
                    peakMemoryMB = memAfter.coerceAtLeast(0),
                    cpuCores = Runtime.getRuntime().availableProcessors(),
                    totalRamGb = profile.totalRamGb.toDouble(),
                    gpuLayers = 0, // ModelLifecyclePlan mein gpuLayers nahi — LLMEngine se actual value milegi
                    promptTokens = 0 // native layer se nahi milta via current API
                )

                _benchmarkState.value = BenchmarkState.Complete(result)
                Log.i("LocalMind-Benchmark", "Benchmark complete: ${result.tokensPerSecond} t/s, TTFT=${firstTokenMs}ms, tokens=$finalTokens")

            } catch (c: CancellationException) {
                llmEngine.stopGeneration()
                _benchmarkState.value = BenchmarkState.Idle
            } catch (oom: OutOfMemoryError) {
                llmEngine.stopGeneration()
                _benchmarkState.value = BenchmarkState.Error("Out of memory during benchmark.\nTry closing other apps.")
            } catch (e: Exception) {
                llmEngine.stopGeneration()
                Log.e("LocalMind-Benchmark", "Benchmark failed", e)
                _benchmarkState.value = BenchmarkState.Error(mapBenchmarkError(e))
            } finally {
                benchmarkJob = null
            }
        }
    }

    fun resetBenchmark() {
        benchmarkJob?.cancel()
        llmEngine.stopGeneration()
        _benchmarkState.value = BenchmarkState.Idle
    }

    private suspend fun resolveBenchmarkCandidates(): List<com.localmind.app.domain.model.Model> {
        val candidates = mutableListOf<com.localmind.app.domain.model.Model>()
        if (modelId.isNotBlank()) modelRepository.getModelById(modelId)?.let { candidates += it }
        modelRepository.ensureAnyActiveModel()?.let { candidates += it }
        modelRepository.getActiveModel()?.let { candidates += it }
        modelRepository.getMostRecentModel()?.let { candidates += it }
        candidates += modelRepository.getAllModelsSync().take(8)
        return candidates.distinctBy { it.id }
    }

    private fun mapBenchmarkError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("Model not found", ignoreCase = true) ->
                "No model available. Open Model Library and download a model."
            message.contains("timed out", ignoreCase = true) ->
                "Benchmark timed out.\nTry again or use a smaller model."
            message.contains("memory pressure", ignoreCase = true) ->
                "Low memory. Close other apps and retry."
            message.contains("No model loaded", ignoreCase = true) ->
                "No model is loaded.\nGo to Settings → select a model first."
            message.isNotBlank() -> message
            else -> "Benchmark failed. Please try again."
        }
    }
}
