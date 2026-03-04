package com.localmind.app.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
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
    private val getDownloadedModelsUseCase: com.localmind.app.domain.usecase.GetDownloadedModelsUseCase,
    private val getAvailablePersonasUseCase: com.localmind.app.domain.usecase.GetAvailablePersonasUseCase,
    private val autoRenameConversationUseCase: com.localmind.app.domain.usecase.AutoRenameConversationUseCase,
    private val renameConversationUseCase: com.localmind.app.domain.usecase.RenameConversationUseCase,
    private val deleteMessageUseCase: com.localmind.app.domain.usecase.DeleteMessageUseCase,
    private val summarizeConversationUseCase: com.localmind.app.domain.usecase.SummarizeConversationUseCase
) : ViewModel() {
    companion object {
        // PERF FIX: Shortened system prompt from ~89 tokens to ~35.
        // Fewer tokens = faster prefill = faster TTFT.
        private const val DEFAULT_SYSTEM_PROMPT =
            "You are LocalMind, a helpful AI assistant running locally on this device. " +
            "Give clear, well-structured answers using Markdown. Respond in the user's language."
        private const val MEDIA_ANALYSIS_UNSUPPORTED_MESSAGE =
            "I am not able to analyze image/video with this model."
    }

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var currentConversationId: String? = null
    private var generationJob: Job? = null
    private var conversationObserverJob: Job? = null
    private var messageObserverJob: Job? = null
    private val generationMutex = Mutex()
    private var launchSessionInitialized: Boolean = false

    init {
        loadActiveModel()
        loadPersonas()
        loadDownloadedModels()
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

                _state.update { it.copy(activeModel = activeModel, isLoading = false) }
                checkPerformance(activeModel)

                if (!chatRepository.isModelLoaded()) {
                    modelLifecycleManager.activateModelSafely(
                        modelId = activeModel.id,
                        options = ActivationOptions(source = ActivationSource.AUTO_RESTORE)
                    ).onFailure { error ->
                        _state.update { it.copy(error = mapUserFacingError(error)) }
                    }
                }
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

            if (launchSessionInitialized) {
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

    fun sendMessage(content: String, context: Context) {
        if (content.isBlank() && _state.value.attachments.isEmpty()) return

        generationJob?.cancel()
        if (chatRepository.isGenerating()) {
            chatRepository.stopGeneration()
        }

        generationJob = viewModelScope.launch {
            try {
                var conversationId: String? = null
                generationMutex.withLock {
                    val id = ensureConversationId() ?: return@withLock
                    conversationId = id
                    val conversationSnapshot = chatRepository.getConversationById(id)
                    val attachmentsSnapshot = _state.value.attachments.toList()

                    val imageAttachment = attachmentsSnapshot.find {
                        val type = context.contentResolver.getType(it.uri) ?: ""
                        type.startsWith("image/")
                    }

                    val userMessage = Message(
                        id = UUID.randomUUID().toString(),
                        conversationId = id,
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
                            userMessage.copy(conversationId = conversationId!!)
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
                        conversationId = conversationId!!,
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

                    executeGeneration(
                        conversationId = conversationId!!,
                        content = finalContent,
                        attachments = attachmentsSnapshot,
                        context = context,
                        currentUserMessageId = userMessage.id
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
        cutoffTimestamp: Long? = null
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

        // PERF FIX: Read all settings in parallel instead of 8 sequential disk reads.
        // Each .first() was a separate DataStore disk IO. Now we batch with async.
        val settingsDeferred = listOf(
            viewModelScope.async(Dispatchers.IO) { settingsRepository.temperature.first() },
            viewModelScope.async(Dispatchers.IO) { settingsRepository.maxTokens.first() },
            viewModelScope.async(Dispatchers.IO) { settingsRepository.topP.first() },
            viewModelScope.async(Dispatchers.IO) { settingsRepository.contextSize.first() },
            viewModelScope.async(Dispatchers.IO) { settingsRepository.repeatPenalty.first() },
            viewModelScope.async(Dispatchers.IO) { settingsRepository.topK.first() },
            viewModelScope.async(Dispatchers.IO) { settingsRepository.threadCount.first() },
            viewModelScope.async(Dispatchers.IO) { settingsRepository.showAdvancedSettings.first() }
        )
        val rawTemperature = settingsDeferred[0].await() as Float
        val rawMaxTokens = settingsDeferred[1].await() as Int
        val rawTopP = settingsDeferred[2].await() as Float
        val rawContextSize = settingsDeferred[3].await() as Int
        val rawRepeatPenalty = settingsDeferred[4].await() as Float
        val rawTopK = settingsDeferred[5].await() as Int
        val rawThreadCount = settingsDeferred[6].await() as Int
        val showAdvanced = settingsDeferred[7].await() as Boolean

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

        if (imageAttachment != null) {
            _state.update { it.copy(isAnalyzingMedia = true) }
            // Always try to prepare the image for inference.
            // If the model supports vision, it will be used.
            // If not, we still provide it to the state for UI and potentially for the engine to handle.
            imageBytes = ImagePickerManager.prepareImageForInference(context, imageAttachment.uri).getOrNull()
        }

        val isQuestionLike = content.contains("?") ||
            content.contains("kya", ignoreCase = true) ||
            content.contains("kaise", ignoreCase = true) ||
            content.contains("why", ignoreCase = true) ||
            content.contains("how", ignoreCase = true)

        var finalContent = content
        if (imageAttachment != null && !supportsMediaAnalysis(conversationModel)) {
            // Append a note to the prompt if the model doesn't support vision
            // This ensures the model is "aware" of the image even without native visual processing.
            finalContent = content + "\n\n[User attached an image: ${imageAttachment.name}]"
        }

        var tunedConfig = modelLifecycleManager.tuneInferenceConfig(
            InferenceConfig(
                temperature = if (isQuestionLike) {
                    baseTemperature.coerceIn(0.15f, 0.55f)
                } else {
                    baseTemperature.coerceIn(0.15f, 0.90f)
                },
                maxTokens = rawMaxTokens.coerceIn(32, 131072),
                topP = baseTopP.coerceIn(0.60f, 0.98f),
                contextSize = rawContextSize.coerceIn(256, 131072),
                repeatPenalty = baseRepeatPenalty.coerceIn(1.02f, 1.25f),
                topK = baseTopK.coerceIn(20, 80),
                threadCount = rawThreadCount.coerceAtLeast(1),
                documentBytes = documentBytes,
                documentUri = documentUriString
            )
        )
        var boundedHistoryMessages = selectRecentHistoryWindow(
            messages = historyMessages,
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

        _state.update {
            it.copy(
                isGenerating = true,
                isAnalyzingMedia = imageBytes != null,
                isAnalyzingDocument = documentAttachment != null,
                error = null,
                streamingResponse = null,
                streamingReasoning = null
            )
        }

        val currentlyActiveModelId = _state.value.activeModel?.id
        val shouldSwitchModel = currentlyActiveModelId != conversationModel.id
        if (shouldSwitchModel || !chatRepository.isModelLoaded()) {
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

        val refreshedModel = modelRepository.getModelById(conversationModel.id) ?: conversationModel
        _state.update { it.copy(activeModel = refreshedModel) }

        val loadedContext = chatRepository.getLoadedModelDetails().second
        if (loadedContext > 0 && loadedContext != tunedConfig.contextSize) {
            val effectiveContext = min(tunedConfig.contextSize, loadedContext)
            // PERF FIX: Only rebuild prompt if context shrank significantly (>20%).
            // Small differences don't meaningfully change the prompt, and rebuilding
            // is expensive (string allocation + token estimation + template rendering).
            val contextShrinkRatio = effectiveContext.toFloat() / tunedConfig.contextSize.toFloat()
            tunedConfig = tunedConfig.copy(contextSize = effectiveContext)
            if (contextShrinkRatio < 0.80f) {
                boundedHistoryMessages = selectRecentHistoryWindow(
                    messages = historyMessages,
                    tunedContextSize = effectiveContext
                )
                promptBuild = buildPromptWithHistory(
                    conversation = conversationSnapshot,
                    model = conversationModel,
                    historyMessages = boundedHistoryMessages,
                    currentInput = content,
                    attachments = attachments,
                    context = context,
                    tunedContextSize = effectiveContext
                )
                tunedConfig = tunedConfig.copy(stopTokens = promptBuild.stopTokens)
                finalPrompt = promptBuild.prompt
            }
        }

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
        var lastTelemetryUpdateMs = 0L  // PERF FIX: Throttle telemetry reads
        var lastUiUpdateMs = 0L  // PERF FIX: Throttle streaming UI updates

        val generationTimeoutMs = if (featureRolloutConfig.snapshot().chatStabilityHardening) {
            Constants.GENERATION_TIMEOUT_MS + 5_000L
        } else {
            Constants.GENERATION_TIMEOUT_MS
        }

        try {
            withTimeout(generationTimeoutMs) {
                chatRepository.generateResponse(
                    prompt = finalPrompt,
                    config = tunedConfig
                ).collect { result ->
                    when (result) {
                        is GenerationResult.Started -> Unit
                        is GenerationResult.Token -> {
                            if (!receivedFirstToken) {
                                receivedFirstToken = true
                                _state.update { it.copy(isAnalyzingMedia = false) }
                            }
                            // PERF: Tags are already stripped in LLMEngine.onToken()
                            // via IGNORED_TAG_PATTERN. No need to run stripStreamingTags
                            // here again — save 4 regex matches per token.
                            val text = result.text
                            if (text.isNotEmpty()) {
                                responseBuilder.append(text)
                                // PERF FIX: Throttle UI updates to ~20fps (50ms) to
                                // reduce GC pressure from responseBuilder.toString()
                                // on every token. Tokens still accumulate instantly.
                                val nowMs = System.currentTimeMillis()
                                if (nowMs - lastUiUpdateMs >= 50) {
                                    lastUiUpdateMs = nowMs
                                    val liveTelemetry = if (nowMs - lastTelemetryUpdateMs > 300) {
                                        lastTelemetryUpdateMs = nowMs
                                        chatRepository.getLastInferenceTelemetry()
                                    } else null
                                    _state.update {
                                        val newState = it.copy(
                                            streamingResponse = responseBuilder.toString()
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
            val finalText = promptSanitizationUseCase.sanitizeAssistantReply(
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
                val finalText = promptSanitizationUseCase.sanitizeAssistantReply(
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
            val finalText = promptSanitizationUseCase.sanitizeAssistantReply(
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
                    error = "Generation timed out. Try with shorter prompt or lower max tokens."
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

        val effectiveSystemPrompt = (conversation?.systemPrompt?.trim()?.takeIf { it.isNotEmpty() }
            ?: personaSystemPrompt?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_SYSTEM_PROMPT)

        val promptBuild = promptBuilderService.buildPrompt(
            PromptBuildRequest(
                model = model,
                historyMessages = historyMessages,
                currentUserBlock = currentUserBlock,
                tunedContextSize = tunedContextSize,
                explicitSystemPrompt = effectiveSystemPrompt,
                fallbackSystemPrompt = DEFAULT_SYSTEM_PROMPT,
                currentSummary = conversation?.summary
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

        // PERF FIX: Tighter history window for faster TTFT.
        // Less history = fewer prefill tokens = faster first token.
        val maxMessages = 6

        var remainingBudgetTokens = (tunedContextSize * 0.25f)
            .toInt()
            .coerceIn(64, 2048)
        val selectedNewestFirst = mutableListOf<Message>()

        for (message in messages.asReversed()) {
            if (selectedNewestFirst.size >= maxMessages || remainingBudgetTokens <= 0) {
                break
            }
            val content = message.content.trim()
            if (content.isBlank()) continue

            val estimatedTokens = (content.length / 4).coerceAtLeast(1)
            if (estimatedTokens <= remainingBudgetTokens) {
                selectedNewestFirst += message
                remainingBudgetTokens -= estimatedTokens
                continue
            }

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

    private suspend fun postAssistantMessage(conversationId: String, content: String) {
        val message = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = content,
            timestamp = System.currentTimeMillis(),
            tokenCount = null
        )
        chatRepository.addMessageSafely(message)
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
            message.contains("timed out", ignoreCase = true) -> {
                "Request timed out. Try shorter prompt or lower max tokens."
            }
            message.isNotBlank() -> message
            else -> inferenceErrorMapper.map(throwable)
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

        val dateFormat = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault())
        val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        val now = java.util.Date()
        val dateStr = dateFormat.format(now)
        val timeStr = timeFormat.format(now)

        val builder = StringBuilder()
        builder.append("CURRENT TIME: $timeStr\n")
        builder.append("CURRENT DATE: $dateStr\n")

        if (mode == PersonaContextMode.FULL) {
             val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
             val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
             val isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                 batteryManager.isCharging
             } else {
                 false
             }
             builder.append("DEVICE BATTERY: $batteryLevel%${if (isCharging) " (Charging)" else ""}\n")
        }

        return builder.toString()
    }

    override fun onCleared() {
        super.onCleared()
        conversationObserverJob?.cancel()
        messageObserverJob?.cancel()
        generationJob?.cancel()
        chatRepository.stopGeneration()
    }

    fun attachDocument(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val helper = com.localmind.app.core.utils.DocumentHelper(context)
            val text = helper.extractTextFromUri(uri)
            if (text != null) {
                _state.update { it.copy(attachedDocumentText = text) }
            }
        }
    }

    fun removeDocument() {
        _state.update { it.copy(attachedDocumentText = null) }
    }
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
    val performanceWarning: String? = null
)
