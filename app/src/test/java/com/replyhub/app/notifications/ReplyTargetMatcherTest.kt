package com.replyhub.app.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplyTargetMatcherTest {
    @Test
    fun `matches notification title or messaging sender alias`() {
        val aliases = buildReplySenderAliases("Project room", "Alex")

        assertTrue(replySenderMatches("Project room", aliases))
        assertTrue(replySenderMatches("alex", aliases))
    }

    @Test
    fun `ignores blanks and unrelated senders`() {
        val aliases = buildReplySenderAliases("  ", null, "Alex")

        assertFalse(replySenderMatches("Taylor", aliases))
    }

    @Test
    fun `never matches another notification from the same messenger`() {
        assertFalse(
            replyTargetMatches(
                targetNotificationKey = "active-room-key",
                targetPackageName = "com.example.messenger",
                notificationKey = "old-room-key",
                packageName = "com.example.messenger",
            ),
        )
    }

    @Test
    fun `matches only the exact notification key`() {
        assertTrue(
            replyTargetMatches(
                targetNotificationKey = "active-room-key",
                targetPackageName = "com.example.messenger",
                notificationKey = "active-room-key",
                packageName = "com.example.messenger",
            ),
        )
    }

    @Test
    fun `conversation fallback requires the same nonblank room id`() {
        assertTrue(
            replyConversationMatches(
                targetPackageName = "com.example.messenger",
                targetConversationId = "shortcut:project",
                packageName = "com.example.messenger",
                conversationId = "shortcut:project",
            ),
        )
        assertFalse(
            replyConversationMatches(
                targetPackageName = "com.example.messenger",
                targetConversationId = "shortcut:project",
                packageName = "com.example.messenger",
                conversationId = "shortcut:sales",
            ),
        )
        assertFalse(
            replyConversationMatches(
                targetPackageName = "com.example.messenger",
                targetConversationId = "shortcut:project",
                packageName = "com.example.messenger",
                conversationId = "",
            ),
        )
    }

    @Test
    fun `exact key wins even when sender aliases are ambiguous`() {
        val targets = listOf(
            target("room-a-key", "room-a", "Alex"),
            target("room-b-key", "room-b", "Alex"),
        )

        assertEquals(
            "room-b-key",
            selectReplyTargetKey(
                targets = targets,
                notificationKey = "room-b-key",
                packageName = "com.example.messenger",
                conversationId = "room-b",
                sender = "Alex",
            ),
        )
    }

    @Test
    fun `refuses ambiguous sender alias fallback`() {
        val targets = listOf(
            target("room-a-key", "room-a", "Alex"),
            target("room-b-key", "room-b", "Alex"),
        )

        assertNull(
            selectReplyTargetKey(
                targets = targets,
                notificationKey = "expired-key",
                packageName = "com.example.messenger",
                conversationId = "unknown-room",
                sender = "Alex",
            ),
        )
    }

    @Test
    fun `refuses multiple active targets for one room when the exact key is gone`() {
        val targets = listOf(
            target("old-room-key", "room-a", "Alex"),
            target("new-room-key", "room-a", "Project room"),
        )

        assertNull(
            selectReplyTargetKey(
                targets = targets,
                notificationKey = "expired-key",
                packageName = "com.example.messenger",
                conversationId = "room-a",
                sender = "Project room",
            ),
        )
    }

    private fun target(key: String, conversationId: String, alias: String) =
        ReplyTargetIdentity(
            notificationKey = key,
            packageName = "com.example.messenger",
            conversationId = conversationId,
            senderAliases = setOf(alias),
        )
}
