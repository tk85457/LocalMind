package com.tk854.localmind.domain.usecase

import javax.inject.Inject

class PromptSanitizationUseCase @Inject constructor() {

    companion object {
        // Pre-compiled regex patterns  —  avoids creating new Regex on every token callback
        private val TAG_PATTERN = Regex("<\\|.*?\\|>")
        private val DANGLING_TAG_OPENER = Regex("<\\|[^\\s\\n]*")
        private val PARTIAL_TAG_SUFFIX = Regex("\\b(?:start_header_id|end_header_id|eot_id|begin_of_text|im_start|im_end)\\|?")
        private val WORD_ID_PATTERN = Regex("\\b\\w+_id\\|")

        // NOTE: ANGLE_BRACKET_TAG_PATTERN is NOT used anymore to avoid stripping <think> etc.

        private val TURN_SEPARATOR_PATTERN = Regex(
            "(?:<\\|im_end\\|>|<\\|im_start\\|>)" +
            "[\\s\\S]*?" +
            "(?:user|assistant|system)" +
            "[\\s\\S]*?" +
            "(?=>|\\n|$)",
            setOf(RegexOption.IGNORE_CASE)
        )

        private val TRAILING_TURN_MARKER = Regex(
            "(?s)" +
            "(" +
            "<\\s*\\|\\s*(?:end\\s+of\\s+sentence|im_end|eot_id|end_header_id)\\s*\\|\\s*>" +
            "|" +
            "<\\|\\s*(?:User|Assistant|user|assistant|system)\\s*\\|>" +
            ")" +
            "[\\s\\S]*$"
        )

        private val TRAILING_SMOLVLM_TURN = Regex(
            "(?is)<end_of_utterance>\\s*(?:user|assistant|system)\\s*:.*$"
        )
    }

    fun stripStreamingTags(text: String): String {
        // Sirf pipe-style special tokens strip karo (<|im_end|> etc.)
        // <think>, <thinking>, <thought>, <reasoning>  —  KABHI STRIP MAT KARO
        var result = TAG_PATTERN.replace(text, "")
        result = result
            .replace("<start_of_turn>", "")
            .replace("</start_of_turn>", "")
            .replace("<end_of_turn>", "")
            .replace("</end_of_turn>", "")
            .replace("<end_of_utterance>", "")
            .replace("<|end|>", "")
        return stripDanglingTagArtifacts(result, trim = false)
    }

    private fun stripDanglingTagArtifacts(text: String, trim: Boolean = true): String {
        val replaced = text
            .replace(DANGLING_TAG_OPENER, "")
            .replace("|>", "")
            .replace(PARTIAL_TAG_SUFFIX, "")
            .replace(WORD_ID_PATTERN, "")

        return if (trim) replaced.trim() else replaced
    }

    private fun trimStopSequences(text: String, stopTokens: List<String>): String {
        if (text.isBlank() || stopTokens.isEmpty()) return text
        var result = text
        var changed: Boolean
        do {
            changed = false
            stopTokens.forEach { stop ->
                if (stop.isBlank()) return@forEach
                if (result.endsWith(stop)) {
                    result = result.removeSuffix(stop).trimEnd()
                    changed = true
                }
            }
        } while (changed)

        return stripDanglingTagArtifacts(
            result
            .replace("<|assistant|>", "")
            .replace("<|im_end|>", "")
            .replace("<|eot_id|>", "")
            .replace("<end_of_utterance>", "")
        ).trim()
    }

    fun sanitizeAssistantReply(rawText: String, stopTokens: List<String>): String {
        if (rawText.isBlank()) return ""
        val originalTrimmed = rawText.trim()

        // <think> / reasoning tags  —  STRIP MAT KARO
        // LLMEngine already reasoning aur answer alag emit karta hai.
        // answerBuffer mein kabhi <think> content nahi hona chahiye.
        // Agar kisi edge case mein aa bhi jaaye toh user ko dikhne do.

        val preStripped = TRAILING_TURN_MARKER.replace(originalTrimmed, "").trim()

        val spacedPipeStripped = preStripped
            .replace(Regex("<\\s*\\|\\s*[^|>]{0,40}\\s*\\|\\s*>"), "")
            .replace(Regex("\\|\\s*end\\s+of\\s+sentence\\s*\\|"), "")
            .replace(Regex("\\|\\s*User\\s*\\|"), "")
            .replace(Regex("\\|\\s*Assistant\\s*\\|"), "")
            .replace(TRAILING_SMOLVLM_TURN, "")
            .trim()

        var text = trimStopSequences(spacedPipeStripped, stopTokens)

        // Strip ONLY pipe-style chat template tokens  —  NOT angle-bracket reasoning tags
        text = text
            .replace("<|begin_of_text|>", "")
            .replace("<|start_header_id|>", "")
            .replace("<|end_header_id|>", "")
            .replace("<|assistant|>", "")
            .replace("<|user|>", "")
            .replace("<|im_start|>", "")
            .replace("<|im_end|>", "")
            .replace("<|eot_id|>", "")
            .replace("<start_of_turn>", "")
            .replace("</start_of_turn>", "")
            .replace("<end_of_turn>", "")
            .replace("</end_of_turn>", "")
            .replace("<end_of_utterance>", "")
            .replace("<|end|>", "")
            .replace("<|endoftext|>", "")

        text = stripDanglingTagArtifacts(text)

        // Role prefix  —  sirf pehli line pe
        val lines = text.lines().toMutableList()
        if (lines.isNotEmpty()) {
            val firstLine = lines[0].trim()
            val roleOnlyFirst = Regex("(?i)^(assistant|user|model|system)\\s*[:\\-]?\\s*$")
            if (roleOnlyFirst.matches(firstLine)) {
                lines.removeAt(0)
            } else {
                val rolePrefixFirst = Regex("(?i)^(assistant|user|model|system)\\s*[:\\-]\\s*")
                val stripped = rolePrefixFirst.replace(firstLine, "")
                if (stripped != firstLine) lines[0] = stripped
            }
        }
        text = lines.joinToString("\n").trim()

        if (text.isNotBlank()) return text.trim()

        // Fallback  —  <think> tags bhi NAHI strip karein
        val fallbackLines = originalTrimmed
            .replace(Regex("<\\|[^>]+\\|>"), " ")
            .replace("<start_of_turn>", " ")
            .replace("</start_of_turn>", " ")
            .replace("<end_of_turn>", " ")
            .replace("</end_of_turn>", " ")
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .lines().toMutableList()
        if (fallbackLines.isNotEmpty()) {
            val fl = fallbackLines[0].trim()
            val stripped = Regex("(?i)^(assistant|user|model|system)\\s*[:\\-]\\s*").replace(fl, "")
            if (stripped != fl) fallbackLines[0] = stripped
        }
        return stripDanglingTagArtifacts(fallbackLines.joinToString("\n")).trim()
    }
}
