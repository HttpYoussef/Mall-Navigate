package com.example.mallar.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * JVM unit test validating WCAG 2.1 relative luminance and contrast compliance
 * for the MallAR colour system according to docs/Theming/palette.md and docs/Theming/README.md.
 */
class ThemeContrastTest {

    // =========================================================================
    // WCAG 2.1 Contrast Math Helpers
    // =========================================================================

    /**
     * Converts an sRGB gamma-compressed color component in [0, 1] to linear luminance.
     */
    private fun srgbToLinear(c: Float): Double {
        val d = c.toDouble()
        return if (d <= 0.04045) {
            d / 12.92
        } else {
            ((d + 0.055) / 1.055).pow(2.4)
        }
    }

    /**
     * Computes the relative luminance of a Color per WCAG 2.1.
     * Formula: L = 0.2126 * R + 0.7152 * G + 0.0722 * B
     */
    private fun relativeLuminance(color: Color): Double {
        val r = srgbToLinear(color.red)
        val g = srgbToLinear(color.green)
        val b = srgbToLinear(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * Computes the WCAG 2.1 contrast ratio between two colors.
     * Formula: (L1 + 0.05) / (L2 + 0.05) where L1 is the lighter and L2 is the darker luminance.
     */
    private fun contrastRatio(c1: Color, c2: Color): Double {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertContrast(
        name: String,
        foreground: Color,
        background: Color,
        targetRatio: Double
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "Pair '$name' failed contrast requirement: got ${"%.2f".format(ratio)}:1, target was $targetRatio:1",
            ratio >= targetRatio
        )
    }

    // =========================================================================
    // Light Mode Contrast Tests (palette.md §1)
    // =========================================================================

    @Test
    fun lightModeContrast_meetsTargets() {
        val colors = MallLightColors
        val scheme = MallARLightScheme

        // textPrimary on surfaces (Target: 4.5:1)
        assertContrast("textPrimary on surface", colors.textPrimary, colors.surface, 4.5)
        assertContrast("textPrimary on screenBackground", colors.textPrimary, colors.screenBackground, 4.5)
        assertContrast("textPrimary on surfaceSunken", colors.textPrimary, colors.surfaceSunken, 4.5)

        // textSecondary on surfaces (Target: 4.5:1)
        assertContrast("textSecondary on surface", colors.textSecondary, colors.surface, 4.5)
        assertContrast("textSecondary on surfaceSunken", colors.textSecondary, colors.surfaceSunken, 4.5)

        // accentText on surfaces (Target: 4.5:1)
        assertContrast("accentText on surface", colors.accentText, colors.surface, 4.5)
        assertContrast("accentText on screenBackground", colors.accentText, colors.screenBackground, 4.5)

        // focusRing (accentText) on surface (Target: 3.0:1 UI boundary/focus)
        assertContrast("focusRing on surface", colors.focusRing, colors.surface, 3.0)

        // borderStrong (accent) on surfaces (Target: 3.0:1 UI boundary)
        assertContrast("borderStrong on surface", colors.borderStrong, colors.surface, 3.0)
        assertContrast("borderStrong on screenBackground", colors.borderStrong, colors.screenBackground, 3.0)

        // onAccent on accent fill (Target: 4.5:1)
        assertContrast("onAccent on accent", colors.onAccent, colors.accent, 4.5)

        // onPrimaryContainer on primaryContainer (Target: 4.5:1)
        assertContrast("onPrimaryContainer on primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer, 4.5)

        // Status pairs (Target: 4.5:1)
        assertContrast("successText on surface", colors.successText, colors.surface, 4.5)
        assertContrast("onSuccess on success", colors.onSuccess, colors.success, 4.5)
        assertContrast("errorText on surface", colors.errorText, colors.surface, 4.5)
        assertContrast("onError on error", colors.onError, colors.error, 4.5)
        assertContrast("onErrorContainer on errorContainer", scheme.onErrorContainer, scheme.errorContainer, 4.5)
        assertContrast("warningText on surface", colors.warningText, colors.surface, 4.5)

        // M3 stock role pairs
        assertContrast("outline on surface", scheme.outline, scheme.surface, 3.0)
        assertContrast("onSurfaceVariant on surfaceVariant", scheme.onSurfaceVariant, scheme.surfaceVariant, 4.5)
        assertContrast("inverseOnSurface on inverseSurface", scheme.inverseOnSurface, scheme.inverseSurface, 4.5)
        assertContrast("inversePrimary on inverseSurface", scheme.inversePrimary, scheme.inverseSurface, 4.5)
        assertContrast("onSecondaryContainer on secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, 4.5)

        // Store logo monogram on imagePlaceholder plate (Target: 4.5:1)
        assertContrast("monogram accentText on imagePlaceholder", colors.accentText, colors.imagePlaceholder, 4.5)
    }

    // =========================================================================
    // Dark Mode Contrast Tests (palette.md §2)
    // =========================================================================

    @Test
    fun darkModeContrast_meetsTargets() {
        val colors = MallDarkColors
        val scheme = MallARDarkScheme

        // textPrimary on surfaces (Target: 4.5:1)
        assertContrast("dark textPrimary on surface", colors.textPrimary, colors.surface, 4.5)
        assertContrast("dark textPrimary on screenBackground", colors.textPrimary, colors.screenBackground, 4.5)

        // textSecondary on surfaces (Target: 4.5:1)
        assertContrast("dark textSecondary on surface", colors.textSecondary, colors.surface, 4.5)
        assertContrast("dark textSecondary on surfaceSunken", colors.textSecondary, colors.surfaceSunken, 4.5)

        // accentText on surface (Target: 4.5:1)
        assertContrast("dark accentText on surface", colors.accentText, colors.surface, 4.5)

        // focusRing (accentText) on surface (Target: 3.0:1 UI boundary/focus)
        assertContrast("dark focusRing on surface", colors.focusRing, colors.surface, 3.0)

        // borderStrong (accent) on surface (Target: 3.0:1 UI boundary)
        assertContrast("dark borderStrong on surface", colors.borderStrong, colors.surface, 3.0)

        // onAccent on accent fill (Target: 4.5:1)
        assertContrast("dark onAccent on accent", colors.onAccent, colors.accent, 4.5)

        // Status pairs (Target: 4.5:1)
        assertContrast("dark successText on surface", colors.successText, colors.surface, 4.5)
        assertContrast("dark onSuccess on success", colors.onSuccess, colors.success, 4.5)
        assertContrast("dark errorText on surface", colors.errorText, colors.surface, 4.5)
        assertContrast("dark onError on error", colors.onError, colors.error, 4.5)
        assertContrast("dark onErrorContainer on errorContainer", scheme.onErrorContainer, scheme.errorContainer, 4.5)
        assertContrast("dark warningText on surface", colors.warningText, colors.surface, 4.5)

        // M3 stock role pairs
        assertContrast("dark outline on surface", scheme.outline, scheme.surface, 3.0)
        assertContrast("dark onSurfaceVariant on surfaceVariant", scheme.onSurfaceVariant, scheme.surfaceVariant, 4.5)
        assertContrast("dark inverseOnSurface on inverseSurface", scheme.inverseOnSurface, scheme.inverseSurface, 4.5)
        assertContrast("dark onSecondaryContainer on secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, 4.5)
    }

    // =========================================================================
    // Always-Dark Chrome Tests (palette.md §3)
    // =========================================================================

    @Test
    fun alwaysDarkChromeContrast_meetsTargets() {
        val colors = MallLightColors

        assertContrast("onScrim on scrimSurface", colors.onScrim, colors.scrimSurface, 4.5)
        assertContrast("onScrim on scrimCard", colors.onScrim, colors.scrimCard, 4.5)
        assertContrast("onScrimMuted on scrimSurface", colors.onScrimMuted, colors.scrimSurface, 4.5)
        assertContrast("onScrimMuted on scrimCard", colors.onScrimMuted, colors.scrimCard, 4.5)
    }

    // =========================================================================
    // brandTeal Large-Text Restriction Assertion
    // =========================================================================

    @Test
    fun brandTealContrast_restrictedToLargeTextOnly() {
        // brandTeal contrast against surface in light mode is in the 3.0 - 4.5 range (~4.06:1).
        // It satisfies WCAG AA for large text (>= 18.66px bold / >= 24px regular, target 3:1),
        // but fails normal body text (target 4.5:1). It is strictly restricted to large text only.
        val lightRatio = contrastRatio(MallLightColors.brandTeal, MallLightColors.surface)
        assertTrue(
            "brandTeal on surface in light mode must be in 3.0..4.5 (large text only), got $lightRatio",
            lightRatio in 3.0..4.5
        )

        // In dark mode, brandTeal is also >= 3.0:1
        val darkRatio = contrastRatio(MallDarkColors.brandTeal, MallDarkColors.surface)
        assertTrue(
            "brandTeal on surface in dark mode must be >= 3.0, got $darkRatio",
            darkRatio >= 3.0
        )
    }

    // =========================================================================
    // Decorative-Exempt Tokens (palette.md §5)
    // =========================================================================

    @Test
    fun decorativeTokens_explicitlyExempt() {
        // The following tokens are decorative or otherwise exempt under WCAG 2.1 AA.
        // They are asserted here to verify they are known and not silently omitted.

        // 1. textDisabled: disabled affordance; WCAG exempts disabled controls.
        assertNotEquals(Color.Unspecified, MallLightColors.textDisabled)
        assertNotEquals(Color.Unspecified, MallDarkColors.textDisabled)

        // 2. border: decorative card / input hairline; visible boundary role is M3 outline.
        assertNotEquals(Color.Unspecified, MallLightColors.border)
        assertNotEquals(Color.Unspecified, MallDarkColors.border)

        // 3. divider: list-row separator; not a component boundary.
        assertNotEquals(Color.Unspecified, MallLightColors.divider)
        assertNotEquals(Color.Unspecified, MallDarkColors.divider)

        // 4. hairlineOverlay: 1px inset sheen on dark cards; decorative only.
        assertEquals(Color.Transparent, MallLightColors.hairlineOverlay)
        assertEquals(Color(0x14FFFFFF), MallDarkColors.hairlineOverlay)

        // 5. imagePlaceholder / imageErrorSurface as fills: surface backdrops, not text foregrounds.
        assertNotEquals(Color.Unspecified, MallLightColors.imagePlaceholder)
        assertNotEquals(Color.Unspecified, MallDarkColors.imagePlaceholder)
        assertNotEquals(Color.Unspecified, MallLightColors.imageErrorSurface)
        assertNotEquals(Color.Unspecified, MallDarkColors.imageErrorSurface)
    }

    // =========================================================================
    // Material 3 ColorScheme — All 36 Roles Asserted Set & Matching README §5
    // =========================================================================

    @Test
    fun material3ColorScheme_all36RolesSetExplicitly_light() {
        val s = MallARLightScheme

        assertRole("primary", s.primary, Color(0xFF0A6360))
        assertRole("onPrimary", s.onPrimary, Color(0xFFFFFFFF))
        assertRole("primaryContainer", s.primaryContainer, Color(0xFF107C7A))
        assertRole("onPrimaryContainer", s.onPrimaryContainer, Color(0xFFFFFFFF))
        assertRole("inversePrimary", s.inversePrimary, Color(0xFF2FA3B8))
        assertRole("secondary", s.secondary, Color(0xFF107C7A))
        assertRole("onSecondary", s.onSecondary, Color(0xFFFFFFFF))
        assertRole("secondaryContainer", s.secondaryContainer, Color(0xFFDCEBEA))
        assertRole("onSecondaryContainer", s.onSecondaryContainer, Color(0xFF0A6360))
        assertRole("tertiary", s.tertiary, Color(0xFF0A6360))
        assertRole("onTertiary", s.onTertiary, Color(0xFFFFFFFF))
        assertRole("tertiaryContainer", s.tertiaryContainer, Color(0xFFEEF1F4))
        assertRole("onTertiaryContainer", s.onTertiaryContainer, Color(0xFF151A1E))
        assertRole("background", s.background, Color(0xFFF7F9FB))
        assertRole("onBackground", s.onBackground, Color(0xFF151A1E))
        assertRole("surface", s.surface, Color(0xFFFFFFFF))
        assertRole("onSurface", s.onSurface, Color(0xFF151A1E))
        assertRole("surfaceVariant", s.surfaceVariant, Color(0xFFEEF1F4))
        assertRole("onSurfaceVariant", s.onSurfaceVariant, Color(0xFF5A6672))
        assertRole("surfaceTint", s.surfaceTint, Color.Transparent)
        assertRole("inverseSurface", s.inverseSurface, Color(0xFF151A1E))
        assertRole("inverseOnSurface", s.inverseOnSurface, Color(0xFFF2F5F6))
        assertRole("surfaceBright", s.surfaceBright, Color(0xFFFFFFFF))
        assertRole("surfaceDim", s.surfaceDim, Color(0xFFE6EAEE))
        assertRole("surfaceContainerLowest", s.surfaceContainerLowest, Color(0xFFFFFFFF))
        assertRole("surfaceContainerLow", s.surfaceContainerLow, Color(0xFFF7F9FB))
        assertRole("surfaceContainer", s.surfaceContainer, Color(0xFFEEF1F4))
        assertRole("surfaceContainerHigh", s.surfaceContainerHigh, Color(0xFFE8ECEF))
        assertRole("surfaceContainerHighest", s.surfaceContainerHighest, Color(0xFFE2E7EC))
        assertRole("error", s.error, Color(0xFFC0362C))
        assertRole("onError", s.onError, Color(0xFFFFFFFF))
        assertRole("errorContainer", s.errorContainer, Color(0xFFF9DEDB))
        assertRole("onErrorContainer", s.onErrorContainer, Color(0xFFB0271F))
        assertRole("outline", s.outline, Color(0xFF8A939C))
        assertRole("outlineVariant", s.outlineVariant, Color(0xFFE2E7EC))
        assertRole("scrim", s.scrim, Color(0xFF000000))
    }

    @Test
    fun material3ColorScheme_all36RolesSetExplicitly_dark() {
        val s = MallARDarkScheme

        assertRole("primary", s.primary, Color(0xFF5BC9C2))
        assertRole("onPrimary", s.onPrimary, Color(0xFF06201F))
        assertRole("primaryContainer", s.primaryContainer, Color(0xFF3FB2AC))
        assertRole("onPrimaryContainer", s.onPrimaryContainer, Color(0xFF06201F))
        assertRole("inversePrimary", s.inversePrimary, Color(0xFF0A6360))
        assertRole("secondary", s.secondary, Color(0xFF3FB2AC))
        assertRole("onSecondary", s.onSecondary, Color(0xFF06201F))
        assertRole("secondaryContainer", s.secondaryContainer, Color(0xFF123B39))
        assertRole("onSecondaryContainer", s.onSecondaryContainer, Color(0xFF5BC9C2))
        assertRole("tertiary", s.tertiary, Color(0xFF5BC9C2))
        assertRole("onTertiary", s.onTertiary, Color(0xFF06201F))
        assertRole("tertiaryContainer", s.tertiaryContainer, Color(0xFF10161A))
        assertRole("onTertiaryContainer", s.onTertiaryContainer, Color(0xFFE8ECEF))
        assertRole("background", s.background, Color(0xFF0E1418))
        assertRole("onBackground", s.onBackground, Color(0xFFE8ECEF))
        assertRole("surface", s.surface, Color(0xFF161D22))
        assertRole("onSurface", s.onSurface, Color(0xFFE8ECEF))
        assertRole("surfaceVariant", s.surfaceVariant, Color(0xFF10161A))
        assertRole("onSurfaceVariant", s.onSurfaceVariant, Color(0xFF9BA8B0))
        assertRole("surfaceTint", s.surfaceTint, Color.Transparent)
        assertRole("inverseSurface", s.inverseSurface, Color(0xFFE8ECEF))
        assertRole("inverseOnSurface", s.inverseOnSurface, Color(0xFF151A1E))
        assertRole("surfaceBright", s.surfaceBright, Color(0xFF1F282E))
        assertRole("surfaceDim", s.surfaceDim, Color(0xFF0A0F12))
        assertRole("surfaceContainerLowest", s.surfaceContainerLowest, Color(0xFF0A0F12))
        assertRole("surfaceContainerLow", s.surfaceContainerLow, Color(0xFF12191E))
        assertRole("surfaceContainer", s.surfaceContainer, Color(0xFF161D22))
        assertRole("surfaceContainerHigh", s.surfaceContainerHigh, Color(0xFF1F282E))
        assertRole("surfaceContainerHighest", s.surfaceContainerHighest, Color(0xFF253038))
        assertRole("error", s.error, Color(0xFFE06A60))
        assertRole("onError", s.onError, Color(0xFF1A0E0D))
        assertRole("errorContainer", s.errorContainer, Color(0xFF5C201B))
        assertRole("onErrorContainer", s.onErrorContainer, Color(0xFFEE9089))
        assertRole("outline", s.outline, Color(0xFF6B7780))
        assertRole("outlineVariant", s.outlineVariant, Color(0xFF2A343A))
        assertRole("scrim", s.scrim, Color(0xFF000000))
    }

    private fun assertRole(roleName: String, actual: Color, expected: Color) {
        assertTrue("Role '$roleName' must be specified", actual.isSpecified)
        assertNotEquals("Role '$roleName' must not be Color.Unspecified", Color.Unspecified, actual)
        assertEquals("Role '$roleName' does not match README §5 spec", expected, actual)
    }
}
