package com.replyhub.app.notifications

import java.util.concurrent.ConcurrentHashMap

internal class RecentNotificationDeduplicator(
    private val retentionMillis: Long = 10 * 60 * 1000L,
) {
    private val fingerprints = ConcurrentHashMap<String, Long>()

    fun shouldProcess(
        packageName: String,
        notificationKey: String,
        body: String,
        postTime: Long,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        fingerprints.entries.removeIf { now - it.value > retentionMillis }
        val fingerprint = listOf(
            packageName.hashCode(),
            notificationKey.hashCode(),
            body.hashCode(),
            postTime.hashCode(),
        ).joinToString(":")
        return fingerprints.putIfAbsent(fingerprint, now) == null
    }
}
