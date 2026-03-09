package com.localmind.app.data.local.dao

import androidx.room.*
import com.localmind.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesByConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesByConversationSync(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(conversationId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND timestamp > :timestamp")
    suspend fun deleteMessagesAfter(conversationId: String, timestamp: Long)

    @Query("SELECT SUM(tokenCount) FROM messages WHERE conversationId = :conversationId")
    suspend fun getTotalTokenCount(conversationId: String): Int?

    // =========================================================
    // PocketPal WatermelonDB parity: Full-Text Search
    // =========================================================

    /**
     * Full-text search across all message content.
     * Returns matching MessageEntity rows using FTS4 MATCH.
     * Query supports: exact phrase ("hello world"), prefix (hello*), boolean (hello OR world)
     */
    @Query("""
        SELECT messages.* FROM messages
        INNER JOIN message_fts ON messages.rowid = message_fts.rowid
        WHERE message_fts MATCH :query
        ORDER BY messages.timestamp DESC
        LIMIT :limit
    """)
    suspend fun searchMessages(query: String, limit: Int = 50): List<MessageEntity>

    /**
     * Full-text search within a specific conversation.
     */
    @Query("""
        SELECT messages.* FROM messages
        INNER JOIN message_fts ON messages.rowid = message_fts.rowid
        WHERE message_fts MATCH :query
          AND messages.conversationId = :conversationId
        ORDER BY messages.timestamp DESC
    """)
    suspend fun searchMessagesInConversation(
        query: String,
        conversationId: String
    ): List<MessageEntity>

    /**
     * Simple LIKE-based search fallback (for queries with special chars that break FTS).
     */
    @Query("""
        SELECT * FROM messages
        WHERE content LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun searchMessagesLike(query: String, limit: Int = 50): List<MessageEntity>

    /**
     * Get all messages for a conversation with a token count for export.
     */
    @Query("""
        SELECT * FROM messages
        WHERE conversationId = :conversationId
        ORDER BY timestamp ASC
    """)
    suspend fun getMessagesForExport(conversationId: String): List<MessageEntity>

    /**
     * Batch delete messages for multiple conversations (cleanup).
     */
    @Query("DELETE FROM messages WHERE conversationId IN (:conversationIds)")
    suspend fun deleteMessagesForConversations(conversationIds: List<String>)

    /**
     * Get recent messages across all conversations — for global history feed.
     * PocketPal equivalent: WatermelonDB query with sort + limit.
     */
    @Query("""
        SELECT * FROM messages
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getRecentMessages(limit: Int = 100): List<MessageEntity>
}
