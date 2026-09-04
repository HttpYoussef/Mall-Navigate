package com.example.mallar.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class TimestampsTest {

    private lateinit var originalLocale: Locale
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        originalTimeZone = TimeZone.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun format_matchesExpectedRegexAndAsciiDigits() {
        val result = Timestamps.format(System.currentTimeMillis())
        val regex = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$""")
        assertTrue("Expected pattern yyyy-MM-dd HH:mm but was $result", regex.matches(result))
        assertTrue("Expected only ASCII characters", result.all { it.code < 128 })
    }

    @Test
    fun format_underArabicLocale_retainsWesternDigits() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        val result = Timestamps.format(System.currentTimeMillis())
        val regex = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$""")
        assertTrue("Expected pattern yyyy-MM-dd HH:mm under Arabic locale but was $result", regex.matches(result))
        assertTrue("Expected only ASCII digits and delimiters", result.all { it in '0'..'9' || it == '-' || it == ' ' || it == ':' })
    }

    @Test
    fun format_deterministicEpochInUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val result = Timestamps.format(0L)
        assertEquals("1970-01-01 00:00", result)
    }
}
