package com.replyhub.app.domain

import com.replyhub.app.data.CapturedMessage

class ConversationToneResolver(
    private val detector: KoreanSpeechLevelDetector = KoreanSpeechLevelDetector(),
) {
    fun resolve(messages: List<CapturedMessage>): ReplyTone {
        val newestFirst = messages.sortedByDescending { it.timestamp }
        val myPreviousReplies = newestFirst.filter { it.isOutgoing }
        val toneContext = myPreviousReplies.ifEmpty { newestFirst.filterNot { it.isOutgoing } }
        return detector.detect(toneContext.map { it.originalText })
    }
}
