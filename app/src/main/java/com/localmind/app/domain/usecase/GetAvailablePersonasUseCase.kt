package com.localmind.app.domain.usecase

import com.localmind.app.domain.model.Persona
import com.localmind.app.data.repository.PersonaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetAvailablePersonasUseCase @Inject constructor(
    private val personaRepository: PersonaRepository
) {
    operator fun invoke(): Flow<List<Persona>> {
        return personaRepository.getAllPersonas().map { dbPersonas ->
            // Built-in personas (DEFAULT + FEATURED) hamesha pehle dikhao
            // DB mein already saved built-in personas ko skip karo (duplicate nahi aaye)
            val dbIds = dbPersonas.map { it.id }.toSet()
            val builtIn = listOf(Persona.DEFAULT_ASSISTANT) + Persona.FEATURED_PERSONAS
            val builtInFiltered = builtIn.filter { it.id !in dbIds }
            // Built-in pehle, phir custom DB personas
            builtInFiltered + dbPersonas
        }
    }
}
