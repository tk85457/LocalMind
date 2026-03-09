package com.localmind.app.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.localmind.app.core.Constants
import com.localmind.app.data.local.LocalMindDatabase
import com.localmind.app.data.local.dao.ConversationDao
import com.localmind.app.data.local.dao.DownloadTaskDao
import com.localmind.app.data.local.dao.MessageDao
import com.localmind.app.data.local.dao.ModelDao
import com.localmind.app.data.local.dao.PersonaDao
import com.localmind.app.data.local.dao.PromptTemplateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS models_new (
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    filePath TEXT,
                    sizeBytes INTEGER NOT NULL,
                    quantization TEXT NOT NULL,
                    contextLength INTEGER NOT NULL,
                    parameterCount TEXT NOT NULL,
                    installDate INTEGER NOT NULL,
                    isActive INTEGER NOT NULL,
                    lastUsed INTEGER,
                    storageType TEXT NOT NULL,
                    storageUri TEXT,
                    fileName TEXT NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO models_new (
                    id,
                    name,
                    filePath,
                    sizeBytes,
                    quantization,
                    contextLength,
                    parameterCount,
                    installDate,
                    isActive,
                    lastUsed,
                    storageType,
                    storageUri,
                    fileName
                )
                SELECT
                    id,
                    name,
                    filePath,
                    sizeBytes,
                    quantization,
                    contextLength,
                    parameterCount,
                    installDate,
                    isActive,
                    lastUsed,
                    'INTERNAL',
                    NULL,
                    CASE
                        WHEN filePath IS NULL OR length(filePath) = 0 THEN id || '.gguf'
                        ELSE filePath
                    END
                FROM models
                """.trimIndent()
            )

            db.execSQL("DROP TABLE models")
            db.execSQL("ALTER TABLE models_new RENAME TO models")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE models ADD COLUMN templateId TEXT NOT NULL DEFAULT 'chatml_default'")
            db.execSQL("ALTER TABLE models ADD COLUMN stopTokensJson TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE models ADD COLUMN recommendedTemperature REAL NOT NULL DEFAULT 0.7")
            db.execSQL("ALTER TABLE models ADD COLUMN recommendedTopP REAL NOT NULL DEFAULT 0.9")
            db.execSQL("ALTER TABLE models ADD COLUMN recommendedTopK INTEGER NOT NULL DEFAULT 40")
            db.execSQL("ALTER TABLE models ADD COLUMN recommendedRepeatPenalty REAL NOT NULL DEFAULT 1.1")
            db.execSQL("ALTER TABLE models ADD COLUMN recommendedSystemPrompt TEXT")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS personas (
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    icon TEXT NOT NULL,
                    systemPrompt TEXT NOT NULL,
                    isDefault INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Version 5 keeps the same schema shape as version 4.
            // No-op migration prevents destructive reset and keeps existing user data.
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN reasoningContent TEXT")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN imageUri TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN inferenceSource TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN ttftMs INTEGER")
            db.execSQL("ALTER TABLE messages ADD COLUMN generationMs INTEGER")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS download_tasks (
                    taskId TEXT NOT NULL,
                    modelId TEXT NOT NULL,
                    fileName TEXT NOT NULL,
                    state TEXT NOT NULL,
                    progress INTEGER NOT NULL,
                    downloadedBytes INTEGER NOT NULL,
                    totalBytes INTEGER NOT NULL,
                    speedBps INTEGER NOT NULL,
                    etaSeconds INTEGER NOT NULL,
                    errorMessage TEXT,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(taskId)
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): LocalMindDatabase {
        return Room.databaseBuilder(
            context,
            LocalMindDatabase::class.java,
            Constants.DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            .addMigrations(MIGRATION_3_4)
            .addMigrations(MIGRATION_4_5)
            .addMigrations(MIGRATION_5_6)
            .addMigrations(MIGRATION_6_7)
            .addMigrations(MIGRATION_7_8)
            .addMigrations(LocalMindDatabase.MIGRATION_8_9)
            .addMigrations(LocalMindDatabase.MIGRATION_9_10)
            .addMigrations(LocalMindDatabase.MIGRATION_10_11)
            .addMigrations(LocalMindDatabase.MIGRATION_11_12)
            .addMigrations(LocalMindDatabase.MIGRATION_12_13)
            .addMigrations(LocalMindDatabase.MIGRATION_13_14)
            .addMigrations(LocalMindDatabase.MIGRATION_14_15)
            .addMigrations(LocalMindDatabase.MIGRATION_15_16)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideModelDao(database: LocalMindDatabase): ModelDao {
        return database.modelDao()
    }

    @Provides
    @Singleton
    fun provideConversationDao(database: LocalMindDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: LocalMindDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    @Singleton
    fun providePersonaDao(database: LocalMindDatabase): PersonaDao {
        return database.personaDao()
    }

    @Provides
    @Singleton
    fun provideDownloadTaskDao(database: LocalMindDatabase): DownloadTaskDao {
        return database.downloadTaskDao()
    }

    @Provides
    @Singleton
    fun providePromptTemplateDao(database: LocalMindDatabase): PromptTemplateDao {
        return database.promptTemplateDao()
    }
}
