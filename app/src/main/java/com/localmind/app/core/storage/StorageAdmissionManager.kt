package com.localmind.app.core.storage

import android.content.Context
import com.localmind.app.core.utils.StorageUtils
import com.localmind.app.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class StorageAdmissionResult(
    val allowed: Boolean,
    val requiredBytes: Long,
    val availableBytes: Long?,
    val storageMode: String,
    val reason: String? = null
)

@Singleton
class StorageAdmissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    suspend fun evaluate(requiredBytes: Long, exactSize: Boolean): StorageAdmissionResult {
        val linked = settingsRepository.modelStorageLinked.first()
        val configuredMode = settingsRepository.modelStorageMode.first()
        val effectiveMode = if (linked && configuredMode == ModelStorageType.SAF) {
            ModelStorageType.SAF
        } else {
            ModelStorageType.INTERNAL
        }

        // For estimated/unknown sizes, allow and let worker-level storage checks decide.
        if (!exactSize || requiredBytes <= 0L) {
            return StorageAdmissionResult(
                allowed = true,
                requiredBytes = requiredBytes,
                availableBytes = resolveAvailableBytes(effectiveMode),
                storageMode = effectiveMode
            )
        }

        val available = resolveAvailableBytes(effectiveMode)
        val allowed = available?.let {
            StorageUtils.hasEnoughSpace(
                availableBytes = it,
                requiredBytes = requiredBytes,
                bufferBytes = StorageUtils.DEFAULT_BUFFER_BYTES
            )
        } ?: true

        val reason = if (!allowed) "Not enough storage for this model." else null
        return StorageAdmissionResult(
            allowed = allowed,
            requiredBytes = requiredBytes,
            availableBytes = available,
            storageMode = effectiveMode,
            reason = reason
        )
    }

    private fun resolveAvailableBytes(storageMode: String): Long? {
        return when (storageMode) {
            ModelStorageType.SAF -> {
                StorageUtils.getAvailablePrimaryExternalStorage()
                    ?: runCatching { StorageUtils.getAvailableInternalStorage(context.filesDir) }.getOrNull()
            }

            else -> runCatching { StorageUtils.getAvailableInternalStorage(context.filesDir) }.getOrNull()
        }
    }
}
