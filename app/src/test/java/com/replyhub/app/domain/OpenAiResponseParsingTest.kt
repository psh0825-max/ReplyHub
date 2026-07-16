package com.replyhub.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenAiResponseParsingTest {
    @Test
    fun `extracts json from a fenced response`() {
        val json = extractJsonObject(
            """
                ```json
                {"detected_language":"en","translated_korean":"안녕하세요","priority":"LATER"}
                ```
            """.trimIndent(),
        )

        assertEquals("en", json.getString("detected_language"))
        assertEquals("안녕하세요", json.getString("translated_korean"))
        assertEquals("LATER", json.getString("priority"))
    }

    @Test
    fun `rejects a response without json`() {
        assertThrows(IllegalArgumentException::class.java) {
            extractJsonObject("No structured response")
        }
    }

    @Test
    fun `enrichment token budget leaves room for structured output`() {
        assertEquals(320, enrichmentOutputTokenBudget(0))
        assertEquals(352, enrichmentOutputTokenBudget(16))
        assertEquals(2_000, enrichmentOutputTokenBudget(1_023))
    }

    @Test
    fun `enrichment token budget is capped for expanded notifications`() {
        assertEquals(2_000, enrichmentOutputTokenBudget(4_000))
    }
}
