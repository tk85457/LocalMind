package com.localmind.app.service

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.localmind.app.R
import com.localmind.app.llm.InferenceConfig
import com.localmind.app.llm.LLMEngine
import com.localmind.app.llm.GenerationResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@AndroidEntryPoint
class LLMService : Service() {

    @Inject
    lateinit var llmEngine: LLMEngine

    private val binder = LLMBinder()

    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generationMutex = Mutex()

    inner class LLMBinder : Binder() {
        fun getService(): LLMService = this@LLMService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()

        serviceScope.launch {
            runCatching {
                llmEngine.stopGeneration()
                withTimeoutOrNull(5_000L) {
                    llmEngine.unloadModel()
                }
            }
        }

        serviceScope.cancel()
    }

    /**
     * Load model safely inside coroutine
     */
    fun loadModel(
        modelPath: String,
        onResult: (Boolean) -> Unit
    ) {
        serviceScope.launch {
            val result = runCatching {
                withTimeout(90_000L) {
                    llmEngine.loadModel(modelPath)
                }
            }.getOrElse { Result.failure(it) }

            withContext(Dispatchers.Main) {
                onResult(result.isSuccess)
            }
        }
    }

    /**
     * Streaming generation
     */
    fun generateResponse(
        prompt: String,
        config: InferenceConfig = InferenceConfig()
    ): Flow<String> {
        return flow {
            generationMutex.withLock {
                llmEngine.generate(prompt, config).collect { result ->
                    when (result) {
                        is GenerationResult.Token -> emit(result.text)
                        else -> Unit
                    }
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    fun stopGeneration() {
        // BUGFIX: Do NOT acquire generationMutex here.
        // generateResponse() holds the mutex while generating.
        // Trying to lock it here = deadlock — the service would freeze permanently.
        serviceScope.launch {
            llmEngine.stopGeneration()
        }
    }

    fun unloadModel() {
        serviceScope.launch {
            llmEngine.stopGeneration()
            llmEngine.unloadModel()
        }
    }

    fun isModelLoaded(): Boolean {
        return llmEngine.isModelLoaded()
    }

    // -------------------------
    // Notification
    // -------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "LLM Inference",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Running AI model inference"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LocalMind")
            .setContentText("AI model is running")
            .setSmallIcon(R.drawable.ic_stat_localmind)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "llm_service_channel"
        private const val NOTIFICATION_ID = 1
    }
}
