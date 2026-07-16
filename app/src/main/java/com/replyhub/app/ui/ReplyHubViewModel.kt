package com.replyhub.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.replyhub.app.ReplyHubApplication
import com.replyhub.app.data.AppLanguage
import com.replyhub.app.data.CapturedMessage
import com.replyhub.app.data.needsTranslationRefresh
import com.replyhub.app.data.PrivacySettingsStore
import com.replyhub.app.domain.DemoMessages
import com.replyhub.app.domain.DraftReply
import com.replyhub.app.domain.ConversationId
import com.replyhub.app.domain.ReplyGenerationMode
import com.replyhub.app.notifications.ReplyDispatcher
import com.replyhub.app.notifications.NotificationReplyStore
import com.replyhub.app.notifications.ReplyHubNotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.io.File
import java.util.concurrent.TimeUnit

enum class ComposerMode {
    ASSISTED_REPLY,
    DIRECT_MESSAGE,
}

data class ComposerState(
    val message: CapturedMessage? = null,
    val mode: ComposerMode = ComposerMode.ASSISTED_REPLY,
    val direction: String = "",
    val replyMode: ReplyGenerationMode = ReplyGenerationMode.CONTEXT,
    val includeSources: Boolean = true,
    val draft: DraftReply? = null,
    val draftEdited: Boolean = false,
    val pendingExternalSend: PendingExternalSend? = null,
    val isGenerating: Boolean = false,
    val isSending: Boolean = false,
    val isListening: Boolean = false,
    val notice: String? = null,
    val noticeIsError: Boolean = false,
)

data class PendingExternalSend(
    val recipientText: String,
    val koreanText: String?,
    val englishText: String?,
)

data class AiSettingsState(
    val isConfigured: Boolean = false,
    val isWorking: Boolean = false,
    val notice: String? = null,
    val isError: Boolean = false,
)

class ReplyHubViewModel(application: Application) : AndroidViewModel(application) {
    private val replyHubApplication = application as ReplyHubApplication
    private val repository = replyHubApplication.repository
    private val assistant = replyHubApplication.replyAssistant
    private val messageProcessor = replyHubApplication.messageProcessor
    private val dispatcher = ReplyDispatcher(application)
    private var translationRefreshJob: Job? = null

    private val _aiSettings = MutableStateFlow(
        AiSettingsState(isConfigured = replyHubApplication.apiKeyStore.isConfigured()),
    )
    val aiSettings: StateFlow<AiSettingsState> = _aiSettings.asStateFlow()

    val messages: StateFlow<List<CapturedMessage>> = repository.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val contactLinks = replyHubApplication.contactLinkStore.links
    val demoMode = replyHubApplication.demoModeStore.enabled
    val appLanguage = replyHubApplication.appLanguageStore.language
    val enabledCapturePackages = replyHubApplication.privacySettingsStore.enabledPackages
    val retentionDays = replyHubApplication.privacySettingsStore.retentionDays

    private val _composer = MutableStateFlow(ComposerState())
    val composer: StateFlow<ComposerState> = _composer.asStateFlow()

    init {
        refreshIncompleteTranslations()
        enforceRetention()
        if (!replyHubApplication.demoModeStore.isEnabled()) {
            ReplyHubNotificationListener.resumeLiveCapture(application)
        }
    }

    fun openComposer(message: CapturedMessage) {
        ReplyHubNotificationListener.resumeLiveCapture(getApplication())
        _composer.value = ComposerState(message = message)
    }

    fun openDirectComposer(message: CapturedMessage) {
        ReplyHubNotificationListener.resumeLiveCapture(getApplication())
        _composer.value = ComposerState(
            message = message,
            mode = ComposerMode.DIRECT_MESSAGE,
        )
    }

    fun closeComposer() {
        _composer.value = ComposerState()
    }

    fun updateDirection(value: String) {
        _composer.value = _composer.value.copy(
            direction = value,
            draft = null,
            notice = null,
            noticeIsError = false,
        )
    }

    fun setReplyMode(value: ReplyGenerationMode) {
        _composer.value = _composer.value.copy(
            replyMode = value,
            draft = null,
            notice = null,
            noticeIsError = false,
        )
    }

    fun setIncludeSources(value: Boolean) {
        _composer.value = _composer.value.copy(includeSources = value)
    }

    fun setListening(value: Boolean) {
        _composer.value = _composer.value.copy(isListening = value)
    }

    fun setNotice(value: String) {
        _composer.value = _composer.value.copy(
            notice = value,
            noticeIsError = true,
            isListening = false,
        )
    }

    fun handleVoiceResult(value: String) {
        updateDirection(value)
        if (_composer.value.mode == ComposerMode.ASSISTED_REPLY) {
            generateDraft()
        }
    }

    fun generateDraft() {
        val state = _composer.value
        val message = state.message ?: return
        _composer.value = state.copy(
            draft = null,
            isGenerating = true,
            notice = null,
            noticeIsError = false,
        )
        viewModelScope.launch {
            val result = runCatching {
                assistant.createDraft(message, state.direction, state.replyMode)
            }
            val current = _composer.value
            if (current.message?.id == message.id && current.replyMode == state.replyMode) {
                _composer.value = current.copy(
                    draft = result.getOrNull(),
                    isGenerating = false,
                    notice = result.exceptionOrNull()?.toFriendlyMessage(),
                    noticeIsError = result.isFailure,
                )
            }
        }
    }

    fun updateDraftText(value: String) {
        val draft = _composer.value.draft ?: return
        _composer.value = _composer.value.copy(
            draft = draft.copy(
                recipientDraft = value,
                evidence = null,
                citations = emptyList(),
            ),
            draftEdited = true,
            notice = null,
            noticeIsError = false,
        )
    }

    fun sendDraft() {
        val state = _composer.value
        val message = state.message ?: return
        val draft = state.draft ?: return
        val recipientText = if (
            state.replyMode == ReplyGenerationMode.WEB_SEARCH &&
            state.includeSources &&
            draft.citations.isNotEmpty()
        ) {
            buildString {
                append(draft.recipientDraft)
                append("\n\nSources:\n")
                append(draft.citations.take(2).joinToString("\n") { it.url })
            }
        } else {
            draft.recipientDraft
        }
        dispatchAndStore(
            state = state,
            message = message,
            recipientText = recipientText,
            englishText = if (state.draftEdited) null else draft.englishDraft,
            koreanText = if (state.draftEdited) null else draft.koreanDraft,
        )
    }

    fun sendDirectMessage() {
        val state = _composer.value
        val message = state.message ?: return
        val text = state.direction.trim()
        if (text.isBlank()) return
        dispatchAndStore(
            state = state,
            message = message,
            recipientText = text,
            koreanText = null,
            englishText = null,
        )
    }

    private fun dispatchAndStore(
        state: ComposerState,
        message: CapturedMessage,
        recipientText: String,
        koreanText: String?,
        englishText: String?,
    ) {
        if (state.isSending) return
        _composer.value = state.copy(
            isSending = true,
            notice = null,
            noticeIsError = false,
        )
        viewModelScope.launch {
            val result = dispatcher.dispatch(
                message,
                recipientText,
                replyHubApplication.appLanguageStore.current(),
            )
            if (result.sentDirectly) {
                _composer.value = ComposerState()
                storeOutgoing(message, recipientText, koreanText, englishText)
            } else if (_composer.value.message?.id == message.id) {
                _composer.value = _composer.value.copy(
                    isSending = false,
                    pendingExternalSend = PendingExternalSend(
                        recipientText = recipientText,
                        koreanText = koreanText,
                        englishText = englishText,
                    ),
                    notice = result.message + " " + localize(
                        "전송했다면 돌아와서 '보냄으로 표시'를 눌러 주세요.",
                        "If you sent it, return and tap 'Mark as sent'.",
                    ),
                    noticeIsError = false,
                )
            }
        }
    }

    fun confirmExternalSend() {
        val state = _composer.value
        val message = state.message ?: return
        val pending = state.pendingExternalSend ?: return
        _composer.value = ComposerState()
        viewModelScope.launch {
            storeOutgoing(
                message = message,
                recipientText = pending.recipientText,
                koreanText = pending.koreanText,
                englishText = pending.englishText,
            )
        }
    }

    fun cancelExternalSendConfirmation() {
        _composer.value = _composer.value.copy(
            pendingExternalSend = null,
            notice = null,
            noticeIsError = false,
        )
    }

    private suspend fun storeOutgoing(
        message: CapturedMessage,
        recipientText: String,
        koreanText: String?,
        englishText: String?,
    ) {
        val outgoingId = repository.save(
            CapturedMessage(
                packageName = message.packageName,
                sender = message.sender,
                originalText = recipientText,
                detectedLanguage = message.detectedLanguage,
                translatedText = koreanText ?: recipientText,
                englishTranslatedText = englishText ?: recipientText,
                timestamp = System.currentTimeMillis(),
                priority = "LATER",
                hasRemoteInputAction = false,
                rawNotificationKey = "replyhub-outgoing:${UUID.randomUUID()}",
                isOutgoing = true,
                conversationId = message.conversationId,
                conversationTitle = message.conversationTitle,
            ),
        )
        if (outgoingId > 0 && (koreanText == null || englishText == null)) {
            val enrichment = messageProcessor.enrich(recipientText)
            repository.updateEnrichment(
                messageId = outgoingId,
                expectedOriginalText = recipientText,
                detectedLanguage = enrichment.detectedLanguage,
                koreanTranslation = enrichment.translatedText,
                englishTranslation = enrichment.englishTranslatedText,
                priority = "LATER",
            )
        }
    }

    fun seedDemoData() {
        replyHubApplication.demoModeStore.setEnabled(true)
        NotificationReplyStore.clear()
        viewModelScope.launch { repository.saveAll(DemoMessages.create()) }
    }

    fun resetDemoData() {
        replyHubApplication.demoModeStore.setEnabled(true)
        NotificationReplyStore.clear()
        viewModelScope.launch {
            repository.clear()
            replyHubApplication.contactLinkStore.clear()
            withContext(Dispatchers.IO) {
                replyHubApplication.clearAttachments()
            }
            repository.saveAll(DemoMessages.create())
        }
    }

    fun setDemoMode(enabled: Boolean) {
        replyHubApplication.demoModeStore.setEnabled(enabled)
        if (enabled) {
            NotificationReplyStore.clear()
        } else {
            ReplyHubNotificationListener.resumeLiveCapture(getApplication())
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        replyHubApplication.appLanguageStore.setLanguage(language)
        _aiSettings.value = _aiSettings.value.copy(notice = null, isError = false)
        _composer.value = _composer.value.copy(notice = null, noticeIsError = false)
        refreshIncompleteTranslations()
    }

    fun setCaptureEnabled(packageName: String, enabled: Boolean) {
        replyHubApplication.privacySettingsStore.setCaptureEnabled(packageName, enabled)
        ReplyHubNotificationListener.refreshReplyTargets()
    }

    fun setRetentionDays(days: Int) {
        replyHubApplication.privacySettingsStore.setRetentionDays(days)
        enforceRetention()
    }

    private fun enforceRetention() {
        val days = replyHubApplication.privacySettingsStore.retentionDays.value
        if (days == PrivacySettingsStore.KEEP_FOREVER) return
        viewModelScope.launch(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
            repository.attachmentPathsOlderThan(cutoff).forEach { path ->
                runCatching { File(path).delete() }
            }
            repository.deleteOlderThan(cutoff)
        }
    }

    private fun refreshIncompleteTranslations() {
        if (!replyHubApplication.apiKeyStore.isConfigured()) {
            Log.i(AI_LOG_TAG, "translation refresh skipped: apiConfigured=false")
            return
        }
        translationRefreshJob?.cancel()
        translationRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            val pendingMessages = repository.all()
                .filter(CapturedMessage::needsTranslationRefresh)
                .groupBy { it.originalText }
            Log.i(
                AI_LOG_TAG,
                "translation refresh started: groups=${pendingMessages.size}, " +
                    "messages=${pendingMessages.values.sumOf(List<CapturedMessage>::size)}",
            )
            var updatedRows = 0
            pendingMessages.forEach { (originalText, messages) ->
                if (originalText.isNotBlank()) {
                    val enrichment = messageProcessor.enrich(originalText)
                    messages.forEach { message ->
                        updatedRows += repository.updateTranslations(
                            messageId = message.id,
                            expectedOriginalText = originalText,
                            koreanTranslation = enrichment.translatedText,
                            englishTranslation = enrichment.englishTranslatedText,
                        )
                    }
                }
            }
            Log.i(AI_LOG_TAG, "translation refresh finished: updatedRows=$updatedRows")
        }
    }

    fun linkContacts(channels: List<ConversationId>, displayName: String) {
        replyHubApplication.contactLinkStore.link(channels, displayName)
    }

    fun unlinkContact(contactId: String) {
        replyHubApplication.contactLinkStore.unlink(contactId)
    }

    fun clearMessages() {
        viewModelScope.launch {
            repository.clear()
            replyHubApplication.contactLinkStore.clear()
            withContext(Dispatchers.IO) {
                replyHubApplication.clearAttachments()
            }
        }
    }

    fun saveAndTestApiKey(value: String) {
        val apiKey = value.trim()
        if (apiKey.length < 20) {
            _aiSettings.value = _aiSettings.value.copy(
                notice = localize(
                    "API 키 형식을 확인해 주세요.",
                    "Check the API key format.",
                ),
                isError = true,
            )
            return
        }

        _aiSettings.value = _aiSettings.value.copy(
            isWorking = true,
            notice = null,
            isError = false,
        )
        viewModelScope.launch {
            val connectionResult = replyHubApplication.openAiClient.testConnection(apiKey)
            if (connectionResult.isSuccess) {
                val saveResult = withContext(Dispatchers.IO) {
                    runCatching { replyHubApplication.apiKeyStore.save(apiKey) }
                }
                _aiSettings.value = if (saveResult.isSuccess) {
                    refreshIncompleteTranslations()
                    AiSettingsState(
                        isConfigured = true,
                        notice = localize(
                            "GPT-5.6 연결을 확인했습니다.",
                            "GPT-5.6 connection verified.",
                        ),
                    )
                } else {
                    _aiSettings.value.copy(
                        isWorking = false,
                        notice = saveResult.exceptionOrNull().toFriendlyMessage(),
                        isError = true,
                    )
                }
            } else {
                _aiSettings.value = _aiSettings.value.copy(
                    isWorking = false,
                    notice = connectionResult.exceptionOrNull().toFriendlyMessage(),
                    isError = true,
                )
            }
        }
    }

    fun removeApiKey() {
        replyHubApplication.apiKeyStore.clear()
        _aiSettings.value = AiSettingsState(
            notice = localize(
                "저장된 API 키를 삭제했습니다. 로컬 기본 답장으로 전환됩니다.",
                "The saved API key was removed. ReplyHub is using basic local replies.",
            ),
        )
    }

    private fun Throwable?.toFriendlyMessage(): String {
        val rawMessage = this?.message
            ?.take(180)
            ?.ifBlank { null }
        if (
            replyHubApplication.appLanguageStore.current() == AppLanguage.ENGLISH &&
            rawMessage?.let { HANGUL_REGEX.containsMatchIn(it) } == true
        ) {
            return "The OpenAI request failed. Check your network and API key."
        }
        return rawMessage ?: localize(
            "OpenAI API에 연결하지 못했습니다. 네트워크와 키를 확인해 주세요.",
            "Could not connect to the OpenAI API. Check your network and key.",
        )
    }

    private fun localize(korean: String, english: String): String =
        replyHubApplication.appLanguageStore.current().text(korean, english)

    private companion object {
        const val AI_LOG_TAG = "ReplyHubAI"
        val HANGUL_REGEX = Regex("[가-힣]")
    }
}
