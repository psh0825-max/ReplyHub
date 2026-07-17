package com.replyhub.app.domain

import com.replyhub.app.data.CapturedMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationInboxTest {
    @Test
    fun `groups messages by messenger and sender`() {
        val inbox = buildConversationInbox(
            listOf(
                message(id = 1, packageName = "kakao", sender = "민수", timestamp = 10),
                message(id = 2, packageName = "wechat", sender = "민수", timestamp = 20),
                message(id = 3, packageName = "kakao", sender = "민수", timestamp = 30),
            ),
        )

        assertEquals(2, inbox.size)
        assertEquals("kakao", inbox.first().id.packageName)
        assertEquals(2, inbox.first().messageCount)
    }

    @Test
    fun `keeps rooms separate when the same sender appears twice`() {
        val inbox = buildConversationInbox(
            listOf(
                message(id = 1, sender = "Alex", timestamp = 10).copy(
                    conversationId = "room-product",
                    conversationTitle = "Product",
                ),
                message(id = 2, sender = "Alex", timestamp = 20).copy(
                    conversationId = "room-sales",
                    conversationTitle = "Sales",
                ),
            ),
        )

        assertEquals(2, inbox.size)
        assertEquals(setOf("room-product", "room-sales"), inbox.map { it.id.conversationId }.toSet())
    }

    @Test
    fun `keeps multiple senders in one group room`() {
        val inbox = buildConversationInbox(
            listOf(
                message(id = 1, sender = "Alex", timestamp = 10).copy(
                    conversationId = "room-product",
                    conversationTitle = "Product",
                ),
                message(id = 2, sender = "Taylor", timestamp = 20).copy(
                    conversationId = "room-product",
                    conversationTitle = "Product",
                ),
            ),
        )

        assertEquals(1, inbox.size)
        assertEquals("Product", inbox.single().displayName)
        assertEquals(listOf("Alex", "Taylor"), inbox.single().messages.map { it.sender })
    }

    @Test
    fun `sorts conversations newest first and messages oldest first`() {
        val inbox = buildConversationInbox(
            listOf(
                message(id = 1, sender = "가영", timestamp = 30),
                message(id = 2, sender = "나영", timestamp = 40),
                message(id = 3, sender = "가영", timestamp = 10),
            ),
        )

        assertEquals("나영", inbox[0].id.sender)
        assertEquals(listOf(10L, 30L), inbox[1].messages.map { it.timestamp })
    }

    @Test
    fun `keeps outgoing reply in the timeline and replies to latest incoming message`() {
        val incoming = message(id = 1, sender = "민수", timestamp = 10)
        val outgoing = message(id = 2, sender = "민수", timestamp = 20)
            .copy(isOutgoing = true)

        val conversation = buildConversationInbox(listOf(incoming, outgoing)).single()

        assertEquals(outgoing, conversation.latestMessage)
        assertEquals(incoming, conversation.latestIncomingMessage)
        assertEquals(listOf(false, true), conversation.messages.map { it.isOutgoing })
    }

    @Test
    fun `summarizes reply needed urgent foreign and handled conversations`() {
        val conversations = buildConversationInbox(
            listOf(
                message(id = 1, packageName = "wechat", sender = "Li", timestamp = 10)
                    .copy(detectedLanguage = "zh"),
                message(id = 2, packageName = "kakao", sender = "민수", timestamp = 20),
                message(id = 3, packageName = "kakao", sender = "민수", timestamp = 30)
                    .copy(isOutgoing = true),
                message(id = 4, packageName = "slack", sender = "Sarah", timestamp = 40),
            ),
        )

        val summary = summarizeInbox(conversations)

        assertEquals(2, summary.needsReplyCount)
        assertEquals(1, summary.urgentCount)
        assertEquals(1, summary.foreignLanguageCount)
        assertEquals(1, summary.handledCount)
    }

    @Test
    fun `combines linked messenger channels into one person timeline`() {
        val links = listOf(
            ContactLink("telegram", "Alex", "contact-alex", "Alex Kim"),
            ContactLink("whatsapp", "Alex K", "contact-alex", "Alex Kim"),
        )

        val conversation = buildConversationInbox(
            messages = listOf(
                message(id = 1, packageName = "telegram", sender = "Alex", timestamp = 10),
                message(id = 2, packageName = "whatsapp", sender = "Alex K", timestamp = 20),
            ),
            contactLinks = links,
        ).single()

        assertEquals("Alex Kim", conversation.displayName)
        assertEquals(2, conversation.channels.size)
        assertEquals(listOf(1L, 2L), conversation.messages.map { it.id })
        assertEquals("whatsapp", conversation.id.packageName)
    }

    @Test
    fun `prioritizes current urgent reply then reply then handled`() {
        val conversations = buildConversationInbox(
            listOf(
                message(id = 2, sender = "일반 답장", timestamp = 40),
                message(id = 3, sender = "처리됨", timestamp = 50).copy(isOutgoing = true),
                message(id = 4, sender = "긴급 답장", timestamp = 30).copy(priority = "URGENT"),
            ),
        )

        val prioritized = prioritizeInbox(conversations)

        assertEquals(listOf("긴급 답장", "일반 답장", "처리됨"), prioritized.map { it.id.sender })
    }

    @Test
    fun `does not keep old urgent state after a newer normal message`() {
        val conversation = buildConversationInbox(
            listOf(
                message(id = 1, sender = "민수", timestamp = 10).copy(priority = "URGENT"),
                message(id = 2, sender = "민수", timestamp = 20).copy(priority = "LATER"),
            ),
        ).single()

        assertEquals(false, conversation.isUrgent)
    }

    @Test
    fun `manually handled incoming message no longer needs a reply`() {
        val conversation = buildConversationInbox(
            listOf(message(id = 1, sender = "민수", timestamp = 10).copy(isHandled = true)),
        ).single()

        assertEquals(false, conversation.needsReply)
        assertEquals(1, summarizeInbox(listOf(conversation)).handledCount)
    }

    @Test
    fun `new incoming message reopens a handled conversation`() {
        val conversation = buildConversationInbox(
            listOf(
                message(id = 1, sender = "민수", timestamp = 10).copy(isHandled = true),
                message(id = 2, sender = "민수", timestamp = 20),
            ),
        ).single()

        assertEquals(true, conversation.needsReply)
    }

    @Test
    fun `foreign language summary follows selected app language`() {
        val conversations = buildConversationInbox(
            listOf(
                message(id = 1, sender = "English", timestamp = 10).copy(
                    detectedLanguage = "en",
                ),
                message(id = 2, sender = "Korean", timestamp = 20).copy(
                    detectedLanguage = "ko",
                ),
            ),
        )

        assertEquals(1, summarizeInbox(conversations, "ko").foreignLanguageCount)
        assertEquals(1, summarizeInbox(conversations, "en").foreignLanguageCount)
    }

    private fun message(
        id: Long,
        packageName: String = "kakao",
        sender: String,
        timestamp: Long,
    ) = CapturedMessage(
        id = id,
        packageName = packageName,
        sender = sender,
        originalText = "메시지 $id",
        detectedLanguage = "ko",
        translatedText = "메시지 $id",
        timestamp = timestamp,
        priority = if (id == 1L) "URGENT" else "LATER",
        hasRemoteInputAction = true,
        rawNotificationKey = "key-$id",
    )
}
