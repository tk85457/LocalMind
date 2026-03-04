package com.localmind.app.core.utils

import android.os.Environment
import android.os.StatFs
import java.io.File

object StorageUtils {
    const val DEFAULT_BUFFER_BYTES: Long = 500L * 1024L * 1024L

    /**
     * Get available internal storage in bytes.
     */
    fun getAvailableInternalStorage(filesDir: File): Long {
        val stat = StatFs(filesDir.path)
        return stat.availableBytes
    }

    fun getAvailablePrimaryExternalStorage(): Long? {
        return runCatching {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)
            stat.availableBytes
        }.getOrNull()
    }

    fun hasEnoughSpace(filesDir: File, requiredBytes: Long, bufferBytes: Long = DEFAULT_BUFFER_BYTES): Boolean {
        try {
            val available = getAvailableInternalStorage(filesDir)
            // If requiredBytes is 0 (unknown size), assume 2GB default requirement
            val actualRequired = if (requiredBytes == 0L) 2L * 1024 * 1024 * 1024 else requiredBytes
            return available > (actualRequired + bufferBytes)
        } catch (e: Exception) {
            // If check fails, be optimistic and allow download to try
            return true
        }
    }

    fun hasEnoughSpace(
        availableBytes: Long,
        requiredBytes: Long,
        bufferBytes: Long = DEFAULT_BUFFER_BYTES
    ): Boolean {
        val actualRequired = if (requiredBytes == 0L) 2L * 1024L * 1024L * 1024L else requiredBytes
        return availableBytes > (actualRequired + bufferBytes)
    }

    /**
     * Format bytes to Human-readable string.
     */
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
