package com.replyhub.app.data

import kotlinx.coroutines.flow.Flow

class MessageRepository(private val dao: CapturedMessageDao) {
    fun observeMessages(): Flow<List<CapturedMessage>> = dao.observeAll()

    suspend fun all(): List<CapturedMessage> = dao.getAll()

    suspend fun save(message: CapturedMessage): Long = dao.insert(message.withConversationDefaults())

    suspend fun saveAll(messages: List<CapturedMessage>) =
        dao.insertAll(messages.map { it.withConversationDefaults() })

    suspend fun saveOrRefreshNotification(message: CapturedMessage): Long {
        val normalizedMessage = message.withConversationDefaults()
        val refreshed = dao.refreshNotification(
            packageName = normalizedMessage.packageName,
            rawNotificationKey = normalizedMessage.rawNotificationKey,
            timestamp = normalizedMessage.timestamp,
            sender = normalizedMessage.sender,
            conversationId = normalizedMessage.conversationId,
            conversationTitle = normalizedMessage.conversationTitle,
            originalText = normalizedMessage.originalText,
            detectedLanguage = normalizedMessage.detectedLanguage,
            translatedText = normalizedMessage.translatedText,
            englishTranslatedText = normalizedMessage.englishTranslatedText,
            priority = normalizedMessage.priority,
            hasRemoteInputAction = normalizedMessage.hasRemoteInputAction,
            attachmentKind = normalizedMessage.attachmentKind,
            attachmentPath = normalizedMessage.attachmentPath,
            attachmentName = normalizedMessage.attachmentName,
            attachmentMimeType = normalizedMessage.attachmentMimeType,
        )
        return if (refreshed > 0) normalizedMessage.id else dao.insert(normalizedMessage)
    }

    suspend fun recentForConversation(
        packageName: String,
        conversationId: String,
        limit: Int = 8,
    ): List<CapturedMessage> = dao.recentForConversation(packageName, conversationId, limit)

    suspend fun searchHistory(
        packageName: String,
        conversationId: String,
        query: String,
    ): List<CapturedMessage> = dao.searchForConversation(packageName, conversationId, query)

    suspend fun notificationExists(
        packageName: String,
        rawNotificationKey: String,
        timestamp: Long,
    ): Boolean = dao.notificationExists(packageName, rawNotificationKey, timestamp)

    suspend fun attachmentPathsOlderThan(timestamp: Long): List<String> =
        dao.attachmentPathsOlderThan(timestamp)

    suspend fun deleteOlderThan(timestamp: Long): Int = dao.deleteOlderThan(timestamp)

    suspend fun updateHandled(messageId: Long, handled: Boolean): Int =
        dao.updateHandled(messageId, handled)

    suspend fun attachmentPathsForConversation(
        packageName: String,
        conversationId: String,
    ): List<String> = dao.attachmentPathsForConversation(packageName, conversationId)

    suspend fun deleteConversation(packageName: String, conversationId: String): Int =
        dao.deleteConversation(packageName, conversationId)

    suspend fun updateTranslations(
        messageId: Long,
        expectedOriginalText: String,
        koreanTranslation: String,
        englishTranslation: String,
    ) = dao.updateTranslations(
        messageId = messageId,
        expectedOriginalText = expectedOriginalText,
        koreanTranslation = koreanTranslation,
        englishTranslation = englishTranslation,
    )

    suspend fun updateEnrichment(
        messageId: Long,
        expectedOriginalText: String,
        detectedLanguage: String,
        koreanTranslation: String,
        englishTranslation: String,
        priority: String,
    ) = dao.updateEnrichment(
        messageId = messageId,
        expectedOriginalText = expectedOriginalText,
        detectedLanguage = detectedLanguage,
        koreanTranslation = koreanTranslation,
        englishTranslation = englishTranslation,
        priority = priority,
    )

    suspend fun clear() = dao.deleteAll()

    private fun CapturedMessage.withConversationDefaults(): CapturedMessage = copy(
        conversationId = resolvedConversationId,
        conversationTitle = resolvedConversationTitle,
    )
}
