package com.replyhub.app.notifications

internal object AutoSendHeuristics {
    private val sendLabels = setOf(
        "send", "전송", "보내기", "발송", "송신",
        "发送", "發送", "傳送", "送出", "送信",
        "senden", "envoyer", "enviar", "invia", "wyślij",
        "gönder", "gửi", "kirim", "отправить", "надіслати",
    )
    private val sendPhrases = setOf(
        "send message", "send a message", "send now", "send button",
        "메시지 보내기", "메세지 보내기", "메시지 전송", "메시지 발송",
        "发送消息", "メッセージを送信",
    )
    private val blockedLabelFragments = listOf(
        "resend", "re-send", "unsend", "재전송", "다시", "취소",
        "search", "검색", "송금", "예약",
    )
    private val blockedIdFragments = listOf(
        "resend", "unsend", "sender", "search", "friend", "money",
    )

    fun isSendLabel(raw: CharSequence?): Boolean {
        val value = raw?.toString()?.trim()?.lowercase() ?: return false
        if (value.isEmpty() || value.length > MAX_LABEL_LENGTH) return false
        if (blockedLabelFragments.any { value.contains(it) }) return false
        return value in sendLabels || value in sendPhrases
    }

    fun isSendViewId(viewIdResourceName: String?): Boolean {
        val id = viewIdResourceName?.substringAfterLast('/')?.lowercase() ?: return false
        if (id.isEmpty()) return false
        if (blockedIdFragments.any { id.contains(it) }) return false
        if (!id.contains("send")) return false
        return id == "send" ||
            id.contains("btn") ||
            id.contains("button") ||
            id.startsWith("send_") ||
            id.endsWith("_send")
    }

    fun isEditableClassName(className: CharSequence?): Boolean =
        className?.toString()?.contains("EditText", ignoreCase = true) == true

    private const val MAX_LABEL_LENGTH = 24
}
