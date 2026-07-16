package com.replyhub.app.notifications

import android.app.Notification
import androidx.core.app.NotificationCompat

internal enum class ReplyActionSource {
    VISIBLE,
    INVISIBLE,
    WEARABLE,
}

internal data class ReplyActionCandidate(
    val source: ReplyActionSource,
    val action: NotificationCompat.Action,
)

internal data class ReplyActionScan(
    val visibleCount: Int,
    val invisibleCount: Int,
    val wearableCount: Int,
    val selected: ReplyActionCandidate?,
)

internal fun scanReplyActions(notification: Notification): ReplyActionScan {
    val visibleActions = (0 until NotificationCompat.getActionCount(notification))
        .mapNotNull { index -> NotificationCompat.getAction(notification, index) }
    val invisibleActions = NotificationCompat.getInvisibleActions(notification)
    val wearableActions = NotificationCompat.WearableExtender(notification).actions
    val candidates = buildList {
        visibleActions.forEach { add(ReplyActionCandidate(ReplyActionSource.VISIBLE, it)) }
        invisibleActions.forEach { add(ReplyActionCandidate(ReplyActionSource.INVISIBLE, it)) }
        wearableActions.forEach { add(ReplyActionCandidate(ReplyActionSource.WEARABLE, it)) }
    }.distinctBy { candidate ->
        candidate.action.actionIntent to
            candidate.action.remoteInputs.orEmpty().map { it.resultKey }
    }

    return ReplyActionScan(
        visibleCount = visibleActions.size,
        invisibleCount = invisibleActions.size,
        wearableCount = wearableActions.size,
        selected = candidates.firstOrNull { candidate ->
            candidate.action.actionIntent != null &&
                candidate.action.remoteInputs.orEmpty().isNotEmpty()
        },
    )
}
