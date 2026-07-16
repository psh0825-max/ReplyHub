package com.replyhub.app

import android.app.Application
import com.replyhub.app.ai.ApiKeyStore
import com.replyhub.app.ai.OpenAiResponsesClient
import com.replyhub.app.data.AppLanguageStore
import com.replyhub.app.data.MessageRepository
import com.replyhub.app.data.ContactLinkStore
import com.replyhub.app.data.DemoModeStore
import com.replyhub.app.data.ReplyHubDatabase
import com.replyhub.app.data.PrivacySettingsStore
import com.replyhub.app.domain.OpenAiMessageProcessor
import com.replyhub.app.domain.OpenAiReplyAssistant
import com.replyhub.app.domain.DemoDraftCacheStore
import java.io.File

class ReplyHubApplication : Application() {
    val database by lazy { ReplyHubDatabase.create(this) }
    val repository by lazy { MessageRepository(database.capturedMessageDao()) }
    val contactLinkStore by lazy { ContactLinkStore(this) }
    val demoModeStore by lazy { DemoModeStore(this) }
    val appLanguageStore by lazy { AppLanguageStore(this) }
    val privacySettingsStore by lazy { PrivacySettingsStore(this) }
    val apiKeyStore by lazy { ApiKeyStore(this) }
    val openAiClient by lazy { OpenAiResponsesClient(apiKeyStore.safetyIdentifier()) }
    val messageProcessor by lazy { OpenAiMessageProcessor(apiKeyStore, openAiClient) }
    val demoDraftCacheStore by lazy { DemoDraftCacheStore(this) }
    val replyAssistant by lazy {
        OpenAiReplyAssistant(
            repository = repository,
            apiKeyStore = apiKeyStore,
            client = openAiClient,
            contactLinkStore = contactLinkStore,
            demoModeStore = demoModeStore,
            demoDraftCacheStore = demoDraftCacheStore,
        )
    }

    fun clearAttachments() {
        File(filesDir, "attachments").deleteRecursively()
    }
}
