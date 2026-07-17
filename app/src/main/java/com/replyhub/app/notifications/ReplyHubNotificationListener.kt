package com.replyhub.app.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.replyhub.app.ReplyHubApplication
import com.replyhub.app.data.CapturedMessage
import com.replyhub.app.messaging.MessengerCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private data class MessagingNotificationContent(
    val sender: String?,
    val text: String,
)

class ReplyHubNotificationListener : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val replyHubApplication by lazy { application as ReplyHubApplication }
    private val processor by lazy { replyHubApplication.messageProcessor }
    private val attachmentExtractor by lazy { NotificationAttachmentExtractor(this) }
    private val deduplicator = RecentNotificationDeduplicator()

    override fun onListenerConnected() {
        super.onListenerConnected()
        connectedInstance = this
        refreshActiveReplyTargets()
        captureActiveNotifications()
        serviceScope.launch {
            replyHubApplication.messageRetentionManager.pruneIfDue()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        captureNotification(sbn)
    }

    private fun captureNotification(sbn: StatusBarNotification) {
        if (replyHubApplication.demoModeStore.isEnabled()) return
        if (sbn.packageName !in SUPPORTED_PACKAGES) return
        if (!replyHubApplication.privacySettingsStore.isCaptureEnabled(sbn.packageName)) return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val conversationTitle = extras
            .getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()
            .orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map(CharSequence::toString)
            .orEmpty()
        val messagingMessage = latestMessagingContent(extras)
        val content = selectNotificationContent(
            title = title,
            text = text,
            bigText = bigText,
            lines = lines,
            messagingSender = messagingMessage?.sender,
            messagingText = messagingMessage?.text,
        )

        val sender = content.sender.ifBlank { appLabel(sbn.packageName) }
        val senderAliases = buildReplySenderAliases(
            sender,
            title,
            subText,
            conversationTitle,
            messagingMessage?.sender,
        )
        val conversation = buildNotificationConversationIdentity(
            shortcutId = notification.shortcutId,
            notificationTag = sbn.tag,
            notificationId = sbn.id,
            conversationTitle = conversationTitle,
            title = title,
            subText = subText,
            sender = sender,
        )
        val hasRemoteInput = registerReplyTargets(
            sbn = sbn,
            sender = sender,
            conversationId = conversation.id,
            senderAliases = senderAliases,
        )
        val body = content.body.take(MAX_MESSAGE_CHARACTERS)
        Log.i(
            TAG,
            "notification package=${sbn.packageName}, actions=${notification.actions.orEmpty().size}, " +
                "bodyCharacters=${body.length}, textCharacters=${text.length}, " +
                "bigTextCharacters=${bigText.length}, messagingStyle=${messagingMessage != null}, " +
                "hasSubText=${subText.isNotBlank()}, hasRemoteInput=$hasRemoteInput",
        )

        val repository = replyHubApplication.repository
        serviceScope.launch {
            if (replyHubApplication.demoModeStore.isEnabled()) return@launch
            if (
                !deduplicator.shouldProcess(
                    packageName = sbn.packageName,
                    notificationKey = sbn.key,
                    body = body.ifBlank { ATTACHMENT_FINGERPRINT },
                    postTime = sbn.postTime,
                )
            ) {
                return@launch
            }
            if (repository.notificationExists(sbn.packageName, sbn.key, sbn.postTime)) {
                return@launch
            }
            val attachment = attachmentExtractor.extract(notification, body)
            val messageBody = body.ifBlank {
                when (attachment?.kind) {
                    AttachmentKinds.IMAGE,
                    AttachmentKinds.IMAGE_UNAVAILABLE,
                    -> "사진"
                    AttachmentKinds.FILE,
                    AttachmentKinds.FILE_UNAVAILABLE,
                    -> attachment.name
                    else -> ""
                }
            }
            if (messageBody.isBlank()) return@launch
            Log.i(
                TAG,
                "attachment kind=${attachment?.kind}, copied=${attachment?.path != null}",
            )
            val pendingMessage = CapturedMessage(
                packageName = sbn.packageName,
                sender = sender,
                originalText = messageBody,
                detectedLanguage = "",
                translatedText = messageBody,
                englishTranslatedText = messageBody,
                timestamp = sbn.postTime,
                priority = "LATER",
                hasRemoteInputAction = hasRemoteInput,
                rawNotificationKey = sbn.key,
                attachmentKind = attachment?.kind,
                attachmentPath = attachment?.path,
                attachmentName = attachment?.name,
                attachmentMimeType = attachment?.mimeType,
                conversationId = conversation.id,
                conversationTitle = conversation.title,
            )
            repository.saveOrRefreshNotification(pendingMessage)

            val enrichment = processor.enrich(messageBody)
            if (replyHubApplication.demoModeStore.isEnabled()) return@launch
            repository.saveOrRefreshNotification(
                pendingMessage.copy(
                    detectedLanguage = enrichment.detectedLanguage,
                    translatedText = enrichment.translatedText,
                    englishTranslatedText = enrichment.englishTranslatedText,
                    priority = enrichment.priority,
                ),
            )
        }
    }

    private fun latestMessagingContent(extras: Bundle): MessagingNotificationContent? =
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?.asSequence()
            ?.filterIsInstance<Bundle>()
            ?.mapNotNull { message ->
                val text = message.getCharSequence(MESSAGING_TEXT_KEY)
                    ?.toString()
                    ?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                MessagingNotificationContent(
                    sender = message.getCharSequence(MESSAGING_SENDER_KEY)?.toString(),
                    text = text,
                )
            }
            ?.lastOrNull()

    private fun captureActiveNotifications() {
        if (replyHubApplication.demoModeStore.isEnabled()) return
        activeNotifications
            .orEmpty()
            .filter { it.packageName in SUPPORTED_PACKAGES }
            .filter { replyHubApplication.privacySettingsStore.isCaptureEnabled(it.packageName) }
            .forEach(::captureNotification)
    }

    override fun onDestroy() {
        if (connectedInstance === this) connectedInstance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationReplyStore.remove(sbn.key)
        super.onNotificationRemoved(sbn)
    }

    private fun refreshActiveReplyTargets(): Boolean = runCatching {
        if (replyHubApplication.demoModeStore.isEnabled()) {
            NotificationReplyStore.clear()
            return@runCatching true
        }
        val active = activeNotifications
            .orEmpty()
            .filter { it.packageName in SUPPORTED_PACKAGES }
            .filter { replyHubApplication.privacySettingsStore.isCaptureEnabled(it.packageName) }
        active.forEach { sbn ->
                val extras = sbn.notification.extras
                val title = extras
                    .getCharSequence(Notification.EXTRA_TITLE)
                    ?.toString()
                    .orEmpty()
                val messagingSender = latestMessagingContent(extras)?.sender
                val sender = messagingSender
                    ?.takeIf(String::isNotBlank)
                    ?: title.ifBlank { appLabel(sbn.packageName) }
                val conversation = buildNotificationConversationIdentity(
                    shortcutId = sbn.notification.shortcutId,
                    notificationTag = sbn.tag,
                    notificationId = sbn.id,
                    conversationTitle = extras
                        .getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                        ?.toString()
                        .orEmpty(),
                    title = title,
                    subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
                        ?.toString()
                        .orEmpty(),
                    sender = sender,
                )
                registerReplyTargets(
                    sbn = sbn,
                    sender = sender,
                    conversationId = conversation.id,
                    senderAliases = buildReplySenderAliases(
                        sender,
                        title,
                        extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
                        extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
                        messagingSender,
                    ),
                )
            }
        NotificationReplyStore.retainOnly(active.mapTo(hashSetOf()) { it.key })
        true
    }.onFailure {
        Log.w(TAG, "Unable to refresh active reply targets", it)
    }.getOrDefault(false)

    private fun registerReplyTargets(
        sbn: StatusBarNotification,
        sender: String,
        conversationId: String,
        senderAliases: Set<String>,
    ): Boolean {
        val actionScan = scanReplyActions(sbn.notification)
        val replyCandidate = actionScan.selected
        Log.i(
            TAG,
            "reply actions visible=${actionScan.visibleCount}, " +
                "invisible=${actionScan.invisibleCount}, wearable=${actionScan.wearableCount}, " +
                "selected=${replyCandidate?.source?.name?.lowercase() ?: "none"}",
        )
        replyCandidate?.let { candidate ->
            val action = candidate.action
            NotificationReplyStore.register(
                notificationKey = sbn.key,
                packageName = sbn.packageName,
                conversationId = conversationId,
                sender = sender,
                senderAliases = senderAliases,
                action = action,
                remoteInputs = action.remoteInputs.orEmpty().map { it }.toTypedArray(),
            )
        } ?: NotificationReplyStore.remove(sbn.key)
        return replyCandidate != null
    }

    private fun appLabel(packageName: String): String = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    companion object {
        private const val TAG = "ReplyHubNotification"
        private const val MESSAGING_TEXT_KEY = "text"
        private const val MESSAGING_SENDER_KEY = "sender"
        private const val MAX_MESSAGE_CHARACTERS = 4_000
        private const val ATTACHMENT_FINGERPRINT = "__replyhub_attachment__"

        @Volatile
        private var connectedInstance: ReplyHubNotificationListener? = null

        fun refreshReplyTargets(): Boolean =
            connectedInstance?.refreshActiveReplyTargets() ?: false

        fun resumeLiveCapture(context: Context): Boolean {
            val instance = connectedInstance
            if (instance != null) {
                instance.refreshActiveReplyTargets()
                return true
            }
            requestRebind(ComponentName(context, ReplyHubNotificationListener::class.java))
            return false
        }

        val SUPPORTED_PACKAGES: Set<String> = MessengerCatalog.packages
    }
}
