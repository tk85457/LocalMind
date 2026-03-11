package com.localmind.app.core.utils

import android.util.Log
import com.localmind.app.BuildConfig

/**
 * SECURITY FIX: SecureLogger
 *
 * Wraps android.util.Log to ensure:
 * - Logs are ONLY emitted in DEBUG builds (BuildConfig.DEBUG = true)
 * - In RELEASE builds, all log calls are no-ops → zero log output
 * - Prevents sensitive information leakage via logcat in production
 *
 * OWASP MASVS: MSTG-RESILIENCE-2 (no debug info in release)
 * Google Play: Sensitive data must not be logged in production builds
 *
 * Usage: Replace Log.d/i/w/e with SecureLogger.d/i/w/e
 */
object SecureLogger {

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.w(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable) {
        if (BuildConfig.DEBUG) Log.w(tag, message, throwable)
    }

    fun e(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.e(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        if (BuildConfig.DEBUG) Log.e(tag, message, throwable)
    }

    /** Safe: strips potential PII from messages before logging */
    fun safe(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            // Mask anything that looks like a token/key (long alphanumeric strings)
            val sanitized = message.replace(Regex("[A-Za-z0-9_\\-]{40,}"), "[REDACTED]")
            Log.d(tag, sanitized)
        }
    }
}
