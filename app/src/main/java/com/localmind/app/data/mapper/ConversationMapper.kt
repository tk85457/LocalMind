package com.localmind.app.data.mapper

import com.localmind.app.data.local.entity.ConversationEntity
import com.localmind.app.domain.model.Conversation

fun ConversationEntity.toDomain(): Conversation {
    return Conversation(
        id = id,
        title = title,
        modelId = modelId,
        modelName = modelName,
        createdAt = createdAt,
        updatedAt = updatedAt,
        messageCount = messageCount,
        systemPrompt = systemPrompt,
        summary = summary,
        isHidden = isHidden,
        isPinned = isPinned,
        personaId = personaId,
        totalTokens = totalTokens,
        lastMessagePreview = lastMessagePreview,
        lastMessageRole = lastMessageRole
    )
}

fun Conversation.toEntity(): ConversationEntity {
    return ConversationEntity(
        id = id,
        title = title,
        modelId = modelId,
        modelName = modelName,
        createdAt = createdAt,
        updatedAt = updatedAt,
        messageCount = messageCount,
        systemPrompt = systemPrompt,
        summary = summary,
        isHidden = isHidden,
        isPinned = isPinned,
        personaId = personaId,
        totalTokens = totalTokens,
        lastMessagePreview = lastMessagePreview,
        lastMessageRole = lastMessageRole
    )
}
