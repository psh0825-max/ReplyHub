package com.replyhub.app.domain

class ContextualReplyComposer {
    fun compose(
        latestMessage: String,
        recentMessages: List<String>,
        userDirection: String,
        tone: ReplyTone,
    ): String {
        val directedTone = when {
            userDirection.contains("존댓말") || userDirection.contains("정중") -> ReplyTone.POLITE
            userDirection.contains("반말") || userDirection.contains("편하게") -> ReplyTone.CASUAL
            else -> tone
        }
        val isPolite = directedTone == ReplyTone.POLITE

        if (userDirection.isNotBlank()) {
            when {
                listOf("거절", "어렵", "불가").any { userDirection.contains(it) } -> {
                    return if (isPolite) {
                        "말씀은 감사하지만 이번에는 어려울 것 같습니다."
                    } else {
                        "말해줘서 고마운데 이번에는 어려울 것 같아."
                    }
                }

                listOf("수락", "긍정", "좋다고", "가능하다고").any { userDirection.contains(it) } -> {
                    return if (isPolite) {
                        "네, 좋습니다. 말씀하신 대로 진행하겠습니다."
                    } else {
                        "좋아. 말한 대로 진행하자."
                    }
                }

                listOf("해줘", "말투", "정중", "짧게", "길게", "답변")
                    .none { userDirection.contains(it) } -> return userDirection
            }
        }

        val latest = latestMessage.trim()
        val topicText = if (latest.length <= SHORT_MESSAGE_LENGTH) {
            (listOf(latest) + recentMessages.take(4)).joinToString(" ")
        } else {
            latest
        }

        return when {
            topicText.containsAny("마감", "기한", "까지 제출", "deadline") -> {
                if (isPolite) {
                    "네, 알려주셔서 감사합니다. 마감 전에 확인하겠습니다."
                } else {
                    "알려줘서 고마워. 마감 전에 확인할게."
                }
            }

            topicText.containsAny("단가", "가격", "견적", "비용", "의견") -> {
                if (isPolite) {
                    "네, 말씀하신 내용 검토해 보고 제 의견을 드리겠습니다."
                } else {
                    "응, 말한 내용 검토해 보고 의견 줄게."
                }
            }

            topicText.containsAny("체력", "무리", "휴식", "쉬어", "피곤") -> {
                if (isPolite) {
                    "맞아요. 무리하지 말고 체력 안배하면서 하시죠."
                } else {
                    "맞아. 무리하지 말고 체력 안배하면서 하자."
                }
            }

            topicText.containsAny("더워", "덥다", "더위", "추워", "춥다", "날씨") -> {
                if (isPolite) {
                    "그러게요. 오늘 날씨가 많이 힘드네요. 건강 조심하세요."
                } else {
                    "그러게. 오늘 날씨 진짜 힘드네. 건강 조심해."
                }
            }

            topicText.containsAny("주소", "장소", "어디", "위치") -> {
                val knownAddress = recentMessages.firstOrNull { candidate ->
                    candidate.containsAny("주소는", "도로", "대로", "길 ") &&
                        candidate.any(Char::isDigit)
                }
                if (knownAddress != null) return knownAddress
                if (isPolite) {
                    "장소를 확인한 뒤 정확히 말씀드리겠습니다."
                } else {
                    "장소 확인하고 정확히 알려줄게."
                }
            }

            topicText.containsAny("몇 시", "시간", "언제", "일정", "날짜") -> {
                if (isPolite) {
                    "말씀하신 일정을 확인해 보고 바로 답변드리겠습니다."
                } else {
                    "말한 일정 확인해 보고 바로 답할게."
                }
            }

            latest.endsWith("?") || latest.endsWith("？") ||
                latest.containsAny("어때", "할까요", "인가요", "맞나요") -> {
                if (isPolite) {
                    "질문하신 내용 확인해서 정확히 답변드리겠습니다."
                } else {
                    "물어본 내용 확인해서 정확히 답할게."
                }
            }

            latest.containsAny("고마워", "감사") -> {
                if (isPolite) "별말씀을요. 도움이 되어 다행입니다." else "별말을. 도움이 됐다니 다행이야."
            }

            else -> {
                if (isPolite) {
                    "네, 말씀하신 내용 이해했습니다. 그 부분을 고려해서 진행하겠습니다."
                } else {
                    "응, 말한 내용 이해했어. 그 부분 생각해서 진행할게."
                }
            }
        }
    }

    private fun String.containsAny(vararg candidates: String): Boolean =
        candidates.any { contains(it, ignoreCase = true) }

    companion object {
        private const val SHORT_MESSAGE_LENGTH = 8
    }
}
