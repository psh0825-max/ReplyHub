package com.replyhub.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** adb shell에서만 호출할 수 있는(DUMP 권한) 디버그 전용 진단 수신기. */
class AutoSendProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReplyHubAutoSendService.probeActiveWindow()
    }
}
