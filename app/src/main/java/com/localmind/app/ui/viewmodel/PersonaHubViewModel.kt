package com.localmind.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmind.app.data.repository.PersonaRepository
import com.localmind.app.domain.model.Persona
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonaHubViewModel @Inject constructor(
    private val personaRepository: PersonaRepository,
    private val chatRepository: com.localmind.app.data.repository.ChatRepository,
    private val modelRepository: com.localmind.app.data.repository.ModelRepository
) : ViewModel() {

    private val _personas = MutableStateFlow<List<Persona>>(emptyList())
    val personas: StateFlow<List<Persona>> = _personas.asStateFlow()

    private val _selectedPersona = MutableStateFlow<Persona?>(null)
    val selectedPersona: StateFlow<Persona?> = _selectedPersona.asStateFlow()

    private val _models = MutableStateFlow<List<com.localmind.app.domain.model.Model>>(emptyList())
    val models: StateFlow<List<com.localmind.app.domain.model.Model>> = _models.asStateFlow()

    init {
        loadPersonas()
    }

    private fun loadPersonas() {
        viewModelScope.launch {
            personaRepository.getAllPersonas().collect {
                _personas.value = it
            }
        viewModelScope.launch {
            modelRepository.getAllModels().collect { list -> _models.value = list }
        }
        }
    }

    fun createPersona(
        name: String,
        icon: String,
        systemPrompt: String,
        contextMode: com.localmind.app.domain.model.PersonaContextMode,
        staticContext: String,
        preferredModelId: String?
    ) {
        viewModelScope.launch {
            val persona = Persona(
                name = name,
                icon = icon,
                systemPrompt = systemPrompt,
                contextMode = contextMode,
                staticContext = staticContext,
                preferredModelId = preferredModelId
            )
            personaRepository.savePersona(persona)
        }
    }

    fun deletePersona(persona: Persona) {
        viewModelScope.launch {
            personaRepository.deletePersona(persona)
            chatRepository.deleteConversationsBySystemPrompt(persona.systemPrompt)
        }
    }

    fun selectPersona(persona: Persona) {
        _selectedPersona.value = persona
    }

    fun updatePersona(persona: Persona) {
        viewModelScope.launch {
            personaRepository.savePersona(persona)
        }
    }
}
