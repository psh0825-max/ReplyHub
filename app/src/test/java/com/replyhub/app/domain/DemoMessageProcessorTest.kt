package com.replyhub.app.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoMessageProcessorTest {
    private val processor = DemoMessageProcessor()

    @Test
    fun detectsAndTranslatesKnownChineseMessage() = runTest {
        val result = processor.enrich("咖啡店今天营业到几点？")

        assertEquals("zh", result.detectedLanguage)
        assertEquals("카페는 오늘 몇 시까지 영업하나요?", result.translatedText)
        assertEquals("What time does the cafe close today?", result.englishTranslatedText)
    }

    @Test
    fun keepsKoreanMessageAsIs() = runTest {
        val text = "내일 오후에 다시 연락할게"
        val result = processor.enrich(text)

        assertEquals("ko", result.detectedLanguage)
        assertEquals(text, result.translatedText)
        assertEquals(text, result.englishTranslatedText)
    }

    @Test
    fun recognizesKoreanCompatibilityJamo() = runTest {
        val text = "ㅋㅋㅋㅋㅋ"
        val result = processor.enrich(text)

        assertEquals("ko", result.detectedLanguage)
        assertEquals(text, result.translatedText)
    }

    @Test
    fun translatesKnownKoreanDemoMessageToEnglish() = runTest {
        val result = processor.enrich("지난번에 말한 주소 다시 보내줄래?")

        assertEquals("ko", result.detectedLanguage)
        assertEquals(
            "Could you send me the address you mentioned last time?",
            result.englishTranslatedText,
        )
    }
}
