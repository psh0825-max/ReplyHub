package com.replyhub.app.data

import android.content.Context
import com.replyhub.app.domain.ContactLink
import com.replyhub.app.domain.ConversationId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ContactLinkStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _links = MutableStateFlow(load())
    val links: StateFlow<List<ContactLink>> = _links.asStateFlow()

    @Synchronized
    fun link(channels: List<ConversationId>, displayName: String) {
        val selectedChannels = channels.distinctBy { it.packageName to it.conversationId }
        if (selectedChannels.size < 2) return

        val current = _links.value
        val selectedSet = selectedChannels.mapTo(hashSetOf()) { it.packageName to it.conversationId }
        val existingContactIds = current
            .filter { (it.packageName to it.conversationId) in selectedSet }
            .mapTo(linkedSetOf()) { it.contactId }
        val mergedChannels = buildSet {
            addAll(selectedChannels)
            current
                .filter { it.contactId in existingContactIds }
                .forEach {
                    add(ConversationId(it.packageName, it.conversationId, it.sender))
                }
        }
        val contactId = existingContactIds.firstOrNull() ?: UUID.randomUUID().toString()
        val resolvedName = displayName.trim().ifBlank { selectedChannels.first().sender }
        val remaining = current.filter { link ->
            link.contactId !in existingContactIds &&
                mergedChannels.none {
                    it.packageName == link.packageName && it.conversationId == link.conversationId
                }
        }
        val updated = remaining + mergedChannels.map { channel ->
            ContactLink(
                packageName = channel.packageName,
                sender = channel.sender,
                contactId = contactId,
                displayName = resolvedName,
                conversationId = channel.conversationId,
            )
        }
        persist(updated)
    }

    @Synchronized
    fun unlink(contactId: String) {
        persist(_links.value.filterNot { it.contactId == contactId })
    }

    @Synchronized
    fun clear() {
        persist(emptyList())
    }

    private fun persist(value: List<ContactLink>) {
        val json = JSONArray().apply {
            value.forEach { link ->
                put(
                    JSONObject()
                        .put("packageName", link.packageName)
                        .put("sender", link.sender)
                        .put("contactId", link.contactId)
                        .put("displayName", link.displayName)
                        .put("conversationId", link.conversationId),
                )
            }
        }
        preferences.edit().putString(KEY_LINKS, json.toString()).apply()
        _links.value = value
    }

    private fun load(): List<ContactLink> = runCatching {
        val json = JSONArray(preferences.getString(KEY_LINKS, "[]"))
        buildList {
            repeat(json.length()) { index ->
                val item = json.getJSONObject(index)
                add(
                    ContactLink(
                        packageName = item.getString("packageName"),
                        sender = item.getString("sender"),
                        contactId = item.getString("contactId"),
                        displayName = item.getString("displayName"),
                        conversationId = item.optString(
                            "conversationId",
                            item.getString("sender"),
                        ),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val PREFERENCES_NAME = "replyhub_contact_links"
        const val KEY_LINKS = "links"
    }
}
