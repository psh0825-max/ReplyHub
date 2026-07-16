package com.replyhub.app.messaging

data class MessengerDefinition(
    val packageName: String,
    val displayName: String,
    val color: Long,
    val fallbackMark: String,
    val englishDisplayName: String = displayName,
)

object MessengerCatalog {
    val apps = listOf(
        MessengerDefinition("com.kakao.talk", "카카오톡", 0xFF6B5B00, "K", "KakaoTalk"),
        MessengerDefinition("com.tencent.mm", "위챗", 0xFF178A45, "W", "WeChat"),
        MessengerDefinition("jp.naver.line.android", "라인", 0xFF009B43, "L", "LINE"),
        MessengerDefinition("org.telegram.messenger", "텔레그램", 0xFF2476A8, "T", "Telegram"),
        MessengerDefinition("com.Slack", "Slack", 0xFF4A154B, "S"),
        MessengerDefinition("com.whatsapp", "WhatsApp", 0xFF128C5E, "W"),
        MessengerDefinition("com.whatsapp.w4b", "WhatsApp Business", 0xFF075E54, "W"),
        MessengerDefinition("com.facebook.orca", "Messenger", 0xFF0866FF, "M"),
        MessengerDefinition("com.instagram.android", "Instagram", 0xFFC13584, "I"),
        MessengerDefinition("com.discord", "Discord", 0xFF5865F2, "D"),
        MessengerDefinition("com.microsoft.teams", "Microsoft Teams", 0xFF6264A7, "T"),
        MessengerDefinition("com.google.android.apps.dynamite", "Google Chat", 0xFF0F9D58, "G"),
        MessengerDefinition("com.google.android.apps.messaging", "Google Messages", 0xFF1A73E8, "G"),
        MessengerDefinition("com.samsung.android.messaging", "Samsung Messages", 0xFF2F6FED, "S"),
        MessengerDefinition("org.thoughtcrime.securesms", "Signal", 0xFF3A76F0, "S"),
        MessengerDefinition("com.viber.voip", "Viber", 0xFF7360F2, "V"),
        MessengerDefinition("com.skype.raider", "Skype", 0xFF0078D4, "S"),
        MessengerDefinition("com.snapchat.android", "Snapchat", 0xFF8A7B00, "S"),
        MessengerDefinition("com.twitter.android", "X", 0xFF202124, "X"),
        MessengerDefinition("com.linkedin.android", "LinkedIn", 0xFF0A66C2, "L"),
        MessengerDefinition("com.zhiliaoapp.musically", "TikTok", 0xFF202124, "T"),
        MessengerDefinition("com.zing.zalo", "Zalo", 0xFF0068FF, "Z"),
        MessengerDefinition("com.nhn.android.band", "BAND", 0xFF00C73C, "B"),
        MessengerDefinition("com.imo.android.imoim", "imo", 0xFF2A8BF2, "i"),
        MessengerDefinition("com.beeper.android", "Beeper", 0xFF202124, "B"),
    )

    val packages: Set<String> = apps.mapTo(linkedSetOf()) { it.packageName }

    fun find(packageName: String): MessengerDefinition? =
        apps.firstOrNull { it.packageName == packageName }
}
