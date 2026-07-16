package com.replyhub.app.notifications

internal fun buildReplySenderAliases(vararg values: String?): Set<String> =
    values.mapNotNullTo(linkedSetOf()) { value ->
        value?.trim()?.takeIf(String::isNotBlank)
    }

internal fun replySenderMatches(sender: String, aliases: Set<String>): Boolean =
    aliases.any { it.equals(sender.trim(), ignoreCase = true) }

internal fun replyTargetMatches(
    targetNotificationKey: String,
    targetPackageName: String,
    notificationKey: String,
    packageName: String,
): Boolean = targetPackageName == packageName && targetNotificationKey == notificationKey

internal fun replyConversationMatches(
    targetPackageName: String,
    targetConversationId: String,
    packageName: String,
    conversationId: String,
): Boolean = conversationId.isNotBlank() &&
    targetPackageName == packageName &&
    targetConversationId == conversationId

internal data class ReplyTargetIdentity(
    val notificationKey: String,
    val packageName: String,
    val conversationId: String,
    val senderAliases: Set<String>,
)

internal fun selectReplyTargetKey(
    targets: Collection<ReplyTargetIdentity>,
    notificationKey: String,
    packageName: String,
    conversationId: String,
    sender: String,
): String? {
    targets.firstOrNull {
        replyTargetMatches(
            targetNotificationKey = it.notificationKey,
            targetPackageName = it.packageName,
            notificationKey = notificationKey,
            packageName = packageName,
        )
    }?.let { return it.notificationKey }

    val conversationMatches = targets.filter {
        replyConversationMatches(
            targetPackageName = it.packageName,
            targetConversationId = it.conversationId,
            packageName = packageName,
            conversationId = conversationId,
        )
    }
    if (conversationMatches.size == 1) return conversationMatches.single().notificationKey
    if (conversationMatches.size > 1) return null

    return targets.filter {
        it.packageName == packageName && replySenderMatches(sender, it.senderAliases)
    }.singleOrNull()?.notificationKey
}
