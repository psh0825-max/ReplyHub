package com.replyhub.app.data

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

/**
 * 접근성 서비스에 화면을 공개하지 않는 메신저(예: 위챗)를 앱 버전별로 기억한다.
 * 메신저가 업데이트되면 기록이 무효가 되어 자동 전송을 다시 시도한다.
 */
class AutoSendCompatibilityStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val packageManager = context.packageManager

    fun isScreenHidden(packageName: String): Boolean {
        if (!preferences.contains(packageName)) return false
        val current = versionCode(packageName) ?: return false
        return preferences.getLong(packageName, NOT_RECORDED) == current
    }

    fun markScreenHidden(packageName: String) {
        val current = versionCode(packageName) ?: return
        preferences.edit().putLong(packageName, current).apply()
    }

    private fun versionCode(packageName: String): Long? = runCatching {
        PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
    }.getOrNull()

    private companion object {
        const val PREFERENCES_NAME = "replyhub_auto_send_compatibility"
        const val NOT_RECORDED = -1L
    }
}
