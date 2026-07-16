# ReplyHub Usability Test Notes

## Test setup

- One iterative test session on a physical Samsung Android phone
- Eight realistic demo messages across six conversations and five messengers
- Scenarios covered inbox scanning, context-aware drafting, web search, direct reply, attachment context, and cross-app contact linking

## Observations and changes

1. A notification-by-notification feed became hard to scan as senders increased.
   ReplyHub now groups messages by person or room and adds an AI inbox with reply-needed, urgent, handled, and foreign-language counts.

2. Incoming messages alone were not enough to understand a conversation.
   Outgoing replies are now stored and displayed in the same chronological timeline, including the sending messenger and a visible `나` marker.

3. A copied reply followed by an app launch looked like a failed send.
   ReplyHub now distinguishes live RemoteInput sending from copy-and-open fallback before the user acts. After returning from the messenger, the user confirms whether it was sent so the outgoing context remains complete. A physical-device test later confirmed that the direct-send path delivered the reply.

4. A generic draft that introduced place and time details felt unrelated to the message.
   Drafting is now split into context reply and GPT web-search reply. The model receives recent incoming and outgoing turns, locally retrieved history, relationship tone, and current-message context.

5. The same tone did not fit both casual and formal conversations.
   Draft output now exposes the selected tone and preserves polite or casual speech based on the conversation.

6. Messenger identity, photos, files, and the originating room were necessary for trust.
   Each message shows its messenger icon and source label, supported notification attachments are retained locally, and a stable conversation ID separates the room from the individual sender. Ambiguous aliases can no longer select a reply target.

7. The same person appearing in multiple apps still fragmented the story.
   Users can now link conversations into one contact while every message keeps its original messenger attribution. AI retrieval also spans every linked channel.

8. Live personal notifications could enter the inbox immediately after resetting a judging scenario.
   Demo mode now pauses notification capture, clears live reply targets, and protects against in-flight processing until the user explicitly resumes live collection.

## Result

Eight concrete usability issues found during iterative device sessions were incorporated into the submitted build. The strongest validated flow was notification capture to context-aware draft to live RemoteInput delivery on the connected phone.
