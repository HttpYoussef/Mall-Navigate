package com.example.mallar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.mallar.data.AppPreferences

private val MallARLightScheme = lightColorScheme(
    primary = Teal,
    onPrimary = White,
    secondary = DarkTeal,
    onSecondary = White,
    background = White,
    onBackground = TextPrimary,
    surface = White,
    onSurface = TextPrimary,
    surfaceVariant = LightGray,
    onSurfaceVariant = TextSecondary
)

private val MallARDarkScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = DarkBackground,
    secondary = TealLight,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkTextSecondary
)

@Composable
fun MallARTheme(
    content: @Composable () -> Unit
) {
    val isDarkMode by AppPreferences.isDarkMode.collectAsState()

    val colorScheme = if (isDarkMode) MallARDarkScheme else MallARLightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}