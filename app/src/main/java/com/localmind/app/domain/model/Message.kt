package com.localmind.app.domain.model

data class Message(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val tokenCount: Int?,
    val imageUri: String? = null,
    val reasoningContent: String? = null,
    val inferenceSource: String? = null,
    val ttftMs: Long? = null,
    val generationMs: Long? = null,
    val tokensPerSecond: Float? = null
)

enum class MessageRole {
    USER,
    ASSISTANT;

    fun toApiString(): String = name.lowercase()

    companion object {
        fun fromApiString(value: String): MessageRole {
            return when (value.lowercase().trim()) {
                "user" -> USER
                "assistant", "model", "ai", "bot", "system" -> ASSISTANT
                else -> ASSISTANT // Safe fallback — never crash on unknown role
            }
        }
    }
}
