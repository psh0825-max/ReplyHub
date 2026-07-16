package com.replyhub.app.messaging

import org.junit.Assert.assertEquals
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
}
