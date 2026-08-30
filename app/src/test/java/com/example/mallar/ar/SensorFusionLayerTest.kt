package com.example.mallar.ar

import android.util.Log
import com.example.mallar.navigation.NavSessionState
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

@OptIn(ExperimentalCoroutinesApi::class)
class SensorFusionLayerTest {

    private val sessionStateFlow = MutableStateFlow(NavSessionState())

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `PDR Accumulation - single step north - produces correct displacement`() = runTest {
        val layer = SensorFusionLayer(backgroundScope, sessionStateFlow)
        runCurrent()
        
        sessionStateFlow.value = NavSessionState(totalSteps = 1, headingDeg = 0f)
        runCurrent()

        val signal = layer.getCorroborationSignal()
        assertEquals(0.0, signal.dx, 0.001)
        assertEquals(-0.75, signal.dy, 0.001)
    }

    @Test
    fun `PDR Accumulation - multiple steps - sums displacement correctly`() = runTest {
        val layer = SensorFusionLayer(backgroundScope, sessionStateFlow)
        runCurrent()

        sessionStateFlow.value = NavSessionState(totalSteps = 1, headingDeg = 0f)
        runCurrent()

        sessionStateFlow.value = NavSessionState(totalSteps = 2, headingDeg = 90f)
        runCurrent()

        val signal = layer.getCorroborationSignal()
        assertEquals(0.75, signal.dx, 0.001)
        assertEquals(-0.75, signal.dy, 0.001)
    }

    @Test
    fun `getCorroborationSignal - resets accumulator on read`() = runTest {
        val layer = SensorFusionLayer(backgroundScope, sessionStateFlow)
        runCurrent()

        sessionStateFlow.value = NavSessionState(totalSteps = 1, headingDeg = 0f)
        runCurrent()

        layer.getCorroborationSignal()
        
        val secondRead = layer.getCorroborationSignal()
        assertEquals(0.0, secondRead.dx, 0.0)
        assertEquals(0.0, secondRead.dy, 0.0)
    }

    @Test
    fun `Staleness Heuristic - frozen heading during motion - flags staleness`() = runTest {
        var currentTime = 1000L
        val layer = SensorFusionLayer(backgroundScope, sessionStateFlow, { currentTime })
        runCurrent()

        sessionStateFlow.value = NavSessionState(totalSteps = 0, headingDeg = 45f)
        runCurrent()
        
        currentTime += 100
        sessionStateFlow.value = NavSessionState(totalSteps = 1, headingDeg = 45f)
        runCurrent()
        assertFalse("Should not be stale after 100ms", layer.getStalenessStatus().isStale)

        currentTime += 3000
        sessionStateFlow.value = NavSessionState(totalSteps = 2, headingDeg = 45f)
        runCurrent()
        assertTrue("Should be stale after 3100ms without heading change during motion", layer.getStalenessStatus().isStale)
        
        currentTime += 100
        sessionStateFlow.value = NavSessionState(totalSteps = 3, headingDeg = 90f)
        runCurrent()
        assertFalse("Should recover from staleness after heading change", layer.getStalenessStatus().isStale)
    }

    @Test
    fun `Static Analysis - verify no new sensor listeners`() {
        val layer = SensorFusionLayer(TestScope(), sessionStateFlow)
        assertFalse(layer is android.hardware.SensorEventListener)
    }

    @Test
    fun `Drift Verification - 10 steps north - matches expected within 5 percent`() = runTest {
        val layer = SensorFusionLayer(backgroundScope, sessionStateFlow)
        runCurrent()

        val steps = 10
        val heading = 0f
        val expectedDist = steps * 0.75

        for (i in 1..steps) {
            sessionStateFlow.value = NavSessionState(totalSteps = i.toLong(), headingDeg = heading)
            runCurrent()
        }

        val total = layer.getFallbackDisplacement()
        val actualDist = sqrt((total.dx * total.dx) + (total.dy * total.dy))
        
        val margin = 0.05 * expectedDist
        assertTrue("Distance $actualDist not within 5% of $expectedDist", abs(actualDist - expectedDist) <= margin)
    }
}
