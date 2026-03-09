package com.localmind.app.llm

import com.localmind.app.core.rollout.FeatureRolloutConfig
import com.localmind.app.data.repository.SettingsRepository
import com.localmind.app.domain.model.InferenceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HybridInferenceRouter @Inject constructor(
    private val localEngine: LocalInferenceEngine,
    private val remoteEngine: RemoteInferenceEngine,
    private val settingsRepository: SettingsRepository,
    private val featureRolloutConfig: FeatureRolloutConfig
) {
    private val _lastTelemetry = MutableStateFlow<InferenceTelemetry?>(null)
    val lastTelemetry: StateFlow<InferenceTelemetry?> = _lastTelemetry.asStateFlow()

    // FIX #5: Synchronous accessor for ChatRepository.getLastInferenceTelemetry()
    fun latestTelemetry(): InferenceTelemetry? = _lastTelemetry.value

    fun generate(
        prompt: String,
        config: InferenceConfig,
        shouldUpdateCache: Boolean = true,
        routeHint: InferenceRouteHint = InferenceRouteHint.AUTO,
        remoteModelOverride: String? = null
    ): Flow<GenerationResult> = flow {
        val mode = when (routeHint) {
            InferenceRouteHint.FORCE_LOCAL -> InferenceMode.LOCAL_ONLY
            InferenceRouteHint.FORCE_REMOTE -> InferenceMode.REMOTE_ONLY
            InferenceRouteHint.AUTO -> InferenceMode.LOCAL_ONLY
        }
        val remoteFallbackEnabled =
            settingsRepository.remoteFallbackEnabled.first() &&
                featureRolloutConfig.snapshot().hybridAutoFallback

        when (mode) {
            InferenceMode.LOCAL_ONLY -> {
                runEngine(
                    engine = localEngine,
                    prompt = prompt,
                    config = config,
                    shouldUpdateCache = shouldUpdateCache,
                    allowFallback = false,
                    remoteModelOverride = remoteModelOverride,
                    collector = this@flow
                )
            }
            InferenceMode.REMOTE_ONLY -> {
                runEngine(
                    engine = remoteEngine,
                    prompt = prompt,
                    config = config,
                    shouldUpdateCache = shouldUpdateCache,
                    allowFallback = false,
                    remoteModelOverride = remoteModelOverride,
                    collector = this@flow
                )
            }
            InferenceMode.HYBRID -> {
                val localOutcome = runEngine(
                    engine = localEngine,
                    prompt = prompt,
                    config = config,
                    shouldUpdateCache = shouldUpdateCache,
                    allowFallback = remoteFallbackEnabled,
                    remoteModelOverride = remoteModelOverride,
                    collector = this@flow
                )
                if (remoteFallbackEnabled && localOutcome.shouldFallback) {
                    runEngine(
                        engine = remoteEngine,
                        prompt = prompt,
                        config = config,
                        shouldUpdateCache = shouldUpdateCache,
                        allowFallback = false,
                        remoteModelOverride = remoteModelOverride,
                        collector = this@flow
                    )
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun runEngine(
        engine: ChatInferenceEngine,
        prompt: String,
        config: InferenceConfig,
        shouldUpdateCache: Boolean,
        allowFallback: Boolean,
        remoteModelOverride: String?,
        collector: FlowCollector<GenerationResult>
    ): EngineRun {
        val startNs = System.nanoTime()
        var firstTokenNs: Long? = null
        var tokenCount = 0
        var errorMessage: String? = null
        var completed = false

        engine.generate(
            prompt = prompt,
            config = config,
            shouldUpdateCache = shouldUpdateCache,
            remoteModelOverride = remoteModelOverride
        ).collect { result ->
            when (result) {
                is GenerationResult.Started -> {
                    collector.emit(result)
                }
                is GenerationResult.Token -> {
                    tokenCount += 1
                    if (firstTokenNs == null) {
                        firstTokenNs = System.nanoTime()
                    }
                    collector.emit(result)
                }
                is GenerationResult.Complete -> {
                    completed = true
                    collector.emit(result)
                }
                is GenerationResult.Error -> {
                    errorMessage = result.message
                    if (!(allowFallback && tokenCount == 0 && isEligibleForFallback(result.message))) {
                        collector.emit(result)
                    }
                }
            }
        }

        val totalMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
        val ttftMs = firstTokenNs?.let { ((it - startNs) / 1_000_000L).coerceAtLeast(0L) }
        val tps = if (tokenCount > 0) {
            (tokenCount.toFloat() * 1000f) / totalMs.toFloat()
        } else {
            0f
        }
        _lastTelemetry.value = InferenceTelemetry(
            source = engine.source,
            ttftMs = ttftMs,
            totalTimeMs = totalMs,
            tokensGenerated = tokenCount,
            tokensPerSecond = tps
        )

        return EngineRun(
            shouldFallback = allowFallback &&
                !completed &&
                tokenCount == 0 &&
                isEligibleForFallback(errorMessage)
        )
    }

    private fun isEligibleForFallback(message: String?): Boolean {
        val text = message.orEmpty()
        if (text.isBlank()) return false
        return text.contains("No model loaded", ignoreCase = true) ||
            text.contains("Model is not ready", ignoreCase = true) ||
            text.contains("Prompt too long", ignoreCase = true) ||
            text.contains("Failed to evaluate prompt", ignoreCase = true) ||
            text.contains("Out of memory", ignoreCase = true) ||
            text.contains("insufficient", ignoreCase = true) ||
            text.contains("cannot fit", ignoreCase = true) ||
            text.contains("failed to load", ignoreCase = true)
    }

    private data class EngineRun(
        val shouldFallback: Boolean
    )
}
