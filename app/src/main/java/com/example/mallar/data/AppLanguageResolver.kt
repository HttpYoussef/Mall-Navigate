package com.example.mallar.data

/**
 * Model of languages known to the app.
 *
 * English and Arabic are actively supported. Spanish and French are modelled
 * for future language groundwork, but are not included in [AppLanguageResolver.supported].
 */
enum class AppLanguage(val code: String, val autonym: String) {
    ENGLISH("en", "English"),
    ARABIC("ar", "العربية"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français");

    companion object {
        /**
         * Resolves an [AppLanguage] by matching the primary language subtag
         * (e.g. "en", "en-US", "ar_EG" -> "en", "ar").
         */
        fun fromTag(tag: String?): AppLanguage? {
            if (tag.isNullOrBlank()) return null
            val primary = tag.trim().split('-', '_')[0].lowercase()
            return entries.firstOrNull { it.code.equals(primary, ignoreCase = true) }
        }
    }
}

/**
 * Outcome of legacy language preference migration.
 */
sealed interface MigrationDecision {
    data object FollowDevice : MigrationDecision
    data class Explicit(val language: AppLanguage) : MigrationDecision
    data object NoOp : MigrationDecision
}

/**
 * Pure Kotlin resolver for application language and migration decisions.
 * Has no dependencies on Android framework types.
 */
object AppLanguageResolver {

    /**
     * The ordered list of actively supported languages.
     */
    val supported: List<AppLanguage> = listOf(AppLanguage.ENGLISH, AppLanguage.ARABIC)

    /**
     * Resolves the effective language.
     *
     * 1. Returns the first explicit tag that matches an actively supported language.
     * 2. Else returns the first device locale that matches an actively supported language.
     * 3. Else falls back to English.
     */
    fun effective(explicitTags: List<String>, deviceLocales: List<String>): AppLanguage {
        for (tag in explicitTags) {
            val lang = AppLanguage.fromTag(tag)
            if (lang != null && supported.contains(lang)) {
                return lang
            }
        }
        for (tag in deviceLocales) {
            val lang = AppLanguage.fromTag(tag)
            if (lang != null && supported.contains(lang)) {
                return lang
            }
        }
        return AppLanguage.ENGLISH
    }

    /**
     * Determines what migration action to take given the legacy stored language preference.
     */
    fun migrationDecision(legacyValue: String?, alreadyMigrated: Boolean): MigrationDecision {
        if (alreadyMigrated) {
            return MigrationDecision.NoOp
        }
        return when (legacyValue) {
            null -> MigrationDecision.FollowDevice
            "ar" -> MigrationDecision.Explicit(AppLanguage.ARABIC)
            "en" -> MigrationDecision.Explicit(AppLanguage.ENGLISH)
            else -> MigrationDecision.FollowDevice
        }
    }
}
