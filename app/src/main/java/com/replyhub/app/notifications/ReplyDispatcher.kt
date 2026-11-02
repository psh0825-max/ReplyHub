package com.replyhub.app.notifications

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.replyhub.app.data.AppLanguage
import com.replyhub.app.data.AutoSendCompatibilityStore
import com.replyhub.app.data.CapturedMessage
import com.replyhub.app.data.resolvedConversationId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class DispatchResult(
    val sentDirectly: Boolean,
    val message: String,
)

class ReplyDispatcher(
    private val context: Context,
    private val compatibility: AutoSendCompatibilityStore,
) {
    suspend fun dispatch(
        message: CapturedMessage,
        text: String,
        language: AppLanguage,
    ): DispatchResult = withContext(Dispatchers.Default) {
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
                    return@withContext DispatchResult(
                        true,
                        language.text(
                            "알림의 빠른 답장으로 전송했습니다.",
                            "Sent using the notification quick reply.",
                        ),
                    )
                }
        }

        val autoSendAvailable = ReplyHubAutoSendService.isAvailableFor(message.packageName)
        if (autoSendAvailable) {
            val outcome = AutoSendCoordinator.requestSend(message.packageName, text) {
                NotificationReplyStore.openConversation(
                    context = context,
                    notificationKey = message.rawNotificationKey,
                    packageName = message.packageName,
                    sender = message.sender,
                    conversationId = message.resolvedConversationId,
                )
            }
            when (outcome) {
                AutoSendOutcome.SENT -> {
                    // 전송된 메시지가 화면에 잠깐 보인 뒤 ReplyHub로 복귀한다.
                    delay(RETURN_TO_APP_DELAY_MILLIS)
                    returnToReplyHub()
                    return@withContext DispatchResult(
                        true,
                        language.text(
                            "빠른 답장이 없는 앱이라 메신저를 열어 자동으로 입력하고 전송했습니다.",
                            "This app has no quick reply, so the messenger was opened and the reply was sent automatically.",
                        ),
                    )
                }
                // 실패한 경우에만 클립보드를 쓴다. 사용자는 지금 메신저 화면을 보고 있으므로 토스트로도 알린다.
                AutoSendOutcome.TYPED_ONLY -> {
                    ReplyClipboard.copy(context, text)
                    showToast(
                        language.text(
                            "답장을 입력해 두었어요. 확인 후 전송 버튼을 눌러 주세요.",
                            "Your reply is typed in. Check it and tap send.",
                        ),
                    )
                    return@withContext DispatchResult(
                        false,
                        language.text(
                            "메신저 입력창에 답장을 넣어 두었습니다. 확인 후 전송 버튼을 눌러 주세요.",
                            "The reply was typed into the messenger's input field. Check it and tap send.",
                        ),
                    )
                }
                AutoSendOutcome.SCREEN_HIDDEN -> {
                    compatibility.markScreenHidden(message.packageName)
                    NotificationReplyStore.notifyAvailabilityChanged()
                    ReplyClipboard.copy(context, text)
                    showToast(
                        language.text(
                            "이 앱은 화면 자동 입력을 막고 있어요. 답장이 복사돼 있으니 붙여넣어 전송해 주세요.",
                            "This app blocks automatic typing. Your reply is copied, so paste it to send.",
                        ),
                    )
                    return@withContext DispatchResult(
                        false,
                        language.text(
                            "이 메신저는 접근성 서비스에 화면을 공개하지 않아 자동 전송할 수 없습니다. " +
                                "답장을 복사하고 대화를 열었으니 붙여넣어 전송해 주세요. " +
                                "다음부터는 바로 이 방식으로 보냅니다.",
                            "This messenger hides its screen from accessibility services, so auto-send " +
                                "can't work. The reply was copied and the conversation opened; paste it to send. " +
                                "Next time ReplyHub will use this method right away.",
                        ),
                    )
                }
                AutoSendOutcome.NOT_HANDLED -> {
                    ReplyClipboard.copy(context, text)
                    showToast(
                        language.text(
                            "자동 입력에 실패했어요. 답장이 복사돼 있으니 입력창에 붙여넣어 주세요.",
                            "Auto-send couldn't type the reply. It's copied, so paste it into the chat.",
                        ),
                    )
                    return@withContext DispatchResult(
                        false,
                        language.text(
                            "자동 전송에 실패해 내용을 복사하고 대화를 열었습니다. 입력창에 붙여넣어 전송해 주세요.",
                            "Auto-send didn't complete, so the reply was copied and the conversation opened. Paste it to send.",
                        ),
                    )
                }
                null -> Unit
            }
        }

        ReplyClipboard.copy(context, text)
        val (autoSendHintKo, autoSendHintEn) = when {
            autoSendAvailable -> "" to ""
            ReplyHubAutoSendService.isScreenHiddenFor(message.packageName) ->
                SCREEN_HIDDEN_HINT_KO to SCREEN_HIDDEN_HINT_EN
            ReplyHubAutoSendService.isConnected -> APP_AUTO_SEND_HINT_KO to APP_AUTO_SEND_HINT_EN
            else -> AUTO_SEND_HINT_KO to AUTO_SEND_HINT_EN
        }
        if (
            !autoSendAvailable &&
            NotificationReplyStore.openConversation(
                context = context,
                notificationKey = message.rawNotificationKey,
                packageName = message.packageName,
                sender = message.sender,
                conversationId = message.resolvedConversationId,
            )
        ) {
            return@withContext DispatchResult(
                false,
                language.text(
                    "이 대화에는 활성 빠른 답장이 없어 내용을 복사하고 대화를 열었습니다. " +
                        "붙여넣어 전송해 주세요.$autoSendHintKo",
                    "No active quick reply is available, so the reply was copied and the conversation " +
                        "opened. Paste it to send.$autoSendHintEn",
                ),
            )
        }

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(message.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
            return@withContext DispatchResult(
                false,
                language.text(
                    "이 대화에는 활성 빠른 답장이 없어 내용을 복사하고 메신저를 열었습니다.$autoSendHintKo",
                    "No active quick reply is available for this conversation, so the message was " +
                        "copied and the messenger opened.$autoSendHintEn",
                ),
            )
        }
        return@withContext DispatchResult(
            false,
            language.text(
                "이 대화에는 활성 빠른 답장이 없어 내용을 복사했습니다.$autoSendHintKo",
                "No active quick reply is available for this conversation, so the message was " +
                    "copied.$autoSendHintEn",
            ),
        )
    }

    private suspend fun showToast(text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    // 접근성 서비스가 켜진 앱은 백그라운드 액티비티 실행 제한의 예외라 복귀가 가능하다.
    private fun returnToReplyHub() {
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
            ?: return
        runCatching { context.startActivity(intent) }
    }

    private companion object {
        const val RETURN_TO_APP_DELAY_MILLIS = 1_200L
        const val AUTO_SEND_HINT_KO =
            " 설정에서 '자동 전송(접근성)'을 켜면 이런 앱에서도 바로 보낼 수 있어요."
        const val AUTO_SEND_HINT_EN =
            " Turn on Auto-send (accessibility) in settings to send directly in apps like this."
        const val APP_AUTO_SEND_HINT_KO =
            " 설정의 자동 전송 앱 목록에서 이 앱을 켜면 바로 보낼 수 있어요."
        const val APP_AUTO_SEND_HINT_EN =
            " Turn this app on in the auto-send app list in settings to send directly."
        const val SCREEN_HIDDEN_HINT_KO =
            " 이 메신저는 화면 자동 입력을 막고 있어 붙여넣기로 보내야 해요."
        const val SCREEN_HIDDEN_HINT_EN =
            " This messenger blocks automatic typing, so pasting is the only option."
    }
}
