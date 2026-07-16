package com.replyhub.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationContentSelectorTest {
    @Test
    fun conciseTextWinsOverExpandedBigText() {
        val content = selectNotificationContent(
            title = "LinkedIn",
            text = "A concise direct message",
            bigText = "Expanded notification text ".repeat(80),
            lines = emptyList(),
        )

        assertEquals("LinkedIn", content.sender)
        assertEquals("A concise direct message", content.body)
    }

    @Test
    fun messagingStyleWinsWhenAvailable() {
        val content = selectNotificationContent(
            title = "Messenger",
            text = "Summary",
            bigText = "Expanded summary",
            lines = listOf("Older", "Latest"),
            messagingSender = "Alex",
            messagingText = "The actual message",
        )

        assertEquals("Alex", content.sender)
        assertEquals("The actual message", content.body)
    }

    @Test
    fun latestLineIsFinalFallback() {
        val content = selectNotificationContent(
            title = "",
            text = "",
            bigText = "",
            lines = listOf("Older message", "Latest message"),
        )

        assertEquals("Latest message", content.body)
    }
}
