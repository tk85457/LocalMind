package com.localmind.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.edit
import com.localmind.app.core.storage.ModelStorageType
import com.localmind.app.domain.model.CompatibilityMode
import com.localmind.app.domain.model.InferenceMode
import com.localmind.app.domain.model.RemoteProvider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        // ── Context Init Params (PocketPal contextInitParamsVersions.ts v2.1) ──
        const val DEFAULT_CONTEXT_SIZE = 2048           // PocketPal: n_ctx = 2048
        const val DEFAULT_BATCH_SIZE = 512              // PocketPal: n_batch = 512 (was 1024)
        const val DEFAULT_PHYSICAL_BATCH_SIZE = 512     // PocketPal: n_ubatch = 512
        const val DEFAULT_GPU_LAYERS = 99               // PocketPal: n_gpu_layers = 99 (all layers, auto) (was 0)
        const val DEFAULT_USE_MLOCK = false             // PocketPal: use_mlock = false
        const val DEFAULT_USE_MMAP = true               // Used only as boolean fallback; "Smart" is the real default
        const val DEFAULT_USE_MMAP_MODE = "Smart"          // PocketPal: use_mmap = smart (Android)
        // PocketPal: flash_attn_type = 'off' on Android (only 'auto' on iOS)
        const val DEFAULT_FLASH_ATTENTION = false
        // PocketPal Android default: F16 (safe, compatible with all devices)
        const val DEFAULT_KEY_CACHE_TYPE = "F16"        // PocketPal: cache_type_k = 'f16'
        const val DEFAULT_VALUE_CACHE_TYPE = "F16"      // PocketPal: cache_type_v = 'f16'
        const val DEFAULT_KV_UNIFIED = true             // PocketPal: kv_unified = true (CRITICAL memory saving)
        const val DEFAULT_IMAGE_MAX_TOKENS = 512        // PocketPal: image_max_tokens = 512

        // ── Completion Params (PocketPal completionSettingsVersions.ts v3) ──
        const val DEFAULT_MAX_TOKENS = 1024             // PocketPal: n_predict = 1024
        const val DEFAULT_TOP_K = 40                    // PocketPal: top_k = 40
        const val DEFAULT_TOP_P = 0.95f                 // PocketPal: top_p = 0.95 (was 0.9)
        const val DEFAULT_MIN_P = 0.05f                 // PocketPal: min_p = 0.05
        const val DEFAULT_XTC_THRESHOLD = 0.1f          // PocketPal: xtc_threshold = 0.1
        const val DEFAULT_XTC_PROBABILITY = 0.0f        // PocketPal: xtc_probability = 0.0 (disabled)
        const val DEFAULT_TYPICAL_P = 1.0f              // PocketPal: typical_p = 1.0 (disabled)
        const val DEFAULT_PENALTY_LAST_N = 64           // PocketPal: penalty_last_n = 64 (was 128)
        const val DEFAULT_PENALTY_REPEAT = 1.0f         // PocketPal: penalty_repeat = 1.0
        const val DEFAULT_PENALTY_FREQ = 0.0f           // PocketPal: penalty_freq = 0.0 (disabled)
        const val DEFAULT_PENALTY_PRESENT = 0.0f        // PocketPal: penalty_present = 0.0 (disabled)
        const val DEFAULT_MIROSTAT = 0                  // PocketPal: mirostat = 0 (off)
        const val DEFAULT_MIROSTAT_TAU = 5.0f           // PocketPal: mirostat_tau = 5.0
        const val DEFAULT_MIROSTAT_ETA = 0.1f           // PocketPal: mirostat_eta = 0.1
        const val DEFAULT_SEED = -1                     // PocketPal: seed = -1 (random)
        const val DEFAULT_N_PROBS = 0                   // PocketPal: n_probs = 0
        // TTFT FIX: jinja=false by default — native template path mein engineMutex
        // contention + extra JNI overhead hai. PromptBuilderService path faster hai for now.
        // Jab generateWithMessages flow optimize ho tab true karna.
        const val DEFAULT_JINJA = true                  // PocketPal: jinja = true
        const val DEFAULT_ENABLE_THINKING = false       // PERF: thinking disabled by default — 2-3x speedup on non-reasoning models
        const val DEFAULT_INCLUDE_THINKING_IN_CONTEXT = false // PERF: no thinking = no context overhead

        // ── Other Settings ──
        const val DEFAULT_CACHE_PROMPT = true
        const val DEFAULT_DEFRAG_THRESHOLD = 0.1f
        const val DEFAULT_SPECULATIVE_DECODING = true
        const val DEFAULT_MIN_N_CTX = 1024
    }

    private object PreferencesKeys {
        val CONTEXT_SIZE = intPreferencesKey("context_size_v2")
        val MEMORY_MAPPING = stringPreferencesKey("memory_mapping")
        val AUTO_OFFLOAD = booleanPreferencesKey("auto_offload")
        val LANGUAGE = stringPreferencesKey("language")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val AUTO_NAVIGATE_CHAT = booleanPreferencesKey("auto_navigate_chat")

        // Existing inference params (moved to repository for persistence)
        val TEMPERATURE = floatPreferencesKey("temperature")
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
        val FLASH_ATTENTION = booleanPreferencesKey("flash_attention")
        val KEY_CACHE_TYPE = stringPreferencesKey("key_cache_type")
        val VALUE_CACHE_TYPE = stringPreferencesKey("value_cache_type")

        // Advanced Inference Settings
        val PENALTY_LAST_N = intPreferencesKey("penalty_last_n")
        val CACHE_PROMPT = booleanPreferencesKey("cache_prompt")
        val USE_MLOCK = booleanPreferencesKey("use_mlock_v2")
        val USE_MMAP = booleanPreferencesKey("use_mmap")
        val DEFRAG_THRESHOLD = floatPreferencesKey("defrag_threshold")
        val SPECULATIVE_DECODING = booleanPreferencesKey("speculative_decoding")
        val MIN_N_CTX = intPreferencesKey("min_n_ctx")
        val KV_UNIFIED = booleanPreferencesKey("kv_unified")
        val IMAGE_MAX_TOKENS = intPreferencesKey("image_max_tokens")
        // Completion Params (PocketPal parity)
        val MIN_P = floatPreferencesKey("min_p")
        val SEED = intPreferencesKey("seed")
        val TOP_P = floatPreferencesKey("top_p_v2")  // new key to avoid legacy conflict
        val XTC_THRESHOLD = floatPreferencesKey("xtc_threshold")
        val XTC_PROBABILITY = floatPreferencesKey("xtc_probability")
        val TYPICAL_P = floatPreferencesKey("typical_p")
        val PENALTY_REPEAT = floatPreferencesKey("penalty_repeat")
        val PENALTY_FREQ = floatPreferencesKey("penalty_freq")
        val PENALTY_PRESENT = floatPreferencesKey("penalty_present")
        val MIROSTAT = intPreferencesKey("mirostat")
        val MIROSTAT_TAU = floatPreferencesKey("mirostat_tau")
        val MIROSTAT_ETA = floatPreferencesKey("mirostat_eta")
        val N_PROBS = intPreferencesKey("n_probs")
        val JINJA = booleanPreferencesKey("jinja")
        val ENABLE_THINKING = booleanPreferencesKey("enable_thinking")
        val INCLUDE_THINKING_IN_CONTEXT = booleanPreferencesKey("include_thinking_in_context")
    }

    val contextSize: Flow<Int> = dataStore.data.map { it[PreferencesKeys.CONTEXT_SIZE] ?: DEFAULT_CONTEXT_SIZE }
    val memoryMapping: Flow<String> = dataStore.data.map { it[PreferencesKeys.MEMORY_MAPPING] ?: "Smart" }
    val autoOffload: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.AUTO_OFFLOAD] ?: false }
    val language: Flow<String> = dataStore.data.map { it[PreferencesKeys.LANGUAGE] ?: "English (EN)" }
    val darkMode: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.DARK_MODE] ?: true }
    val autoNavigateChat: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.AUTO_NAVIGATE_CHAT] ?: true }

    val temperature: Flow<Float> = dataStore.data.map { it[PreferencesKeys.TEMPERATURE] ?: 0.7f }
    val topP: Flow<Float> = dataStore.data.map { it[PreferencesKeys.TOP_P] ?: DEFAULT_TOP_P }  // PocketPal: 0.95
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
    val flashAttention: Flow<Boolean> =
        dataStore.data.map { it[PreferencesKeys.FLASH_ATTENTION] ?: DEFAULT_FLASH_ATTENTION }
    val keyCacheType: Flow<String> =
        dataStore.data.map { it[PreferencesKeys.KEY_CACHE_TYPE] ?: DEFAULT_KEY_CACHE_TYPE }
    val valueCacheType: Flow<String> =
        dataStore.data.map { it[PreferencesKeys.VALUE_CACHE_TYPE] ?: DEFAULT_VALUE_CACHE_TYPE }

    // New Advanced Settings Flows
    val penaltyLastN: Flow<Int> = dataStore.data.map { it[PreferencesKeys.PENALTY_LAST_N] ?: DEFAULT_PENALTY_LAST_N }
    val cachePrompt: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.CACHE_PROMPT] ?: DEFAULT_CACHE_PROMPT }
    val useMlock: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.USE_MLOCK] ?: DEFAULT_USE_MLOCK }
    val useMmap: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.USE_MMAP] ?: DEFAULT_USE_MMAP }
    val defragThreshold: Flow<Float> = dataStore.data.map { it[PreferencesKeys.DEFRAG_THRESHOLD] ?: DEFAULT_DEFRAG_THRESHOLD }
    val speculativeDecoding: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.SPECULATIVE_DECODING] ?: DEFAULT_SPECULATIVE_DECODING }
    val minNCtx: Flow<Int> = dataStore.data.map { it[PreferencesKeys.MIN_N_CTX] ?: DEFAULT_MIN_N_CTX }
    // PocketPal Completion Params — all flows
    val minP: Flow<Float> = dataStore.data.map { it[PreferencesKeys.MIN_P] ?: DEFAULT_MIN_P }
    val seed: Flow<Int> = dataStore.data.map { it[PreferencesKeys.SEED] ?: DEFAULT_SEED }
    val xtcThreshold: Flow<Float> = dataStore.data.map { it[PreferencesKeys.XTC_THRESHOLD] ?: DEFAULT_XTC_THRESHOLD }
    val xtcProbability: Flow<Float> = dataStore.data.map { it[PreferencesKeys.XTC_PROBABILITY] ?: DEFAULT_XTC_PROBABILITY }
    val typicalP: Flow<Float> = dataStore.data.map { it[PreferencesKeys.TYPICAL_P] ?: DEFAULT_TYPICAL_P }
    val penaltyRepeat: Flow<Float> = dataStore.data.map { it[PreferencesKeys.PENALTY_REPEAT] ?: DEFAULT_PENALTY_REPEAT }
    val penaltyFreq: Flow<Float> = dataStore.data.map { it[PreferencesKeys.PENALTY_FREQ] ?: DEFAULT_PENALTY_FREQ }
    val penaltyPresent: Flow<Float> = dataStore.data.map { it[PreferencesKeys.PENALTY_PRESENT] ?: DEFAULT_PENALTY_PRESENT }
    val mirostat: Flow<Int> = dataStore.data.map { it[PreferencesKeys.MIROSTAT] ?: DEFAULT_MIROSTAT }
    val mirostatTau: Flow<Float> = dataStore.data.map { it[PreferencesKeys.MIROSTAT_TAU] ?: DEFAULT_MIROSTAT_TAU }
    val mirostatEta: Flow<Float> = dataStore.data.map { it[PreferencesKeys.MIROSTAT_ETA] ?: DEFAULT_MIROSTAT_ETA }
    val nProbs: Flow<Int> = dataStore.data.map { it[PreferencesKeys.N_PROBS] ?: DEFAULT_N_PROBS }
    val jinja: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.JINJA] ?: DEFAULT_JINJA }
    val enableThinking: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.ENABLE_THINKING] ?: DEFAULT_ENABLE_THINKING }
    val includeThinkingInContext: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.INCLUDE_THINKING_IN_CONTEXT] ?: DEFAULT_INCLUDE_THINKING_IN_CONTEXT }
    // Context Init Params
    val kvUnified: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.KV_UNIFIED] ?: DEFAULT_KV_UNIFIED }
    val imageMaxTokens: Flow<Int> = dataStore.data.map { it[PreferencesKeys.IMAGE_MAX_TOKENS] ?: DEFAULT_IMAGE_MAX_TOKENS }

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

    suspend fun updateFlashAttention(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FLASH_ATTENTION] = enabled }
    }

    suspend fun updateKeyCacheType(type: String) {
        dataStore.edit { it[PreferencesKeys.KEY_CACHE_TYPE] = type }
    }

    suspend fun updateValueCacheType(type: String) {
        dataStore.edit { it[PreferencesKeys.VALUE_CACHE_TYPE] = type }
    }

    suspend fun updatePenaltyLastN(value: Int) {
        dataStore.edit { it[PreferencesKeys.PENALTY_LAST_N] = value }
    }

    suspend fun updateCachePrompt(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CACHE_PROMPT] = enabled }
    }

    suspend fun updateUseMlock(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.USE_MLOCK] = enabled }
    }

    suspend fun updateUseMmap(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.USE_MMAP] = enabled }
    }

    suspend fun updateDefragThreshold(value: Float) {
        dataStore.edit { it[PreferencesKeys.DEFRAG_THRESHOLD] = value }
    }

    suspend fun updateSpeculativeDecoding(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SPECULATIVE_DECODING] = enabled }
    }

    suspend fun updateMinNCtx(value: Int) {
        dataStore.edit { it[PreferencesKeys.MIN_N_CTX] = value }
    }

    suspend fun updateMinP(value: Float) {
        dataStore.edit { it[PreferencesKeys.MIN_P] = value }
    }

    suspend fun updateSeed(value: Int) {
        dataStore.edit { it[PreferencesKeys.SEED] = value }
    }

    // ── New PocketPal Parity update functions ──
    suspend fun updateXtcThreshold(value: Float) { dataStore.edit { it[PreferencesKeys.XTC_THRESHOLD] = value } }
    suspend fun updateXtcProbability(value: Float) { dataStore.edit { it[PreferencesKeys.XTC_PROBABILITY] = value } }
    suspend fun updateTypicalP(value: Float) { dataStore.edit { it[PreferencesKeys.TYPICAL_P] = value } }
    suspend fun updatePenaltyRepeat(value: Float) { dataStore.edit { it[PreferencesKeys.PENALTY_REPEAT] = value } }
    suspend fun updatePenaltyFreq(value: Float) { dataStore.edit { it[PreferencesKeys.PENALTY_FREQ] = value } }
    suspend fun updatePenaltyPresent(value: Float) { dataStore.edit { it[PreferencesKeys.PENALTY_PRESENT] = value } }
    suspend fun updateMirostat(value: Int) { dataStore.edit { it[PreferencesKeys.MIROSTAT] = value } }
    suspend fun updateMirostatTau(value: Float) { dataStore.edit { it[PreferencesKeys.MIROSTAT_TAU] = value } }
    suspend fun updateMirostatEta(value: Float) { dataStore.edit { it[PreferencesKeys.MIROSTAT_ETA] = value } }
    suspend fun updateNProbs(value: Int) { dataStore.edit { it[PreferencesKeys.N_PROBS] = value } }
    suspend fun updateJinja(enabled: Boolean) { dataStore.edit { it[PreferencesKeys.JINJA] = enabled } }
    suspend fun updateEnableThinking(enabled: Boolean) { dataStore.edit { it[PreferencesKeys.ENABLE_THINKING] = enabled } }
    suspend fun updateIncludeThinkingInContext(enabled: Boolean) { dataStore.edit { it[PreferencesKeys.INCLUDE_THINKING_IN_CONTEXT] = enabled } }
    suspend fun updateKvUnified(enabled: Boolean) { dataStore.edit { it[PreferencesKeys.KV_UNIFIED] = enabled } }
    suspend fun updateImageMaxTokens(value: Int) { dataStore.edit { it[PreferencesKeys.IMAGE_MAX_TOKENS] = value } }
    suspend fun updateTopP(value: Float) { dataStore.edit { it[PreferencesKeys.TOP_P] = value } }

    private fun defaultThreadCount(): Int {
        // PocketPal formula: Math.floor(cores * 0.8) — same as getRecommendedThreadCount()
        val cores = Runtime.getRuntime().availableProcessors()
        return (cores * 0.8).toInt().coerceIn(1, cores)
    }

    private fun defaultGpuLayers(): Int {
        return DEFAULT_GPU_LAYERS
    }

    // FIX #1: Single flow that emits a fully-populated CachedSettings snapshot whenever
    // ANY pref changes. Since DataStore writes all prefs to a single file, every pref
    // change triggers one emission here — we then read every value from the same snapshot.
    // This replaces 23 parallel .first() calls per message with zero disk reads at inference time.
    fun allSettingsFlow(): kotlinx.coroutines.flow.Flow<ChatViewModelSettings> {
        return dataStore.data.map { prefs ->
            ChatViewModelSettings(
                temperature = prefs[PreferencesKeys.TEMPERATURE] ?: 0.7f,
                maxTokens = prefs[PreferencesKeys.MAX_TOKENS] ?: DEFAULT_MAX_TOKENS,
                topP = prefs[PreferencesKeys.TOP_P] ?: DEFAULT_TOP_P,
                contextSize = prefs[PreferencesKeys.CONTEXT_SIZE] ?: DEFAULT_CONTEXT_SIZE,
                penaltyRepeat = prefs[PreferencesKeys.PENALTY_REPEAT] ?: DEFAULT_PENALTY_REPEAT,
                topK = prefs[PreferencesKeys.TOP_K] ?: DEFAULT_TOP_K,
                threadCount = prefs[PreferencesKeys.THREAD_COUNT] ?: defaultThreadCount(),
                showAdvancedSettings = prefs[PreferencesKeys.SHOW_ADVANCED_SETTINGS] ?: false,
                minP = prefs[PreferencesKeys.MIN_P] ?: DEFAULT_MIN_P,
                seed = prefs[PreferencesKeys.SEED] ?: DEFAULT_SEED,
                xtcThreshold = prefs[PreferencesKeys.XTC_THRESHOLD] ?: DEFAULT_XTC_THRESHOLD,
                xtcProbability = prefs[PreferencesKeys.XTC_PROBABILITY] ?: DEFAULT_XTC_PROBABILITY,
                typicalP = prefs[PreferencesKeys.TYPICAL_P] ?: DEFAULT_TYPICAL_P,
                penaltyLastN = prefs[PreferencesKeys.PENALTY_LAST_N] ?: DEFAULT_PENALTY_LAST_N,
                penaltyFreq = prefs[PreferencesKeys.PENALTY_FREQ] ?: DEFAULT_PENALTY_FREQ,
                penaltyPresent = prefs[PreferencesKeys.PENALTY_PRESENT] ?: DEFAULT_PENALTY_PRESENT,
                mirostat = prefs[PreferencesKeys.MIROSTAT] ?: DEFAULT_MIROSTAT,
                mirostatTau = prefs[PreferencesKeys.MIROSTAT_TAU] ?: DEFAULT_MIROSTAT_TAU,
                mirostatEta = prefs[PreferencesKeys.MIROSTAT_ETA] ?: DEFAULT_MIROSTAT_ETA,
                nProbs = prefs[PreferencesKeys.N_PROBS] ?: DEFAULT_N_PROBS,
                jinja = prefs[PreferencesKeys.JINJA] ?: DEFAULT_JINJA,
                enableThinking = prefs[PreferencesKeys.ENABLE_THINKING] ?: DEFAULT_ENABLE_THINKING,
                includeThinkingInContext = prefs[PreferencesKeys.INCLUDE_THINKING_IN_CONTEXT] ?: DEFAULT_INCLUDE_THINKING_IN_CONTEXT
            )
        }
    }
}

// FIX #1: Separate data class in repository package to avoid circular dependency.
// ChatViewModel.CachedSettings wraps this.
data class ChatViewModelSettings(
    val temperature: Float,
    val maxTokens: Int,
    val topP: Float,
    val contextSize: Int,
    val penaltyRepeat: Float,
    val topK: Int,
    val threadCount: Int,
    val showAdvancedSettings: Boolean,
    val minP: Float,
    val seed: Int,
    val xtcThreshold: Float,
    val xtcProbability: Float,
    val typicalP: Float,
    val penaltyLastN: Int,
    val penaltyFreq: Float,
    val penaltyPresent: Float,
    val mirostat: Int,
    val mirostatTau: Float,
    val mirostatEta: Float,
    val nProbs: Int,
    val jinja: Boolean,
    val enableThinking: Boolean,
    val includeThinkingInContext: Boolean
)
