package com.example.mallar.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.mallar.data.AppLanguagePlatform
import com.example.mallar.data.AppPreferences

internal val MallARLightScheme = lightColorScheme(
    primary = M3LightPrimary,
    onPrimary = M3LightOnPrimary,
    primaryContainer = M3LightPrimaryContainer,
    onPrimaryContainer = M3LightOnPrimaryContainer,
    inversePrimary = M3LightInversePrimary,
    secondary = M3LightSecondary,
    onSecondary = M3LightOnSecondary,
    secondaryContainer = M3LightSecondaryContainer,
    onSecondaryContainer = M3LightOnSecondaryContainer,
    tertiary = M3LightTertiary,
    onTertiary = M3LightOnTertiary,
    tertiaryContainer = M3LightTertiaryContainer,
    onTertiaryContainer = M3LightOnTertiaryContainer,
    background = M3LightBackground,
    onBackground = M3LightOnBackground,
    surface = M3LightSurface,
    onSurface = M3LightOnSurface,
    surfaceVariant = M3LightSurfaceVariant,
    onSurfaceVariant = M3LightOnSurfaceVariant,
    surfaceTint = Color.Transparent,
    inverseSurface = M3LightInverseSurface,
    inverseOnSurface = M3LightInverseOnSurface,
    surfaceBright = M3LightSurfaceBright,
    surfaceDim = M3LightSurfaceDim,
    surfaceContainerLowest = M3LightSurfaceContainerLowest,
    surfaceContainerLow = M3LightSurfaceContainerLow,
    surfaceContainer = M3LightSurfaceContainer,
    surfaceContainerHigh = M3LightSurfaceContainerHigh,
    surfaceContainerHighest = M3LightSurfaceContainerHighest,
    error = M3LightError,
    onError = M3LightOnError,
    errorContainer = M3LightErrorContainer,
    onErrorContainer = M3LightOnErrorContainer,
    outline = M3LightOutline,
    outlineVariant = M3LightOutlineVariant,
    scrim = M3LightScrim,
)

internal val MallARDarkScheme = darkColorScheme(
    primary = M3DarkPrimary,
    onPrimary = M3DarkOnPrimary,
    primaryContainer = M3DarkPrimaryContainer,
    onPrimaryContainer = M3DarkOnPrimaryContainer,
    inversePrimary = M3DarkInversePrimary,
    secondary = M3DarkSecondary,
    onSecondary = M3DarkOnSecondary,
    secondaryContainer = M3DarkSecondaryContainer,
    onSecondaryContainer = M3DarkOnSecondaryContainer,
    tertiary = M3DarkTertiary,
    onTertiary = M3DarkOnTertiary,
    tertiaryContainer = M3DarkTertiaryContainer,
    onTertiaryContainer = M3DarkOnTertiaryContainer,
    background = M3DarkBackground,
    onBackground = M3DarkOnBackground,
    surface = M3DarkSurface,
    onSurface = M3DarkOnSurface,
    surfaceVariant = M3DarkSurfaceVariant,
    onSurfaceVariant = M3DarkOnSurfaceVariant,
    surfaceTint = Color.Transparent,
    inverseSurface = M3DarkInverseSurface,
    inverseOnSurface = M3DarkInverseOnSurface,
    surfaceBright = M3DarkSurfaceBright,
    surfaceDim = M3DarkSurfaceDim,
    surfaceContainerLowest = M3DarkSurfaceContainerLowest,
    surfaceContainerLow = M3DarkSurfaceContainerLow,
    surfaceContainer = M3DarkSurfaceContainer,
    surfaceContainerHigh = M3DarkSurfaceContainerHigh,
    surfaceContainerHighest = M3DarkSurfaceContainerHighest,
    error = M3DarkError,
    onError = M3DarkOnError,
    errorContainer = M3DarkErrorContainer,
    onErrorContainer = M3DarkOnErrorContainer,
    outline = M3DarkOutline,
    outlineVariant = M3DarkOutlineVariant,
    scrim = M3DarkScrim,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MallARTheme(
    content: @Composable () -> Unit
) {
    val isDarkMode by AppPreferences.isDarkMode.collectAsState()
    val colorScheme = if (isDarkMode) MallARDarkScheme else MallARLightScheme
    val mallColors = if (isDarkMode) MallDarkColors else MallLightColors

    // The active language only changes on an Activity recreate (AppCompat locale
    // switch, system per-app-language change), which tears down this composition
    // anyway — so resolve it once. currentLanguage() hits AppCompat/LocaleManager
    // (binder calls on API 33+); it must not run on every recomposition.
    val context = LocalContext.current
    val activeFontFamily = remember(context) {
        fontFamilyFor(AppLanguagePlatform.currentLanguage(context))
    }
    val typography = remember(activeFontFamily) { typographyFor(activeFontFamily) }

    CompositionLocalProvider(
        LocalMallColors provides mallColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography
        ) {
            val baseTextStyle = LocalTextStyle.current
            val localizedTextStyle = remember(baseTextStyle, activeFontFamily) {
                baseTextStyle.copy(fontFamily = activeFontFamily)
            }
            CompositionLocalProvider(
                LocalTextStyle provides localizedTextStyle,
                LocalRippleConfiguration provides RippleConfiguration(color = mallColors.accent),
                LocalTextSelectionColors provides TextSelectionColors(
                    handleColor = mallColors.accent,
                    backgroundColor = mallColors.accent.copy(alpha = 0.4f)
                )
            ) {
                content()
            }
        }
    }
}