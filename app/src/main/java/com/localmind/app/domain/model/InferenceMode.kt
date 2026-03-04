package com.localmind.app.domain.model

enum class InferenceMode {
    LOCAL_ONLY,
    REMOTE_ONLY,
    HYBRID;

    companion object {
        fun fromStored(value: String?): InferenceMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: HYBRID
        }
    }
}
