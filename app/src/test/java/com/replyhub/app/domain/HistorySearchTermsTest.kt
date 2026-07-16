package com.replyhub.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySearchTermsTest {
    @Test
    fun `prioritizes useful semantic terms and removes request filler`() {
        val terms = searchHistoryTerms("지난번에 말한 주소 다시 보내줄래?")

        assertEquals("주소", terms.first())
        assertTrue("주소" in terms)
        assertFalse("지난번에" in terms)
        assertFalse("다시" in terms)
    }
}
