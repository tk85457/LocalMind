package com.localmind.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["personaId"], name = "idx_conversations_personaId"),
        Index(value = ["isPinned", "updatedAt"], name = "idx_conversations_isPinned", orders = [Index.Order.ASC, Index.Order.DESC])
    ]
)
data class ConversationEntity(
    @PrimaryKey
    val id: String, // UUID
    val title: String,
    val modelId: String, // Reference to ModelEntity
    val modelName: String, // Cached for display
    val createdAt: Long, // timestamp
    val updatedAt: Long, // timestamp
    val messageCount: Int = 0,
    val systemPrompt: String? = null,
    val summary: String? = null,
    val isHidden: Boolean = false,
    // PocketPal WatermelonDB parity: structured metadata
    val isPinned: Boolean = false,           // Pin conversation to top
    val personaId: String? = null,           // FK → personas.id (soft ref, no FK constraint)
    val totalTokens: Int = 0,                // Running total of token usage
    val lastMessagePreview: String? = null,  // Cached last message text (< 120 chars)
    val lastMessageRole: String? = null      // "user" or "assistant"
)
