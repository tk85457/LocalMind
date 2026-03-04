package com.localmind.app.core.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

data class HardwareStats(
    val cpuUsage: Float,
    val ramUsagePercent: Float,
    val usedRamGb: Double,
    val totalRamGb: Double,
    val availableStorage: String
)

@Singleton
class HardwareMonitor @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {

    private val _refreshTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)

    fun refresh() {
        _refreshTrigger.tryEmit(Unit)
    }

    fun getStatsFlow(): Flow<HardwareStats> = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(generateStats())
            // BUGFIX: Replaced fragile java.util.concurrent.CancellationException hack.
            // Old code threw JUC CancellationException inside a coroutine collector — wrong type,
            // could silently swallow real coroutine cancellation in some Kotlin versions.
            // New approach: just delay 1 second. The refresh trigger is a nice-to-have
            // but hardware stats don\'t need sub-second freshness; 1s polling is fine.
            try {
                kotlinx.coroutines.delay(1_000L)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Coroutine cancelled — exit cleanly
                throw e
            }
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    private fun generateStats(): HardwareStats {
        val cpu = getCpuUsage()
        val (usedRam, totalRam, ramPercent) = getRamStats()
        val storage = getStorageInfo()

        return HardwareStats(
            cpuUsage = cpu,
            ramUsagePercent = ramPercent,
            usedRamGb = usedRam,
            totalRamGb = totalRam,
            availableStorage = storage
        )
    }

    private fun getCpuUsage(): Float {
        // Read /proc/stat twice with a short sleep to calculate real CPU usage.
        // Thread.sleep is acceptable here because getCpuUsage() is always called
        // from getStatsFlow() which runs on Dispatchers.IO — blocking is safe.
        // Fallback to 0f if file unreadable (strict SELinux on some ROMs).
        return try {
            val line1 = java.io.BufferedReader(java.io.FileReader("/proc/stat")).use { it.readLine() }
            val parts1 = line1?.split(Regex("\\s+"))?.drop(1)?.mapNotNull { it.toLongOrNull() } ?: return 0f
            val idle1 = parts1.getOrElse(3) { 0L }
            val total1 = parts1.sum()
            // FIX: Thread.sleep is fine on Dispatchers.IO. Do NOT use coroutines delay here
            // because getCpuUsage is a regular (non-suspend) function.
            Thread.sleep(200)
            val line2 = java.io.BufferedReader(java.io.FileReader("/proc/stat")).use { it.readLine() }
            val parts2 = line2?.split(Regex("\\s+"))?.drop(1)?.mapNotNull { it.toLongOrNull() } ?: return 0f
            val idle2 = parts2.getOrElse(3) { 0L }
            val total2 = parts2.sum()
            val deltaTotal = (total2 - total1).coerceAtLeast(1L)
            val deltaIdle = idle2 - idle1
            ((deltaTotal - deltaIdle).toFloat() / deltaTotal.toFloat()).coerceIn(0f, 1f)
        } catch (_: Exception) {
            0f
        }
    }

    private fun getRamStats(): Triple<Double, Double, Float> {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)

        val total = memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
        val avail = memInfo.availMem.toDouble() / (1024 * 1024 * 1024)
        val used = total - avail
        val percent = (used / total).toFloat()

        return Triple(used, total, percent)
    }

    private fun getStorageInfo(): String {
        val path = context.filesDir
        val stat = android.os.StatFs(path.path)
        val bytes = stat.availableBytes
        return StorageUtils.formatSize(bytes)
    }

    companion object {
        fun getDeviceDetails(context: Context): String {
            val ram = getTotalRam(context)
            val cpu = Runtime.getRuntime().availableProcessors()
            val chipset = Build.HARDWARE
            val model = Build.MODEL

            return "$model ($chipset)\nRAM: $ram | CPU: $cpu Cores"
        }

        private fun getTotalRam(context: Context): String {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val totalMem = memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
            return String.format("%.1f GB", totalMem)
        }
    }
}
