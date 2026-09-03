package com.example.mallar.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized app preferences for dark mode settings.
 * Initialize in MainActivity before setContent.
 */
object AppPreferences {

    private const val PREFS_NAME = "mallar_app_prefs"
    private const val KEY_DARK_MODE = "dark_mode"

    private lateinit var prefs: SharedPreferences

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isDarkMode.value = prefs.getBoolean(KEY_DARK_MODE, false)
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }
}
