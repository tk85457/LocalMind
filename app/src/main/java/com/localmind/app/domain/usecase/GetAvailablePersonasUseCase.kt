package com.localmind.app.domain.usecase

import com.localmind.app.domain.model.Persona
import com.localmind.app.data.repository.PersonaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAvailablePersonasUseCase @Inject constructor(
    private val personaRepository: PersonaRepository
) {
    operator fun invoke(): Flow<List<Persona>> {
        return personaRepository.getAllPersonas()
    }
}
