package com.replyhub.app.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationReplyActionScannerTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun findsInvisibleReplyActionWhenNoVisibleReplyExists() {
        val notification = NotificationCompat.Builder(context, "reply-scanner-test")
            .addInvisibleAction(replyAction("invisible", 1))
            .build()

        val scan = scanReplyActions(notification)

        assertEquals(0, scan.visibleCount)
        assertEquals(1, scan.invisibleCount)
        assertEquals(ReplyActionSource.INVISIBLE, scan.selected?.source)
        assertNotNull(scan.selected?.action?.remoteInputs)
    }

    @Test
    fun prefersVisibleReplyActionOverCompatibilityActions() {
        val notification = NotificationCompat.Builder(context, "reply-scanner-test")
            .addAction(replyAction("visible", 2))
            .addInvisibleAction(replyAction("invisible", 3))
            .build()

        val scan = scanReplyActions(notification)

        assertEquals(ReplyActionSource.VISIBLE, scan.selected?.source)
    }

    private fun replyAction(resultKey: String, requestCode: Int): NotificationCompat.Action {
        val intent = Intent("com.replyhub.app.TEST_REPLY_$requestCode")
            .setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val remoteInput = RemoteInput.Builder(resultKey).build()
        return NotificationCompat.Action.Builder(0, "Reply", pendingIntent)
            .addRemoteInput(remoteInput)
            .build()
    }
}
