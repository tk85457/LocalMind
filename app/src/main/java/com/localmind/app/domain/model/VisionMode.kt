package com.localmind.app.domain.model

enum class VisionMode {
    LOCAL_ONLY,
    REMOTE_FALLBACK,
    DISABLED;

    companion object {
        fun fromStored(value: String?): VisionMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: REMOTE_FALLBACK
        }
    }
}
