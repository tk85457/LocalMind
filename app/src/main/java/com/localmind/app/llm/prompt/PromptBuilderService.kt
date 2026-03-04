package com.localmind.app.llm.prompt

import com.localmind.app.core.performance.DeviceProfileManager
import com.localmind.app.domain.model.Message
import com.localmind.app.domain.model.Model
import javax.inject.Inject
import javax.inject.Singleton

data class PromptBuildRequest(
    val model: Model,
    val historyMessages: List<Message>,
    val currentUserBlock: String,
    val tunedContextSize: Int,
    val explicitSystemPrompt: String,
    val fallbackSystemPrompt: String,
    val currentSummary: String? = null
)

data class PromptBuildOutput(
    val prompt: String,
    val stopTokens: List<String>
)

@Singleton
class PromptBuilderService @Inject constructor(
    private val templateResolver: TemplateResolver,
    private val promptTemplateEngine: PromptTemplateEngine,
    private val deviceProfileManager: DeviceProfileManager
) {
    fun buildPrompt(request: PromptBuildRequest): PromptBuildOutput {
        val ramGb = deviceProfileManager.currentProfile().totalRamGb
        // PERF FIX: Generous generation reserve = less prompt = faster prefill = lower TTFT
        val generationReserveRatio = when {
            ramGb <= 3 -> 0.60f  // PERF: Tiny context, reserve more for generation
            ramGb <= 4 -> 0.50f
            ramGb <= 6 -> 0.40f
            else -> 0.30f
        }
        val generationReserveTokens = (request.tunedContextSize * generationReserveRatio)
            .toInt()
            .coerceAtLeast(if (ramGb <= 4) 128 else 128)
        val inputBudgetTokens = (request.tunedContextSize - generationReserveTokens).coerceAtLeast(128)

        val resolvedProfile = templateResolver.resolve(
            model = request.model,
            explicitSystemPrompt = request.explicitSystemPrompt
        )

        val effectiveSystemPrompt = resolvedProfile.systemPrompt
        val fixedTokens = estimateTokens(effectiveSystemPrompt) + estimateTokens(request.currentUserBlock)
        var remainingTokens = (inputBudgetTokens - fixedTokens).coerceAtLeast(0)

        // Summary is dynamic context, place it at the end of history to keep prefix stable
        val summaryMessage = if (!request.currentSummary.isNullOrBlank()) {
            Message(
                id = "summary",
                conversationId = "",
                role = com.localmind.app.domain.model.MessageRole.ASSISTANT, // Treat as context/memo
                content = "CONTEXT SUMMARY: ${request.currentSummary}",
                timestamp = 0,
                tokenCount = null
            )
        } else null

        if (summaryMessage != null) {
            remainingTokens -= estimateTokens(summaryMessage.content)
        }

        val selectedNewestFirst = mutableListOf<Message>()
        for (message in request.historyMessages.asReversed()) {
            val entry = message.content.trim()
            if (entry.isBlank()) continue

            val entryTokens = estimateTokens(entry)
            if (entryTokens <= remainingTokens) {
                selectedNewestFirst += message
                remainingTokens -= entryTokens
                continue
            }

            if (remainingTokens >= 16) {
                val charBudget = remainingTokens * 4
                val trimmed = entry.take(charBudget).trim()
                if (trimmed.isNotBlank()) {
                    selectedNewestFirst += message.copy(content = trimmed)
                }
            }
            break
        }

        var selectedHistory: MutableList<Message> = selectedNewestFirst.asReversed().toMutableList()
        if (summaryMessage != null) {
            selectedHistory.add(0, summaryMessage) // Place at start for context stability
        }
        var currentUserBlock = request.currentUserBlock

        fun renderPrompt(history: List<Message>, userInput: String): String {
            return promptTemplateEngine.buildPrompt(
                family = resolvedProfile.family,
                systemPrompt = resolvedProfile.systemPrompt.ifBlank { request.fallbackSystemPrompt },
                history = history,
                currentUserInput = userInput,
                bosEnabled = request.model.bosEnabled,
                eosEnabled = request.model.eosEnabled,
                addGenPrompt = request.model.addGenPrompt
            )
        }

        fun isWithinBudget(prompt: String): Boolean {
            return estimateTokens(prompt) <= inputBudgetTokens
        }

        var prompt = renderPrompt(selectedHistory, currentUserBlock)

        if (!isWithinBudget(prompt)) {
            val historyToTrim = selectedHistory.toMutableList()
            while (historyToTrim.isNotEmpty() && !isWithinBudget(prompt)) {
                historyToTrim.removeAt(0)
                prompt = renderPrompt(historyToTrim, currentUserBlock)
            }
            selectedHistory = historyToTrim.toMutableList()
        }

        if (!isWithinBudget(prompt)) {
            var currentBudgetTokens = estimateTokens(currentUserBlock)
            while (currentBudgetTokens > 32 && !isWithinBudget(prompt)) {
                currentBudgetTokens = (currentBudgetTokens - 16).coerceAtLeast(32)
                currentUserBlock = trimToTokenBudget(currentUserBlock, currentBudgetTokens)
                prompt = renderPrompt(selectedHistory, currentUserBlock)
            }
        }

        return PromptBuildOutput(
            prompt = prompt,
            stopTokens = resolvedProfile.stopTokens
        )
    }

    private fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        return (text.length / 4).coerceAtLeast(1)
    }

    private fun trimToTokenBudget(text: String, tokenBudget: Int): String {
        if (text.isBlank()) return text
        val safeBudget = tokenBudget.coerceAtLeast(16)
        val maxChars = safeBudget * 4
        if (text.length <= maxChars) {
            return text
        }
        return text.takeLast(maxChars).trimStart()
    }

}
