package com.localmind.app.core.performance

import android.content.Context
import android.util.Log
import com.localmind.app.core.rollout.FeatureRolloutConfig
import com.localmind.app.core.storage.PersistentModelStorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class CacheClearResult(
    val deletedFiles: Int,
    val failedDeletes: Int,
    val freedBytes: Long
)

@Singleton
class SafeCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val persistentModelStorageManager: PersistentModelStorageManager,
    private val featureRolloutConfig: FeatureRolloutConfig
) {
    suspend fun clearAppCacheSafely(): CacheClearResult = withContext(Dispatchers.IO) {
        var deletedFiles = 0
        var failedDeletes = 0
        var freedBytes = 0L
        val cacheSafeMode = featureRolloutConfig.snapshot().cacheSafeMode
        Log.i("LocalMind-Storage", "Starting cache clear, safeMode=$cacheSafeMode")

        // Do not clear codeCache while process is alive; it may destabilize runtime on some devices.
        val cacheRoots = if (cacheSafeMode) {
            listOfNotNull(context.cacheDir, context.externalCacheDir)
        } else {
            listOfNotNull(context.cacheDir, context.externalCacheDir, context.codeCacheDir)
        }
        cacheRoots.forEach { root ->
            root.listFiles().orEmpty().forEach { child ->
                val (deleted, failed, bytes) = deleteRecursivelySafe(child)
                deletedFiles += deleted
                failedDeletes += failed
                freedBytes += bytes
            }
        }

        if (cacheSafeMode) {
            // Keep model files and DB intact. Only remove temporary artifacts.
            val modelsDir = File(context.filesDir, "models")
            modelsDir.listFiles().orEmpty().forEach { file ->
                if (file.name.endsWith(".tmp", ignoreCase = true) ||
                    file.name.endsWith(".part", ignoreCase = true) ||
                    file.name.contains("temp", ignoreCase = true)
                ) {
                    val (deleted, failed, bytes) = deleteRecursivelySafe(file)
                    deletedFiles += deleted
                    failedDeletes += failed
                    freedBytes += bytes
                }
            }

            // Clean temporary artifacts in linked SAF folder as well.
            deletedFiles += persistentModelStorageManager.cleanSafTempArtifacts()
        }

        Log.i(
            "LocalMind-Storage",
            "Cache clear complete deleted=$deletedFiles failed=$failedDeletes freedBytes=$freedBytes"
        )

        CacheClearResult(
            deletedFiles = deletedFiles,
            failedDeletes = failedDeletes,
            freedBytes = freedBytes
        )
    }

    private fun deleteRecursivelySafe(file: File): Triple<Int, Int, Long> {
        var deleted = 0
        var failed = 0
        var bytes = 0L

        if (file.isDirectory) {
            file.listFiles().orEmpty().forEach { child ->
                val (childDeleted, childFailed, childBytes) = deleteRecursivelySafe(child)
                deleted += childDeleted
                failed += childFailed
                bytes += childBytes
            }
        }

        val fileSize = runCatching { if (file.isFile) file.length() else 0L }.getOrDefault(0L)
        val removed = runCatching { file.delete() }.getOrDefault(false)
        if (removed) {
            deleted += 1
            bytes += fileSize
        } else {
            failed += 1
        }

        return Triple(deleted, failed, bytes)
    }
}
