package com.localmind.app.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class StoredModelRef(
    val storageType: String,
    val filePath: String?,
    val storageUri: String?,
    val fileName: String,
    val sizeBytes: Long
)

@Singleton
class PersistentModelStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val internalModelStorageManager: InternalModelStorageManager
) {
    suspend fun saveModel(
        fileName: String,
        writer: suspend (OutputStream) -> Unit
    ): Result<StoredModelRef> = withContext(Dispatchers.IO) {
        internalModelStorageManager.saveModel(fileName, writer).map { file ->
            StoredModelRef(
                storageType = ModelStorageType.INTERNAL,
                filePath = file.absolutePath,
                storageUri = null,
                fileName = file.name,
                sizeBytes = file.length()
            )
        }
    }

    suspend fun exists(
        storageType: String,
        filePath: String?,
        storageUri: String?,
        fileName: String
    ): Boolean = withContext(Dispatchers.IO) {
        resolveLocalFile(storageType, filePath, storageUri, fileName)
            ?.let { it.exists() && it.isFile && it.length() > 0L }
            ?: false
    }

    suspend fun deleteModel(
        storageType: String,
        filePath: String?,
        storageUri: String?,
        fileName: String
    ): Boolean = withContext(Dispatchers.IO) {
        resolveLocalFile(storageType, filePath, storageUri, fileName)?.delete() ?: false
    }

    suspend fun cleanSafTempArtifacts(): Int = withContext(Dispatchers.IO) {
        // SAF-specific temp cleanup is currently not active in this build variant.
        0
    }

    private fun resolveLocalFile(
        storageType: String,
        filePath: String?,
        @Suppress("UNUSED_PARAMETER") storageUri: String?,
        fileName: String
    ): File? {
        if (!filePath.isNullOrBlank()) {
            return File(filePath)
        }

        val safeName = fileName.trim()
        if (safeName.isBlank()) {
            return null
        }

        return if (storageType == ModelStorageType.INTERNAL || storageType == ModelStorageType.SAF) {
            File(File(context.filesDir, "models"), safeName)
        } else {
            null
        }
    }
}

