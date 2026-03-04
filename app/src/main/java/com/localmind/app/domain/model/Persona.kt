package com.localmind.app.domain.model

import java.util.UUID

// NOTE: @Entity belongs only on PersonaEntity (in data/local/entity).
// Domain models must NOT be annotated with Room annotations.
data class Persona(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String, // Emoji support
    val systemPrompt: String,
    val contextMode: PersonaContextMode = PersonaContextMode.NONE,
    val staticContext: String = "",
    val isDefault: Boolean = false,
    val preferredModelId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        val DEFAULT_ASSISTANT = Persona(
            id = "default_assistant",
            name = "Local Mind AI",
            icon = "🤖",
            systemPrompt = "You are Local Mind, a helpful and efficient local AI model. You run entirely offline on the user's phone. Be concise, accurate, and helpful.",
            isDefault = true
        )
    }
}

enum class PersonaContextMode {
    NONE,
    BASIC, // Time, Date
    FULL   // Time, Date, Device Info
}
