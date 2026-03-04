package com.localmind.app.llm

data class ActivationOptions(
    val forceLoad: Boolean = false,
    val source: ActivationSource = ActivationSource.USER
)

enum class ActivationSource {
    USER,
    AUTO_RESTORE,
    CHAT_AUTOSTART,
    BENCHMARK,
    INTERNAL
}
