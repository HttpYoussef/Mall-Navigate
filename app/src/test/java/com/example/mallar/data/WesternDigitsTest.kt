package com.example.mallar.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class WesternDigitsTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun format_int_outputsWesternDigitsUnderDefaultLocale() {
        assertEquals("2", WesternDigits.format(2))
        assertEquals("1234", WesternDigits.format(1234))
        assertEquals("0", WesternDigits.format(0))
        assertEquals("-15", WesternDigits.format(-15))
    }

    @Test
    fun format_int_outputsWesternDigitsUnderArabicLocale() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        assertEquals("2", WesternDigits.format(2))
        assertEquals("1234", WesternDigits.format(1234))
        assertEquals("0", WesternDigits.format(0))
        assertEquals("-50", WesternDigits.format(-50))
    }

    @Test
    fun format_long_outputsWesternDigitsUnderArabicLocale() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        assertEquals("1234", WesternDigits.format(1234L))
        assertEquals("9876543210", WesternDigits.format(9876543210L))
    }

    @Test
    fun format_double_outputsWesternDigitsAndPeriodUnderArabicLocale() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        assertEquals("3", WesternDigits.format(3.14159, decimals = 0))
        assertEquals("3.14", WesternDigits.format(3.14159, decimals = 2))
        assertEquals("10.5", WesternDigits.format(10.5, decimals = 1))
    }

    @Test
    fun percent_outputsWesternDigitsAndLiteralPercentUnderArabicLocale() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        assertEquals("45%", WesternDigits.percent(45))
        assertEquals("0%", WesternDigits.percent(0))
        assertEquals("100%", WesternDigits.percent(100))
    }
}
