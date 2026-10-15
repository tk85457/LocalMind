package com.tk854.localmind.llm

// PART 1  —  Model Capability Detection
enum class ModelCapability { CHAT, CODE, REASONING }

fun detectModelCapability(modelName: String): ModelCapability {
    val lower = modelName.lowercase()
    return when {
        lower.contains("r1") || lower.contains("reason") ||
        lower.contains("deepseek") || lower.contains("qwq") ||
        lower.contains("qwen3") || lower.contains("marco-o1") ||
        lower.contains("thinking") -> ModelCapability.REASONING
        lower.contains("code") || lower.contains("coder") ||
        lower.contains("starcoder") || lower.contains("codellama") -> ModelCapability.CODE
        else -> ModelCapability.CHAT
    }
}

// PART 3  —  Token Type
enum class TokenType { THINK_START, THINK_END, REASONING, ANSWER }

// PART 4  —  Stream Chunk
data class StreamChunk(
    val reasoning: String? = null,
    val answer: String? = null,
    val isThinking: Boolean = false,
    val thinkingDurationMs: Long = 0L
)

/**
 * ReasoningParser  —  Multi-model thinking tag separator.
 *
 * Supported tag pairs (Issue #5 fix  —  model detection incomplete):
 *   DeepSeek R1  : <think>    ... </think>
 *   Qwen3        : <thinking> ... </thinking>
 *   Generic      : <thought>  ... </thought>
 *   Generic      : <reasoning>... </reasoning>
 *   Phi-4        : <analysis> ... </analysis>
 *
 * Architecture: Simple state machine with indexOf.
 * - inThinking=false → find start tag → switch to reasoning mode
 * - inThinking=true  → find end tag   → switch to answer mode
 *
 * Partial tag buffering: hold only if text ends with an incomplete
 * tag prefix (<, </, <th, etc.) and no ">" seen yet.
 */
class ReasoningParser(
    /** When true, parser STARTS in THINKING state  —  for models that
     *  output reasoning without an opening <think> tag.
     *  Everything before </think> (or any known end tag) is reasoning. */
    private val assumeThinkingFromStart: Boolean = false
) {

    // Issue #6 fix  —  ThinkingState machine
    enum class ThinkingState { IDLE, THINKING, ANSWERING, COMPLETE }

    private var state: ThinkingState = if (assumeThinkingFromStart) ThinkingState.THINKING else ThinkingState.IDLE
    private var activeStartTag: String = if (assumeThinkingFromStart) "" else ""
    private var activeEndTag: String = if (assumeThinkingFromStart) "" else ""

    private var thinkingStartMs = 0L
    private var thinkingEndMs = 0L

    val reasoningBuffer = StringBuilder()
    val answerBuffer = StringBuilder()

    // Issue #7 fix  —  answer-only token count
    var answerTokenCount = 0
        private set

    // Small hold buffer for partial tag detection
    private val holdBuffer = StringBuilder()

    val isCurrentlyThinking: Boolean
        get() = state == ThinkingState.THINKING

    // Issue #7 fix  —  thinking duration in seconds
    val thinkingDurationSec: Int
        get() {
            val endMs = if (thinkingEndMs > 0) thinkingEndMs else System.currentTimeMillis()
            return if (thinkingStartMs > 0) ((endMs - thinkingStartMs) / 1000L).toInt().coerceAtLeast(0) else 0
        }

    fun processToken(token: String): TokenType {
        holdBuffer.append(token)
        val text = holdBuffer.toString()

        // Hold if this looks like an incomplete tag prefix with no ">" yet
        if (!text.contains(">") && isLikelyPartialTagPrefix(text)) {
            return if (isCurrentlyThinking) TokenType.REASONING else TokenType.ANSWER
        }

        holdBuffer.clear()
        return processAccumulated(text)
    }

    private fun processAccumulated(text: String): TokenType {
        var remaining = text
        var lastType: TokenType = if (isCurrentlyThinking) TokenType.REASONING else TokenType.ANSWER

        while (remaining.isNotEmpty()) {
            if (state == ThinkingState.IDLE || state == ThinkingState.ANSWERING) {
                // Look for any start tag
                val match = findEarliestStartTag(remaining)
                if (match != null) {
                    val (idx, startTag, endTag) = match
                    // Everything before the tag is answer
                    if (idx > 0) {
                        answerBuffer.append(remaining.substring(0, idx))
                        answerTokenCount++
                    }
                    // Enter thinking state
                    state = ThinkingState.THINKING
                    activeStartTag = startTag
                    activeEndTag = endTag
                    if (thinkingStartMs == 0L) thinkingStartMs = System.currentTimeMillis()
                    remaining = remaining.substring(idx + startTag.length)
                    lastType = TokenType.THINK_START
                } else {
                    // No start tag  —  all answer
                    answerBuffer.append(remaining)
                    answerTokenCount++
                    if (state == ThinkingState.IDLE) state = ThinkingState.ANSWERING
                    lastType = TokenType.ANSWER
                    remaining = ""
                }
            } else {
                // THINKING state  —  look for ANY known end tag
                // When assumeThinkingFromStart=true, activeEndTag may be empty
                // so we search for all known end tags
                val endIdx: Int
                val endTagLen: Int
                if (activeEndTag.isNotEmpty()) {
                    endIdx = remaining.indexOf(activeEndTag, ignoreCase = true)
                    endTagLen = activeEndTag.length
                } else {
                    // No specific end tag set (assumeThinkingFromStart mode)
                    // Search for ANY known end tag
                    val match = findEarliestEndTag(remaining)
                    if (match != null) {
                        endIdx = match.first
                        endTagLen = match.second.length
                    } else {
                        endIdx = -1
                        endTagLen = 0
                    }
                }
                if (endIdx != -1) {
                    if (endIdx > 0) reasoningBuffer.append(remaining.substring(0, endIdx))
                    state = ThinkingState.ANSWERING
                    thinkingEndMs = System.currentTimeMillis()
                    remaining = remaining.substring(endIdx + endTagLen)
                    lastType = TokenType.THINK_END
                } else {
                    // No end tag yet  —  all reasoning
                    reasoningBuffer.append(remaining)
                    lastType = TokenType.REASONING
                    remaining = ""
                }
            }
        }

        return lastType
    }

    private data class TagMatch(val index: Int, val startTag: String, val endTag: String)

    /** Find the earliest occurring start tag in the text */
    private fun findEarliestStartTag(text: String): TagMatch? {
        var best: TagMatch? = null
        for ((start, end) in TAG_PAIRS) {
            val idx = text.indexOf(start, ignoreCase = true)
            if (idx != -1 && (best == null || idx < best.index)) {
                best = TagMatch(idx, start, end)
            }
        }
        return best
    }

    /** Find the earliest end tag (any known) in the text  —  used when activeEndTag is empty */
    private fun findEarliestEndTag(text: String): Pair<Int, String>? {
        var bestIdx = Int.MAX_VALUE
        var bestTag: String? = null
        for ((_, end) in TAG_PAIRS) {
            val idx = text.indexOf(end, ignoreCase = true)
            if (idx != -1 && idx < bestIdx) {
                bestIdx = idx
                bestTag = end
            }
        }
        return if (bestTag != null) bestIdx to bestTag else null
    }

    /**
     * Returns true only if text looks like an INCOMPLETE tag prefix.
     * E.g.: "<", "</", "<t", "<th", "<thi", "<thin", "<think", "<thinking", etc.
     * Max prefix length = len("</reasoning>") - 1 = 11 chars
     */
    private fun isLikelyPartialTagPrefix(s: String): Boolean {
        if (s.length > 12) return false
        val lower = s.lowercase()
        return ALL_START_PREFIXES.any { lower == it }
    }

    fun toStreamChunk(): StreamChunk = StreamChunk(
        reasoning = reasoningBuffer.toString().takeIf { it.isNotBlank() },
        answer = answerBuffer.toString().takeIf { it.isNotBlank() },
        isThinking = isCurrentlyThinking,
        thinkingDurationMs = if (thinkingStartMs > 0) {
            val end = if (thinkingEndMs > 0) thinkingEndMs else System.currentTimeMillis()
            end - thinkingStartMs
        } else 0L
    )

    // Issue #15 fix  —  force close unclosed tags
    fun forceCloseThinking() {
        if (isCurrentlyThinking) {
            state = ThinkingState.ANSWERING
            if (thinkingEndMs == 0L) thinkingEndMs = System.currentTimeMillis()
        }
    }

    fun reset() {
        state = if (assumeThinkingFromStart) ThinkingState.THINKING else ThinkingState.IDLE
        activeStartTag = ""
        activeEndTag = ""
        thinkingStartMs = 0L
        thinkingEndMs = 0L
        reasoningBuffer.clear()
        answerBuffer.clear()
        answerTokenCount = 0
        holdBuffer.clear()
    }

    companion object {
        // Issue #5 fix  —  all supported tag pairs
        val TAG_PAIRS = listOf(
            "<think>" to "</think>",
            "<thinking>" to "</thinking>",
            "<thought>" to "</thought>",
            "<reasoning>" to "</reasoning>",
            "<analysis>" to "</analysis>"
        )

        // All partial prefixes that could be the start of a tag
        // Generated from TAG_PAIRS start tags
        private val ALL_START_PREFIXES: Set<String> = buildSet {
            for ((start, _) in TAG_PAIRS) {
                // Add all prefixes including close tag prefixes
                for (i in 1 until start.length) add(start.substring(0, i))
                val closeStart = "</" + start.substring(1)
                for (i in 1 until closeStart.length) add(closeStart.substring(0, i))
            }
        }

        // Keep these for backward compat
        const val THINK_START_TAG = "<think>"
        const val THINK_END_TAG = "</think>"
    }
}
