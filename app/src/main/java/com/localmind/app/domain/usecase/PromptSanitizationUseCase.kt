package com.localmind.app.domain.usecase

import javax.inject.Inject

class PromptSanitizationUseCase @Inject constructor() {

    companion object {
        // Pre-compiled regex patterns — avoids creating new Regex on every token callback
        private val TAG_PATTERN = Regex("<\\|.*?\\|>")
        private val DANGLING_TAG_OPENER = Regex("<\\|[^\\s\\n]*")
        private val PARTIAL_TAG_SUFFIX = Regex("\\b(?:start_header_id|end_header_id|eot_id|begin_of_text|im_start|im_end)\\|?")
        private val WORD_ID_PATTERN = Regex("\\b\\w+_id\\|")
        // Any <word_of_word> or </word_of_word> style chat template tags
        private val ANGLE_BRACKET_TAG_PATTERN = Regex("</?[a-zA-Z][a-zA-Z0-9_]*(?:_of_[a-zA-Z0-9_]+)*>")
    }

    fun stripStreamingTags(text: String): String {
        var result = TAG_PATTERN.replace(text, "")
        // Also strip angle-bracket chat template tags during streaming
        result = result
            .replace("<start_of_turn>", "")
            .replace("</start_of_turn>", "")
            .replace("<end_of_turn>", "")
            .replace("</end_of_turn>", "")
            .replace("<|end|>", "")
        result = ANGLE_BRACKET_TAG_PATTERN.replace(result, "")
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
        ).trim()
    }

    fun sanitizeAssistantReply(rawText: String, stopTokens: List<String>): String {
        if (rawText.isBlank()) return ""
        val originalTrimmed = rawText.trim()
        var text = trimStopSequences(originalTrimmed, stopTokens)
        // Strip all known chat template tags (pipe-style and angle-bracket-style)
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
            .replace("<think>", "")
            .replace("</think>", "")
            .replace("<|end|>", "")
            .replace("<|endoftext|>", "")
        // Strip any remaining <word> or </word> angle-bracket template tags
        text = ANGLE_BRACKET_TAG_PATTERN.replace(text, "")
        text = stripDanglingTagArtifacts(text)

        // BUG FIX: Role prefix/line regex sirf response ke SHURU mein apply karo.
        // Poori response mein apply karne se "The user said..." ya "As an assistant..."
        // jaisi valid lines delete ho jaati thin.
        val lines = text.lines().toMutableList()
        // Sirf pehli line pe role prefix check karo (agar woh sirf role label hai)
        if (lines.isNotEmpty()) {
            val firstLine = lines[0].trim()
            val roleOnlyFirst = Regex("(?i)^(assistant|user|model|system)\\s*[:\\-]?\\s*$")
            if (roleOnlyFirst.matches(firstLine)) {
                lines.removeAt(0)
            } else {
                // Role: prefix ke baad content ho toh sirf prefix hatao
                val rolePrefixFirst = Regex("(?i)^(assistant|user|model|system)\\s*[:\\-]\\s*")
                val stripped = rolePrefixFirst.replace(firstLine, "")
                if (stripped != firstLine) lines[0] = stripped
            }
        }
        text = lines.joinToString("\n").trim()

        if (text.isNotBlank()) {
            return text.trim()
        }

        // Fallback: keep all usable visible text even if tag sanitization consumed too much.
        // BUG FIX: role prefix regex sirf pehli line pe — poori response mein nahi.
        val fallbackLines = originalTrimmed
            .replace(Regex("<\\|[^>]+\\|>"), " ")
            .replace("<start_of_turn>", " ")
            .replace("</start_of_turn>", " ")
            .replace("<end_of_turn>", " ")
            .replace("</end_of_turn>", " ")
            .replace("<think>", " ")
            .replace("</think>", " ")
            .let { ANGLE_BRACKET_TAG_PATTERN.replace(it, " ") }
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
