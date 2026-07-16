package com.replyhub.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class KoreanSpeechLevelDetectorTest {
    private val detector = KoreanSpeechLevelDetector()

    @Test
    fun detectsPoliteConversation() {
        val result = detector.detect(
            listOf("덥다고요", "32도는 덥습니다", "조금 있다가 연락드릴게요"),
        )

        assertEquals(ReplyTone.POLITE, result)
    }

    @Test
    fun detectsCasualConversation() {
        val result = detector.detect(
            listOf("겁나 큰데", "ㅋㅋㅋㅋㅋ", "응 이따가 확인할게"),
        )

        assertEquals(ReplyTone.CASUAL, result)
    }

    @Test
    fun prefersPoliteToneForMixedConversation() {
        val result = detector.detect(
            listOf(
                "겁나 큰데",
                "그럼",
                "ㅋㅋㅋㅋㅋ",
                "덥다고요",
                "32도는 덥습니다",
                "죽습니다",
                "더워요",
            ),
        )

        assertEquals(ReplyTone.POLITE, result)
    }

    @Test
    fun defaultsToPoliteWhenThereIsNoToneEvidence() {
        val result = detector.detect(listOf("인천", "수영장"))

        assertEquals(ReplyTone.POLITE, result)
    }
}
