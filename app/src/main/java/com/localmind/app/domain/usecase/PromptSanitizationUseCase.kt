package com.localmind.app.domain.usecase

import javax.inject.Inject

class PromptSanitizationUseCase @Inject constructor() {

    companion object {
        // Pre-compiled regex patterns — avoids creating new Regex on every token callback
        private val TAG_PATTERN = Regex("<\\|.*?\\|>")
        private val DANGLING_TAG_OPENER = Regex("<\\|[^\\s\\n]*")
        private val PARTIAL_TAG_SUFFIX = Regex("\\b(?:start_header_id|end_header_id|eot_id|begin_of_text|im_start|im_end|assistant|system|user)\\|?")
        private val WORD_ID_PATTERN = Regex("\\b\\w+_id\\|")
    }

    fun stripStreamingTags(text: String): String {
        return stripDanglingTagArtifacts(TAG_PATTERN.replace(text, ""), trim = false)
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
            .replace("<end_of_turn>", "")
            .replace("<think>", "")
            .replace("</think>", "")
        text = stripDanglingTagArtifacts(text)

        val rolePrefixRegex = Regex("(?im)^\\s*(assistant|user|model|system)\\s*[:\\-]\\s*")
        text = rolePrefixRegex.replace(text, "")

        val roleOnlyLineRegex =
            Regex("(?i)^\\s*(assistant|user|model|system)(\\s+(assistant|user|model|system))*\\s*$")
        text = text
            .lines()
            .map { it.trimEnd() }
            .filterNot { line -> roleOnlyLineRegex.matches(line) }
            .joinToString("\n")
            .trim()

        if (text.isNotBlank()) {
            return text.trim()
        }

        // Fallback: keep all usable visible text even if tag sanitization consumed too much.
        return stripDanglingTagArtifacts(
            originalTrimmed
            .replace(Regex("<\\|[^>]+\\|>"), " ")
            .replace("<start_of_turn>", " ")
            .replace("<end_of_turn>", " ")
            .replace("<think>", " ")
            .replace("</think>", " ")
            .replace(Regex("(?im)^\\s*(assistant|user|model|system)\\s*[:\\-]\\s*"), "")
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
        ).trim()
    }
}
