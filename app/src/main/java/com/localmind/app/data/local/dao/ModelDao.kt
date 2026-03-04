package com.localmind.app.data.local.dao

import androidx.room.*
import com.localmind.app.data.local.entity.ModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {

    @Query("SELECT * FROM models ORDER BY lastUsed DESC")
    fun getAllModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models ORDER BY installDate DESC")
    suspend fun getAllModelsSync(): List<ModelEntity>

    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun getModelById(id: String): ModelEntity?

    @Query("SELECT * FROM models WHERE id = :id")
    fun getModelFlowById(id: String): Flow<ModelEntity?>

    @Query("SELECT * FROM models WHERE fileName = :fileName LIMIT 1")
    suspend fun getModelByFileName(fileName: String): ModelEntity?

    @Query("SELECT * FROM models WHERE storageUri = :storageUri LIMIT 1")
    suspend fun getModelByStorageUri(storageUri: String): ModelEntity?

    @Query("SELECT * FROM models WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveModel(): ModelEntity?

    @Query("SELECT * FROM models WHERE isActive = 1 LIMIT 1")
    fun getActiveModelFlow(): Flow<ModelEntity?>

    @Query("SELECT * FROM models ORDER BY installDate DESC LIMIT 1")
    suspend fun getMostRecentModel(): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelEntity)

    @Update
    suspend fun updateModel(model: ModelEntity)

    @Delete
    suspend fun deleteModel(model: ModelEntity)

    @Query("UPDATE models SET isActive = 0")
    suspend fun deactivateAllModels()

    @Transaction
    suspend fun activateModelTransaction(id: String) {
        deactivateAllModels()
        activateModel(id)
    }

    @Query("UPDATE models SET isActive = 1 WHERE id = :id")
    suspend fun activateModel(id: String)

    @Query("UPDATE models SET lastUsed = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: String, timestamp: Long)

    @Query("SELECT COUNT(*) FROM models")
    suspend fun getModelCount(): Int
}
