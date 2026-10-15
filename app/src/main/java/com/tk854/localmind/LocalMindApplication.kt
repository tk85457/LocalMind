package com.tk854.localmind

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tk854.localmind.data.repository.ModelRepository
import com.tk854.localmind.llm.ActivationOptions
import com.tk854.localmind.llm.ActivationSource
import com.tk854.localmind.llm.LLMEngine
import com.tk854.localmind.llm.ModelLifecycleManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LocalMindApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var llmEngine: LLMEngine
    @Inject lateinit var modelRepository: ModelRepository
    @Inject lateinit var modelLifecycleManager: ModelLifecycleManager
    @Inject lateinit var settingsRepository: com.tk854.localmind.data.repository.SettingsRepository

    private val applicationScope = MainScope()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Create notification channel for foreground service
        createNotificationChannel()

        // Initialize storage directories
        initializeStorageDirectories()

        // LOW-RAM MIGRATION: Existing users ke liye purani heavy defaults fix karo.
        // DataStore mein saved values override karo  —  sirf tab jab old (heavy) values hain.
        applicationScope.launch { settingsRepository.applyLowRamMigrationIfNeeded() }

        // Preload active model as soon as app boots.
        preloadActiveModelAtStartup()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Keep model resident for active conversations; Android will reclaim process if needed.
    }

    private fun preloadActiveModelAtStartup() {
        applicationScope.launch {
            val activeModel = runCatching { modelRepository.ensureAnyActiveModel() }.getOrNull()
                ?: return@launch
            runCatching {
                modelLifecycleManager.activateModelSafely(
                    modelId = activeModel.id,
                    options = ActivationOptions(source = ActivationSource.AUTO_RESTORE)
                ).getOrThrow()
            }.onFailure { e ->
                if (BuildConfig.DEBUG) android.util.Log.w("LocalMindApp", "Startup model preload failed: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_LLM_SERVICE,
                "LLM Generation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when LocalMind is generating responses"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun initializeStorageDirectories() {
        val baseDir = filesDir
        val modelsDir = baseDir.resolve("models")
        val conversationsDir = baseDir.resolve("conversations")
        val cacheDir = baseDir.resolve("cache")

        modelsDir.mkdirs()
        conversationsDir.mkdirs()
        cacheDir.mkdirs()
    }

    companion object {
        const val CHANNEL_ID_LLM_SERVICE = "llm_service_channel"
    }
}
