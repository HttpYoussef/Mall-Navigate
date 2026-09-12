package com.example.mallar.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// Legacy Constants (DO NOT DELETE OR RENAME — referenced by unmigrated screens)
// =============================================================================

// MallAR Brand Colors
val Teal = Color(0xFF1A8C8C)
val DarkTeal = Color(0xFF0F5F5F)
val TealLight = Color(0xFF2FA3B8)
val White = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF1A1A1A)
val TextSecondary = Color(0xFF666666)
val CameraPlaceholder = Color(0xFF222222)
val LightGray = Color(0xFFF0F0F0)
val CardShadow = Color(0x1A000000)
val RedAccent = Color(0xFFE53935)
val GreenArrow = Color(0xFF4CAF50)

// Modern UI Palette
val SurfaceLight = Color(0xFFF8FBFA)
val BrandSecondary = Color(0xFFC39D51)
val ErrorRed = Color(0xFFBA1A1A)
val SuccessGreen = Color(0xFF2E7D32)
val DividerColor = Color(0xFFE1E5E4)

// Dark Mode Colors
val DarkBackground = Color(0xFF121218)
val DarkSurface = Color(0xFF1E1E2A)
val DarkCard = Color(0xFF282838)
val DarkTextPrimary = Color(0xFFE8E8F0)
val DarkTextSecondary = Color(0xFF9898A8)
val DarkDivider = Color(0xFF363648)

// =============================================================================
// MallColors Raw Palette Tokens (README §3)
// =============================================================================

// Surfaces
internal val MallLightScreenBackground = Color(0xFFF7F9FB)
internal val MallDarkScreenBackground = Color(0xFF0E1418)

internal val MallLightSurface = Color(0xFFFFFFFF)
internal val MallDarkSurface = Color(0xFF161D22)

internal val MallLightSurfaceSunken = Color(0xFFEEF1F4)
internal val MallDarkSurfaceSunken = Color(0xFF10161A)

// Text
internal val MallLightTextPrimary = Color(0xFF151A1E)
internal val MallDarkTextPrimary = Color(0xFFE8ECEF)

internal val MallLightTextSecondary = Color(0xFF5A6672)
internal val MallDarkTextSecondary = Color(0xFF9BA8B0)

internal val MallLightTextDisabled = Color(0xFF9AA5AF)
internal val MallDarkTextDisabled = Color(0xFF6B7780)

// Brand Teal
internal val MallLightBrandTeal = Color(0xFF1A8C8C)
internal val MallDarkBrandTeal = Color(0xFF2FA3B8)

internal val MallLightAccent = Color(0xFF107C7A)
internal val MallDarkAccent = Color(0xFF3FB2AC)

internal val MallLightAccentText = Color(0xFF0A6360)
internal val MallDarkAccentText = Color(0xFF5BC9C2)

internal val MallLightOnAccent = Color(0xFFFFFFFF)
internal val MallDarkOnAccent = Color(0xFF06201F)

// Lines
internal val MallLightBorder = Color(0xFFE2E7EC)
internal val MallDarkBorder = Color(0xFF2A343A)

internal val MallLightBorderStrong = MallLightAccent
internal val MallDarkBorderStrong = MallDarkAccent

internal val MallLightDivider = Color(0xFFEAEEF1)
internal val MallDarkDivider = Color(0xFF232C31)

internal val MallLightFocusRing = MallLightAccentText
internal val MallDarkFocusRing = MallDarkAccentText

// Status
internal val MallLightSuccess = Color(0xFF1E7A3E)
internal val MallDarkSuccess = Color(0xFF3EA55F)

internal val MallLightOnSuccess = Color(0xFFFFFFFF)
internal val MallDarkOnSuccess = Color(0xFF06201F)

internal val MallLightSuccessText = Color(0xFF136B32)
internal val MallDarkSuccessText = Color(0xFF6FCB89)

internal val MallLightError = Color(0xFFC0362C)
internal val MallDarkError = Color(0xFFE06A60)

internal val MallLightOnError = Color(0xFFFFFFFF)
internal val MallDarkOnError = Color(0xFF1A0E0D)

internal val MallLightErrorText = Color(0xFFB0271F)
internal val MallDarkErrorText = Color(0xFFEE9089)

internal val MallLightWarningText = Color(0xFF8A5A00)
internal val MallDarkWarningText = Color(0xFFD8A24B)

// Always-dark chrome (both modes)
internal val ScrimSurface = Color(0xFF0E1A1F)
internal val ScrimCard = Color(0xFF182830)
internal val OnScrim = Color(0xFFF2F5F6)
internal val OnScrimMuted = Color(0xFF9BAAB2)

// Alpha-bearing / Overlays
internal val Scrim = Color(0xFF000000)
internal val MallLightHairlineOverlay = Color.Transparent
internal val MallDarkHairlineOverlay = Color(0x14FFFFFF) // #FFFFFF @ 8%

// Image Surfaces
internal val MallLightImagePlaceholder = Color(0xFFFFFFFF)
internal val MallDarkImagePlaceholder = Color(0xFFF0F2F4)

internal val MallLightImageErrorSurface = MallLightSurfaceSunken
internal val MallDarkImageErrorSurface = MallDarkSurfaceSunken

// =============================================================================
// Material 3 ColorScheme 36 Roles (README §5)
// =============================================================================

internal val M3LightPrimary = Color(0xFF0A6360)
internal val M3DarkPrimary = Color(0xFF5BC9C2)

internal val M3LightOnPrimary = Color(0xFFFFFFFF)
internal val M3DarkOnPrimary = Color(0xFF06201F)

internal val M3LightPrimaryContainer = Color(0xFF107C7A)
internal val M3DarkPrimaryContainer = Color(0xFF3FB2AC)

internal val M3LightOnPrimaryContainer = Color(0xFFFFFFFF)
internal val M3DarkOnPrimaryContainer = Color(0xFF06201F)

internal val M3LightInversePrimary = Color(0xFF2FA3B8)
internal val M3DarkInversePrimary = Color(0xFF0A6360)

internal val M3LightSecondary = Color(0xFF107C7A)
internal val M3DarkSecondary = Color(0xFF3FB2AC)

internal val M3LightOnSecondary = Color(0xFFFFFFFF)
internal val M3DarkOnSecondary = Color(0xFF06201F)

internal val M3LightSecondaryContainer = Color(0xFFDCEBEA)
internal val M3DarkSecondaryContainer = Color(0xFF123B39)

internal val M3LightOnSecondaryContainer = Color(0xFF0A6360)
internal val M3DarkOnSecondaryContainer = Color(0xFF5BC9C2)

internal val M3LightTertiary = Color(0xFF0A6360)
internal val M3DarkTertiary = Color(0xFF5BC9C2)

internal val M3LightOnTertiary = Color(0xFFFFFFFF)
internal val M3DarkOnTertiary = Color(0xFF06201F)

internal val M3LightTertiaryContainer = Color(0xFFEEF1F4)
internal val M3DarkTertiaryContainer = Color(0xFF10161A)

internal val M3LightOnTertiaryContainer = Color(0xFF151A1E)
internal val M3DarkOnTertiaryContainer = Color(0xFFE8ECEF)

internal val M3LightBackground = Color(0xFFF7F9FB)
internal val M3DarkBackground = Color(0xFF0E1418)

internal val M3LightOnBackground = Color(0xFF151A1E)
internal val M3DarkOnBackground = Color(0xFFE8ECEF)

internal val M3LightSurface = Color(0xFFFFFFFF)
internal val M3DarkSurface = Color(0xFF161D22)

internal val M3LightOnSurface = Color(0xFF151A1E)
internal val M3DarkOnSurface = Color(0xFFE8ECEF)

internal val M3LightSurfaceVariant = Color(0xFFEEF1F4)
internal val M3DarkSurfaceVariant = Color(0xFF10161A)

internal val M3LightOnSurfaceVariant = Color(0xFF5A6672)
internal val M3DarkOnSurfaceVariant = Color(0xFF9BA8B0)

internal val M3LightSurfaceTint = Color.Transparent
internal val M3DarkSurfaceTint = Color.Transparent

internal val M3LightInverseSurface = Color(0xFF151A1E)
internal val M3DarkInverseSurface = Color(0xFFE8ECEF)

internal val M3LightInverseOnSurface = Color(0xFFF2F5F6)
internal val M3DarkInverseOnSurface = Color(0xFF151A1E)

internal val M3LightSurfaceBright = Color(0xFFFFFFFF)
internal val M3DarkSurfaceBright = Color(0xFF1F282E)

internal val M3LightSurfaceDim = Color(0xFFE6EAEE)
internal val M3DarkSurfaceDim = Color(0xFF0A0F12)

internal val M3LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val M3DarkSurfaceContainerLowest = Color(0xFF0A0F12)

internal val M3LightSurfaceContainerLow = Color(0xFFF7F9FB)
internal val M3DarkSurfaceContainerLow = Color(0xFF12191E)

internal val M3LightSurfaceContainer = Color(0xFFEEF1F4)
internal val M3DarkSurfaceContainer = Color(0xFF161D22)

internal val M3LightSurfaceContainerHigh = Color(0xFFE8ECEF)
internal val M3DarkSurfaceContainerHigh = Color(0xFF1F282E)

internal val M3LightSurfaceContainerHighest = Color(0xFFE2E7EC)
internal val M3DarkSurfaceContainerHighest = Color(0xFF253038)

internal val M3LightError = Color(0xFFC0362C)
internal val M3DarkError = Color(0xFFE06A60)

internal val M3LightOnError = Color(0xFFFFFFFF)
internal val M3DarkOnError = Color(0xFF1A0E0D)

internal val M3LightErrorContainer = Color(0xFFF9DEDB)
internal val M3DarkErrorContainer = Color(0xFF5C201B)

internal val M3LightOnErrorContainer = Color(0xFFB0271F)
internal val M3DarkOnErrorContainer = Color(0xFFEE9089)

internal val M3LightOutline = Color(0xFF8A939C)
internal val M3DarkOutline = Color(0xFF6B7780)

internal val M3LightOutlineVariant = Color(0xFFE2E7EC)
internal val M3DarkOutlineVariant = Color(0xFF2A343A)

internal val M3LightScrim = Color(0xFF000000)
internal val M3DarkScrim = Color(0xFF000000)