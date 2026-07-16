# ReplyHub

ReplyHub is an Android notification triage hub for major consumer and workplace messengers. It captures message notifications by conversation, presents original and translated text in one inbox, drafts context-aware replies, accepts voice input, and sends through Android RemoteInput when available. Unsupported delivery paths fall back to sensitive clipboard copy, app launch, and explicit sent-history confirmation.

## Current hackathon build

- NotificationListenerService captures supported messenger notifications and logs only non-content diagnostics such as package, length, and RemoteInput availability.
- Room stores the original message immediately, then updates translation and priority asynchronously so slow AI calls never hide a new notification.
- Compose provides a searchable unified feed with urgent/later filters. The same search field finds installed launchable apps and opens them directly from ReplyHub.
- Messenger filters place apps with captured conversations first and mark them with persistent brand-color blocks and a connected-notification indicator.
- The AI inbox distinguishes reply-needed, urgent, handled, and foreign-language conversations at a glance.
- Demo mode pauses live notification capture so personal notifications cannot contaminate judging scenarios.
- Users can link the same person across messenger channels into one chronological timeline and unlink them at any time.
- The messenger catalog covers 25 major apps, including KakaoTalk, WeChat, LINE, Telegram, Slack, WhatsApp, Messenger, Instagram, Discord, Teams, Google Chat, Google Messages, Signal, Viber, Skype, Zalo, and BAND.
- Users can connect their own OpenAI API key in ReplyHub settings. The key is connection-tested, encrypted with Android Keystore, and never displayed again after saving.
- The Responses API performs fast translation and priority analysis with `gpt-5.6-luna`, then drafts replies with `gpt-5.6` using strict Structured Outputs.
- **맥락 답장** never accesses the web. **웹 검색** always runs OpenAI web search, displays clickable citations, and can append up to two source links to the sent message.
- Local keyword retrieval spans every channel linked to the same contact, so older details such as addresses, times, and prices can be reused across apps.
- Basic deterministic local replies remain available when no API key is configured or a network request fails; the saved-address demo remains functional without the network.
- Successful GPT results for synthetic demo messages can be cached on-device for recording resilience. A cached result is labeled as such and web-search results are cached only when citations were returned.
- SpeechRecognizer converts Korean speech into reply instructions.
- RemoteInput routes by exact notification key or one unambiguous conversation ID. Clipboard + launch intent handles unsupported actions and asks the user to confirm whether the reply was sent before adding it to history.
- Users can disable capture per installed messenger and choose 7, 30, 90 days, or no automatic deletion. The default is 30 days.

## Run

1. Open this folder in Android Studio.
2. Select an Android 8.0+ device.
3. Run the `app` configuration.
4. In ReplyHub, open notification access settings and enable ReplyHub.
5. Open ReplyHub settings, enter an OpenAI API key, and tap **연결하고 저장**. This is optional; the local engine works without a key.
6. Send test messages from a supported catalog app and inspect non-content diagnostics in Logcat with tag `ReplyHubNotification`.
7. Use **데모 모드로 초기화** to pause live capture and rehearse the submission scenarios without external APIs.

## Privacy

Captured messages are stored in the app's sandboxed local Room database, excluded from Android backup, and automatically deleted after 30 days by default. Without an API key, ReplyHub uses basic processing on the device. When a user connects a key, relevant message text and limited linked-conversation context are sent directly from the device to the OpenAI Responses API with `store: false`; OpenAI's published data controls still apply. The API key is encrypted by Android Keystore and is not written to logs. Cleartext network traffic is disabled, persistent notification identities prevent duplicate AI calls after restart, copied replies are marked sensitive, and copied attachments are capped at 20 MB. Installed-app search uses the standard launcher intent query instead of the restricted `QUERY_ALL_PACKAGES` permission. For a public production release, use a rate-limited server-side proxy instead of providing a shared key in the APK.

## Platform limitations

- Direct sending uses a messenger notification's live Android `RemoteInput` action. If the notification was opened, dismissed, or does not provide a reply action, ReplyHub copies the answer and opens the messenger instead.
- The new-message picker contains people observed in ReplyHub notification history, not the messenger's private contact database. Personal contact lists are not exposed by a common Android API.
- ReplyHub does not automate messenger interfaces with AccessibilityService. This keeps the hackathon build safer and less brittle, but means it cannot guarantee background sending for every app and old conversation.
- Installed-app search lists apps that expose a launcher activity for the current Android profile. Hidden services, disabled packages, and apps isolated in another profile are intentionally not enumerated.

## Architecture

```text
Messenger notification
  -> ReplyHubNotificationListener
  -> Room / MessageRepository (immediate original)
  -> MessageProcessor (asynchronous enrichment)
  -> Compose unified feed
  -> OpenAI Responses API (when configured) or local fallback
  -> ReplyAssistant
  -> RemoteInput or clipboard + app launch
```

## Demo scenarios

1. WeChat Chinese business-hours question uses **웹 검색**, shows citations, and produces a Chinese reply.
2. KakaoTalk address question uses **맥락 답장**, searches local conversation history, and reuses the saved address.
3. The microphone captures Korean instructions, drafts the recipient-language response, and dispatches through the available delivery path.

Submission-ready copy, the recording runbook, privacy explanation, and device-test notes are in [SUBMISSION.md](./SUBMISSION.md), [DEMO_SCRIPT_KO.md](./DEMO_SCRIPT_KO.md), [PRIVACY.md](./PRIVACY.md), and [USABILITY_NOTES.md](./USABILITY_NOTES.md).
Release signing uses environment variables documented in [RELEASE.md](./RELEASE.md); no signing secret is stored in the repository.
