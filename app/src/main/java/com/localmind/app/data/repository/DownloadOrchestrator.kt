package com.localmind.app.data.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.localmind.app.data.local.dao.DownloadTaskDao
import com.localmind.app.data.local.entity.DownloadTaskEntity
import com.localmind.app.domain.model.ModelCatalogItem
import com.localmind.app.domain.model.ModelVariantSizeSource
import com.localmind.app.worker.DownloadModelWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadTaskDao: DownloadTaskDao
) {
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    suspend fun enqueue(modelInfo: ModelCatalogItem): String = withContext(Dispatchers.IO) {
        val taskId = UUID.randomUUID().toString()
        val selectedVariant = modelInfo.variants
            .firstOrNull { it.filename == modelInfo.ggufFileName }
        val isSplitVariant = isSplitModelFile(modelInfo.ggufFileName)
        // Only enforce strict size validation when we have an exact single-file size.
        // Estimated sizes can differ a lot and would incorrectly mark valid downloads as corrupted.
        val expectedSizeBytes = if (
            !isSplitVariant &&
            selectedVariant?.sizeSource == ModelVariantSizeSource.EXACT &&
            selectedVariant.sizeBytes > 0L
        ) {
            selectedVariant.sizeBytes
        } else {
            0L
        }
        val uniqueWorkName = DownloadModelWorker.workName(modelInfo.id, modelInfo.ggufFileName)

        downloadTaskDao.upsert(
            DownloadTaskEntity(
                taskId = taskId,
                modelId = modelInfo.id,
                fileName = modelInfo.ggufFileName,
                state = STATE_ENQUEUED,
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                speedBps = 0L,
                etaSeconds = -1L
            )
        )

        val workRequest = DownloadModelWorker.createWorkRequest(
            taskId = taskId,
            downloadUrl = modelInfo.downloadUrl,
            fileName = modelInfo.ggufFileName,
            modelName = modelInfo.name,
            repoId = modelInfo.id,
            quantization = modelInfo.quantization,
            parameterCount = modelInfo.parameterCount,
            templateId = modelInfo.templateId,
            stopTokensJson = modelInfo.stopTokensJson(),
            recommendedTemperature = modelInfo.recommendedTemperature,
            recommendedTopP = modelInfo.recommendedTopP,
            recommendedTopK = modelInfo.recommendedTopK,
            recommendedRepeatPenalty = modelInfo.recommendedRepeatPenalty,
            recommendedSystemPrompt = modelInfo.recommendedSystemPrompt,
            expectedSizeBytes = expectedSizeBytes
        )

        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        taskId
    }

    fun observeTask(modelId: String): Flow<com.localmind.app.data.local.entity.DownloadTaskEntity?> {
        return downloadTaskDao.getTaskByModelFlow(modelId)
    }

    suspend fun cancel(modelId: String) {
        withContext(Dispatchers.IO) {
            workManager.cancelAllWorkByTag(DownloadModelWorker.modelTag(modelId))
            downloadTaskDao.deleteByModelId(modelId)
        }
    }

    companion object {
        const val STATE_ENQUEUED = "ENQUEUED"
        private val SPLIT_GGUF_REGEX = Regex(
            pattern = "^(.*)-(\\d{5})-of-(\\d{5})\\.gguf$",
            option = RegexOption.IGNORE_CASE
        )

        private fun isSplitModelFile(fileName: String): Boolean {
            return SPLIT_GGUF_REGEX.matches(fileName)
        }
    }
}
