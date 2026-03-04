package com.localmind.app.llm

import com.localmind.app.core.performance.DeviceProfileManager
import com.localmind.app.data.repository.ChatRepository
import com.localmind.app.data.repository.ModelRepository
import com.localmind.app.data.repository.SettingsRepository
import com.localmind.app.domain.model.Model
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import java.io.File

data class ModelLifecyclePlan(
    val contextSize: Int,
    val threadCount: Int,
    val maxTokens: Int
)

@Singleton
class ModelLifecycleManager @Inject constructor(
    private val modelRepository: ModelRepository,
    private val chatRepository: ChatRepository,
    private val deviceProfileManager: DeviceProfileManager,
    private val settingsRepository: SettingsRepository
) {
    private val modelLock = Mutex()
    private var pendingModelId: String? = null

    suspend fun activateModelSafely(
        modelId: String,
        options: ActivationOptions = ActivationOptions()
    ): Result<ModelLifecyclePlan> {
        pendingModelId = modelId

        return modelLock.withLock {
            // Check if another request has superseded this one while waiting for the lock
            if (pendingModelId != modelId) {
                return Result.failure(CancellationException("Superseded by a newer activation request"))
            }

            val model = modelRepository.getModelById(modelId)
                ?: return Result.failure(IllegalArgumentException("Model not found"))

            if (!modelRepository.modelFileExists(model)) {
                return Result.failure(IllegalStateException("Model file is missing. Re-download required."))
            }

            // Determine target configuration
            val userContextSize = settingsRepository.contextSize.first()
            val requestedContextSize = userContextSize

            // Check if this exact model configuration is already loaded
            val (loadedKey, loadedContext) = chatRepository.getLoadedModelDetails()
            val targetKey = if (model.storageType == com.localmind.app.core.storage.ModelStorageType.SAF) {
                "saf:${model.storageUri}"
            } else {
                val normalizedPath = model.filePath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { File(it).absolutePath }
                    ?: model.fileName
                "path:$normalizedPath"
            }

            if (loadedKey == targetKey && !options.forceLoad) {
                // Already loaded with correct config, skip reload
                val currentPlan = ModelLifecyclePlan(
                    contextSize = loadedContext,
                    threadCount = settingsRepository.threadCount.first(),
                    maxTokens = settingsRepository.maxTokens.first()
                )
                // Just ensure it is marked active
                modelRepository.activateModel(modelId)
                settingsRepository.clearActiveCloudModel()
                return Result.success(currentPlan)
            }

            val requestedPlan = if (options.forceLoad) {
                deviceProfileManager.forcePlan(
                    modelSizeBytes = model.sizeBytes,
                    modelNameHint = model.name,
                    quantizationHint = model.quantization,
                    parameterCountHint = model.parameterCount,
                    requestedContextSize = requestedContextSize
                )
            } else {
                deviceProfileManager.safePlan(
                    modelSizeBytes = model.sizeBytes,
                    modelNameHint = model.name,
                    quantizationHint = model.quantization,
                    parameterCountHint = model.parameterCount,
                    requestedContextSize = requestedContextSize
                )
            }

            val fallbackForcePlan = if (!requestedPlan.allowed) {
                deviceProfileManager.forcePlan(
                    modelSizeBytes = model.sizeBytes,
                    modelNameHint = model.name,
                    quantizationHint = model.quantization,
                    parameterCountHint = model.parameterCount,
                    requestedContextSize = requestedContextSize
                )
            } else {
                null
            }

            val effectivePlan = when {
                requestedPlan.allowed -> requestedPlan
                fallbackForcePlan?.allowed == true -> fallbackForcePlan
                else -> requestedPlan
            }

            val plan = if (effectivePlan.allowed) {
                ModelLifecyclePlan(
                    contextSize = effectivePlan.contextSize,
                    threadCount = effectivePlan.threadCount,
                    maxTokens = effectivePlan.maxTokens
                )
            } else {
                val totalRamGb = deviceProfileManager.currentProfile().totalRamGb
                ModelLifecyclePlan(
                    contextSize = model.contextLength.coerceIn(512, if (totalRamGb <= 3) 1024 else 2048),
                    // PERF FIX: 4 threads minimum — 2 threads was halving speed on 8-core SoCs
                    threadCount = 4,
                    maxTokens = if (totalRamGb <= 3) 768 else 1024
                )
            }

            // Unload only when switching to another model.
            if (chatRepository.isModelLoaded() && loadedKey != targetKey) {
                chatRepository.stopGeneration()
                withTimeoutOrNull(8_000L) {
                    chatRepository.unloadModel()
                }
            }

            try {
                val loadResult = withTimeout(95_000L) {
                    chatRepository.loadModel(model)
                }
                if (loadResult.isFailure) {
                    Result.failure(
                        loadResult.exceptionOrNull() ?: IllegalStateException("Failed to load selected model")
                    )
                } else {
                    modelRepository.activateModel(modelId)
                    applyRecommendedSamplingSettings(model)
                    settingsRepository.clearActiveCloudModel()
                    Result.success(plan)
                }
            } catch (timeout: TimeoutCancellationException) {
                Result.failure(IllegalStateException("Model activation timed out"))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }

    suspend fun ensureModelLoaded(model: Model): Result<ModelLifecyclePlan> {
        if (!modelRepository.modelFileExists(model)) {
            return Result.failure(IllegalStateException("Model file is missing. Re-download required."))
        }

        val check = deviceProfileManager.checkModelLoad(
            modelSizeBytes = model.sizeBytes,
            modelNameHint = model.name,
            quantizationHint = model.quantization,
            parameterCountHint = model.parameterCount,
            requestedContextSize = model.contextLength
        )

        val result = chatRepository.loadModel(model)
        if (result.isFailure) {
            return Result.failure(result.exceptionOrNull() ?: Exception("Failed to load model"))
        }

        val plan = if (check.allowed) {
            ModelLifecyclePlan(
                contextSize = check.tunedContextSize,
                threadCount = check.tunedThreadCount,
                maxTokens = check.tunedMaxTokens
            )
        } else {
            val totalRamGb = deviceProfileManager.currentProfile().totalRamGb
            ModelLifecyclePlan(
                contextSize = model.contextLength.coerceIn(512, 1024),
                threadCount = if (totalRamGb <= 4) 1 else 2,
                maxTokens = if (totalRamGb <= 4) 64 else 128
            )
        }

        return Result.success(
            plan
        )
    }

    suspend fun unloadModelSafely() {
        chatRepository.stopGeneration()
        withTimeout(10_000L) {
            chatRepository.unloadModel()
        }
        modelRepository.clearActiveModel()
    }

    fun tuneInferenceConfig(config: InferenceConfig, benchmarkMode: Boolean = false): InferenceConfig {
        return deviceProfileManager.tuneInferenceConfig(config, benchmarkMode = benchmarkMode)
    }

    private suspend fun applyRecommendedInferenceSettings(
        plan: ModelLifecyclePlan
    ) {
        settingsRepository.updateContextSize(plan.contextSize)
        settingsRepository.updateThreadCount(plan.threadCount)
    }

    private suspend fun applyRecommendedSamplingSettings(
        model: Model
    ) {
        val showAdvanced = settingsRepository.showAdvancedSettings.first()
        if (showAdvanced) {
            return
        }

        settingsRepository.updateTemperature(model.recommendedTemperature.coerceIn(0.1f, 1.2f))
        settingsRepository.updateTopP(model.recommendedTopP.coerceIn(0.5f, 0.99f))
        settingsRepository.updateTopK(model.recommendedTopK.coerceIn(10, 100))
        settingsRepository.updateRepeatPenalty(model.recommendedRepeatPenalty.coerceIn(1.0f, 1.5f))
    }
}
