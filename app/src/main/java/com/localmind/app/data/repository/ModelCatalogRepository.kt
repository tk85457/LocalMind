package com.localmind.app.data.repository

import com.localmind.app.core.performance.DeviceProfileManager
import com.localmind.app.data.remote.HFModelResponse
import com.localmind.app.data.remote.HFSibling
import com.localmind.app.data.remote.HuggingFaceApi
import com.localmind.app.domain.model.HuggingFaceModelInfo
import com.localmind.app.domain.model.ModelCatalogItem
import com.localmind.app.domain.model.ModelCategory
import com.localmind.app.domain.model.ModelCompatibilityState
import com.localmind.app.domain.model.ModelMemoryTier
import com.localmind.app.domain.model.ModelRunTarget
import com.localmind.app.domain.model.ModelVariant
import com.localmind.app.domain.model.ModelVariantSizeSource
import com.localmind.app.llm.prompt.TemplateCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelCatalogRepository @Inject constructor(
    private val api: HuggingFaceApi,
    private val huggingFaceRepository: HuggingFaceRepository,
    private val deviceProfileManager: DeviceProfileManager
) {
    data class ModelCatalogPageResult(
        val items: List<ModelCatalogItem>,
        val hasMore: Boolean,
        val nextCursor: String?
    )

    suspend fun loadCatalog(
        query: String,
        sort: String = "downloads",
        direction: Int = -1
    ): List<ModelCatalogItem> = withContext(Dispatchers.IO) {
        val profile = deviceProfileManager.currentProfile()

        val curated = huggingFaceRepository.searchModels(query)
            .map { curatedToCatalog(it, profile.totalRamGb) }

        markDownloaded(curated)
    }

    suspend fun loadLiveCatalogPage(
        query: String,
        sort: String = "downloads",
        direction: Int = -1,
        cursor: String?,
        pageSize: Int
    ): ModelCatalogPageResult = withContext(Dispatchers.IO) {
        val profile = deviceProfileManager.currentProfile()
        val safePageSize = pageSize.coerceIn(10, 100)

        val allItems = fetchLiveCatalog(
            query = query.ifBlank { "gguf" },
            totalRamGb = profile.totalRamGb,
            sort = sort,
            direction = direction
        )
        val startIndex = if (cursor.isNullOrBlank()) {
            0
        } else {
            val index = allItems.indexOfFirst { cursorKey(it) == cursor }
            if (index >= 0) index + 1 else 0
        }

        if (startIndex >= allItems.size) {
            return@withContext ModelCatalogPageResult(
                items = emptyList(),
                hasMore = false,
                nextCursor = null
            )
        }

        val endIndexExclusive = (startIndex + safePageSize).coerceAtMost(allItems.size)
        val pageItems = markDownloaded(allItems.subList(startIndex, endIndexExclusive))
        val hasMore = endIndexExclusive < allItems.size && pageItems.isNotEmpty()
        val nextCursor = pageItems.lastOrNull()?.let { cursorKey(it) }

        ModelCatalogPageResult(
            items = pageItems,
            hasMore = hasMore,
            nextCursor = if (hasMore) nextCursor else null
        )
    }

    suspend fun loadLiveModelDetails(repoId: String): ModelCatalogItem? = withContext(Dispatchers.IO) {
        val profile = deviceProfileManager.currentProfile()
        val normalizedRepoId = repoId.trim()
        if (normalizedRepoId.isBlank()) return@withContext null

        val response = runCatching {
            val call = api.getModelDetails(
                repoId = normalizedRepoId,
                expand = "siblings"
            )
            val res = call.execute()
            if (!res.isSuccessful) throw Exception("API Error: ${res.code()}")
            res.body()
        }.getOrNull() ?: return@withContext null

        val item = liveToCatalog(
            response = response,
            totalRamGb = profile.totalRamGb,
            variantLimit = 200
        ) ?: return@withContext null

        markDownloaded(listOf(item)).firstOrNull()
    }

    suspend fun searchCatalog(
        query: String,
        sort: String = "downloads",
        direction: Int = -1
    ): List<ModelCatalogItem> {
        return loadCatalog(query, sort, direction)
    }

    private suspend fun fetchLiveCatalog(
        query: String,
        totalRamGb: Int,
        sort: String = "downloads",
        direction: Int = -1
    ): List<ModelCatalogItem> {
        return runCatching {
            val liveSearch = if (query.isBlank()) "gguf" else query
            val call = api.searchModels(
                search = liveSearch,
                filter = "gguf",
                sort = sort,
                direction = direction,
                limit = 80,
                expand = "siblings"
            )
            val response = call.execute()
            if (!response.isSuccessful) throw Exception("API Error: ${response.code()}")
            val body = response.body() ?: emptyList()
            body.mapNotNull { it -> liveToCatalog(it, totalRamGb) }
        }.getOrElse { throw it }
    }

    private fun curatedToCatalog(info: HuggingFaceModelInfo, totalRamGb: Int): ModelCatalogItem {
        val modelSizeBytes = (info.sizeGb * 1024 * 1024 * 1024).toLong()
        val guidance = deviceProfileManager.getCompatibilityGuidance(
            modelSizeBytes = modelSizeBytes,
            modelNameHint = info.name,
            quantizationHint = info.quantization,
            parameterCountHint = info.parameterCount
        )
        val memoryTier = memoryTierFor(
            compatible = guidance.compatible,
            totalRamGb = totalRamGb,
            sizeGb = info.sizeGb
        )
        val templateId = TemplateCatalog.inferTemplateId(info.name, info.repoId)
        val templateSpec = TemplateCatalog.get(templateId)
        return ModelCatalogItem(
            id = info.id,
            name = info.name,
            repoId = info.repoId,
            author = info.author,
            lastModified = null,
            description = info.description,
            parameterCount = info.parameterCount,
            sizeGb = info.sizeGb,
            quantization = info.quantization,
            ggufFileName = info.ggufFileName,
            downloadUrl = info.downloadUrl,
            category = info.category,
            minRamGb = info.minRamGb,
            downloads = info.downloads,
            likes = info.likes,
            tags = info.tags,
            isVision = info.tags.any { it.contains("vision", ignoreCase = true) },
            requiresToken = false,
            isDownloaded = info.isDownloaded,
            isDownloading = info.isDownloading,
            downloadProgress = info.downloadProgress,
            templateId = templateId,
            stopTokens = templateSpec.defaultStopTokens,
            recommendedTemperature = 0.7f,
            recommendedTopP = 0.9f,
            recommendedTopK = 40,
            recommendedRepeatPenalty = 1.1f,
            recommendedSystemPrompt = templateSpec.defaultSystemPrompt,
            compatibilityWarning = guidance.reason ?: compatibilityWarning(
                totalRamGb = totalRamGb,
                sizeGb = info.sizeGb,
                quantization = info.quantization
            ),
            isCompatible = guidance.compatible,
            compatibilityReason = guidance.reason,
            compatibilityState = if (guidance.compatible) {
                ModelCompatibilityState.COMPATIBLE
            } else {
                ModelCompatibilityState.NOT_COMPATIBLE
            },
            compatibilityFixTips = guidance.fixTips,
            memoryTier = memoryTier,
            runTarget = ModelRunTarget.LOCAL,
            variants = listOf(
                ModelVariant(
                    filename = info.ggufFileName,
                    quantization = info.quantization,
                    sizeBytes = modelSizeBytes,
                    sizeSource = ModelVariantSizeSource.ESTIMATED,
                    estimatedRamBytes = (info.sizeGb * 1.4 * 1024 * 1024 * 1024).toLong(),
                    memoryTier = memoryTier,
                    compatible = guidance.compatible,
                    compatReason = guidance.reason,
                    compatFixTips = guidance.fixTips,
                    rank = 100
                )
            ),
            selectedVariantFilename = info.ggufFileName
        )
    }

    private fun liveToCatalog(
        response: HFModelResponse,
        totalRamGb: Int,
        variantLimit: Int = 8
    ): ModelCatalogItem? {
        val repoId = response.modelId.takeIf { it.isNotBlank() } ?: return null
        val variants = buildVariants(
            response = response,
            totalRamGb = totalRamGb,
            maxVariants = variantLimit.coerceAtLeast(8).coerceAtMost(256)
        )
        val selectedFile = variants.maxByOrNull { it.rank } ?: return null
        val sizeGb = selectedFile.sizeBytes.toDouble() / BYTES_IN_GB
        val parameterCount = estimateParameterCountLabel(repoId, selectedFile.filename)
        val guidance = deviceProfileManager.getCompatibilityGuidance(
            modelSizeBytes = selectedFile.sizeBytes,
            modelNameHint = repoId.substringAfterLast('/'),
            quantizationHint = selectedFile.quantization,
            parameterCountHint = parameterCount
        )
        val templateId = TemplateCatalog.inferTemplateId(
            modelNameHint = repoId.substringAfterLast('/'),
            repoIdHint = repoId
        )
        val templateSpec = TemplateCatalog.get(templateId)
        val tags = response.tags ?: emptyList()
        return ModelCatalogItem(
            id = repoId,
            name = repoId.substringAfterLast('/'),
            repoId = repoId,
            author = response.author ?: repoId.substringBefore('/'),
            lastModified = response.lastModified,
            description = "HF model - ${response.downloads} downloads - ${response.likes} likes",
            parameterCount = parameterCount,
            sizeGb = sizeGb,
            quantization = selectedFile.quantization,
            ggufFileName = selectedFile.filename,
            downloadUrl = "https://huggingface.co/$repoId/resolve/main/${selectedFile.filename}",
            category = categoryForSize(sizeGb),
            minRamGb = minRamForSize(sizeGb),
            downloads = response.downloads,
            likes = response.likes,
            tags = tags,
            isVision = isVisionModel(tags, response.pipelineTag),
            requiresToken = requiresToken(tags),
            templateId = templateId,
            stopTokens = templateSpec.defaultStopTokens,
            recommendedTemperature = 0.7f,
            recommendedTopP = 0.9f,
            recommendedTopK = 40,
            recommendedRepeatPenalty = 1.1f,
            recommendedSystemPrompt = templateSpec.defaultSystemPrompt,
            compatibilityWarning = guidance.reason ?: compatibilityWarning(
                totalRamGb = totalRamGb,
                sizeGb = sizeGb,
                quantization = selectedFile.quantization
            ),
            isCompatible = guidance.compatible,
            compatibilityReason = guidance.reason,
            compatibilityState = if (guidance.compatible) {
                ModelCompatibilityState.COMPATIBLE
            } else {
                ModelCompatibilityState.NOT_COMPATIBLE
            },
            compatibilityFixTips = guidance.fixTips,
            memoryTier = selectedFile.memoryTier,
            runTarget = ModelRunTarget.LOCAL,
            variants = variants
                .take(variantLimit)
                .map {
                ModelVariant(
                    filename = it.filename,
                    quantization = it.quantization,
                    sizeBytes = it.sizeBytes,
                    sizeSource = it.sizeSource,
                    estimatedRamBytes = (it.sizeGb * 1.4 * 1024 * 1024 * 1024).toLong(),
                    memoryTier = it.memoryTier,
                    compatible = it.compatible,
                    compatReason = it.compatReason,
                    compatFixTips = it.compatFixTips,
                    rank = it.rank
                )
            },
            selectedVariantFilename = selectedFile.filename
        )
    }

    private fun buildVariants(
        response: HFModelResponse,
        totalRamGb: Int,
        maxVariants: Int = 24
    ): List<RankedGgufFile> {
        val safeMaxVariants = maxVariants.coerceIn(8, 256)
        val ggufSiblings = response.siblings
            .orEmpty()
            .asSequence()
            .filter { it.filename.endsWith(".gguf", ignoreCase = true) }
            .take((safeMaxVariants * 32).coerceAtMost(1024))
            .toList()

        if (ggufSiblings.isEmpty()) return emptyList()

        val splitGroups = LinkedHashMap<String, MutableList<HFSibling>>()
        val standalone = mutableListOf<HFSibling>()

        ggufSiblings.forEach { sibling ->
            val splitInfo = parseSplitInfo(sibling.filename)
            if (splitInfo == null) {
                standalone += sibling
            } else {
                val groupKey = "${splitInfo.prefix.lowercase(Locale.US)}|${splitInfo.totalParts}"
                splitGroups.getOrPut(groupKey) { mutableListOf() }.add(sibling)
            }
        }

        val ranked = mutableListOf<RankedGgufFile>()
        standalone.forEach { sibling ->
            ranked += buildRankedVariant(
                response = response,
                totalRamGb = totalRamGb,
                siblings = listOf(sibling),
                splitTemplate = null
            )
        }
        splitGroups.values.forEach { siblings ->
            val splitTemplate = siblings
                .asSequence()
                .mapNotNull { parseSplitInfo(it.filename) }
                .firstOrNull()
            if (splitTemplate != null) {
                ranked += buildRankedVariant(
                    response = response,
                    totalRamGb = totalRamGb,
                    siblings = siblings,
                    splitTemplate = splitTemplate
                )
            }
        }

        return ranked
            .sortedByDescending { it.rank }
            .take(safeMaxVariants)
            .toList()
    }

    private fun buildRankedVariant(
        response: HFModelResponse,
        totalRamGb: Int,
        siblings: List<HFSibling>,
        splitTemplate: SplitInfo?
    ): RankedGgufFile {
        val sortedSiblings = if (splitTemplate != null) {
            siblings.sortedBy { sibling ->
                parseSplitInfo(sibling.filename)?.partIndex ?: Int.MAX_VALUE
            }
        } else {
            siblings
        }
        val primarySibling = if (splitTemplate != null) {
            sortedSiblings.firstOrNull { parseSplitInfo(it.filename)?.partIndex == 1 }
                ?: sortedSiblings.first()
        } else {
            sortedSiblings.first()
        }

        val file = primarySibling.filename
        val quant = extractQuantization(file)
        val exactSizes = sortedSiblings
            .mapNotNull { sibling -> sibling.lfs?.size ?: sibling.size }
            .filter { it > 0L }
        val observedParts = sortedSiblings
            .mapNotNull { sibling -> parseSplitInfo(sibling.filename)?.partIndex }
            .distinct()
            .size
        val expectedParts = splitTemplate?.totalParts ?: 1
        val hasAllParts = splitTemplate == null || observedParts >= expectedParts
        val isExact = if (splitTemplate == null) {
            exactSizes.isNotEmpty()
        } else {
            hasAllParts && exactSizes.size == expectedParts
        }
        val sizeBytes = when {
            isExact -> exactSizes.sum().coerceAtLeast(1L)
            exactSizes.isNotEmpty() && splitTemplate != null -> {
                val avgPartSize = exactSizes.average().toLong().coerceAtLeast(1L)
                (avgPartSize * expectedParts).coerceAtLeast(exactSizes.sum())
            }
            exactSizes.isNotEmpty() -> exactSizes.first()
            else -> (estimateModelSizeGb(response.modelId, file) * BYTES_IN_GB).toLong()
        }
        val sizeSource = if (isExact) {
            ModelVariantSizeSource.EXACT
        } else {
            ModelVariantSizeSource.ESTIMATED
        }
        val sizeGb = sizeBytes.toDouble() / BYTES_IN_GB
        val guidance = deviceProfileManager.getCompatibilityGuidance(
            modelSizeBytes = sizeBytes,
            modelNameHint = response.modelId.substringAfterLast('/'),
            quantizationHint = quant,
            parameterCountHint = estimateParameterCountLabel(response.modelId, file)
        )
        return RankedGgufFile(
            filename = file,
            quantization = quant,
            sizeBytes = sizeBytes,
            sizeSource = sizeSource,
            sizeGb = sizeGb,
            rank = scoreQuantization(quant, totalRamGb, sizeGb),
            memoryTier = memoryTierFor(
                compatible = guidance.compatible,
                totalRamGb = totalRamGb,
                sizeGb = sizeGb
            ),
            compatible = guidance.compatible,
            compatReason = guidance.reason,
            compatFixTips = guidance.fixTips
        )
    }

    private fun memoryTierFor(
        compatible: Boolean,
        totalRamGb: Int,
        sizeGb: Double
    ): ModelMemoryTier {
        if (!compatible) return ModelMemoryTier.LOW_MEMORY
        val tightThreshold = when {
            totalRamGb <= 4 -> 1.2
            totalRamGb <= 6 -> 2.4
            else -> 4.0
        }
        return if (sizeGb > tightThreshold) {
            ModelMemoryTier.MEMORY_TIGHT
        } else {
            ModelMemoryTier.GOOD
        }
    }

    private fun isVisionModel(tags: List<String>, pipelineTag: String?): Boolean {
        val normalizedTags = tags.map { it.lowercase(Locale.US) }
        val normalizedPipeline = pipelineTag.orEmpty().lowercase(Locale.US)
        return normalizedPipeline.contains("image") ||
            normalizedPipeline.contains("vision") ||
            normalizedTags.any {
                it.contains("vision") ||
                    it.contains("multimodal") ||
                    it.contains("image")
            }
    }

    private fun requiresToken(tags: List<String>): Boolean {
        val normalized = tags.map { it.lowercase(Locale.US) }
        return normalized.any {
            it.contains("gated") ||
                it.contains("requires-auth") ||
                it == "private"
        }
    }

    private fun scoreQuantization(quant: String, totalRamGb: Int, sizeGb: Double): Int {
        val quantScore = when {
            quant.startsWith("Q4_K_M", ignoreCase = true) -> 100
            quant.startsWith("Q5_K_M", ignoreCase = true) -> 90
            quant.startsWith("Q4_K_S", ignoreCase = true) -> 80
            quant.startsWith("Q4_0", ignoreCase = true) -> 70
            quant.startsWith("Q5", ignoreCase = true) -> 60
            quant.startsWith("Q4", ignoreCase = true) -> 50
            quant.startsWith("Q8", ignoreCase = true) -> if (totalRamGb <= 4) -50 else 20
            else -> 10
        }
        val sizePenalty = (sizeGb * 4).toInt()
        return quantScore - sizePenalty
    }

    private fun extractQuantization(fileName: String): String {
        val match = Regex("Q\\d(?:_[A-Z0-9]+)+|Q\\d(?:_[0-9])?|Q\\d", RegexOption.IGNORE_CASE)
            .find(fileName)
            ?.value
        return match?.uppercase(Locale.US) ?: "Q4_K_M"
    }

    private fun parseSplitInfo(fileName: String): SplitInfo? {
        val match = SPLIT_GGUF_REGEX.matchEntire(fileName) ?: return null
        val prefix = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val partIndex = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        val totalParts = match.groupValues.getOrNull(3)?.toIntOrNull() ?: return null
        if (totalParts <= 1 || partIndex <= 0 || partIndex > totalParts) return null
        return SplitInfo(prefix = prefix, partIndex = partIndex, totalParts = totalParts)
    }

    private fun estimateModelSizeGb(repoId: String, fileName: String): Double {
        val hint = "$repoId $fileName".lowercase(Locale.US)
        return when {
            hint.contains("13b") -> 6.0
            hint.contains("8b") -> 4.3
            hint.contains("7b") -> 3.8
            hint.contains("4b") -> 2.5
            hint.contains("3b") -> 1.9
            hint.contains("2.7b") -> 1.5
            hint.contains("2b") -> 1.3
            hint.contains("1.7b") -> 1.1
            hint.contains("1.5b") -> 0.95
            hint.contains("1.3b") -> 0.85
            hint.contains("1.1b") -> 0.7
            hint.contains("1b") -> 0.65
            else -> 1.4
        }
    }

    private fun estimateParameterCountLabel(repoId: String, fileName: String): String {
        val hint = "$repoId $fileName".lowercase(Locale.US)
        val match = Regex("(\\d+(?:\\.\\d+)?)b").find(hint)?.groupValues?.getOrNull(1)
        return if (match != null) "${match}B" else "Unknown"
    }

    private fun categoryForSize(sizeGb: Double): ModelCategory {
        return when {
            sizeGb < 0.9 -> ModelCategory.TINY
            sizeGb < 2.2 -> ModelCategory.SMALL
            else -> ModelCategory.MEDIUM
        }
    }

    private fun minRamForSize(sizeGb: Double): Int {
        return when {
            sizeGb < 2.4 -> 4
            sizeGb < 4.6 -> 6
            else -> 8
        }
    }

    private fun maxAllowedSizeForRam(totalRamGb: Int): Double {
        return when {
            totalRamGb <= 4 -> 2.8
            totalRamGb <= 6 -> 4.2
            else -> 8.0
        }
    }

    private fun compatibilityWarning(totalRamGb: Int, sizeGb: Double, quantization: String): String? {
        return when {
            totalRamGb <= 4 && quantization.startsWith("Q8", ignoreCase = true) ->
                "Q8 quantization is unstable on 4GB devices."
            totalRamGb <= 4 && sizeGb > 2.6 ->
                "Model may be slow or fail on 4GB RAM devices."
            totalRamGb <= 6 && sizeGb > 4.0 ->
                "Large model. Keep low context/tokens for stable responses."
            else -> null
        }
    }

    private fun ModelCatalogItem.toLegacyInfo(): HuggingFaceModelInfo {
        return HuggingFaceModelInfo(
            id = id,
            name = name,
            repoId = repoId,
            author = author,
            description = description,
            parameterCount = parameterCount,
            sizeGb = sizeGb,
            quantization = quantization,
            ggufFileName = ggufFileName,
            downloadUrl = downloadUrl,
            category = category,
            minRamGb = minRamGb,
            downloads = downloads,
            likes = likes,
            tags = tags,
            isDownloaded = isDownloaded,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress
        )
    }

    private suspend fun markDownloaded(items: List<ModelCatalogItem>): List<ModelCatalogItem> {
        return items.map { item ->
            val downloaded = runCatching {
                huggingFaceRepository.isModelDownloaded(item.toLegacyInfo())
            }.getOrDefault(false)
            item.copy(isDownloaded = downloaded)
        }
    }

    private fun cursorKey(item: ModelCatalogItem): String {
        return "${item.repoId.lowercase(Locale.US)}|${item.selectedVariantFilename?.lowercase(Locale.US).orEmpty()}"
    }

    private data class RankedGgufFile(
        val filename: String,
        val quantization: String,
        val sizeBytes: Long,
        val sizeSource: ModelVariantSizeSource,
        val sizeGb: Double,
        val rank: Int,
        val memoryTier: ModelMemoryTier,
        val compatible: Boolean,
        val compatReason: String?,
        val compatFixTips: List<String>
    )

    private data class SplitInfo(
        val prefix: String,
        val partIndex: Int,
        val totalParts: Int
    )

    private companion object {
        private const val BYTES_IN_GB = 1024.0 * 1024.0 * 1024.0
        private val SPLIT_GGUF_REGEX = Regex(
            pattern = "^(.*)-(\\d{5})-of-(\\d{5})\\.gguf$",
            option = RegexOption.IGNORE_CASE
        )
    }
}

