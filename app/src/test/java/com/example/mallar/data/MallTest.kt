package com.example.mallar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MallTest {

    @Test
    fun citystars_hasNavigationData_isTrue() {
        assertTrue(
            "CITY_STARS must have navigation data",
            Mall.CITY_STARS.hasNavigationData
        )
    }

    @Test
    fun citycentreAlmaza_hasNavigationData_isFalse() {
        assertFalse(
            "CITY_CENTRE_ALMAZA must not have navigation data yet",
            Mall.CITY_CENTRE_ALMAZA.hasNavigationData
        )
    }

    @Test
    fun mallOfEgypt_hasNavigationData_isFalse() {
        assertFalse(
            "MALL_OF_EGYPT must not have navigation data yet",
            Mall.MALL_OF_EGYPT.hasNavigationData
        )
    }

    @Test
    fun entries_hasExactlyThreeMalls_inCorrectOrder() {
        val entries = Mall.entries
        assertEquals("Mall.entries must have exactly 3 entries", 3, entries.size)
        assertEquals(Mall.CITY_STARS, entries[0])
        assertEquals(Mall.CITY_CENTRE_ALMAZA, entries[1])
        assertEquals(Mall.MALL_OF_EGYPT, entries[2])
    }
}
