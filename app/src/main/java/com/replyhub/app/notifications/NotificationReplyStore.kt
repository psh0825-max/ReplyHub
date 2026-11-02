package com.replyhub.app.notifications

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

enum class ReplySendPath {
    REMOTE_INPUT,
    AUTO_SEND,
    AUTO_SEND_OFF,

    /** 대화는 열 수 있지만 메신저가 화면을 숨겨 자동 입력이 불가능하다. */
    SCREEN_HIDDEN,
    MANUAL,
}

val ReplySendPath.canSendNow: Boolean
    get() = this == ReplySendPath.REMOTE_INPUT || this == ReplySendPath.AUTO_SEND

object NotificationReplyStore {
    private data class ReplyTarget(
        val notificationKey: String,
        val packageName: String,
        val conversationId: String,
        val senderAliases: Set<String>,
        val actionIntent: PendingIntent?,
        val remoteInputs: Array<RemoteInput>,
        val contentIntent: PendingIntent?,
        val registeredAt: Long,
    ) {
        val supportsRemoteInput: Boolean
            get() = actionIntent != null && remoteInputs.isNotEmpty()
    }

    private val targets = ConcurrentHashMap<String, ReplyTarget>()
    private val changeCounter = AtomicLong(0)
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    fun register(
        notificationKey: String,
        packageName: String,
        conversationId: String,
        sender: String,
        senderAliases: Set<String> = emptySet(),
        action: NotificationCompat.Action?,
        remoteInputs: Array<RemoteInput>,
        contentIntent: PendingIntent? = null,
    ) {
        pruneExpiredTargets()
        val actionIntent = action?.actionIntent
        if (actionIntent == null && contentIntent == null) {
            remove(notificationKey)
            return
        }
        targets[notificationKey] = ReplyTarget(
            notificationKey = notificationKey,
            packageName = packageName,
            conversationId = conversationId,
            senderAliases = buildReplySenderAliases(
                sender,
                *senderAliases.toTypedArray(),
            ),
            actionIntent = actionIntent,
            remoteInputs = if (actionIntent == null) emptyArray() else remoteInputs,
            contentIntent = contentIntent,
            registeredAt = System.currentTimeMillis(),
        )
        signalChanged()
    }

    fun remove(notificationKey: String) {
        if (targets.remove(notificationKey) != null) signalChanged()
    }

    /** 자동 전송 서비스 연결이나 앱별 허용 설정이 바뀌면 화면의 전송 경로 표시를 다시 계산하게 한다. */
    fun notifyAvailabilityChanged() = signalChanged()

    fun clear() {
        if (targets.isNotEmpty()) {
            targets.clear()
            signalChanged()
        }
    }

    fun retainOnly(notificationKeys: Set<String>) {
        if (targets.entries.removeIf { it.key !in notificationKeys }) signalChanged()
    }

    fun isAvailable(
        notificationKey: String,
        packageName: String,
        sender: String,
        conversationId: String,
    ): Boolean = findTarget(notificationKey, packageName, sender, conversationId)
        ?.supportsRemoteInput == true

    fun sendPath(
        notificationKey: String,
        packageName: String,
        sender: String,
        conversationId: String,
    ): ReplySendPath {
        val target = findTarget(notificationKey, packageName, sender, conversationId)
            ?: return ReplySendPath.MANUAL
        return when {
            target.supportsRemoteInput -> ReplySendPath.REMOTE_INPUT
            target.contentIntent != null &&
                ReplyHubAutoSendService.isAvailableFor(target.packageName) ->
                ReplySendPath.AUTO_SEND
            target.contentIntent != null &&
                ReplyHubAutoSendService.isScreenHiddenFor(target.packageName) ->
                ReplySendPath.SCREEN_HIDDEN
            target.contentIntent != null -> ReplySendPath.AUTO_SEND_OFF
            else -> ReplySendPath.MANUAL
        }
    }

    fun activeTargetPackages(): Set<String> {
        pruneExpiredTargets()
        return targets.values
            .filter { it.supportsRemoteInput }
            .mapTo(linkedSetOf()) { it.packageName }
    }

    suspend fun awaitAvailable(
        notificationKey: String,
        packageName: String,
        sender: String,
        conversationId: String,
        timeoutMillis: Long = TARGET_RECONNECT_TIMEOUT_MILLIS,
    ): Boolean = withTimeoutOrNull(timeoutMillis) {
        changes
            .map { isAvailable(notificationKey, packageName, sender, conversationId) }
            .first { it }
    } ?: false

    fun send(
        context: Context,
        notificationKey: String,
        packageName: String,
        sender: String,
        conversationId: String,
        text: String,
    ): Result<Unit> = runCatching {
        pruneExpiredTargets()
        val target = findTarget(notificationKey, packageName, sender, conversationId)
            ?.takeIf { it.supportsRemoteInput }
        checkNotNull(target) {
            "원본 알림의 빠른 답장 액션을 더 이상 사용할 수 없습니다."
        }
        val actionIntent = checkNotNull(target.actionIntent)
        val fillInIntent = Intent()
        val results = Bundle().apply {
            target.remoteInputs.forEach { putCharSequence(it.resultKey, text) }
        }
        RemoteInput.addResultsToIntent(target.remoteInputs, fillInIntent, results)
        try {
            actionIntent.send(context, 0, fillInIntent)
        } catch (error: PendingIntent.CanceledException) {
            targets.remove(target.notificationKey)
            signalChanged()
            throw error
        }
    }

    /**
     * 알림의 contentIntent로 해당 대화 화면을 직접 연다.
     * 빠른 답장이 없는 앱에서 자동 전송·수동 붙여넣기 전 단계로 사용한다.
     */
    fun openConversation(
        context: Context,
        notificationKey: String,
        packageName: String,
        sender: String,
        conversationId: String,
    ): Boolean {
        pruneExpiredTargets()
        val target = findTarget(notificationKey, packageName, sender, conversationId)
        val contentIntent = target?.contentIntent ?: return false
        return runCatching {
            // Android 14+에서는 다른 앱 PendingIntent로 액티비티를 띄울 때 명시적 허용이 필요하다.
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                    .toBundle()
            } else {
                null
            }
            contentIntent.send(context, 0, null, null, null, null, options)
        }.onFailure {
            if (it is PendingIntent.CanceledException) {
                targets.remove(target.notificationKey)
                signalChanged()
            }
        }.isSuccess
    }

    private fun findTarget(
        notificationKey: String,
        packageName: String,
        sender: String,
        conversationId: String,
    ): ReplyTarget? {
        val selectedKey = selectReplyTargetKey(
            targets = targets.values.map {
                ReplyTargetIdentity(
                    notificationKey = it.notificationKey,
                    packageName = it.packageName,
                    conversationId = it.conversationId,
                    senderAliases = it.senderAliases,
                )
            },
            notificationKey = notificationKey,
            packageName = packageName,
            conversationId = conversationId,
            sender = sender,
        )
        return selectedKey?.let { targets[it] }
    }

    private fun pruneExpiredTargets() {
        val oldestAllowed = System.currentTimeMillis() - TARGET_TTL_MILLIS
        if (targets.entries.removeIf { it.value.registeredAt < oldestAllowed }) {
            signalChanged()
        }
    }

    private fun signalChanged() {
        _changes.value = changeCounter.incrementAndGet()
    }

    private const val TARGET_TTL_MILLIS = 2 * 60 * 60 * 1000L
    private const val TARGET_RECONNECT_TIMEOUT_MILLIS = 1_500L
}
