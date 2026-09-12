package com.example.mallar.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.mallar.data.AppPreferences

/**
 * Opt-out for always-dark routes (camera viewfinders, AR HUD, 2D map canvas, splash).
 *
 * Forces light/white status-bar and navigation-bar icons while in composition.
 * On disposal, restores the icon appearance that [MallARTheme]'s default contract
 * would set for the current dark-mode state (!isDarkMode).
 */
@Composable
fun DarkSystemBars() {
    val isDarkMode by AppPreferences.isDarkMode.collectAsState()
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(isDarkMode) {
            val window = (view.context as? Activity)?.window
            if (window == null) {
                return@DisposableEffect onDispose { }
            }
            val insetsController = WindowCompat.getInsetsController(window, view)

            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false

            onDispose {
                insetsController.isAppearanceLightStatusBars = !isDarkMode
                insetsController.isAppearanceLightNavigationBars = !isDarkMode
            }
        }
    }
}
