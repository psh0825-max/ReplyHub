package com.replyhub.app.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentNotificationDeduplicatorTest {
    @Test
    fun `rejects an exact repost but accepts a new message`() {
        val deduplicator = RecentNotificationDeduplicator(retentionMillis = 1_000)

        assertTrue(deduplicator.shouldProcess("chat", "key", "hello", 100, now = 100))
        assertFalse(deduplicator.shouldProcess("chat", "key", "hello", 100, now = 200))
        assertTrue(deduplicator.shouldProcess("chat", "key", "hello again", 300, now = 300))
    }

    @Test
    fun `allows the same notification after retention expires`() {
        val deduplicator = RecentNotificationDeduplicator(retentionMillis = 100)

        assertTrue(deduplicator.shouldProcess("chat", "key", "hello", 100, now = 100))
        assertTrue(deduplicator.shouldProcess("chat", "key", "hello", 100, now = 250))
    }
}
