package com.replyhub.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CapturedMessage::class], version = 6, exportSchema = false)
abstract class ReplyHubDatabase : RoomDatabase() {
    abstract fun capturedMessageDao(): CapturedMessageDao

    companion object {
        fun create(context: Context): ReplyHubDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ReplyHubDatabase::class.java,
                "replyhub.db",
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                )
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE captured_messages " +
                        "ADD COLUMN isOutgoing INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE captured_messages ADD COLUMN attachmentKind TEXT")
                db.execSQL("ALTER TABLE captured_messages ADD COLUMN attachmentPath TEXT")
                db.execSQL("ALTER TABLE captured_messages ADD COLUMN attachmentName TEXT")
                db.execSQL("ALTER TABLE captured_messages ADD COLUMN attachmentMimeType TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM captured_messages
                    WHERE id NOT IN (
                        SELECT MIN(id)
                        FROM captured_messages
                        GROUP BY packageName, rawNotificationKey, originalText, timestamp
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    index_captured_messages_packageName_rawNotificationKey_originalText_timestamp
                    ON captured_messages(packageName, rawNotificationKey, originalText, timestamp)
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE captured_messages " +
                        "ADD COLUMN englishTranslatedText TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE captured_messages " +
                        "ADD COLUMN conversationId TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE captured_messages " +
                        "ADD COLUMN conversationTitle TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "UPDATE captured_messages " +
                        "SET conversationId = sender, conversationTitle = sender " +
                        "WHERE conversationId = ''",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "index_captured_messages_packageName_conversationId " +
                        "ON captured_messages(packageName, conversationId)",
                )
            }
        }
    }
}
