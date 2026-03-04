package com.localmind.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
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
    val isHidden: Boolean = false
)
