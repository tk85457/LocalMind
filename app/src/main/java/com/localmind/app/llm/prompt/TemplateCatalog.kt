package com.localmind.app.llm.prompt

data class TemplateSpec(
    val id: String,
    val family: TemplateFamily,
    val defaultSystemPrompt: String,
    val defaultStopTokens: List<String>
)

enum class TemplateFamily {
    LLAMA3,
    PHI3,
    QWEN,
    GEMMA,
    CHATML,
    DEEPSEEK,  // DeepSeek-R1 reasoning
    QWQ        // QwQ reasoning
}

object TemplateCatalog {
    const val TEMPLATE_LLAMA3 = "llama3"
    const val TEMPLATE_LLAMA32 = "llama32"
    const val TEMPLATE_PHI3 = "phi3"
    const val TEMPLATE_QWEN2 = "qwen2"
    const val TEMPLATE_QWEN25 = "qwen25"
    const val TEMPLATE_GEMMA = "gemma"
    const val TEMPLATE_GEMMA2 = "gemma2"
    const val TEMPLATE_CHATML_DEFAULT = "chatml_default"
    const val TEMPLATE_DEEPSEEK_R1 = "deepseek_r1"
    const val TEMPLATE_QWQ = "qwq"

    private val templates = mapOf(
        TEMPLATE_LLAMA3 to TemplateSpec(
            id = TEMPLATE_LLAMA3,
            family = TemplateFamily.LLAMA3,
            defaultSystemPrompt = "", // PERF: no default — persona se aata hai
            defaultStopTokens = listOf("<|eot_id|>", "<|end_of_text|>")
        ),
        TEMPLATE_LLAMA32 to TemplateSpec(
            id = TEMPLATE_LLAMA32,
            family = TemplateFamily.LLAMA3,
            defaultSystemPrompt = "", // PERF: no default — persona se aata hai
            // Llama-3.2 kadhi kadhi Gemma-style tags bhi generate karta hai
            defaultStopTokens = listOf("<|eot_id|>", "<|end_of_text|>", "<start_of_turn>", "</start_of_turn>", "<end_of_turn>")
        ),
        TEMPLATE_PHI3 to TemplateSpec(
            id = TEMPLATE_PHI3,
            family = TemplateFamily.PHI3,
            defaultSystemPrompt = "", // PERF: no default — persona se aata hai
            defaultStopTokens = listOf("<|end|>")
        ),
        TEMPLATE_QWEN2 to TemplateSpec(
            id = TEMPLATE_QWEN2,
            family = TemplateFamily.QWEN,
            defaultSystemPrompt = "", // PERF: no default — persona se aata hai
            defaultStopTokens = listOf("<|im_end|>", "<|endoftext|>")
        ),
        TEMPLATE_QWEN25 to TemplateSpec(
            id = TEMPLATE_QWEN25,
            family = TemplateFamily.QWEN,
            defaultSystemPrompt = "", // PERF: no default — persona se aata hai
            defaultStopTokens = listOf("<|im_end|>", "<|endoftext|>")
        ),
        TEMPLATE_GEMMA to TemplateSpec(
            id = TEMPLATE_GEMMA,
            family = TemplateFamily.GEMMA,
            defaultSystemPrompt = "", // PERF: no default — persona se aata hai
            // POCKETPAL FIX: Complete Gemma stop tokens
            defaultStopTokens = listOf("<end_of_turn>", "<|end_of_text|>", "<eos>", "<|endoftext|>")
        ),
        TEMPLATE_GEMMA2 to TemplateSpec(
            id = TEMPLATE_GEMMA2,
            family = TemplateFamily.GEMMA,
            defaultSystemPrompt = "", // PERF: no default — persona se aata hai
            // POCKETPAL FIX: Complete Gemma2 stop tokens
            defaultStopTokens = listOf("<end_of_turn>", "<|end_of_text|>", "<eos>", "<|endoftext|>")
        ),
        TEMPLATE_CHATML_DEFAULT to TemplateSpec(
            id = TEMPLATE_CHATML_DEFAULT,
            family = TemplateFamily.CHATML,
            defaultSystemPrompt = "", // PERF: no default — persona se aata hai
            defaultStopTokens = listOf("<|im_end|>", "</s>")
        ),
        // POCKETPAL FIX: DeepSeek-R1 reasoning model support
        TEMPLATE_DEEPSEEK_R1 to TemplateSpec(
            id = TEMPLATE_DEEPSEEK_R1,
            family = TemplateFamily.DEEPSEEK,
            defaultSystemPrompt = "", // PERF: no default — persona se aata hai
            // DeepSeek R1 Qwen-based: Qwen2 tokenizer EOS tokens
            defaultStopTokens = listOf("<|im_end|>", "<|endoftext|>", "</s>")
        ),
        // POCKETPAL FIX: QwQ reasoning model support
        TEMPLATE_QWQ to TemplateSpec(
            id = TEMPLATE_QWQ,
            family = TemplateFamily.QWQ,
            defaultSystemPrompt = "", // PERF: no default — persona se aata hai
            defaultStopTokens = listOf("<|im_end|>", "</s>", "<|endoftext|>")
        )
    )

    // POCKETPAL FIX: Complete stop tokens list (from PocketPal chat.ts)
    val safeFallbackStops: List<String> = listOf(
        "</s>",
        "<|eot_id|>",
        "<|end_of_text|>",
        "<|im_end|>",
        "<|EOT|>",
        "<|END_OF_TURN_TOKEN|>",
        "<|end_of_turn|>",
        "<end_of_turn>",
        "</end_of_turn>",
        "<start_of_turn>",
        "</start_of_turn>",
        "<|endoftext|>",
        "<|end|>",
        "<|return|>"
    )

    fun get(templateId: String?): TemplateSpec {
        if (templateId.isNullOrBlank()) return templates.getValue(TEMPLATE_CHATML_DEFAULT)
        return templates[templateId] ?: templates.getValue(TEMPLATE_CHATML_DEFAULT)
    }

    fun inferTemplateId(modelNameHint: String, repoIdHint: String?): String {
        val normalized = buildString {
            append(modelNameHint.lowercase())
            if (!repoIdHint.isNullOrBlank()) {
                append(" ")
                append(repoIdHint.lowercase())
            }
        }
        return when {
            normalized.contains("llama-3.2") || normalized.contains("llama3.2") || normalized.contains("llama32") ->
                TEMPLATE_LLAMA32
            normalized.contains("llama-3") || normalized.contains("llama3") -> TEMPLATE_LLAMA3
            normalized.contains("phi-3") || normalized.contains("phi3") || normalized.contains("phi-2") -> TEMPLATE_PHI3
            normalized.contains("qwen2.5") || normalized.contains("qwen-2.5") -> TEMPLATE_QWEN25
            normalized.contains("qwen2") || normalized.contains("qwen-2") -> TEMPLATE_QWEN2
            normalized.contains("qwen") -> TEMPLATE_QWEN25
            normalized.contains("gemma2") || normalized.contains("gemma-2") -> TEMPLATE_GEMMA2
            normalized.contains("gemma") -> TEMPLATE_GEMMA
            // POCKETPAL FIX: Reasoning model detection
            normalized.contains("deepseek-r1") || normalized.contains("deepseek_r1") -> TEMPLATE_DEEPSEEK_R1
            normalized.contains("qwq") -> TEMPLATE_QWQ
            normalized.contains("deepseek") -> TEMPLATE_DEEPSEEK_R1
            else -> TEMPLATE_CHATML_DEFAULT
        }
    }
}
