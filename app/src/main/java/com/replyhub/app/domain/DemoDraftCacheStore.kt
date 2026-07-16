package com.replyhub.app.domain

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class DemoDraftCacheStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(key: String): DraftReply? = runCatching {
        val json = JSONObject(preferences.getString(key, null) ?: return null)
        val citationsJson = json.optJSONArray("citations") ?: JSONArray()
        DraftReply(
            koreanDraft = json.getString("koreanDraft"),
            englishDraft = json.getString("englishDraft"),
            recipientDraft = json.getString("recipientDraft"),
            tone = ReplyTone.valueOf(json.getString("tone")),
            tool = ReplyTool.valueOf(json.getString("tool")),
            evidence = json.optString("evidence").takeIf(String::isNotBlank),
            engine = json.getString("engine"),
            usedHistory = json.optBoolean("usedHistory"),
            citations = buildList {
                repeat(citationsJson.length()) { index ->
                    val item = citationsJson.getJSONObject(index)
                    add(ReplyCitation(item.getString("title"), item.getString("url")))
                }
            },
        )
    }.getOrNull()

    fun save(key: String, draft: DraftReply) {
        val citations = JSONArray().apply {
            draft.citations.forEach { citation ->
                put(JSONObject().put("title", citation.title).put("url", citation.url))
            }
        }
        val json = JSONObject()
            .put("koreanDraft", draft.koreanDraft)
            .put("englishDraft", draft.englishDraft)
            .put("recipientDraft", draft.recipientDraft)
            .put("tone", draft.tone.name)
            .put("tool", draft.tool.name)
            .put("evidence", draft.evidence.orEmpty())
            .put("engine", draft.engine)
            .put("usedHistory", draft.usedHistory)
            .put("citations", citations)
        preferences.edit().putString(key, json.toString()).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "replyhub_demo_draft_cache"
    }
}
