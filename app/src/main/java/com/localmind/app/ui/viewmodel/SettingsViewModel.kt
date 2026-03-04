package com.localmind.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmind.app.core.performance.DeviceProfileManager
import com.localmind.app.core.performance.SafeCacheManager
import com.localmind.app.data.repository.ModelRepository
import com.localmind.app.data.repository.SettingsRepository
import com.localmind.app.domain.model.CompatibilityMode
import com.localmind.app.domain.model.InferenceMode
import com.localmind.app.domain.model.RemoteProvider
import com.localmind.app.domain.model.VisionMode
import com.localmind.app.llm.ActivationOptions
import com.localmind.app.llm.ActivationSource
import com.localmind.app.llm.ModelLifecycleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min
import javax.inject.Inject
import com.localmind.app.core.utils.UiState
import com.localmind.app.data.local.entity.ConversationEntity
import com.localmind.app.data.local.entity.MessageEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.io.InputStream
import java.io.OutputStream

// ImportExportStatus removed in favor of core.utils.UiState

data class SettingsState(
    val theme: String = "Dark",
    val storageUsed: Long = 0L,
    val cacheSize: Long = 0L,
    val defaultTemperature: Float = 0.7f,
    val defaultTopP: Float = 0.9f,
    val defaultMaxTokens: Int = SettingsRepository.DEFAULT_MAX_TOKENS,
    val contextSize: Int = SettingsRepository.DEFAULT_CONTEXT_SIZE,
    val memoryMapping: String = "Smart",
    val autoOffload: Boolean = false,
    val language: String = "English (EN)",
    val darkMode: Boolean = true,
    val autoNavigateChat: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val showAdvancedSettings: Boolean = false,
    val repeatPenalty: Float = 1.1f,
    val threadCount: Int = 4,
    val gpuLayers: Int = SettingsRepository.DEFAULT_GPU_LAYERS,
    val topK: Int = 40,
    val permissionsRequested: Boolean = false,
    val compatibilityMode: CompatibilityMode = CompatibilityMode.SAFE,
    val allowForceLoad: Boolean = true,
    val inferenceMode: InferenceMode = InferenceMode.HYBRID,
    val visionMode: VisionMode = VisionMode.REMOTE_FALLBACK,
    val remoteFallbackEnabled: Boolean = true,
    val remoteProvider: RemoteProvider = RemoteProvider.HUGGING_FACE,
    val fontScale: Float = 1.0f,
    val themeColor: String = "Neon",
    val fontFamily: String = "Default",
    val biometricLock: Boolean = false,
    val autoDeleteDays: Int = 0,
    val importExportStatus: UiState<String> = UiState.Idle,
    val batchSize: Int = SettingsRepository.DEFAULT_BATCH_SIZE,
    val physicalBatchSize: Int = SettingsRepository.DEFAULT_PHYSICAL_BATCH_SIZE,
    val flashAttention: String = SettingsRepository.DEFAULT_FLASH_ATTENTION,
    val keyCacheType: String = SettingsRepository.DEFAULT_KEY_CACHE_TYPE,
    val valueCacheType: String = SettingsRepository.DEFAULT_VALUE_CACHE_TYPE
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val chatRepository: com.localmind.app.data.repository.ChatRepository,
    private val hardwareMonitor: com.localmind.app.core.utils.HardwareMonitor,
    private val safeCacheManager: SafeCacheManager,
    private val deviceProfileManager: DeviceProfileManager,
    private val modelLifecycleManager: ModelLifecycleManager,
    private val modelRepository: ModelRepository
) : ViewModel() {

    val onboardingCompletedStartup: StateFlow<Boolean?> = repository.onboardingCompleted
        .map<Boolean, Boolean?> { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Real-time hardware stats (Hot flow)
    val hardwareStats = hardwareMonitor.getStatsFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val _importExportStatus = MutableStateFlow<UiState<String>>(UiState.Idle)
    val importExportStatus = _importExportStatus.asStateFlow()

    val settings: StateFlow<SettingsState> = combine(
        repository.temperature,
        repository.topP,
        repository.maxTokens,
        repository.contextSize,
        repository.memoryMapping,
        repository.autoOffload,
        repository.language,
        repository.darkMode,
        repository.autoNavigateChat,
        repository.onboardingCompleted,
        repository.showAdvancedSettings,
        repository.repeatPenalty,
        repository.threadCount,
        repository.topK,
        repository.permissionsRequested,
        repository.compatibilityMode,
        repository.allowForceLoad,
        repository.inferenceMode,
        repository.visionMode,
        repository.remoteFallbackEnabled,
        repository.remoteProvider,
        repository.gpuLayers,
        repository.fontScale,
        repository.themeColor,
        repository.fontFamily,
        repository.biometricLock,
        repository.autoDeleteDays,
        repository.batchSize,
        repository.physicalBatchSize,
        repository.flashAttention,
        repository.keyCacheType,
        repository.valueCacheType
    ) { args ->
        SettingsState(
            defaultTemperature = args[0] as Float,
            defaultTopP = args[1] as Float,
            defaultMaxTokens = args[2] as Int,
            contextSize = args[3] as Int,
            memoryMapping = args[4] as String,
            autoOffload = args[5] as Boolean,
            language = args[6] as String,
            darkMode = args[7] as Boolean,
            autoNavigateChat = args[8] as Boolean,
            onboardingCompleted = args[9] as Boolean,
            showAdvancedSettings = args[10] as Boolean,
            repeatPenalty = args[11] as Float,
            threadCount = args[12] as Int,
            topK = args[13] as Int,
            permissionsRequested = args[14] as Boolean,
            compatibilityMode = args[15] as CompatibilityMode,
            allowForceLoad = args[16] as Boolean,
            inferenceMode = args[17] as InferenceMode,
            visionMode = args[18] as VisionMode,
            remoteFallbackEnabled = args[19] as Boolean,
            remoteProvider = args[20] as RemoteProvider,
            gpuLayers = args[21] as Int,
            fontScale = args[22] as Float,
            themeColor = args[23] as String,
            fontFamily = args[24] as String,
            biometricLock = args[25] as Boolean,
            autoDeleteDays = args[26] as Int,
            batchSize = args[27] as Int,
            physicalBatchSize = args[28] as Int,
            flashAttention = args[29] as String,
            keyCacheType = args[30] as String,
            valueCacheType = args[31] as String
        )
    }.combine(_importExportStatus) { state, status ->
        state.copy(importExportStatus = status)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsState()
    )

    private val _isClearingCache = MutableStateFlow(false)
    val isClearingCache = _isClearingCache.asStateFlow()

    // _importExportStatus declared above (before `settings` combine chain)

    private val _isExporting = MutableStateFlow(false)
    val isExporting = _isExporting.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting = _isImporting.asStateFlow()
    private var runtimeSettingsDirty = false
    private var applyRuntimeSettingsJob: Job? = null

    init {
        // FIX: Only call ONE initialization function. applyMaximumSpeedCaps() and
        // applyDeviceSafeDefaults() had overlapping logic writing the same DataStore
        // keys simultaneously from two separate coroutines — potential DataStore write
        // conflict and wasted double-disk-IO. Merged into a single call.
        applyDeviceSafeDefaults()
        observeAutoDelete()
    }

    private fun observeAutoDelete() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.autoDeleteDays.collectLatest { days ->
                if (days > 0) {
                    chatRepository.deleteOldChats(days)
                }
            }
        }
    }

    private fun applyMaximumSpeedCaps() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val onboarded = repository.onboardingCompleted.first()
            if (onboarded) return@launch

            val profile = deviceProfileManager.currentProfile()
            val targetContext = min(
                profile.recommendedContextSize,
                SettingsRepository.DEFAULT_CONTEXT_SIZE
            )
            val targetThreads = profile.recommendedThreadCount
            val targetMaxTokens = min(
                profile.recommendedMaxTokens,
                SettingsRepository.DEFAULT_MAX_TOKENS
            )
            val targetTopK = when {
                profile.totalRamGb <= 4 -> 28
                profile.totalRamGb <= 6 -> 32
                profile.totalRamGb <= 8 -> 36
                else -> 40
            }

            val currentContext = repository.contextSize.first()
            val currentThreads = repository.threadCount.first()
            val currentMaxTokens = repository.maxTokens.first()
            val currentTopK = repository.topK.first()

            if (currentContext != targetContext) {
                repository.updateContextSize(targetContext)
            }
            if (currentThreads != targetThreads) {
                repository.updateThreadCount(targetThreads)
            }
            if (currentMaxTokens != targetMaxTokens) {
                repository.updateMaxTokens(targetMaxTokens)
            }
            if (currentTopK != targetTopK) {
                repository.updateTopK(targetTopK)
            }
        }
    }

    private fun applyDeviceSafeDefaults() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val onboarded = repository.onboardingCompleted.first()
            if (onboarded) return@launch // Don't override user settings if already onboarded

            val profile = deviceProfileManager.currentProfile()
            val currentContext = repository.contextSize.first()
            val currentThreads = repository.threadCount.first()
            val currentMaxTokens = repository.maxTokens.first()
            val currentTopK = repository.topK.first()
            val targetContextDefault = min(
                profile.recommendedContextSize,
                SettingsRepository.DEFAULT_CONTEXT_SIZE
            )
            val targetMaxTokensDefault = min(
                profile.recommendedMaxTokens,
                SettingsRepository.DEFAULT_MAX_TOKENS
            )

            if (currentContext != targetContextDefault) {
                repository.updateContextSize(targetContextDefault)
            }
            val balancedThreads = (profile.cpuCores - 1).coerceAtLeast(1)
            val turboThreads = min(balancedThreads, profile.recommendedThreadCount).coerceAtLeast(1)
            if (currentThreads != turboThreads) {
                repository.updateThreadCount(turboThreads)
            }
            if (currentMaxTokens != targetMaxTokensDefault) {
                repository.updateMaxTokens(targetMaxTokensDefault)
            }
            val turboTopK = when {
                profile.totalRamGb <= 4 -> 32
                profile.totalRamGb <= 6 -> 36
                else -> 40
            }
            if (currentTopK != turboTopK) {
                repository.updateTopK(turboTopK)
            }
        }
    }

    fun clearCache(@Suppress("UNUSED_PARAMETER") context: android.content.Context) {
        if (_isClearingCache.value) return
        viewModelScope.launch {
            _isClearingCache.value = true
            try {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // Stop generation and unload model with separate timeouts
                    // Use runCatching so one failure doesn't block the rest of cache clearing
                    runCatching {
                        withTimeoutOrNull(3_000L) {
                            chatRepository.stopGeneration()
                        }
                    }

                    runCatching {
                        withTimeoutOrNull(10_000L) {
                            chatRepository.unloadModel()
                        }
                    }

                    // Perform actual cleanup
                    safeCacheManager.clearAppCacheSafely()
                }

                // Refresh hardware stats on IO as well
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    hardwareMonitor.refresh()
                }
                kotlinx.coroutines.delay(500)
            } catch (e: Throwable) {
                android.util.Log.e("SettingsViewModel", "Error clearing cache", e)
            } finally {
                // Ensure the flag is reset even on failure or cancellation
                withContext(kotlinx.coroutines.NonCancellable) {
                    _isClearingCache.value = false
                }
            }
        }
    }

    fun exportChatsToUri(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val conversations = chatRepository.getAllConversationsSync()
                val stringBuilder = StringBuilder()

                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                stringBuilder.append("Local Mind Chat Export\n")
                stringBuilder.append("Date: ${dateFormat.format(java.util.Date())}\n")
                stringBuilder.append("==========================================\n\n")

                conversations.forEach { conv ->
                    stringBuilder.append("Chat: ${conv.title}\n")
                    stringBuilder.append("Model: ${conv.modelName}\n")
                    stringBuilder.append("ID: ${conv.id}\n")
                    stringBuilder.append("------------------------------------------\n")

                    val messages = chatRepository.getMessagesByConversationSync(conv.id)
                    messages.forEach { msg ->
                        val role = if (msg.role.name.equals("user", ignoreCase = true)) "User" else "AI"
                        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(msg.timestamp))
                        stringBuilder.append("[$time] $role:\n${msg.content}\n\n")
                    }
                    stringBuilder.append("==========================================\n\n")
                }

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(stringBuilder.toString().toByteArray())
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    fun exportChatsToJsonUri(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isExporting.value = true
            _importExportStatus.value = UiState.Loading
            try {
                val conversations = chatRepository.getAllConversationsSync()
                val jsonArray = JSONArray()

                conversations.forEach { conv ->
                    val jsonConv = JSONObject()
                    jsonConv.put("id", conv.id)
                    jsonConv.put("title", conv.title)
                    jsonConv.put("modelId", conv.modelId)
                    jsonConv.put("modelName", conv.modelName)
                    jsonConv.put("createdAt", conv.createdAt)
                    jsonConv.put("updatedAt", conv.updatedAt)
                    jsonConv.put("systemPrompt", conv.systemPrompt ?: "")
                    jsonConv.put("summary", conv.summary ?: "")
                    jsonConv.put("isHidden", conv.isHidden)

                    val messagesArray = JSONArray()
                    val messages = chatRepository.getMessagesByConversationSync(conv.id)
                    messages.forEach { msg ->
                        val jsonMsg = JSONObject()
                        jsonMsg.put("id", msg.id)
                        jsonMsg.put("role", msg.role.name)
                        jsonMsg.put("content", msg.content)
                        jsonMsg.put("timestamp", msg.timestamp)
                        jsonMsg.put("tokenCount", msg.tokenCount ?: 0)
                        jsonMsg.put("imageUri", msg.imageUri ?: "")
                        jsonMsg.put("reasoningContent", msg.reasoningContent ?: "")
                        jsonMsg.put("inferenceSource", msg.inferenceSource ?: "")
                        jsonMsg.put("ttftMs", msg.ttftMs ?: 0L)
                        jsonMsg.put("generationMs", msg.generationMs ?: 0L)
                        jsonMsg.put("tokensPerSecond", msg.tokensPerSecond ?: 0f)
                        messagesArray.put(jsonMsg)
                    }
                    jsonConv.put("messages", messagesArray)
                    jsonArray.put(jsonConv)
                }

                val root = JSONObject()
                root.put("app", "LocalMind")
                root.put("version", "1.1")
                root.put("exportDate", System.currentTimeMillis())
                root.put("conversations", jsonArray)

                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(root.toString(2).toByteArray())
                }
                _importExportStatus.value = UiState.Success("Chats exported successfully")
            } catch (e: Exception) {
                _importExportStatus.value = UiState.Error("Export failed: ${e.message}")
                e.printStackTrace()
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun importChatsFromJsonUri(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isImporting.value = true
            _importExportStatus.value = UiState.Loading
            try {
                val jsonStr = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@launch
                val root = JSONObject(jsonStr)
                val conversationsJson = root.getJSONArray("conversations")

                val conversationsToInsert = mutableListOf<ConversationEntity>()
                val messagesToInsert = mutableListOf<MessageEntity>()

                for (i in 0 until conversationsJson.length()) {
                    val convJson = conversationsJson.getJSONObject(i)
                    val convId = convJson.getString("id")

                    val convEntity = ConversationEntity(
                        id = convId,
                        title = convJson.getString("title"),
                        modelId = convJson.optString("modelId", ""),
                        modelName = convJson.optString("modelName", "Unknown"),
                        createdAt = convJson.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = convJson.optLong("updatedAt", System.currentTimeMillis()),
                        messageCount = 0, // Will be calculated/updated
                        systemPrompt = convJson.optString("systemPrompt", null).takeIf { it?.isNotEmpty() == true },
                        summary = convJson.optString("summary", null).takeIf { it?.isNotEmpty() == true },
                        isHidden = convJson.optBoolean("isHidden", false)
                    )
                    conversationsToInsert.add(convEntity)

                    val messagesJson = convJson.getJSONArray("messages")
                    var actualMessageCount = 0
                    for (j in 0 until messagesJson.length()) {
                        val msgJson = messagesJson.getJSONObject(j)
                        val roleStr = msgJson.getString("role")

                        val msgEntity = MessageEntity(
                            id = msgJson.optString("id", UUID.randomUUID().toString()),
                            conversationId = convId,
                            role = roleStr.lowercase(),
                            content = msgJson.getString("content"),
                            timestamp = msgJson.optLong("timestamp", System.currentTimeMillis()),
                            tokenCount = msgJson.optInt("tokenCount", 0).takeIf { it > 0 },
                            imageUri = msgJson.optString("imageUri", null).takeIf { it?.isNotEmpty() == true },
                            reasoningContent = msgJson.optString("reasoningContent", null).takeIf { it?.isNotEmpty() == true },
                            inferenceSource = msgJson.optString("inferenceSource", null).takeIf { it?.isNotEmpty() == true },
                            ttftMs = msgJson.optLong("ttftMs", 0).takeIf { it > 0 },
                            generationMs = msgJson.optLong("generationMs", 0).takeIf { it > 0 },
                            tokensPerSecond = msgJson.optDouble("tokensPerSecond", 0.0).toFloat().takeIf { it > 0 }
                        )
                        messagesToInsert.add(msgEntity)
                        actualMessageCount++
                    }

                    // Update message count in the entity
                    conversationsToInsert[i] = convEntity.copy(messageCount = actualMessageCount)
                }

                chatRepository.importChatBackup(conversationsToInsert, messagesToInsert)
                _importExportStatus.value = UiState.Success("${conversationsToInsert.size} chats imported successfully")
            } catch (e: Exception) {
                _importExportStatus.value = UiState.Error("Import failed: ${e.message}")
                e.printStackTrace()
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun resetImportExportStatus() {
        _importExportStatus.value = UiState.Idle
    }

    fun updateTheme(value: String) = viewModelScope.launch { repository.updateThemeColor(value) }

    fun updateTemperature(value: Float) = viewModelScope.launch {
        repository.updateTemperature(value)
    }

    fun updateTopP(value: Float) = viewModelScope.launch {
        repository.updateTopP(value)
    }

    fun updateMaxTokens(value: Int) = viewModelScope.launch {
        repository.updateMaxTokens(value)
    }

    fun updateContextSize(value: Int) = viewModelScope.launch {
        if (settings.value.contextSize == value) return@launch

        repository.updateContextSize(value)
        markRuntimeSettingsDirty()
    }

    fun updateMemoryMapping(value: String) = viewModelScope.launch {
        repository.updateMemoryMapping(value)
        markRuntimeSettingsDirty()
    }

    fun updateAutoOffload(value: Boolean) = viewModelScope.launch {
        repository.updateAutoOffload(value)
    }

    fun updateLanguage(value: String) = viewModelScope.launch {
        repository.updateLanguage(value)
    }

    fun updateDarkMode(value: Boolean) = viewModelScope.launch {
        repository.updateDarkMode(value)
    }

    fun updateAutoNavigateChat(value: Boolean) = viewModelScope.launch {
        repository.updateAutoNavigateChat(value)
    }

    fun setOnboardingCompleted(value: Boolean) = viewModelScope.launch {
        repository.setOnboardingCompleted(value)
    }

    fun setShowAdvancedSettings(value: Boolean) = viewModelScope.launch {
        repository.setShowAdvancedSettings(value)
    }

    fun updateRepeatPenalty(value: Float) = viewModelScope.launch {
        repository.updateRepeatPenalty(value)
    }

    fun updateThreadCount(value: Int) = viewModelScope.launch {
        repository.updateThreadCount(value)
        markRuntimeSettingsDirty()
    }

    fun updateGpuLayers(value: Int) = viewModelScope.launch {
        repository.updateGpuLayers(value)
        if (value > 0) {
            repository.setSkipGpuAfterFail(false)
        }
        markRuntimeSettingsDirty()
    }

    fun updateTopK(value: Int) = viewModelScope.launch {
        repository.updateTopK(value)
    }

    fun setPermissionsRequested(value: Boolean) = viewModelScope.launch {
        repository.setPermissionsRequested(value)
    }

    fun setCompatibilityMode(mode: CompatibilityMode) = viewModelScope.launch {
        repository.setCompatibilityMode(mode)
    }

    fun setAllowForceLoad(allow: Boolean) = viewModelScope.launch {
        repository.setAllowForceLoad(allow)
    }

    fun setInferenceMode(mode: InferenceMode) = viewModelScope.launch {
        repository.setInferenceMode(mode)
    }

    fun setVisionMode(mode: VisionMode) = viewModelScope.launch {
        repository.setVisionMode(mode)
    }

    fun setRemoteFallbackEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setRemoteFallbackEnabled(enabled)
    }

    fun setRemoteProvider(provider: RemoteProvider) = viewModelScope.launch {
        repository.setRemoteProvider(provider)
    }

    fun updateFontScale(value: Float) = viewModelScope.launch {
        repository.updateFontScale(value)
    }

    fun updateThemeColor(value: String) = viewModelScope.launch {
        repository.updateThemeColor(value)
    }

    fun updateFontFamily(value: String) = viewModelScope.launch {
        repository.updateFontFamily(value)
    }

    fun updateBiometricLock(value: Boolean) = viewModelScope.launch {
        repository.updateBiometricLock(value)
    }

    fun updateAutoDeleteDays(value: Int) = viewModelScope.launch {
        repository.updateAutoDeleteDays(value)
    }

    fun updateBatchSize(value: Int) = viewModelScope.launch {
        repository.updateBatchSize(value)
        markRuntimeSettingsDirty()
    }

    fun updatePhysicalBatchSize(value: Int) = viewModelScope.launch {
        repository.updatePhysicalBatchSize(value)
        markRuntimeSettingsDirty()
    }

    fun updateFlashAttention(mode: String) = viewModelScope.launch {
        repository.updateFlashAttention(mode)
        markRuntimeSettingsDirty()
    }

    fun updateKeyCacheType(type: String) = viewModelScope.launch {
        repository.updateKeyCacheType(type)
        markRuntimeSettingsDirty()
    }

    fun updateValueCacheType(type: String) = viewModelScope.launch {
        repository.updateValueCacheType(type)
        markRuntimeSettingsDirty()
    }

    fun applyPendingRuntimeSettingChanges() {
        if (!runtimeSettingsDirty) return
        if (applyRuntimeSettingsJob?.isActive == true) return

        applyRuntimeSettingsJob = viewModelScope.launch {
            if (!runtimeSettingsDirty) return@launch
            runtimeSettingsDirty = false
            refreshLoadedModelForRuntimeSettingChange()
        }
    }

    private fun markRuntimeSettingsDirty() {
        runtimeSettingsDirty = true
    }

    private suspend fun refreshLoadedModelForRuntimeSettingChange() {
        if (!chatRepository.isModelLoaded()) return

        // Find the active model before unloading so we can reload it with new settings
        val activeModel = modelRepository.getActiveModel()

        runCatching {
            withTimeoutOrNull(3_000L) {
                chatRepository.stopGeneration()
            }
        }

        runCatching {
            withTimeoutOrNull(10_000L) {
                chatRepository.unloadModel()
            }
        }

        // Reload the model with updated settings (context size, thread count, etc.) in realtime
        if (activeModel != null) {
            runCatching {
                modelLifecycleManager.activateModelSafely(
                    modelId = activeModel.id,
                    options = ActivationOptions(
                        forceLoad = true,
                        source = ActivationSource.AUTO_RESTORE
                    )
                )
            }
        }
    }
}

