package com.example.mallar.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevation levels and mode-specific shadow colours.
 * Defined in docs/Theming/README.md §8.
 */
object Elevation {
    val flat: Dp = 0.dp
    val raised: Dp = 4.dp
    val overlay: Dp = 8.dp

    // Light mode shadow colours
    // flat: 0dp, no shadow
    val shadowFlatLight: Color = Color.Transparent
    // raised: 4dp, shadow #0F1F2E @ 10%
    val shadowRaisedLight: Color = Color(0x1A0F1F2E)
    // overlay: 8dp, shadow #0F1F2E @ 14%
    val shadowOverlayLight: Color = Color(0x240F1F2E)

    // Dark mode shadow colours
    // flat: 0dp, no shadow
    val shadowFlatDark: Color = Color.Transparent
    // raised: 4dp, shadow #000000 @ 40%
    val shadowRaisedDark: Color = Color(0x66000000)
    // overlay: 8dp, shadow #000000 @ 48%
    val shadowOverlayDark: Color = Color(0x7A000000)
}
