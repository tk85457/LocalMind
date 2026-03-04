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
    CHATML
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

    private val templates = mapOf(
        TEMPLATE_LLAMA3 to TemplateSpec(
            id = TEMPLATE_LLAMA3,
            family = TemplateFamily.LLAMA3,
            defaultSystemPrompt = "You are a helpful model.",
            defaultStopTokens = listOf("<|eot_id|>", "<|end_of_text|>")
        ),
        TEMPLATE_LLAMA32 to TemplateSpec(
            id = TEMPLATE_LLAMA32,
            family = TemplateFamily.LLAMA3,
            defaultSystemPrompt = "You are a helpful model.",
            defaultStopTokens = listOf("<|eot_id|>", "<|end_of_text|>")
        ),
        TEMPLATE_PHI3 to TemplateSpec(
            id = TEMPLATE_PHI3,
            family = TemplateFamily.PHI3,
            defaultSystemPrompt = "You are a concise and accurate model.",
            defaultStopTokens = listOf("<|end|>")
        ),
        TEMPLATE_QWEN2 to TemplateSpec(
            id = TEMPLATE_QWEN2,
            family = TemplateFamily.QWEN,
            defaultSystemPrompt = "You are Qwen, a helpful model.",
            defaultStopTokens = listOf("<|im_end|>", "<|endoftext|>")
        ),
        TEMPLATE_QWEN25 to TemplateSpec(
            id = TEMPLATE_QWEN25,
            family = TemplateFamily.QWEN,
            defaultSystemPrompt = "You are Qwen, a helpful model.",
            defaultStopTokens = listOf("<|im_end|>", "<|endoftext|>")
        ),
        TEMPLATE_GEMMA to TemplateSpec(
            id = TEMPLATE_GEMMA,
            family = TemplateFamily.GEMMA,
            defaultSystemPrompt = "You are a helpful model.",
            defaultStopTokens = listOf("<end_of_turn>", "<|end_of_text|>")
        ),
        TEMPLATE_GEMMA2 to TemplateSpec(
            id = TEMPLATE_GEMMA2,
            family = TemplateFamily.GEMMA,
            defaultSystemPrompt = "You are a helpful model.",
            defaultStopTokens = listOf("<end_of_turn>", "<|end_of_text|>")
        ),
        TEMPLATE_CHATML_DEFAULT to TemplateSpec(
            id = TEMPLATE_CHATML_DEFAULT,
            family = TemplateFamily.CHATML,
            defaultSystemPrompt = "You are a helpful model.",
            defaultStopTokens = listOf("<|im_end|>", "</s>")
        )
    )

    val safeFallbackStops: List<String> = listOf(
        "</s>",
        "<|eot_id|>",
        "<|end_of_text|>",
        "<|im_end|>",
        "<end_of_turn>"
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
            else -> TEMPLATE_CHATML_DEFAULT
        }
    }
}
