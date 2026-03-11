package com.localmind.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmind.app.data.repository.ModelRepository
import com.localmind.app.domain.model.Model
import com.localmind.app.llm.ActivationOptions
import com.localmind.app.llm.ActivationSource
import com.localmind.app.llm.ModelLifecycleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelManagerViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val modelLifecycleManager: ModelLifecycleManager
) : ViewModel() {
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _processingModelId = MutableStateFlow<String?>(null)
    val processingModelId: StateFlow<String?> = _processingModelId.asStateFlow()

    val models: StateFlow<List<Model>> = modelRepository.getAllModels()
        .onEach { _isLoading.value = false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeModel: StateFlow<Model?> = models
        .map { list -> list.firstOrNull { it.isActive } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun activateModel(model: Model) {
        activateModelById(model.id)
    }

    fun activateModelById(modelId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _processingModelId.value = modelId
            runCatching {
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
            }.onFailure {
                _errorMessage.value = it.message ?: "Could not start model on this phone."
            }
            _processingModelId.value = null
            _isLoading.value = false
        }
    }

    fun offloadModel() {
        viewModelScope.launch {
            _isLoading.value = true
            _processingModelId.value = activeModel.value?.id
            modelLifecycleManager.unloadModelSafely()
            _processingModelId.value = null
            _isLoading.value = false
        }
    }

    fun deleteModel(model: Model) {
        viewModelScope.launch {
            _isLoading.value = true
            _processingModelId.value = model.id
            runCatching {
                modelRepository.deleteModel(model.id)
            }.onFailure {
                _errorMessage.value = it.message ?: "Failed to delete model"
            }
            _processingModelId.value = null
            _isLoading.value = false
        }
    }

    fun reportError(message: String) {
        _errorMessage.value = message
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun updateModelSettings(
        modelId: String,
        bos: Boolean,
        eos: Boolean,
        addGenPrompt: Boolean,
        systemPrompt: String,
        stopWords: List<String>,
        templateId: String = ""
    ) {
        viewModelScope.launch {
            modelRepository.updateModelSettings(modelId, bos, eos, addGenPrompt, systemPrompt, stopWords, templateId)
        }
    }
}
