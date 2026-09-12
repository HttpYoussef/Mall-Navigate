package com.example.mallar.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour tokens for MallAR.
 * Defined in docs/Theming/README.md §7.1.
 */
@Immutable
data class MallColors(
    val screenBackground: Color,
    val surface: Color,
    val surfaceSunken: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val brandTeal: Color,
    val accent: Color,
    val accentText: Color,
    val onAccent: Color,
    val border: Color,
    val borderStrong: Color,
    val divider: Color,
    val focusRing: Color,
    val success: Color,
    val onSuccess: Color,
    val successText: Color,
    val error: Color,
    val onError: Color,
    val errorText: Color,
    val warningText: Color,
    val scrimSurface: Color,
    val scrimCard: Color,
    val onScrim: Color,
    val onScrimMuted: Color,
    val scrim: Color,
    val imagePlaceholder: Color,
    val imageErrorSurface: Color,
    val overlayScrimGradient: Brush,
    val hairlineOverlay: Color,
)

val MallLightColors = MallColors(
    screenBackground = MallLightScreenBackground,
    surface = MallLightSurface,
    surfaceSunken = MallLightSurfaceSunken,
    textPrimary = MallLightTextPrimary,
    textSecondary = MallLightTextSecondary,
    textDisabled = MallLightTextDisabled,
    brandTeal = MallLightBrandTeal,
    accent = MallLightAccent,
    accentText = MallLightAccentText,
    onAccent = MallLightOnAccent,
    border = MallLightBorder,
    borderStrong = MallLightBorderStrong,
    divider = MallLightDivider,
    focusRing = MallLightFocusRing,
    success = MallLightSuccess,
    onSuccess = MallLightOnSuccess,
    successText = MallLightSuccessText,
    error = MallLightError,
    onError = MallLightOnError,
    errorText = MallLightErrorText,
    warningText = MallLightWarningText,
    scrimSurface = ScrimSurface,
    scrimCard = ScrimCard,
    onScrim = OnScrim,
    onScrimMuted = OnScrimMuted,
    scrim = Scrim,
    imagePlaceholder = MallLightImagePlaceholder,
    imageErrorSurface = MallLightImageErrorSurface,
    overlayScrimGradient = Brush.verticalGradient(
        listOf(
            Color(0x00FFFFFF),
            Color(0xE6FFFFFF)
        )
    ),
    hairlineOverlay = MallLightHairlineOverlay,
)

val MallDarkColors = MallColors(
    screenBackground = MallDarkScreenBackground,
    surface = MallDarkSurface,
    surfaceSunken = MallDarkSurfaceSunken,
    textPrimary = MallDarkTextPrimary,
    textSecondary = MallDarkTextSecondary,
    textDisabled = MallDarkTextDisabled,
    brandTeal = MallDarkBrandTeal,
    accent = MallDarkAccent,
    accentText = MallDarkAccentText,
    onAccent = MallDarkOnAccent,
    border = MallDarkBorder,
    borderStrong = MallDarkBorderStrong,
    divider = MallDarkDivider,
    focusRing = MallDarkFocusRing,
    success = MallDarkSuccess,
    onSuccess = MallDarkOnSuccess,
    successText = MallDarkSuccessText,
    error = MallDarkError,
    onError = MallDarkOnError,
    errorText = MallDarkErrorText,
    warningText = MallDarkWarningText,
    scrimSurface = ScrimSurface,
    scrimCard = ScrimCard,
    onScrim = OnScrim,
    onScrimMuted = OnScrimMuted,
    scrim = Scrim,
    imagePlaceholder = MallDarkImagePlaceholder,
    imageErrorSurface = MallDarkImageErrorSurface,
    overlayScrimGradient = Brush.verticalGradient(
        listOf(
            Color(0x00161D22),
            Color(0xE6161D22)
        )
    ),
    hairlineOverlay = MallDarkHairlineOverlay,
)

val LocalMallColors = staticCompositionLocalOf { MallLightColors }

object MallTheme {
    val colors: MallColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMallColors.current
}
