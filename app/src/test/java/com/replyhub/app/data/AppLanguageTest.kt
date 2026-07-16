package com.replyhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun storageValueRestoresLanguageAndFallsBackToKorean() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromStorage("en"))
        assertEquals(AppLanguage.KOREAN, AppLanguage.fromStorage("ko"))
        assertEquals(AppLanguage.KOREAN, AppLanguage.fromStorage("unknown"))
        assertEquals(AppLanguage.KOREAN, AppLanguage.fromStorage(null))
    }

    @Test
    fun textReturnsSelectedLanguage() {
        assertEquals("한국어", AppLanguage.KOREAN.text("한국어", "English"))
        assertEquals("English", AppLanguage.ENGLISH.text("한국어", "English"))
    }
}
