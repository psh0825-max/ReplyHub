package com.replyhub.app.notifications

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import com.replyhub.app.data.AppLanguage
import com.replyhub.app.data.CapturedMessage
import com.replyhub.app.data.resolvedConversationId

data class DispatchResult(
    val sentDirectly: Boolean,
    val message: String,
)

class ReplyDispatcher(private val context: Context) {
    suspend fun dispatch(
        message: CapturedMessage,
        text: String,
        language: AppLanguage,
    ): DispatchResult {
        val listenerReady = ReplyHubNotificationListener.refreshReplyTargets()
        var targetAvailable = NotificationReplyStore.isAvailable(
            message.rawNotificationKey,
            message.packageName,
            message.sender,
            message.resolvedConversationId,
        )
        if (!targetAvailable && !listenerReady) {
            ReplyHubNotificationListener.resumeLiveCapture(context)
            targetAvailable = NotificationReplyStore.awaitAvailable(
                message.rawNotificationKey,
                message.packageName,
                message.sender,
                message.resolvedConversationId,
            )
        }
        if (targetAvailable) {
            NotificationReplyStore.send(
                context = context,
                notificationKey = message.rawNotificationKey,
                packageName = message.packageName,
                sender = message.sender,
                conversationId = message.resolvedConversationId,
                text = text,
            )
                .onSuccess {
                    return DispatchResult(
                        true,
                        language.text(
                            "알림의 빠른 답장으로 전송했습니다.",
                            "Sent using the notification quick reply.",
                        ),
                    )
                }
        }

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText("ReplyHub reply", text).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(SENSITIVE_CLIPBOARD_KEY, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(message.packageName)
            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
            return DispatchResult(
                false,
                language.text(
                    "이 대화에는 활성 빠른 답장이 없어 내용을 복사하고 메신저를 열었습니다.",
                    "No active quick reply is available for this conversation, so the message was copied and the messenger opened.",
                ),
            )
        }
        return DispatchResult(
            false,
            language.text(
                "이 대화에는 활성 빠른 답장이 없어 내용을 복사했습니다.",
                "No active quick reply is available for this conversation, so the message was copied.",
            ),
        )
    }

    private companion object {
        const val SENSITIVE_CLIPBOARD_KEY = "android.content.extra.IS_SENSITIVE"
    }
}
