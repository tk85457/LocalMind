package com.localmind.app.data.repository

import com.localmind.app.data.local.dao.PersonaDao
import com.localmind.app.data.local.entity.toDomain
import com.localmind.app.data.local.entity.toEntity
import com.localmind.app.domain.model.Persona
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonaRepository @Inject constructor(
    private val personaDao: PersonaDao
) {
    fun getAllPersonas(): Flow<List<Persona>> {
        return personaDao.getAllPersonas().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getPersonaById(id: String): Persona? {
        return personaDao.getPersonaById(id)?.toDomain()
    }

    suspend fun savePersona(persona: Persona) {
        personaDao.insertPersona(persona.toEntity())
    }

    suspend fun deletePersona(persona: Persona) {
        personaDao.deletePersona(persona.toEntity())
    }

    suspend fun getDefaultPersona(): Persona {
        return personaDao.getDefaultPersona()?.toDomain() ?: Persona.DEFAULT_ASSISTANT
    }
}
