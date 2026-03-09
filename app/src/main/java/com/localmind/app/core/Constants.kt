package com.localmind.app.core

object Constants {
    // App
    const val APP_NAME = "LocalMind"
    const val APP_VERSION = "1.0.0"

    // Storage
    const val BASE_DIR = "LocalMind"
    const val DATABASE_NAME = "localmind.db"
    const val MODELS_DIR = "LocalMind/models"
    const val CONVERSATIONS_DIR = "LocalMind/conversations"
    const val CACHE_DIR = "LocalMind/cache"

    // Model Constraints
    const val MIN_RAM_GB = 2
    const val RECOMMENDED_RAM_GB = 6
    const val MAX_MODEL_SIZE_GB = 8L
    const val MODEL_MEMORY_SAFETY_FACTOR = 1.3f

    // Model Quantization
    const val QUANTIZATION_Q4 = "Q4"
    const val QUANTIZATION_Q5 = "Q5"
    const val QUANTIZATION_Q8 = "Q8"

    // Context Limits — single source of truth
    const val MAX_CONTEXT_SIZE = 4960          // Hard cap: sabhi jagah yahi use hoga
    const val CONTEXT_SIZE_4GB = 1024
    const val CONTEXT_SIZE_6GB = 1536
    const val CONTEXT_SIZE_8GB = 2048
    const val CONTEXT_SIZE_12GB = 4096

    // Inference Defaults
    const val DEFAULT_TEMPERATURE = 0.7f
    const val DEFAULT_TOP_P = 0.9f
    const val DEFAULT_REPEAT_PENALTY = 1.1f
    const val DEFAULT_MAX_TOKENS = 1024  // Aligned with SettingsRepository.DEFAULT_MAX_TOKENS
    const val DEFAULT_CONTEXT_SIZE = 2048

    // Threading
    const val MIN_THREADS = 1
    const val MAX_THREADS = 8

    // Generation
    const val GENERATION_TIMEOUT_MS = 300_000L
    const val TOKEN_STREAM_DELAY_MS = 50L

    // Database
    const val DATABASE_VERSION = 3

    // Preferences
    const val PREFS_NAME = "localmind_prefs"
    const val PREF_ONBOARDING_COMPLETED = "onboarding_completed"
    const val PREF_ACTIVE_MODEL_ID = "active_model_id"
    const val PREF_TEMPERATURE = "temperature"
    const val PREF_TOP_P = "top_p"
    const val PREF_REPEAT_PENALTY = "repeat_penalty"
    const val PREF_MAX_TOKENS = "max_tokens"
    const val PREF_CONTEXT_SIZE = "context_size"
    const val PREF_THREAD_COUNT = "thread_count"

    // File Extensions
    const val GGUF_EXTENSION = ".gguf"

    // MIME Types
    const val MIME_TYPE_GGUF = "application/octet-stream"

    // Notification
    const val NOTIFICATION_ID_LLM_SERVICE = 1001
}
