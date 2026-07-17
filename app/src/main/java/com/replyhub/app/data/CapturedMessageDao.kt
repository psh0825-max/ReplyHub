package com.replyhub.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedMessageDao {
    @Query("SELECT * FROM captured_messages ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CapturedMessage>>

    @Query("SELECT * FROM captured_messages ORDER BY timestamp DESC")
    suspend fun getAll(): List<CapturedMessage>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: CapturedMessage): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<CapturedMessage>)

    @Query(
        """
        UPDATE captured_messages
        SET sender = :sender,
            conversationId = :conversationId,
            conversationTitle = :conversationTitle,
            isHandled = CASE WHEN originalText = :originalText THEN isHandled ELSE 0 END,
            originalText = :originalText,
            detectedLanguage = :detectedLanguage,
            translatedText = :translatedText,
            englishTranslatedText = :englishTranslatedText,
            priority = :priority,
            hasRemoteInputAction = :hasRemoteInputAction,
            attachmentKind = :attachmentKind,
            attachmentPath = :attachmentPath,
            attachmentName = :attachmentName,
            attachmentMimeType = :attachmentMimeType
        WHERE packageName = :packageName
          AND rawNotificationKey = :rawNotificationKey
          AND timestamp = :timestamp
        """,
    )
    suspend fun refreshNotification(
        packageName: String,
        rawNotificationKey: String,
        timestamp: Long,
        sender: String,
        conversationId: String,
        conversationTitle: String,
        originalText: String,
        detectedLanguage: String,
        translatedText: String,
        englishTranslatedText: String,
        priority: String,
        hasRemoteInputAction: Boolean,
        attachmentKind: String?,
        attachmentPath: String?,
        attachmentName: String?,
        attachmentMimeType: String?,
    ): Int

    @Query(
        """
        SELECT * FROM captured_messages
        WHERE packageName = :packageName AND conversationId = :conversationId
        ORDER BY timestamp DESC
        LIMIT :limit
        """,
    )
    suspend fun recentForConversation(
        packageName: String,
        conversationId: String,
        limit: Int,
    ): List<CapturedMessage>

    @Query(
        """
        SELECT * FROM captured_messages
        WHERE packageName = :packageName
          AND conversationId = :conversationId
          AND (originalText LIKE '%' || :query || '%'
               OR translatedText LIKE '%' || :query || '%'
               OR englishTranslatedText LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
        LIMIT :limit
        """,
    )
    suspend fun searchForConversation(
        packageName: String,
        conversationId: String,
        query: String,
        limit: Int = 5,
    ): List<CapturedMessage>

    @Query(
        """
        UPDATE captured_messages
        SET translatedText = :koreanTranslation,
            englishTranslatedText = :englishTranslation
        WHERE id = :messageId
          AND originalText = :expectedOriginalText
        """,
    )
    suspend fun updateTranslations(
        messageId: Long,
        expectedOriginalText: String,
        koreanTranslation: String,
        englishTranslation: String,
    ): Int

    @Query(
        """
        UPDATE captured_messages
        SET detectedLanguage = :detectedLanguage,
            translatedText = :koreanTranslation,
            englishTranslatedText = :englishTranslation,
            priority = :priority
        WHERE id = :messageId
          AND originalText = :expectedOriginalText
        """,
    )
    suspend fun updateEnrichment(
        messageId: Long,
        expectedOriginalText: String,
        detectedLanguage: String,
        koreanTranslation: String,
        englishTranslation: String,
        priority: String,
    ): Int

    @Query("UPDATE captured_messages SET isHandled = :handled WHERE id = :messageId")
    suspend fun updateHandled(messageId: Long, handled: Boolean): Int

    @Query(
        "SELECT attachmentPath FROM captured_messages " +
            "WHERE packageName = :packageName AND conversationId = :conversationId " +
            "AND attachmentPath IS NOT NULL",
    )
    suspend fun attachmentPathsForConversation(
        packageName: String,
        conversationId: String,
    ): List<String>

    @Query(
        "DELETE FROM captured_messages " +
            "WHERE packageName = :packageName AND conversationId = :conversationId",
    )
    suspend fun deleteConversation(packageName: String, conversationId: String): Int

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM captured_messages
            WHERE packageName = :packageName
              AND rawNotificationKey = :rawNotificationKey
              AND timestamp = :timestamp
        )
        """,
    )
    suspend fun notificationExists(
        packageName: String,
        rawNotificationKey: String,
        timestamp: Long,
    ): Boolean

    @Query(
        "SELECT attachmentPath FROM captured_messages " +
            "WHERE timestamp < :timestamp AND attachmentPath IS NOT NULL",
    )
    suspend fun attachmentPathsOlderThan(timestamp: Long): List<String>

    @Query("DELETE FROM captured_messages WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    @Query("DELETE FROM captured_messages")
    suspend fun deleteAll()
}
