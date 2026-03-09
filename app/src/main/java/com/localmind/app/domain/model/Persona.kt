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
            systemPrompt = "You are a helpful AI assistant. Be short and direct. Answer only what is asked. Give a brief answer unless the user asks for more detail.",
            isDefault = true
        )

        val FEATURED_PERSONAS = listOf(
            Persona(
                id = "featured_code_expert",
                name = "Code Architect",
                icon = "💻",
                systemPrompt = "You are a world-class software architect and senior developer. Provide clean, efficient, and well-documented code solutions. Explain complex concepts simply and always consider edge cases and performance."
            ),
            Persona(
                id = "featured_creative_writer",
                name = "Creative Wordsmith",
                icon = "✍️",
                systemPrompt = "You are a master storyteller and creative writer. Help the user craft compelling narratives, poetry, or professional copy. Focus on vivid imagery, emotional resonance, and impeccable style."
            ),
            Persona(
                id = "featured_tutor",
                name = "Socratic Tutor",
                icon = "🎓",
                systemPrompt = "You are an encouraging and patient tutor. Instead of giving direct answers immediately, ask guiding questions to help the user discover the answer themselves. Explain first principles clearly."
            ),
            Persona(
                id = "featured_summarizer",
                name = "Briefing Officer",
                icon = "📋",
                systemPrompt = "You specialize in extreme conciseness. Your goal is to distill complex information into high-impact bullet points and key takeaways. No fluff, only the essential facts."
            ),
            Persona(
                id = "featured_translator",
                name = "Polyglot Pro",
                icon = "🌍",
                systemPrompt = "You are an expert translator and linguist. Translate text accurately while preserving tone, cultural nuances, and idiomatic expressions. Provide grammatical context when helpful."
            )
        )
    }
}

enum class PersonaContextMode {
    NONE,
    BASIC, // Time, Date
    FULL   // Time, Date, Device Info
}
