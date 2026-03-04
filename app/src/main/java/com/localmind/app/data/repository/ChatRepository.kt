package com.localmind.app.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.localmind.app.data.local.LocalMindDatabase
import com.localmind.app.data.local.dao.ConversationDao
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
        return hybridInferenceRouter.generate(
            prompt = prompt,
            config = config,
            shouldUpdateCache = shouldUpdateCache,
            routeHint = routeHint,
            remoteModelOverride = remoteModelOverride
        )
    }

    fun stopGeneration() {
        llmEngine.stopGeneration()
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
        return hybridInferenceRouter.latestTelemetry()
    }

    fun getLoadedModelDetails(): Pair<String?, Int> {
        return llmEngine.getLoadedModelDetails()
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
