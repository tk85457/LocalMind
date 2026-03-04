package com.localmind.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey
    val taskId: String,
    val modelId: String,
    val fileName: String,
    val state: String,
    val progress: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBps: Long,
    val etaSeconds: Long,
    val errorMessage: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
