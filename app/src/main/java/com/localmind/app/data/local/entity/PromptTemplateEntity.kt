package com.localmind.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prompt_templates")
data class PromptTemplateEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val content: String,
    val category: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
