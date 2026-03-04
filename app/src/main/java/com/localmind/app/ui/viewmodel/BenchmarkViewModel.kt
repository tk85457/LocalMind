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
    val threadCount: Int = 0
)

sealed class BenchmarkState {
    object Idle : BenchmarkState()
    data class Running(val progress: Float, val currentTps: Float = 0f) : BenchmarkState()
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
            _activeModel.value = modelRepository.getActiveModel()
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
                    if (!modelRepository.modelFileExists(candidate)) {
                        continue
                    }
                    val safeActivation = modelLifecycleManager.activateModelSafely(
                        modelId = candidate.id,
                        options = ActivationOptions(source = ActivationSource.BENCHMARK)
                    )
                    val finalActivation = if (safeActivation.isSuccess) {
                        safeActivation
                    } else {
                        modelLifecycleManager.activateModelSafely(
                            modelId = candidate.id,
                            options = ActivationOptions(
                                forceLoad = true,
                                source = ActivationSource.BENCHMARK
                            )
                        )
                    }

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
                val prompt = if (profile.totalRamGb <= 4) {
                    "Write one short sentence about offline AI."
                } else {
                    "Explain offline mobile AI in three short bullet points."
                }

                val baseConfig = InferenceConfig(
                    temperature = 0.2f,
                    topP = 0.9f,
                    repeatPenalty = 1.05f,
                    maxTokens = if (reliabilityMode) profile.benchmarkMaxTokens else 128,
                    contextSize = selectedPlan.contextSize,
                    threadCount = selectedPlan.threadCount,
                    topK = 40
                )
                val tunedConfig = modelLifecycleManager.tuneInferenceConfig(baseConfig, benchmarkMode = true)

                var tokenCount = 0
                val startNs = System.nanoTime()

                withTimeout(if (reliabilityMode) 45_000L else 60_000L) {
                    llmEngine.generate(prompt, tunedConfig).collect { result ->
                        when (result) {
                            is GenerationResult.Started -> Unit
                            is GenerationResult.Token -> {
                                tokenCount++
                                if (tokenCount % 4 == 0) {
                                    val progress = (tokenCount.toFloat() / tunedConfig.maxTokens)
                                        .coerceIn(0f, 0.98f)

                                    val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                                    val currentTps = (tokenCount.toFloat() * 1000f) / elapsedMs.toFloat()

                                    _benchmarkState.value = BenchmarkState.Running(progress, currentTps)
                                }

                                if (reliabilityMode && deviceProfileManager.isMemoryPressureHigh()) {
                                    llmEngine.stopGeneration()
                                    throw IllegalStateException("Benchmark stopped due to memory pressure")
                                }
                            }
                            is GenerationResult.Complete -> {
                                _benchmarkState.value = BenchmarkState.Running(1f)
                            }
                            is GenerationResult.Error -> {
                                throw IllegalStateException(result.message)
                            }
                        }
                    }
                }

                if (tokenCount <= 0) {
                    _benchmarkState.value = BenchmarkState.Error(
                        "Benchmark could not generate tokens. Try another model."
                    )
                    return@launch
                }

                val nativeMetrics = llmEngine.getPerfMetrics()
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                val tokensPerSecond = if (nativeMetrics != null && nativeMetrics.tokensGenerated > 0) {
                     (nativeMetrics.tokensGenerated.toFloat() * 1000f / nativeMetrics.generationTimeMs.toFloat())
                } else {
                    (tokenCount.toFloat() * 1000f) / elapsedMs.toFloat()
                }

                val runtime = Runtime.getRuntime()
                val usedMemoryMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).coerceAtLeast(0)

                val result = BenchmarkResult(
                    tokensPerSecond = tokensPerSecond,
                    totalTokens = if (nativeMetrics?.tokensGenerated ?: 0 > 0) nativeMetrics!!.tokensGenerated else tokenCount,
                    timeMs = elapsedMs,
                    memoryUsedMB = usedMemoryMb,
                    promptEvalTimeMs = nativeMetrics?.promptEvalTimeMs ?: 0.0,
                    generationTimeMs = nativeMetrics?.generationTimeMs ?: 0.0,
                    modelLoadTimeMs = nativeMetrics?.modelLoadTimeMs ?: 0.0,
                    contextSize = tunedConfig.contextSize,
                    threadCount = tunedConfig.threadCount
                )

                _benchmarkState.value = BenchmarkState.Complete(result)
                Log.i("LocalMind-Benchmark", "Benchmark complete with ${result.tokensPerSecond} tps")
            } catch (c: CancellationException) {
                llmEngine.stopGeneration()
                _benchmarkState.value = BenchmarkState.Idle
            } catch (oom: OutOfMemoryError) {
                llmEngine.stopGeneration()
                _benchmarkState.value = BenchmarkState.Error("Out of memory during benchmark")
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

        if (modelId.isNotBlank()) {
            modelRepository.getModelById(modelId)?.let { candidates += it }
        }
        modelRepository.ensureAnyActiveModel()?.let { candidates += it }
        modelRepository.getActiveModel()?.let { candidates += it }
        modelRepository.getMostRecentModel()?.let { candidates += it }
        candidates += modelRepository.getAllModelsSync().take(8)

        return candidates.distinctBy { it.id }
    }

    private fun mapBenchmarkError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("Model not found", ignoreCase = true) -> {
                "No model available. Open Model Library and activate one model."
            }
            message.contains("timed out", ignoreCase = true) -> {
                "Benchmark timed out. Try again with lower settings."
            }
            message.contains("memory pressure", ignoreCase = true) -> {
                "Benchmark stopped due to low memory pressure."
            }
            message.isNotBlank() -> message
            else -> "Benchmark failed"
        }
    }
}
