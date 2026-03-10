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
    val defaultTopP: Float = SettingsRepository.DEFAULT_TOP_P,
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
    val flashAttention: Boolean = SettingsRepository.DEFAULT_FLASH_ATTENTION,
    val useMlock: Boolean = SettingsRepository.DEFAULT_USE_MLOCK,
    val useMmap: Boolean = SettingsRepository.DEFAULT_USE_MMAP,
    val keyCacheType: String = SettingsRepository.DEFAULT_KEY_CACHE_TYPE,
    val valueCacheType: String = SettingsRepository.DEFAULT_VALUE_CACHE_TYPE,
    // PocketPal Completion Params — full parity
    val minP: Float = SettingsRepository.DEFAULT_MIN_P,
    val seed: Int = SettingsRepository.DEFAULT_SEED,
    val xtcThreshold: Float = SettingsRepository.DEFAULT_XTC_THRESHOLD,
    val xtcProbability: Float = SettingsRepository.DEFAULT_XTC_PROBABILITY,
    val typicalP: Float = SettingsRepository.DEFAULT_TYPICAL_P,
    val penaltyLastN: Int = SettingsRepository.DEFAULT_PENALTY_LAST_N,
    val penaltyRepeat: Float = SettingsRepository.DEFAULT_PENALTY_REPEAT,
    val penaltyFreq: Float = SettingsRepository.DEFAULT_PENALTY_FREQ,
    val penaltyPresent: Float = SettingsRepository.DEFAULT_PENALTY_PRESENT,
    val mirostat: Int = SettingsRepository.DEFAULT_MIROSTAT,
    val mirostatTau: Float = SettingsRepository.DEFAULT_MIROSTAT_TAU,
    val mirostatEta: Float = SettingsRepository.DEFAULT_MIROSTAT_ETA,
    val jinja: Boolean = SettingsRepository.DEFAULT_JINJA,
    val includeThinkingInContext: Boolean = SettingsRepository.DEFAULT_INCLUDE_THINKING_IN_CONTEXT
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

    // Kotlin combine() max 16 flows — isliye 2 combine chains use karo + merge
    private val _settingsPart1 = combine(
        repository.temperature,       // 0
        repository.topP,               // 1
        repository.maxTokens,          // 2
        repository.contextSize,        // 3
        repository.memoryMapping,      // 4
        repository.autoOffload,        // 5
        repository.language,           // 6
        repository.darkMode,           // 7
        repository.autoNavigateChat,   // 8
        repository.onboardingCompleted,// 9
        repository.showAdvancedSettings, // 10
        repository.repeatPenalty,      // 11
        repository.threadCount,        // 12
        repository.topK,               // 13
        repository.permissionsRequested, // 14
        repository.compatibilityMode   // 15
    ) { a -> a }

    private val _settingsPart2 = combine(
        repository.allowForceLoad,     // 0
        repository.inferenceMode,      // 1
        repository.remoteFallbackEnabled, // 2
        repository.remoteProvider,     // 3
        repository.gpuLayers,          // 4
        repository.fontScale,          // 5
        repository.themeColor,         // 6
        repository.fontFamily,         // 7
        repository.biometricLock,      // 8
        repository.autoDeleteDays,     // 9
        repository.batchSize,          // 10
        repository.physicalBatchSize,  // 11
        repository.flashAttention,     // 12
        repository.useMlock,           // 13
        repository.useMmap,            // 14
        repository.keyCacheType        // 15
    ) { a -> a }

    private val _settingsPart3 = combine(
        repository.valueCacheType,     // 0
        repository.minP,               // 1
        repository.seed,               // 2
        repository.xtcThreshold,       // 3
        repository.xtcProbability,     // 4
        repository.typicalP,           // 5
        repository.penaltyLastN,       // 6
        repository.penaltyRepeat,      // 7
        repository.penaltyFreq,        // 8
        repository.penaltyPresent,     // 9
        repository.mirostat,           // 10
        repository.mirostatTau,        // 11
        repository.mirostatEta,        // 12
        repository.jinja,              // 13
        repository.includeThinkingInContext // 14
    ) { a -> a }

    val settings: StateFlow<SettingsState> = combine(
        _settingsPart1,
        _settingsPart2,
        _settingsPart3
    ) { p1, p2, p3 ->
        SettingsState(
            defaultTemperature = p1[0] as Float,
            defaultTopP = p1[1] as Float,
            defaultMaxTokens = p1[2] as Int,
            contextSize = p1[3] as Int,
            memoryMapping = p1[4] as String,
            autoOffload = p1[5] as Boolean,
            language = p1[6] as String,
            darkMode = p1[7] as Boolean,
            autoNavigateChat = p1[8] as Boolean,
            onboardingCompleted = p1[9] as Boolean,
            showAdvancedSettings = p1[10] as Boolean,
            repeatPenalty = p1[11] as Float,
            threadCount = p1[12] as Int,
            topK = p1[13] as Int,
            permissionsRequested = p1[14] as Boolean,
            compatibilityMode = p1[15] as CompatibilityMode,
            allowForceLoad = p2[0] as Boolean,
            inferenceMode = p2[1] as InferenceMode,
            remoteFallbackEnabled = p2[2] as Boolean,
            remoteProvider = p2[3] as RemoteProvider,
            gpuLayers = p2[4] as Int,
            fontScale = p2[5] as Float,
            themeColor = p2[6] as String,
            fontFamily = p2[7] as String,
            biometricLock = p2[8] as Boolean,
            autoDeleteDays = p2[9] as Int,
            batchSize = p2[10] as Int,
            physicalBatchSize = p2[11] as Int,
            flashAttention = p2[12] as Boolean,
            useMlock = p2[13] as Boolean,
            useMmap = p2[14] as Boolean,
            keyCacheType = p2[15] as String,
            valueCacheType = p3[0] as String,
            minP = p3[1] as Float,
            seed = p3[2] as Int,
            xtcThreshold = p3[3] as Float,
            xtcProbability = p3[4] as Float,
            typicalP = p3[5] as Float,
            penaltyLastN = p3[6] as Int,
            penaltyRepeat = p3[7] as Float,
            penaltyFreq = p3[8] as Float,
            penaltyPresent = p3[9] as Float,
            mirostat = p3[10] as Int,
            mirostatTau = p3[11] as Float,
            mirostatEta = p3[12] as Float,
            jinja = p3[13] as Boolean,
            includeThinkingInContext = p3[14] as Boolean
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

    private fun JSONObject.optNonBlankString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key, "").trim()
        return value.takeIf { it.isNotEmpty() }
    }

    private fun applyMaximumSpeedCaps() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val onboarded = repository.onboardingCompleted.first()
            if (onboarded) return@launch

            val profile = deviceProfileManager.currentProfile()
            val targetContext = 2048 // Fixed: always 2048, not RAM-based
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
            // Context size: always use 2480 as default, never RAM-based override
            val targetContextDefault = 2048
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
                        systemPrompt = convJson.optNonBlankString("systemPrompt"),
                        summary = convJson.optNonBlankString("summary"),
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
                            imageUri = msgJson.optNonBlankString("imageUri"),
                            reasoningContent = msgJson.optNonBlankString("reasoningContent"),
                            inferenceSource = msgJson.optNonBlankString("inferenceSource"),
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

    fun updateFlashAttention(mode: Boolean) = viewModelScope.launch {
        repository.updateFlashAttention(mode)
        markRuntimeSettingsDirty()
    }

    fun updateUseMlock(enabled: Boolean) = viewModelScope.launch {
        repository.updateUseMlock(enabled)
        markRuntimeSettingsDirty()
    }

    fun updateUseMmap(enabled: Boolean) = viewModelScope.launch {
        repository.updateUseMmap(enabled)
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

    // PocketPal features
    fun updateMinP(value: Float) = viewModelScope.launch {
        repository.updateMinP(value)
    }

    fun updateSeed(value: Int) = viewModelScope.launch {
        repository.updateSeed(value)
    }

    // PocketPal full parity — new completion params
    fun updateXtcThreshold(value: Float) = viewModelScope.launch { repository.updateXtcThreshold(value) }
    fun updateXtcProbability(value: Float) = viewModelScope.launch { repository.updateXtcProbability(value) }
    fun updateTypicalP(value: Float) = viewModelScope.launch { repository.updateTypicalP(value) }
    fun updatePenaltyLastN(value: Int) = viewModelScope.launch { repository.updatePenaltyLastN(value) }
    fun updatePenaltyRepeat(value: Float) = viewModelScope.launch { repository.updatePenaltyRepeat(value) }
    fun updatePenaltyFreq(value: Float) = viewModelScope.launch { repository.updatePenaltyFreq(value) }
    fun updatePenaltyPresent(value: Float) = viewModelScope.launch { repository.updatePenaltyPresent(value) }
    fun updateMirostat(value: Int) = viewModelScope.launch { repository.updateMirostat(value) }
    fun updateMirostatTau(value: Float) = viewModelScope.launch { repository.updateMirostatTau(value) }
    fun updateMirostatEta(value: Float) = viewModelScope.launch { repository.updateMirostatEta(value) }
    fun updateJinja(value: Boolean) = viewModelScope.launch { repository.updateJinja(value) }
    fun updateIncludeThinkingInContext(value: Boolean) = viewModelScope.launch { repository.updateIncludeThinkingInContext(value) }

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
