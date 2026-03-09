package com.localmind.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.localmind.app.data.local.entity.PromptTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptTemplateDao {
    @Query("SELECT * FROM prompt_templates ORDER BY timestamp DESC")
    fun getAllPromptsFlow(): Flow<List<PromptTemplateEntity>>

    @Query("SELECT * FROM prompt_templates WHERE id = :id")
    suspend fun getPromptById(id: String): PromptTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompt(prompt: PromptTemplateEntity)

    @Delete
    suspend fun deletePrompt(prompt: PromptTemplateEntity)

    @Query("DELETE FROM prompt_templates WHERE id = :id")
    suspend fun deletePromptById(id: String)
}
