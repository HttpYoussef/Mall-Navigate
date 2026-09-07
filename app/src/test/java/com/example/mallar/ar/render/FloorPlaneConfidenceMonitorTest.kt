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

    @Test
    fun resolveFloorElevation_rejectsFarPlaneWhenOutsideDistanceThreshold() {
        val monitor = FloorPlaneConfidenceMonitor(maxFallbackDistanceMeters = 5.0f)
        val session = mockk<Session>()
        val farPlane = mockk<Plane>()

        every { farPlane.type } returns Plane.Type.HORIZONTAL_UPWARD_FACING
        every { farPlane.trackingState } returns TrackingState.TRACKING
        // Plane center is at (10.0, -1.10, 10.0), distance from (0, 0) is ~14.14m > 5.0m
        every { farPlane.centerPose } returns Pose.makeTranslation(10f, -1.10f, 10f)
        every { farPlane.isPoseInPolygon(any()) } returns false
        every { session.getAllTrackables(Plane::class.java) } returns listOf(farPlane)

        val (elevation, plane) = monitor.resolveFloorElevation(session, 0f, 0f, -1.35f)

        // Far plane should be rejected; falls back to fallbackElevation and returns null plane
        assertEquals(-1.35f, elevation, 0.001f)
        assertNull(plane)
        assertEquals(0f, monitor.confidenceScore, 0.001f)
    }

    @Test
    fun resolveFloorElevation_acceptsNearestPlaneWithinDistanceThreshold() {
        val monitor = FloorPlaneConfidenceMonitor(maxFallbackDistanceMeters = 5.0f, minConfidenceAreaM2 = 0.5f)
        val session = mockk<Session>()
        val nearPlane = mockk<Plane>()

        every { nearPlane.type } returns Plane.Type.HORIZONTAL_UPWARD_FACING
        every { nearPlane.trackingState } returns TrackingState.TRACKING
        // Plane center is at (2.0, -1.20, 2.0), distance from (0, 0) is sqrt(8) ~ 2.83m <= 5.0m
        every { nearPlane.centerPose } returns Pose.makeTranslation(2f, -1.20f, 2f)
        every { nearPlane.isPoseInPolygon(any()) } returns false

        val polygonBuffer = FloatBuffer.wrap(floatArrayOf(
            0f, 0f,
            1f, 0f,
            1f, 1f,
            0f, 1f
        ))
        every { nearPlane.polygon } returns polygonBuffer
        every { session.getAllTrackables(Plane::class.java) } returns listOf(nearPlane)

        val (elevation, plane) = monitor.resolveFloorElevation(session, 0f, 0f, -1.35f)

        assertEquals(-1.20f, elevation, 0.001f)
        assertEquals(nearPlane, plane)
        assertEquals(1.0f, monitor.confidenceScore, 0.001f)
    }

    @Test
    fun resolveFloorElevation_returnsDampedElevationInsteadOfRawPlaneY() {
        val monitor = FloorPlaneConfidenceMonitor(elevationDampingAlpha = 0.10f, minConfidenceAreaM2 = 0.5f)
        val session = mockk<Session>()
        val mockPlane = mockk<Plane>()

        every { mockPlane.type } returns Plane.Type.HORIZONTAL_UPWARD_FACING
        every { mockPlane.trackingState } returns TrackingState.TRACKING
        every { mockPlane.isPoseInPolygon(any()) } returns true
        val polygonBuffer = FloatBuffer.wrap(floatArrayOf(
            0f, 0f,
            1f, 0f,
            1f, 1f,
            0f, 1f
        ))
        every { mockPlane.polygon } returns polygonBuffer

        // Frame 1: plane elevation is -1.00m. Initializes rollingFloorElevation = -1.00m.
        every { mockPlane.centerPose } returns Pose.makeTranslation(0f, -1.00f, 0f)
        every { session.getAllTrackables(Plane::class.java) } returns listOf(mockPlane)
        val (firstElevation, _) = monitor.resolveFloorElevation(session, 0f, 0f, -1.35f)
        assertEquals(-1.00f, firstElevation, 0.001f)

        // Frame 2: plane elevation jumps abruptly to -2.00m.
        // Exponential smoothing with alpha = 0.10:
        // expected = -1.00 * (1 - 0.10) + (-2.00) * 0.10 = -0.90 - 0.20 = -1.10m.
        // If raw planeY were returned, elevation would be -2.00m.
        every { mockPlane.centerPose } returns Pose.makeTranslation(0f, -2.00f, 0f)
        val (secondElevation, returnedPlane) = monitor.resolveFloorElevation(session, 0f, 0f, -1.35f)

        assertEquals(-1.10f, secondElevation, 0.001f)
        assertEquals(mockPlane, returnedPlane)
    }
}
