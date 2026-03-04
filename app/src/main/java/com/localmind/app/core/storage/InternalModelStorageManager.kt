package com.localmind.app.core.storage

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InternalModelStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getModelsDir(): File = File(context.filesDir, "models").apply {
        if (!exists()) mkdirs()
    }

    suspend fun saveModel(
        fileName: String,
        writer: suspend (OutputStream) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val modelsDir = getModelsDir()
            val tempFile = File(modelsDir, "$fileName.tmp")
            val finalFile = File(modelsDir, fileName)

            if (tempFile.exists()) tempFile.delete()
            tempFile.outputStream().use { writer(it) }

            if (tempFile.length() <= 0L) {
                tempFile.delete()
                throw IllegalStateException("Model write produced empty file")
            }

            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                tempFile.delete()
                throw IllegalStateException("Failed to finalize model file")
            }
            finalFile
        }
    }

    suspend fun exists(fileName: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(getModelsDir(), fileName)
        file.exists() && file.isFile && file.length() > 0
    }

    suspend fun deleteModel(fileName: String): Boolean = withContext(Dispatchers.IO) {
        File(getModelsDir(), fileName).delete()
    }

    suspend fun copyUriToStorage(sourceUri: Uri, fileName: String): Result<File> = withContext(Dispatchers.IO) {
        saveModel(fileName) { output ->
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                input.copyTo(output)
            } ?: throw IllegalStateException("Could not open source model stream")
        }
    }

    suspend fun openModelInputStream(fileName: String): InputStream? = withContext(Dispatchers.IO) {
        val file = File(getModelsDir(), fileName)
        if (file.exists()) file.inputStream() else null
    }
}
