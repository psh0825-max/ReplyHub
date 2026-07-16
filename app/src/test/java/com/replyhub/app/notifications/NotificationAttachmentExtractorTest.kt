package com.replyhub.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class NotificationAttachmentExtractorTest {
    @Test
    fun `recognizes a photo notification without treating normal photo talk as an attachment`() {
        assertEquals(
            AttachmentKinds.IMAGE_UNAVAILABLE,
            inferAttachmentHint("사진을 보냈습니다.")?.kind,
        )
        assertNull(inferAttachmentHint("주말에 사진 찍으러 갈까요?"))
    }

    @Test
    fun `recognizes common file names`() {
        val hint = inferAttachmentHint("견적서_최종.xlsx")

        assertEquals(AttachmentKinds.FILE_UNAVAILABLE, hint?.kind)
        assertEquals("견적서_최종.xlsx", hint?.name)
    }

    @Test
    fun `ignores ordinary messages`() {
        assertNull(inferAttachmentHint("회의 시간을 다시 확인해 주세요."))
    }

    @Test
    fun `stops copying when an attachment exceeds the limit`() {
        val input = ByteArrayInputStream(ByteArray(12))
        val output = ByteArrayOutputStream()

        assertThrows(IllegalArgumentException::class.java) {
            input.copyToWithLimit(output, maxBytes = 8)
        }
    }
}
