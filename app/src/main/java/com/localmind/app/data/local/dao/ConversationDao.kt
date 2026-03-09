package com.localmind.app.data.local.dao

import androidx.room.*
import com.localmind.app.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    // ─── Core queries ──────────────────────────────────────────────────────────

    /**
     * All visible conversations — pinned first, then by updatedAt DESC.
     * PocketPal WatermelonDB: sortBy([{column: 'is_pinned', order: 'desc'}, {column: 'updated_at', order: 'desc'}])
     */
    @Query("SELECT * FROM conversations WHERE isHidden = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE isHidden = 0 ORDER BY isPinned DESC, updatedAt DESC")
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

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity)

    // ─── Targeted updates (avoid full row rewrites) ────────────────────────────

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

    // ─── PocketPal WatermelonDB parity ────────────────────────────────────────

    /**
     * Toggle pin status. Pinned conversations appear first in the list.
     * PocketPal equivalent: conversation.update(c => { c.isPinned = !c.isPinned })
     */
    @Query("UPDATE conversations SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePinStatus(id: String, isPinned: Boolean, updatedAt: Long)

    /**
     * Link / unlink a persona to a conversation.
     * PocketPal: conversation.persona.set(persona) via WatermelonDB relation.
     */
    @Query("UPDATE conversations SET personaId = :personaId WHERE id = :id")
    suspend fun updatePersonaId(id: String, personaId: String?)

    /**
     * Update cached last-message preview for list display.
     * PocketPal: denormalized lastMessage field on conversation record.
     */
    @Query("""
        UPDATE conversations
        SET lastMessagePreview = :preview,
            lastMessageRole = :role,
            updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateLastMessagePreview(id: String, preview: String?, role: String?, updatedAt: Long)

    /**
     * Update total token count (running sum across all messages).
     * PocketPal: conversation.totalTokens (computed field).
     */
    @Query("UPDATE conversations SET totalTokens = totalTokens + :delta WHERE id = :id")
    suspend fun addTokens(id: String, delta: Int)

    @Query("UPDATE conversations SET totalTokens = :total WHERE id = :id")
    suspend fun setTotalTokens(id: String, total: Int)

    /**
     * Get all pinned conversations.
     */
    @Query("SELECT * FROM conversations WHERE isPinned = 1 AND isHidden = 0 ORDER BY updatedAt DESC")
    fun getPinnedConversations(): Flow<List<ConversationEntity>>

    /**
     * Get conversations linked to a specific persona.
     * PocketPal: persona.conversations.fetch()
     */
    @Query("SELECT * FROM conversations WHERE personaId = :personaId AND isHidden = 0 ORDER BY updatedAt DESC")
    fun getConversationsByPersona(personaId: String): Flow<List<ConversationEntity>>

    /**
     * Full-text search on conversation title and lastMessagePreview.
     * PocketPal equivalent: WatermelonDB Q.where(Q.like('%query%', Q.column('title')))
     */
    @Query("""
        SELECT * FROM conversations
        WHERE isHidden = 0
          AND (title LIKE '%' || :query || '%'
               OR lastMessagePreview LIKE '%' || :query || '%'
               OR summary LIKE '%' || :query || '%')
        ORDER BY isPinned DESC, updatedAt DESC
        LIMIT :limit
    """)
    suspend fun searchConversations(query: String, limit: Int = 30): List<ConversationEntity>

    /**
     * Get conversation stats: total messages, total tokens per model.
     * PocketPal: analytics/usage data from WatermelonDB aggregation.
     */
    @Query("""
        SELECT modelName, COUNT(*) as count, SUM(totalTokens) as tokens
        FROM conversations
        WHERE isHidden = 0
        GROUP BY modelName
        ORDER BY count DESC
    """)
    suspend fun getUsageByModel(): List<ModelUsageStat>

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    @Query("DELETE FROM messages WHERE conversationId IN (SELECT id FROM conversations WHERE updatedAt < :timestamp)")
    suspend fun deleteMessagesForOldConversations(timestamp: Long)

    @Query("DELETE FROM conversations WHERE updatedAt < :timestamp AND isPinned = 0")
    suspend fun deleteOldConversations(timestamp: Long)
}

/**
 * Lightweight projection for model usage stats.
 * Used by analytics/settings screen.
 */
data class ModelUsageStat(
    val modelName: String,
    val count: Int,
    val tokens: Long
)
