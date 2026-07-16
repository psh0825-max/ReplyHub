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

## OpenAI processing

- Without an API key, ReplyHub uses its deterministic local fallback and does not send message text to OpenAI.
- When the user connects an API key, relevant message text and limited conversation context are sent to the OpenAI Responses API for translation, classification, reply drafting, or web search.
- Requests set `store: false`. This setting does not replace OpenAI's published API data controls and retention policy.
- The API key is encrypted with Android Keystore AES/GCM and never written to logs.
- The key-entry screen uses secure-window protection and clears the clipboard after a successful save.

## App visibility

Installed-app search uses an Android launcher intent query. ReplyHub does not request the restricted `QUERY_ALL_PACKAGES` permission. Hidden services, disabled apps, and apps isolated in another profile are not enumerated.

## Sending and automation

ReplyHub sends directly only through a live notification RemoteInput action supplied by the messenger. Exact notification keys are preferred; a fallback target is used only when one active conversation matches. If no unambiguous action is available, ReplyHub marks the copied text as sensitive, opens the app, and asks the user to confirm sending before recording the outgoing message. ReplyHub does not use AccessibilityService to click, scrape, or impersonate another app's interface.

## Production recommendations

- Use a rate-limited backend proxy for any sponsor-provided API credit; never ship a shared key in the APK.
- Complete messenger-by-messenger notification compatibility tests and document partial support.
- Review the public privacy policy with counsel before a commercial release.
