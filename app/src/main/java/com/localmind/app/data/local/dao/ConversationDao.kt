package com.localmind.app.data.local.dao

import androidx.room.*
import com.localmind.app.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations WHERE isHidden = 0 ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE isHidden = 0 ORDER BY updatedAt DESC")
    suspend fun getAllConversationsSync(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE isHidden = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getMostRecentConversation(): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun getConversationByIdFlow(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE systemPrompt = :systemPrompt")
    suspend fun getConversationsBySystemPrompt(systemPrompt: String): List<ConversationEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM conversations WHERE id = :id)")
    suspend fun conversationExists(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET updatedAt = :timestamp, messageCount = messageCount + 1 WHERE id = :id")
    suspend fun incrementMessageCount(id: String, timestamp: Long)

    @Query("UPDATE conversations SET updatedAt = :timestamp, messageCount = messageCount + 1, isHidden = 0 WHERE id = :id")
    suspend fun incrementMessageCountAndReveal(id: String, timestamp: Long)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateConversationTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET modelId = :modelId, modelName = :modelName, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateConversationModel(id: String, modelId: String, modelName: String, updatedAt: Long)

    @Query("UPDATE conversations SET isHidden = 0 WHERE id = :id")
    suspend fun unhideConversation(id: String)

    @Query("UPDATE conversations SET summary = :summary WHERE id = :id")
    suspend fun updateSummary(id: String, summary: String)

    @Query("SELECT COUNT(*) FROM conversations WHERE isHidden = 0")
    suspend fun getConversationCount(): Int

    @Query("DELETE FROM messages WHERE conversationId IN (SELECT id FROM conversations WHERE updatedAt < :timestamp)")
    suspend fun deleteMessagesForOldConversations(timestamp: Long)

    @Query("DELETE FROM conversations WHERE updatedAt < :timestamp")
    suspend fun deleteOldConversations(timestamp: Long)
}
