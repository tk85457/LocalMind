package com.localmind.app.data.repository

import android.content.Context
import com.localmind.app.core.storage.PersistentModelStorageManager
import com.localmind.app.data.local.dao.DownloadTaskDao
import com.localmind.app.data.local.dao.ModelDao
import com.localmind.app.data.local.entity.DownloadTaskEntity
import com.localmind.app.data.local.entity.ModelEntity
import com.localmind.app.data.remote.HuggingFaceApi
import com.localmind.app.domain.model.HuggingFaceModelInfo
import com.localmind.app.domain.model.ModelCatalogItem
import com.localmind.app.domain.model.ModelCategory
import com.localmind.app.worker.DownloadModelWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HuggingFaceRepository @Inject constructor(
    private val api: HuggingFaceApi,
    private val modelDao: ModelDao,
    private val downloadTaskDao: DownloadTaskDao,
    private val downloadOrchestrator: DownloadOrchestrator,
    private val storageManager: PersistentModelStorageManager,
    @ApplicationContext private val context: Context
) {

    /**
     * Curated list — exact same as PocketPal AI defaultModels.ts (v14)
     * Sizes taken directly from HuggingFace LFS metadata (accurate bytes)
     */
    fun getCuratedModels(): List<HuggingFaceModelInfo> {
        return listOf(

            // -------- Gemma --------
            HuggingFaceModelInfo(
                id = "bartowski/gemma-2-2b-it-GGUF|gemma-2-2b-it-Q6_K.gguf",
                name = "Gemma-2-2b-it (Q6_K)",
                repoId = "bartowski/gemma-2-2b-it-GGUF",
                author = "Google",
                description = "Google's Gemma 2 2B instruction-tuned model. Excellent reasoning and summarization.",
                parameterCount = "2.6B",
                sizeGb = 2.00,  // 2,151,393,120 bytes = 2.00 GB
                quantization = "Q6_K",
                ggufFileName = "gemma-2-2b-it-Q6_K.gguf",
                downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q6_K.gguf",
                category = ModelCategory.SMALL,
                minRamGb = 4,
                tags = listOf("google", "gemma", "reasoning", "summarization")
            ),

            HuggingFaceModelInfo(
                id = "TheDrummer/Gemmasutra-Mini-2B-v1-GGUF|Gemmasutra-Mini-2B-v1-Q6_K.gguf",
                name = "Gemmasutra-Mini-2B-v1 (Q6_K)",
                repoId = "TheDrummer/Gemmasutra-Mini-2B-v1-GGUF",
                author = "TheDrummer",
                description = "Gemma-based roleplay model fine-tuned by TheDrummer. Great for creative conversations.",
                parameterCount = "2.6B",
                sizeGb = 2.00,  // 2,151,393,152 bytes = 2.00 GB
                quantization = "Q6_K",
                ggufFileName = "Gemmasutra-Mini-2B-v1-Q6_K.gguf",
                downloadUrl = "https://huggingface.co/TheDrummer/Gemmasutra-Mini-2B-v1-GGUF/resolve/main/Gemmasutra-Mini-2B-v1-Q6_K.gguf",
                category = ModelCategory.SMALL,
                minRamGb = 4,
                tags = listOf("gemma", "roleplay", "creative")
            ),

            // -------- Phi --------
            HuggingFaceModelInfo(
                id = "MaziyarPanahi/Phi-3.5-mini-instruct-GGUF|Phi-3.5-mini-instruct.Q4_K_M.gguf",
                name = "Phi-3.5 mini 4k instruct (Q4_K_M)",
                repoId = "MaziyarPanahi/Phi-3.5-mini-instruct-GGUF",
                author = "Microsoft",
                description = "Microsoft's Phi-3.5 mini model. Exceptional at reasoning, code, and math for its size.",
                parameterCount = "3.8B",
                sizeGb = 2.23,  // 2,393,232,608 bytes = 2.23 GB
                quantization = "Q4_K_M",
                ggufFileName = "Phi-3.5-mini-instruct.Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/MaziyarPanahi/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct.Q4_K_M.gguf",
                category = ModelCategory.MEDIUM,
                minRamGb = 4,
                tags = listOf("microsoft", "phi", "reasoning", "code", "math", "multilingual")
            ),

            // -------- Qwen --------
            HuggingFaceModelInfo(
                id = "Qwen/Qwen2.5-1.5B-Instruct-GGUF|qwen2.5-1.5b-instruct-q8_0.gguf",
                name = "Qwen2.5-1.5B-Instruct (Q8_0)",
                repoId = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
                author = "Alibaba",
                description = "Alibaba's compact 1.5B multilingual model. Fast and capable for instructions and roleplay.",
                parameterCount = "1.8B",
                sizeGb = 1.76,  // 1,894,532,128 bytes = 1.76 GB
                quantization = "Q8_0",
                ggufFileName = "qwen2.5-1.5b-instruct-q8_0.gguf",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q8_0.gguf",
                category = ModelCategory.SMALL,
                minRamGb = 4,
                tags = listOf("alibaba", "qwen", "multilingual", "instructions")
            ),

            HuggingFaceModelInfo(
                id = "Qwen/Qwen2.5-3B-Instruct-GGUF|qwen2.5-3b-instruct-q5_k_m.gguf",
                name = "Qwen2.5-3B-Instruct (Q5_K_M)",
                repoId = "Qwen/Qwen2.5-3B-Instruct-GGUF",
                author = "Alibaba",
                description = "Alibaba's 3B multilingual model. Strong general performance with excellent instruction following.",
                parameterCount = "3.4B",
                sizeGb = 2.27,  // 2,438,740,384 bytes = 2.27 GB
                quantization = "Q5_K_M",
                ggufFileName = "qwen2.5-3b-instruct-q5_k_m.gguf",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q5_k_m.gguf",
                category = ModelCategory.MEDIUM,
                minRamGb = 4,
                tags = listOf("alibaba", "qwen", "multilingual", "powerful")
            ),

            // -------- Llama --------
            HuggingFaceModelInfo(
                id = "hugging-quants/Llama-3.2-1B-Instruct-Q8_0-GGUF|llama-3.2-1b-instruct-q8_0.gguf",
                name = "Llama-3.2-1b-instruct (Q8_0)",
                repoId = "hugging-quants/Llama-3.2-1B-Instruct-Q8_0-GGUF",
                author = "Meta",
                description = "Meta's ultra-compact Llama 3.2 1B model. Fastest on-device inference with decent quality.",
                parameterCount = "1.2B",
                sizeGb = 1.23,  // 1,321,079,200 bytes = 1.23 GB
                quantization = "Q8_0",
                ggufFileName = "llama-3.2-1b-instruct-q8_0.gguf",
                downloadUrl = "https://huggingface.co/hugging-quants/Llama-3.2-1B-Instruct-Q8_0-GGUF/resolve/main/llama-3.2-1b-instruct-q8_0.gguf",
                category = ModelCategory.SMALL,
                minRamGb = 4,
                tags = listOf("meta", "llama", "fast", "summarization")
            ),

            HuggingFaceModelInfo(
                id = "bartowski/Llama-3.2-3B-Instruct-GGUF|Llama-3.2-3B-Instruct-Q6_K.gguf",
                name = "Llama-3.2-3B-Instruct (Q6_K)",
                repoId = "bartowski/Llama-3.2-3B-Instruct-GGUF",
                author = "Meta",
                description = "Meta's 3B Llama 3.2 model. Balanced speed and quality for instructions and summarization.",
                parameterCount = "3.2B",
                sizeGb = 2.46,  // 2,643,853,856 bytes = 2.46 GB
                quantization = "Q6_K",
                ggufFileName = "Llama-3.2-3B-Instruct-Q6_K.gguf",
                downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q6_K.gguf",
                category = ModelCategory.MEDIUM,
                minRamGb = 6,
                tags = listOf("meta", "llama", "smart", "summarization")
            ),

            // -------- SmolLM --------
            HuggingFaceModelInfo(
                id = "bartowski/SmolLM2-1.7B-Instruct-GGUF|SmolLM2-1.7B-Instruct-Q8_0.gguf",
                name = "SmolLM2-1.7B-Instruct (Q8_0)",
                repoId = "bartowski/SmolLM2-1.7B-Instruct-GGUF",
                author = "HuggingFace",
                description = "HuggingFace's SmolLM2 optimized for lightweight on-device chat. Fast and efficient.",
                parameterCount = "1.7B",
                sizeGb = 1.70,  // 1,820,414,944 bytes = 1.70 GB
                quantization = "Q8_0",
                ggufFileName = "SmolLM2-1.7B-Instruct-Q8_0.gguf",
                downloadUrl = "https://huggingface.co/bartowski/SmolLM2-1.7B-Instruct-GGUF/resolve/main/SmolLM2-1.7B-Instruct-Q8_0.gguf",
                category = ModelCategory.SMALL,
                minRamGb = 4,
                tags = listOf("huggingface", "smol", "fast")
            ),

            // -------- SmolVLM (Vision) --------
            HuggingFaceModelInfo(
                id = "ggml-org/SmolVLM-500M-Instruct-GGUF|SmolVLM-500M-Instruct-Q8_0.gguf",
                name = "SmolVLM2-500M-Instruct (Q8_0)",
                repoId = "ggml-org/SmolVLM-500M-Instruct-GGUF",
                author = "HuggingFace",
                description = "Compact vision-language model. Can see and understand images on-device.",
                parameterCount = "0.5B",
                sizeGb = 0.42,  // 436,806,912 bytes + mmproj 108,783,360 = 0.51 GB total
                quantization = "Q8_0",
                ggufFileName = "SmolVLM-500M-Instruct-Q8_0.gguf",
                downloadUrl = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/SmolVLM-500M-Instruct-Q8_0.gguf",
                category = ModelCategory.TINY,
                minRamGb = 4,
                tags = listOf("huggingface", "vision", "multimodal", "smolvlm")
            )
        )
    }

    /**
     * Search curated models by name or description
     */
    fun searchModels(query: String): List<HuggingFaceModelInfo> {
        if (query.isBlank()) return getCuratedModels()
        val lowerQuery = query.lowercase()
        return getCuratedModels().filter { model ->
            model.name.lowercase().contains(lowerQuery) ||
                    model.description.lowercase().contains(lowerQuery) ||
                    model.tags.any { it.lowercase().contains(lowerQuery) } ||
                    model.author.lowercase().contains(lowerQuery) ||
                    model.parameterCount.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Search the entire Hugging Face Hub for GGUF models
     */
    suspend fun searchHubModels(
        query: String,
        sort: String = "downloads",
        direction: Int = -1
    ): List<HuggingFaceModelInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val call = api.searchModels(
                    search = query,
                    sort = sort,
                    direction = direction,
                    limit = 100
                )
                val response = call.execute()
                val body = if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
                body.map { mapResponseToInfo(it) }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    private fun mapResponseToInfo(response: com.localmind.app.data.remote.HFModelResponse): HuggingFaceModelInfo {
        val repoId = response.modelId
        val author = response.author ?: repoId.split("/").firstOrNull() ?: "HF"
        val name = repoId.split("/").lastOrNull() ?: repoId

        // Find a GGUF file in siblings or fallback to tags
        val ggufFile = response.siblings?.find { it.filename.endsWith(".gguf", ignoreCase = true) }?.filename
            ?: response.tags?.find { it.endsWith(".gguf", ignoreCase = true) }
            ?: "model.gguf"

        // Estimate size if possible, or use a default large enough for storage check
        // Most GGUF models are around 1-4GB. Reducing estimates to prevent false positives.
        val sizeGb = if (repoId.contains("7b", ignoreCase = true)) 3.5 // Was 4.5
                      else if (repoId.contains("3b", ignoreCase = true)) 1.8 // Was 2.2
                      else 1.0 // Was 1.2

        return HuggingFaceModelInfo(
            id = repoId,
            name = name,
            repoId = repoId,
            author = author,
            description = "HF Model • ${response.downloads} downloads • ${response.likes} likes",
            parameterCount = if (repoId.contains("7b", ignoreCase = true)) "7B" else "Unknown",
            sizeGb = sizeGb,
            quantization = "Q4_K_M",
            ggufFileName = ggufFile,
            downloadUrl = "https://huggingface.co/$repoId/resolve/main/$ggufFile",
            category = if (sizeGb > 3.0) ModelCategory.MEDIUM else ModelCategory.SMALL,
            minRamGb = if (sizeGb > 3.0) 8 else 4,
            tags = response.tags ?: emptyList()
        )
    }

    /**
     * Filter models by category
     */
    fun filterByCategory(
        models: List<HuggingFaceModelInfo>,
        category: ModelCategory
    ): List<HuggingFaceModelInfo> {
        if (category == ModelCategory.ALL) return models
        return models.filter { it.category == category }
    }

    /**
     * Get the currently active model from the database
     */
    suspend fun getActiveModel(): com.localmind.app.data.local.entity.ModelEntity? {
        return withContext(Dispatchers.IO) {
            modelDao.getActiveModel()
        }
    }

    /**
     * Check if a model is already downloaded
     */
    suspend fun isModelDownloaded(modelInfo: HuggingFaceModelInfo): Boolean {
        return withContext(Dispatchers.IO) {
            val entity = modelDao.getModelById(modelInfo.id)
            if (entity != null) {
                return@withContext storageManager.exists(
                    storageType = entity.storageType,
                    filePath = entity.filePath,
                    storageUri = entity.storageUri,
                    fileName = entity.fileName.ifBlank { modelInfo.ggufFileName }
                )
            }

            val byFileName = modelDao.getModelByFileName(modelInfo.ggufFileName)
            if (byFileName != null) {
                return@withContext storageManager.exists(
                    storageType = byFileName.storageType,
                    filePath = byFileName.filePath,
                    storageUri = byFileName.storageUri,
                    fileName = byFileName.fileName.ifBlank { modelInfo.ggufFileName }
                )
            }

            storageManager.exists(
                storageType = "INTERNAL",
                filePath = File(context.filesDir, "models/${modelInfo.ggufFileName}").absolutePath,
                storageUri = null,
                fileName = modelInfo.ggufFileName
            )
        }
    }

    /**
     * Start a background download using WorkManager
     */
    suspend fun startDownloadWorker(modelInfo: HuggingFaceModelInfo) {
        startDownloadWorker(
            modelInfo = ModelCatalogItem(
                id = modelInfo.id,
                name = modelInfo.name,
                repoId = modelInfo.repoId,
                author = modelInfo.author,
                description = modelInfo.description,
                parameterCount = modelInfo.parameterCount,
                sizeGb = modelInfo.sizeGb,
                quantization = modelInfo.quantization,
                ggufFileName = modelInfo.ggufFileName,
                downloadUrl = modelInfo.downloadUrl,
                category = modelInfo.category,
                minRamGb = modelInfo.minRamGb,
                downloads = modelInfo.downloads,
                likes = modelInfo.likes,
                tags = modelInfo.tags
            )
        )
    }

    suspend fun startDownloadWorker(modelInfo: ModelCatalogItem) {
        downloadOrchestrator.enqueue(modelInfo)
    }

    /**
     * Set a downloaded model as the active model in the database
     */
    suspend fun activateModel(modelId: String) {
        withContext(Dispatchers.IO) {
            modelDao.activateModelTransaction(modelId)
        }
    }

    /**
     * Get the download progress flow from WorkManager for a specific model
     */
    fun getDownloadProgress(modelId: String): Flow<DownloadState> = flow {
        val persisted = downloadOrchestrator.observeTask(modelId).first()
        if (persisted != null) {
            when (persisted.state) {
                DownloadModelWorker.STATE_RUNNING, DownloadOrchestrator.STATE_ENQUEUED -> {
                    emit(
                        DownloadState.Downloading(
                            progress = persisted.progress / 100f,
                            downloadedBytes = persisted.downloadedBytes,
                            totalBytes = persisted.totalBytes,
                            speedBps = persisted.speedBps,
                            etaSeconds = persisted.etaSeconds
                        )
                    )
                }
                DownloadModelWorker.STATE_COMPLETED -> {
                    emit(DownloadState.Completed(modelId))
                }
                DownloadModelWorker.STATE_FAILED -> {
                    emit(DownloadState.Error(persisted.errorMessage ?: "Download failed"))
                }
            }
        }

        val workManager = androidx.work.WorkManager.getInstance(context)
        val workInfosFlow = workManager.getWorkInfosByTagFlow(DownloadModelWorker.modelTag(modelId))

        workInfosFlow.collect { workInfos ->
            val workInfo = workInfos.firstOrNull { it.state == androidx.work.WorkInfo.State.RUNNING }
                ?: workInfos.firstOrNull { it.state == androidx.work.WorkInfo.State.ENQUEUED }
                ?: workInfos.firstOrNull { it.state == androidx.work.WorkInfo.State.BLOCKED }
                ?: workInfos.firstOrNull() ?: return@collect

            when (workInfo.state) {
                androidx.work.WorkInfo.State.RUNNING -> {
                    val progress = workInfo.progress.getInt(com.localmind.app.worker.DownloadModelWorker.KEY_PROGRESS, 0)
                    val downloaded = workInfo.progress.getLong(com.localmind.app.worker.DownloadModelWorker.KEY_DOWNLOADED_BYTES, 0L)
                    val total = workInfo.progress.getLong(com.localmind.app.worker.DownloadModelWorker.KEY_TOTAL_BYTES, 0L)
                    val speed = workInfo.progress.getLong(com.localmind.app.worker.DownloadModelWorker.KEY_SPEED_BPS, 0L)
                    val eta = workInfo.progress.getLong(com.localmind.app.worker.DownloadModelWorker.KEY_ETA_SECONDS, -1L)
                    emit(DownloadState.Downloading(progress.toFloat() / 100f, downloaded, total, speed, eta))
                }
                androidx.work.WorkInfo.State.SUCCEEDED -> {
                    emit(DownloadState.Completed(modelId))
                }
                androidx.work.WorkInfo.State.FAILED -> {
                    val error = workInfo.outputData.getString(com.localmind.app.worker.DownloadModelWorker.KEY_ERROR_MESSAGE) ?: "Unknown error"
                    emit(DownloadState.Error(error))
                }
                else -> {
                    // Idle or other states
                }
            }
        }
    }

    fun getActiveDownloads(): Flow<List<DownloadTaskEntity>> {
        return downloadTaskDao.getActiveTasksFlow()
    }

    /**
     * Download a GGUF model file (Manual flow, kept for compatibility if needed elsewhere)
     */

    /**
     * Delete a downloaded model file (but not from DB, that's handled by ModelRepository)
     */
    /**
     * Cancel an ongoing model download
     */
    suspend fun cancelDownload(modelId: String) {
        downloadOrchestrator.cancel(modelId)
    }

    suspend fun deleteDownloadedFile(fileName: String) {
        withContext(Dispatchers.IO) {
            val entity = modelDao.getModelByFileName(fileName)
            if (entity != null) {
                storageManager.deleteModel(
                    storageType = entity.storageType,
                    filePath = entity.filePath,
                    storageUri = entity.storageUri,
                    fileName = entity.fileName.ifBlank { fileName }
                )
            } else {
                val internalFile = File(context.filesDir, "models/$fileName")
                if (internalFile.exists()) {
                    runCatching { internalFile.delete() }
                } else {
                    storageManager.deleteModel(
                        storageType = "SAF",
                        filePath = null,
                        storageUri = null,
                        fileName = fileName
                    )
                }
            }
        }
    }
}

/**
 * Sealed class representing download states
 */
sealed class DownloadState {
    object Starting : DownloadState()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBps: Long = 0L,
        val etaSeconds: Long = -1L
    ) : DownloadState()
    data class Completed(val modelName: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
