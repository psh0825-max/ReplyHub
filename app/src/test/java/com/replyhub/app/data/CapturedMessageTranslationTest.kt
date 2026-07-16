package com.replyhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CapturedMessageTranslationTest {
    private val message = CapturedMessage(
        packageName = "messenger",
        sender = "Li Wei",
        originalText = "咖啡店今天营业到几点？",
        detectedLanguage = "zh",
        translatedText = "카페는 오늘 몇 시까지 영업하나요?",
        englishTranslatedText = "What time does the cafe close today?",
        timestamp = 1,
        priority = "LATER",
        hasRemoteInputAction = false,
        rawNotificationKey = "key",
    )

    @Test
    fun returnsTranslationForSelectedAppLanguage() {
        assertEquals(
            "카페는 오늘 몇 시까지 영업하나요?",
            message.translationFor(AppLanguage.KOREAN),
        )
        assertEquals(
            "What time does the cafe close today?",
            message.translationFor(AppLanguage.ENGLISH),
        )
    }

    @Test
    fun usesOriginalWhenMessageAlreadyMatchesSelectedLanguage() {
        val englishMessage = message.copy(
            originalText = "Hello",
            detectedLanguage = "en",
            englishTranslatedText = "Different text",
        )

        assertEquals("Hello", englishMessage.translationFor(AppLanguage.ENGLISH))
    }

    @Test
    fun detectsMissingKoreanTranslationForEnglishMessage() {
        val untranslated = message.copy(
            originalText = "An English message",
            detectedLanguage = "en",
            translatedText = "An English message",
            englishTranslatedText = "An English message",
        )

        assertEquals(true, untranslated.needsTranslationRefresh())
    }
}
