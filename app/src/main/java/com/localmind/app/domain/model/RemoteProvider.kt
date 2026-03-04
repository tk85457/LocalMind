package com.localmind.app.domain.model

enum class RemoteProvider {
    HUGGING_FACE,
    OPENAI_COMPATIBLE;

    companion object {
        fun fromStored(value: String?): RemoteProvider {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: HUGGING_FACE
        }
    }
}
