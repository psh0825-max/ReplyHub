package com.replyhub.app.notifications

import java.util.Locale

internal data class NotificationConversationIdentity(
    val id: String,
    val title: String,
)

internal fun buildNotificationConversationIdentity(
    shortcutId: String?,
    notificationTag: String?,
    notificationId: Int,
    conversationTitle: String,
    title: String,
    subText: String,
    sender: String,
): NotificationConversationIdentity {
    val displayTitle = sequenceOf(conversationTitle, title, subText, sender)
        .map(String::trim)
        .firstOrNull(String::isNotBlank)
        ?: sender
    val stableId = when {
        !shortcutId.isNullOrBlank() -> "shortcut:${shortcutId.trim()}"
        conversationTitle.isNotBlank() -> "conversation:${conversationTitle.normalizedIdentity()}"
        !notificationTag.isNullOrBlank() -> "tag:${notificationTag.trim()}"
        displayTitle.isNotBlank() -> "title:${displayTitle.normalizedIdentity()}"
        else -> "notification:$notificationId"
    }
    return NotificationConversationIdentity(stableId, displayTitle)
}

private fun String.normalizedIdentity(): String =
    trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
