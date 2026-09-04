package com.example.mallar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BidiFormattingTest {

    @Test
    fun isolate_wrapsValueInFsiAndPdi() {
        val isolated = BidiFormatting.isolate("Zara")
        assertEquals("\u2068Zara\u2069", isolated)
        assertTrue(isolated.startsWith(BidiFormatting.FSI))
        assertTrue(isolated.endsWith(BidiFormatting.PDI))
    }

    @Test
    fun isolate_brandAndPercentage() {
        val brand = BidiFormatting.isolate("Zara 15%")
        assertEquals("\u2068Zara 15%\u2069", brand)
    }

    @Test
    fun bidiIsolated_extensionMatchesIsolateHelper() {
        val value = "H&M"
        assertEquals(BidiFormatting.isolate(value), value.bidiIsolated())
    }
}
