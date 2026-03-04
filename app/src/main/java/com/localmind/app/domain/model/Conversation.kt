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
    val isHidden: Boolean = false
)
