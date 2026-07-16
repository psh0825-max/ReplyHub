package com.replyhub.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppLanguage(
    val storageValue: String,
    val locale: Locale,
) {
    KOREAN("ko", Locale.KOREA),
    ENGLISH("en", Locale.US),
    ;

    fun text(korean: String, english: String): String =
        if (this == KOREAN) korean else english

    companion object {
        fun fromStorage(value: String?): AppLanguage =
            entries.firstOrNull { it.storageValue == value } ?: KOREAN
    }
}

class AppLanguageStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _language = MutableStateFlow(
        AppLanguage.fromStorage(preferences.getString(KEY_LANGUAGE, null)),
    )

    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        preferences.edit().putString(KEY_LANGUAGE, language.storageValue).apply()
        _language.value = language
    }

    fun current(): AppLanguage = _language.value

    private companion object {
        const val PREFERENCES_NAME = "replyhub_app_language"
        const val KEY_LANGUAGE = "language"
    }
}
