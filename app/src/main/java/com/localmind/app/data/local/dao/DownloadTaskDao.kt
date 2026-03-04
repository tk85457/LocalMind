package com.localmind.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.localmind.app.data.local.entity.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: DownloadTaskEntity)

    @Query("SELECT * FROM download_tasks WHERE modelId = :modelId ORDER BY updatedAt DESC LIMIT 1")
    fun getTaskByModelFlow(modelId: String): Flow<DownloadTaskEntity?>

    @Query("SELECT * FROM download_tasks WHERE taskId = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: String): DownloadTaskEntity?

    @Query("DELETE FROM download_tasks WHERE modelId = :modelId")
    suspend fun deleteByModelId(modelId: String)

    @Query("SELECT * FROM download_tasks WHERE state IN ('RUNNING', 'ENQUEUED')")
    fun getActiveTasksFlow(): Flow<List<DownloadTaskEntity>>
}
