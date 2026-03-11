package com.localmind.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.localmind.app.core.utils.SecureLogger
import com.localmind.app.data.repository.DownloadOrchestrator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var downloadOrchestrator: DownloadOrchestrator

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CANCEL_DOWNLOAD) {
            val modelId = intent.getStringExtra(EXTRA_MODEL_ID)
            if (!modelId.isNullOrBlank()) {
                // SECURITY FIX: modelId NOT logged in release builds — could leak model path info
                SecureLogger.i("LocalMind-Receiver", "Received cancel request for model")
                val pendingResult = goAsync()
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                scope.launch {
                    try {
                        downloadOrchestrator.cancel(modelId)
                    } catch (e: Exception) {
                        SecureLogger.e("LocalMind-Receiver", "Cancel failed", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_CANCEL_DOWNLOAD = "com.localmind.app.action.CANCEL_DOWNLOAD"
        const val EXTRA_MODEL_ID = "com.localmind.app.extra.MODEL_ID"
    }
}
