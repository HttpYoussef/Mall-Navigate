package com.example.mallar.ar

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import com.example.mallar.ar.model.RouteNodeMetadata
import com.example.mallar.ar.render.FloorPlaneConfidenceMonitor
import com.example.mallar.ar.render.GuidanceVisualFactory
import com.example.mallar.data.AStarDirection
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.Node
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArAnchorRendererTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockVisualFactory = mockk<GuidanceVisualFactory>(relaxed = true)
    private val mockConfidenceMonitor = mockk<FloorPlaneConfidenceMonitor>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun existingMarkerRotationIsUpdatedOnTransformRevisionChange() {
        val config = AnchorWindowConfig()
        val renderer = ArAnchorRenderer(
            context = mockContext,
            config = config,
            planner = AnchorWindowPlanner(config),
            planeConfidenceMonitor = mockConfidenceMonitor,
            visualFactory = mockVisualFactory
        )

        // Route along Y in facility space: Node 0 at (100, 100), Node 1 at (100, 200)
        val route = listOf(
            metadata(0, 100.0, 100.0),
            metadata(1, 100.0, 200.0)
        )

        // Revision 1: Heading = 0 deg.
        // Node 0 -> world (0, 0), Node 1 -> world (0, 5) -> corridor tangent heading = 0 deg
        val transformRev1 = FacilityTransform(
            facilityX = 100.0,
            facilityY = 100.0,
            headingDeg = 0f,
            localOrigin = LocalTrackingPose(0.0, 0.0, 0f, 1000L),
            tier = FixConfidenceTier.CONFIRMED,
            acceptedAtMs = 1000L
        )

        val mockMarker = mockk<Node>(relaxed = true)
        var currentRotation = Rotation(-90f, 0f, 0f)
        var currentPosition = Position(0f, GuidanceVisualFactory.ELEVATION_STANDARD_METERS, 0f)
        every { mockMarker.rotation } answers { currentRotation }
        every { mockMarker.rotation = any() } answers { currentRotation = firstArg() }
        every { mockMarker.position } answers { currentPosition }
        every { mockMarker.position = any() } answers { currentPosition = firstArg() }

        val spec0 = AnchorSpec(route[0], AnchorKind.STANDARD, routeIndex = 0)
        val (worldX0, worldZ0) = transformRev1.worldPositionFor(route[0].x, route[0].y, config.pixelsPerMeter)

        val managedAnchor0 = ArAnchorRenderer.ManagedAnchor(
            spec = spec0,
            anchorNode = mockk<AnchorNode>(relaxed = true),
            marker = mockMarker,
            materialColor = Color(0xFF1A73E8),
            initialWorldX = worldX0,
            initialWorldZ = worldZ0,
            correction = CorrectionInterpolator(config.correctionFrames),
            lastTransformAcceptedAt = 1L
        )

        renderer.anchors[0] = managedAnchor0
        renderer.lastTransformAcceptedAt = 1L

        val initialRotation = renderer.getMarker(0)!!.rotation
        assertEquals(-90f, initialRotation.x, 0.001f)
        assertEquals(0f, initialRotation.y, 0.001f)
        assertEquals(0f, initialRotation.z, 0.001f)
        assertFalse(managedAnchor0.correction.isActive)

        // Revision 2: FacilityTransform rotated by 90 deg and shifted by (2.0, 3.0).
        // Under 90 deg heading, corridor vector from Node 0 to Node 1 points along +X, so tangent heading = 90 deg.
        val transformRev2 = FacilityTransform(
            facilityX = 100.0,
            facilityY = 100.0,
            headingDeg = 90f,
            localOrigin = LocalTrackingPose(2.0, 3.0, 0f, 2000L),
            tier = FixConfidenceTier.CONFIRMED,
            acceptedAtMs = 2000L
        )

        renderer.update(
            transform = transformRev2,
            transformRevision = 2L,
            route = route
        )

        // Verify rotation is recomputed under new transform and updated
        val updatedRotation = renderer.getMarker(0)!!.rotation
        assertEquals(-90f, updatedRotation.x, 0.001f)
        assertEquals(90f, updatedRotation.y, 0.001f)
        assertEquals(0f, updatedRotation.z, 0.001f)
        assertNotEquals("Marker rotation must not remain at stale creation heading", initialRotation.y, updatedRotation.y, 0.001f)

        // Verify position correction is initiated alongside rotation update
        assertTrue("Position correction must be initiated on transform revision change", managedAnchor0.correction.isActive)
        assertEquals(2L, managedAnchor0.lastTransformAcceptedAt)
        assertEquals(2L, renderer.lastTransformAcceptedAt)
    }

    @Test
    fun terminalMarkerHeadingIsUpdatedOnTransformRevisionChange() {
        val config = AnchorWindowConfig()
        val renderer = ArAnchorRenderer(
            context = mockContext,
            config = config,
            planner = AnchorWindowPlanner(config),
            planeConfidenceMonitor = mockConfidenceMonitor,
            visualFactory = mockVisualFactory
        )

        // Route along Y in facility space: Node 0 at (100, 100), Node 1 at (100, 200)
        val route = listOf(
            metadata(0, 100.0, 100.0),
            metadata(1, 100.0, 200.0)
        )

        val transformRev1 = FacilityTransform(
            facilityX = 100.0,
            facilityY = 100.0,
            headingDeg = 0f,
            localOrigin = LocalTrackingPose(0.0, 0.0, 0f, 1000L),
            tier = FixConfidenceTier.CONFIRMED,
            acceptedAtMs = 1000L
        )

        val mockMarker = mockk<Node>(relaxed = true)
        var currentRotation = Rotation(-90f, 0f, 0f)
        var currentPosition = Position(0f, GuidanceVisualFactory.ELEVATION_STANDARD_METERS, 0f)
        every { mockMarker.rotation } answers { currentRotation }
        every { mockMarker.rotation = any() } answers { currentRotation = firstArg() }
        every { mockMarker.position } answers { currentPosition }
        every { mockMarker.position = any() } answers { currentPosition = firstArg() }

        // Node 1 is at the end of the route (routeIndex = 1 = route.lastIndex)
        val spec1 = AnchorSpec(route[1], AnchorKind.STANDARD, routeIndex = 1)
        val (worldX1, worldZ1) = transformRev1.worldPositionFor(route[1].x, route[1].y, config.pixelsPerMeter)

        val managedAnchor1 = ArAnchorRenderer.ManagedAnchor(
            spec = spec1,
            anchorNode = mockk<AnchorNode>(relaxed = true),
            marker = mockMarker,
            materialColor = Color(0xFF1A73E8),
            initialWorldX = worldX1,
            initialWorldZ = worldZ1,
            correction = CorrectionInterpolator(config.correctionFrames),
            lastTransformAcceptedAt = 1L
        )

        renderer.anchors[1] = managedAnchor1
        renderer.lastTransformAcceptedAt = 1L

        val transformRev2 = FacilityTransform(
            facilityX = 100.0,
            facilityY = 100.0,
            headingDeg = 90f,
            localOrigin = LocalTrackingPose(2.0, 3.0, 0f, 2000L),
            tier = FixConfidenceTier.CONFIRMED,
            acceptedAtMs = 2000L
        )

        renderer.update(
            transform = transformRev2,
            transformRevision = 2L,
            route = route
        )

        // Terminal marker heading should also be updated to 90 deg using previous waypoint tangent
        val updatedRotation = renderer.getMarker(1)!!.rotation
        assertEquals(-90f, updatedRotation.x, 0.001f)
        assertEquals(90f, updatedRotation.y, 0.001f)
        assertEquals(0f, updatedRotation.z, 0.001f)
        assertTrue(managedAnchor1.correction.isActive)
    }

    @Test
    fun sameTransformRevisionDoesNotTriggerCorrectionOrUpdateRotation() {
        val config = AnchorWindowConfig()
        val renderer = ArAnchorRenderer(
            context = mockContext,
            config = config,
            planner = AnchorWindowPlanner(config),
            planeConfidenceMonitor = mockConfidenceMonitor,
            visualFactory = mockVisualFactory
        )

        val route = listOf(
            metadata(0, 100.0, 100.0),
            metadata(1, 100.0, 200.0)
        )

        val transformRev1 = FacilityTransform(
            facilityX = 100.0,
            facilityY = 100.0,
            headingDeg = 0f,
            localOrigin = LocalTrackingPose(0.0, 0.0, 0f, 1000L),
            tier = FixConfidenceTier.CONFIRMED,
            acceptedAtMs = 1000L
        )

        val mockMarker = mockk<Node>(relaxed = true)
        var currentRotation = Rotation(-90f, 0f, 0f)
        every { mockMarker.rotation } answers { currentRotation }
        every { mockMarker.rotation = any() } answers { currentRotation = firstArg() }
        every { mockMarker.position } returns Position(0f, GuidanceVisualFactory.ELEVATION_STANDARD_METERS, 0f)

        val spec0 = AnchorSpec(route[0], AnchorKind.STANDARD, routeIndex = 0)
        val (worldX0, worldZ0) = transformRev1.worldPositionFor(route[0].x, route[0].y, config.pixelsPerMeter)

        val managedAnchor0 = ArAnchorRenderer.ManagedAnchor(
            spec = spec0,
            anchorNode = mockk<AnchorNode>(relaxed = true),
            marker = mockMarker,
            materialColor = Color(0xFF1A73E8),
            initialWorldX = worldX0,
            initialWorldZ = worldZ0,
            correction = CorrectionInterpolator(config.correctionFrames),
            lastTransformAcceptedAt = 1L
        )

        renderer.anchors[0] = managedAnchor0
        renderer.lastTransformAcceptedAt = 1L

        // Update with the same revision 1L
        renderer.update(
            transform = transformRev1,
            transformRevision = 1L,
            route = route
        )

        assertEquals(0f, renderer.getMarker(0)!!.rotation.y, 0.001f)
        assertFalse("Correction must not be active when transform revision has not changed", managedAnchor0.correction.isActive)
    }

    private fun metadata(id: Int, x: Double, y: Double) = RouteNodeMetadata(
        nodeId = id,
        x = x,
        y = y,
        floor = 2,
        direction = AStarDirection.STRAIGHT,
        isDestination = false
    )
}
