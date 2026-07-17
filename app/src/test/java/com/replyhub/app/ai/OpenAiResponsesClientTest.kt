package com.replyhub.app.ai

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayDeque

class OpenAiResponsesClientTest {
    @Test
    fun `connection test accepts a 2xx response without output text`() = runTest {
        val connection = FakeHttpURLConnection(
            statusCode = 200,
            responseBody = incompleteResponse(),
        )
        val client = clientWith(connection)

        val result = client.testConnection("valid-key")

        assertTrue(result.isSuccess)
        val request = connection.requestJson()
        assertEquals(256, request.getInt("max_output_tokens"))
        assertEquals("low", request.getJSONObject("reasoning").getString("effort"))
    }

    @Test
    fun `connection test preserves a 401 server error`() = runTest {
        val connection = FakeHttpURLConnection(
            statusCode = 401,
            responseBody = """{"error":{"message":"Incorrect API key"}}""",
        )
        val client = clientWith(connection)

        val result = client.testConnection("invalid-key")

        assertTrue(result.isFailure)
        assertEquals("Incorrect API key", result.exceptionOrNull()?.message)
    }

    @Test
    fun `incomplete response retries once with a doubled token budget`() = runTest {
        val first = FakeHttpURLConnection(200, incompleteResponse())
        val second = FakeHttpURLConnection(200, completedResponse("OK"))
        val client = clientWith(first, second)

        val response = client.createResponse(
            apiKey = "valid-key",
            instructions = "Reply briefly.",
            input = "Hello",
            maxOutputTokens = 100,
        )

        assertEquals("OK", response.outputText)
        assertEquals(100, first.requestJson().getInt("max_output_tokens"))
        assertEquals(200, second.requestJson().getInt("max_output_tokens"))
    }

    @Test
    fun `second incomplete response reports the length limit accurately`() = runTest {
        val client = clientWith(
            FakeHttpURLConnection(200, incompleteResponse()),
            FakeHttpURLConnection(200, incompleteResponse()),
        )

        val result = runCatching {
            client.createResponse(
                apiKey = "valid-key",
                instructions = "Reply briefly.",
                input = "Hello",
                maxOutputTokens = 100,
            )
        }

        assertTrue(result.exceptionOrNull() is OpenAiIncompleteException)
        assertEquals(
            "OpenAI 응답이 길이 제한으로 잘렸습니다.",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `retryable HTTP error retries once after backoff`() = runTest {
        val connections = ArrayDeque(
            listOf(
                FakeHttpURLConnection(429, """{"error":{"message":"Rate limited"}}"""),
                FakeHttpURLConnection(200, completedResponse("OK")),
            ),
        )
        val delays = mutableListOf<Long>()
        val client = OpenAiResponsesClient(
            safetyIdentifier = "test-safety-id",
            connectionFactory = { connections.removeFirst() },
            retryDelay = { delays += it },
        )

        val response = client.createResponse(
            apiKey = "valid-key",
            instructions = "Reply briefly.",
            input = "Hello",
        )

        assertEquals("OK", response.outputText)
        assertEquals(listOf(400L), delays)
    }

    @Test
    fun `non-retryable HTTP error fails immediately`() = runTest {
        val connection = FakeHttpURLConnection(
            statusCode = 400,
            responseBody = """{"error":{"message":"Bad request"}}""",
        )
        val client = clientWith(connection)

        val result = runCatching {
            client.createResponse(
                apiKey = "valid-key",
                instructions = "Reply briefly.",
                input = "Hello",
            )
        }

        val error = result.exceptionOrNull()
        assertTrue(error is OpenAiHttpException)
        assertEquals(400, (error as OpenAiHttpException).statusCode)
        assertEquals("Bad request", error.message)
    }

    @Test
    fun `citations only retain HTTPS URLs`() = runTest {
        val responseJson = JSONObject(completedResponse("Answer"))
        responseJson.getJSONArray("output")
            .getJSONObject(0)
            .getJSONArray("content")
            .getJSONObject(0)
            .put(
                "annotations",
                org.json.JSONArray()
                    .put(
                        JSONObject()
                            .put("type", "url_citation")
                            .put("title", "Secure")
                            .put("url", "https://example.com/secure"),
                    )
                    .put(
                        JSONObject()
                            .put("type", "url_citation")
                            .put("title", "Cleartext")
                            .put("url", "http://example.com/cleartext"),
                    ),
            )
        val client = clientWith(FakeHttpURLConnection(200, responseJson.toString()))

        val response = client.createResponse(
            apiKey = "valid-key",
            instructions = "Reply briefly.",
            input = "Hello",
        )

        assertEquals(listOf("https://example.com/secure"), response.citations.map { it.url })
    }

    private fun clientWith(vararg connections: FakeHttpURLConnection): OpenAiResponsesClient {
        val queue = ArrayDeque(connections.toList())
        return OpenAiResponsesClient("test-safety-id") { queue.removeFirst() }
    }

    private fun incompleteResponse(): String =
        """
            {
              "status": "incomplete",
              "incomplete_details": {"reason": "max_output_tokens"},
              "output": []
            }
        """.trimIndent()

    private fun completedResponse(text: String): String =
        JSONObject()
            .put("status", "completed")
            .put(
                "output",
                org.json.JSONArray().put(
                    JSONObject()
                        .put("type", "message")
                        .put(
                            "content",
                            org.json.JSONArray().put(
                                JSONObject()
                                    .put("type", "output_text")
                                    .put("text", text),
                            ),
                        ),
                ),
            )
            .toString()
}

private class FakeHttpURLConnection(
    private val statusCode: Int,
    private val responseBody: String,
) : HttpURLConnection(URL("https://api.openai.com/v1/responses")) {
    private val writtenBody = ByteArrayOutputStream()

    override fun connect() = Unit

    override fun disconnect() = Unit

    override fun usingProxy(): Boolean = false

    override fun getResponseCode(): Int = statusCode

    override fun getOutputStream(): OutputStream = writtenBody

    override fun getInputStream(): InputStream {
        check(statusCode in 200..299)
        return responseBody.byteInputStream(Charsets.UTF_8)
    }

    override fun getErrorStream(): InputStream? =
        responseBody.takeIf { statusCode !in 200..299 }
            ?.let { ByteArrayInputStream(it.toByteArray(Charsets.UTF_8)) }

    fun requestJson(): JSONObject = JSONObject(writtenBody.toString(Charsets.UTF_8.name()))
}
