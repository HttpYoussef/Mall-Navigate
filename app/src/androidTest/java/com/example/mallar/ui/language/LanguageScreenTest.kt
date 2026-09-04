package com.example.mallar.ui.language

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mallar.data.AppLanguage
import com.example.mallar.data.AppLanguageResolver
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [LanguageScreenContent].
 *
 * These tests require an Android device or emulator. They CANNOT be run
 * headlessly. They are included here for completeness per spec "Tests" section
 * and verified by code-review only until a CI emulator is configured.
 */
@RunWith(AndroidJUnit4::class)
class LanguageScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Helper: set up screen with given inputs ──────────────────────────────

    private fun setup(
        languages: List<AppLanguage> = AppLanguageResolver.supported,
        current: AppLanguage = AppLanguage.ENGLISH,
        onSelect: (AppLanguage) -> Unit = {},
        onBackClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            LanguageScreenContent(
                languages = languages,
                current = current,
                onSelect = onSelect,
                onBackClick = onBackClick,
            )
        }
    }

    // ── Test 1: Renders one row showing "English" and one showing "العربية" ───

    @Test
    fun rendersOneRowPerSupportedLanguageWithAutonym() {
        setup(
            languages = AppLanguageResolver.supported,
            current = AppLanguage.ENGLISH,
        )

        composeTestRule
            .onNodeWithText("English")
            .assertExists()

        composeTestRule
            .onNodeWithText("العربية")
            .assertExists()
    }

    // ── Test 2: Tapping "العربية" invokes onSelect with AppLanguage.ARABIC ────

    @Test
    fun tappingArabicRow_invokesOnSelectWithArabic() {
        var selectedLanguage: AppLanguage? = null

        setup(
            languages = AppLanguageResolver.supported,
            current = AppLanguage.ENGLISH,
            onSelect = { selectedLanguage = it },
        )

        composeTestRule
            .onNodeWithText("العربية")
            .performClick()

        assert(selectedLanguage == AppLanguage.ARABIC) {
            "Expected ARABIC to be selected, got $selectedLanguage"
        }
    }

    // ── Test 3: English is marked selected and Arabic is not ─────────────────

    @Test
    fun whenCurrentIsEnglish_englishIsSelectedAndArabicIsNot() {
        setup(
            languages = AppLanguageResolver.supported,
            current = AppLanguage.ENGLISH,
        )

        composeTestRule
            .onNodeWithText("English")
            .assertIsSelected()

        composeTestRule
            .onNodeWithText("العربية")
            .assertIsNotSelected()
    }
}
