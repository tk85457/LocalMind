package com.localmind.app.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.localmind.app.data.local.LocalMindDatabase
import com.localmind.app.data.local.dao.ConversationDao
import com.localmind.app.data.local.dao.ModelUsageStat
import com.localmind.app.data.local.dao.MessageDao
import com.localmind.app.data.local.entity.ConversationEntity
import com.localmind.app.data.local.entity.MessageEntity
import com.localmind.app.data.mapper.toDomain
import com.localmind.app.data.mapper.toEntity
import androidx.room.withTransaction
import com.localmind.app.domain.model.Conversation
import com.localmind.app.domain.model.Message
import com.localmind.app.domain.model.MessageRole
import com.localmind.app.domain.model.Model
import com.localmind.app.llm.GenerationResult
import com.localmind.app.llm.HybridInferenceRouter
import com.localmind.app.llm.InferenceConfig
import com.localmind.app.llm.InferenceRouteHint
import com.localmind.app.llm.InferenceTelemetry
import com.localmind.app.llm.LLMEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val database: LocalMindDatabase,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val llmEngine: LLMEngine,
    private val hybridInferenceRouter: HybridInferenceRouter
) {
    // Conversation operations
    fun getAllConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getAllConversationsSync(): List<Conversation> {
        return conversationDao.getAllConversationsSync().map { it.toDomain() }
    }

    suspend fun getMostRecentConversation(): Conversation? {
        return conversationDao.getMostRecentConversation()?.toDomain()
    }

    suspend fun getConversationById(id: String): Conversation? {
        return conversationDao.getConversationById(id)?.toDomain()
    }

    fun getConversationByIdFlow(id: String): Flow<Conversation?> {
        return conversationDao.getConversationByIdFlow(id).map { it?.toDomain() }
    }

    suspend fun conversationExists(conversationId: String): Boolean {
        return conversationDao.conversationExists(conversationId)
    }

    suspend fun createConversation(
        title: String,
        modelId: String,
        modelName: String,
        systemPrompt: String? = null
    ): Conversation {
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = title,
            modelId = modelId,
            modelName = modelName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            messageCount = 0,
            systemPrompt = systemPrompt,
            isHidden = true
        )
        conversationDao.insertConversation(conversation.toEntity())
        return conversation
    }

    suspend fun deleteConversation(conversation: Conversation) {
        // Delete all messages first
        messageDao.deleteMessagesByConversation(conversation.id)
        // Delete conversation
        conversationDao.deleteConversation(conversation.toEntity())
    }

    suspend fun deleteConversationsBySystemPrompt(systemPrompt: String) {
        val conversations = conversationDao.getConversationsBySystemPrompt(systemPrompt)
        conversations.forEach {
            deleteConversation(it.id)
        }
    }

    suspend fun deleteConversation(conversationId: String) {
        // Delete all messages first
        messageDao.deleteMessagesByConversation(conversationId)
        // Delete conversation by ID
        val entity = conversationDao.getConversationById(conversationId)
        if (entity != null) {
            conversationDao.deleteConversation(entity)
        }
    }

    suspend fun deleteOldChats(days: Int) {
        if (days <= 0) return
        val cutoffTimestamp = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        conversationDao.deleteOldConversations(cutoffTimestamp)
    }

    suspend fun deleteMessage(messageId: String) {
        val entity = messageDao.getMessageById(messageId) ?: return
        messageDao.deleteMessage(entity)
        // Decrement message count for the conversation
        val conv = conversationDao.getConversationById(entity.conversationId)
        if (conv != null) {
            val updated = conv.copy(messageCount = (conv.messageCount - 1).coerceAtLeast(0))
            conversationDao.updateConversation(updated)
        }
    }

    suspend fun renameConversation(conversationId: String, newTitle: String) {
        conversationDao.updateConversationTitle(
            id = conversationId,
            title = newTitle,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun updateConversationModel(conversationId: String, modelId: String, modelName: String) {
        conversationDao.updateConversationModel(
            id = conversationId,
            modelId = modelId,
            modelName = modelName,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun updateConversationSystemPrompt(conversationId: String, systemPrompt: String?) {
        val conv = conversationDao.getConversationById(conversationId)
        if (conv != null) {
            val updated = conv.copy(systemPrompt = systemPrompt, updatedAt = System.currentTimeMillis())
            conversationDao.updateConversation(updated)
        }
    }

    // Import helpers for JSON import
    suspend fun createConversationFromImport(id: String, title: String, modelName: String, timestamp: Long) {
        if (conversationDao.conversationExists(id)) return // skip duplicates
        val conv = ConversationEntity(
            id = id, title = title, modelId = "", modelName = modelName,
            createdAt = timestamp, updatedAt = timestamp, messageCount = 0,
            systemPrompt = null, isHidden = false
        )
        conversationDao.insertConversation(conv)
    }

    suspend fun insertImportedMessage(conversationId: String, role: String, content: String, timestamp: Long) {
        val msgRole = when (role.trim().lowercase()) {
            "user" -> MessageRole.USER
            "assistant", "model", "ai", "bot" -> MessageRole.ASSISTANT
            else -> MessageRole.ASSISTANT
        }
        val msg = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = msgRole.toApiString(),
            content = content,
            timestamp = timestamp
        )
        messageDao.insertMessage(msg)
        conversationDao.incrementMessageCount(id = conversationId, timestamp = System.currentTimeMillis())
    }

    /**
     * Bulk import conversations and messages in a single transaction.
     */
    suspend fun importChatBackup(conversations: List<ConversationEntity>, messages: List<MessageEntity>) {
        database.withTransaction {
            conversations.forEach { conv ->
                if (!conversationDao.conversationExists(conv.id)) {
                    conversationDao.insertConversation(conv)
                }
            }
            messageDao.insertMessages(messages)
        }
    }

    // Message operations
    fun getMessagesByConversation(conversationId: String): Flow<List<Message>> {
        return messageDao.getMessagesByConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getMessagesByConversationSync(conversationId: String): List<Message> {
        return messageDao.getMessagesByConversationSync(conversationId).map { it.toDomain() }
    }

    suspend fun addMessage(message: Message) {
        messageDao.insertMessage(message.toEntity())
        // Update conversation timestamp and message count
        if (message.role == MessageRole.USER) {
            conversationDao.incrementMessageCountAndReveal(
                id = message.conversationId,
                timestamp = System.currentTimeMillis()
            )
        } else {
            conversationDao.incrementMessageCount(
                id = message.conversationId,
                timestamp = System.currentTimeMillis()
            )
        }
        // PocketPal WatermelonDB parity: update denormalized fields
        val preview = message.content.take(120)
        conversationDao.updateLastMessagePreview(
            id = message.conversationId,
            preview = preview,
            role = message.role.toApiString(),
            updatedAt = System.currentTimeMillis()
        )
        // Update token running total
        val tokenDelta = message.tokenCount ?: 0
        if (tokenDelta > 0) {
            conversationDao.addTokens(message.conversationId, tokenDelta)
        }
    }

    suspend fun addMessageSafely(message: Message): Result<Unit> {
        return runCatching {
            if (!conversationDao.conversationExists(message.conversationId)) {
                // If the conversation was deleted while generating, ignore the message
                return@runCatching
            }

            messageDao.insertMessage(message.toEntity())

            if (message.role == MessageRole.USER) {
                conversationDao.incrementMessageCountAndReveal(
                    id = message.conversationId,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                conversationDao.incrementMessageCount(
                    id = message.conversationId,
                    timestamp = System.currentTimeMillis()
                )
            }
        }.mapErrorToUserSafe()
    }

    suspend fun updateConversationSummary(conversationId: String, summary: String) {
        conversationDao.updateSummary(conversationId, summary)
    }

    suspend fun updateConversationTitle(conversationId: String, title: String) {
        conversationDao.updateConversationTitle(
            id = conversationId,
            title = title,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun getMessageById(messageId: String): Message? {
        return messageDao.getMessageById(messageId)?.toDomain()
    }

    suspend fun deleteMessagesAfter(conversationId: String, timestamp: Long) {
        messageDao.deleteMessagesAfter(conversationId, timestamp)
    }

    // LLM operations
    fun generateResponse(
        prompt: String,
        config: InferenceConfig = InferenceConfig.DEFAULT,
        shouldUpdateCache: Boolean = true,
        routeHint: InferenceRouteHint = InferenceRouteHint.AUTO,
        remoteModelOverride: String? = null
    ): Flow<GenerationResult> {
        // FIX #5: For LOCAL_ONLY (default), skip HybridInferenceRouter + LocalInferenceEngine
        // indirection layers. Go directly to LLMEngine to eliminate 2-3 coroutine context
        // switches and flow wrapping overhead. This matches PocketPal's single-hop: context.completion().
        if (routeHint == InferenceRouteHint.AUTO || routeHint == InferenceRouteHint.FORCE_LOCAL) {
            return llmEngine.generate(prompt = prompt, config = config, shouldUpdateCache = shouldUpdateCache)
        }
        return hybridInferenceRouter.generate(
            prompt = prompt,
            config = config,
            shouldUpdateCache = shouldUpdateCache,
            routeHint = routeHint,
            remoteModelOverride = remoteModelOverride
        )
    }

    /**
     * POCKETPAL PARITY: Generate using native chat template (messages API).
     * Delegates directly to LLMEngine.generateWithMessages().
     */
    fun generateWithMessages(
        messagesJson: String,
        config: InferenceConfig = InferenceConfig.DEFAULT,
        shouldUpdateCache: Boolean = true
    ): Flow<GenerationResult> {
        return llmEngine.generateWithMessages(
            messagesJson = messagesJson,
            config = config,
            shouldUpdateCache = shouldUpdateCache
        )
    }

    /**
     * POCKETPAL PARITY: Build JSON messages array for native chat template.
     * Delegates to LLMEngine.buildMessagesJson().
     */
    fun buildMessagesJson(
        systemPrompt: String?,
        historyMessages: List<Message>,
        currentUserInput: String
    ): String {
        return llmEngine.buildMessagesJson(
            systemPrompt = systemPrompt,
            historyMessages = historyMessages,
            currentUserInput = currentUserInput
        )
    }

    fun stopGeneration() {
        llmEngine.stopGeneration()
    }

    suspend fun waitForEngineReady(timeoutMs: Long = 3_000L) {
        llmEngine.waitForMutexRelease(timeoutMs)
    }

    suspend fun loadModel(
        modelPath: String,
        quantizationHint: String? = null,
        parameterCountHint: String? = null
    ): Result<Unit> {
        return llmEngine.loadModel(
            modelPath = modelPath,
            quantizationHint = quantizationHint,
            parameterCountHint = parameterCountHint
        )
    }

    suspend fun loadModel(model: Model): Result<Unit> {
        return llmEngine.loadModel(
            modelPath = model.filePath ?: model.fileName,
            quantizationHint = model.quantization,
            parameterCountHint = model.parameterCount,
            storageType = model.storageType,
            storageUri = model.storageUri
        )
    }

    suspend fun unloadModel() {
        llmEngine.unloadModel()
    }

    fun isModelLoaded(): Boolean {
        return llmEngine.isModelLoaded()
    }

    fun isGenerating(): Boolean {
        return llmEngine.isGenerating()
    }

    suspend fun processImage(imageBytes: ByteArray): Result<Unit> {
        return llmEngine.processImage(imageBytes)
    }

    fun getPerfMetrics(): com.localmind.app.core.engine.PerfMetrics? {
        return llmEngine.getPerfMetrics()
    }

    fun getLastInferenceTelemetry(): InferenceTelemetry? {
        // FIX #5: LLMEngine direct call ke baad telemetry router mein nahi hoti.
        // LLMEngine ka apna latestTelemetry check karo, router se fallback karo.
        return llmEngine.getLastTelemetry() ?: hybridInferenceRouter.latestTelemetry()
    }

    fun getLoadedModelDetails(): Pair<String?, Int> {
        return llmEngine.getLoadedModelDetails()
    }

    // =========================================================
    // PocketPal WatermelonDB parity: Pin, Persona, Search, Stats
    // =========================================================

    /**
     * Toggle pin status of a conversation.
     * Pinned conversations always appear at the top of the list.
     * PocketPal equivalent: conversation.update(c => { c.isPinned = !c.isPinned })
     */
    suspend fun togglePin(conversationId: String) {
        val conv = conversationDao.getConversationById(conversationId) ?: return
        conversationDao.updatePinStatus(
            id = conversationId,
            isPinned = !conv.isPinned,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Set pin status explicitly.
     */
    suspend fun setPinned(conversationId: String, pinned: Boolean) {
        conversationDao.updatePinStatus(
            id = conversationId,
            isPinned = pinned,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Link or unlink a persona from a conversation.
     * PocketPal: conversation.persona.set(persona) via WatermelonDB relation.
     */
    suspend fun setConversationPersona(conversationId: String, personaId: String?) {
        conversationDao.updatePersonaId(conversationId, personaId)
    }

    /**
     * Get all conversations for a specific persona.
     * PocketPal: persona.conversations.fetch()
     */
    fun getConversationsByPersona(personaId: String): Flow<List<Conversation>> {
        return conversationDao.getConversationsByPersona(personaId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get pinned conversations only.
     */
    fun getPinnedConversations(): Flow<List<Conversation>> {
        return conversationDao.getPinnedConversations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Full-text search across all message content.
     * PocketPal: WatermelonDB Q.where(Q.like('%query%', Q.column('body')))
     * Uses FTS4 for speed; falls back to LIKE if query has special chars.
     */
    suspend fun searchMessages(query: String, limit: Int = 50): List<Message> {
        if (query.isBlank()) return emptyList()
        return try {
            // FTS4 query — sanitize to avoid SQLite FTS syntax errors
            val ftsQuery = query.trim()
                .replace("'", "''")
                .replace("\"", "")
            messageDao.searchMessages(ftsQuery, limit).map { it.toDomain() }
        } catch (e: Exception) {
            // Fallback to LIKE search if FTS fails (e.g. special chars)
            messageDao.searchMessagesLike(query, limit).map { it.toDomain() }
        }
    }

    /**
     * Search messages within a specific conversation.
     */
    suspend fun searchMessagesInConversation(query: String, conversationId: String): List<Message> {
        if (query.isBlank()) return emptyList()
        return try {
            messageDao.searchMessagesInConversation(query.trim(), conversationId).map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Search conversations by title, summary, or last message preview.
     * PocketPal: WatermelonDB title LIKE query.
     */
    suspend fun searchConversations(query: String, limit: Int = 30): List<Conversation> {
        if (query.isBlank()) return emptyList()
        return conversationDao.searchConversations(query.trim(), limit).map { it.toDomain() }
    }

    /**
     * Get token usage statistics per model.
     * PocketPal: WatermelonDB aggregation on conversations table.
     */
    suspend fun getUsageByModel(): List<ModelUsageStat> {
        return conversationDao.getUsageByModel()
    }

    /**
     * Update the cached last-message preview for a conversation.
     * Called after every message is saved to keep the list up-to-date.
     */
    suspend fun refreshLastMessagePreview(conversationId: String) {
        val lastMessage = messageDao.getLastMessage(conversationId) ?: return
        val preview = lastMessage.content.take(120)
        conversationDao.updateLastMessagePreview(
            id = conversationId,
            preview = preview,
            role = lastMessage.role,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Recalculate and update total token count for a conversation.
     * Called after generation completes.
     */
    suspend fun recalculateTotalTokens(conversationId: String) {
        val total = messageDao.getTotalTokenCount(conversationId) ?: 0
        conversationDao.setTotalTokens(conversationId, total)
    }

    /**
     * Add incremental token delta to a conversation's running total.
     * Faster than full recalculation — called after each message save.
     */
    suspend fun addTokensToConversation(conversationId: String, tokenDelta: Int) {
        if (tokenDelta > 0) {
            conversationDao.addTokens(conversationId, tokenDelta)
        }
    }

    /**
     * Get recent messages across all conversations — for global history view.
     */
    suspend fun getRecentMessages(limit: Int = 100): List<Message> {
        return messageDao.getRecentMessages(limit).map { it.toDomain() }
    }
}

private fun Result<Unit>.mapErrorToUserSafe(): Result<Unit> {
    return exceptionOrNull()?.let { throwable ->
        val mapped = when {
            throwable is SQLiteConstraintException ||
                throwable.message?.contains("FOREIGN KEY", ignoreCase = true) == true -> {
                IllegalStateException("Chat session expired. Please retry.")
            }
            else -> throwable
        }
        Result.failure(mapped)
    } ?: this
}
