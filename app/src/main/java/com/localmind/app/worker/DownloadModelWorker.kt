package com.localmind.app.worker

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.localmind.app.R
import com.localmind.app.core.storage.StoredModelRef
import com.localmind.app.core.storage.PersistentModelStorageManager
import com.localmind.app.data.local.dao.DownloadTaskDao
import com.localmind.app.data.local.dao.ModelDao
import com.localmind.app.data.local.entity.DownloadTaskEntity
import com.localmind.app.data.local.entity.ModelEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import javax.inject.Named
import kotlin.math.abs

@HiltWorker
class DownloadModelWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadTaskDao: DownloadTaskDao,
    private val modelDao: ModelDao,
    private val storageManager: PersistentModelStorageManager,
    @Named("downloadClient") private val downloadClient: OkHttpClient
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): ListenableWorker.Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: UUID.randomUUID().toString()
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()
        val modelName = inputData.getString(KEY_MODEL_NAME) ?: fileName
        val repoId = inputData.getString(KEY_REPO_ID) ?: ""
        val quantization = inputData.getString(KEY_QUANTIZATION) ?: "Q4_K_M"
        val parameterCount = inputData.getString(KEY_PARAMETER_COUNT) ?: "Unknown"
        val templateId = inputData.getString(KEY_TEMPLATE_ID) ?: "chatml_default"
        val stopTokensJson = inputData.getString(KEY_STOP_TOKENS_JSON) ?: "[\"<|im_end|>\",\"</s>\"]"
        val recommendedTemperature = inputData.getFloat(KEY_RECOMMENDED_TEMPERATURE, 0.7f)
        val recommendedTopP = inputData.getFloat(KEY_RECOMMENDED_TOP_P, 0.9f)
        val recommendedTopK = inputData.getInt(KEY_RECOMMENDED_TOP_K, 40)
        val recommendedRepeatPenalty = inputData.getFloat(KEY_RECOMMENDED_REPEAT_PENALTY, 1.1f)
        val recommendedSystemPrompt = inputData.getString(KEY_RECOMMENDED_SYSTEM_PROMPT)
        val expectedSizeBytes = inputData.getLong(KEY_EXPECTED_SIZE_BYTES, 0L)
        val totalRamGb = getTotalRamGb()
        val modelId = repoId.ifBlank { modelName }

        createNotificationChannel()

        val notificationId = repoId.hashCode()
        updateTaskState(
            taskId = taskId,
            modelId = modelId,
            fileName = fileName,
            state = STATE_RUNNING,
            progress = 0,
            downloadedBytes = 0L,
            totalBytes = 0L,
            speedBps = 0L,
            etaSeconds = -1L
        )
        val foregroundStart = runCatching {
            setForeground(createForegroundInfo(
                notificationId = notificationId,
                modelName = modelName,
                progress = 0,
                speedBps = 0L,
                etaSeconds = -1L,
                modelId = modelId
            ))
        }
        if (foregroundStart.isFailure) {
            val message = foregroundStart.exceptionOrNull()?.message
                ?: "Unable to start foreground download service"
            updateTaskState(
                taskId = taskId,
                modelId = repoId.ifBlank { modelName },
                fileName = fileName,
                state = STATE_FAILED,
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                speedBps = 0L,
                etaSeconds = -1L,
                errorMessage = message
            )
            return Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))
        }

        try {
            val downloadOutcome = downloadModelFiles(
                taskId = taskId,
                notificationId = notificationId,
                modelName = modelName,
                repoId = repoId,
                downloadUrl = downloadUrl,
                fileName = fileName,
                expectedSizeBytes = expectedSizeBytes
            )

            val entity = ModelEntity(
                id = repoId.ifBlank { UUID.randomUUID().toString() },
                name = modelName,
                filePath = downloadOutcome.primaryFile.filePath,
                sizeBytes = downloadOutcome.totalSizeBytes,
                quantization = quantization,
                contextLength = recommendedContextLength(totalRamGb),
                parameterCount = parameterCount,
                installDate = System.currentTimeMillis(),
                isActive = false,
                lastUsed = null,
                storageType = downloadOutcome.primaryFile.storageType,
                storageUri = downloadOutcome.primaryFile.storageUri,
                fileName = downloadOutcome.primaryFile.fileName,
                templateId = templateId,
                stopTokensJson = stopTokensJson,
                recommendedTemperature = recommendedTemperature,
                recommendedTopP = recommendedTopP,
                recommendedTopK = recommendedTopK,
                recommendedRepeatPenalty = recommendedRepeatPenalty,
                recommendedSystemPrompt = recommendedSystemPrompt
            )
            modelDao.insertModel(entity)
            updateTaskState(
                taskId = taskId,
                modelId = repoId.ifBlank { modelName },
                fileName = fileName,
                state = STATE_COMPLETED,
                progress = 100,
                downloadedBytes = downloadOutcome.totalSizeBytes,
                totalBytes = downloadOutcome.totalSizeBytes,
                speedBps = 0L,
                etaSeconds = 0L
            )
            val elapsedMs = downloadOutcome.elapsedMs.coerceAtLeast(1L)
            val averageBps = (downloadOutcome.totalSizeBytes * 1000L) / elapsedMs
            Log.i(
                "LocalMind-Download",
                "Completed model=$modelName size=${downloadOutcome.totalSizeBytes}B elapsedMs=$elapsedMs avgSpeed=${formatSpeed(averageBps)}"
            )


            // Download completion notification (#19)
            val completionNotification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle("Download Complete")
                .setContentText("$modelName is ready to use!")
                .setSmallIcon(R.drawable.ic_stat_localmind)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()
            notificationManager.notify(notificationId + 1000, completionNotification)

            return Result.success(
                workDataOf(
                    KEY_FILE_PATH to (downloadOutcome.primaryFile.filePath ?: ""),
                    KEY_STORAGE_URI to (downloadOutcome.primaryFile.storageUri ?: "")
                )
            )
        } catch (e: java.io.IOException) {
            updateTaskState(
                taskId = taskId,
                modelId = repoId.ifBlank { modelName },
                fileName = fileName,
                state = STATE_RETRY,
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                speedBps = 0L,
                etaSeconds = -1L,
                errorMessage = "Network error"
            )
            return Result.retry()
        } catch (e: OutOfMemoryError) {
            updateTaskState(
                taskId = taskId,
                modelId = repoId.ifBlank { modelName },
                fileName = fileName,
                state = STATE_FAILED,
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                speedBps = 0L,
                etaSeconds = -1L,
                errorMessage = "Not enough memory while downloading model"
            )
            return Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Not enough memory while downloading model"))
        } catch (e: SecurityException) {
            updateTaskState(
                taskId = taskId,
                modelId = repoId.ifBlank { modelName },
                fileName = fileName,
                state = STATE_FAILED,
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                speedBps = 0L,
                etaSeconds = -1L,
                errorMessage = "Storage permission denied while writing model"
            )
            return Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Storage permission denied while writing model"))
        } catch (e: kotlinx.coroutines.CancellationException) {
            updateTaskState(
                taskId = taskId,
                modelId = repoId.ifBlank { modelName },
                fileName = fileName,
                state = STATE_FAILED,
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                speedBps = 0L,
                etaSeconds = -1L,
                errorMessage = "Download cancelled"
            )
            return Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Download cancelled"))
        } catch (e: Exception) {
            val message = mapDownloadError(e)
            updateTaskState(
                taskId = taskId,
                modelId = repoId.ifBlank { modelName },
                fileName = fileName,
                state = STATE_FAILED,
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                speedBps = 0L,
                etaSeconds = -1L,
                errorMessage = message
            )
            return Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))
        }
    }

    private suspend fun downloadModelFiles(
        taskId: String,
        notificationId: Int,
        modelName: String,
        repoId: String,
        downloadUrl: String,
        fileName: String,
        expectedSizeBytes: Long
    ): DownloadOutcome {
        val targetFiles = buildTargetFileNames(fileName)
        val modelId = repoId.ifBlank { modelName }
        val storedParts = mutableListOf<StoredModelRef>()
        val startedAt = System.currentTimeMillis()

        var aggregateDownloadedBytes = 0L
        var aggregateKnownTotalBytes = 0L
        var lastUpdateTime = startedAt
        var lastUpdateBytes = 0L

        try {
            for ((partIndex, partFileName) in targetFiles.withIndex()) {
                throwIfStopped()
                val partUrl = buildPartUrl(downloadUrl, partFileName)
                val request = Request.Builder()
                    .url(partUrl)
                    .build()

                downloadClient.newCall(request).execute().use { httpResponse ->
                    if (!httpResponse.isSuccessful) {
                        throw IllegalStateException(mapHttpError(httpResponse.code, targetFiles.size > 1))
                    }

                    val body = httpResponse.body ?: throw IllegalStateException("Empty response body")
                    val partTotalBytes = body.contentLength().coerceAtLeast(0L)
                    if (partTotalBytes > 0L) {
                        aggregateKnownTotalBytes += partTotalBytes
                    } else if (expectedSizeBytes > 0L && targetFiles.size == 1) {
                        aggregateKnownTotalBytes = expectedSizeBytes
                    }

                    var partDownloadedBytes = 0L
                    val storedResult = storageManager.saveModel(partFileName) { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                throwIfStopped()
                                output.write(buffer, 0, bytesRead)
                                partDownloadedBytes += bytesRead
                                aggregateDownloadedBytes += bytesRead

                                val currentTime = System.currentTimeMillis()
                                val timeDiff = currentTime - lastUpdateTime
                                val shouldPublish = timeDiff >= 1000L ||
                                    (partTotalBytes > 0L && partDownloadedBytes >= partTotalBytes)

                                if (shouldPublish) {
                                    val progress = computeProgressPercent(
                                        partIndex = partIndex,
                                        partDownloadedBytes = partDownloadedBytes,
                                        partTotalBytes = partTotalBytes,
                                        totalParts = targetFiles.size,
                                        aggregateDownloadedBytes = aggregateDownloadedBytes,
                                        aggregateKnownTotalBytes = aggregateKnownTotalBytes
                                    )
                                    val bytesSinceLast = aggregateDownloadedBytes - lastUpdateBytes
                                    val speedBps = if (timeDiff > 0L) {
                                        (bytesSinceLast * 1000L) / timeDiff
                                    } else {
                                        0L
                                    }
                                    val etaSeconds = if (speedBps > 0L && aggregateKnownTotalBytes > 0L) {
                                        (aggregateKnownTotalBytes - aggregateDownloadedBytes)
                                            .coerceAtLeast(0L) / speedBps
                                    } else {
                                        -1L
                                    }

                                    publishProgress(
                                        taskId = taskId,
                                        modelId = modelId,
                                        fileName = fileName,
                                        notificationId = notificationId,
                                        modelName = modelName,
                                        progress = progress,
                                        downloadedBytes = aggregateDownloadedBytes,
                                        totalBytes = aggregateKnownTotalBytes,
                                        speedBps = speedBps,
                                        etaSeconds = etaSeconds
                                    )

                                    lastUpdateTime = currentTime
                                    lastUpdateBytes = aggregateDownloadedBytes
                                }
                            }
                        }
                    }

                    val storedPart = storedResult.getOrElse { throw it }
                    validatePartFile(
                        storedPart = storedPart,
                        expectedBytes = when {
                            targetFiles.size == 1 && expectedSizeBytes > 0L -> expectedSizeBytes
                            partTotalBytes > 0L -> partTotalBytes
                            else -> 0L
                        }
                    )
                    storedParts += storedPart

                    publishProgress(
                        taskId = taskId,
                        modelId = modelId,
                        fileName = fileName,
                        notificationId = notificationId,
                        modelName = modelName,
                        progress = (((partIndex + 1f) / targetFiles.size) * 100f).toInt().coerceIn(0, 99),
                        downloadedBytes = aggregateDownloadedBytes,
                        totalBytes = aggregateKnownTotalBytes,
                        speedBps = 0L,
                        etaSeconds = -1L
                    )
                }
            }
        } catch (t: Throwable) {
            cleanupStoredParts(storedParts)
            throw t
        }

        val primaryPart = storedParts.firstOrNull { it.fileName.equals(fileName, ignoreCase = true) }
            ?: storedParts.firstOrNull()
            ?: throw IllegalStateException("Model file was not saved")
        validatePrimaryGgufFile(primaryPart)
        val totalSizeBytes = storedParts.sumOf { it.sizeBytes }

        return DownloadOutcome(
            primaryFile = primaryPart,
            totalSizeBytes = totalSizeBytes,
            elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        )
    }

    private fun computeProgressPercent(
        partIndex: Int,
        partDownloadedBytes: Long,
        partTotalBytes: Long,
        totalParts: Int,
        aggregateDownloadedBytes: Long,
        aggregateKnownTotalBytes: Long
    ): Int {
        val progress = when {
            aggregateKnownTotalBytes > 0L -> {
                (aggregateDownloadedBytes.toDouble() / aggregateKnownTotalBytes.toDouble() * 100.0)
            }
            partTotalBytes > 0L -> {
                ((partIndex.toDouble() + (partDownloadedBytes.toDouble() / partTotalBytes.toDouble())) /
                    totalParts.toDouble()) * 100.0
            }
            else -> {
                (partIndex.toDouble() / totalParts.toDouble()) * 100.0
            }
        }
        return progress.toInt().coerceIn(0, 99)
    }

    private suspend fun publishProgress(
        taskId: String,
        modelId: String,
        fileName: String,
        notificationId: Int,
        modelName: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBps: Long,
        etaSeconds: Long
    ) {
        setProgress(
            workDataOf(
                KEY_PROGRESS to progress,
                KEY_DOWNLOADED_BYTES to downloadedBytes,
                KEY_TOTAL_BYTES to totalBytes,
                KEY_SPEED_BPS to speedBps,
                KEY_ETA_SECONDS to etaSeconds
            )
        )
        updateTaskState(
            taskId = taskId,
            modelId = modelId,
            fileName = fileName,
            state = STATE_RUNNING,
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            speedBps = speedBps,
            etaSeconds = etaSeconds
        )
        setForeground(
            createForegroundInfo(
                notificationId = notificationId,
                modelName = modelName,
                progress = progress,
                speedBps = speedBps,
                etaSeconds = etaSeconds,
                modelId = modelId
            )
        )
    }

    private suspend fun validatePartFile(storedPart: StoredModelRef, expectedBytes: Long) {
        if (storedPart.sizeBytes < MIN_VALID_MODEL_BYTES) {
            storageManager.deleteModel(
                storageType = storedPart.storageType,
                filePath = storedPart.filePath,
                storageUri = storedPart.storageUri,
                fileName = storedPart.fileName
            )
            throw IllegalStateException("Downloaded model file appears corrupted")
        }
        if (expectedBytes <= 0L) return

        val tolerance = (expectedBytes / 100).coerceAtLeast(16L * 1024L)
        if (abs(storedPart.sizeBytes - expectedBytes) > tolerance) {
            storageManager.deleteModel(
                storageType = storedPart.storageType,
                filePath = storedPart.filePath,
                storageUri = storedPart.storageUri,
                fileName = storedPart.fileName
            )
            throw IllegalStateException("Downloaded model size mismatch. Please retry.")
        }
    }

    private fun validatePrimaryGgufFile(primaryPart: StoredModelRef) {
        val localFile = when {
            !primaryPart.filePath.isNullOrBlank() -> java.io.File(primaryPart.filePath)
            primaryPart.fileName.isNotBlank() -> java.io.File(
                java.io.File(applicationContext.filesDir, "models"),
                primaryPart.fileName
            )
            else -> null
        } ?: throw IllegalStateException("Downloaded model path missing")

        if (!localFile.exists() || !localFile.isFile || localFile.length() < MIN_VALID_MODEL_BYTES) {
            throw IllegalStateException("Downloaded model file appears corrupted")
        }

        val isValidMagic = runCatching {
            localFile.inputStream().use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 &&
                    magic[0] == 'G'.code.toByte() &&
                    magic[1] == 'G'.code.toByte() &&
                    magic[2] == 'U'.code.toByte() &&
                    magic[3] == 'F'.code.toByte()
            }
        }.getOrDefault(false)

        if (!isValidMagic) {
            throw IllegalStateException("Downloaded model is invalid GGUF. Re-download required.")
        }
    }

    private suspend fun cleanupStoredParts(parts: List<StoredModelRef>) {
        parts.forEach { part ->
            try {
                storageManager.deleteModel(
                    storageType = part.storageType,
                    filePath = part.filePath,
                    storageUri = part.storageUri,
                    fileName = part.fileName
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun buildTargetFileNames(fileName: String): List<String> {
        val split = parseSplitInfo(fileName) ?: return listOf(fileName)
        return (1..split.totalParts).map { index ->
            "${split.prefix}-${index.toString().padStart(5, '0')}-of-${split.totalParts.toString().padStart(5, '0')}.gguf"
        }
    }

    private fun parseSplitInfo(fileName: String): SplitInfo? {
        val match = SPLIT_GGUF_REGEX.matchEntire(fileName) ?: return null
        val prefix = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val partIndex = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        val totalParts = match.groupValues.getOrNull(3)?.toIntOrNull() ?: return null
        if (totalParts <= 1 || partIndex <= 0 || partIndex > totalParts) return null
        return SplitInfo(prefix, partIndex, totalParts)
    }

    private fun buildPartUrl(downloadUrl: String, targetFileName: String): String {
        val base = downloadUrl.substringBeforeLast('/', missingDelimiterValue = downloadUrl)
        return if (base == downloadUrl) {
            downloadUrl
        } else {
            "$base/$targetFileName"
        }
    }

    private fun mapHttpError(code: Int, isSplitModel: Boolean): String {
        return when (code) {
            401, 403 -> "Auth required. Set valid HF token and retry."
            404 -> if (isSplitModel) {
                "Model shard missing on Hugging Face. Try a different GGUF variant."
            } else {
                "Model file not found on Hugging Face."
            }
            in 500..599 -> "Hugging Face server error. Try again later."
            else -> "HTTP $code"
        }
    }

    private fun throwIfStopped() {
        if (isStopped) {
            throw kotlinx.coroutines.CancellationException("Download cancelled")
        }
    }

    private fun createForegroundInfo(
        notificationId: Int,
        modelName: String,
        progress: Int,
        speedBps: Long = 0L,
        etaSeconds: Long = -1L,
        modelId: String = ""
    ): ForegroundInfo {
        val speedText = if (speedBps > 0) {
            " - ${formatSpeed(speedBps)}"
        } else ""
        val etaText = if (etaSeconds > 0) {
            " - ${formatEta(etaSeconds)} left"
        } else ""

        val cancelIntent = Intent(applicationContext, com.localmind.app.receiver.DownloadActionReceiver::class.java).apply {
            action = com.localmind.app.receiver.DownloadActionReceiver.ACTION_CANCEL_DOWNLOAD
            putExtra(com.localmind.app.receiver.DownloadActionReceiver.EXTRA_MODEL_ID, modelId)
        }
        val cancelPendingIntent = androidx.core.app.PendingIntentCompat.getBroadcast(
            applicationContext,
            notificationId,
            cancelIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )

        val openIntent = Intent(applicationContext, com.localmind.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "model_manager") // Hardcoded for now, or match MainActivity const
        }
        val openPendingIntent = androidx.core.app.PendingIntentCompat.getActivity(
            applicationContext,
            notificationId,
            openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading $modelName")
            .setContentText("$progress%$speedText$etaText")
            .setSmallIcon(R.drawable.ic_stat_localmind)
            .setOngoing(true)
            .setAutoCancel(false)
            .setProgress(100, progress, false)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setContentIntent(openPendingIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of model downloads"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_REPO_ID = "repo_id"
        const val KEY_QUANTIZATION = "quantization"
        const val KEY_PARAMETER_COUNT = "parameter_count"
        const val KEY_TEMPLATE_ID = "template_id"
        const val KEY_STOP_TOKENS_JSON = "stop_tokens_json"
        const val KEY_RECOMMENDED_TEMPERATURE = "recommended_temperature"
        const val KEY_RECOMMENDED_TOP_P = "recommended_top_p"
        const val KEY_RECOMMENDED_TOP_K = "recommended_top_k"
        const val KEY_RECOMMENDED_REPEAT_PENALTY = "recommended_repeat_penalty"
        const val KEY_RECOMMENDED_SYSTEM_PROMPT = "recommended_system_prompt"
        const val KEY_EXPECTED_SIZE_BYTES = "expected_size_bytes"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_STORAGE_URI = "storage_uri"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_SPEED_BPS = "speed_bps"
        const val KEY_ETA_SECONDS = "eta_seconds"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val STATE_RUNNING = "RUNNING"
        const val STATE_COMPLETED = "COMPLETED"
        const val STATE_FAILED = "FAILED"
        const val STATE_RETRY = "RETRY"
        private const val MIN_VALID_MODEL_BYTES = 1024L // 1KB - Support small models like tinygemma
        private const val CHANNEL_ID = "download_channel"
        private val SPLIT_GGUF_REGEX = Regex(
            pattern = "^(.*)-(\\d{5})-of-(\\d{5})\\.gguf$",
            option = RegexOption.IGNORE_CASE
        )

        fun createWorkRequest(
            taskId: String,
            downloadUrl: String,
            fileName: String,
            modelName: String,
            repoId: String,
            quantization: String,
            parameterCount: String,
            templateId: String,
            stopTokensJson: String,
            recommendedTemperature: Float,
            recommendedTopP: Float,
            recommendedTopK: Int,
            recommendedRepeatPenalty: Float,
            recommendedSystemPrompt: String?,
            expectedSizeBytes: Long
        ): androidx.work.OneTimeWorkRequest {
            val inputData = workDataOf(
                KEY_TASK_ID to taskId,
                KEY_DOWNLOAD_URL to downloadUrl,
                KEY_FILE_NAME to fileName,
                KEY_MODEL_NAME to modelName,
                KEY_REPO_ID to repoId,
                KEY_QUANTIZATION to quantization,
                KEY_PARAMETER_COUNT to parameterCount,
                KEY_TEMPLATE_ID to templateId,
                KEY_STOP_TOKENS_JSON to stopTokensJson,
                KEY_RECOMMENDED_TEMPERATURE to recommendedTemperature,
                KEY_RECOMMENDED_TOP_P to recommendedTopP,
                KEY_RECOMMENDED_TOP_K to recommendedTopK,
                KEY_RECOMMENDED_REPEAT_PENALTY to recommendedRepeatPenalty,
                KEY_RECOMMENDED_SYSTEM_PROMPT to recommendedSystemPrompt,
                KEY_EXPECTED_SIZE_BYTES to expectedSizeBytes
            )

            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build()

            return androidx.work.OneTimeWorkRequestBuilder<DownloadModelWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(modelTag(repoId))
                .addTag(variantTag(repoId, fileName))
                .addTag("download_$repoId")
                .build()
        }

        fun workName(modelId: String, fileName: String): String {
            val base = "${sanitizeTagValue(modelId)}_${sanitizeTagValue(fileName)}"
            return "download_${base.take(120)}"
        }

        fun modelTag(modelId: String): String = "download_model_${sanitizeTagValue(modelId)}"

        fun variantTag(modelId: String, fileName: String): String {
            return "download_variant_${sanitizeTagValue(modelId)}_${sanitizeTagValue(fileName)}".take(120)
        }

        private fun sanitizeTagValue(value: String): String {
            return value.lowercase()
                .replace(Regex("[^a-z0-9._-]"), "_")
                .ifBlank { "unknown" }
        }
    }

    private suspend fun updateTaskState(
        taskId: String,
        modelId: String,
        fileName: String,
        state: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBps: Long,
        etaSeconds: Long,
        errorMessage: String? = null
    ) {
        downloadTaskDao.upsert(
            DownloadTaskEntity(
                taskId = taskId,
                modelId = modelId,
                fileName = fileName,
                state = state,
                progress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                speedBps = speedBps,
                etaSeconds = etaSeconds,
                errorMessage = errorMessage,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun getTotalRamGb(): Int {
        val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return ((memInfo.totalMem + (1024L * 1024L * 1024L - 1)) / (1024L * 1024L * 1024L)).toInt()
    }

    private fun recommendedContextLength(totalRamGb: Int): Int {
        return when {
            totalRamGb <= 4 -> 2048
            totalRamGb <= 6 -> 3072
            totalRamGb <= 8 -> 4096
            else -> 6144
        }
    }

    private fun mapDownloadError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("Auth required", ignoreCase = true) -> message
            message.contains("not found", ignoreCase = true) -> message
            message.contains("invalid gguf", ignoreCase = true) -> message
            message.contains("corrupted", ignoreCase = true) -> message
            message.contains("storage", ignoreCase = true) -> "Not enough storage for this model."
            message.contains("memory", ignoreCase = true) -> "Not enough memory to process download."
            message.isNotBlank() -> message
            else -> "Download failed"
        }
    }

    private fun formatSpeed(bps: Long): String {
        if (bps <= 0) return "0 B/s"
        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
        var speed = bps.toDouble()
        var unitIndex = 0
        while (speed >= 1024 && unitIndex < units.size - 1) {
            speed /= 1024
            unitIndex++
        }
        return String.format("%.1f %s", speed, units[unitIndex])
    }

    private fun formatEta(seconds: Long): String {
        if (seconds <= 0L) return "0s"
        return when {
            seconds < 60L -> "${seconds}s"
            seconds < 3600L -> String.format("%dm %ds", seconds / 60L, seconds % 60L)
            else -> String.format("%dh %dm", seconds / 3600L, (seconds % 3600L) / 60L)
        }
    }

    private data class DownloadOutcome(
        val primaryFile: StoredModelRef,
        val totalSizeBytes: Long,
        val elapsedMs: Long
    )

    private data class SplitInfo(
        val prefix: String,
        val partIndex: Int,
        val totalParts: Int
    )
}

