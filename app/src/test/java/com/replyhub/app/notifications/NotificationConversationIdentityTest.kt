package com.replyhub.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationConversationIdentityTest {
    @Test
    fun `prefers a stable shortcut id and keeps the room title`() {
        val identity = buildNotificationConversationIdentity(
            shortcutId = "product-room",
            notificationTag = "tag-1",
            notificationId = 7,
            conversationTitle = "Product launch",
            title = "Alex",
            subText = "Workspace",
            sender = "Alex",
        )

        assertEquals("shortcut:product-room", identity.id)
        assertEquals("Product launch", identity.title)
    }

    @Test
    fun `normalizes a conversation title when no shortcut exists`() {
        val identity = buildNotificationConversationIdentity(
            shortcutId = null,
            notificationTag = null,
            notificationId = 7,
            conversationTitle = "  Product   Launch  ",
            title = "Alex",
            subText = "",
            sender = "Alex",
        )

        assertEquals("conversation:product launch", identity.id)
    }
}
