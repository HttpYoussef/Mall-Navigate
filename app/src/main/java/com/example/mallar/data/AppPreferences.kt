package com.example.mallar.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized app preferences for dark mode and language settings.
 * Initialize in MainActivity before setContent.
 */
object AppPreferences {

    private const val PREFS_NAME = "mallar_app_prefs"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_LANGUAGE = "language"

    private lateinit var prefs: SharedPreferences

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _language = MutableStateFlow("en")
    val language: StateFlow<String> = _language.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isDarkMode.value = prefs.getBoolean(KEY_DARK_MODE, false)
        _language.value = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun setLanguage(lang: String) {
        _language.value = lang
        prefs.edit().putString(KEY_LANGUAGE, lang).commit()
    }

    fun getLanguage(): String = if (::prefs.isInitialized) {
        prefs.getString(KEY_LANGUAGE, "en") ?: "en"
    } else "en"
}
