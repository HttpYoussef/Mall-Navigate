package com.example.mallar.ar.render

import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.FloatBuffer

class FloorPlaneConfidenceMonitorTest {

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun resolveFloorElevation_returnsFallbackWhenNoPlanesTracked() {
        val monitor = FloorPlaneConfidenceMonitor()
        val session = mockk<Session>()
        every { session.getAllTrackables(Plane::class.java) } returns emptyList()

        val (elevation, plane) = monitor.resolveFloorElevation(session, 0f, 0f, -1.35f)

        assertEquals(-1.35f, elevation, 0.001f)
        assertNull(plane)
        assertEquals(0f, monitor.confidenceScore, 0.001f)
    }

    @Test
    fun resolveFloorElevation_returnsPlaneHeightAndUpdatesConfidence() {
        val monitor = FloorPlaneConfidenceMonitor(minConfidenceAreaM2 = 0.5f)
        val session = mockk<Session>()
        val mockPlane = mockk<Plane>()

        every { mockPlane.type } returns Plane.Type.HORIZONTAL_UPWARD_FACING
        every { mockPlane.trackingState } returns TrackingState.TRACKING
        every { mockPlane.centerPose } returns Pose.makeTranslation(1f, -1.20f, 2f)
        every { mockPlane.isPoseInPolygon(any()) } returns true

        // 1m x 1m square polygon = 1.0 m^2
        val polygonBuffer = FloatBuffer.wrap(floatArrayOf(
            0f, 0f,
            1f, 0f,
            1f, 1f,
            0f, 1f
        ))
        every { mockPlane.polygon } returns polygonBuffer
        every { session.getAllTrackables(Plane::class.java) } returns listOf(mockPlane)

        val (elevation, plane) = monitor.resolveFloorElevation(session, 1f, 2f, -1.35f)

        assertEquals(-1.20f, elevation, 0.001f)
        assertEquals(mockPlane, plane)
        assertEquals(1.0f, monitor.confidenceScore, 0.001f)

        // Now test fallback when planes are lost: rolling average should smoothly maintain near -1.20f
        every { session.getAllTrackables(Plane::class.java) } returns emptyList()
        val (fallbackElevation, fallbackPlane) = monitor.resolveFloorElevation(session, 1f, 2f, -1.35f)

        assertNull(fallbackPlane)
        assertEquals(0.0f, monitor.confidenceScore, 0.001f)
        assertEquals(-1.20f, fallbackElevation, 0.05f) // Remains close to -1.20f rather than popping to -1.35f
    }
}
