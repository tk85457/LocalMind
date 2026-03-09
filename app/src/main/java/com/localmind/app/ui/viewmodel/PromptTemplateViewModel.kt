package com.localmind.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmind.app.data.local.dao.PromptTemplateDao
import com.localmind.app.data.local.entity.PromptTemplateEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PromptTemplateViewModel @Inject constructor(
    private val promptTemplateDao: PromptTemplateDao
) : ViewModel() {

    val templates = promptTemplateDao.getAllPromptsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveTemplate(template: PromptTemplateEntity) {
        viewModelScope.launch {
            promptTemplateDao.insertPrompt(template)
        }
    }

    fun deleteTemplate(template: PromptTemplateEntity) {
        viewModelScope.launch {
            promptTemplateDao.deletePrompt(template)
        }
    }
}
