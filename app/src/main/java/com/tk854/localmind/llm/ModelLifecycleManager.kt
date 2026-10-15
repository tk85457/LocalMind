package com.tk854.localmind.llm

import android.content.Context
import android.app.ActivityManager
import com.tk854.localmind.core.performance.DeviceProfileManager
import com.tk854.localmind.data.repository.ChatRepository
import com.tk854.localmind.data.repository.ModelRepository
import com.tk854.localmind.data.repository.SettingsRepository
import com.tk854.localmind.domain.model.Model
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
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

        // TTFT FIX: Fast-path check BEFORE acquiring modelLock.
        // Agar model already loaded hai toh lock acquire karne ki zarurat hi nahi.
        // Pehle lock ke andar yeh check tha  —  lock wait + 3 DataStore reads = ~8s delay.
        if (!options.forceLoad) {
            val model = modelRepository.getModelById(modelId)
            if (model != null && modelRepository.modelFileExists(model)) {
                val (loadedKey, loadedContext) = chatRepository.getLoadedModelDetails()
                val targetKey = if (model.storageType == com.tk854.localmind.core.storage.ModelStorageType.SAF) {
                    "saf:${model.storageUri}"
                } else {
                    val normalizedPath = model.filePath
                        ?.takeIf { it.isNotBlank() }
                        ?.let { File(it).absolutePath }
                        ?: model.fileName
                    "path:$normalizedPath"
                }
                if (loadedKey == targetKey && chatRepository.isModelLoaded()) {
                    // Already-loaded fast path still has to update DB/UI active state.
                    modelRepository.activateModel(modelId)
                    settingsRepository.clearActiveCloudModel()
                    // Already loaded  —  return immediately, zero disk I/O
                    return Result.success(ModelLifecyclePlan(
                        contextSize = loadedContext,
                        threadCount = 0, // not used by caller
                        maxTokens = 0   // not used by caller
                    ))
                }
            }
        }

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

            val userContextSize = settingsRepository.contextSize.first()
            val requestedContextSize = userContextSize

            // Check if this exact model configuration is already loaded
            val (loadedKey, loadedContext) = chatRepository.getLoadedModelDetails()
            val targetKey = if (model.storageType == com.tk854.localmind.core.storage.ModelStorageType.SAF) {
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

            // Refresh profile cache to get fresh RAM readings
            deviceProfileManager.invalidateProfileCache()

            val effectivePlan = if (options.forceLoad) {
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

            val totalRamGb = deviceProfileManager.currentProfile().totalRamGb
            val plan = ModelLifecyclePlan(
                contextSize = effectivePlan.contextSize.coerceAtLeast(512),
                threadCount = effectivePlan.threadCount.coerceAtLeast(if (totalRamGb <= 3) 2 else 4),
                maxTokens = effectivePlan.maxTokens.coerceAtLeast(256)
            )

            // Direct, clean unload of any existing model before loading the new one
            if (chatRepository.isModelLoaded()) {
                chatRepository.stopGeneration()
                withTimeoutOrNull(5_000L) {
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
                    settingsRepository.clearActiveCloudModel()
                    runCatching { applyRecommendedSamplingSettings(model) }
                    Result.success(plan)
                }
            } catch (timeout: TimeoutCancellationException) {
                Result.failure(IllegalStateException("Model activation timed out"))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
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
