package com.localmind.app.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DownloadMetricsFormatter {
    fun formatBytes(bytes: Long): String {
        val safeBytes = bytes.coerceAtLeast(0L)
        val gb = safeBytes / (1024.0 * 1024.0 * 1024.0)
        val mb = safeBytes / (1024.0 * 1024.0)
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.0f MB", mb)
            else -> String.format(Locale.US, "%d KB", safeBytes / 1024L)
        }
    }

    fun formatSpeed(bytesPerSecond: Long): String {
        val bps = bytesPerSecond.coerceAtLeast(0L)
        val kbps = bps / 1024.0
        val mbps = kbps / 1024.0
        return when {
            mbps >= 1.0 -> String.format(Locale.US, "%.1f MB/s", mbps)
            kbps >= 1.0 -> String.format(Locale.US, "%.0f KB/s", kbps)
            else -> "$bps B/s"
        }
    }

    fun formatEta(seconds: Long): String {
        val safeSeconds = seconds.coerceAtLeast(0L)
        return when {
            safeSeconds < 60L -> "${safeSeconds}s"
            safeSeconds < 3600L -> {
                String.format(
                    Locale.US,
                    "%dm %ds",
                    safeSeconds / 60L,
                    safeSeconds % 60L
                )
            }

            else -> {
                String.format(
                    Locale.US,
                    "%dh %dm",
                    safeSeconds / 3600L,
                    (safeSeconds % 3600L) / 60L
                )
            }
        }
    }

    fun estimateFinishAtMs(etaSeconds: Long, nowMs: Long = System.currentTimeMillis()): Long? {
        return if (etaSeconds > 0L) nowMs + (etaSeconds * 1000L) else null
    }

    fun formatFinishTime(timeMs: Long): String {
        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        return formatter.format(Date(timeMs))
    }
}
