package com.localmind.app.ui.viewmodel

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmind.app.core.rollout.FeatureRolloutConfig
import com.localmind.app.core.storage.StorageAdmissionManager
import com.localmind.app.core.utils.NetworkUtils
import com.localmind.app.core.utils.StorageUtils
import com.localmind.app.data.repository.DownloadState
import com.localmind.app.data.repository.HuggingFaceRepository
import com.localmind.app.data.repository.ModelCatalogRepository
import com.localmind.app.data.repository.ModelRepository
import com.localmind.app.data.repository.SettingsRepository
import com.localmind.app.domain.model.ModelCatalogItem
import com.localmind.app.domain.model.ModelCategory
import com.localmind.app.domain.model.ModelCompatibilityState
import com.localmind.app.domain.model.ModelVariantSizeSource
import com.localmind.app.llm.ActivationOptions
import com.localmind.app.llm.ActivationSource
import com.localmind.app.llm.ModelLifecycleManager
import com.localmind.app.ui.components.DownloadMetricsFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

enum class HubSortOrder(val value: String, val label: String) {
    RELEVANCE("relevance", "Relevance"),
    DOWNLOADS("downloads", "Most Downloaded"),
    LIKES("likes", "Most Liked"),
    LAST_MODIFIED("lastModified", "Recently Added")
}

data class ModelHubUiState(
    val models: List<ModelCatalogItem> = emptyList(),
    val curatedModels: List<ModelCatalogItem> = emptyList(),
    val filteredModels: List<ModelCatalogItem> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: ModelCategory = ModelCategory.ALL,
    val selectedAuthor: String = "All",
    val availableAuthors: List<String> = listOf("All"),
    val hubSortOrder: HubSortOrder = HubSortOrder.RELEVANCE,
    val isLoading: Boolean = false,
    val isLoadingNextPage: Boolean = false,
    val hasMore: Boolean = true,
    val nextCursor: String? = null,
    val downloadingModels: Map<String, DownloadProgress> = emptyMap(),
    val pendingDownloadModelIds: Set<String> = emptySet(),
    val downloadedModelIds: Set<String> = emptySet(),
    val selectedVariants: Map<String, String> = emptyMap(),
    val activeModelId: String? = null,
    val autoActivatingModelIds: Set<String> = emptySet(),
    val forceModeEnabled: Boolean = false,
    val showAdvancedSettings: Boolean = false,
    val currentContextSize: Int = 2048,
    val currentThreadCount: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
    val currentMaxTokens: Int = 512,
    val currentTemperature: Float = 0.7f,
    val currentTopP: Float = 0.9f,
    val currentTopK: Int = 40,
    val currentRepeatPenalty: Float = 1.1f,
    val onlineDetailModel: ModelCatalogItem? = null,
    val isOnlineDetailLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

data class DownloadProgress(
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBps: Long = 0L,
    val etaSeconds: Long = -1L,
    val estimatedFinishAtMs: Long? = null
)

@HiltViewModel
class HuggingFaceViewModel @Inject constructor(
    private val repository: HuggingFaceRepository,
    private val modelCatalogRepository: ModelCatalogRepository,
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository,
    private val modelLifecycleManager: ModelLifecycleManager,
    private val storageAdmissionManager: StorageAdmissionManager,
    private val featureRolloutConfig: FeatureRolloutConfig,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val pageSize = 15

    @Volatile
    private var catalogCursorPagingEnabled: Boolean = true

    private val _uiState = MutableStateFlow(ModelHubUiState())
    val uiState: StateFlow<ModelHubUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<NavigationEvent>()
    val navigationEvents = _navigationEvents.asSharedFlow()

    private var searchJob: Job? = null
    private var catalogLoadJob: Job? = null
    private var detailLoadJob: Job? = null
    private val downloadTrackingJobs = mutableMapOf<String, Job>()
    private val activationMutex = Mutex()
    @Volatile private var pendingActivationModelId: String? = null

    init {
        observeRuntimeMode()
        observeAdvancedSettings()
        observeActiveModel()
        observeRolloutFlags()
        observeLocalModels()
        loadModels(reset = true)
        loadCuratedModels()
        restoreActiveDownloads()
    }

    private fun loadCuratedModels() {
        viewModelScope.launch {
            val curated = repository.getCuratedModels().map { info ->
                // Map HuggingFaceModelInfo to ModelCatalogItem
                com.localmind.app.domain.model.ModelCatalogItem(
                    id = info.id,
                    name = info.name,
                    repoId = info.repoId,
                    author = info.author,
                    description = info.description,
                    sizeGb = info.sizeGb,
                    parameterCount = info.parameterCount,
                    quantization = info.quantization,
                    ggufFileName = info.ggufFileName,
                    downloadUrl = info.downloadUrl,
                    isDownloaded = _uiState.value.downloadedModelIds.contains(info.id),
                    category = info.category,
                    tags = info.tags,
                    minRamGb = info.minRamGb
                )
            }
            _uiState.update { it.copy(curatedModels = curated) }
        }
    }

    private fun restoreActiveDownloads() {
        viewModelScope.launch {
            repository.getActiveDownloads().collect { tasks ->
                tasks.forEach { task ->
                    if (!_uiState.value.downloadingModels.containsKey(task.modelId)) {
                        _uiState.update {
                            it.copy(
                                downloadingModels = it.downloadingModels + (
                                    task.modelId to DownloadProgress(
                                        progress = task.progress / 100f,
                                        downloadedBytes = task.downloadedBytes,
                                        totalBytes = task.totalBytes,
                                        speedBps = task.speedBps,
                                        etaSeconds = task.etaSeconds
                                    )
                                ),
                                pendingDownloadModelIds = it.pendingDownloadModelIds + task.modelId
                            )
                        }
                        // Start tracking progress for this restored task
                        trackDownloadProgress(task.modelId)
                    }
                }
            }
        }
    }

    private fun trackDownloadProgress(modelId: String) {
        if (downloadTrackingJobs[modelId]?.isActive == true) return

        val trackingJob = viewModelScope.launch {
            repository.getDownloadProgress(modelId)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            pendingDownloadModelIds = it.pendingDownloadModelIds - modelId,
                            downloadingModels = it.downloadingModels - modelId,
                            errorMessage = error.message ?: "Download tracking failed"
                        )
                    }
                }
                .onEach { state ->
                    when (state) {
                        is DownloadState.Downloading -> {
                            _uiState.update {
                                it.copy(
                                    downloadingModels = it.downloadingModels + (
                                        modelId to DownloadProgress(
                                            progress = state.progress,
                                            downloadedBytes = state.downloadedBytes,
                                            totalBytes = state.totalBytes,
                                            speedBps = state.speedBps,
                                            etaSeconds = state.etaSeconds,
                                            estimatedFinishAtMs = DownloadMetricsFormatter.estimateFinishAtMs(
                                                state.etaSeconds
                                            )
                                        )
                                    )
                                )
                            }
                        }
                        is DownloadState.Completed -> {
                            val persistedModel = modelRepository.getModelById(modelId)
                            val isPersisted = persistedModel?.let { modelRepository.modelFileExists(it) } == true
                            if (!isPersisted) {
                                _uiState.update {
                                    it.copy(
                                        pendingDownloadModelIds = it.pendingDownloadModelIds - modelId,
                                        downloadingModels = it.downloadingModels - modelId,
                                        errorMessage = "Download finished but model file is missing. Please retry download."
                                    )
                                }
                                return@onEach
                            }
                            _uiState.update { current ->
                                val newDownloadedIds = current.downloadedModelIds + modelId
                                val updatedModels = current.models.map { model ->
                                    if (model.id == modelId) model.copy(isDownloaded = true) else model
                                }
                                val updatedCurated = current.curatedModels.map { model ->
                                    if (model.id == modelId) model.copy(isDownloaded = true) else model
                                }
                                val updatedDetail = current.onlineDetailModel?.let { detail ->
                                    if (detail.id == modelId) detail.copy(isDownloaded = true) else detail
                                }
                                current.copy(
                                    models = updatedModels,
                                    curatedModels = updatedCurated,
                                    filteredModels = applyFilters(
                                        models = updatedModels,
                                        query = current.searchQuery,
                                        category = current.selectedCategory,
                                        selectedAuthor = current.selectedAuthor,
                                        downloadedIds = newDownloadedIds
                                    ),
                                    downloadedModelIds = newDownloadedIds,
                                    onlineDetailModel = updatedDetail,
                                    pendingDownloadModelIds = current.pendingDownloadModelIds - modelId,
                                    downloadingModels = current.downloadingModels - modelId,
                                    successMessage = "Model download completed & activating!"
                                )
                            }
                            // Auto-activate the newly downloaded model
                            activateModelInternal(
                                modelId = modelId,
                                source = ActivationSource.AUTO_RESTORE,
                                successMessage = "Model download completed & activated!",
                                failureMessage = "Model downloaded, but activation failed."
                            )
                        }
                        is DownloadState.Error -> {
                            _uiState.update {
                                it.copy(
                                    pendingDownloadModelIds = it.pendingDownloadModelIds - modelId,
                                    downloadingModels = it.downloadingModels - modelId,
                                    errorMessage = state.message
                                )
                            }
                        }
                        else -> Unit
                    }
                }
                .takeWhile { state ->
                    state !is DownloadState.Completed && state !is DownloadState.Error
                }
                .collect {
                    // no-op body; state handling happens in onEach for clarity.
                }
        }
        downloadTrackingJobs[modelId] = trackingJob
        trackingJob.invokeOnCompletion {
            downloadTrackingJobs.remove(modelId)
        }
    }

    private fun observeRolloutFlags() {
        viewModelScope.launch {
            featureRolloutConfig.flags.collect { flags ->
                catalogCursorPagingEnabled = flags.catalogCursorPaging
            }
        }
    }

    private fun observeLocalModels() {
        viewModelScope.launch {
            modelRepository.getAllModels().collect { localModels ->
                val downloadedIds = localModels.map { it.id }.toSet()
                _uiState.update { current ->
                    current.copy(
                        downloadedModelIds = downloadedIds,
                        models = current.models.map { it.copy(isDownloaded = downloadedIds.contains(it.id)) },
                        curatedModels = current.curatedModels.map { it.copy(isDownloaded = downloadedIds.contains(it.id)) },
                        filteredModels = applyFilters(
                            models = current.models.map { it.copy(isDownloaded = downloadedIds.contains(it.id)) },
                            query = current.searchQuery,
                            category = current.selectedCategory,
                            selectedAuthor = current.selectedAuthor,
                            downloadedIds = downloadedIds
                        )
                    )
                }
            }
        }
    }

    private fun observeRuntimeMode() {
        viewModelScope.launch {
            settingsRepository.allowForceLoad.collect { enabled ->
                _uiState.update { it.copy(forceModeEnabled = enabled) }
            }
        }
    }

    private fun observeAdvancedSettings() {
        viewModelScope.launch {
            combine(
                settingsRepository.showAdvancedSettings,
                settingsRepository.contextSize,
                settingsRepository.threadCount,
                settingsRepository.maxTokens,
                settingsRepository.temperature,
                settingsRepository.topP,
                settingsRepository.topK,
                settingsRepository.repeatPenalty
            ) { values ->
                _uiState.update {
                    it.copy(
                        showAdvancedSettings = values[0] as Boolean,
                        currentContextSize = values[1] as Int,
                        currentThreadCount = values[2] as Int,
                        currentMaxTokens = values[3] as Int,
                        currentTemperature = values[4] as Float,
                        currentTopP = values[5] as Float,
                        currentTopK = values[6] as Int,
                        currentRepeatPenalty = values[7] as Float
                    )
                }
            }.collect()
        }
    }

    private fun observeActiveModel() {
        viewModelScope.launch {
            modelRepository.getActiveModelFlow().collect { activeModel ->
                _uiState.update { state ->
                    state.copy(
                        activeModelId = resolveUiActiveModelId(
                            activeModelId = activeModel?.id,
                            activeModelFileName = activeModel?.fileName,
                            models = state.models
                        )
                    )
                }
            }
        }
    }

    private fun loadModels(reset: Boolean) {
        val state = _uiState.value
        if (!catalogCursorPagingEnabled && !reset) return
        if (!reset && (state.isLoading || state.isLoadingNextPage || !state.hasMore)) return
        if (reset && state.isLoading) return

        catalogLoadJob?.cancel()
        catalogLoadJob = viewModelScope.launch {
            val pagingEnabled = catalogCursorPagingEnabled
            val previousState = _uiState.value
            val cursor = if (reset) null else previousState.nextCursor

            _uiState.update {
                if (reset) {
                    it.copy(
                        isLoading = true,
                        isLoadingNextPage = false,
                        errorMessage = null,
                        hasMore = true,
                        nextCursor = null
                    )
                } else {
                    it.copy(isLoadingNextPage = true, errorMessage = null)
                }
            }

            val (sort, direction) = when (_uiState.value.hubSortOrder) {
                HubSortOrder.RELEVANCE -> "downloads" to -1
                HubSortOrder.DOWNLOADS -> "downloads" to -1
                HubSortOrder.LIKES -> "likes" to -1
                HubSortOrder.LAST_MODIFIED -> "lastModified" to -1
            }

            if (!pagingEnabled) {
                val models = runCatching {
                    modelCatalogRepository.loadCatalog(
                        query = _uiState.value.searchQuery,
                        sort = sort,
                        direction = direction
                    )
                }.onFailure { error ->
                    Log.e("LocalMind-Catalog", "Failed to load non-paged catalog", error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingNextPage = false,
                            errorMessage = error.message ?: "Failed to load models"
                        )
                    }
                }.getOrNull() ?: return@launch

                applyCatalogUpdate(
                    reset = true,
                    previousModels = previousState.models,
                    pageModels = models
                )
                return@launch
            }

            val pageResult = runCatching {
                modelCatalogRepository.loadLiveCatalogPage(
                    query = _uiState.value.searchQuery,
                    sort = sort,
                    direction = direction,
                    cursor = cursor,
                    pageSize = pageSize
                )
            }.onFailure { error ->
                Log.e("LocalMind-Catalog", "Failed to load paged catalog", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingNextPage = false,
                        errorMessage = error.message ?: "Failed to load models"
                    )
                }
            }.getOrNull() ?: return@launch

            applyCatalogUpdate(
                reset = reset,
                previousModels = previousState.models,
                pageModels = pageResult.items,
                hasMore = pageResult.hasMore,
                nextCursor = pageResult.nextCursor
            )
        }
    }

    private suspend fun applyCatalogUpdate(
        reset: Boolean,
        previousModels: List<ModelCatalogItem>,
        pageModels: List<ModelCatalogItem>,
        hasMore: Boolean = false,
        nextCursor: String? = null
    ) {
        val selectedMap = _uiState.value.selectedVariants
        val pageItems = pageModels.map { model ->
            applySelectedVariant(model, selectedMap[model.id])
        }

        val mergedModels = if (reset) {
            pageItems
        } else {
            val merged = LinkedHashMap<String, ModelCatalogItem>()
            previousModels.forEach { merged[it.id] = it }
            pageItems.forEach { merged[it.id] = it }
            merged.values.toList()
        }

        val activeModel = modelRepository.getActiveModel()
        val resolvedActiveModelId = resolveUiActiveModelId(
            activeModelId = activeModel?.id,
            activeModelFileName = activeModel?.fileName,
            models = mergedModels
        )
        val downloadedIds = mergedModels.filter { it.isDownloaded }.map { it.id }.toSet()
        val authors = buildList {
            add("All")
            addAll(
                mergedModels
                    .map { it.author.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
            )
        }

        _uiState.update { current ->
            current.copy(
                models = mergedModels,
                filteredModels = applyFilters(
                    models = mergedModels,
                    query = current.searchQuery,
                    category = current.selectedCategory,
                    selectedAuthor = current.selectedAuthor,
                    downloadedIds = downloadedIds
                ),
                downloadedModelIds = downloadedIds,
                availableAuthors = authors,
                selectedAuthor = current.selectedAuthor.takeIf { authors.contains(it) } ?: "All",
                activeModelId = resolvedActiveModelId,
                isLoading = false,
                isLoadingNextPage = false,
                hasMore = hasMore,
                nextCursor = if (hasMore) nextCursor else null
            )
        }
    }

    fun loadNextPage() {
        loadModels(reset = false)
    }

    fun refreshCatalog() {
        loadModels(reset = true)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            loadModels(reset = true)
        }
    }

    fun onCategorySelected(category: ModelCategory) {
        _uiState.update { state ->
            state.copy(
                selectedCategory = category,
                filteredModels = applyFilters(
                    models = state.models,
                    query = state.searchQuery,
                    category = category,
                    selectedAuthor = state.selectedAuthor,
                    downloadedIds = state.downloadedModelIds
                )
            )
        }
    }

    fun onAuthorSelected(author: String) {
        _uiState.update { state ->
            state.copy(
                selectedAuthor = author,
                filteredModels = applyFilters(
                    models = state.models,
                    query = state.searchQuery,
                    category = state.selectedCategory,
                    selectedAuthor = author,
                    downloadedIds = state.downloadedModelIds
                )
            )
        }
    }

    fun onSortOrderChanged(sortOrder: HubSortOrder) {
        if (_uiState.value.hubSortOrder == sortOrder) return
        _uiState.update { it.copy(hubSortOrder = sortOrder) }
        loadModels(reset = true)
    }

    fun onVariantSelected(modelId: String, fileName: String) {
        _uiState.update { state ->
            val selected = state.selectedVariants + (modelId to fileName)
            val updatedModels = state.models.map { model ->
                if (model.id == modelId) applySelectedVariant(model, fileName) else model
            }
            val updatedDetail = state.onlineDetailModel?.let { detail ->
                if (detail.id == modelId) applySelectedVariant(detail, fileName) else detail
            }
            state.copy(
                selectedVariants = selected,
                models = updatedModels,
                filteredModels = applyFilters(
                    models = updatedModels,
                    query = state.searchQuery,
                    category = state.selectedCategory,
                    selectedAuthor = state.selectedAuthor,
                    downloadedIds = state.downloadedModelIds
                ),
                onlineDetailModel = updatedDetail
            )
        }
    }

    fun loadOnlineModelDetails(repoId: String) {
        if (repoId.isBlank()) return
        detailLoadJob?.cancel()
        detailLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(isOnlineDetailLoading = true, errorMessage = null) }
            val detail = modelCatalogRepository.loadLiveModelDetails(repoId)
            if (detail == null) {
                _uiState.update {
                    it.copy(
                        isOnlineDetailLoading = false,
                        onlineDetailModel = null,
                        errorMessage = "Model details unavailable. Please refresh and retry."
                    )
                }
                return@launch
            }

            val selected = _uiState.value.selectedVariants[detail.id]
            val effectiveDetail = applySelectedVariant(detail, selected)
            _uiState.update {
                it.copy(
                    isOnlineDetailLoading = false,
                    onlineDetailModel = effectiveDetail
                )
            }
        }
    }

    fun clearOnlineModelDetails() {
        _uiState.update { it.copy(onlineDetailModel = null, isOnlineDetailLoading = false) }
    }

    fun downloadModel(modelInfo: ModelCatalogItem) {
        downloadModelVariant(
            modelInfo = modelInfo,
            variantFileName = modelInfo.selectedVariantFilename ?: modelInfo.ggufFileName
        )
    }

    fun downloadModelVariant(modelInfo: ModelCatalogItem, variantFileName: String) {
        val uniqueId = "${modelInfo.repoId}|$variantFileName"
        if (
            _uiState.value.downloadingModels.containsKey(uniqueId) ||
            _uiState.value.pendingDownloadModelIds.contains(uniqueId)
        ) return

        if (!NetworkUtils.isOnline(context)) {
            Toast.makeText(context, "Please turn on internet first", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            val effectiveModel = applySelectedVariant(modelInfo, variantFileName).copy(
                id = uniqueId
            )

            if (_uiState.value.downloadedModelIds.contains(effectiveModel.id)) {
                _uiState.update { it.copy(successMessage = "Model already downloaded. Activating...") }
                activateModelInternal(
                    modelId = effectiveModel.id,
                    source = ActivationSource.AUTO_RESTORE,
                    successMessage = "Model activated!",
                    failureMessage = "Model downloaded, but activation failed."
                )
                return@launch
            }

            val selectedVariant = effectiveModel.variants
                .firstOrNull { it.filename == effectiveModel.ggufFileName }
            val exactSize = selectedVariant?.sizeSource == ModelVariantSizeSource.EXACT
            val requiredBytes = if (exactSize) {
                selectedVariant?.sizeBytes ?: 0L
            } else {
                0L
            }
            val admission = storageAdmissionManager.evaluate(
                requiredBytes = requiredBytes,
                exactSize = exactSize
            )
            if (!admission.allowed) {
                val requiredText = StorageUtils.formatSize(requiredBytes)
                val availableText = admission.availableBytes?.let(StorageUtils::formatSize) ?: "Unknown"
                _uiState.update {
                    it.copy(
                        pendingDownloadModelIds = it.pendingDownloadModelIds - effectiveModel.id,
                        errorMessage = "Not enough storage. Required: $requiredText, Available: $availableText."
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    pendingDownloadModelIds = it.pendingDownloadModelIds + effectiveModel.id,
                    downloadingModels = it.downloadingModels + (
                        effectiveModel.id to DownloadProgress(
                            progress = 0f,
                            downloadedBytes = 0L,
                            totalBytes = 0L,
                            speedBps = 0L,
                            etaSeconds = -1L,
                            estimatedFinishAtMs = null
                        )
                    )
                )
            }

            if (!isDownloadStillRequested(effectiveModel.id)) {
                return@launch
            }
            val startResult = runCatching {
                repository.startDownloadWorker(effectiveModel)
            }
            startResult.onFailure { error ->
                _uiState.update {
                    it.copy(
                        pendingDownloadModelIds = it.pendingDownloadModelIds - effectiveModel.id,
                        downloadingModels = it.downloadingModels - effectiveModel.id,
                        errorMessage = error.message ?: "Failed to start download"
                    )
                }
                return@launch
            }

            trackDownloadProgress(effectiveModel.id)
        }
    }

    fun activateModel(modelId: String) {
        viewModelScope.launch {
            activateModelInternal(
                modelId = modelId,
                source = ActivationSource.USER,
                successMessage = "Model activated!",
                failureMessage = "Could not start model on this phone."
            )
        }
    }

    fun offloadModel() {
        viewModelScope.launch {
            modelLifecycleManager.unloadModelSafely()
        }
    }

    fun cancelDownload(modelId: String) {
        viewModelScope.launch {
            repository.cancelDownload(modelId)
            downloadTrackingJobs.remove(modelId)?.cancel()
            _uiState.update {
                it.copy(
                    pendingDownloadModelIds = it.pendingDownloadModelIds - modelId,
                    downloadingModels = it.downloadingModels - modelId
                )
            }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            try {
                if (_uiState.value.activeModelId == modelId) {
                    modelLifecycleManager.unloadModelSafely()
                }
                modelRepository.deleteModel(modelId)

                // Update UI state to reflect model is no longer downloaded
                _uiState.update { current ->
                    val newDownloadedIds = current.downloadedModelIds - modelId
                    val updatedModels = current.models.map { model ->
                        if (model.id == modelId) model.copy(isDownloaded = false) else model
                    }
                    val updatedDetail = current.onlineDetailModel?.let { detail ->
                        if (detail.id == modelId) detail.copy(isDownloaded = false) else detail
                    }
                    current.copy(
                        models = updatedModels,
                        curatedModels = current.curatedModels.map {
                            if (it.id == modelId) it.copy(isDownloaded = false) else it
                        },
                        filteredModels = applyFilters(
                            models = updatedModels,
                            query = current.searchQuery,
                            category = current.selectedCategory,
                            selectedAuthor = current.selectedAuthor,
                            downloadedIds = newDownloadedIds
                        ),
                        downloadedModelIds = newDownloadedIds,
                        onlineDetailModel = updatedDetail,
                        successMessage = "Model deleted successfully"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to delete: ${e.message}") }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private suspend fun activateModelInternal(
        modelId: String,
        source: ActivationSource,
        successMessage: String,
        failureMessage: String
    ) {
        pendingActivationModelId = modelId
        _uiState.update {
            it.copy(autoActivatingModelIds = it.autoActivatingModelIds + modelId)
        }

        activationMutex.withLock {
            if (pendingActivationModelId != modelId) {
                _uiState.update {
                    it.copy(autoActivatingModelIds = it.autoActivatingModelIds - modelId)
                }
                return
            }

            var targetModelId = resolveActivationTargetId(modelId)
            var safeResult = modelLifecycleManager.activateModelSafely(
                modelId = targetModelId,
                options = ActivationOptions(
                    forceLoad = false,
                    source = source
                )
            )
            if (
                safeResult.isFailure &&
                safeResult.exceptionOrNull()?.message?.contains("Model not found", ignoreCase = true) == true
            ) {
                delay(250L)
                targetModelId = resolveActivationTargetId(modelId)
                safeResult = modelLifecycleManager.activateModelSafely(
                    modelId = targetModelId,
                    options = ActivationOptions(
                        forceLoad = false,
                        source = source
                    )
                )
            }
            if (pendingActivationModelId != modelId) {
                _uiState.update {
                    it.copy(autoActivatingModelIds = it.autoActivatingModelIds - modelId)
                }
                return
            }

            val finalResult = if (safeResult.isSuccess) {
                safeResult
            } else {
                modelLifecycleManager.activateModelSafely(
                    modelId = targetModelId,
                    options = ActivationOptions(
                        forceLoad = true,
                        source = source
                    )
                )
            }
            if (pendingActivationModelId != modelId) {
                _uiState.update {
                    it.copy(autoActivatingModelIds = it.autoActivatingModelIds - modelId)
                }
                return
            }

            finalResult
                .onSuccess {
                    _uiState.update { state ->
                        val resolvedUiActiveId = resolveUiActiveModelId(
                            activeModelId = targetModelId,
                            activeModelFileName = null,
                            models = state.models
                        ) ?: modelId
                        state.copy(
                            activeModelId = resolvedUiActiveId,
                            autoActivatingModelIds = state.autoActivatingModelIds - modelId,
                            successMessage = successMessage
                        )
                    }
                    if (settingsRepository.autoNavigateChat.first()) {
                        _navigationEvents.emit(NavigationEvent.NavigateToChat)
                    }
                }
                .onFailure { error ->
                    val errorDetail = error.message?.takeIf { it.isNotBlank() }
                    _uiState.update { state ->
                        state.copy(
                            autoActivatingModelIds = state.autoActivatingModelIds - modelId,
                            errorMessage = errorDetail?.let { "$failureMessage ($it)" } ?: failureMessage
                        )
                    }
                }
        }
    }

    private suspend fun resolveActivationTargetId(requestedModelId: String): String {
        // We poll for the model to be inserted by the download worker because it may take a few
        // milliseconds for room to persist the transaction.
        repeat(10) { attempt ->
            if (modelRepository.getModelById(requestedModelId) != null) {
                return requestedModelId
            }

            val repoIdCandidate = _uiState.value.models
                .firstOrNull { it.id == requestedModelId }
                ?.repoId
                ?: _uiState.value.onlineDetailModel
                    ?.takeIf { it.id == requestedModelId }
                    ?.repoId

            if (!repoIdCandidate.isNullOrBlank()) {
                val byRepoId = modelRepository.getModelById(repoIdCandidate)
                if (byRepoId != null) {
                    return byRepoId.id
                }
            }
            if (attempt < 9) {
                kotlinx.coroutines.delay(200L) // Wait 200ms before checking again
            }
        }

        // If not found after polling, fallback to most recent model
        return modelRepository.getMostRecentModel()?.id ?: requestedModelId
    }

    private fun resolveUiActiveModelId(
        activeModelId: String?,
        activeModelFileName: String?,
        models: List<ModelCatalogItem>
    ): String? {
        if (models.isEmpty()) return null

        val normalizedActiveId = activeModelId?.trim().orEmpty()
        if (normalizedActiveId.isNotBlank()) {
            models.firstOrNull { it.id == normalizedActiveId }?.let { return it.id }
            models.firstOrNull { it.repoId == normalizedActiveId }?.let { return it.id }
        }

        val normalizedFileName = activeModelFileName?.trim().orEmpty()
        if (normalizedFileName.isNotBlank()) {
            models.firstOrNull { model ->
                model.ggufFileName.equals(normalizedFileName, ignoreCase = true) ||
                    model.selectedVariantFilename?.equals(normalizedFileName, ignoreCase = true) == true ||
                    model.variants.any { variant ->
                        variant.filename.equals(normalizedFileName, ignoreCase = true)
                    }
            }?.let { return it.id }
        }

        return null
    }

    private fun applyFilters(
        models: List<ModelCatalogItem>,
        query: String,
        category: ModelCategory,
        selectedAuthor: String,
        downloadedIds: Set<String>
    ): List<ModelCatalogItem> {
        val loweredQuery = query.trim().lowercase()
        val authorFilter = selectedAuthor.trim().lowercase()
        return models
            .asSequence()
            .filter { category == ModelCategory.ALL || it.category == category }
            .filter { model ->
                authorFilter == "all" || authorFilter.isBlank() || model.author.lowercase() == authorFilter
            }
            .filter { model ->
                if (loweredQuery.isBlank()) return@filter true
                model.name.lowercase().contains(loweredQuery) ||
                    model.author.lowercase().contains(loweredQuery) ||
                    model.description.lowercase().contains(loweredQuery) ||
                    model.tags.any { tag -> tag.lowercase().contains(loweredQuery) } ||
                    model.repoId.lowercase().contains(loweredQuery)
            }
            .map { model ->
                if (downloadedIds.contains(model.id)) model.copy(isDownloaded = true) else model
            }
            .toList()
    }

    private fun applySelectedVariant(
        model: ModelCatalogItem,
        selectedFileName: String?
    ): ModelCatalogItem {
        val selectedVariant = model.variants.firstOrNull { it.filename == selectedFileName }
            ?: model.variants.firstOrNull { it.filename == model.selectedVariantFilename }
            ?: model.variants.maxByOrNull { it.rank }
            ?: return model

        return model.copy(
            ggufFileName = selectedVariant.filename,
            selectedVariantFilename = selectedVariant.filename,
            quantization = selectedVariant.quantization,
            sizeGb = selectedVariant.sizeBytes / (1024.0 * 1024.0 * 1024.0),
            downloadUrl = "https://huggingface.co/${model.repoId}/resolve/main/${selectedVariant.filename}",
            isCompatible = selectedVariant.compatible,
            compatibilityReason = selectedVariant.compatReason,
            compatibilityState = if (selectedVariant.compatible) {
                ModelCompatibilityState.COMPATIBLE
            } else {
                ModelCompatibilityState.NOT_COMPATIBLE
            },
            compatibilityFixTips = selectedVariant.compatFixTips,
            memoryTier = selectedVariant.memoryTier
        )
    }

    private fun isDownloadStillRequested(modelId: String): Boolean {
        val state = _uiState.value
        return state.pendingDownloadModelIds.contains(modelId) || state.downloadingModels.containsKey(modelId)
    }

    sealed class NavigationEvent {
        data object NavigateToChat : NavigationEvent()
    }

}
