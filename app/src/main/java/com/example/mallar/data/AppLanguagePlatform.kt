package com.example.mallar.data

import android.content.Context
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat

/**
 * Android platform adapter bridging [AppLanguageResolver] with AndroidX AppCompat per-app language APIs.
 */
object AppLanguagePlatform {

    /**
     * Returns the list of language tags currently applied to the application.
     */
    fun currentTags(): List<String> {
        val locales = AppCompatDelegate.getApplicationLocales()
        return (0 until locales.size()).mapNotNull { locales.get(it)?.toLanguageTag() }
    }

    /**
     * Returns the list of language tags from the device/system locale list.
     * Uses [LocaleManagerCompat] if [context] is provided, falling back to [Resources.getSystem].
     */
    fun deviceLocales(context: Context? = null): List<String> {
        val localeList = if (context != null) {
            LocaleManagerCompat.getSystemLocales(context)
        } else {
            LocaleListCompat.wrap(Resources.getSystem().configuration.locales)
        }
        return (0 until localeList.size()).mapNotNull { localeList.get(it)?.toLanguageTag() }
    }

    /**
     * Overload returning device locales without requiring a [Context].
     */
    fun deviceLocales(): List<String> = deviceLocales(null)

    /**
     * Applies an explicit [AppLanguage] as the application locale.
     */
    fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.code))
    }

    /**
     * Clears explicit application locales so the app follows the device locale.
     */
    fun applyFollowDevice() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }
}
