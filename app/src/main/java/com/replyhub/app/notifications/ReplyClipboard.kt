package com.replyhub.app.notifications

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

internal object ReplyClipboard {
    fun copy(context: Context, text: String) {
        val clip = ClipData.newPlainText(LABEL, text).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(SENSITIVE_CLIPBOARD_KEY, true)
            }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    fun clear(context: Context) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    private const val LABEL = "ReplyHub reply"
    private const val SENSITIVE_CLIPBOARD_KEY = "android.content.extra.IS_SENSITIVE"
}
