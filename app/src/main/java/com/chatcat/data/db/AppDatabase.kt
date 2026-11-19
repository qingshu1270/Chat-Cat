package com.chatcat.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        BlockEntity::class,
        AttachmentEntity::class,
        UsageRecordEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        const val NAME = "chatcat.db"

        /** v2：多版本回答（active）+ 思考耗时（thinkingMs） */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN active INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE messages ADD COLUMN thinkingMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3：附件表（Files API 的 file_id + 本地原件） */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `attachments` (
                        `id` TEXT NOT NULL,
                        `messageId` TEXT NOT NULL,
                        `ordinal` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `mime` TEXT NOT NULL,
                        `size` INTEGER NOT NULL,
                        `isImage` INTEGER NOT NULL,
                        `fileId` TEXT,
                        `localUri` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_messageId` ON `attachments` (`messageId`)")
            }
        }

        /** v4：用量记录（每条回答一次或多次请求，按行累加） */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `usage_records` (
                        `id` TEXT NOT NULL,
                        `messageId` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `model` TEXT NOT NULL,
                        `inputTokens` INTEGER NOT NULL,
                        `outputTokens` INTEGER NOT NULL,
                        `cacheReadTokens` INTEGER NOT NULL,
                        `cacheCreationTokens` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_records_messageId` ON `usage_records` (`messageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_records_sessionId` ON `usage_records` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_records_createdAt` ON `usage_records` (`createdAt`)")
            }
        }
    }
}
