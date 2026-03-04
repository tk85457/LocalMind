package com.localmind.app.data.repository

import com.localmind.app.core.storage.InternalModelStorageManager
import com.localmind.app.data.local.dao.ModelDao
import com.localmind.app.data.local.entity.ModelEntity
import com.localmind.app.data.mapper.toDomain
import com.localmind.app.data.mapper.toEntity
import com.localmind.app.domain.model.Model
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray

@Singleton
class ModelRepository @Inject constructor(
    private val modelDao: ModelDao,
    private val storageManager: InternalModelStorageManager
) {
    private val defaultStopTokensJson = "[\"<|im_end|>\",\"</s>\"]"

    fun getAllModels(): Flow<List<Model>> {
        return modelDao.getAllModels().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getActiveModel(): Model? {
        return modelDao.getActiveModel()?.toDomain()
    }

    suspend fun ensureAnyActiveModel(): Model? {
        val active = modelDao.getActiveModel()
        if (active != null) return active.toDomain()

        val fallback = modelDao.getMostRecentModel() ?: return null
        modelDao.activateModelTransaction(fallback.id)
        return modelDao.getActiveModel()?.toDomain()
    }

    fun getActiveModelFlow(): Flow<Model?> {
        return modelDao.getActiveModelFlow().map { it?.toDomain() }
    }

    suspend fun getModelById(id: String): Model? {
        return modelDao.getModelById(id)?.toDomain()
    }

    fun getModelFlowById(id: String): Flow<Model?> {
        return modelDao.getModelFlowById(id).map { it?.toDomain() }
    }

    suspend fun getMostRecentModel(): Model? {
        return modelDao.getMostRecentModel()?.toDomain()
    }

    suspend fun getAllModelsSync(): List<Model> {
        return modelDao.getAllModelsSync().map { it.toDomain() }
    }

    suspend fun modelFileExists(model: Model): Boolean {
        val normalizedFileName = normalizeFileName(model.fileName, model.filePath)
        return storageManager.exists(normalizedFileName)
    }

    suspend fun insertModel(model: Model) {
        modelDao.insertModel(model.toEntity())
    }

    suspend fun deleteModel(model: Model) {
        storageManager.deleteModel(normalizeFileName(model.fileName, model.filePath))
        modelDao.deleteModel(model.toEntity())
    }

    suspend fun deleteModel(modelId: String) {
        val entity = modelDao.getModelById(modelId)
        if (entity != null) {
            storageManager.deleteModel(normalizeFileName(entity.fileName, entity.filePath))
            modelDao.deleteModel(entity)
        }
    }

    suspend fun activateModel(modelId: String) {
        modelDao.activateModelTransaction(modelId)
    }

    suspend fun setActiveModel(modelId: String) {
        activateModel(modelId)
    }

    suspend fun clearActiveModel() {
        modelDao.deactivateAllModels()
    }

    suspend fun updateLastUsed(modelId: String, timestamp: Long) {
        modelDao.updateLastUsed(modelId, timestamp)
    }

    suspend fun getModelCount(): Int {
        return modelDao.getModelCount()
    }

    suspend fun updateModelSettings(
        modelId: String,
        bos: Boolean,
        eos: Boolean,
        addGenPrompt: Boolean,
        systemPrompt: String,
        stopWords: List<String>
    ) {
        val entity = modelDao.getModelById(modelId) ?: return
        val updated = entity.copy(
            recommendedSystemPrompt = systemPrompt,
            bosEnabled = bos,
            eosEnabled = eos,
            addGenPrompt = addGenPrompt,
            stopTokensJson = JSONArray(stopWords).toString()
        )
        modelDao.updateModel(updated)
    }

    suspend fun importModel(uri: android.net.Uri, name: String): Result<Model> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Validate file extension
                if (!name.endsWith(".gguf", ignoreCase = true)) {
                    return@withContext Result.failure(Exception("Only .gguf files are supported"))
                }

                // 2. Prepare destination
                val safeName = name.replace(Regex("[^a-zA-Z0-9.\\-]"), "_")
                val fileName = if (safeName.endsWith(".gguf", ignoreCase = true)) safeName else "$safeName.gguf"

                // 3. Copy file to internal storage
                val storedFile = storageManager.copyUriToStorage(uri, fileName).getOrElse {
                    return@withContext Result.failure(it)
                }

                // 4. Create Entity

                // PERF FIX: Infer template + quantization from model filename
                // instead of always defaulting to chatml_default + Q4_K_M.
                // Correct template = correct stop tokens = no hallucinated garbage at end of reply.
                val inferredTemplateId = com.localmind.app.llm.prompt.TemplateCatalog
                    .inferTemplateId(modelNameHint = name, repoIdHint = null)
                val inferredQuantization = run {
                    val upper = name.uppercase()
                    val match = Regex("Q\\d+(_[A-Z0-9]+)?").find(upper)
                    match?.value ?: "Q4_K_M"
                }
                val entity = ModelEntity(
                    id = UUID.randomUUID().toString(),
                    name = name.removeSuffix(".gguf"),
                    filePath = storedFile.absolutePath,
                    sizeBytes = storedFile.length(),
                    quantization = inferredQuantization,
                    contextLength = 4096, // Safer default — most modern models support 4K+
                    parameterCount = "Unknown",
                    installDate = System.currentTimeMillis(),
                    isActive = true,
                    lastUsed = System.currentTimeMillis(),
                    storageType = "INTERNAL",
                    storageUri = null,
                    fileName = storedFile.name,
                    templateId = inferredTemplateId,
                    stopTokensJson = defaultStopTokensJson,
                    recommendedTemperature = 0.7f,
                    recommendedTopP = 0.9f,
                    recommendedTopK = 40,
                    recommendedRepeatPenalty = 1.1f,
                    recommendedSystemPrompt = null,
                    supportsVision = name.contains("llava", ignoreCase = true) || name.contains("vision", ignoreCase = true),
                    bosEnabled = true,
                    eosEnabled = true,
                    addGenPrompt = true
                )

                // 5. Insert into DB
                modelDao.deactivateAllModels()
                modelDao.insertModel(entity)

                Result.success(entity.toDomain())
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    private fun normalizeFileName(fileName: String, filePath: String?): String {
        if (fileName.isNotBlank() && !fileName.contains("/") && !fileName.contains("\\")) {
            return fileName
        }
        if (!filePath.isNullOrBlank()) {
            return File(filePath).name
        }
        return "model.gguf"
    }

    private fun parseSplitInfo(fileName: String): SplitInfo? {
        val match = SPLIT_GGUF_REGEX.matchEntire(fileName) ?: return null
        val prefix = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val partIndex = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        val totalParts = match.groupValues.getOrNull(3)?.toIntOrNull() ?: return null
        if (totalParts <= 1 || partIndex <= 0 || partIndex > totalParts) return null
        return SplitInfo(prefix = prefix, totalParts = totalParts)
    }

    private data class SplitInfo(
        val prefix: String,
        val totalParts: Int
    )

    private companion object {
        private val SPLIT_GGUF_REGEX = Regex(
            pattern = "^(.*)-(\\d{5})-of-(\\d{5})\\.gguf$",
            option = RegexOption.IGNORE_CASE
        )
    }
}
