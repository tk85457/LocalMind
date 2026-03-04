package com.localmind.app.core.rollout

import com.localmind.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class FeatureRolloutFlags(
    val catalogCursorPaging: Boolean = true,
    val chatStabilityHardening: Boolean = true,
    val benchmarkReliabilityMode: Boolean = true,
    val cacheSafeMode: Boolean = true,
    val hybridAutoFallback: Boolean = true
)

@Singleton
class FeatureRolloutConfig @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    val flags: Flow<FeatureRolloutFlags> = combine(
        settingsRepository.catalogCursorPaging,
        settingsRepository.chatStabilityHardening,
        settingsRepository.benchmarkReliabilityMode,
        settingsRepository.cacheSafeMode,
        settingsRepository.hybridAutoFallback
    ) { catalogCursorPaging, chatStabilityHardening, benchmarkReliabilityMode, cacheSafeMode, hybridAutoFallback ->
        FeatureRolloutFlags(
            catalogCursorPaging = catalogCursorPaging,
            chatStabilityHardening = chatStabilityHardening,
            benchmarkReliabilityMode = benchmarkReliabilityMode,
            cacheSafeMode = cacheSafeMode,
            hybridAutoFallback = hybridAutoFallback
        )
    }

    suspend fun snapshot(): FeatureRolloutFlags = flags.first()
}
