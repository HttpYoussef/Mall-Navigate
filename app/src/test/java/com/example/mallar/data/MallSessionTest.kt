package com.example.mallar.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MallSessionTest {

    /**
     * MallSession is a process-global object with intentionally no reset().
     * To isolate tests, restore the initial null state before each one by
     * setting the private `_selected` MutableStateFlow back to null via reflection.
     */
    @Before
    fun resetSession() {
        // Access the private MutableStateFlow via reflection to isolate tests.
        val field = MallSession::class.java.getDeclaredField("_selected")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(MallSession) as kotlinx.coroutines.flow.MutableStateFlow<Mall?>
        flow.value = null
    }

    @Test
    fun initialSelected_isNull() = runTest {
        assertNull(
            "MallSession.selected must start as null on a fresh launch",
            MallSession.selected.first()
        )
    }

    @Test
    fun select_citystars_emitsCityStars() = runTest {
        MallSession.select(Mall.CITY_STARS)
        assertEquals(
            "After select(CITY_STARS), selected must emit CITY_STARS",
            Mall.CITY_STARS,
            MallSession.selected.value
        )
    }

    @Test
    fun select_twice_replacesValue() = runTest {
        MallSession.select(Mall.CITY_STARS)
        MallSession.select(Mall.MALL_OF_EGYPT)
        assertEquals(
            "Selecting a second mall must replace the first",
            Mall.MALL_OF_EGYPT,
            MallSession.selected.value
        )
    }
}
