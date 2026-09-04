package com.example.mallar.data

import android.content.Context

/**
 * Executes a one-time migration of any legacy language preference stored in
 * "mallar_app_prefs" to the AndroidX AppCompat per-app-language system.
 */
object LegacyLanguageMigration {

    private const val PREFS_NAME = "mallar_app_prefs"
    private const val KEY_LANGUAGE_MIGRATED = "language_migrated"
    private const val KEY_LEGACY_LANGUAGE = "language"

    /**
     * Runs the migration once if not previously completed.
     */
    fun runOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LANGUAGE_MIGRATED, false)) {
            return
        }

        val legacyValue = if (prefs.contains(KEY_LEGACY_LANGUAGE)) {
            prefs.getString(KEY_LEGACY_LANGUAGE, null)
        } else {
            null
        }

        when (val decision = AppLanguageResolver.migrationDecision(legacyValue, alreadyMigrated = false)) {
            is MigrationDecision.FollowDevice -> {
                AppLanguagePlatform.applyFollowDevice()
            }
            is MigrationDecision.Explicit -> {
                AppLanguagePlatform.apply(decision.language)
            }
            is MigrationDecision.NoOp -> {
                // No action needed
            }
        }

        prefs.edit().putBoolean(KEY_LANGUAGE_MIGRATED, true).commit()
    }
}
