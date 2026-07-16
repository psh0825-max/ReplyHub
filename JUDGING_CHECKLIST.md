# OpenAI Build Week Judging Checklist

## Technological Implementation

- [x] GPT-5.6 strict structured translation and priority output
- [x] GPT-5.6 context-aware recipient-language reply generation
- [x] Required web search mode with parsed citations
- [x] Local conversation-history retrieval including outgoing replies
- [x] Cross-messenger history retrieval for linked contacts
- [x] Stable room identity and ambiguity-safe RemoteInput routing
- [x] Android NotificationListener and RemoteInput on a physical device
- [x] Korean voice input
- [x] Encrypted BYOK configuration
- [x] Unit tests, lint, clean APK build
- [x] Public GitHub Actions test, lint, and APK build workflow
- [ ] Record one uninterrupted real-notification-to-send take

## Design

- [x] Person-first conversation inbox
- [x] AI reply-needed, urgent, handled, and foreign-language triage
- [x] Original and translated message timeline
- [x] Editable draft and Korean meaning
- [x] Visible tone, evidence, tool, and citations
- [x] Unified contacts across messenger channels
- [x] Installed-app search and launch
- [x] Explicit direct-send versus copy-and-open status
- [x] Copy-and-open sent confirmation preserves outgoing context
- [x] Per-app capture and configurable retention controls
- [x] Capture final light-theme screenshots without keyboard or private data

## Potential Impact

- [x] Specific audience: multilingual multi-messenger Android users
- [x] Concrete cost: delayed replies, translation errors, lost context
- [x] Privacy and graceful-degradation story documented
- [x] Add one quantified usability observation after a physical-device test

## Quality of the Idea

- [x] AI acts inside the communication workflow, not as a separate chatbot
- [x] Search results and sources can be shared with the recipient
- [x] Same person can be unified across apps
- [x] Honest Android capability boundaries

## Submission Package

- [x] `README.md`
- [x] `SUBMISSION.md`
- [x] `DEMO_SCRIPT_KO.md`
- [x] `PRIVACY.md`
- [x] `USABILITY_NOTES.md`
- [x] Environment-based release signing configuration
- [x] Debug APK artifact
- [x] Public repository URL: https://github.com/psh0825-max/ReplyHub
- [ ] Demo video URL
- [ ] Devpost project images
- [x] Codex task reference: `019f688b-b60c-75e0-bfa6-4e5363875b30`
- [ ] Replace the remaining demo-video `ADD_URL` placeholder before submission
