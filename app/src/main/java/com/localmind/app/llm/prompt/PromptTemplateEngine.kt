package com.localmind.app.llm.prompt

import com.localmind.app.domain.model.Message
import com.localmind.app.domain.model.MessageRole
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptTemplateEngine @Inject constructor() {
    fun buildPrompt(
        family: TemplateFamily,
        systemPrompt: String,
        history: List<Message>,
        currentUserInput: String,
        bosEnabled: Boolean = true,
        eosEnabled: Boolean = true,
        addGenPrompt: Boolean = true
    ): String {
        val builder = StringBuilder()
        when (family) {
            TemplateFamily.LLAMA3 -> {
                if (bosEnabled) builder.append("<|begin_of_text|>")
                appendLlama3Message(builder, "system", systemPrompt)
                history.forEach { msg ->
                    appendLlama3Message(builder, msg.role.toTemplateRole(), msg.content)
                }
                builder.append("<|start_header_id|>user<|end_header_id|>\n")
                builder.append(currentUserInput.trim())
                if (eosEnabled) builder.append("\n<|eot_id|>")
                if (addGenPrompt) builder.append("<|start_header_id|>assistant<|end_header_id|>\n")
            }

            TemplateFamily.PHI3 -> {
                appendPhi3Message(builder, "system", systemPrompt)
                history.forEach { msg ->
                    appendPhi3Message(builder, msg.role.toTemplateRole(), msg.content)
                }
                appendPhi3Message(builder, "user", currentUserInput.trim())
                if (addGenPrompt) builder.append("<|assistant|>\n")
            }

            TemplateFamily.QWEN,
            TemplateFamily.CHATML -> {
                appendChatMlMessage(builder, "system", systemPrompt)
                history.forEach { msg ->
                    appendChatMlMessage(builder, msg.role.toTemplateRole(), msg.content)
                }
                appendChatMlMessage(builder, "user", currentUserInput.trim())
                if (addGenPrompt) builder.append("<|im_start|>assistant\n")
            }

            TemplateFamily.GEMMA -> {
                if (history.isEmpty()) {
                    val combined = if (systemPrompt.isNotBlank()) "$systemPrompt\n\n$currentUserInput" else currentUserInput
                    appendGemmaMessage(builder, "user", combined)
                } else {
                    var systemPromptApplied = false
                    history.forEach { msg ->
                        val gemmaRole = if (msg.role == MessageRole.USER) "user" else "model"
                        val content = if (!systemPromptApplied && msg.role == MessageRole.USER) {
                            systemPromptApplied = true
                            "$systemPrompt\n\n${msg.content}"
                        } else msg.content
                        appendGemmaMessage(builder, gemmaRole, content)
                    }
                    if (!systemPromptApplied) {
                        appendGemmaMessage(builder, "user", systemPrompt)
                    }
                    appendGemmaMessage(builder, "user", currentUserInput.trim())
                }
                if (addGenPrompt) builder.append("<start_of_turn>model\n")
            }
        }
        return builder.toString()
    }

    private fun appendLlama3Message(builder: StringBuilder, role: String, content: String) {
        builder.append("<|start_header_id|>")
            .append(role)
            .append("<|end_header_id|>\n")
            .append(content.trim())
            .append("\n<|eot_id|>")
    }

    private fun appendPhi3Message(builder: StringBuilder, role: String, content: String) {
        builder.append("<|").append(role).append("|>\n")
            .append(content.trim())
            .append("\n<|end|>\n")
    }

    private fun appendChatMlMessage(builder: StringBuilder, role: String, content: String) {
        builder.append("<|im_start|>").append(role).append("\n")
            .append(content.trim())
            .append("\n<|im_end|>\n")
    }

    private fun appendGemmaMessage(builder: StringBuilder, role: String, content: String) {
        builder.append("<start_of_turn>").append(role).append("\n")
            .append(content.trim())
            .append("\n<end_of_turn>\n")
    }

    private fun MessageRole.toTemplateRole(): String {
        return if (this == MessageRole.USER) "user" else "assistant"
    }
}

