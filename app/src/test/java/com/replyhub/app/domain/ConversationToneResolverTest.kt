package com.replyhub.app.domain

import com.replyhub.app.data.CapturedMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationToneResolverTest {
    private val resolver = ConversationToneResolver()

    @Test
    fun `prefers my polite reply style over recipient casual speech`() {
        val messages = listOf(
            message("뭐해", isOutgoing = false),
            message("확인하고 말씀드릴게요.", isOutgoing = true),
        )

        assertEquals(ReplyTone.POLITE, resolver.resolve(messages))
    }

    @Test
    fun `prefers my casual reply style over recipient polite speech`() {
        val messages = listOf(
            message("지금 괜찮으세요?", isOutgoing = false),
            message("응, 지금 가는 중이야.", isOutgoing = true),
        )

        assertEquals(ReplyTone.CASUAL, resolver.resolve(messages))
    }

    @Test
    fun `uses recipient tone when I have no previous reply`() {
        val messages = listOf(
            message("확인 부탁드립니다.", isOutgoing = false),
            message("내일 다시 연락드릴게요.", isOutgoing = false),
        )

        assertEquals(ReplyTone.POLITE, resolver.resolve(messages))
    }

    @Test
    fun `recent replies outweigh an older speech level`() {
        val messages = listOf(
            message("확인하겠습니다.", true, timestamp = 1),
            message("알겠습니다.", true, timestamp = 2),
            message("감사합니다.", true, timestamp = 3),
            message("곧 연락드릴게요.", true, timestamp = 4),
            message("응, 확인할게.", true, timestamp = 5),
            message("좋아, 그렇게 하자.", true, timestamp = 6),
            message("응", true, timestamp = 7),
            message("응, 괜찮아.", true, timestamp = 8),
        )

        assertEquals(ReplyTone.CASUAL, resolver.resolve(messages))
    }

    private fun message(text: String, isOutgoing: Boolean, timestamp: Long = 0) = CapturedMessage(
        packageName = "com.kakao.talk",
        sender = "민수",
        originalText = text,
        detectedLanguage = "ko",
        translatedText = text,
        timestamp = timestamp,
        priority = "LATER",
        hasRemoteInputAction = true,
        rawNotificationKey = text,
        isOutgoing = isOutgoing,
    )
}
