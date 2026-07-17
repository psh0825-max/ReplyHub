package com.replyhub.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "captured_messages",
    indices = [
        Index("sender"),
        Index("timestamp"),
        Index(value = ["packageName", "conversationId"]),
        Index(
            value = ["packageName", "rawNotificationKey", "originalText", "timestamp"],
            unique = true,
        ),
    ],
)
data class CapturedMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val sender: String,
    val originalText: String,
    val detectedLanguage: String,
    val translatedText: String,
    val timestamp: Long,
    val priority: String,
    val hasRemoteInputAction: Boolean,
    val rawNotificationKey: String,
    val isOutgoing: Boolean = false,
    val attachmentKind: String? = null,
    val attachmentPath: String? = null,
    val attachmentName: String? = null,
    val attachmentMimeType: String? = null,
    val englishTranslatedText: String = "",
    val conversationId: String = "",
    val conversationTitle: String = "",
    val isHandled: Boolean = false,
)

val CapturedMessage.resolvedConversationId: String
    get() = conversationId.ifBlank { sender }

val CapturedMessage.resolvedConversationTitle: String
    get() = conversationTitle.ifBlank { sender }

fun CapturedMessage.translationFor(language: AppLanguage): String = when {
    detectedLanguage == language.storageValue -> originalText
    language == AppLanguage.KOREAN -> translatedText.ifBlank { originalText }
    else -> englishTranslatedText.ifBlank { originalText }
}

fun CapturedMessage.needsTranslationRefresh(): Boolean {
    val normalizedLanguage = detectedLanguage.trim().lowercase()
    val koreanMissing = normalizedLanguage !in setOf("ko", "kr", "kor", "korean") &&
        (translatedText.isBlank() || translatedText.trim() == originalText.trim())
    val englishMissing = normalizedLanguage !in setOf("en", "eng", "english") &&
        (englishTranslatedText.isBlank() ||
            englishTranslatedText.trim() == originalText.trim())
    return koreanMissing || englishMissing
}
