package com.replyhub.app.domain

enum class ReplyTone {
    POLITE,
    CASUAL,
}

class KoreanSpeechLevelDetector {
    fun detect(messages: List<String>): ReplyTone {
        var politeScore = 0
        var casualScore = 0

        messages.take(MAX_CONTEXT_MESSAGES).forEachIndexed { index, message ->
            val weight = (5 - index / 4).coerceAtLeast(1)
            message.split(SENTENCE_BREAKS).forEach { sentence ->
                val normalized = sentence.trim().replace(TRAILING_MARKS, "")
                when {
                    normalized.isBlank() -> Unit
                    POLITE_ENDINGS.containsMatchIn(normalized) -> politeScore += weight
                    CASUAL_STANDALONE.matches(normalized) ||
                        CASUAL_ENDINGS.containsMatchIn(normalized) -> casualScore += weight
                }
            }
        }

        return if (politeScore >= casualScore) ReplyTone.POLITE else ReplyTone.CASUAL
    }

    companion object {
        private const val MAX_CONTEXT_MESSAGES = 20
        private val SENTENCE_BREAKS = Regex("[\\n\\r]+")
        private val TRAILING_MARKS = Regex("[\\s.!?~…]+$")
        private val POLITE_ENDINGS = Regex(
            "(요|습니다|습니까|세요|십시오|드립니다|합니다|이에요|예요|죠|네요|군요|까요|겠어요|랍니다)$",
        )
        private val CASUAL_STANDALONE = Regex("^(응|어|그래|아니|ㅇㅇ|ㄴㄴ|ㅋ+|ㅎ+)$")
        private val CASUAL_ENDINGS = Regex(
            "(야|해|해줘|할게|갈게|볼게|줄게|올게|알았어|했어|맞아|좋아|괜찮아|뭐해|인데|큰데|거든|잖아|라고|냐|니|자|지|네|다)$",
        )
    }
}
