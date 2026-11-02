package com.replyhub.app.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessengerCatalogTest {
    @Test
    fun packagesAreUniqueAndIncludeMajorMessengers() {
        assertEquals(MessengerCatalog.apps.size, MessengerCatalog.packages.size)
        assertTrue("com.whatsapp" in MessengerCatalog.packages)
        assertTrue("com.facebook.orca" in MessengerCatalog.packages)
        assertTrue("com.discord" in MessengerCatalog.packages)
        assertTrue("com.microsoft.teams" in MessengerCatalog.packages)
        assertTrue("org.thoughtcrime.securesms" in MessengerCatalog.packages)
    }

    @Test
    fun autoSendDefaultsCoverChatAppsButNotFeedApps() {
        val defaults = MessengerCatalog.defaultAutoSendPackages

        assertTrue("com.tencent.mm" in defaults)
        assertTrue("com.kakao.talk" in defaults)
        listOf(
            "com.instagram.android",
            "com.twitter.android",
            "com.linkedin.android",
            "com.zhiliaoapp.musically",
        ).forEach { feedApp ->
            assertFalse("$feedApp should be off by default", feedApp in defaults)
        }
        assertTrue(MessengerCatalog.packages.containsAll(defaults))
    }
}
