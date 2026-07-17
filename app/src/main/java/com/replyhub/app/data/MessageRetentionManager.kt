package com.replyhub.app.data

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MessageRetentionManager(
    private val repository: MessageRepository,
    private val settingsStore: PrivacySettingsStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val deleteAttachment: (String) -> Unit = { path -> File(path).delete() },
) {
    private val pruneMutex = Mutex()

    suspend fun pruneIfDue(): Int? = pruneMutex.withLock {
        val nowMillis = clock()
        if (!settingsStore.isRetentionPruneDue(nowMillis)) return@withLock null
        prune(nowMillis)?.also { settingsStore.markRetentionPruned(nowMillis) }
    }

    suspend fun pruneNow(): Int? = pruneMutex.withLock {
        val nowMillis = clock()
        prune(nowMillis)?.also { settingsStore.markRetentionPruned(nowMillis) }
    }

    private suspend fun prune(nowMillis: Long): Int? {
        val retentionDays = settingsStore.retentionDays.value
        if (retentionDays == PrivacySettingsStore.KEEP_FOREVER) return null

        val cutoff = nowMillis - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        repository.attachmentPathsOlderThan(cutoff).forEach { path ->
            runCatching { deleteAttachment(path) }
        }
        return repository.deleteOlderThan(cutoff)
    }
}
