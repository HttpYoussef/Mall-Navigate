package com.example.mallar.ui.mall

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mallar.data.Mall
import com.example.mallar.data.StartupState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [MallSelectionScreen].
 *
 * These tests require an Android device or emulator.  They CANNOT be run
 * headlessly.  They are included here for completeness per spec "Tests" section
 * and verified by code-review only until a CI emulator is configured.
 */
@RunWith(AndroidJUnit4::class)
class MallSelectionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Helper: set up screen with a given startupState ───────────────────────

    private fun setup(
        startupState: StartupState,
        onMallSelected: (Mall) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MallSelectionScreen(
                startupState   = startupState,
                onMallSelected = onMallSelected,
                onRetry        = onRetry,
            )
        }
    }

    // ── Test 1: Available card invokes onMallSelected on tap when Success ─────

    @Test
    fun citystarsCard_tapped_whenSuccess_invokesOnMallSelected() {
        var selected: Mall? = null

        setup(
            startupState   = StartupState.Success,
            onMallSelected = { selected = it },
        )

        // The City Stars card has merged semantics containing "City Stars"
        composeTestRule
            .onNodeWithText("City Stars", substring = true)
            .performClick()

        assert(selected == Mall.CITY_STARS) {
            "Expected CITY_STARS to be selected, got $selected"
        }
    }

    // ── Test 2: Coming-soon cards are disabled and do not invoke callback ─────

    @Test
    fun comingSoonCards_areDisabled_andDoNotInvokeCallback() {
        var selected: Mall? = null

        setup(
            startupState   = StartupState.Success,
            onMallSelected = { selected = it },
        )

        // City Centre Almaza — must be marked disabled in semantics
        composeTestRule
            .onNodeWithText("City Centre Almaza", substring = true)
            .assertIsNotEnabled()

        // Mall of Egypt — must be marked disabled in semantics
        composeTestRule
            .onNodeWithText("Mall of Egypt", substring = true)
            .assertIsNotEnabled()

        // Confirm no selection occurred
        assert(selected == null) {
            "Coming-soon cards must not trigger onMallSelected; got $selected"
        }
    }

    // ── Test 3: Card does not invoke callback while Loading ───────────────────

    @Test
    fun citystarsCard_isNotClickable_whileLoading() {
        var selected: Mall? = null

        setup(
            startupState   = StartupState.Loading,
            onMallSelected = { selected = it },
        )

        // Card is rendered but disabled during Loading
        composeTestRule
            .onNodeWithText("City Stars", substring = true)
            .assertIsNotEnabled()

        assert(selected == null) {
            "onMallSelected must not be called while Loading; got $selected"
        }
    }

    // ── Test 4: Error state shows Retry button that invokes onRetry ───────────

    @Test
    fun errorState_showsRetryButton_andInvokesOnRetry() {
        var retryCalled = false

        setup(
            startupState = StartupState.Error,
            onRetry      = { retryCalled = true },
        )

        composeTestRule
            .onNodeWithText("Retry", substring = true)
            .assertIsDisplayed()
            .performClick()

        assert(retryCalled) { "Retry click must invoke onRetry" }
    }

    // ── Test 5: Disabled cards expose "Coming soon" in their semantics ────────

    @Test
    fun comingSoonCards_exposeComingSoonInSemantics() {
        setup(startupState = StartupState.Success)

        // Both unavailable cards must include "Coming soon" in their
        // merged content description
        composeTestRule
            .onNodeWithContentDescription("City Centre Almaza, Heliopolis, Cairo, Coming soon")
            .assertExists()

        composeTestRule
            .onNodeWithContentDescription("Mall of Egypt, 6th of October, Giza, Coming soon")
            .assertExists()
    }
}
