package com.localmind.app.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 virtual table for full-text search across messages.
 * PocketPal WatermelonDB equivalent: indexed queries on message content.
 *
 * Room generates: CREATE VIRTUAL TABLE message_fts USING fts4(...)
 * Usage: SELECT rowid FROM message_fts WHERE message_fts MATCH :query
 * Then join with messages table on rowid = messages.rowid
 */
@Fts4(contentEntity = MessageEntity::class)
@Entity(tableName = "message_fts")
data class MessageFtsEntity(
    val content: String
)
