package com.replyhub.app

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.LruCache
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.replyhub.app.data.CapturedMessage
import com.replyhub.app.data.AppLanguage
import com.replyhub.app.data.resolvedConversationId
import com.replyhub.app.data.translationFor
import com.replyhub.app.domain.ConversationId
import com.replyhub.app.domain.ConversationSummary
import com.replyhub.app.domain.ReplyTone
import com.replyhub.app.domain.ReplyGenerationMode
import com.replyhub.app.domain.ReplyTool
import com.replyhub.app.domain.SmartInboxSummary
import com.replyhub.app.domain.buildConversationInbox
import com.replyhub.app.domain.prioritizeInbox
import com.replyhub.app.domain.summarizeInbox
import com.replyhub.app.messaging.MessengerCatalog
import com.replyhub.app.notifications.AttachmentKinds
import com.replyhub.app.notifications.NotificationReplyStore
import com.replyhub.app.notifications.ReplyHubNotificationListener
import com.replyhub.app.ui.ComposerMode
import com.replyhub.app.ui.AiSettingsState
import com.replyhub.app.ui.ReplyHubTheme
import com.replyhub.app.ui.ReplyHubViewModel
import com.replyhub.app.ui.VoiceInputController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

private val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.KOREAN }

@Composable
private fun uiText(korean: String, english: String): String =
    LocalAppLanguage.current.text(korean, english)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReplyHubTheme(darkTheme = isSystemInDarkTheme()) {
                val viewModel: ReplyHubViewModel = viewModel()
                val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
                    ReplyHubApp(viewModel)
                }
            }
        }
    }
}

private enum class InboxFilter { ALL, NEEDS_REPLY, URGENT, HANDLED }

private data class InstalledApp(
    val label: String,
    val packageName: String,
)

@Composable
private fun ReplyHubApp(viewModel: ReplyHubViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val contactLinks by viewModel.contactLinks.collectAsStateWithLifecycle()
    val composer by viewModel.composer.collectAsStateWithLifecycle()
    val aiSettings by viewModel.aiSettings.collectAsStateWithLifecycle()
    val demoMode by viewModel.demoMode.collectAsStateWithLifecycle()
    val enabledCapturePackages by viewModel.enabledCapturePackages.collectAsStateWithLifecycle()
    val retentionDays by viewModel.retentionDays.collectAsStateWithLifecycle()
    val appLanguage = LocalAppLanguage.current
    val replyAvailabilityVersion by NotificationReplyStore.changes.collectAsStateWithLifecycle()
    val conversations = remember(messages, contactLinks) {
        buildConversationInbox(messages, contactLinks)
    }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedContactId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNewMessagePicker by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var contactDialogConversation by remember { mutableStateOf<ConversationSummary?>(null) }
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var installedAppsRefreshVersion by remember { mutableIntStateOf(0) }
    var notificationAccess by remember {
        mutableStateOf(hasNotificationAccess(context.packageName, context))
    }

    LaunchedEffect(context, installedAppsRefreshVersion) {
        installedApps = loadLaunchableApps(context)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccess = hasNotificationAccess(context.packageName, context)
                installedAppsRefreshVersion += 1
                ReplyHubNotificationListener.refreshReplyTargets()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selectedConversation = conversations.firstOrNull {
        if (selectedContactId != null) {
            it.contactId == selectedContactId
        } else {
            it.id.packageName == selectedPackage && it.id.conversationId == selectedConversationId
        }
    }
    val selectedCanSendNow = remember(selectedConversation, replyAvailabilityVersion) {
        selectedConversation?.let { conversation ->
            val replyMessage = conversation.latestIncomingMessage
            NotificationReplyStore.isAvailable(
                replyMessage.rawNotificationKey,
                replyMessage.packageName,
                replyMessage.sender,
                replyMessage.resolvedConversationId,
            )
        } ?: false
    }

    if (selectedConversation == null) {
        ConversationInboxScreen(
            conversations = conversations,
            installedApps = installedApps,
            totalMessageCount = messages.size,
            availabilityVersion = replyAvailabilityVersion,
            notificationAccess = notificationAccess,
            aiConfigured = aiSettings.isConfigured,
            demoMode = demoMode,
            onOpenConversation = { id ->
                val conversation = conversations.firstOrNull { it.id == id }
                selectedPackage = id.packageName
                selectedConversationId = id.conversationId
                selectedContactId = conversation?.contactId
            },
            onOpenAppSettings = { showSettings = true },
            onOpenNotificationSettings = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            onSeedDemo = viewModel::seedDemoData,
            onEnableLiveCapture = { viewModel.setDemoMode(false) },
            onNewMessage = { showNewMessagePicker = true },
            onSetHandled = { conversation, handled ->
                viewModel.setMessageHandled(conversation.latestMessage.id, handled)
            },
            onDeleteConversation = { conversation ->
                viewModel.deleteConversation(conversation.channels)
            },
            onOpenInstalledApp = { app ->
                context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { intent ->
                    context.startActivity(intent)
                }
            },
        )
    } else {
        BackHandler {
            selectedPackage = null
            selectedConversationId = null
            selectedContactId = null
        }
        ConversationDetailScreen(
            conversation = selectedConversation,
            canSendNow = selectedCanSendNow,
            onBack = {
                selectedPackage = null
                selectedConversationId = null
                selectedContactId = null
            },
            onReply = { viewModel.openComposer(selectedConversation.latestIncomingMessage) },
            onManageContact = { contactDialogConversation = selectedConversation },
        )
    }

    if (composer.message != null) {
        ReplyDialog(viewModel = viewModel)
    }

    if (showNewMessagePicker) {
        ConversationPickerDialog(
            conversations = conversations,
            availabilityVersion = replyAvailabilityVersion,
            onSelect = { conversation ->
                showNewMessagePicker = false
                viewModel.openDirectComposer(conversation.latestIncomingMessage)
            },
            onDismiss = { showNewMessagePicker = false },
        )
    }

    if (showSettings) {
        AppSettingsDialog(
            state = aiSettings,
            appLanguage = appLanguage,
            notificationAccess = notificationAccess,
            messageCount = messages.size,
            demoMode = demoMode,
            enabledCapturePackages = enabledCapturePackages,
            retentionDays = retentionDays,
            onSaveAndTest = viewModel::saveAndTestApiKey,
            onRemoveApiKey = viewModel::removeApiKey,
            onOpenNotificationSettings = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            onResetDemo = viewModel::resetDemoData,
            onDemoModeChange = viewModel::setDemoMode,
            onAppLanguageChange = viewModel::setAppLanguage,
            onCaptureEnabledChange = viewModel::setCaptureEnabled,
            onRetentionDaysChange = viewModel::setRetentionDays,
            onClearMessages = viewModel::clearMessages,
            onDismiss = { showSettings = false },
        )
    }

    contactDialogConversation?.let { current ->
        ContactLinkDialog(
            current = current,
            conversations = conversations,
            onLink = { target, displayName ->
                viewModel.linkContacts(current.channels + target.channels, displayName)
                contactDialogConversation = null
                selectedPackage = null
                selectedConversationId = null
                selectedContactId = null
            },
            onUnlink = {
                current.contactId?.let(viewModel::unlinkContact)
                contactDialogConversation = null
                selectedPackage = null
                selectedConversationId = null
                selectedContactId = null
            },
            onDismiss = { contactDialogConversation = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationInboxScreen(
    conversations: List<ConversationSummary>,
    installedApps: List<InstalledApp>,
    totalMessageCount: Int,
    availabilityVersion: Long,
    notificationAccess: Boolean,
    aiConfigured: Boolean,
    demoMode: Boolean,
    onOpenConversation: (ConversationId) -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSeedDemo: () -> Unit,
    onEnableLiveCapture: () -> Unit,
    onNewMessage: () -> Unit,
    onSetHandled: (ConversationSummary, Boolean) -> Unit,
    onDeleteConversation: (ConversationSummary) -> Unit,
    onOpenInstalledApp: (InstalledApp) -> Unit,
) {
    val language = LocalAppLanguage.current
    val fallbackMessengerColor = MaterialTheme.colorScheme.secondary
    var filter by rememberSaveable { mutableStateOf(InboxFilter.ALL) }
    var messengerPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ConversationSummary?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val smartSummary = remember(conversations, language) {
        summarizeInbox(conversations, language.storageValue)
    }

    val visibleConversations = prioritizeInbox(
        conversations.filter { conversation ->
            val filterMatches = when (filter) {
                InboxFilter.ALL -> true
                InboxFilter.NEEDS_REPLY -> conversation.needsReply
                InboxFilter.URGENT -> conversation.isUrgent
                InboxFilter.HANDLED -> !conversation.needsReply
            }
            val messengerMatches = messengerPackage == null ||
                conversation.channels.any { it.packageName == messengerPackage }
            val queryMatches = query.isBlank() ||
                conversation.displayName.contains(query, ignoreCase = true) ||
                conversation.channels.any { channel ->
                    channel.sender.contains(query, ignoreCase = true) ||
                        appMeta(channel.packageName, language, fallbackMessengerColor)
                            .name.contains(query, ignoreCase = true)
                } ||
                conversation.messages.any { message ->
                    message.originalText.contains(query, ignoreCase = true) ||
                        message.translatedText.contains(query, ignoreCase = true) ||
                        message.englishTranslatedText.contains(query, ignoreCase = true)
                }
            filterMatches && messengerMatches && queryMatches
        },
        language.storageValue,
    )
    val visibleApps = if (query.isBlank()) {
        emptyList()
    } else {
        installedApps.filter { app ->
            app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
        }
    }
    val installedPackages = remember(installedApps) {
        installedApps.mapTo(hashSetOf()) { it.packageName }
    }
    val connectedPackages = remember(conversations) {
        conversations
            .flatMapTo(hashSetOf()) { conversation ->
                conversation.channels.map(ConversationId::packageName)
            }
    }
    val directReplyPackages = remember(availabilityVersion) {
        NotificationReplyStore.activeTargetPackages()
    }
    val messengerFilters = remember(installedPackages, connectedPackages) {
        MessengerCatalog.apps
            .filterIndexed { index, app ->
                index < 10 || app.packageName in installedPackages ||
                    app.packageName in connectedPackages
            }
            .sortedWith(
                compareByDescending<com.replyhub.app.messaging.MessengerDefinition> {
                    it.packageName in connectedPackages
                }.thenByDescending {
                    it.packageName in installedPackages
                },
            )
            .map { it.packageName }
    }
    val markHandled: (ConversationSummary) -> Unit = { conversation ->
        onSetHandled(conversation, true)
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = language.text(
                    "${conversation.displayName} 대화를 완료했습니다.",
                    "Marked ${conversation.displayName} as handled.",
                ),
                actionLabel = language.text("실행 취소", "Undo"),
            )
            if (result == SnackbarResult.ActionPerformed) {
                onSetHandled(conversation, false)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ReplyHub", fontWeight = FontWeight.SemiBold)
                        Text(
                            language.text(
                                "대화 ${conversations.size}개 · 메시지 ${totalMessageCount}개 · ",
                                "${englishCount(conversations.size, "conversation")} · " +
                                    "${englishCount(totalMessageCount, "message")} · ",
                            ) +
                                buildString {
                                    if (demoMode) append(language.text("데모 · ", "Demo · "))
                                    append(
                                        if (aiConfigured) {
                                            "GPT-5.6"
                                        } else {
                                            language.text("기본 답장", "Basic replies")
                                        },
                                    )
                                },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (conversations.isNotEmpty()) {
                        IconButton(onClick = onNewMessage) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = uiText("새 메시지 작성", "New message"),
                            )
                        }
                    }
                    IconButton(onClick = onOpenAppSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = uiText("ReplyHub 설정", "ReplyHub settings"),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!notificationAccess && !demoMode) {
                NotificationPermissionBanner(onOpenSettings = onOpenNotificationSettings)
            }

            if (demoMode) {
                DemoModeBanner(onEnableLiveCapture = onEnableLiveCapture)
            }

            if (conversations.isNotEmpty()) {
                SmartInboxBand(
                    summary = smartSummary,
                    onClick = {
                        filter = InboxFilter.NEEDS_REPLY
                        messengerPackage = null
                    },
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = {
                    Text(uiText("사람, 대화 또는 설치 앱 검색", "Search people, chats, or apps"))
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = messengerPackage == null,
                    onClick = { messengerPackage = null },
                    label = {
                        Text(
                            language.text(
                                "전체 · 대화 감지 ${connectedPackages.size}",
                                "All · ${connectedPackages.size} with activity",
                            ),
                        )
                    },
                )
                messengerFilters.forEach { packageName ->
                    val messenger = appMeta(packageName, language, fallbackMessengerColor)
                    val isConnected = packageName in connectedPackages
                    val canReplyDirectly = packageName in directReplyPackages
                    val isSelected = messengerPackage == packageName
                    FilterChip(
                        selected = isSelected,
                        onClick = { messengerPackage = packageName },
                        label = { Text(messenger.name) },
                        leadingIcon = {
                            MessengerIcon(
                                packageName = packageName,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingIcon = if (canReplyDirectly || isConnected) {
                            {
                                Icon(
                                    imageVector = if (canReplyDirectly) {
                                        Icons.Outlined.Reply
                                    } else {
                                        Icons.Outlined.Notifications
                                    },
                                    contentDescription = if (canReplyDirectly) {
                                        uiText("바로 답장 가능", "Quick reply available")
                                    } else {
                                        uiText("대화 감지됨", "Conversation activity detected")
                                    },
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        } else {
                            null
                        },
                        colors = if (isConnected) {
                            FilterChipDefaults.filterChipColors(
                                containerColor = messenger.color.copy(alpha = 0.14f),
                                labelColor = messenger.color,
                                iconColor = messenger.color,
                                selectedContainerColor = messenger.color,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White,
                                selectedTrailingIconColor = Color.White,
                            )
                        } else {
                            FilterChipDefaults.filterChipColors()
                        },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == InboxFilter.ALL,
                    onClick = { filter = InboxFilter.ALL },
                    label = { Text(uiText("전체", "All")) },
                )
                FilterChip(
                    selected = filter == InboxFilter.NEEDS_REPLY,
                    onClick = { filter = InboxFilter.NEEDS_REPLY },
                    label = { Text(uiText("답장 필요", "Needs reply")) },
                )
                FilterChip(
                    selected = filter == InboxFilter.URGENT,
                    onClick = { filter = InboxFilter.URGENT },
                    label = { Text(uiText("긴급", "Urgent")) },
                )
                FilterChip(
                    selected = filter == InboxFilter.HANDLED,
                    onClick = { filter = InboxFilter.HANDLED },
                    label = { Text(uiText("처리됨", "Handled")) },
                )
            }

            when {
                query.isNotBlank() && visibleApps.isEmpty() && visibleConversations.isEmpty() -> {
                    EmptySearchResult()
                }
                query.isNotBlank() -> LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    if (visibleApps.isNotEmpty()) {
                        item(key = "installed-apps-title") {
                            SearchSectionHeader(uiText("설치된 앱", "Installed apps"))
                        }
                        items(visibleApps, key = { "app:${it.packageName}" }) { app ->
                            InstalledAppRow(
                                app = app,
                                onClick = { onOpenInstalledApp(app) },
                            )
                            Divider(modifier = Modifier.padding(start = 76.dp))
                        }
                    }
                    if (visibleConversations.isNotEmpty()) {
                        item(key = "conversations-title") {
                            SearchSectionHeader(uiText("대화", "Conversations"))
                        }
                        items(
                            visibleConversations,
                            key = { "conversation:${it.id.packageName}:${it.id.conversationId}" },
                        ) { conversation ->
                            SwipeableConversationRow(
                                conversation = conversation,
                                availabilityVersion = availabilityVersion,
                                onMarkHandled = { markHandled(conversation) },
                                onDeleteRequest = { pendingDelete = conversation },
                                onClick = { onOpenConversation(conversation.id) },
                            )
                            Divider(modifier = Modifier.padding(start = 82.dp))
                        }
                    }
                }
                conversations.isEmpty() -> EmptyInbox(onSeedDemo = onSeedDemo)
                visibleConversations.isEmpty() -> EmptySearchResult()
                else -> LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(visibleConversations, key = { "${it.id.packageName}:${it.id.conversationId}" }) {
                        conversation ->
                        SwipeableConversationRow(
                            conversation = conversation,
                            availabilityVersion = availabilityVersion,
                            onMarkHandled = { markHandled(conversation) },
                            onDeleteRequest = { pendingDelete = conversation },
                            onClick = { onOpenConversation(conversation.id) },
                        )
                        Divider(modifier = Modifier.padding(start = 82.dp))
                    }
                }
            }
        }
    }

    pendingDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(uiText("대화를 삭제할까요?", "Delete conversation?")) },
            text = {
                Text(
                    language.text(
                        "${conversation.displayName} 대화의 메시지 ${conversation.messageCount}개와 " +
                            "저장된 첨부파일을 이 기기에서 삭제합니다.",
                        "Delete ${englishCount(conversation.messageCount, "message")} and saved " +
                            "attachments for ${conversation.displayName} from this device.",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteConversation(conversation)
                    },
                ) {
                    Text(uiText("삭제", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(uiText("취소", "Cancel"))
                }
            },
        )
    }

}

@Composable
private fun DemoModeBanner(onEnableLiveCapture: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    uiText(
                        "데모 모드: 실제 알림 수집 꺼짐",
                        "Demo mode: live capture is off",
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    uiText(
                        "LinkedIn 등 새 메시지를 받으려면 실시간 모드를 켜세요.",
                        "Enable live mode to receive new messages from LinkedIn and other apps.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onEnableLiveCapture) {
                Text(uiText("실시간 켜기", "Go live"))
            }
        }
    }
}

@Composable
private fun SmartInboxBand(
    summary: SmartInboxSummary,
    onClick: () -> Unit,
) {
    val language = LocalAppLanguage.current
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(uiText("AI 인박스", "AI inbox"), fontWeight = FontWeight.SemiBold)
                Text(
                    language.text(
                        "답장 필요 ${summary.needsReplyCount} · 긴급 ${summary.urgentCount} · " +
                            "외국어 ${summary.foreignLanguageCount}",
                        "${englishNeedsReply(summary.needsReplyCount)} · " +
                            "${summary.urgentCount} urgent · " +
                            englishCount(
                                summary.foreignLanguageCount,
                                "foreign language",
                                "foreign languages",
                            ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                uiText("답장 보기", "View replies"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun InstalledAppRow(
    app: InstalledApp,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MessengerIcon(
            packageName = app.packageName,
            appName = app.label,
            modifier = Modifier.size(44.dp),
        )
        Text(
            app.label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
        Icon(
            Icons.Outlined.OpenInNew,
            contentDescription = uiText("${app.label} 열기", "Open ${app.label}"),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContactLinkDialog(
    current: ConversationSummary,
    conversations: List<ConversationSummary>,
    onLink: (ConversationSummary, String) -> Unit,
    onUnlink: () -> Unit,
    onDismiss: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val fallbackMessengerColor = MaterialTheme.colorScheme.secondary
    var displayName by remember(current) { mutableStateOf(current.displayName) }
    var selected by remember(current) { mutableStateOf<ConversationSummary?>(null) }
    val candidates = conversations.filter { candidate ->
        candidate.id != current.id &&
            (current.contactId == null || candidate.contactId != current.contactId)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("통합 연락처", "Unified contact")) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    uiText(
                        "같은 사람의 여러 메신저 대화를 하나의 타임라인으로 묶습니다.",
                        "Combine the same person's chats across messengers into one timeline.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(uiText("표시 이름", "Display name")) },
                    singleLine = true,
                )
                Text(uiText("연결할 대화", "Conversation to link"), fontWeight = FontWeight.SemiBold)
                if (candidates.isEmpty()) {
                    Text(
                        uiText("연결할 다른 대화가 없습니다.", "No other conversations are available."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(
                            candidates,
                            key = { "link:${it.id.packageName}:${it.id.conversationId}" },
                        ) { candidate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = candidate }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                RadioButton(
                                    selected = selected?.id == candidate.id,
                                    onClick = { selected = candidate },
                                )
                                MessengerIcon(
                                    packageName = candidate.latestMessage.packageName,
                                    modifier = Modifier.size(34.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        candidate.displayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        appMeta(
                                            candidate.latestMessage.packageName,
                                            language,
                                            fallbackMessengerColor,
                                        ).name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                if (current.contactId != null) {
                    OutlinedButton(
                        onClick = onUnlink,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(uiText("통합 연락처 연결 해제", "Unlink unified contact"))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selected?.let { onLink(it, displayName) } },
                enabled = selected != null && displayName.isNotBlank(),
            ) {
                Icon(Icons.Outlined.Link, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(uiText("연결", "Link"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(uiText("취소", "Cancel")) }
        },
    )
}

@Composable
private fun ConversationPickerDialog(
    conversations: List<ConversationSummary>,
    availabilityVersion: Long,
    onSelect: (ConversationSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val fallbackMessengerColor = MaterialTheme.colorScheme.secondary
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("최근 대화에 메시지 보내기", "Message a recent conversation")) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                items(
                    conversations,
                    key = { "compose:${it.id.packageName}:${it.id.conversationId}" },
                ) { conversation ->
                    val replyMessage = conversation.latestIncomingMessage
                    val app = appMeta(replyMessage.packageName, language, fallbackMessengerColor)
                    val canSendNow = remember(conversation, availabilityVersion) {
                        NotificationReplyStore.isAvailable(
                            replyMessage.rawNotificationKey,
                            replyMessage.packageName,
                            replyMessage.sender,
                            replyMessage.resolvedConversationId,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(conversation) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MessengerIcon(
                            packageName = replyMessage.packageName,
                            modifier = Modifier.size(38.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                conversation.displayName,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${app.name} · ${
                                    if (canSendNow) {
                                        language.text("바로 전송 가능", "Send directly")
                                    } else {
                                        language.text("메신저에서 보내기", "Send in messenger")
                                    }
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (canSendNow) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    Divider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(uiText("닫기", "Close")) }
        },
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SwipeableConversationRow(
    conversation: ConversationSummary,
    availabilityVersion: Long,
    onMarkHandled: () -> Unit,
    onDeleteRequest: () -> Unit,
    onClick: () -> Unit,
) {
    val dismissState = rememberDismissState { value ->
        when (value) {
            DismissValue.DismissedToEnd -> {
                if (conversation.needsReply) onMarkHandled()
                false
            }
            DismissValue.DismissedToStart -> {
                onDeleteRequest()
                false
            }
            DismissValue.Default -> true
        }
    }
    val directions = if (conversation.needsReply) {
        setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart)
    } else {
        setOf(DismissDirection.EndToStart)
    }

    SwipeToDismiss(
        state = dismissState,
        directions = directions,
        background = {
            val deleting = dismissState.dismissDirection == DismissDirection.EndToStart
            val containerColor = if (deleting) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
            val contentColor = if (deleting) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(containerColor)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (deleting) Arrangement.End else Arrangement.Start,
            ) {
                if (deleting) {
                    Text(
                        uiText("삭제", "Delete"),
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        tint = contentColor,
                    )
                } else {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = contentColor,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        uiText("완료", "Handled"),
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        dismissContent = {
            Surface(color = MaterialTheme.colorScheme.background) {
                ConversationRow(
                    conversation = conversation,
                    availabilityVersion = availabilityVersion,
                    onClick = onClick,
                )
            }
        },
    )
}

@Composable
private fun ConversationRow(
    conversation: ConversationSummary,
    availabilityVersion: Long,
    onClick: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val app = appMeta(
        conversation.latestMessage.packageName,
        language,
        MaterialTheme.colorScheme.secondary,
    )
    val replyMessage = conversation.latestIncomingMessage
    val canSendNow = remember(conversation, availabilityVersion) {
        NotificationReplyStore.isAvailable(
            replyMessage.rawNotificationKey,
            replyMessage.packageName,
            replyMessage.sender,
            replyMessage.resolvedConversationId,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(54.dp)) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .align(Alignment.TopStart)
                    .background(app.color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    conversation.displayName.take(1).ifBlank { "?" },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            MessengerIcon(
                packageName = conversation.latestMessage.packageName,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd),
            )
            if (conversation.isUrgent) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.error, CircleShape),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.displayName,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatInboxTime(conversation.latestMessage.timestamp, language),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                buildString {
                    if (conversation.latestMessage.isOutgoing) {
                        append(language.text("나: ", "You: "))
                    }
                    append(conversation.latestMessage.translationFor(language))
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    buildString {
                        append(
                            if (conversation.channels.size > 1) {
                                language.text(
                                    "통합 · ${conversation.channels.size}개 앱",
                                    "Unified · ${englishCount(conversation.channels.size, "app")}",
                                )
                            } else {
                                app.name
                            },
                        )
                        append(
                            language.text(
                                " · ${conversation.messageCount}개",
                                " · ${englishCount(conversation.messageCount, "message")}",
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = app.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (conversation.isUrgent) {
                    Text(
                        uiText("긴급", "Urgent"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (conversation.needsReply) {
                    if (canSendNow) {
                        Icon(
                            Icons.Outlined.Reply,
                            contentDescription = uiText("바로 답장 가능", "Quick reply available"),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        if (canSendNow) {
                            uiText("바로 답장", "Quick reply")
                        } else {
                            uiText("답장 필요", "Needs reply")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationDetailScreen(
    conversation: ConversationSummary,
    canSendNow: Boolean,
    onBack: () -> Unit,
    onReply: () -> Unit,
    onManageContact: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val app = appMeta(
        conversation.id.packageName,
        language,
        MaterialTheme.colorScheme.secondary,
    )
    val listState = rememberLazyListState()

    LaunchedEffect(conversation.latestMessage.id) {
        if (conversation.messages.isNotEmpty()) {
            listState.scrollToItem(conversation.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = uiText("대화함으로 돌아가기", "Back to inbox"),
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MessengerIcon(
                            packageName = conversation.id.packageName,
                            modifier = Modifier.size(32.dp),
                        )
                        Column {
                            Text(
                                conversation.displayName,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (conversation.channels.size > 1) {
                                    language.text(
                                        "통합 연락처 · ${conversation.channels.size}개 앱 · " +
                                            "메시지 ${conversation.messageCount}개",
                                        "Unified contact · " +
                                            "${englishCount(conversation.channels.size, "app")} · " +
                                            englishCount(conversation.messageCount, "message"),
                                    )
                                } else {
                                    language.text(
                                        "${app.name} · 메시지 ${conversation.messageCount}개",
                                        "${app.name} · ${
                                            englishCount(conversation.messageCount, "message")
                                        }",
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onManageContact) {
                        Icon(
                            Icons.Outlined.Link,
                            contentDescription = uiText(
                                "연락처 통합 관리",
                                "Manage unified contact",
                            ),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (canSendNow) {
                                uiText("활성 알림으로 바로 전송", "Send directly via active notification")
                            } else {
                                uiText(
                                    "활성 알림 없음 · 복사 후 앱에서 전송",
                                    "No active notification · copy and send in the app",
                                )
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            uiText(
                                "최근 대화의 말투를 반영해 초안을 만듭니다",
                                "Drafts match the tone of the recent conversation",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(onClick = onReply) {
                        Icon(Icons.Outlined.Reply, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(uiText("답장", "Reply"))
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(conversation.messages, key = { _, message -> message.id }) { index, message ->
                val previous = conversation.messages.getOrNull(index - 1)
                if (
                    previous == null ||
                    formatDay(previous.timestamp, language) != formatDay(message.timestamp, language)
                ) {
                    DaySeparator(timestamp = message.timestamp)
                }
                MessageBubble(message = message)
            }
        }
    }
}

@Composable
private fun DaySeparator(timestamp: Long) {
    val language = LocalAppLanguage.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            formatDay(timestamp, language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageBubble(message: CapturedMessage) {
    val language = LocalAppLanguage.current
    val isOutgoing = message.isOutgoing
    val translatedText = message.translationFor(language)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 340.dp),
            color = if (isOutgoing) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = if (isOutgoing) {
                RoundedCornerShape(
                    topStart = 8.dp,
                    topEnd = 4.dp,
                    bottomEnd = 8.dp,
                    bottomStart = 8.dp,
                )
            } else {
                RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 8.dp,
                    bottomEnd = 8.dp,
                    bottomStart = 8.dp,
                )
            },
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (message.attachmentKind != null) {
                    AttachmentPreview(message = message)
                    Spacer(Modifier.height(8.dp))
                }
                Text(message.originalText, style = MaterialTheme.typography.bodyMedium)
                if (translatedText != message.originalText) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        uiText("번역", "Translation"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(translatedText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(
            modifier = Modifier.padding(
                start = if (isOutgoing) 0.dp else 4.dp,
                end = if (isOutgoing) 4.dp else 0.dp,
                top = 3.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MessengerIcon(
                packageName = message.packageName,
                modifier = Modifier.size(16.dp),
            )
            Text(
                appMeta(
                    message.packageName,
                    language,
                    MaterialTheme.colorScheme.secondary,
                ).name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isOutgoing) {
                Text(
                    uiText("나", "You"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                formatMessageTime(message.timestamp, language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (message.priority == "URGENT") {
                Text(
                    uiText("긴급", "Urgent"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AttachmentPreview(message: CapturedMessage) {
    val maxPreviewPixels = with(LocalDensity.current) {
        (260.dp / 0.75f).roundToPx()
    }
    val decodedBitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = message.attachmentPath,
        key2 = maxPreviewPixels,
    ) {
        value = message.attachmentPath?.let { path ->
            withContext(Dispatchers.IO) {
                decodeSampledBitmap(path, maxPreviewPixels)
            }
        }
    }
    val canShowImage = message.attachmentKind == AttachmentKinds.IMAGE && decodedBitmap != null

    if (canShowImage) {
        val bitmap = checkNotNull(decodedBitmap)
        val ratio = (bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
            .coerceIn(0.75f, 1.8f)
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = message.attachmentName ?: uiText("첨부 사진", "Attached image"),
            modifier = Modifier
                .width(260.dp)
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )
        return
    }

    Row(
        modifier = Modifier.widthIn(min = 180.dp, max = 280.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Outlined.AttachFile,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                message.attachmentName ?: uiText("첨부파일", "Attachment"),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (message.attachmentPath == null) {
                    uiText("미리보기를 가져올 수 없음", "Preview unavailable")
                } else {
                    message.attachmentMimeType ?: uiText("파일", "File")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotificationPermissionBanner(onOpenSettings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Notifications, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    uiText("알림 접근이 필요합니다", "Notification access required"),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    uiText(
                        "메신저 알림을 대화방별로 안전하게 모읍니다.",
                        "Collect messenger notifications safely by conversation.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onOpenSettings) { Text(uiText("설정", "Settings")) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSettingsDialog(
    state: AiSettingsState,
    appLanguage: AppLanguage,
    notificationAccess: Boolean,
    messageCount: Int,
    demoMode: Boolean,
    enabledCapturePackages: Set<String>,
    retentionDays: Int,
    onSaveAndTest: (String) -> Unit,
    onRemoveApiKey: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onResetDemo: () -> Unit,
    onDemoModeChange: (Boolean) -> Unit,
    onAppLanguageChange: (AppLanguage) -> Unit,
    onCaptureEnabledChange: (String, Boolean) -> Unit,
    onRetentionDaysChange: (Int) -> Unit,
    onClearMessages: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var apiKey by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var submittedKey by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var showCaptureApps by remember { mutableStateOf(false) }
    val installedCaptureApps = remember(context) {
        MessengerCatalog.apps.filter { app ->
            context.packageManager.getLaunchIntentForPackage(app.packageName) != null
        }
    }

    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    LaunchedEffect(state.isConfigured, state.isWorking) {
        if (submittedKey && state.isConfigured && !state.isWorking) {
            apiKey = ""
            submittedKey = false
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                @Suppress("DEPRECATION")
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("ReplyHub 설정", "ReplyHub settings")) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(uiText("앱 언어", "App language"), fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = appLanguage == AppLanguage.KOREAN,
                        onClick = { onAppLanguageChange(AppLanguage.KOREAN) },
                        modifier = Modifier.weight(1f),
                        label = { Text("한국어") },
                    )
                    FilterChip(
                        selected = appLanguage == AppLanguage.ENGLISH,
                        onClick = { onAppLanguageChange(AppLanguage.ENGLISH) },
                        modifier = Modifier.weight(1f),
                        label = { Text("English") },
                    )
                }
                Text(
                    uiText(
                        "선택한 언어는 UI, 메시지 번역, 답장 의미에 즉시 적용되며 이 기기에 저장됩니다.",
                        "Your choice is saved on this device and applied to the UI, message translations, and reply meanings.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Divider()
                Surface(
                    color = if (state.isConfigured) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            if (state.isConfigured) {
                                uiText("GPT-5.6 연결됨", "GPT-5.6 connected")
                            } else {
                                uiText("로컬 기본 답장 사용 중", "Using basic local replies")
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (state.isConfigured) {
                                uiText(
                                    "번역, 우선순위 분석, 맥락 답장에 내 API를 사용합니다.",
                                    "Your API powers translation, priority analysis, and contextual replies.",
                                )
                            } else {
                                uiText(
                                    "API 키를 연결하면 실제 GPT 답장을 생성합니다.",
                                    "Connect an API key to generate replies with GPT.",
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(uiText("OpenAI API 키", "OpenAI API key")) },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    enabled = !state.isWorking,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showApiKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showApiKey) {
                                    uiText("API 키 숨기기", "Hide API key")
                                } else {
                                    uiText("API 키 보기", "Show API key")
                                },
                            )
                        }
                    },
                )
                Text(
                    uiText(
                        "연결 확인 후 Android Keystore로 암호화해 이 기기에만 저장합니다.",
                        "After verification, the key is encrypted with Android Keystore and stored only on this device.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    uiText(
                        "ChatGPT 구독 로그인과 API 사용량은 별개이며, 이 앱은 사용자가 입력한 API 키만 사용합니다.",
                        "ChatGPT subscriptions and API usage are separate; this app only uses the API key you provide.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    uiText(
                        "키를 연결하면 번역과 답장에 필요한 메시지 내용이 OpenAI로 전송됩니다. " +
                            "API 요청에는 store:false를 사용하며 데이터 처리는 OpenAI 정책의 적용을 받습니다.",
                        "When connected, message content needed for translation and replies is sent to OpenAI. " +
                            "API requests use store:false, and OpenAI's data policies still apply.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = {
                        submittedKey = true
                        onSaveAndTest(apiKey)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isWorking && apiKey.length >= 20,
                ) {
                    if (state.isWorking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(uiText("연결 확인 중", "Checking connection"))
                    } else {
                        Text(
                            if (state.isConfigured) {
                                uiText("새 키로 교체", "Replace key")
                            } else {
                                uiText("연결하고 저장", "Connect and save")
                            },
                        )
                    }
                }

                if (state.isConfigured) {
                    OutlinedButton(
                        onClick = onRemoveApiKey,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isWorking,
                    ) {
                        Text(uiText("저장된 API 키 삭제", "Remove saved API key"))
                    }
                }

                Divider()
                OutlinedButton(
                    onClick = onOpenNotificationSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Notifications, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (notificationAccess) {
                            uiText("알림 접근 허용됨", "Notification access enabled")
                        } else {
                            uiText("알림 접근 허용하기", "Enable notification access")
                        },
                    )
                }

                if (installedCaptureApps.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showCaptureApps = !showCaptureApps },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (showCaptureApps) {
                                uiText("수집 앱 접기", "Hide capture apps")
                            } else {
                                appLanguage.text(
                                    "수집 앱 선택 · ${installedCaptureApps.size}개 설치됨",
                                    "Choose capture apps · ${installedCaptureApps.size} installed",
                                )
                            },
                        )
                    }
                    if (showCaptureApps) {
                        installedCaptureApps.forEach { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                MessengerIcon(
                                    packageName = app.packageName,
                                    modifier = Modifier.size(30.dp),
                                )
                                Text(
                                    if (appLanguage == AppLanguage.KOREAN) {
                                        app.displayName
                                    } else {
                                        app.englishDisplayName
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = app.packageName in enabledCapturePackages,
                                    onCheckedChange = {
                                        onCaptureEnabledChange(app.packageName, it)
                                    },
                                )
                            }
                        }
                    }
                }

                Divider()
                Text(uiText("데이터", "Data"), fontWeight = FontWeight.SemiBold)
                Text(
                    appLanguage.text(
                        "이 기기에 저장된 메시지 ${messageCount}개 · Android 백업 제외",
                        "${englishCount(messageCount, "message")} stored on this device · " +
                            "excluded from Android backup",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(uiText("자동 삭제", "Auto-delete"), fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(7, 30, 90, -1).forEach { days ->
                        FilterChip(
                            selected = retentionDays == days,
                            onClick = { onRetentionDaysChange(days) },
                            label = {
                                Text(
                                    if (days < 0) {
                                        uiText("삭제 안 함", "Keep forever")
                                    } else {
                                        appLanguage.text("${days}일", "$days days")
                                    },
                                )
                            },
                        )
                    }
                }
                OutlinedButton(
                    onClick = { showClearConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = messageCount > 0,
                ) {
                    Icon(
                        Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(uiText("모든 대화 삭제", "Delete all conversations"), color = MaterialTheme.colorScheme.error)
                }

                Divider()
                Text(uiText("데모", "Demo"), fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(uiText("데모 모드", "Demo mode"), fontWeight = FontWeight.Medium)
                        Text(
                            if (demoMode) {
                                uiText(
                                    "실시간 알림 수집 일시 중지",
                                    "Live notification capture paused",
                                )
                            } else {
                                uiText("실시간 알림 수집 중", "Capturing live notifications")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = demoMode,
                        onCheckedChange = onDemoModeChange,
                    )
                }
                Text(
                    uiText(
                        "중국어 웹 검색, 과거 주소 검색, 음성 답장 시나리오로 현재 대화를 초기화합니다.",
                        "Reset conversations with Chinese web search, address lookup, and voice reply scenarios.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onResetDemo,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(uiText("데모 모드로 초기화", "Reset demo mode"))
                }

                state.notice?.let { notice ->
                    Text(
                        notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(uiText("닫기", "Close")) } },
    )

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(uiText("모든 대화를 삭제할까요?", "Delete all conversations?")) },
            text = {
                Text(
                    uiText(
                        "저장된 메시지, 통합 연락처, 첨부파일이 이 기기에서 삭제됩니다.",
                        "Saved messages, unified contacts, and attachments will be deleted from this device.",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClearMessages()
                    },
                ) {
                    Text(uiText("삭제", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(uiText("취소", "Cancel"))
                }
            },
        )
    }
}

@Composable
private fun EmptyInbox(onSeedDemo: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Notifications,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(uiText("아직 대화가 없습니다", "No conversations yet"), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            uiText(
                "새 메신저 알림이 오면 사람별로 묶어 보여드립니다.",
                "New messenger notifications will be grouped by person.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = onSeedDemo) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(uiText("데모 대화 불러오기", "Load demo conversations"))
        }
    }
}

@Composable
private fun EmptySearchResult() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            uiText("조건에 맞는 대화가 없습니다", "No conversations match your filters"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplyDialog(viewModel: ReplyHubViewModel) {
    val language = LocalAppLanguage.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val state by viewModel.composer.collectAsStateWithLifecycle()
    val replyAvailabilityVersion by NotificationReplyStore.changes.collectAsStateWithLifecycle()
    val message = state.message ?: return
    val app = appMeta(message.packageName, language, MaterialTheme.colorScheme.secondary)
    val isDirectMessage = state.mode == ComposerMode.DIRECT_MESSAGE
    val canSendNow = remember(message, replyAvailabilityVersion) {
        NotificationReplyStore.isAvailable(
            message.rawNotificationKey,
            message.packageName,
            message.sender,
            message.resolvedConversationId,
        )
    }

    val voiceController = remember(language) {
        VoiceInputController(
            context = context,
            language = language,
            onListeningChanged = viewModel::setListening,
            onResult = viewModel::handleVoiceResult,
            onError = viewModel::setNotice,
        )
    }
    DisposableEffect(voiceController) {
        onDispose { voiceController.destroy() }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            voiceController.start()
        } else {
            viewModel.setNotice(
                language.text("마이크 권한이 필요합니다.", "Microphone permission is required."),
            )
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!state.isSending) viewModel.closeComposer()
        },
        title = {
            Text(
                if (isDirectMessage) {
                    language.text("메시지 · ${message.sender}", "Message · ${message.sender}")
                } else {
                    language.text("답장 · ${message.sender}", "Reply · ${message.sender}")
                },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ReplyContextPreview(message = message, app = app)
                if (!isDirectMessage) {
                    Text(uiText("답장 방식", "Reply mode"), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = state.replyMode == ReplyGenerationMode.CONTEXT,
                            onClick = { viewModel.setReplyMode(ReplyGenerationMode.CONTEXT) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isGenerating,
                            label = { Text(uiText("맥락 답장", "Context"), maxLines = 1) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                        FilterChip(
                            selected = state.replyMode == ReplyGenerationMode.WEB_SEARCH,
                            onClick = { viewModel.setReplyMode(ReplyGenerationMode.WEB_SEARCH) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isGenerating,
                            label = { Text(uiText("웹 검색", "Web search"), maxLines = 1) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                    Text(
                        if (state.replyMode == ReplyGenerationMode.WEB_SEARCH) {
                            uiText(
                                "최신 정보를 검색하고 출처가 있는 답장을 만듭니다.",
                                "Search current information and draft a reply with sources.",
                            )
                        } else {
                            uiText(
                                "대화 기록과 내가 보낸 답장을 바탕으로 작성합니다.",
                                "Draft from the conversation history and your previous replies.",
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = state.direction,
                    onValueChange = viewModel::updateDirection,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    enabled = !state.isGenerating,
                    label = {
                        Text(
                            when {
                                isDirectMessage -> uiText("보낼 메시지", "Message")
                                state.replyMode == ReplyGenerationMode.WEB_SEARCH ->
                                    uiText("무엇을 검색해 답할까요?", "What should I research?")
                                else -> uiText("어떻게 답할까요?", "How should I reply?")
                            },
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            enabled = !state.isGenerating,
                            onClick = {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO,
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    voiceController.start()
                                } else {
                                    microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        ) {
                            if (state.isListening) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Outlined.Mic,
                                    contentDescription = uiText(
                                        "음성으로 답장 지시",
                                        "Dictate reply instructions",
                                    ),
                                )
                            }
                        }
                    },
                )

                DeliveryStatusBand(
                    app = app,
                    canSendNow = canSendNow,
                )

                if (!isDirectMessage) state.draft?.let { draft ->
                    val meaningDraft = if (language == AppLanguage.KOREAN) {
                        draft.koreanDraft
                    } else {
                        draft.englishDraft
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            OutlinedTextField(
                                value = draft.recipientDraft,
                                onValueChange = viewModel::updateDraftText,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(uiText("보낼 답장", "Reply to send")) },
                                minLines = 2,
                            )
                            if (
                                !state.draftEdited && meaningDraft.isNotBlank() &&
                                meaningDraft.trim() != draft.recipientDraft.trim()
                            ) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    uiText("한국어 의미", "English meaning"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    meaningDraft,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (state.draftEdited) {
                                    uiText("직접 수정됨", "Edited manually")
                                } else {
                                    uiText("AI 판단", "AI reasoning")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (state.draftEdited) {
                                    uiText(
                                        "기존 번역과 근거를 숨겼습니다. 전송 기록의 번역은 다시 갱신됩니다.",
                                        "The previous meaning and evidence are hidden. The sent history will be translated again.",
                                    )
                                } else buildString {
                                    append(
                                        if (draft.tone == ReplyTone.POLITE) {
                                            language.text("존댓말", "Polite")
                                        } else {
                                            language.text("반말", "Casual")
                                        },
                                    )
                                    append(" · ")
                                    append(
                                        when (draft.tool) {
                                            ReplyTool.WEB_SEARCH ->
                                                language.text("웹 검색 근거", "Web search")
                                            ReplyTool.CONVERSATION_HISTORY ->
                                                language.text("대화 기록 근거", "Conversation history")
                                            ReplyTool.NONE ->
                                                language.text("현재 대화 맥락", "Current context")
                                        },
                                    )
                                    append(" · ${localizedDraftEngine(draft.engine, language)}")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (!state.draftEdited) draft.evidence?.let { evidence ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    language.text(
                                        "사용한 근거: $evidence",
                                        "Evidence used: $evidence",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (!state.draftEdited) draft.citations.forEach { citation ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    citation.title,
                                    modifier = Modifier.clickable {
                                        uriHandler.openUri(citation.url)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (
                                !state.draftEdited &&
                                state.replyMode == ReplyGenerationMode.WEB_SEARCH &&
                                draft.citations.isNotEmpty()
                            ) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setIncludeSources(!state.includeSources)
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = state.includeSources,
                                        onCheckedChange = viewModel::setIncludeSources,
                                    )
                                    Text(
                                        uiText(
                                            "출처 링크도 상대에게 보내기",
                                            "Send source links to the recipient",
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                    draft.warning?.let { warning ->
                        Text(
                            localizedDraftWarning(warning, language),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = viewModel::generateDraft,
                            enabled = !state.isGenerating,
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text(uiText("다시 만들기", "Regenerate"))
                        }
                    }
                }

                state.notice?.let {
                    Surface(
                        color = if (state.noticeIsError) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            it,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            color = if (state.noticeIsError) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state.pendingExternalSend != null) {
                Button(onClick = viewModel::confirmExternalSend) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(uiText("보냄으로 표시", "Mark as sent"))
                }
            } else if (isDirectMessage) {
                Button(
                    onClick = viewModel::sendDirectMessage,
                    enabled = state.direction.isNotBlank() && !state.isSending,
                ) {
                    if (state.isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Send, contentDescription = null)
                    }
                    Spacer(Modifier.size(6.dp))
                    Text(
                        if (state.isSending) {
                            uiText("전송 연결 확인 중", "Checking send connection")
                        } else if (canSendNow) {
                            uiText("바로 전송", "Send now")
                        } else {
                            uiText("복사 후 열기", "Copy and open")
                        },
                    )
                }
            } else if (state.draft == null) {
                Button(onClick = viewModel::generateDraft, enabled = !state.isGenerating) {
                    if (state.isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    }
                    Spacer(Modifier.size(6.dp))
                    Text(uiText("초안 만들기", "Create draft"))
                }
            } else {
                Button(
                    onClick = viewModel::sendDraft,
                    enabled = state.draft?.recipientDraft?.isNotBlank() == true &&
                        !state.isSending,
                ) {
                    if (state.isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Send, contentDescription = null)
                    }
                    Spacer(Modifier.size(6.dp))
                    Text(
                        if (state.isSending) {
                            uiText("전송 연결 확인 중", "Checking send connection")
                        } else if (canSendNow) {
                            uiText("바로 전송", "Send now")
                        } else {
                            uiText("복사 후 열기", "Copy and open")
                        },
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (state.pendingExternalSend != null) {
                        viewModel.cancelExternalSendConfirmation()
                    } else {
                        viewModel.closeComposer()
                    }
                },
                enabled = !state.isSending,
            ) {
                Text(
                    if (state.pendingExternalSend != null) {
                        uiText("아직 보내지 않음", "Not sent yet")
                    } else {
                        uiText("닫기", "Close")
                    },
                )
            }
        },
    )
}

@Composable
private fun ReplyContextPreview(
    message: CapturedMessage,
    app: MessengerMeta,
) {
    val language = LocalAppLanguage.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MessengerIcon(
                packageName = message.packageName,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = app.color,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    message.translationFor(language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DeliveryStatusBand(
    app: MessengerMeta,
    canSendNow: Boolean,
) {
    Surface(
        color = if (canSendNow) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (canSendNow) Icons.Outlined.Send else Icons.Outlined.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (canSendNow) {
                        uiText("바로 전송 가능", "Direct send available")
                    } else {
                        uiText(
                            "복사 후 ${app.name}에서 전송",
                            "Copy and send in ${app.name}",
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (canSendNow) {
                        uiText(
                            "활성 알림의 답장 기능을 사용합니다.",
                            "Uses the reply action from the active notification.",
                        )
                    } else {
                        uiText(
                            "답장 권한이 없어 자동 전송하지 않습니다.",
                            "Automatic sending is unavailable without reply permission.",
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private data class MessengerMeta(
    val name: String,
    val color: Color,
    val fallbackMark: String,
)

@Composable
private fun MessengerIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    appName: String? = null,
) {
    val context = LocalContext.current
    val app = appMeta(
        packageName,
        LocalAppLanguage.current,
        MaterialTheme.colorScheme.secondary,
    )
    val bitmap by produceState<ImageBitmap?>(
        initialValue = MessengerIconBitmapCache.get(packageName),
        key1 = packageName,
        key2 = context,
    ) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                MessengerIconBitmapCache.get(packageName) ?: runCatching {
                    context.packageManager
                        .getApplicationIcon(packageName)
                        .toBitmap(width = 96, height = 96)
                        .asImageBitmap()
                        .also { MessengerIconBitmapCache.put(packageName, it) }
                }.getOrNull()
            }
        }
    }
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, shape)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        val loadedBitmap = bitmap
        if (loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap,
                contentDescription = uiText(
                    "${appName ?: app.name} 아이콘",
                    "${appName ?: app.name} icon",
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(app.color, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    app.fallbackMark,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun hasNotificationAccess(packageName: String, context: android.content.Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName)

private fun formatInboxTime(timestamp: Long, language: AppLanguage): String =
    SimpleDateFormat(
        if (language == AppLanguage.KOREAN) "M/d HH:mm" else "M/d h:mm a",
        language.locale,
    ).format(Date(timestamp))

private fun formatMessageTime(timestamp: Long, language: AppLanguage): String =
    SimpleDateFormat(
        if (language == AppLanguage.KOREAN) "a h:mm" else "h:mm a",
        language.locale,
    ).format(Date(timestamp))

private fun formatDay(timestamp: Long, language: AppLanguage): String =
    SimpleDateFormat(
        if (language == AppLanguage.KOREAN) "M월 d일 EEEE" else "EEEE, MMM d",
        language.locale,
    ).format(Date(timestamp))

private fun appMeta(
    packageName: String,
    language: AppLanguage,
    fallbackColor: Color,
): MessengerMeta {
    val definition = MessengerCatalog.find(packageName)
        ?: return MessengerMeta(
            language.text("메시지", "Messages"),
            fallbackColor,
            "M",
        )
    return MessengerMeta(
        name = if (language == AppLanguage.KOREAN) {
            definition.displayName
        } else {
            definition.englishDisplayName
        },
        color = Color(definition.color),
        fallbackMark = definition.fallbackMark,
    )
}

private fun localizedDraftEngine(engine: String, language: AppLanguage): String = when (engine) {
    "로컬 기본 답장", "로컬 데모", "로컬 폴백" ->
        language.text("로컬 기본 답장", "Basic local replies")
    "GPT-5.6 · 데모 캐시" -> language.text("GPT-5.6 · 데모 캐시", "GPT-5.6 · demo cache")
    else -> engine
}

private fun localizedDraftWarning(warning: String, language: AppLanguage): String {
    if (language == AppLanguage.KOREAN) return warning
    return when {
        warning == "웹 검색을 사용할 수 없어 로컬 기본 답장을 만들었습니다." ->
            "Web search was unavailable, so a basic local reply was created."
        warning == "API 키가 없어 로컬 기본 답장을 만들었습니다." ->
            "No API key is connected, so a basic local reply was created."
        warning.startsWith("API 키를 사용할 수 없어 이전에 검증한 데모 결과") ->
            "The API key was unavailable, so a previously verified demo result is shown."
        warning.startsWith("연결이 불안정해 이전에 검증한 데모 결과") ->
            "The connection was unstable, so a previously verified demo result is shown."
        warning.startsWith("검색은 실행됐지만 표시할 출처를 찾지 못했습니다.") ->
            "Search ran but no sources were available to show. Review the reply before sending."
        warning.startsWith("GPT 요청에 실패해 로컬 기본 답장을 만들었습니다:") ->
            "The GPT request failed, so a basic local reply was created."
        else -> warning
    }
}

private fun englishCount(
    count: Int,
    singular: String,
    plural: String = "${singular}s",
): String = "$count ${if (count == 1) singular else plural}"

private fun englishNeedsReply(count: Int): String =
    if (count == 1) "1 needs reply" else "$count need replies"

private suspend fun loadLaunchableApps(context: android.content.Context): List<InstalledApp> =
    LaunchableAppsCache.load(context.applicationContext)

private fun queryLaunchableApps(context: android.content.Context): List<InstalledApp> {
    val packageManager = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.ResolveInfoFlags.of(0),
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(launcherIntent, 0)
    }
    return activities
        .map { resolveInfo ->
            InstalledApp(
                label = resolveInfo.loadLabel(packageManager).toString()
                    .ifBlank { resolveInfo.activityInfo.packageName },
                packageName = resolveInfo.activityInfo.packageName,
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}

private object LaunchableAppsCache {
    private const val CACHE_MILLIS = 30_000L
    private val lock = Any()
    private var loadedAt = 0L
    private var apps: List<InstalledApp> = emptyList()

    suspend fun load(context: android.content.Context): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val now = System.currentTimeMillis()
                if (loadedAt > 0L && now - loadedAt < CACHE_MILLIS) {
                    return@synchronized apps
                }
                queryLaunchableApps(context).also { loaded ->
                    apps = loaded
                    loadedAt = now
                }
            }
        }
}

private object MessengerIconBitmapCache {
    private val cache = LruCache<String, ImageBitmap>(32)

    fun get(packageName: String): ImageBitmap? = synchronized(cache) {
        cache.get(packageName)
    }

    fun put(packageName: String, bitmap: ImageBitmap) = synchronized(cache) {
        cache.put(packageName, bitmap)
    }
}

private fun decodeSampledBitmap(path: String, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val target = maxDimension.coerceAtLeast(1)
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= target) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    )
}
