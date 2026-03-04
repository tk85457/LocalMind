package com.localmind.app.core.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class STTHelper(
    private val context: Context,
    private val onResults: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onPartialResults: (String) -> Unit = {},
    private val onReady: () -> Unit = {},
    private val onEndOfSpeech: () -> Unit = {}
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var lastAttemptPreferredOffline: Boolean = true
    private var onlineFallbackAttempted: Boolean = false
    private var relaxedRetryAttempted: Boolean = false
    private var recognizerSessionActive: Boolean = false
    private var manualStopRequested: Boolean = false
    private var suppressClientErrorUntilMs: Long = 0L

    private fun ensureRecognizer() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(this@STTHelper)
            }
        }
    }

    private fun recreateRecognizer() {
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null
        ensureRecognizer()
    }

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(preferOffline: Boolean = true) {
        if (!isRecognitionAvailable()) {
            onError("Speech recognition service not available on this device.")
            return
        }
        ensureRecognizer()
        lastAttemptPreferredOffline = preferOffline
        onlineFallbackAttempted = false
        relaxedRetryAttempted = false
        manualStopRequested = false
        startListeningInternal(preferOffline = preferOffline, forceLocaleTag = true)
    }

    private fun startListeningInternal(preferOffline: Boolean, forceLocaleTag: Boolean) {
        ensureRecognizer()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            if (forceLocaleTag) {
                val languageTag = Locale.getDefault().toLanguageTag()
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            }
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            }
        }
        try {
            if (recognizerSessionActive) {
                suppressClientErrorUntilMs = SystemClock.elapsedRealtime() + 1_200L
                speechRecognizer?.cancel()
                recognizerSessionActive = false
            }
            speechRecognizer?.startListening(intent)
        } catch (t: Throwable) {
            Log.e("STTHelper", "Failed to start listening", t)
            onError("Could not start speech recognition: ${t.message ?: "unknown error"}")
        }
    }

    fun stopListening() {
        manualStopRequested = true
        recognizerSessionActive = false
        suppressClientErrorUntilMs = SystemClock.elapsedRealtime() + 1_200L
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun onReadyForSpeech(params: Bundle?) {
        recognizerSessionActive = true
        manualStopRequested = false
        onReady()
    }

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        recognizerSessionActive = false
        this.onEndOfSpeech.invoke()
    }

    override fun onError(error: Int) {
        recognizerSessionActive = false
        if (manualStopRequested && error == SpeechRecognizer.ERROR_CLIENT) {
            manualStopRequested = false
            return
        }
        if (error == SpeechRecognizer.ERROR_CLIENT && SystemClock.elapsedRealtime() < suppressClientErrorUntilMs) {
            return
        }

        if (
            lastAttemptPreferredOffline &&
            !onlineFallbackAttempted &&
            (error == SpeechRecognizer.ERROR_NETWORK ||
                error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                error == SpeechRecognizer.ERROR_SERVER)
        ) {
            onlineFallbackAttempted = true
            startListeningInternal(preferOffline = false, forceLocaleTag = true)
            return
        }

        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            recreateRecognizer()
            if (!relaxedRetryAttempted) {
                relaxedRetryAttempted = true
                startListeningInternal(preferOffline = false, forceLocaleTag = false)
                return
            }
        }

        val languageNotSupported = error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
        val languageUnavailable = error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
        if ((languageNotSupported || languageUnavailable) && !relaxedRetryAttempted) {
            relaxedRetryAttempted = true
            startListeningInternal(preferOffline = false, forceLocaleTag = false)
            return
        }

        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Selected language not supported by speech engine"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Selected language pack is unavailable. Download offline language in Google app."
            else -> "Speech recognition failed: $error"
        }
        Log.e("STTHelper", "Error code $error: $errorMessage")
        onError(errorMessage)
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            onResults(matches[0])
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            onPartialResults(matches[0])
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}

