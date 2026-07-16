package com.replyhub.app.ai

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.replyhub.app.data.CapturedMessage
import com.replyhub.app.data.MessageRepository
import com.replyhub.app.data.ReplyHubDatabase
import com.replyhub.app.domain.OpenAiReplyAssistant
import com.replyhub.app.domain.ReplyGenerationMode
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Ignore("Manual test: requires a disposable device with a configured API key")
class OpenAiIntegrationTest {
    @Test
    fun structuredContextAndRequiredWebSearchWorkWithConfiguredKey() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val keyStore = ApiKeyStore(context)
        val apiKey = keyStore.read()
        assumeTrue("OpenAI API key is not configured", !apiKey.isNullOrBlank())
        val client = OpenAiResponsesClient(keyStore.safetyIdentifier())
        val output = StructuredOutput(
            name = "integration_answer",
            schema = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject().put("answer", JSONObject().put("type", "string")),
                )
                .put("required", org.json.JSONArray(listOf("answer")))
                .put("additionalProperties", false),
        )

        val structuredResponse = client.createResponse(
            apiKey = requireNotNull(apiKey),
            instructions = "Return the requested structured answer.",
            input = "Reply with the word ready.",
            structuredOutput = output,
            maxOutputTokens = 80,
        )
        assertEquals("ready", JSONObject(structuredResponse.outputText).getString("answer").lowercase())

        val searchResponse = client.createResponse(
            apiKey = apiKey,
            instructions = "Search the web and return one short factual answer.",
            input = "What is the official name of OpenAI's Responses API?",
            webSearchMode = WebSearchMode.REQUIRED,
            structuredOutput = output,
            maxOutputTokens = 180,
        )
        assertTrue(searchResponse.usedWebSearch)
        assertTrue(searchResponse.citations.isNotEmpty())

        val database = Room.inMemoryDatabaseBuilder(context, ReplyHubDatabase::class.java).build()
        try {
            val repository = MessageRepository(database.capturedMessageDao())
            repository.save(
                CapturedMessage(
                    packageName = "synthetic.messenger",
                    sender = "Demo Partner",
                    originalText = "내일 회의 자료 확인해 주실 수 있나요?",
                    detectedLanguage = "ko",
                    translatedText = "내일 회의 자료 확인해 주실 수 있나요?",
                    timestamp = 100,
                    priority = "LATER",
                    hasRemoteInputAction = false,
                    rawNotificationKey = "synthetic-incoming",
                ),
            )
            val assistant = OpenAiReplyAssistant(repository, keyStore, client)
            val draft = assistant.createDraft(
                message = repository.recentForConversation(
                    "synthetic.messenger",
                    "Demo Partner",
                    1,
                ).single(),
                userDirection = "가능하다고 짧고 정중하게 답해줘",
                generationMode = ReplyGenerationMode.CONTEXT,
            )

            assertEquals(OpenAiResponsesClient.MODEL, draft.engine)
            assertTrue(draft.recipientDraft.isNotBlank())
            assertTrue(draft.warning == null)
        } finally {
            database.close()
        }
    }
}
