package com.replyhub.app.ai

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenAiConnectionTest {
    @Test
    fun configuredKeyPassesConnectionTest() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val keyStore = ApiKeyStore(context)
        val apiKey = keyStore.read()
        assumeTrue("OpenAI API key is not configured", !apiKey.isNullOrBlank())

        val result = OpenAiResponsesClient(keyStore.safetyIdentifier())
            .testConnection(requireNotNull(apiKey))

        assertTrue(result.exceptionOrNull()?.message, result.isSuccess)
    }

    @Test
    fun fastModelAcceptsNoneReasoningEffort() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val keyStore = ApiKeyStore(context)
        val apiKey = keyStore.read()
        assumeTrue("OpenAI API key is not configured", !apiKey.isNullOrBlank())

        val response = OpenAiResponsesClient(keyStore.safetyIdentifier()).createResponse(
            apiKey = requireNotNull(apiKey),
            model = OpenAiResponsesClient.FAST_MODEL,
            instructions = "Reply with exactly OK.",
            input = "Reasoning effort compatibility test",
            maxOutputTokens = 256,
            reasoningEffort = "none",
        )

        assertTrue(response.outputText.isNotBlank())
    }
}
