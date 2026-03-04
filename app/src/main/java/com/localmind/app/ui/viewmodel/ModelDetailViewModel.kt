package com.localmind.app.ui.viewmodel

import android.net.Uri
import java.io.File

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmind.app.data.repository.ModelRepository
import com.localmind.app.domain.model.Model
import com.localmind.app.llm.ActivationOptions
import com.localmind.app.llm.ActivationSource
import com.localmind.app.llm.ModelLifecycleManager
import com.localmind.app.core.engine.ModelMetadata
import com.localmind.app.core.performance.MemoryEstimator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelDetailViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val modelLifecycleManager: ModelLifecycleManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val modelId: String = Uri.decode(savedStateHandle.get<String>("modelId").orEmpty())

    private val _model = MutableStateFlow<Model?>(null)
    val model: StateFlow<Model?> = _model.asStateFlow()

    private val _metadata = MutableStateFlow<ModelMetadata?>(null)
    val metadata: StateFlow<ModelMetadata?> = _metadata.asStateFlow()

    private val _estimatedRAM = MutableStateFlow<String?>(null)
    val estimatedRAM: StateFlow<String?> = _estimatedRAM.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            _isLoading.value = true
            if (modelId.isBlank()) {
                _errorMessage.value = "Invalid navigation state: Model ID is missing"
                _isLoading.value = false
                return@launch
            }
            modelRepository.getModelFlowById(modelId).collect { m ->
                _model.value = m
                if (m != null) {
                    loadModelMetadata(m)
                } else {
                    _errorMessage.value = "Model info not found in database"
                    _isLoading.value = false
                }
            }
        }
    }

    private var metadataJob: kotlinx.coroutines.Job? = null

    private suspend fun loadModelMetadata(model: Model) {
        metadataJob?.cancel() // Clear pending requests
        metadataJob = viewModelScope.launch {
            _isLoading.value = true
            _metadata.value = null
            val readablePath = model.filePath.orEmpty()
            if (readablePath.isEmpty()) {
                _estimatedRAM.value = estimateRamText(model)
                _isLoading.value = false
                return@launch
            }

            val file = File(readablePath)
            if (file.exists()) {
                if (file.length() < model.sizeBytes) {
                    _errorMessage.value = "Model is still downloading (${file.length()} / ${model.sizeBytes} bytes)"
                    _estimatedRAM.value = estimateRamText(model)
                    _isLoading.value = false
                } else {
                    _estimatedRAM.value = estimateRamText(model)
                    _isLoading.value = false
                }
            } else {
                _errorMessage.value = "Model file not found"
                _estimatedRAM.value = estimateRamText(model)
                _isLoading.value = false
            }
        }
    }

    private fun estimateRamText(model: Model): String {
        // Stable heuristic to avoid native metadata parsing crashes for some GGUF variants.
        val modelResidentBytes = (model.sizeBytes.toDouble() * 1.22).toLong()
        val kvCacheBytes = model.contextLength.coerceIn(512, 8192).toLong() * 2_048L
        val runtimeOverheadBytes = 192L * 1024L * 1024L
        val estimate = (modelResidentBytes + kvCacheBytes + runtimeOverheadBytes).coerceAtLeast(model.sizeBytes)
        return MemoryEstimator.formatBytes(estimate)
    }
    fun activateModel() {
        viewModelScope.launch {
            val safeResult = modelLifecycleManager.activateModelSafely(
                modelId = modelId,
                options = ActivationOptions(source = ActivationSource.USER)
            )
            val finalResult = if (safeResult.isSuccess) {
                safeResult
            } else {
                modelLifecycleManager.activateModelSafely(
                    modelId = modelId,
                    options = ActivationOptions(
                        forceLoad = true,
                        source = ActivationSource.USER
                    )
                )
            }
            finalResult.onFailure {
                _errorMessage.value = it.message ?: "Could not start model on this phone."
            }
            // State is updated reactively via flow
        }
    }

    fun offloadModel() {
        viewModelScope.launch {
            _isLoading.value = true
            modelLifecycleManager.unloadModelSafely()
            // Flow will update the UI reactively
            _isLoading.value = false
        }
    }

    fun deleteModel(onSuccess: () -> Unit) {
        viewModelScope.launch {
            modelRepository.deleteModel(modelId)
            onSuccess()
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
