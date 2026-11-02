package com.replyhub.app.notifications

import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

enum class AutoSendOutcome {
    SENT,
    TYPED_ONLY,
    NOT_HANDLED,

    /** 메신저가 접근성 서비스에 화면을 공개하지 않아 입력창을 찾을 수 없다. */
    SCREEN_HIDDEN,
}

object AutoSendCoordinator {
    internal enum class Stage {
        OPENING,
        TYPED,
        CLICKED,
    }

    internal class Job(
        val packageName: String,
        val text: String,
        private val expiresAtElapsed: Long,
    ) {
        val result = CompletableDeferred<AutoSendOutcome>()

        @Volatile
        var stage: Stage = Stage.OPENING

        @Volatile
        var windowSeenAtElapsed: Long = 0L

        @Volatile
        var clickedAtElapsed: Long = 0L

        @Volatile
        var usedPasteFallback: Boolean = false

        @Volatile
        var replyVisibleInInput: Boolean = false

        @Volatile
        var sendButtonMissLogged: Boolean = false

        val isExpired: Boolean
            get() = SystemClock.elapsedRealtime() > expiresAtElapsed
    }

    @Volatile
    internal var currentJob: Job? = null
        private set

    /**
     * 자동 전송 잡을 등록한 뒤 대화를 열고 접근성 서비스가 전송을 마칠 때까지 기다린다.
     * 잡 등록이 대화 열기보다 먼저여야 메신저 창이 뜨는 순간의 접근성 이벤트를 놓치지 않는다.
     * 반환값 null은 대화를 열지 못해 자동 전송을 시도조차 못 한 경우다.
     */
    suspend fun requestSend(
        packageName: String,
        text: String,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        launchConversation: () -> Boolean,
    ): AutoSendOutcome? {
        val job = Job(packageName, text, SystemClock.elapsedRealtime() + timeoutMillis)
        currentJob = job
        try {
            if (!launchConversation()) {
                return null
            }
            ReplyHubAutoSendService.nudge()
            val outcome = withTimeoutOrNull(timeoutMillis) { job.result.await() }
            return outcome ?: if (job.stage == Stage.OPENING) {
                AutoSendOutcome.NOT_HANDLED
            } else {
                AutoSendOutcome.TYPED_ONLY
            }
        } finally {
            currentJob = null
        }
    }

    internal fun completeIfCurrent(job: Job, outcome: AutoSendOutcome) {
        if (currentJob === job) {
            job.result.complete(outcome)
        }
    }

    private const val DEFAULT_TIMEOUT_MILLIS = 12_000L
}
