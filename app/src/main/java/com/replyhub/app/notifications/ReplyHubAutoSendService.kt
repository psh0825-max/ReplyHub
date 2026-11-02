package com.replyhub.app.notifications

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.replyhub.app.ReplyHubApplication
import com.replyhub.app.messaging.MessengerCatalog
import com.replyhub.app.notifications.AutoSendCoordinator.Job
import com.replyhub.app.notifications.AutoSendCoordinator.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 빠른 답장(RemoteInput)이 없는 메신저를 위한 자동 전송 서비스.
 * 사용자가 ReplyHub에서 전송을 누른 직후(AutoSendCoordinator에 잡이 있는 동안)에만 동작하며,
 * 그 외 시점의 접근성 이벤트는 즉시 무시한다. 화면 내용은 저장하거나 전송하지 않고,
 * 로그에는 노드 개수 같은 진단 정보만 남긴다.
 */
class ReplyHubAutoSendService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val attemptRunnable = Runnable { attemptPendingSend() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val privacySettings by lazy {
        (application as ReplyHubApplication).privacySettingsStore
    }
    private val compatibility by lazy {
        (application as ReplyHubApplication).autoSendCompatibilityStore
    }

    private class Snapshot(
        val infos: List<AccessibilityNodeInfo>,
        val nodes: List<ScreenNode>,
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        connectedInstance = this
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 60
            packageNames = watchedPackages(privacySettings.autoSendPackages.value)
        }
        scope.launch {
            privacySettings.autoSendPackages.collect { allowed ->
                serviceInfo = serviceInfo?.apply { packageNames = watchedPackages(allowed) }
                NotificationReplyStore.notifyAvailabilityChanged()
            }
        }
    }

    override fun onDestroy() {
        if (connectedInstance === this) connectedInstance = null
        handler.removeCallbacks(attemptRunnable)
        scope.cancel()
        NotificationReplyStore.notifyAvailabilityChanged()
        super.onDestroy()
    }

    // packageNames가 비면 모든 앱의 이벤트를 받게 되므로, 허용 앱이 없으면 자기 패키지로 좁힌다.
    private fun watchedPackages(allowed: Set<String>): Array<String> =
        MessengerCatalog.packages
            .filter { it in allowed }
            .ifEmpty { listOf(packageName) }
            .toTypedArray()

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val job = AutoSendCoordinator.currentJob ?: return
        if (event?.packageName?.toString() != job.packageName) return
        scheduleAttempt(0L)
    }

    private fun scheduleAttempt(delayMillis: Long) {
        handler.removeCallbacks(attemptRunnable)
        handler.postDelayed(attemptRunnable, delayMillis)
    }

    private fun attemptPendingSend() {
        val job = AutoSendCoordinator.currentJob ?: return
        if (job.isExpired) return
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != job.packageName) {
            return scheduleAttempt(RETRY_DELAY_MILLIS)
        }
        val now = SystemClock.elapsedRealtime()
        val snapshot = snapshot(root)
        if (job.windowSeenAtElapsed == 0L) {
            job.windowSeenAtElapsed = now
            Log.i(
                TAG,
                "window ready package=${job.packageName}, nodes=${snapshot.nodes.size}, " +
                    "inputs=${AutoSendScreen.countEditables(snapshot.nodes)}",
            )
        }
        when (job.stage) {
            Stage.OPENING -> typeReply(job, snapshot, now)
            Stage.TYPED -> clickSend(job, snapshot)
            Stage.CLICKED -> confirmSent(job, snapshot, now)
        }
    }

    private fun typeReply(job: Job, snapshot: Snapshot, now: Long) {
        val inputIndex = AutoSendScreen.findMessageInput(snapshot.nodes)
        if (inputIndex == null) {
            // 12초를 다 기다리지 않고 일찍 포기한다. 화면을 숨기는 앱(예: 위챗)은 더 빨리 판정한다.
            val waited = now - job.windowSeenAtElapsed
            when {
                AutoSendScreen.isOpaqueWindow(snapshot.nodes) && waited > OPAQUE_GRACE_MILLIS -> {
                    Log.i(TAG, "gave up: window hides its content (nodes=${snapshot.nodes.size})")
                    AutoSendCoordinator.completeIfCurrent(job, AutoSendOutcome.SCREEN_HIDDEN)
                }
                waited > NO_INPUT_GRACE_MILLIS -> {
                    // 묶음 알림이 대화 목록을 여는 경우 등
                    Log.i(TAG, "gave up: no input field (nodes=${snapshot.nodes.size})")
                    AutoSendCoordinator.completeIfCurrent(job, AutoSendOutcome.NOT_HANDLED)
                }
                else -> scheduleAttempt(RETRY_DELAY_MILLIS)
            }
            return
        }
        val input = snapshot.infos[inputIndex]
        input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val method = when {
            setText(input, job.text) -> "set_text"
            pasteText(input, job.text) -> {
                job.usedPasteFallback = true
                "paste"
            }
            else -> return scheduleAttempt(RETRY_DELAY_MILLIS)
        }
        job.stage = Stage.TYPED
        Log.i(TAG, "typed reply via $method")
        // 위챗 등 다수 앱은 텍스트가 입력된 뒤에야 전송 버튼이 나타나므로 다음 패스에서 찾는다.
        scheduleAttempt(SEND_BUTTON_DELAY_MILLIS)
    }

    private fun clickSend(job: Job, snapshot: Snapshot) {
        val inputIndex = AutoSendScreen.findMessageInput(snapshot.nodes)
            ?: return scheduleAttempt(RETRY_DELAY_MILLIS)
        if (AutoSendScreen.inputShowsReply(snapshot.nodes[inputIndex], job.text)) {
            job.replyVisibleInInput = true
        }
        val buttonIndex = AutoSendScreen.findSendButton(snapshot.nodes, inputIndex)
        if (buttonIndex == null) {
            if (!job.sendButtonMissLogged) {
                job.sendButtonMissLogged = true
                Log.i(TAG, "waiting for send button (nodes=${snapshot.nodes.size})")
            }
            return scheduleAttempt(RETRY_DELAY_MILLIS)
        }
        if (snapshot.infos[buttonIndex].performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            job.clickedAtElapsed = SystemClock.elapsedRealtime()
            job.stage = Stage.CLICKED
            Log.i(TAG, "clicked send button")
            scheduleAttempt(CONFIRM_DELAY_MILLIS)
        } else {
            scheduleAttempt(RETRY_DELAY_MILLIS)
        }
    }

    // 클릭이 전달됐다는 사실만으로는 전송을 보장하지 않으므로 입력창이 비워졌는지 확인한다.
    private fun confirmSent(job: Job, snapshot: Snapshot, now: Long) {
        if (!job.replyVisibleInInput) {
            // 입력창 텍스트를 노출하지 않는 앱은 확인할 수단이 없어 클릭 결과를 그대로 믿는다.
            Log.i(TAG, "sent (unverifiable: input text not exposed)")
            return finishSent(job)
        }
        val inputIndex = AutoSendScreen.findMessageInput(snapshot.nodes)
        if (
            inputIndex != null &&
            !AutoSendScreen.inputShowsReply(snapshot.nodes[inputIndex], job.text)
        ) {
            Log.i(TAG, "sent (input cleared)")
            return finishSent(job)
        }
        if (now - job.clickedAtElapsed > CONFIRM_TIMEOUT_MILLIS) {
            Log.i(TAG, "unconfirmed: reply still in input (inputFound=${inputIndex != null})")
            AutoSendCoordinator.completeIfCurrent(job, AutoSendOutcome.TYPED_ONLY)
        } else {
            scheduleAttempt(RETRY_DELAY_MILLIS)
        }
    }

    private fun logProbe() {
        val root = rootInActiveWindow
        if (root == null) {
            Log.i(TAG, "probe: no active window root")
            return
        }
        val snapshot = snapshot(root)
        Log.i(
            TAG,
            "probe package=${root.packageName}, nodes=${snapshot.nodes.size}, " +
                "inputs=${AutoSendScreen.countEditables(snapshot.nodes)}",
        )
    }

    private fun finishSent(job: Job) {
        if (job.usedPasteFallback) ReplyClipboard.clear(this)
        AutoSendCoordinator.completeIfCurrent(job, AutoSendOutcome.SENT)
    }

    private fun setText(input: AccessibilityNodeInfo, text: String): Boolean {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    // 일부 입력창은 SET_TEXT를 거부한다. 그때만 클립보드를 거쳐 붙여넣는다.
    private fun pasteText(input: AccessibilityNodeInfo, text: String): Boolean {
        ReplyClipboard.copy(this, text)
        return input.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun snapshot(root: AccessibilityNodeInfo): Snapshot {
        val infos = mutableListOf<AccessibilityNodeInfo>()
        val parents = mutableListOf<Int>()
        fun visit(node: AccessibilityNodeInfo?, parent: Int) {
            if (node == null || infos.size >= MAX_SNAPSHOT_NODES) return
            val index = infos.size
            infos += node
            parents += parent
            for (child in 0 until node.childCount) {
                visit(node.getChild(child), index)
            }
        }
        visit(root, -1)
        val clickTargets = AutoSendScreen.clickTargets(
            parents = parents.toIntArray(),
            clickable = BooleanArray(infos.size) { infos[it].isClickable },
        )
        val nodes = infos.mapIndexed { index, info ->
            val rect = Rect().also(info::getBoundsInScreen)
            ScreenNode(
                text = info.text?.toString(),
                contentDescription = info.contentDescription?.toString(),
                viewId = info.viewIdResourceName,
                className = info.className?.toString(),
                bounds = NodeBounds(rect.left, rect.top, rect.right, rect.bottom),
                isEditable = info.isEditable,
                isEnabled = info.isEnabled,
                isVisible = info.isVisibleToUser,
                isShowingHint = info.isShowingHintText,
                clickTargetIndex = clickTargets[index],
            )
        }
        return Snapshot(infos, nodes)
    }

    companion object {
        private const val TAG = "ReplyHubAutoSend"
        private const val RETRY_DELAY_MILLIS = 350L
        private const val SEND_BUTTON_DELAY_MILLIS = 250L
        private const val FIRST_ATTEMPT_DELAY_MILLIS = 400L
        private const val CONFIRM_DELAY_MILLIS = 300L
        private const val CONFIRM_TIMEOUT_MILLIS = 2_500L
        private const val NO_INPUT_GRACE_MILLIS = 4_000L
        private const val OPAQUE_GRACE_MILLIS = 2_500L
        private const val MAX_SNAPSHOT_NODES = 1_500

        @Volatile
        private var connectedInstance: ReplyHubAutoSendService? = null

        val isConnected: Boolean
            get() = connectedInstance != null

        fun isAvailableFor(packageName: String): Boolean {
            val instance = connectedInstance ?: return false
            return instance.privacySettings.isAutoSendEnabled(packageName) &&
                !instance.compatibility.isScreenHidden(packageName)
        }

        fun isScreenHiddenFor(packageName: String): Boolean =
            connectedInstance?.compatibility?.isScreenHidden(packageName) == true

        fun nudge() {
            connectedInstance?.scheduleAttempt(FIRST_ATTEMPT_DELAY_MILLIS)
        }

        /** 디버그 빌드의 adb 진단용: 현재 화면에서 서비스가 읽을 수 있는 노드 수만 기록한다. */
        internal fun probeActiveWindow() {
            val instance = connectedInstance
            if (instance == null) {
                Log.i(TAG, "probe: service not connected")
                return
            }
            instance.handler.post { instance.logProbe() }
        }

        fun isEnabled(context: Context): Boolean {
            if (isConnected) return true
            val component = ComponentName(context, ReplyHubAutoSendService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabledServices
                .split(':')
                .any { ComponentName.unflattenFromString(it) == component }
        }
    }
}
