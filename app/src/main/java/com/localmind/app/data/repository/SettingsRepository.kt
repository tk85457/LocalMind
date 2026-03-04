package com.localmind.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.edit
import com.localmind.app.core.storage.ModelStorageType
import com.localmind.app.domain.model.CompatibilityMode
import com.localmind.app.domain.model.InferenceMode
import com.localmind.app.domain.model.RemoteProvider
import com.localmind.app.domain.model.VisionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        const val DEFAULT_CONTEXT_SIZE = 2048
        const val DEFAULT_MAX_TOKENS = 1024
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_GPU_LAYERS = -1 // -1 = Auto
        const val DEFAULT_BATCH_SIZE = 2048       // was 512 — larger batch = faster prefill
        const val DEFAULT_PHYSICAL_BATCH_SIZE = 512 // was 256
        const val DEFAULT_FLASH_ATTENTION = "Auto"
        const val DEFAULT_KEY_CACHE_TYPE = "F16"
        const val DEFAULT_VALUE_CACHE_TYPE = "F16"
    }

    private object PreferencesKeys {
        val CONTEXT_SIZE = intPreferencesKey("context_size")
        val MEMORY_MAPPING = stringPreferencesKey("memory_mapping")
        val AUTO_OFFLOAD = booleanPreferencesKey("auto_offload")
        val LANGUAGE = stringPreferencesKey("language")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val AUTO_NAVIGATE_CHAT = booleanPreferencesKey("auto_navigate_chat")

        // Existing inference params (moved to repository for persistence)
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_P = floatPreferencesKey("top_p")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val REPEAT_PENALTY = floatPreferencesKey("repeat_penalty")
        val THREAD_COUNT = intPreferencesKey("thread_count")
        val GPU_LAYERS = intPreferencesKey("gpu_layers")
        val SKIP_GPU_AFTER_FAIL = booleanPreferencesKey("skip_gpu_after_fail")
        val TOP_K = intPreferencesKey("top_k")
        val SHOW_ADVANCED_SETTINGS = booleanPreferencesKey("show_advanced_settings")
        val PERMISSIONS_REQUESTED = booleanPreferencesKey("permissions_requested")
        val ACTIVE_CLOUD_MODEL_REPO_ID = stringPreferencesKey("active_cloud_model_repo_id")
        val ACTIVE_CLOUD_MODEL_NAME = stringPreferencesKey("active_cloud_model_name")
        val COMPAT_MODE = stringPreferencesKey("compat_mode")
        val ALLOW_FORCE_LOAD = booleanPreferencesKey("allow_force_load")
        val INFERENCE_MODE = stringPreferencesKey("inference_mode")
        val VISION_MODE = stringPreferencesKey("vision_mode")
        val REMOTE_FALLBACK_ENABLED = booleanPreferencesKey("remote_fallback_enabled")
        val REMOTE_PROVIDER = stringPreferencesKey("remote_provider")
        val CATALOG_CURSOR_PAGING = booleanPreferencesKey("catalog_cursor_paging")
        val CHAT_STABILITY_HARDENING = booleanPreferencesKey("chat_stability_hardening")
        val BENCHMARK_RELIABILITY_MODE = booleanPreferencesKey("benchmark_reliability_mode")
        val CACHE_SAFE_MODE = booleanPreferencesKey("cache_safe_mode")
        val HYBRID_AUTO_FALLBACK = booleanPreferencesKey("hybrid_auto_fallback")
        val MODEL_STORAGE_TREE_URI = stringPreferencesKey("model_storage_tree_uri")
        val MODEL_STORAGE_MODE = stringPreferencesKey("model_storage_mode")
        val MODEL_STORAGE_LINKED = booleanPreferencesKey("model_storage_linked")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val THEME_COLOR = stringPreferencesKey("theme_color")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val AUTO_DELETE_DAYS = intPreferencesKey("auto_delete_days")

        // Advanced Inference
        val BATCH_SIZE = intPreferencesKey("batch_size")
        val PHYSICAL_BATCH_SIZE = intPreferencesKey("physical_batch_size")
        val FLASH_ATTENTION = stringPreferencesKey("flash_attention")
        val KEY_CACHE_TYPE = stringPreferencesKey("key_cache_type")
        val VALUE_CACHE_TYPE = stringPreferencesKey("value_cache_type")
    }

    val contextSize: Flow<Int> = dataStore.data.map { it[PreferencesKeys.CONTEXT_SIZE] ?: DEFAULT_CONTEXT_SIZE }
    val memoryMapping: Flow<String> = dataStore.data.map { it[PreferencesKeys.MEMORY_MAPPING] ?: "Smart" }
    val autoOffload: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.AUTO_OFFLOAD] ?: false }
    val language: Flow<String> = dataStore.data.map { it[PreferencesKeys.LANGUAGE] ?: "English (EN)" }
    val darkMode: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.DARK_MODE] ?: true }
    val autoNavigateChat: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.AUTO_NAVIGATE_CHAT] ?: true }

    val temperature: Flow<Float> = dataStore.data.map { it[PreferencesKeys.TEMPERATURE] ?: 0.7f }
    val topP: Flow<Float> = dataStore.data.map { it[PreferencesKeys.TOP_P] ?: 0.9f }
    val maxTokens: Flow<Int> = dataStore.data.map { it[PreferencesKeys.MAX_TOKENS] ?: DEFAULT_MAX_TOKENS }
    val repeatPenalty: Flow<Float> = dataStore.data.map { it[PreferencesKeys.REPEAT_PENALTY] ?: 1.1f }
    val threadCount: Flow<Int> = dataStore.data.map {
        it[PreferencesKeys.THREAD_COUNT] ?: defaultThreadCount()
    }
    val gpuLayers: Flow<Int> = dataStore.data.map { it[PreferencesKeys.GPU_LAYERS] ?: defaultGpuLayers() }
    val skipGpuAfterFail: Flow<Boolean> =
        dataStore.data.map { it[PreferencesKeys.SKIP_GPU_AFTER_FAIL] ?: false }
    val topK: Flow<Int> = dataStore.data.map { it[PreferencesKeys.TOP_K] ?: DEFAULT_TOP_K }
    val batchSize: Flow<Int> = dataStore.data.map { it[PreferencesKeys.BATCH_SIZE] ?: DEFAULT_BATCH_SIZE }
    val physicalBatchSize: Flow<Int> =
        dataStore.data.map { it[PreferencesKeys.PHYSICAL_BATCH_SIZE] ?: DEFAULT_PHYSICAL_BATCH_SIZE }
    val flashAttention: Flow<String> =
        dataStore.data.map { it[PreferencesKeys.FLASH_ATTENTION] ?: DEFAULT_FLASH_ATTENTION }
    val keyCacheType: Flow<String> =
        dataStore.data.map { it[PreferencesKeys.KEY_CACHE_TYPE] ?: DEFAULT_KEY_CACHE_TYPE }
    val valueCacheType: Flow<String> =
        dataStore.data.map { it[PreferencesKeys.VALUE_CACHE_TYPE] ?: DEFAULT_VALUE_CACHE_TYPE }

    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.ONBOARDING_COMPLETED] ?: false }
    val showAdvancedSettings: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.SHOW_ADVANCED_SETTINGS] ?: false }
    val permissionsRequested: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.PERMISSIONS_REQUESTED] ?: false }
    val activeCloudModelRepoId: Flow<String?> =
        dataStore.data.map { it[PreferencesKeys.ACTIVE_CLOUD_MODEL_REPO_ID] }
    val activeCloudModelName: Flow<String?> =
        dataStore.data.map { it[PreferencesKeys.ACTIVE_CLOUD_MODEL_NAME] }
    val compatibilityMode: Flow<CompatibilityMode> = dataStore.data.map {
        CompatibilityMode.fromStored(it[PreferencesKeys.COMPAT_MODE])
    }
    val allowForceLoad: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.ALLOW_FORCE_LOAD] ?: true }
    val inferenceMode: Flow<InferenceMode> = dataStore.data.map {
        InferenceMode.fromStored(it[PreferencesKeys.INFERENCE_MODE])
    }
    val visionMode: Flow<VisionMode> = dataStore.data.map {
        VisionMode.fromStored(it[PreferencesKeys.VISION_MODE])
    }
    val remoteFallbackEnabled: Flow<Boolean> =
        dataStore.data.map { it[PreferencesKeys.REMOTE_FALLBACK_ENABLED] ?: true }
    val remoteProvider: Flow<RemoteProvider> = dataStore.data.map {
        RemoteProvider.fromStored(it[PreferencesKeys.REMOTE_PROVIDER])
    }
    val catalogCursorPaging: Flow<Boolean> =
        dataStore.data.map { it[PreferencesKeys.CATALOG_CURSOR_PAGING] ?: true }
    val chatStabilityHardening: Flow<Boolean> =
        dataStore.data.map { it[PreferencesKeys.CHAT_STABILITY_HARDENING] ?: true }
    val benchmarkReliabilityMode: Flow<Boolean> =
        dataStore.data.map { it[PreferencesKeys.BENCHMARK_RELIABILITY_MODE] ?: true }
    val cacheSafeMode: Flow<Boolean> =
        dataStore.data.map { it[PreferencesKeys.CACHE_SAFE_MODE] ?: true }
    val hybridAutoFallback: Flow<Boolean> =
        dataStore.data.map { it[PreferencesKeys.HYBRID_AUTO_FALLBACK] ?: true }
    val modelStorageTreeUri: Flow<String?> =
        dataStore.data.map { it[PreferencesKeys.MODEL_STORAGE_TREE_URI] }
    val modelStorageMode: Flow<String> =
        dataStore.data.map { it[PreferencesKeys.MODEL_STORAGE_MODE] ?: ModelStorageType.INTERNAL }
    val modelStorageLinked: Flow<Boolean> =
        dataStore.data.map { it[PreferencesKeys.MODEL_STORAGE_LINKED] ?: false }
    val fontScale: Flow<Float> = dataStore.data.map { it[PreferencesKeys.FONT_SCALE] ?: 1.0f }
    val themeColor: Flow<String> = dataStore.data.map { it[PreferencesKeys.THEME_COLOR] ?: "Neon" }
    val fontFamily: Flow<String> = dataStore.data.map { it[PreferencesKeys.FONT_FAMILY] ?: "Default" }
    val biometricLock: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.BIOMETRIC_LOCK] ?: false }
    val autoDeleteDays: Flow<Int> = dataStore.data.map { it[PreferencesKeys.AUTO_DELETE_DAYS] ?: 0 }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[PreferencesKeys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setShowAdvancedSettings(show: Boolean) {
        dataStore.edit { it[PreferencesKeys.SHOW_ADVANCED_SETTINGS] = show }
    }

    suspend fun setPermissionsRequested(requested: Boolean) {
        dataStore.edit { it[PreferencesKeys.PERMISSIONS_REQUESTED] = requested }
    }

    suspend fun setActiveCloudModel(repoId: String, modelName: String) {
        dataStore.edit {
            it[PreferencesKeys.ACTIVE_CLOUD_MODEL_REPO_ID] = repoId
            it[PreferencesKeys.ACTIVE_CLOUD_MODEL_NAME] = modelName
        }
    }

    suspend fun clearActiveCloudModel() {
        dataStore.edit {
            it.remove(PreferencesKeys.ACTIVE_CLOUD_MODEL_REPO_ID)
            it.remove(PreferencesKeys.ACTIVE_CLOUD_MODEL_NAME)
        }
    }

    suspend fun setCompatibilityMode(mode: CompatibilityMode) {
        dataStore.edit { it[PreferencesKeys.COMPAT_MODE] = mode.name }
    }

    suspend fun setAllowForceLoad(allow: Boolean) {
        dataStore.edit { it[PreferencesKeys.ALLOW_FORCE_LOAD] = allow }
    }

    suspend fun setInferenceMode(mode: InferenceMode) {
        dataStore.edit { it[PreferencesKeys.INFERENCE_MODE] = mode.name }
    }

    suspend fun setVisionMode(mode: VisionMode) {
        dataStore.edit { it[PreferencesKeys.VISION_MODE] = mode.name }
    }

    suspend fun setRemoteFallbackEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.REMOTE_FALLBACK_ENABLED] = enabled }
    }

    suspend fun setRemoteProvider(provider: RemoteProvider) {
        dataStore.edit { it[PreferencesKeys.REMOTE_PROVIDER] = provider.name }
    }

    suspend fun setCatalogCursorPaging(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CATALOG_CURSOR_PAGING] = enabled }
    }

    suspend fun setChatStabilityHardening(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CHAT_STABILITY_HARDENING] = enabled }
    }

    suspend fun setBenchmarkReliabilityMode(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.BENCHMARK_RELIABILITY_MODE] = enabled }
    }

    suspend fun setCacheSafeMode(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CACHE_SAFE_MODE] = enabled }
    }

    suspend fun setHybridAutoFallback(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.HYBRID_AUTO_FALLBACK] = enabled }
    }

    suspend fun setModelStorageTreeUri(uri: String?) {
        dataStore.edit {
            if (uri.isNullOrBlank()) {
                it.remove(PreferencesKeys.MODEL_STORAGE_TREE_URI)
            } else {
                it[PreferencesKeys.MODEL_STORAGE_TREE_URI] = uri
            }
        }
    }

    suspend fun setModelStorageMode(mode: String) {
        dataStore.edit { it[PreferencesKeys.MODEL_STORAGE_MODE] = mode }
    }

    suspend fun setModelStorageLinked(linked: Boolean) {
        dataStore.edit { it[PreferencesKeys.MODEL_STORAGE_LINKED] = linked }
    }

    suspend fun updateContextSize(size: Int) {
        dataStore.edit { it[PreferencesKeys.CONTEXT_SIZE] = size }
    }

    suspend fun updateMemoryMapping(mapping: String) {
        dataStore.edit { it[PreferencesKeys.MEMORY_MAPPING] = mapping }
    }

    suspend fun updateAutoOffload(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.AUTO_OFFLOAD] = enabled }
    }

    suspend fun updateLanguage(lang: String) {
        dataStore.edit { it[PreferencesKeys.LANGUAGE] = lang }
    }

    suspend fun updateDarkMode(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DARK_MODE] = enabled }
    }

    suspend fun updateAutoNavigateChat(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.AUTO_NAVIGATE_CHAT] = enabled }
    }

    suspend fun updateTemperature(value: Float) {
        dataStore.edit { it[PreferencesKeys.TEMPERATURE] = value }
    }

    suspend fun updateTopP(value: Float) {
        dataStore.edit { it[PreferencesKeys.TOP_P] = value }
    }

    suspend fun updateMaxTokens(value: Int) {
        dataStore.edit { it[PreferencesKeys.MAX_TOKENS] = value }
    }

    suspend fun updateRepeatPenalty(value: Float) {
        dataStore.edit { it[PreferencesKeys.REPEAT_PENALTY] = value }
    }

    suspend fun updateThreadCount(value: Int) {
        dataStore.edit { it[PreferencesKeys.THREAD_COUNT] = value }
    }

    suspend fun updateGpuLayers(value: Int) {
        dataStore.edit { it[PreferencesKeys.GPU_LAYERS] = value }
    }

    suspend fun setSkipGpuAfterFail(skip: Boolean) {
        dataStore.edit { it[PreferencesKeys.SKIP_GPU_AFTER_FAIL] = skip }
    }

    suspend fun updateTopK(value: Int) {
        dataStore.edit { it[PreferencesKeys.TOP_K] = value }
    }

    suspend fun updateFontScale(value: Float) {
        dataStore.edit { it[PreferencesKeys.FONT_SCALE] = value }
    }

    suspend fun updateThemeColor(value: String) {
        dataStore.edit { it[PreferencesKeys.THEME_COLOR] = value }
    }

    suspend fun updateFontFamily(value: String) {
        dataStore.edit { it[PreferencesKeys.FONT_FAMILY] = value }
    }

    suspend fun updateBiometricLock(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.BIOMETRIC_LOCK] = enabled }
    }

    suspend fun updateAutoDeleteDays(days: Int) {
        dataStore.edit { it[PreferencesKeys.AUTO_DELETE_DAYS] = days }
    }

    suspend fun updateBatchSize(size: Int) {
        dataStore.edit { it[PreferencesKeys.BATCH_SIZE] = size }
    }

    suspend fun updatePhysicalBatchSize(size: Int) {
        dataStore.edit { it[PreferencesKeys.PHYSICAL_BATCH_SIZE] = size }
    }

    suspend fun updateFlashAttention(mode: String) {
        dataStore.edit { it[PreferencesKeys.FLASH_ATTENTION] = mode }
    }

    suspend fun updateKeyCacheType(type: String) {
        dataStore.edit { it[PreferencesKeys.KEY_CACHE_TYPE] = type }
    }

    suspend fun updateValueCacheType(type: String) {
        dataStore.edit { it[PreferencesKeys.VALUE_CACHE_TYPE] = type }
    }

    private fun defaultThreadCount(): Int {
        return (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 8)
    }

    private fun defaultGpuLayers(): Int {
        return DEFAULT_GPU_LAYERS
    }
}
