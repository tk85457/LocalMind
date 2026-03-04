package com.localmind.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmind.app.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ImportState {
    object Idle : ImportState()
    object Selecting : ImportState()
    data class Importing(val progress: Float) : ImportState()
    data class Success(val modelName: String) : ImportState()
    data class Error(val message: String) : ImportState()
}

@HiltViewModel
class ImportModelViewModel @Inject constructor(
    private val modelRepository: ModelRepository
) : ViewModel() {

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    fun importModel(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _importState.value = ImportState.Importing(0f)

            val result = modelRepository.importModel(uri, fileName)

            result.onSuccess { model ->
                _importState.value = ImportState.Success(model.name)
            }.onFailure { e ->
                _importState.value = ImportState.Error(e.message ?: "Import failed")
            }
        }
    }

    fun resetState() {
        _importState.value = ImportState.Idle
    }
}
