package com.localmind.app.core.utils

import java.util.Locale

object ConversationTitleGenerator {
    private const val DEFAULT_TITLE = "New Chat"
    private const val MAX_WORDS = 7
    private const val MAX_CHARS = 40

    private val stopWords = setOf(
        "the", "and", "for", "with", "that", "this", "from", "your", "have",
        "about", "please", "need", "want", "make", "what", "when", "where",
        "can", "could", "should", "will", "are", "you", "how", "into"
    )

    fun generate(firstUserMessage: String): String {
        val normalized = firstUserMessage
            .replace(Regex("`[^`]*`"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) {
            return DEFAULT_TITLE
        }

        val words = normalized
            .split(" ")
            .map { it.trim().trim(',', '.', ':', ';', '!', '?', '"', '\'') }
            .filter { it.isNotBlank() }

        if (words.isEmpty()) {
            return DEFAULT_TITLE
        }

        val keywords = words.filter { word ->
            val lower = word.lowercase(Locale.US)
            lower.length >= 3 && lower !in stopWords
        }

        val chosen = (if (keywords.size >= 3) keywords else words)
            .take(MAX_WORDS)
            .joinToString(" ")
            .trim()

        val capped = chosen.take(MAX_CHARS).trim()
        if (capped.isBlank()) return DEFAULT_TITLE

        return capped.replaceFirstChar { first ->
            if (first.isLowerCase()) first.titlecase(Locale.US) else first.toString()
        }
    }
}
