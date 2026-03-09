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
import com.localmind.app.data.local.entity.MessageFtsEntity
import com.localmind.app.data.local.entity.ModelEntity
import com.localmind.app.data.local.entity.PersonaEntity
import com.localmind.app.data.local.entity.PromptTemplateEntity
import com.localmind.app.data.local.dao.PromptTemplateDao

@Database(
    entities = [
        ModelEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        MessageFtsEntity::class,
        PersonaEntity::class,
        DownloadTaskEntity::class,
        PromptTemplateEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class LocalMindDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun personaDao(): PersonaDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun promptTemplateDao(): PromptTemplateDao

    companion object {
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN summary TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE personas ADD COLUMN preferredModelId TEXT DEFAULT NULL")
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

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `prompt_templates` (" +
                    "`id` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`content` TEXT NOT NULL, " +
                    "`category` TEXT, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
                )
            }
        }

        /**
         * v14: PocketPal WatermelonDB parity migration.
         * Adds:
         *  1. conversations.isPinned       — pin to top
         *  2. conversations.personaId      — link to persona
         *  3. conversations.totalTokens    — running token sum
         *  4. conversations.lastMessagePreview — cached preview
         *  5. conversations.lastMessageRole    — "user"/"assistant"
         *  6. message_fts FTS4 virtual table   — full-text search
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Check which columns are already present (safe upgrade)
                val convCursor = db.query("PRAGMA table_info(conversations)")
                val existingConvCols = mutableSetOf<String>()
                convCursor.use {
                    val nameIdx = it.getColumnIndex("name")
                    while (it.moveToNext()) {
                        if (nameIdx != -1) existingConvCols.add(it.getString(nameIdx))
                    }
                }

                // 2. Add new conversation columns if missing
                if ("isPinned" !in existingConvCols) {
                    db.execSQL("ALTER TABLE conversations ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                }
                if ("personaId" !in existingConvCols) {
                    db.execSQL("ALTER TABLE conversations ADD COLUMN personaId TEXT DEFAULT NULL")
                }
                if ("totalTokens" !in existingConvCols) {
                    db.execSQL("ALTER TABLE conversations ADD COLUMN totalTokens INTEGER NOT NULL DEFAULT 0")
                }
                if ("lastMessagePreview" !in existingConvCols) {
                    db.execSQL("ALTER TABLE conversations ADD COLUMN lastMessagePreview TEXT DEFAULT NULL")
                }
                if ("lastMessageRole" !in existingConvCols) {
                    db.execSQL("ALTER TABLE conversations ADD COLUMN lastMessageRole TEXT DEFAULT NULL")
                }

                // 3. Backfill totalTokens from existing message data
                db.execSQL("""
                    UPDATE conversations
                    SET totalTokens = COALESCE((
                        SELECT SUM(COALESCE(tokenCount, 0))
                        FROM messages
                        WHERE messages.conversationId = conversations.id
                    ), 0)
                """.trimIndent())

                // 4. Backfill lastMessagePreview from most recent message
                db.execSQL("""
                    UPDATE conversations
                    SET lastMessagePreview = (
                        SELECT SUBSTR(content, 1, 120)
                        FROM messages
                        WHERE messages.conversationId = conversations.id
                        ORDER BY timestamp DESC
                        LIMIT 1
                    ),
                    lastMessageRole = (
                        SELECT role
                        FROM messages
                        WHERE messages.conversationId = conversations.id
                        ORDER BY timestamp DESC
                        LIMIT 1
                    )
                """.trimIndent())

                // 5. Create FTS4 virtual table for full-text search on messages
                // content="" means external content table — keeps DB size small
                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS message_fts
                    USING fts4(content="messages", content)
                """.trimIndent())

                // 6. Populate FTS from existing messages
                db.execSQL("""
                    INSERT INTO message_fts(rowid, content)
                    SELECT rowid, content FROM messages
                """.trimIndent())

                // 7. Create index on conversations.personaId for fast persona queries
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversations_personaId ON conversations(personaId)")

                // 8. Create index on conversations.isPinned for fast pinned queries
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversations_isPinned ON conversations(isPinned, updatedAt DESC)")
            }
        }

        /**
         * v16: Rebuild conversations table to fix schema mismatch crash.
         * Root cause: Room's generated schema for conversations didn't match the
         * actual SQLite table on some upgrade paths, causing IllegalStateException.
         * Fix: Drop and recreate with the exact schema Room v16 expects.
         * NOTE: Conversations data is preserved via temp table backup.
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Backup existing data
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS conversations_backup AS
                    SELECT * FROM conversations
                """.trimIndent())

                // Drop old table and indexes
                db.execSQL("DROP INDEX IF EXISTS idx_conversations_personaId")
                db.execSQL("DROP INDEX IF EXISTS idx_conversations_isPinned")
                db.execSQL("DROP TABLE IF EXISTS conversations")

                // Recreate with exact Room v16 schema
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `conversations` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `modelId` TEXT NOT NULL,
                        `modelName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `messageCount` INTEGER NOT NULL DEFAULT 0,
                        `systemPrompt` TEXT,
                        `summary` TEXT,
                        `isHidden` INTEGER NOT NULL DEFAULT 0,
                        `isPinned` INTEGER NOT NULL DEFAULT 0,
                        `personaId` TEXT,
                        `totalTokens` INTEGER NOT NULL DEFAULT 0,
                        `lastMessagePreview` TEXT,
                        `lastMessageRole` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                // Restore data (only columns that exist in backup)
                db.execSQL("""
                    INSERT INTO conversations (
                        id, title, modelId, modelName, createdAt, updatedAt,
                        messageCount, systemPrompt, summary, isHidden,
                        isPinned, personaId, totalTokens, lastMessagePreview, lastMessageRole
                    )
                    SELECT
                        id, title, modelId, modelName, createdAt, updatedAt,
                        COALESCE(messageCount, 0),
                        systemPrompt,
                        summary,
                        COALESCE(isHidden, 0),
                        COALESCE(isPinned, 0),
                        personaId,
                        COALESCE(totalTokens, 0),
                        lastMessagePreview,
                        lastMessageRole
                    FROM conversations_backup
                """.trimIndent())

                // Drop backup
                db.execSQL("DROP TABLE IF EXISTS conversations_backup")

                // Recreate indexes exactly as Room expects
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_conversations_personaId` ON `conversations`(`personaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_conversations_isPinned` ON `conversations`(`isPinned` ASC, `updatedAt` DESC)")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // CRASH FIX: Room validates index definitions strictly against @Entity annotations.
                // MIGRATION_13_14 created idx_conversations_isPinned as:
                //   CREATE INDEX ... ON conversations(isPinned, updatedAt DESC)
                // But ConversationEntity declares it as Index.Order.ASC for isPinned + DESC for updatedAt.
                // Room generates a different CREATE INDEX statement, causing schema mismatch → crash.
                // Fix: Drop the old index and recreate it exactly as Room expects.
                db.execSQL("DROP INDEX IF EXISTS idx_conversations_isPinned")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_conversations_isPinned " +
                    "ON conversations(isPinned ASC, updatedAt DESC)"
                )

            }
        }
    }
}
