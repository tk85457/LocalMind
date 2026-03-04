package com.localmind.app.domain.model

/**
 * Domain model representing a phone-compatible LLM available on Hugging Face.
 */
data class HuggingFaceModelInfo(
    val id: String,
    val name: String,
    val repoId: String,
    val author: String,
    val description: String,
    val parameterCount: String,
    val sizeGb: Double,
    val quantization: String,
    val ggufFileName: String,
    val downloadUrl: String,
    val category: ModelCategory,
    val minRamGb: Int,
    val downloads: Int = 0,
    val likes: Int = 0,
    val tags: List<String> = emptyList(),
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f
)

/**
 * Model size categories for filtering
 */
enum class ModelCategory(val label: String, val emoji: String) {
    TINY("Tiny (<1B)", "⚡"),
    SMALL("Small (1-3B)", "🔹"),
    MEDIUM("Medium (3-7B)", "🔷"),
    ALL("All Models", "📦")
}
