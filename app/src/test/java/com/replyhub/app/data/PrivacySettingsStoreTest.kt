package com.replyhub.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacySettingsStoreTest {
    @Test
    fun `retention run is due when it has never run`() {
        assertTrue(isRetentionRunDue(lastRunMillis = 0L, nowMillis = 1_000L))
    }

    @Test
    fun `retention run is throttled for one day`() {
        val lastRun = 10_000L

        assertFalse(
            isRetentionRunDue(
                lastRunMillis = lastRun,
                nowMillis = lastRun + RETENTION_PRUNE_INTERVAL_MILLIS - 1L,
            ),
        )
        assertTrue(
            isRetentionRunDue(
                lastRunMillis = lastRun,
                nowMillis = lastRun + RETENTION_PRUNE_INTERVAL_MILLIS,
            ),
        )
    }

    @Test
    fun `retention run recovers when the clock moves backwards`() {
        assertTrue(isRetentionRunDue(lastRunMillis = 5_000L, nowMillis = 4_000L))
    }
}
