package com.replyhub.app.notifications

internal data class NotificationContent(
    val sender: String,
    val body: String,
)

internal fun selectNotificationContent(
    title: String,
    text: String,
    bigText: String,
    lines: List<String>,
    messagingSender: String? = null,
    messagingText: String? = null,
): NotificationContent {
    val sender = messagingSender
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: title.trim()
    val body = messagingText
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: text.trim().takeIf { it.isNotBlank() }
        ?: bigText.trim().takeIf { it.isNotBlank() }
        ?: lines.lastOrNull { it.isNotBlank() }?.trim().orEmpty()
    return NotificationContent(sender = sender, body = body)
}
