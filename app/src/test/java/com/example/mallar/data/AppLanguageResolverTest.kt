package com.example.mallar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageResolverTest {

    @Test
    fun supported_isExactlyEnglishAndArabicInOrder() {
        assertEquals(
            "supported must contain exactly English and Arabic in that order",
            listOf(AppLanguage.ENGLISH, AppLanguage.ARABIC),
            AppLanguageResolver.supported
        )
    }

    @Test
    fun appLanguage_fromTag() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-US"))
        assertEquals(AppLanguage.ARABIC, AppLanguage.fromTag("ar"))
        assertEquals(AppLanguage.ARABIC, AppLanguage.fromTag("ar-EG"))
        assertEquals(AppLanguage.ARABIC, AppLanguage.fromTag("ar_EG"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTag("es"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTag("fr"))
        assertNull(AppLanguage.fromTag("de"))
        assertNull(AppLanguage.fromTag(""))
        assertNull(AppLanguage.fromTag(null))
    }

    @Test
    fun effective_explicitArabic_returnsArabic() {
        val result = AppLanguageResolver.effective(
            explicitTags = listOf("ar"),
            deviceLocales = listOf("en")
        )
        assertEquals(AppLanguage.ARABIC, result)
    }

    @Test
    fun effective_explicitEnglish_returnsEnglish() {
        val result = AppLanguageResolver.effective(
            explicitTags = listOf("en"),
            deviceLocales = listOf("ar")
        )
        assertEquals(AppLanguage.ENGLISH, result)
    }

    @Test
    fun effective_explicitUnsupported_fallsThroughToDeviceArabic() {
        val result = AppLanguageResolver.effective(
            explicitTags = listOf("fr"),
            deviceLocales = listOf("ar")
        )
        assertEquals(AppLanguage.ARABIC, result)
    }

    @Test
    fun effective_noExplicit_deviceArabicEG_returnsArabic() {
        val result = AppLanguageResolver.effective(
            explicitTags = emptyList(),
            deviceLocales = listOf("ar-EG", "en")
        )
        assertEquals(AppLanguage.ARABIC, result)
    }

    @Test
    fun effective_noExplicit_unsupportedDevice_fallsBackToEnglish() {
        val result = AppLanguageResolver.effective(
            explicitTags = emptyList(),
            deviceLocales = listOf("de")
        )
        assertEquals(AppLanguage.ENGLISH, result)
    }

    @Test
    fun effective_noExplicit_emptyDevice_fallsBackToEnglish() {
        val result = AppLanguageResolver.effective(
            explicitTags = emptyList(),
            deviceLocales = emptyList()
        )
        assertEquals(AppLanguage.ENGLISH, result)
    }

    @Test
    fun migrationDecision_nullValue_returnsFollowDevice() {
        assertEquals(
            MigrationDecision.FollowDevice,
            AppLanguageResolver.migrationDecision(legacyValue = null, alreadyMigrated = false)
        )
    }

    @Test
    fun migrationDecision_arabicValue_returnsExplicitArabic() {
        assertEquals(
            MigrationDecision.Explicit(AppLanguage.ARABIC),
            AppLanguageResolver.migrationDecision(legacyValue = "ar", alreadyMigrated = false)
        )
    }

    @Test
    fun migrationDecision_englishValue_returnsExplicitEnglish() {
        assertEquals(
            MigrationDecision.Explicit(AppLanguage.ENGLISH),
            AppLanguageResolver.migrationDecision(legacyValue = "en", alreadyMigrated = false)
        )
    }

    @Test
    fun migrationDecision_unrecognisedValue_returnsFollowDevice() {
        assertEquals(
            MigrationDecision.FollowDevice,
            AppLanguageResolver.migrationDecision(legacyValue = "xx", alreadyMigrated = false)
        )
        assertEquals(
            MigrationDecision.FollowDevice,
            AppLanguageResolver.migrationDecision(legacyValue = "es", alreadyMigrated = false)
        )
    }

    @Test
    fun migrationDecision_alreadyMigrated_returnsNoOp() {
        assertEquals(
            MigrationDecision.NoOp,
            AppLanguageResolver.migrationDecision(legacyValue = null, alreadyMigrated = true)
        )
        assertEquals(
            MigrationDecision.NoOp,
            AppLanguageResolver.migrationDecision(legacyValue = "ar", alreadyMigrated = true)
        )
        assertEquals(
            MigrationDecision.NoOp,
            AppLanguageResolver.migrationDecision(legacyValue = "en", alreadyMigrated = true)
        )
        assertEquals(
            MigrationDecision.NoOp,
            AppLanguageResolver.migrationDecision(legacyValue = "xx", alreadyMigrated = true)
        )
    }
}
