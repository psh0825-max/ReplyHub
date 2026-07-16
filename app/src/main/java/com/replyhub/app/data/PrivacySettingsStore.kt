package com.replyhub.app.data

import android.content.Context
import com.replyhub.app.messaging.MessengerCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PrivacySettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _disabledPackages = MutableStateFlow(
        preferences.getStringSet(KEY_DISABLED_PACKAGES, emptySet()).orEmpty(),
    )
    private val _retentionDays = MutableStateFlow(
        preferences.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS),
    )
    private val _enabledPackages = MutableStateFlow(enabledPackageSnapshot())

    val enabledPackages: StateFlow<Set<String>> = _enabledPackages.asStateFlow()
    val retentionDays: StateFlow<Int> = _retentionDays.asStateFlow()

    fun isCaptureEnabled(packageName: String): Boolean = packageName !in _disabledPackages.value

    @Synchronized
    fun setCaptureEnabled(packageName: String, enabled: Boolean) {
        val updated = _disabledPackages.value.toMutableSet().apply {
            if (enabled) remove(packageName) else add(packageName)
        }.toSet()
        preferences.edit().putStringSet(KEY_DISABLED_PACKAGES, updated).apply()
        _disabledPackages.value = updated
        _enabledPackages.value = enabledPackageSnapshot()
    }

    @Synchronized
    fun setRetentionDays(days: Int) {
        require(days == KEEP_FOREVER || days in ALLOWED_RETENTION_DAYS)
        preferences.edit().putInt(KEY_RETENTION_DAYS, days).apply()
        _retentionDays.value = days
    }

    private fun enabledPackageSnapshot(): Set<String> =
        MessengerCatalog.packages - _disabledPackages.value

    companion object {
        const val KEEP_FOREVER = -1
        val ALLOWED_RETENTION_DAYS = setOf(7, 30, 90)
        const val DEFAULT_RETENTION_DAYS = 30

        private const val PREFERENCES_NAME = "replyhub_privacy_settings"
        private const val KEY_DISABLED_PACKAGES = "disabled_packages"
        private const val KEY_RETENTION_DAYS = "retention_days"
    }
}
