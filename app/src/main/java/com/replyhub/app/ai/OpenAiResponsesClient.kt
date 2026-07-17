package com.replyhub.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class OpenAiCitation(
    val title: String,
    val url: String,
)

data class OpenAiResponse(
    val outputText: String,
    val usedWebSearch: Boolean,
    val citations: List<OpenAiCitation>,
)

enum class WebSearchMode {
    DISABLED,
    REQUIRED,
}

data class StructuredOutput(
    val name: String,
    val schema: JSONObject,
)

class OpenAiIncompleteException(
    val reason: String,
) : IllegalStateException(
    if (reason == MAX_OUTPUT_TOKENS_REASON) {
        "OpenAI 응답이 길이 제한으로 잘렸습니다."
    } else {
        "OpenAI 응답 생성이 완료되지 않았습니다." +
            reason.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    },
)

class OpenAiHttpException(
    val statusCode: Int,
    message: String,
) : IllegalStateException(message)

class OpenAiResponsesClient(
    private val safetyIdentifier: String,
    private val retryDelay: suspend (Long) -> Unit = { millis -> delay(millis) },
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) {
    suspend fun createResponse(
        apiKey: String,
        model: String = MODEL,
        instructions: String,
        input: String,
        webSearchMode: WebSearchMode = WebSearchMode.DISABLED,
        structuredOutput: StructuredOutput? = null,
        maxOutputTokens: Int = 700,
        reasoningEffort: String = "low",
    ): OpenAiResponse = withContext(Dispatchers.IO) {
        var currentMaxOutputTokens = maxOutputTokens
        var incompleteRetryCount = 0
        var parsedResponse: OpenAiResponse? = null

        while (parsedResponse == null) {
            val request = buildRequest(
                model = model,
                instructions = instructions,
                input = input,
                webSearchMode = webSearchMode,
                structuredOutput = structuredOutput,
                maxOutputTokens = currentMaxOutputTokens,
                reasoningEffort = reasoningEffort,
            )
            val responseBody = executeRequestWithRetry(apiKey, request)

            try {
                parsedResponse = parseResponse(JSONObject(responseBody))
            } catch (error: OpenAiIncompleteException) {
                if (
                    error.reason != MAX_OUTPUT_TOKENS_REASON ||
                    incompleteRetryCount >= MAX_INCOMPLETE_RETRIES
                ) {
                    throw error
                }
                incompleteRetryCount += 1
                currentMaxOutputTokens = (currentMaxOutputTokens.toLong() * 2)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            }
        }
        parsedResponse
    }

    suspend fun testConnection(apiKey: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val request = buildRequest(
                model = MODEL,
                instructions = "Reply with exactly OK.",
                input = "Connection test",
                webSearchMode = WebSearchMode.DISABLED,
                structuredOutput = null,
                maxOutputTokens = 256,
                reasoningEffort = "low",
            )
            executeRequestWithRetry(apiKey, request)
            Unit
        }
    }

    private suspend fun executeRequestWithRetry(apiKey: String, request: JSONObject): String {
        var retryCount = 0
        while (true) {
            try {
                return executeRequest(apiKey, request)
            } catch (error: OpenAiHttpException) {
                if (error.statusCode !in RETRYABLE_HTTP_STATUS_CODES || retryCount >= MAX_HTTP_RETRIES) {
                    throw error
                }
                val backoffMillis = RETRY_BASE_DELAY_MILLIS * (1L shl retryCount)
                retryCount += 1
                retryDelay(backoffMillis)
            }
        }
    }

    private fun buildRequest(
        model: String,
        instructions: String,
        input: String,
        webSearchMode: WebSearchMode,
        structuredOutput: StructuredOutput?,
        maxOutputTokens: Int,
        reasoningEffort: String,
    ): JSONObject {
        val request = JSONObject()
            .put("model", model)
            .put("instructions", instructions)
            .put("input", input)
            .put("store", false)
            .put("safety_identifier", safetyIdentifier)
            .put("max_output_tokens", maxOutputTokens)
            .put("reasoning", JSONObject().put("effort", reasoningEffort))

        structuredOutput?.let { output ->
            request.put(
                "text",
                JSONObject().put(
                    "format",
                    JSONObject()
                        .put("type", "json_schema")
                        .put("name", output.name)
                        .put("strict", true)
                        .put("schema", output.schema),
                ),
            )
        }

        if (webSearchMode != WebSearchMode.DISABLED) {
            request.put(
                "tools",
                JSONArray().put(
                    JSONObject()
                        .put("type", "web_search")
                        .put("search_context_size", "low"),
                ),
            )
            request.put("tool_choice", "required")
            request.put("include", JSONArray().put("web_search_call.action.sources"))
        }

        return request
    }

    private fun executeRequest(apiKey: String, request: JSONObject): String {
        val connection = connectionFactory(URL(RESPONSES_URL)).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(request.toString())
            }
            val status = connection.responseCode
            val responseBody = (if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            })?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty().ifBlank { "OpenAI API 요청에 실패했습니다. ($status)" }
                throw OpenAiHttpException(status, message)
            }
            return responseBody
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(json: JSONObject): OpenAiResponse {
        if (json.optString("status") == "incomplete") {
            val reason = json.optJSONObject("incomplete_details")
                ?.optString("reason")
                .orEmpty()
            throw OpenAiIncompleteException(reason)
        }

        val output = json.optJSONArray("output") ?: JSONArray()
        val textParts = mutableListOf<String>()
        val citations = linkedMapOf<String, OpenAiCitation>()
        var usedWebSearch = false

        for (index in 0 until output.length()) {
            val item = output.optJSONObject(index) ?: continue
            if (item.optString("type") == "web_search_call") usedWebSearch = true
            if (item.optString("type") == "web_search_call") {
                val sources = item.optJSONObject("action")?.optJSONArray("sources") ?: JSONArray()
                for (sourceIndex in 0 until sources.length()) {
                    val source = sources.optJSONObject(sourceIndex) ?: continue
                    val url = source.optString("url")
                    if (!url.isSafeWebUrl()) continue
                    citations[url] = OpenAiCitation(
                        title = source.optString("title").ifBlank { url },
                        url = url,
                    )
                }
            }
            if (item.optString("type") != "message") continue

            val content = item.optJSONArray("content") ?: continue
            for (contentIndex in 0 until content.length()) {
                val part = content.optJSONObject(contentIndex) ?: continue
                if (part.optString("type") == "output_text") {
                    part.optString("text").takeIf { it.isNotBlank() }?.let(textParts::add)
                }
                if (part.optString("type") == "refusal") {
                    error(part.optString("refusal").ifBlank { "요청을 처리할 수 없습니다." })
                }
                val annotations = part.optJSONArray("annotations") ?: continue
                for (annotationIndex in 0 until annotations.length()) {
                    val annotation = annotations.optJSONObject(annotationIndex) ?: continue
                    if (annotation.optString("type") != "url_citation") continue
                    val url = annotation.optString("url")
                    if (!url.isSafeWebUrl()) continue
                    citations[url] = OpenAiCitation(
                        title = annotation.optString("title").ifBlank { url },
                        url = url,
                    )
                }
            }
        }

        val outputText = textParts.joinToString("\n").trim()
        check(outputText.isNotBlank()) { "OpenAI API 응답에서 텍스트를 찾지 못했습니다." }
        return OpenAiResponse(outputText, usedWebSearch, citations.values.toList())
    }

    companion object {
        const val MODEL = "gpt-5.6"
        const val FAST_MODEL = "gpt-5.6-luna"
        private const val RESPONSES_URL = "https://api.openai.com/v1/responses"
        private const val CONNECT_TIMEOUT_MILLIS = 15_000
        private const val READ_TIMEOUT_MILLIS = 45_000
        private const val MAX_INCOMPLETE_RETRIES = 1
        private const val MAX_HTTP_RETRIES = 1
        private const val RETRY_BASE_DELAY_MILLIS = 400L
        private val RETRYABLE_HTTP_STATUS_CODES = setOf(429, 500, 502, 503)
    }
}

private const val MAX_OUTPUT_TOKENS_REASON = "max_output_tokens"

private fun String.isSafeWebUrl(): Boolean =
    startsWith("https://", ignoreCase = true)
