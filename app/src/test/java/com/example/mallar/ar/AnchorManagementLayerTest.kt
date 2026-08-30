package com.example.mallar.ar

import com.example.mallar.data.AStarDirection
import com.example.mallar.ar.model.RouteNodeMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class AnchorManagementLayerTest {

    @Test
    fun windowKeepsConfiguredAheadAndTrailingBounds() {
        val route = route(30)
        val plan = AnchorWindowPlanner().plan(route, 12.0, 0.0)

        assertEquals(13, plan.active.size)
        assertEquals((10..22).toSet(), plan.active.map { it.routeIndex }.toSet())
        assertTrue(plan.active.size <= AnchorWindowConfig().maxActiveAnchors)
    }

    @Test
    fun shortRouteNeverCreatesMoreNodesThanExist() {
        val plan = AnchorWindowPlanner().plan(route(4), 0.0, 0.0)
        assertEquals(4, plan.active.size)
    }

    @Test
    fun turnMarkerUsesTheDefinedAngleThreshold() {
        val route = listOf(
            metadata(0, 0.0, 0.0),
            metadata(1, 1.0, 0.0),
            metadata(2, 0.5, sin(Math.toRadians(120.0)))
        )
        val planner = AnchorWindowPlanner()
        assertEquals(AnchorKind.TURN, planner.markerKind(route, 1))
    }

    @Test
    fun shallowBendRemainsStandardMarker() {
        val route = listOf(
            metadata(0, 0.0, 0.0),
            metadata(1, 1.0, 0.0),
            metadata(2, 2.0, 0.5)
        )
        assertEquals(AnchorKind.STANDARD, AnchorWindowPlanner().markerKind(route, 1))
    }

    @Test
    fun facilityOffsetRoundTripsAtZeroHeading() {
        val transform = FacilityTransform(
            facilityX = 100.0,
            facilityY = 200.0,
            headingDeg = 0f,
            localOrigin = LocalTrackingPose(0.0, 0.0, 0f, 0L),
            tier = FixConfidenceTier.CONFIRMED,
            acceptedAtMs = 1L
        )
        val offset = transform.localOffsetFor(104.48, 195.52, LocalTrackingPose(0.0, 0.0, 0f, 1L))
        assertEquals(1.0, offset.xMeters, 0.0001)
        assertEquals(-1.0, offset.zMeters, 0.0001)
    }

    @Test
    fun worldPositionIsInvariantAndMatchesExpectedTranslation() {
        val transform = FacilityTransform(
            facilityX = 100.0,
            facilityY = 200.0,
            headingDeg = 0f,
            localOrigin = LocalTrackingPose(5.0, 10.0, 0f, 0L),
            tier = FixConfidenceTier.CONFIRMED,
            acceptedAtMs = 1L
        )
        val (worldX, worldZ) = transform.worldPositionFor(104.48, 195.52, 4.48)
        assertEquals(6.0f, worldX, 0.001f)
        assertEquals(9.0f, worldZ, 0.001f)
    }

    @Test
    fun correctionIsMultiFrameAndMonotonic() {
        val interpolation = CorrectionInterpolator(8)
        interpolation.begin(LocalAnchorOffset(0.0, 0.0, 0.0), LocalAnchorOffset(8.0, 0.0, 0.0))

        val values = (1..8).map { interpolation.step().xMeters }
        assertEquals(8, values.size)
        assertTrue(values.zipWithNext().all { (a, b) -> b > a })
        assertEquals(8.0, values.last(), 0.0001)
    }

    @Test
    fun randomizedRoutesNeverExceedWindowBound() {
        val random = Random(42)
        repeat(500) { iteration ->
            val size = random.nextInt(1, 80)
            val route = (0 until size).map { index ->
                metadata(index, index * 1.0, if (index % 7 == 0) random.nextDouble() else 0.0)
            }
            val plan = AnchorWindowPlanner().plan(route, random.nextDouble(0.0, size.toDouble()), 0.0)
            assertTrue("iteration=$iteration", plan.active.size <= AnchorWindowConfig().maxActiveAnchors)
            assertEquals(plan.active.map { it.node.nodeId }.toSet().size, plan.active.size)
        }
    }

    private fun route(size: Int): List<RouteNodeMetadata> =
        (0 until size).map { metadata(it, it.toDouble(), 0.0) }

    private fun metadata(id: Int, x: Double, y: Double) = RouteNodeMetadata(
        nodeId = id,
        x = x,
        y = y,
        floor = 2,
        direction = AStarDirection.STRAIGHT,
        isDestination = false
    )
}
