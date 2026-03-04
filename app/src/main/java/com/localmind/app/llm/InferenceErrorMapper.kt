package com.localmind.app.llm

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InferenceErrorMapper @Inject constructor() {
    fun map(throwable: Throwable?): String {
        val message = throwable?.message.orEmpty()
        return when {
            message.contains("auth", ignoreCase = true) ||
                message.contains("401", ignoreCase = true) ||
                message.contains("403", ignoreCase = true) -> {
                "Authentication failed. Check your token settings."
            }
            message.contains("storage", ignoreCase = true) ||
                message.contains("disk", ignoreCase = true) -> {
                "Storage issue detected. Free up space and retry."
            }
            message.contains("Prompt too long for context", ignoreCase = true) ||
                message.contains("Failed to evaluate prompt", ignoreCase = true) -> {
                "Prompt exceeded context budget. Try a shorter prompt."
            }
            message.contains("Model not loaded", ignoreCase = true) ||
                message.contains("No model loaded", ignoreCase = true) -> {
                "Model is not ready. Activate a model first."
            }
            message.contains("timeout", ignoreCase = true) ||
                message.contains("timed out", ignoreCase = true) -> {
                "Inference timed out. Retry with lower max tokens."
            }
            message.contains("network", ignoreCase = true) ||
                message.contains("unable to resolve host", ignoreCase = true) -> {
                "Network error during remote inference."
            }
            message.isBlank() -> "Inference failed."
            else -> message
        }
    }
}
