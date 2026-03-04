package com.localmind.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.localmind.app.domain.model.Persona
import java.util.UUID

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val icon: String,
    val systemPrompt: String,
    val isDefault: Boolean,
    val preferredModelId: String? = null,
    val createdAt: Long
)

fun PersonaEntity.toDomain(): Persona = Persona(
    id = id,
    name = name,
    icon = icon,
    systemPrompt = systemPrompt,
    isDefault = isDefault,
    createdAt = createdAt,
    preferredModelId = preferredModelId
)

fun Persona.toEntity(): PersonaEntity = PersonaEntity(
    id = id,
    name = name,
    icon = icon,
    systemPrompt = systemPrompt,
    isDefault = isDefault,
    createdAt = createdAt,
    preferredModelId = preferredModelId
)
