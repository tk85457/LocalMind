package com.localmind.app.domain.model

data class Conversation(
    val id: String,
    val title: String,
    val modelId: String,
    val modelName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int,
    val systemPrompt: String?,
    val summary: String? = null,
    val isHidden: Boolean = false,
    // PocketPal WatermelonDB parity features
    val isPinned: Boolean = false,           // Pinned conversations top pe dikhti hain
    val personaId: String? = null,           // Linked persona (WatermelonDB relation)
    val totalTokens: Int = 0,                // Total tokens used in this conversation
    val lastMessagePreview: String? = null,  // Last message preview for list display
    val lastMessageRole: String? = null      // "user" or "assistant" — for list icon
)
