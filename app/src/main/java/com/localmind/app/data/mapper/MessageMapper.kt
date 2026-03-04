package com.localmind.app.data.mapper

import com.localmind.app.data.local.entity.MessageEntity
import com.localmind.app.domain.model.Message
import com.localmind.app.domain.model.MessageRole

fun MessageEntity.toDomain(): Message {
    return Message(
        id = id,
        conversationId = conversationId,
        role = MessageRole.fromApiString(role),
        content = content,
        timestamp = timestamp,
        tokenCount = tokenCount,
        imageUri = imageUri,
        reasoningContent = reasoningContent,
        inferenceSource = inferenceSource,
        ttftMs = ttftMs,
        generationMs = generationMs,
        tokensPerSecond = tokensPerSecond
    )
}

fun Message.toEntity(): MessageEntity {
    return MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role.toApiString(),
        content = content,
        timestamp = timestamp,
        tokenCount = tokenCount,
        imageUri = imageUri,
        reasoningContent = reasoningContent,
        inferenceSource = inferenceSource,
        ttftMs = ttftMs,
        generationMs = generationMs,
        tokensPerSecond = tokensPerSecond
    )
}
