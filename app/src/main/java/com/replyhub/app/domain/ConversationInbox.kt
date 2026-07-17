package com.replyhub.app.domain

import com.replyhub.app.data.CapturedMessage
import com.replyhub.app.data.resolvedConversationId
import com.replyhub.app.data.resolvedConversationTitle

data class ConversationId(
    val packageName: String,
    val conversationId: String,
    val sender: String = conversationId,
)

data class ContactLink(
    val packageName: String,
    val sender: String,
    val contactId: String,
    val displayName: String,
    val conversationId: String = sender,
)

data class ConversationSummary(
    val id: ConversationId,
    val messages: List<CapturedMessage>,
    val displayName: String = id.sender,
    val channels: List<ConversationId> = listOf(id),
    val contactId: String? = null,
) {
    val latestMessage: CapturedMessage = messages.last()
    val latestIncomingMessage: CapturedMessage = messages.lastOrNull { !it.isOutgoing } ?: latestMessage
    val messageCount: Int = messages.size
    val urgentCount: Int = messages.count { it.priority == "URGENT" }
    val supportsQuickReply: Boolean = messages.any { it.hasRemoteInputAction }
    val needsReply: Boolean = !latestMessage.isOutgoing && !latestMessage.isHandled
    val isUrgent: Boolean = needsReply && latestMessage.priority == "URGENT"
    val hasForeignLanguage: Boolean = messages.any { message ->
        !message.isOutgoing && message.detectedLanguage.isForeignLanguage()
    }
}

data class SmartInboxSummary(
    val needsReplyCount: Int,
    val urgentCount: Int,
    val foreignLanguageCount: Int,
    val handledCount: Int,
)

fun summarizeInbox(
    conversations: List<ConversationSummary>,
    targetLanguage: String = "ko",
): SmartInboxSummary =
    SmartInboxSummary(
        needsReplyCount = conversations.count { it.needsReply },
        urgentCount = conversations.count { it.isUrgent },
        foreignLanguageCount = conversations.count {
            it.needsReply && it.hasForeignLanguage(targetLanguage)
        },
        handledCount = conversations.count { !it.needsReply },
    )

fun prioritizeInbox(
    conversations: List<ConversationSummary>,
    targetLanguage: String = "ko",
): List<ConversationSummary> =
    conversations.sortedWith(
        compareByDescending<ConversationSummary> { it.isUrgent }
            .thenByDescending { it.needsReply }
            .thenByDescending { it.hasForeignLanguage(targetLanguage) }
            .thenByDescending { it.latestMessage.timestamp },
    )

private fun ConversationSummary.hasForeignLanguage(targetLanguage: String): Boolean =
    messages.any { message ->
        !message.isOutgoing && !message.detectedLanguage.matchesLanguage(targetLanguage)
    }

fun buildConversationInbox(
    messages: List<CapturedMessage>,
    contactLinks: List<ContactLink> = emptyList(),
): List<ConversationSummary> {
    val linksByChannel = contactLinks.associateBy { it.packageName to it.conversationId }
    return messages
        .groupBy { message ->
            val channelKey = message.packageName to message.resolvedConversationId
            linksByChannel[channelKey]?.contactId?.let { "contact:$it" }
                ?: "channel:${message.packageName}:${message.resolvedConversationId}"
        }
        .map { (_, groupedMessages) ->
            val sortedMessages = groupedMessages.sortedBy { it.timestamp }
            val latest = sortedMessages.last()
            val id = ConversationId(
                packageName = latest.packageName,
                conversationId = latest.resolvedConversationId,
                sender = latest.resolvedConversationTitle,
            )
            val link = linksByChannel[id.packageName to id.conversationId]
            ConversationSummary(
                id = id,
                messages = sortedMessages,
                displayName = link?.displayName ?: id.sender,
                channels = sortedMessages
                    .map {
                        ConversationId(
                            packageName = it.packageName,
                            conversationId = it.resolvedConversationId,
                            sender = it.resolvedConversationTitle,
                        )
                    }
                    .distinctBy { it.packageName to it.conversationId },
                contactId = link?.contactId,
            )
        }
        .sortedByDescending { it.latestMessage.timestamp }
}

private fun String.isForeignLanguage(): Boolean {
    val normalized = trim().lowercase()
    return normalized.isNotBlank() && normalized !in setOf("ko", "kr", "kor", "korean")
}

private fun String.matchesLanguage(targetLanguage: String): Boolean {
    val normalized = trim().lowercase()
    val normalizedTarget = targetLanguage.trim().lowercase()
    if (normalized.isBlank()) return false
    return when (normalizedTarget) {
        "ko" -> normalized in setOf("ko", "kr", "kor", "korean")
        "en" -> normalized in setOf("en", "eng", "english")
        else -> normalized == normalizedTarget
    }
}
