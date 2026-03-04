package com.localmind.app.domain.model

enum class CompatibilityMode {
    SAFE,
    FORCE;

    companion object {
        fun fromStored(value: String?): CompatibilityMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SAFE
        }
    }
}
