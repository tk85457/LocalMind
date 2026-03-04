package com.localmind.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey
    val id: String, // UUID
    val conversationId: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long,
    val tokenCount: Int? = null,
    val imageUri: String? = null,
    val reasoningContent: String? = null,
    val inferenceSource: String? = null,
    val ttftMs: Long? = null,
    val generationMs: Long? = null,
    val tokensPerSecond: Float? = null
)
