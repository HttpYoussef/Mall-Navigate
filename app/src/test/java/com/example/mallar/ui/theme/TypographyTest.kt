package com.example.mallar.ui.theme

import androidx.compose.ui.text.font.FontFamily
import com.example.mallar.data.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TypographyTest {

    @Test
    fun fontFamilyFor_arabic_returnsCairoFontFamily() {
        assertEquals(CairoFontFamily, fontFamilyFor(AppLanguage.ARABIC))
    }

    @Test
    fun fontFamilyFor_english_returnsSansSerif() {
        assertEquals(FontFamily.SansSerif, fontFamilyFor(AppLanguage.ENGLISH))
    }

    @Test
    fun fontFamilyFor_otherLanguages_returnsSansSerif() {
        assertEquals(FontFamily.SansSerif, fontFamilyFor(AppLanguage.SPANISH))
        assertEquals(FontFamily.SansSerif, fontFamilyFor(AppLanguage.FRENCH))
    }

    @Test
    fun typographyFor_sansSerif_returnsStaticTypographyInstance() {
        assertSame(Typography, typographyFor(FontFamily.SansSerif))
    }

    @Test
    fun typographyFor_arabic_swapsFontFamilyPreservingStyleProperties() {
        val arabicTypography = typographyFor(CairoFontFamily)

        assertEquals(CairoFontFamily, arabicTypography.headlineLarge.fontFamily)
        assertEquals(Typography.headlineLarge.fontSize, arabicTypography.headlineLarge.fontSize)
        assertEquals(Typography.headlineLarge.fontWeight, arabicTypography.headlineLarge.fontWeight)
        assertEquals(Typography.headlineLarge.lineHeight, arabicTypography.headlineLarge.lineHeight)

        assertEquals(CairoFontFamily, arabicTypography.bodyLarge.fontFamily)
        assertEquals(Typography.bodyLarge.fontSize, arabicTypography.bodyLarge.fontSize)
        assertEquals(Typography.bodyLarge.fontWeight, arabicTypography.bodyLarge.fontWeight)
        assertEquals(Typography.bodyLarge.lineHeight, arabicTypography.bodyLarge.lineHeight)
        assertEquals(Typography.bodyLarge.letterSpacing, arabicTypography.bodyLarge.letterSpacing)

        assertEquals(CairoFontFamily, arabicTypography.labelMedium.fontFamily)
        assertEquals(Typography.labelMedium.fontSize, arabicTypography.labelMedium.fontSize)
        assertEquals(Typography.labelMedium.fontWeight, arabicTypography.labelMedium.fontWeight)
        assertEquals(Typography.labelMedium.lineHeight, arabicTypography.labelMedium.lineHeight)
        assertEquals(Typography.labelMedium.letterSpacing, arabicTypography.labelMedium.letterSpacing)
    }

    @Test
    fun typographyFor_languageOverload() {
        assertSame(Typography, typographyFor(AppLanguage.ENGLISH))
        assertEquals(CairoFontFamily, typographyFor(AppLanguage.ARABIC).bodyLarge.fontFamily)
    }
}

