package com.replyhub.app.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

object NotificationReplyStore {
    private data class ReplyTarget(
        val notificationKey: String,
        val packageName: String,
        val conversationId: String,
        val senderAliases: Set<String>,
        val actionIntent: PendingIntent,
        val remoteInputs: Array<RemoteInput>,
        val registeredAt: Long,
    )

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
        action: NotificationCompat.Action,
        remoteInputs: Array<RemoteInput>,
    ) {
        pruneExpiredTargets()
        targets[notificationKey] = ReplyTarget(
            notificationKey = notificationKey,
            packageName = packageName,
            conversationId = conversationId,
            senderAliases = buildReplySenderAliases(
                sender,
                *senderAliases.toTypedArray(),
            ),
            actionIntent = checkNotNull(action.actionIntent),
            remoteInputs = remoteInputs,
            registeredAt = System.currentTimeMillis(),
        )
        signalChanged()
    }

    fun remove(notificationKey: String) {
        if (targets.remove(notificationKey) != null) signalChanged()
    }

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
    ): Boolean = findTarget(notificationKey, packageName, sender, conversationId) != null

    fun activeTargetPackages(): Set<String> {
        pruneExpiredTargets()
        return targets.values.mapTo(linkedSetOf()) { it.packageName }
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
        val target = checkNotNull(findTarget(notificationKey, packageName, sender, conversationId)) {
            "원본 알림의 빠른 답장 액션을 더 이상 사용할 수 없습니다."
        }
        val fillInIntent = Intent()
        val results = Bundle().apply {
            target.remoteInputs.forEach { putCharSequence(it.resultKey, text) }
        }
        RemoteInput.addResultsToIntent(target.remoteInputs, fillInIntent, results)
        try {
            target.actionIntent.send(context, 0, fillInIntent)
        } catch (error: PendingIntent.CanceledException) {
            targets.remove(target.notificationKey)
            signalChanged()
            throw error
        }
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
