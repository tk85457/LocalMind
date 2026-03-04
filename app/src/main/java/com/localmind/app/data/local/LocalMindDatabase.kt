package com.localmind.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.localmind.app.data.local.dao.ConversationDao
import com.localmind.app.data.local.dao.DownloadTaskDao
import com.localmind.app.data.local.dao.MessageDao
import com.localmind.app.data.local.dao.ModelDao
import com.localmind.app.data.local.dao.PersonaDao
import com.localmind.app.data.local.entity.ConversationEntity
import com.localmind.app.data.local.entity.DownloadTaskEntity
import com.localmind.app.data.local.entity.MessageEntity
import com.localmind.app.data.local.entity.ModelEntity
import com.localmind.app.data.local.entity.PersonaEntity

@Database(
    entities = [
        ModelEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        PersonaEntity::class,
        DownloadTaskEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class LocalMindDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun personaDao(): PersonaDao
    abstract fun downloadTaskDao(): DownloadTaskDao

    companion object {
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE conversations ADD COLUMN summary TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE personas ADD COLUMN preferredModelId TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                var hasSupportsVision = false
                val cursor = db.query("PRAGMA table_info(models)")
                cursor.use {
                    val nameIndex = it.getColumnIndex("name")
                    while (it.moveToNext()) {
                        if (nameIndex != -1 && it.getString(nameIndex) == "supportsVision") {
                            hasSupportsVision = true
                            break
                        }
                    }
                }

                if (!hasSupportsVision) {
                    db.execSQL("ALTER TABLE models ADD COLUMN supportsVision INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        // CRITICAL FIX: tokensPerSecond was added to MessageEntity but never migrated.
        // Any user upgrading from DB v6 or earlier had this column missing → Room schema
        // mismatch crash on app launch. This migration safely adds it if absent.
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                var hasTokensPerSecond = false
                val cursor = db.query("PRAGMA table_info(messages)")
                cursor.use {
                    val nameIndex = it.getColumnIndex("name")
                    while (it.moveToNext()) {
                        if (nameIndex != -1 && it.getString(nameIndex) == "tokensPerSecond") {
                            hasTokensPerSecond = true
                            break
                        }
                    }
                }
                if (!hasTokensPerSecond) {
                    db.execSQL("ALTER TABLE messages ADD COLUMN tokensPerSecond REAL DEFAULT NULL")
                }

                // Also add bosEnabled / eosEnabled / addGenPrompt to models if missing
                // (these were added directly at table creation but have no migration for upgrades)
                val modelsCursor = db.query("PRAGMA table_info(models)")
                val existingModelColumns = mutableSetOf<String>()
                modelsCursor.use {
                    val nameIndex = it.getColumnIndex("name")
                    while (it.moveToNext()) {
                        if (nameIndex != -1) existingModelColumns.add(it.getString(nameIndex))
                    }
                }
                if ("bosEnabled" !in existingModelColumns) {
                    db.execSQL("ALTER TABLE models ADD COLUMN bosEnabled INTEGER NOT NULL DEFAULT 1")
                }
                if ("eosEnabled" !in existingModelColumns) {
                    db.execSQL("ALTER TABLE models ADD COLUMN eosEnabled INTEGER NOT NULL DEFAULT 1")
                }
                if ("addGenPrompt" !in existingModelColumns) {
                    db.execSQL("ALTER TABLE models ADD COLUMN addGenPrompt INTEGER NOT NULL DEFAULT 1")
                }
            }
        }
    }
}
