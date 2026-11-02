package com.replyhub.app.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSendHeuristicsTest {

    @Test
    fun `send labels across languages are recognized`() {
        listOf("Send", " 전송 ", "보내기", "发送", "發送", "送信", "Senden", "Gửi").forEach { label ->
            assertTrue("expected send label: $label", AutoSendHeuristics.isSendLabel(label))
        }
    }

    @Test
    fun `send phrases are recognized`() {
        listOf("Send message", "메시지 보내기", "메세지 보내기", "발송").forEach { label ->
            assertTrue("expected send phrase: $label", AutoSendHeuristics.isSendLabel(label))
        }
    }

    @Test
    fun `resend and unrelated labels are rejected`() {
        listOf(
            "Resend", "재전송", "다시 보내기", "예약 전송", "송금", "Search", "검색",
            "메시지를 입력하고 전송 버튼을 눌러 주세요",
            null, "", "   ",
        ).forEach { label ->
            assertFalse("unexpected send label: $label", AutoSendHeuristics.isSendLabel(label))
        }
    }

    @Test
    fun `send view ids are recognized`() {
        listOf(
            "com.whatsapp:id/send",
            "com.kakao.talk:id/send_button",
            "jp.naver.line.android:id/chat_ui_send_btn",
            "com.example:id/button_send",
        ).forEach { viewId ->
            assertTrue("expected send view id: $viewId", AutoSendHeuristics.isSendViewId(viewId))
        }
    }

    @Test
    fun `misleading view ids are rejected`() {
        listOf(
            "com.example:id/resend_button",
            "com.example:id/unsend_btn",
            "com.example:id/sender_name",
            "com.example:id/send_to_friend_button",
            "com.example:id/send_money_btn",
            "com.example:id/message_list",
            null,
            "",
        ).forEach { viewId ->
            assertFalse("unexpected send view id: $viewId", AutoSendHeuristics.isSendViewId(viewId))
        }
    }

    @Test
    fun `edit text class names are recognized`() {
        assertTrue(AutoSendHeuristics.isEditableClassName("android.widget.EditText"))
        assertTrue(
            AutoSendHeuristics.isEditableClassName(
                "androidx.appcompat.widget.AppCompatEditText",
            ),
        )
        assertFalse(AutoSendHeuristics.isEditableClassName("android.widget.TextView"))
        assertFalse(AutoSendHeuristics.isEditableClassName(null))
    }
}
