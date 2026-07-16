package com.replyhub.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ContextualReplyComposerTest {
    private val composer = ContextualReplyComposer()

    @Test
    fun `replies to fitness context instead of using an unrelated template`() {
        val reply = compose("체력 안배를 해야됨 ㅋ")

        assertEquals("맞아요. 무리하지 말고 체력 안배하면서 하시죠.", reply)
    }

    @Test
    fun `acknowledges a deadline without inventing a time`() {
        val reply = compose("오페라 티켓 신청 내일 정오에 마감하겠습니다~")

        assertEquals("네, 알려주셔서 감사합니다. 마감 전에 확인하겠습니다.", reply)
        assertFalse(reply.contains("8시"))
    }

    @Test
    fun `does not invent a schedule when asked about time`() {
        val reply = compose("내일 몇 시가 괜찮으세요?")

        assertEquals("말씀하신 일정을 확인해 보고 바로 답변드리겠습니다.", reply)
        assertFalse(reply.contains("7시"))
        assertFalse(reply.contains("8시"))
    }

    @Test
    fun `does not invent an address`() {
        val reply = compose("지난번 장소가 어디였죠?")

        assertEquals("장소를 확인한 뒤 정확히 말씀드리겠습니다.", reply)
        assertFalse(reply.contains("강남"))
    }

    @Test
    fun `reuses a known address from conversation history`() {
        val reply = composer.compose(
            latestMessage = "지난번 장소가 어디였죠?",
            recentMessages = listOf("주소는 강남대로 123, 2층이야."),
            userDirection = "",
            tone = ReplyTone.CASUAL,
        )

        assertEquals("주소는 강남대로 123, 2층이야.", reply)
    }

    @Test
    fun `uses recent context when the newest message is short`() {
        val reply = composer.compose(
            latestMessage = "그럼",
            recentMessages = listOf("무리하지 않게 체력 안배가 필요해요"),
            userDirection = "",
            tone = ReplyTone.POLITE,
        )

        assertEquals("맞아요. 무리하지 말고 체력 안배하면서 하시죠.", reply)
    }

    private fun compose(message: String): String = composer.compose(
        latestMessage = message,
        recentMessages = emptyList(),
        userDirection = "",
        tone = ReplyTone.POLITE,
    )
}
