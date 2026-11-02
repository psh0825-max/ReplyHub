# ReplyHub Privacy and Safety

## Data handled

ReplyHub can read notifications only after the user explicitly grants Android notification-listener access. For supported messenger notifications it may process the sender label, message text, timestamp, notification reply action, and an attachment exposed by the notification.

## Local storage

- Messages and contact links are stored in the app's private local storage.
- Android backup is disabled for the application.
- Copied notification attachments are limited to 20 MB.
- Clearing ReplyHub conversations also clears copied attachments.
- Messages are automatically removed after 30 days by default. Users can choose 7, 30, 90 days, or no automatic deletion.
- Users can disable notification capture separately for each installed supported messenger.
- Notification logs contain package names, character counts, and action availability, not message or sender content.
- For auto-send, ReplyHub remembers only which messenger versions hide their screens from accessibility services (package name and version number).

## OpenAI processing

- Without an API key, ReplyHub uses its deterministic local fallback and does not send message text to OpenAI.
- When the user connects an API key, relevant message text and limited conversation context are sent to the OpenAI Responses API for translation, classification, reply drafting, or web search.
- Requests set `store: false`. This setting does not replace OpenAI's published API data controls and retention policy.
- The API key is encrypted with Android Keystore AES/GCM and never written to logs.
- The key-entry screen uses secure-window protection and clears the clipboard after a successful save.

## App visibility

Installed-app search uses an Android launcher intent query. ReplyHub does not request the restricted `QUERY_ALL_PACKAGES` permission. Hidden services, disabled apps, and apps isolated in another profile are not enumerated.

## Sending and automation

ReplyHub sends directly through a live notification RemoteInput action supplied by the messenger. Exact notification keys are preferred, but a key is used only while it still describes the same conversation or sender, because some messengers reuse one notification for several chats. Otherwise a fallback target is used only when exactly one active conversation matches.

For messengers whose notifications expose no RemoteInput action (for example WeChat), users can turn on the optional **ReplyHub auto-send** accessibility service. It is off until the user enables it in Android accessibility settings, and it acts only for a few seconds after the user presses send in ReplyHub. During that window it opens the conversation through the original notification, reads the messenger window's interface structure to find the message field and the send button on the same row, types the reply, taps send, and checks that the field was cleared before recording the reply as sent. Users choose which messengers it may act in; feed-style apps such as Instagram, X, LinkedIn, TikTok, Snapchat, and BAND are off by default because their like and mention notifications could put a reply into a public comment field. The service receives events only from the allowed messenger packages, ignores them unless a send is in progress, and does not store, log, or transmit screen content; its diagnostic logs contain only step names and node counts. ReplyHub shows what the service reads and does, and asks for agreement, before opening Android accessibility settings.

If neither path is available or auto-send cannot finish, ReplyHub marks the copied text as sensitive, opens the app, and asks the user to confirm sending before recording the outgoing message.

## Production recommendations

- Use a rate-limited backend proxy for any sponsor-provided API credit; never ship a shared key in the APK.
- Complete messenger-by-messenger notification compatibility tests and document partial support.
- Review the public privacy policy with counsel before a commercial release.
