package com.localmind.app.ui.viewmodel

import com.localmind.app.domain.usecase.*

import android.content.Context
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmind.app.core.Constants
import com.localmind.app.core.engine.PerfMetrics
import com.localmind.app.core.performance.DeviceProfileManager
import com.localmind.app.core.rollout.FeatureRolloutConfig
import com.localmind.app.core.utils.ConversationTitleGenerator

import com.localmind.app.data.repository.*
import com.localmind.app.domain.model.*
import com.localmind.app.llm.*
import com.localmind.app.llm.prompt.PromptBuildRequest
import com.localmind.app.llm.prompt.PromptBuilderService
import com.localmind.app.ui.utils.ImagePickerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository,
    private val modelLifecycleManager: ModelLifecycleManager,
    private val deviceProfileManager: DeviceProfileManager,
    private val promptBuilderService: PromptBuilderService,
    private val inferenceErrorMapper: InferenceErrorMapper,
    private val personaRepository: PersonaRepository,
    private val featureRolloutConfig: FeatureRolloutConfig,
    private val promptSanitizationUseCase: com.localmind.app.domain.usecase.PromptSanitizationUseCase,
    private val getChatMessagesUseCase: com.localmind.app.domain.usecase.GetChatMessagesUseCase,
    private val getDownloadedModelsUseCase: GetDownloadedModelsUseCase,
    private val getAvailablePersonasUseCase: com.localmind.app.domain.usecase.GetAvailablePersonasUseCase,
    private val autoRenameConversationUseCase: com.localmind.app.domain.usecase.AutoRenameConversationUseCase,
    private val renameConversationUseCase: com.localmind.app.domain.usecase.RenameConversationUseCase,
    private val deleteMessageUseCase: com.localmind.app.domain.usecase.DeleteMessageUseCase,
    private val summarizeConversationUseCase: com.localmind.app.domain.usecase.SummarizeConversationUseCase,
    private val promptTemplateDao: com.localmind.app.data.local.dao.PromptTemplateDao,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    companion object {
        // POCKETPAL FIX: Minimal fallback system prompt — model ke built-in template
        // se system prompt auto-detect hota hai (TemplateResolver via PromptBuilderService).
        // Ye sirf tab use hota hai jab koi persona/model template na ho.
        // PERF: Koi default system prompt NAHI — sirf persona ka system prompt use hoga.
        // System prompt extra tokens = TTFT badh jaata hai = slow first response.
        // Agar persona ka system prompt blank ho tabhi empty string dena hai.
        private const val DEFAULT_SYSTEM_PROMPT = ""

        private const val MEDIA_ANALYSIS_UNSUPPORTED_MESSAGE =
            "I am not able to analyze image/video with this model."
    }

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    // STREAMING FIX: Streaming text ko alag StateFlow mein nikalo.
    // Pehle: poora ChatState har token pe update hota tha = pura ChatScreen recompose.
    // Ab: sirf StreamingOverlay composable re-renders, baaki UI stable rehti hai.
    // PocketPal mein bhi yahi pattern hai — modelStore.inferencingText alag observable hai.
    val streamingText: StateFlow<String?> = _state
        .map { it.streamingResponse }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val streamingReasoningText: StateFlow<String?> = _state
        .map { it.streamingReasoning }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var currentConversationId: String? = null
    private var generationJob: Job? = null
    private var conversationObserverJob: Job? = null
    private var messageObserverJob: Job? = null
    private val generationMutex = Mutex()
    private var launchSessionInitialized: Boolean = false

    // ── FIX #1: In-Memory Settings Cache ─────────────────────────────────────
    // PocketPal parity: Settings are always in-memory (MobX observables).
    // ChatViewModelSettings (in SettingsRepository.kt) se data aata hai, jo
    // DataStore ke single snapshot se build hota hai.
    // ─────────────────────────────────────────────────────────────────────────
    @Volatile private var cachedSettings: ChatViewModelSettings? = null
    private var settingsCollectorJob: Job? = null

    // Wake lock — CPU awake rakhta hai generation ke dauran (screen FLAG_KEEP_SCREEN_ON se ChatScreen handle karta hai)
    private val wakeLock: PowerManager.WakeLock by lazy {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LocalMind:CpuWakeLock"
        ).apply { setReferenceCounted(false) }
    }

    private fun acquireWakeLock() {
        if (!wakeLock.isHeld) wakeLock.acquire(10 * 60 * 1000L) // 10 min max
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    init {
        loadActiveModel()
        observeActiveModel()
        loadPersonas()
        loadDownloadedModels()
        loadPromptTemplates()
        startSettingsCollector()
    }

    // FIX #1: Settings collector — har settings change par cachedSettings update hota hai.
    // Ye PocketPal ke MobX observables ki tarah kaam karta hai — zero disk I/O at inference time.
    // Strategy: combine() max 5 streams support karta hai. Isliye hum pehle saare streams ko
    // individually collect karte hain aur flatMapLatest pattern use karte hain. Simplest
    // correct approach: har DataStore change pe fresh read karo (still only 1 read cycle, not 23).
    private fun startSettingsCollector() {
        settingsCollectorJob?.cancel()
        settingsCollectorJob = viewModelScope.launch(Dispatchers.IO) {
            // DataStore internally stores ALL prefs in a single protobuf file.
            // Subscribing to dataStore.data gives us ALL changes in one flow.
            // On each emission we build a full CachedSettings from the single snapshot.
            // This is the most reliable approach: 1 flow subscription, 1 disk read per change.
            settingsRepository.allSettingsFlow().collect { snapshot ->
                cachedSettings = snapshot
            }
        }
    }

    // FIX #1: Helper — ek baar fresh read karo agar cache abhi tak populate nahi hua.
    // Normal case mein ye kabhi call nahi hota (cache init pe bhar jaata hai).
    private suspend fun readSettingsOnceFresh(): ChatViewModelSettings {
        return settingsRepository.allSettingsFlow().first()
    }

    private fun loadPromptTemplates() {
        viewModelScope.launch {
            promptTemplateDao.getAllPromptsFlow().collect { templates ->
                _state.update { it.copy(promptTemplates = templates) }
            }
        }
    }

    private fun loadDownloadedModels() {
        viewModelScope.launch {
            getDownloadedModelsUseCase().collect { models ->
                _state.update { it.copy(downloadedModels = models) }
            }
        }
    }

    private fun loadPersonas() {
        viewModelScope.launch {
            getAvailablePersonasUseCase().collect { personas ->
                _state.update { it.copy(availablePersonas = personas) }
                if (_state.value.selectedPersona == null) {
                    val default = personaRepository.getDefaultPersona()
                    _state.update { it.copy(selectedPersona = default) }
                }
            }
        }
    }



    fun selectPersona(persona: Persona) {
        _state.update { it.copy(selectedPersona = persona) }

        currentConversationId?.let { convId ->
            viewModelScope.launch {
                chatRepository.updateConversationSystemPrompt(convId, persona.systemPrompt)
            }
        }

        // #9: Auto-switch model when persona has a preferred model
        val modelId = persona.preferredModelId
        if (!modelId.isNullOrBlank()) {
            viewModelScope.launch {
                val model = modelRepository.getModelById(modelId)
                if (model != null && model.id != _state.value.activeModel?.id) {
                    runCatching {
                        modelLifecycleManager.activateModelSafely(
                            modelId = model.id,
                            options = ActivationOptions(source = ActivationSource.USER)
                        )
                    }
                    _state.update { it.copy(activeModel = model) }
                    checkPerformance(model)
                }
            }
        }
    }

    fun switchModel(modelId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingModel = true, error = null) }
            try {
                modelLifecycleManager.activateModelSafely(modelId)
                loadActiveModel()
            } catch (e: Exception) {
                _state.update { it.copy(error = mapUserFacingError(e)) }
            } finally {
                _state.update { it.copy(isLoadingModel = false) }
            }
        }
    }

    private fun loadActiveModel() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val activeModel = modelRepository.ensureAnyActiveModel()
                if (activeModel == null) {
                    _state.update {
                        it.copy(
                            activeModel = null,
                            isLoading = false,
                            error = "No active model. Please select a model first."
                        )
                    }
                    return@launch
                }
                // STARTUP FIX: Sirf UI state update karo — model load mat karo.
                // LocalMindApplication.preloadActiveModelAtStartup() app open hote hi
                // model load kar deta hai. Yahan dobara activateModelSafely() call karna
                // redundant tha aur engineMutex block karta tha.
                // ensureModelLoaded() (ChatScreen LaunchedEffect se) handle karega agar load nahi hua.
                _state.update { it.copy(activeModel = activeModel, isLoading = false) }
                checkPerformance(activeModel)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = mapUserFacingError(e)) }
            }
        }
    }

    private fun checkPerformance(model: Model) {
        viewModelScope.launch {
            val guidance = deviceProfileManager.getCompatibilityGuidance(
                modelSizeBytes = model.sizeBytes,
                modelNameHint = model.name,
                quantizationHint = model.quantization,
                parameterCountHint = model.parameterCount
            )
            if (!guidance.compatible) {
                _state.update { it.copy(performanceWarning = guidance.reason) }
            } else {
                _state.update { it.copy(performanceWarning = null) }
            }
        }
    }

    private fun observeActiveModel() {
        viewModelScope.launch {
            modelRepository.getActiveModelFlow().collect { activeModel ->
                if (activeModel != null && activeModel.id != _state.value.activeModel?.id) {
                    _state.update { it.copy(activeModel = activeModel, error = null) }
                    checkPerformance(activeModel)
                } else if (activeModel == null && _state.value.activeModel != null) {
                    _state.update { it.copy(activeModel = null) }
                }
            }
        }
    }


    fun bootstrapConversation(conversationId: String?) {
        viewModelScope.launch {
            if (!conversationId.isNullOrBlank()) {
                launchSessionInitialized = true
                if (currentConversationId != null && currentConversationId != conversationId) {
                    stopGenerationForSessionSwitch()
                }
                loadConversation(conversationId)
                return@launch
            }

            // FIX: Reset flag jab same screen recompose ho (conversationId=null ke saath)
            // This allows "New Chat" to work after first launch
            if (launchSessionInitialized && currentConversationId != null) {
                return@launch
            }
            launchSessionInitialized = true
            stopGenerationForSessionSwitch()
            createNewConversation()
        }
    }

    fun loadConversation(conversationId: String) {
        if (
            currentConversationId == conversationId &&
            conversationObserverJob?.isActive == true &&
            messageObserverJob?.isActive == true
        ) {
            return
        }

        if (currentConversationId != null && currentConversationId != conversationId) {
            stopGenerationForSessionSwitch()
        }

        currentConversationId = conversationId
        conversationObserverJob?.cancel()
        messageObserverJob?.cancel()

        _state.update { it.copy(isLoading = true, messages = emptyList(), streamingResponse = null, error = null) }

        conversationObserverJob = viewModelScope.launch {
            chatRepository.getConversationByIdFlow(conversationId).collect { conversation ->
                if (conversation == null) {
                    currentConversationId = null
                    _state.update {
                        it.copy(
                            currentConversation = null,
                            messages = emptyList(),
                            isLoading = false,
                            error = "This chat no longer exists. Start a new chat."
                        )
                    }
                    return@collect
                }
                _state.update { it.copy(currentConversation = conversation, isLoading = false) }
            }
        }

        messageObserverJob = viewModelScope.launch {
            getChatMessagesUseCase(conversationId).collect { messages ->
                _state.update { it.copy(messages = messages) }
            }
        }
    }

    fun createNewConversation() {
        viewModelScope.launch {
            stopGenerationForSessionSwitch()
            _state.update { it.copy(isLoading = true) }
            val activeModel = modelRepository.ensureAnyActiveModel()
            if (activeModel == null) {
                _state.update { it.copy(error = "No active model. Please select a model first.", isLoading = false) }
                return@launch
            }

            val conversation = chatRepository.createConversation(
                title = "New Chat",
                modelId = activeModel.id,
                modelName = activeModel.name,
                systemPrompt = _state.value.selectedPersona?.systemPrompt
            )
            loadConversation(conversation.id)
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            deleteMessageUseCase(messageId)
        }
    }

    fun renameCurrentConversation(newTitle: String) {
        val id = currentConversationId ?: return
        viewModelScope.launch {
            renameConversationUseCase(id, newTitle)
        }
    }

    fun attachFile(uri: Uri, context: Context) {
        viewModelScope.launch {
            val type = context.contentResolver.getType(uri) ?: ""
            val isImageOrVideo = type.startsWith("image/") || type.startsWith("video/")
            val activeModel = _state.value.activeModel

            if (isImageOrVideo) {
                // Universal image support: allow attachments regardless of model vision capability
                // The model will handle support gracefully or provide a textual response.
            }

            val fileName = getFileName(uri, context) ?: "Unknown File"
            val newAttachment = Attachment(uri, fileName)
            _state.update { it.copy(attachments = it.attachments + newAttachment, error = null) }

            // Extract text if it's a document (#27)
            if (type == "application/pdf" || type == "text/plain") {
                if (activeModel != null && activeModel.supportsDocument) {
                    // Do not extract text if the model supports it natively.
                } else {
                    _state.update { it.copy(isAnalyzingDocument = true) }
                    viewModelScope.launch(Dispatchers.IO) {
                        val helper = com.localmind.app.core.utils.DocumentHelper(context)
                        val text = helper.extractTextFromUri(uri)
                        if (text != null) {
                            _state.update { it.copy(attachedDocumentText = text, isAnalyzingDocument = false) }
                        } else {
                            _state.update { it.copy(isAnalyzingDocument = false) }
                        }
                    }
                }
            }
        }
    }

    fun removeAttachment(attachment: Attachment) {
        _state.update { it.copy(attachments = it.attachments - attachment, attachedDocumentText = null) }
    }

    private fun getFileName(uri: Uri, context: Context): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name ?: uri.path?.substringAfterLast('/')
    }

    /**
     * PocketPal-style edit: message content ko input box mein bhejne ke baad
     * user ne edit kiya — purana message delete karo aur phir naya send karo.
     * Returns the content so ChatScreen can put it in inputText.
     */
    fun startEditMessage(messageId: String): String? {
        val message = _state.value.messages.find { it.id == messageId } ?: return null
        _state.update { it.copy(pendingEditMessageId = messageId) }
        return message.content
    }

    fun cancelEdit() {
        _state.update { it.copy(pendingEditMessageId = null) }
    }

    fun sendMessage(content: String, context: Context) {
        if (content.isBlank() && _state.value.attachments.isEmpty()) return

        generationJob?.cancel()
        if (chatRepository.isGenerating()) {
            chatRepository.stopGeneration()
        }

        val sendMessageStartMs = System.currentTimeMillis()
        generationJob = viewModelScope.launch {
            acquireWakeLock()
            // TTFT FIX: engineMutex release hone tak wait karo.
            // Cancel ke baad nativeJob.invokeOnCompletion async hai — mutex abhi bhi held ho sakta hai.
            // Is wait ke bina naya generate() call 30s timeout ke liye block ho jaata tha.
            chatRepository.waitForEngineReady(timeoutMs = 500L) // PERF: 2s → 500ms — zyada wait nahi karna
            try {
                generationMutex.withLock {
                    // PocketPal-style edit: pehle purana message aur uske baad ke sab messages delete karo
                    val editingMessageId = _state.value.pendingEditMessageId
                    if (editingMessageId != null) {
                        val editTarget = chatRepository.getMessageById(editingMessageId)
                        if (editTarget != null) {
                            // Delete the edited message and all messages after it (assistant replies)
                            val convId = editTarget.conversationId
                            val allMessages = chatRepository.getMessagesByConversationSync(convId)
                            val messagesAfter = allMessages.filter { it.timestamp >= editTarget.timestamp }
                            messagesAfter.forEach { deleteMessageUseCase(it.id) }
                        }
                        _state.update { it.copy(pendingEditMessageId = null) }
                    }

                    var conversationId = ensureConversationId() ?: return@withLock
                    val conversationSnapshot = chatRepository.getConversationById(conversationId)
                    val attachmentsSnapshot = _state.value.attachments.toList()

                    val imageAttachment = attachmentsSnapshot.find {
                        val type = context.contentResolver.getType(it.uri) ?: ""
                        type.startsWith("image/")
                    }

                    val userMessage = Message(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        role = MessageRole.USER,
                        content = content,
                        timestamp = System.currentTimeMillis(),
                        tokenCount = null,
                        imageUri = imageAttachment?.uri?.toString()
                    )

                    var userInsert = chatRepository.addMessageSafely(userMessage)
                    if (userInsert.isFailure) {
                        val recoveredConversation = recreateConversationForRetry()
                        if (recoveredConversation == null) {
                            _state.update {
                                it.copy(
                                    isGenerating = false,
                                    error = mapUserFacingError(userInsert.exceptionOrNull())
                                )
                            }
                            return@withLock
                        }
                        conversationId = recoveredConversation.id
                        userInsert = chatRepository.addMessageSafely(
                            userMessage.copy(conversationId = conversationId)
                        )
                        if (userInsert.isFailure) {
                            _state.update {
                                it.copy(
                                    isGenerating = false,
                                    error = mapUserFacingError(userInsert.exceptionOrNull())
                                )
                            }
                            return@withLock
                        }
                    }

                    autoRenameConversationUseCase.invoke(
                        conversationId = conversationId,
                        currentTitle = conversationSnapshot?.title,
                        firstUserMessage = content
                    )

                    val docText = _state.value.attachedDocumentText
                    val finalContent = if (!docText.isNullOrBlank()) {
                        "--- DOCUMENT CONTEXT ---\n$docText\n--- END CONTEXT ---\n\n$content"
                    } else {
                        content
                    }

                    _state.update { it.copy(attachments = emptyList(), attachedDocumentText = null) }

                    // POCKETPAL FIX: isGenerating=true IMMEDIATELY after user message insert.
                    // PocketPal mein modelStore.setInferencing(true) user message addMessage() ke
                    // theek baad call hota hai — model load se pehle. Is se:
                    // 1. User ka send button turant disabled hota hai (no double-send)
                    // 2. UI generating state mein enter karti hai (thinking dots etc)
                    // Local Mind mein pehle ye executeGeneration() ke andar hota tha, matlab
                    // 500-2000ms baad (model load time). Tab tak user fir send press kar sakta tha.
                    _state.update { it.copy(isGenerating = true, error = null) }

                    executeGeneration(
                        conversationId = conversationId,
                        content = finalContent,
                        attachments = attachmentsSnapshot,
                        context = context,
                        currentUserMessageId = userMessage.id,
                        sendMessageStartMs = sendMessageStartMs
                    )
                }
                // PERF FIX: Summarization DISABLED — it was running a second inference
                // after every message, blocking the engine for 5-15s. This was the #1
                // bottleneck vs competitor apps. Can be re-enabled with a deferred,
                // non-blocking approach (e.g., idle-time summarization).
                // conversationId?.let { summarizeConversationUseCase(it) }
            } catch (c: CancellationException) {
                _state.update { it.copy(isGenerating = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isGenerating = false,
                        error = mapUserFacingError(e)
                    )
                }
            } finally {
                releaseWakeLock()
                generationJob = null
            }
        }
    }

    private suspend fun executeGeneration(
        conversationId: String,
        content: String,
        attachments: List<Attachment>,
        context: Context,
        currentUserMessageId: String? = null,
        cutoffTimestamp: Long? = null,
        sendMessageStartMs: Long = System.currentTimeMillis()
    ) {
        val conversationSnapshot = chatRepository.getConversationById(conversationId)
        val allMessages = chatRepository.getMessagesByConversationSync(conversationId)
        val dedupedMessages = allMessages.filter { message ->
            currentUserMessageId == null || message.id != currentUserMessageId
        }

        // Filter history if cutoff is provided (for regeneration)
        val historyMessages = if (cutoffTimestamp != null) {
            dedupedMessages.filter { it.timestamp < cutoffTimestamp }
        } else {
            dedupedMessages
        }

        val conversationModel = resolveConversationModel(conversationSnapshot)
        if (conversationModel == null) {
            _state.update {
                it.copy(
                    isGenerating = false,
                    error = "No model is available for this chat. Select a model and try again."
                )
            }
            return
        }

        // FIX #1: In-memory settings cache — PocketPal parity.
        // 23 DataStore disk reads ki jagah ab ek in-memory object se padhte hain.
        // cachedSettings null ho sakta hai sirf pehli baar jab app just start hua ho;
        // us case mein ek baar fresh read karte hain (readSettingsOnceFresh).
        val s: ChatViewModelSettings = cachedSettings ?: readSettingsOnceFresh()
        val rawTemperature = s.temperature
        val rawMaxTokens = s.maxTokens
        val rawTopP = s.topP
        val rawContextSize = s.contextSize // user ki setting — no cap
        val rawRepeatPenalty = s.penaltyRepeat
        val rawTopK = s.topK
        val rawThreadCount = s.threadCount
        val showAdvanced = s.showAdvancedSettings
        val rawMinP = s.minP
        val rawSeed = s.seed
        val rawXtcThreshold = s.xtcThreshold
        val rawXtcProbability = s.xtcProbability
        val rawTypicalP = s.typicalP
        val rawPenaltyLastN = s.penaltyLastN
        val rawPenaltyFreq = s.penaltyFreq
        val rawPenaltyPresent = s.penaltyPresent
        val rawMirostat = s.mirostat
        val rawMirostatTau = s.mirostatTau
        val rawMirostatEta = s.mirostatEta
        val rawNProbs = s.nProbs
        val rawJinja = s.jinja
        val rawEnableThinking = s.enableThinking
        val rawIncludeThinkingInContext = s.includeThinkingInContext

        val baseTemperature = if (showAdvanced) rawTemperature else conversationModel.recommendedTemperature
        val baseTopP = if (showAdvanced) rawTopP else conversationModel.recommendedTopP
        val baseTopK = if (showAdvanced) rawTopK else conversationModel.recommendedTopK
        val baseRepeatPenalty = if (showAdvanced) rawRepeatPenalty else conversationModel.recommendedRepeatPenalty

        // Detect and prepare media if present
        var imageBytes: ByteArray? = null
        val imageAttachment = attachments.find {
            val type = context.contentResolver.getType(it.uri) ?: ""
            type.startsWith("image/")
        }

        val documentAttachment = attachments.find {
            val type = context.contentResolver.getType(it.uri) ?: ""
            type == "application/pdf" || type == "text/plain"
        }
        var documentBytes: ByteArray? = null
        var documentUriString: String? = null

        if (documentAttachment != null && supportsDocumentAnalysis(conversationModel)) {
            _state.update { it.copy(isAnalyzingMedia = true) }
            documentUriString = documentAttachment.uri.toString()
            documentBytes = try {
                context.contentResolver.openInputStream(documentAttachment.uri)?.readBytes()
            } catch (e: Exception) {
                null
            }
        }

        val hasImageAttachment = imageAttachment != null
        if (imageAttachment != null) {
            _state.update { it.copy(isAnalyzingMedia = true) }
            // Always try to prepare the image for inference.
            // If the model supports vision, it will be used.
            // If not, we still provide it to the state for UI and potentially for the engine to handle.
            imageBytes = ImagePickerManager.prepareImageForInference(context, imageAttachment.uri).getOrNull()
        }

        // isQuestionLike: reserved for future adaptive temperature logic
        @Suppress("UNUSED_VARIABLE") val isQuestionLike = content.contains("?") ||
            content.contains("kya", ignoreCase = true) ||
            content.contains("kaise", ignoreCase = true) ||
            content.contains("why", ignoreCase = true) ||
            content.contains("how", ignoreCase = true)
        val isReasoningModel = conversationModel.name.contains("deepseek", ignoreCase = true) ||
            conversationModel.name.contains("qwq", ignoreCase = true) ||
            conversationModel.templateId.contains("deepseek", ignoreCase = true) ||
            conversationModel.templateId.contains("qwq", ignoreCase = true)

        var finalContent = content

        if (imageAttachment != null && !supportsMediaAnalysis(conversationModel)) {
            // Append a note to the prompt if the model doesn't support vision
            // This ensures the model is "aware" of the image even without native visual processing.
            finalContent = finalContent + "\n\n[User attached an image: ${imageAttachment.name}]"
        }

        var tunedConfig = modelLifecycleManager.tuneInferenceConfig(
            InferenceConfig(
                // POCKETPAL FIX: Temperature clamp hatao — user ki setting respect karo.
                // PocketPal temperature ko bilkul touch nahi karta.
                // Pehle question-type detection se 0.55 max tha — ye models ko robotic banata tha.
                temperature = baseTemperature.coerceIn(0f, 2f),
                maxTokens = rawMaxTokens.coerceIn(32, 131072),
                topP = baseTopP.coerceIn(0f, 1f),
                topK = baseTopK.coerceIn(1, 128),
                minP = rawMinP.coerceIn(0f, 1f),
                // PocketPal: XTC, typical_p
                xtcThreshold = rawXtcThreshold,
                xtcProbability = rawXtcProbability,
                typicalP = rawTypicalP,
                // PocketPal: repeat / freq / presence penalties
                repeatPenalty = baseRepeatPenalty.coerceIn(0.8f, 1.5f),
                penaltyLastN = rawPenaltyLastN,
                penaltyFreq = rawPenaltyFreq,
                penaltyPresent = rawPenaltyPresent,
                // PocketPal: mirostat
                mirostat = rawMirostat,
                mirostatTau = rawMirostatTau,
                mirostatEta = rawMirostatEta,
                // PocketPal: misc
                seed = rawSeed,
                nProbs = rawNProbs,
                jinja = rawJinja,
                enableThinking = rawEnableThinking,
                includeThinkingInContext = rawIncludeThinkingInContext,
                // Context / hardware
                contextSize = rawContextSize.coerceIn(256, 131072), // user unlimited
                threadCount = rawThreadCount.coerceAtLeast(1),
                documentBytes = documentBytes,
                documentUri = documentUriString
            )
        )
        // POCKETPAL FIX: includeThinkingInContext = false hone par assistant messages se
        // <think>...</think> blocks remove karo context mein jaane se pehle.
        // PocketPal useChatSession.ts: removeThinkingParts() function yahi karta hai.
        val processedHistoryMessages = if (!rawIncludeThinkingInContext) {
            historyMessages.map { msg ->
                if (msg.role == com.localmind.app.domain.model.MessageRole.ASSISTANT) {
                    val stripped = msg.content
                        .replace(Regex("<think>[\\s\\S]*?</think>"), "")
                        .replace(Regex("<thought>[\\s\\S]*?</thought>"), "")
                        .replace(Regex("<thinking>[\\s\\S]*?</thinking>"), "")
                        .trim()
                    msg.copy(content = stripped.ifBlank { msg.content })
                } else msg
            }
        } else historyMessages

        var boundedHistoryMessages = selectRecentHistoryWindow(
            messages = processedHistoryMessages,
            tunedContextSize = tunedConfig.contextSize
        )
        var promptBuild = buildPromptWithHistory(
            conversation = conversationSnapshot,
            model = conversationModel,
            historyMessages = boundedHistoryMessages,
            currentInput = finalContent,
            attachments = attachments,
            context = context,
            tunedContextSize = tunedConfig.contextSize
        )
        tunedConfig = tunedConfig.copy(stopTokens = promptBuild.stopTokens)
        var finalPrompt = promptBuild.prompt

        // POCKETPAL FIX: "Instant Typing Bubble" — PocketPal ka sabse bada visible trick!
        // isGenerating=true already sendMessage() mein set ho chuka hai.
        // Yahan sirf media/document states aur streamingResponse="" (empty = instant thinking dots) set karo.
        // streamingResponse="" ensure karta hai ki "Thinking..." indicator turant dikhey.
        _state.update {
            it.copy(
                isGenerating = true,  // ensure = true (regenerate path ke liye bhi)
                isAnalyzingMedia = imageBytes != null,
                isAnalyzingDocument = documentAttachment != null,
                error = null,
                streamingResponse = "",  // ← INSTANT BUBBLE: empty string triggers "Thinking..." indicator
                streamingReasoning = null
            )
        }

        val currentlyActiveModelId = _state.value.activeModel?.id
        @Suppress("UNUSED_VARIABLE") val shouldSwitchModel = currentlyActiveModelId != conversationModel.id
        // TTFT FIX: isModelLoaded() check ke saath loadedKey bhi verify karo.
        // isModelLoaded() sirf ptr != 0 check karta hai — lekin loadedKey se confirm karo
        // ki SAME model loaded hai. Agar same model already loaded hai toh skip karo entirely.
        val (loadedKey, _) = chatRepository.getLoadedModelDetails()
        val targetKey = if (conversationModel.storageType == com.localmind.app.core.storage.ModelStorageType.SAF) {
            "saf:${conversationModel.storageUri}"
        } else {
            val normalizedPath = conversationModel.filePath?.takeIf { it.isNotBlank() }
                ?.let { java.io.File(it).absolutePath } ?: conversationModel.fileName
            "path:$normalizedPath"
        }
        val modelAlreadyLoaded = chatRepository.isModelLoaded() && loadedKey == targetKey
        if (!modelAlreadyLoaded) {
            val loadResult = modelLifecycleManager.activateModelSafely(
                modelId = conversationModel.id,
                options = ActivationOptions(source = ActivationSource.CHAT_AUTOSTART)
            )

            loadResult.onFailure { error ->
                _state.update {
                    it.copy(
                        isGenerating = false,
                        isAnalyzingMedia = false,
                        error = "Failed to load model: ${error.message}"
                    )
                }
                return
            }
        }
        // else: model already loaded + same model — skip entirely (PocketPal behaviour)

        // TTFT FIX: getModelById() is a Room DB call — skip it if model hasn't changed.
        // Only refresh if activeModel is different (e.g. after model switch).
        if (_state.value.activeModel?.id != conversationModel.id) {
            val refreshedModel = modelRepository.getModelById(conversationModel.id) ?: conversationModel
            _state.update { it.copy(activeModel = refreshedModel) }
        }

        // USER SETTING RESPECT: loaded context se override mat karo.
        // User ne jo set kiya wahi native engine ko pass karo.
        // Agar model us context size ko support nahi karta toh native layer khud error dega.

        if (imageBytes != null && supportsMediaAnalysis(conversationModel)) {
            val imageProcessResult = chatRepository.processImage(imageBytes)
            if (imageProcessResult.isFailure) {
                // If native processing fails even for vision models, show a graceful notice
                _state.update { it.copy(isAnalyzingMedia = false) }
            }
        }

        val assistantMessageId = UUID.randomUUID().toString()
        val responseBuilder = StringBuilder(256) // Pre-allocate for typical response

        var receivedFirstToken = false
        var lastTelemetryUpdateMs = 0L
        var lastUiUpdateMs = 0L
        // STREAMING FIX: Pending streaming text jo UI ko nahi bheja gaya abhi tak.
        // Background thread pe accumulate karo, batch mein main thread pe bhejo.
        val pendingStreamText = StringBuilder(256)

        val generationTimeoutMs = if (featureRolloutConfig.snapshot().chatStabilityHardening) {
            Constants.GENERATION_TIMEOUT_MS + 5_000L
        } else {
            Constants.GENERATION_TIMEOUT_MS
        }

        // POCKETPAL PARITY: jinja=true hone par native messages API use karo.
        // Ye PocketPal ke context.completion({ messages: [...], jinja: true }) ke equivalent hai.
        // llm_chat_apply_template() model ke GGUF-embedded chat template use karta hai —
        // exact same tokens jo model training pe use hue the.
        // jinja=false (default) pe purana PromptBuilderService flow chal ta hai (backward compat).
        val useNativeTemplate = rawJinja && !hasImageAttachment

        val generationFlow = if (useNativeTemplate) {
            // Native messages API path
            // PERF: Sirf persona ka system prompt — koi fallback nahi.
            val effectiveSystemPrompt = conversationSnapshot?.systemPrompt?.trim()?.takeIf { it.isNotEmpty() }
                ?: _state.value.selectedPersona?.systemPrompt?.trim().orEmpty()
            val messagesJson = chatRepository.buildMessagesJson(
                systemPrompt = effectiveSystemPrompt,
                historyMessages = boundedHistoryMessages,
                currentUserInput = finalContent
            )
            android.util.Log.d("ChatViewModel", "Using native chat template (jinja=true), messages_json_len=${messagesJson.length}")
            chatRepository.generateWithMessages(messagesJson, tunedConfig)
        } else {
            // Legacy prompt-string path (PromptBuilderService)
            chatRepository.generateResponse(prompt = finalPrompt, config = tunedConfig)
        }

        try {
            withTimeout(generationTimeoutMs) {
                generationFlow.collect { result ->
                    when (result) {
                        is GenerationResult.Started -> Unit
                        is GenerationResult.Token -> {
                            val isFirstToken = !receivedFirstToken
                            if (isFirstToken) {
                                receivedFirstToken = true
                                val ttftKotlinMs = System.currentTimeMillis() - sendMessageStartMs
                                android.util.Log.i("TTFT-KOTLIN", "First token received. sendMessage→firstToken=${ttftKotlinMs}ms")
                                _state.update { it.copy(isAnalyzingMedia = false) }
                            }

                            val text = result.text
                            if (text.isNotEmpty()) {
                                responseBuilder.append(text)
                                pendingStreamText.append(text)

                                // STREAMING FIX: Batched UI updates.
                                // Pehle: har token pe _state.update() = har token pe Compose recomposition.
                                // Ab: tokens accumulate karo, 32ms (30fps) mein ek baar UI update karo.
                                // 32ms = smooth enough + Compose ko breathe karne ka time milta hai.
                                // Native thread se tokens 2-8ms ke beech aate hain — 32ms mein ~4-16 tokens batch.
                                // Iska matlab hai UI updates 30fps pe rahenge lekin tokens miss nahi honge.
                                val nowMs = System.currentTimeMillis()
                                if (isFirstToken || nowMs - lastUiUpdateMs >= 32) {
                                    lastUiUpdateMs = nowMs
                                    // Snapshot aur clear — thread-safe (sab ek coroutine mein hai)
                                    pendingStreamText.clear() // Batched tokens consumed — reset for next batch

                                    val liveTelemetry = if (nowMs - lastTelemetryUpdateMs > 1000) {
                                        lastTelemetryUpdateMs = nowMs
                                        chatRepository.getLastInferenceTelemetry()
                                    } else null

                                    // PERF FIX: splitThinkingContent() O(n) scan — sirf reasoning models ke liye
                                    // aur sirf 200ms interval pe (response grow hone pe expensive hota hai).
                                    val fullText = responseBuilder.toString()
                                    val (thinkContent, mainContent) = if (isReasoningModel) {
                                        splitThinkingContent(fullText)
                                    } else {
                                        Pair("", fullText)
                                    }

                                    // Strip any leaked XML tags from visible streaming text
                                    // O(n) single pass — no regex, negligible perf impact
                                    val cleanMain = com.localmind.app.ui.components.stripXmlLikeTags(
                                        mainContent.trimStart()
                                    )
                                    _state.update {
                                        val newState = it.copy(
                                            streamingResponse = cleanMain.ifBlank { null },
                                            streamingReasoning = thinkContent.ifBlank { null }
                                        )
                                        if (liveTelemetry != null) newState.copy(streamingTelemetry = liveTelemetry) else newState
                                    }
                                }
                            }
                        }
                        is GenerationResult.Complete -> {
                            _state.update { it.copy(isAnalyzingMedia = false, streamingTelemetry = chatRepository.getLastInferenceTelemetry()) }
                        }
                        is GenerationResult.Error -> {
                            throw IllegalStateException(result.message)
                        }
                    }
                }
            }
            val rawFull = responseBuilder.toString()
            val finalText = resolveFinalAssistantText(
                rawText = rawFull,
                stopTokens = tunedConfig.stopTokens
            )
            // POCKETPAL FIX: Reasoning content (thinking) persist karo database mein
            // Pehle sirf UI mein dikhta tha, save nahi hota tha
            val (savedReasoning, _) = if (isReasoningModel) splitThinkingContent(rawFull) else Pair("", "")
            if (finalText.isNotBlank()) {
                val inferenceTelemetry = chatRepository.getLastInferenceTelemetry()
                val assistantMessage = Message(
                    id = assistantMessageId,
                    conversationId = conversationId,
                    role = MessageRole.ASSISTANT,
                    content = finalText,
                    timestamp = System.currentTimeMillis(),
                    tokenCount = null,
                    reasoningContent = savedReasoning.ifBlank { null },
                    inferenceSource = inferenceTelemetry?.source?.name,
                    ttftMs = inferenceTelemetry?.ttftMs,
                    generationMs = inferenceTelemetry?.totalTimeMs,
                    tokensPerSecond = inferenceTelemetry?.tokensPerSecond
                )
                val finalInsert = chatRepository.addMessageSafely(assistantMessage)
                if (finalInsert.isFailure) {
                    throw finalInsert.exceptionOrNull() ?: IllegalStateException("Failed to save response")
                }

                // Capture performance metrics
                val metrics = chatRepository.getPerfMetrics()
                _state.update {
                    it.copy(
                        isGenerating = false,
                        isAnalyzingMedia = false,
                        streamingResponse = null,
                        streamingReasoning = null,
                        lastPerfMetrics = metrics
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isGenerating = false,
                        isAnalyzingMedia = false,
                        streamingResponse = null,
                        streamingReasoning = null,
                        error = "Model did not return text. Try again with this model."
                    )
                }
            }

            // Removed redundant summarization call from here.
            // It is handled in the sendMessage caller to avoid overlapping jobs.
        } catch (cancel: CancellationException) {
            withContext(NonCancellable) {
                val finalText = resolveFinalAssistantText(
                    rawText = responseBuilder.toString(),
                    stopTokens = tunedConfig.stopTokens
                )
                if (finalText.isNotBlank()) {
                    val inferenceTelemetry = chatRepository.getLastInferenceTelemetry()
                    val assistantMessage = Message(
                        id = assistantMessageId,
                        conversationId = conversationId,
                        role = MessageRole.ASSISTANT,
                        content = finalText,
                        timestamp = System.currentTimeMillis(),
                        tokenCount = null,
                        reasoningContent = null,
                        inferenceSource = inferenceTelemetry?.source?.name,
                        ttftMs = inferenceTelemetry?.ttftMs,
                        generationMs = inferenceTelemetry?.totalTimeMs,
                        tokensPerSecond = inferenceTelemetry?.tokensPerSecond
                    )
                    chatRepository.addMessageSafely(assistantMessage)
                }
                _state.update {
                    it.copy(
                        isGenerating = false,
                        isAnalyzingMedia = false,
                        streamingResponse = null,
                        streamingReasoning = null
                    )
                }
            }
            throw cancel
        } catch (oom: OutOfMemoryError) {
            Log.e("LocalMind-Chat", "Generation OOM", oom)
            _state.update {
                it.copy(
                    isGenerating = false,
                    isAnalyzingMedia = false,
                    streamingResponse = null,
                    streamingReasoning = null,
                    error = "Out of memory during generation. Try lower context/tokens."
                )
            }
        } catch (timeout: TimeoutCancellationException) {
            Log.w("LocalMind-Chat", "Generation timed out")
            val finalText = resolveFinalAssistantText(
                rawText = responseBuilder.toString(),
                stopTokens = tunedConfig.stopTokens
            )
            if (finalText.isNotBlank()) {
                val inferenceTelemetry = chatRepository.getLastInferenceTelemetry()
                val assistantMessage = Message(
                    id = assistantMessageId,
                    conversationId = conversationId,
                    role = MessageRole.ASSISTANT,
                    content = finalText,
                    timestamp = System.currentTimeMillis(),
                    tokenCount = null,
                    reasoningContent = null,
                    inferenceSource = inferenceTelemetry?.source?.name,
                    ttftMs = inferenceTelemetry?.ttftMs,
                    generationMs = inferenceTelemetry?.totalTimeMs,
                    tokensPerSecond = inferenceTelemetry?.tokensPerSecond
                )
                chatRepository.addMessageSafely(assistantMessage)
            }
            _state.update {
                it.copy(
                    isGenerating = false,
                    isAnalyzingMedia = false,
                    streamingResponse = null,
                    streamingReasoning = null,
                    error = "Generation timed out. Try shorter prompt or lower max tokens."
                )
            }
        } catch (t: Throwable) {
            Log.e("LocalMind-Chat", "Generation failed", t)
            _state.update {
                it.copy(
                    isGenerating = false,
                    isAnalyzingMedia = false,
                    streamingResponse = null,
                    streamingReasoning = null,
                    error = mapUserFacingError(t)
                )
            }
        } finally {
            _state.update {
                it.copy(
                    isGenerating = false,
                    isAnalyzingMedia = false
                )
            }
        }
    }



    private suspend fun ensureConversationId(): String? {
        currentConversationId?.let { existingId ->
            if (chatRepository.conversationExists(existingId)) {
                return existingId
            }
            currentConversationId = null
            _state.update {
                it.copy(
                    currentConversation = null,
                    messages = emptyList(),
                    streamingResponse = null,
                    error = "Previous chat was removed. Creating a new chat."
                )
            }
        }

        val activeModel = _state.value.activeModel ?: modelRepository.ensureAnyActiveModel()
        if (activeModel == null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = "No active model. Please select a model first."
                )
            }
            return null
        }

        _state.update { it.copy(isLoading = true) }
        val conversation = chatRepository.createConversation(
            title = "New Chat",
            modelId = activeModel.id,
            modelName = activeModel.name,
            systemPrompt = _state.value.selectedPersona?.systemPrompt
        )
        loadConversation(conversation.id)
        return conversation.id
    }

    private suspend fun recreateConversationForRetry(): Conversation? {
        val activeModel = _state.value.activeModel ?: modelRepository.ensureAnyActiveModel()
        if (activeModel == null) {
            _state.update {
                it.copy(error = "No active model. Please select a model first.", isGenerating = false)
            }
            return null
        }

        val conversation = chatRepository.createConversation(
            title = "New Chat",
            modelId = activeModel.id,
            modelName = activeModel.name,
            systemPrompt = _state.value.selectedPersona?.systemPrompt
        )
        loadConversation(conversation.id)
        return conversation
    }

    private suspend fun buildPromptWithHistory(
        conversation: Conversation?,
        model: Model,
        historyMessages: List<Message>,
        currentInput: String,
        attachments: List<Attachment>,
        context: Context,
        tunedContextSize: Int
    ): PromptBuildResult {
        val persona = _state.value.selectedPersona
        val personaSystemPrompt = persona?.systemPrompt

        val dynamicContext = if (persona != null) {
            resolveDynamicContext(persona.contextMode, context) +
            (if (persona.staticContext.isNotBlank()) "\nCONTEXT: ${persona.staticContext}" else "")
        } else ""

        val attachmentBlock = buildAttachmentBlock(attachments, context)
        val documentText = _state.value.attachedDocumentText
        val currentUserBlock = buildString {
            append(currentInput.trim())
            if (attachmentBlock.isNotBlank()) {
                append("\n\n")
                append(attachmentBlock)
            }
            if (!documentText.isNullOrBlank()) {
                append("\n\n[Document Content]:\n")
                append(documentText)
            }
            // STABLE PREFIX: Move changing timestamp to the end of the prompt.
            // This keeps the system prompt and history stable for prefix caching.
            if (dynamicContext.isNotBlank()) {
                append("\n\n[Current Environment]:\n")
                append(dynamicContext)
            }
        }

        // PERF: System prompt sirf persona se aata hai. Koi hardcoded fallback nahi.
        // Blank system prompt = model apna built-in default use karta hai = extra tokens nahi.
        val effectiveSystemPrompt = conversation?.systemPrompt?.trim()?.takeIf { it.isNotEmpty() }
            ?: personaSystemPrompt?.trim().orEmpty()

        // POCKETPAL FIX: Dynamic EOS token detection — model file path pass karo
        // taaki GGUF metadata se actual EOS token automatically detect ho sake.
        val modelFilePath = model.filePath?.takeIf { it.isNotBlank() }
        val promptBuild = promptBuilderService.buildPrompt(
            PromptBuildRequest(
                model = model,
                historyMessages = historyMessages,
                currentUserBlock = currentUserBlock,
                tunedContextSize = tunedContextSize,
                explicitSystemPrompt = effectiveSystemPrompt,
                fallbackSystemPrompt = DEFAULT_SYSTEM_PROMPT,
                currentSummary = conversation?.summary,
                modelPath = modelFilePath
            )
        )

        return PromptBuildResult(
            prompt = promptBuild.prompt,
            stopTokens = promptBuild.stopTokens
        )
    }

    private suspend fun buildAttachmentBlock(
        attachments: List<Attachment>,
        context: Context
    ): String {
        if (attachments.isEmpty()) return ""

        val builder = StringBuilder("Attached Files:\n")
        var totalRemaining = 12_000

        attachments.forEach { attachment ->
            if (totalRemaining <= 0) return@forEach
            builder.append("- ").append(attachment.name).append('\n')

            val perFileLimit = minOf(4_000, totalRemaining)
            val fileContent = readFileContentLimited(attachment.uri, context, perFileLimit)
            if (!fileContent.isNullOrBlank()) {
                val clipped = fileContent.take(perFileLimit)
                builder.append("Content:\n").append(clipped).append('\n')
                totalRemaining -= clipped.length
            }
        }

        return builder.toString().trim()
    }

    private suspend fun readFileContentLimited(
        uri: Uri,
        context: Context,
        maxChars: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri).orEmpty()
            val path = uri.path.orEmpty().lowercase()
            val isTextLike = mimeType.startsWith("text/") ||
                mimeType == "application/json" ||
                path.endsWith(".kt") ||
                path.endsWith(".java") ||
                path.endsWith(".md") ||
                path.endsWith(".txt") ||
                path.endsWith(".xml")

            if (!isTextLike) return@withContext null

            contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(512)
                while (output.length < maxChars) {
                    val toRead = minOf(buffer.size, maxChars - output.length)
                    val read = reader.read(buffer, 0, toRead)
                    if (read <= 0) break
                    output.append(buffer, 0, read)
                }
                output.toString()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun stopGenerationForSessionSwitch() {
        generationJob?.cancel()
        chatRepository.stopGeneration()
        _state.update {
            it.copy(
                isGenerating = false,
                isAnalyzingMedia = false,
                streamingResponse = null,
                streamingReasoning = null
            )
        }
    }

    private suspend fun resolveConversationModel(
        conversation: Conversation?
    ): Model? {
        val active = _state.value.activeModel ?: modelRepository.getActiveModel()
        if (active != null) return active

        val byConversation = conversation?.modelId?.let { modelRepository.getModelById(it) }
        if (byConversation != null) return byConversation

        return modelRepository.ensureAnyActiveModel()
    }





    private fun selectRecentHistoryWindow(
        messages: List<Message>,
        tunedContextSize: Int
    ): List<Message> {
        if (messages.isEmpty()) return emptyList()

        // POCKETPAL FIX: Hardcoded maxMessages=6 limit hatao.
        // PocketPal mein koi message count limit nahi — sirf token budget se control hota hai.
        // Pehle sirf 6 messages aur 25% budget tha — ye prompt cut aur memory loss ka main cause tha.
        // Ab: 50% context budget history ke liye (PocketPal ki tarah).
        var remainingBudgetTokens = (tunedContextSize * 0.50f)
            .toInt()
            .coerceIn(128, tunedContextSize.coerceAtLeast(128))
        val selectedNewestFirst = mutableListOf<Message>()

        for (message in messages.asReversed()) {
            if (remainingBudgetTokens <= 0) break

            val content = message.content.trim()
            if (content.isBlank()) continue

            val estimatedTokens = (content.length / 4).coerceAtLeast(1)
            if (estimatedTokens <= remainingBudgetTokens) {
                selectedNewestFirst += message
                remainingBudgetTokens -= estimatedTokens
                continue
            }

            // Partial fit: last part of big message include karo
            if (remainingBudgetTokens >= 16) {
                val clipped = content.takeLast(remainingBudgetTokens * 4).trimStart()
                if (clipped.isNotBlank()) {
                    selectedNewestFirst += message.copy(content = clipped)
                }
            }
            break
        }

        return selectedNewestFirst.asReversed()
    }

    private fun supportsMediaAnalysis(model: Model): Boolean {
        return model.supportsVision
    }

    private fun supportsDocumentAnalysis(model: Model): Boolean {
        return model.supportsDocument
    }

    fun regenerateWithModel(messageId: String, model: Model, context: Context) {
        viewModelScope.launch {
            // Race-condition-safe: switch model dan isLoadingModel=false hone tak wait karo
            _state.update { it.copy(isLoadingModel = true, error = null) }
            try {
                modelLifecycleManager.activateModelSafely(model.id)
                loadActiveModel()
            } catch (e: Exception) {
                _state.update { it.copy(error = mapUserFacingError(e), isLoadingModel = false) }
                return@launch
            } finally {
                _state.update { it.copy(isLoadingModel = false) }
            }
            // Only regenerate AFTER model is confirmed loaded
            regenerateResponse(messageId, context)
        }
    }

    fun regenerateResponse(messageId: String, context: Context) {
        generationJob?.cancel()
        if (chatRepository.isGenerating()) {
            chatRepository.stopGeneration()
        }

        viewModelScope.launch {
            try {
                generationMutex.withLock {
                    val targetMessage = chatRepository.getMessageById(messageId) ?: return@withLock
                    val conversationId = targetMessage.conversationId

                    // Find the user message that preceded this or is this
                    val messages = chatRepository.getMessagesByConversationSync(conversationId)
                    val lastUserMessage = if (targetMessage.role == MessageRole.USER) {
                        targetMessage
                    } else {
                        messages.filter { it.timestamp <= targetMessage.timestamp && it.role == MessageRole.USER }
                            .maxByOrNull { it.timestamp } ?: return@withLock
                    }

                    // chatRepository.deleteMessagesAfter(conversationId, lastUserMessage.timestamp)

                    val attachments = if (!lastUserMessage.imageUri.isNullOrBlank()) {
                        listOf(Attachment(Uri.parse(lastUserMessage.imageUri), "Image"))
                    } else emptyList()

                    // Pass cutoffTimestamp to ignore subsequent messages in history context
                    executeGeneration(
                        conversationId,
                        lastUserMessage.content,
                        attachments,
                        context,
                        currentUserMessageId = lastUserMessage.id,
                        cutoffTimestamp = lastUserMessage.timestamp
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isGenerating = false, error = mapUserFacingError(e)) }
            } finally {
                generationJob = null
            }
        }
    }

    private fun mapUserFacingError(throwable: Throwable?): String {
        val message = throwable?.message.orEmpty()
        return when {
            message.contains("FOREIGN KEY", ignoreCase = true) -> {
                "Chat session expired. New session created, please retry."
            }
            message.contains("Conversation not found", ignoreCase = true) -> {
                "Chat not available anymore. Started a new chat."
            }
            message.contains("Engine is busy", ignoreCase = true) -> {
                "Model is busy. Please wait and retry."
            }
            message.contains("Prompt too long for context", ignoreCase = true) ||
                message.contains("Failed to evaluate prompt", ignoreCase = true) -> {
                "Prompt too long for current context. Try shorter input or lower context size."
            }
            message.contains("Native model initialization failed", ignoreCase = true) ||
                message.contains("Failed to load model", ignoreCase = true) -> {
                "Model initialization failed. Lower context/batch in Settings or re-download the model."
            }
            message.contains("timed out", ignoreCase = true) -> {
                "Request timed out. Try shorter prompt or lower max tokens."
            }
            message.isNotBlank() -> message
            else -> inferenceErrorMapper.map(throwable)
        }
    }

    private fun resolveFinalAssistantText(rawText: String, stopTokens: List<String>): String {
        val sanitized = promptSanitizationUseCase.sanitizeAssistantReply(
            rawText = rawText,
            stopTokens = stopTokens
        )
        // Strip any remaining XML-like tags from final saved text too
        if (sanitized.isNotBlank())
            return com.localmind.app.ui.components.stripXmlLikeTags(sanitized).trim()

        val stripped = promptSanitizationUseCase.stripStreamingTags(rawText).trim()
        if (stripped.isNotBlank())
            return com.localmind.app.ui.components.stripXmlLikeTags(stripped).trim()

        val (reasoning, main) = splitThinkingContent(rawText)
        if (main.isNotBlank())
            return com.localmind.app.ui.components.stripXmlLikeTags(main).trim()
        if (reasoning.isNotBlank()) return reasoning.trim()

        return ""
    }

    /**
     * PocketPal-style: App open hone par ya model select karne ke baad
     * ensure karo ki model actually engine mein loaded hai.
     * Ye ChatScreen ke LaunchedEffect se call hota hai.
     */
    private var ensureLoadedJob: Job? = null

    fun ensureModelLoaded() {
        val model = _state.value.activeModel ?: return
        // STARTUP FIX: Agar model already loaded hai toh bilkul skip karo.
        // Application.preloadActiveModelAtStartup() background mein load kar raha hota hai.
        // is function ka kaam sirf "safety net" hai — jab genuinely load nahi hua tab.
        if (chatRepository.isModelLoaded()) return
        if (_state.value.isLoadingModel || _state.value.isGenerating) return
        if (ensureLoadedJob?.isActive == true) return

        ensureLoadedJob = viewModelScope.launch {
            // Double-check after acquiring (Application may have finished loading by now)
            if (chatRepository.isModelLoaded()) return@launch
            _state.update { it.copy(isLoadingModel = true) }
            try {
                modelLifecycleManager.activateModelSafely(
                    modelId = model.id,
                    options = ActivationOptions(source = ActivationSource.AUTO_RESTORE)
                )
            } catch (e: Exception) {
                android.util.Log.w("LocalMind", "Auto-load model failed: ${e.message}")
            } finally {
                _state.update { it.copy(isLoadingModel = false) }
                ensureLoadedJob = null
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        chatRepository.stopGeneration()
        _state.update {
            it.copy(
                isGenerating = false,
                isAnalyzingMedia = false,
                streamingResponse = null,
                streamingReasoning = null
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun resolveDynamicContext(mode: PersonaContextMode, context: Context): String {
        if (mode == PersonaContextMode.NONE) return ""

        // PERF FIX: Timestamp ko minute-level par truncate karo, second nahi.
        // Exact time (h:mm:ss) har message pe system prompt change karta tha —
        // iska matlab prefix cache har baar invalidate ho jaata tha = full reprefill = high TTFT.
        // Minute-level truncation se same-minute ke messages cache reuse kar sakte hain.
        val dateFormat = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault())
        // Hour-only truncation: cache 60 minutes tak valid rehta hai
        val hourFormat = java.text.SimpleDateFormat("h a", java.util.Locale.getDefault())
        val now = java.util.Date()
        val dateStr = dateFormat.format(now)
        // Use hour-only for system prompt (stable) — exact time user message mein add karo agar zaroori ho
        val hourStr = hourFormat.format(now)

        val builder = StringBuilder()
        builder.append("CURRENT DATE: $dateStr\n")
        builder.append("APPROXIMATE TIME: $hourStr\n")  // hour-level = prefix cache stable

        if (mode == PersonaContextMode.FULL) {
             val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
             val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
             val isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                 batteryManager.isCharging
             } else {
                 false
             }
             // Battery level bhi round karo 10% increments mein — cache stability ke liye
             val roundedBattery = (batteryLevel / 10) * 10
             builder.append("DEVICE BATTERY: ~$roundedBattery%${if (isCharging) " (Charging)" else ""}\n")
        }

        return builder.toString()
    }

    override fun onCleared() {
        super.onCleared()
        releaseWakeLock()
        conversationObserverJob?.cancel()
        messageObserverJob?.cancel()
        generationJob?.cancel()
        settingsCollectorJob?.cancel() // FIX #1: cleanup settings collector
        chatRepository.stopGeneration()
    }

    fun exportChatAsText(@Suppress("UNUSED_PARAMETER") context: android.content.Context): String {
        val messages = _state.value.messages
        val conversation = _state.value.currentConversation
        val sb = StringBuilder()
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)

        sb.appendLine("# ${conversation?.title ?: "Chat Export"}")
        sb.appendLine("Model: ${conversation?.modelName ?: "Unknown"}")
        sb.appendLine("Exported: ${dateFormat.format(java.util.Date())}")
        sb.appendLine()

        messages.forEach { msg ->
            val role = if (msg.role == com.localmind.app.domain.model.MessageRole.USER) "You" else "AI"
            val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(msg.timestamp))
            sb.appendLine("**[$time] $role:**")
            sb.appendLine(msg.content)
            sb.appendLine()
        }
        return sb.toString()
    }

    fun exportChatAsJson(): String {
        val messages = _state.value.messages
        val conversation = _state.value.currentConversation
        val root = org.json.JSONObject()
        root.put("title", conversation?.title ?: "Chat Export")
        root.put("model", conversation?.modelName ?: "Unknown")
        root.put("exportedAt", System.currentTimeMillis())
        val arr = org.json.JSONArray()
        messages.forEach { msg ->
            val obj = org.json.JSONObject()
            obj.put("role", msg.role.name.lowercase())
            obj.put("content", msg.content)
            obj.put("timestamp", msg.timestamp)
            arr.put(obj)
        }
        root.put("messages", arr)
        return root.toString(2)
    }


}
/**
 * PocketPal-style: <think>...</think> content ko main response se alag karo.
 * Returns Pair(reasoningContent, mainResponseContent)
 */
private fun splitThinkingContent(fullText: String): Pair<String, String> {
    val thinkStart = "<think>"
    val thinkEnd = "</think>"
    val reasoning = StringBuilder()
    val main = StringBuilder()
    var inThink = false
    var pos = 0
    while (pos < fullText.length) {
        when {
            !inThink && fullText.startsWith(thinkStart, pos) -> {
                inThink = true
                pos += thinkStart.length
            }
            inThink && fullText.startsWith(thinkEnd, pos) -> {
                inThink = false
                pos += thinkEnd.length
            }
            inThink -> { reasoning.append(fullText[pos]); pos++ }
            else -> { main.append(fullText[pos]); pos++ }
        }
    }
    return Pair(reasoning.toString(), main.toString())
}

private data class PromptBuildResult(
    val prompt: String,
    val stopTokens: List<String>
)


data class Attachment(
    val uri: Uri,
    val name: String
)

data class ChatState(
    val currentConversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val activeModel: Model? = null,
    val isGenerating: Boolean = false,
    val isAnalyzingMedia: Boolean = false,
    val isAnalyzingDocument: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingModel: Boolean = false,
    val streamingResponse: String? = null,
    val streamingReasoning: String? = null,
    val error: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val availablePersonas: List<Persona> = emptyList(),
    val selectedPersona: Persona? = null,
    val lastPerfMetrics: PerfMetrics? = null,
    val streamingTelemetry: InferenceTelemetry? = null,
    val downloadedModels: List<Model> = emptyList(),
    val attachedDocumentText: String? = null,
    val performanceWarning: String? = null,
    val promptTemplates: List<com.localmind.app.data.local.entity.PromptTemplateEntity> = emptyList(),
    // PocketPal-style edit: jab user koi message edit kare toh pehle usse delete karo
    val pendingEditMessageId: String? = null
)
