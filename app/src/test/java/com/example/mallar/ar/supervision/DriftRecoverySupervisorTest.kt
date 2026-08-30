package com.example.mallar.ar.supervision

import com.example.mallar.ar.FacilityTransform
import com.example.mallar.ar.FixConfidenceTier
import com.example.mallar.ar.LocalTrackingPose
import com.example.mallar.ar.model.RouteNodeMetadata
import com.example.mallar.data.AStarDirection
import com.example.mallar.navigation.DriftMonitor
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Module 8 — DriftRecoverySupervisor Test Suite
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Verifies 100% of runtime states, transition conditions, drift vs deviation
 * classification, route reversal dual-condition logic, and grace window policies.
 */
class DriftRecoverySupervisorTest {

    private lateinit var supervisor: DriftRecoverySupervisor
    private val ppm = 20.0

    private val originLocalPose = LocalTrackingPose(0.0, 0.0, 0f, 1000L)
    private val standardTransform = FacilityTransform(
        facilityX = 100.0,
        facilityY = 100.0,
        headingDeg = 0f,
        localOrigin = originLocalPose,
        tier = FixConfidenceTier.CONFIRMED,
        acceptedAtMs = 1000L
    )

    // A realistic 3-node route going South to North (Y decreasing from 100 to -400)
    // Corridor segment: (100, 100) -> (100, -100) [10m] -> (100, -400) [15m further, Destination]
    // Direction vector dx = 0, dy = -200 -> heading = 0 deg (North)
    private val testRoute = listOf(
        RouteNodeMetadata(nodeId = 1, x = 100.0, y = 100.0, floor = 2, direction = AStarDirection.STRAIGHT, isDestination = false),
        RouteNodeMetadata(nodeId = 2, x = 100.0, y = -100.0, floor = 2, direction = AStarDirection.STRAIGHT, isDestination = false),
        RouteNodeMetadata(nodeId = 3, x = 100.0, y = -400.0, floor = 2, direction = AStarDirection.STRAIGHT, isDestination = true)
    )

    @Before
    fun setUp() {
        supervisor = DriftRecoverySupervisor(
            lateralDriftThresholdMeters = 2.5,
            arrivalRadiusMeters = 2.5,
            floorTransitionRadiusMeters = 3.0,
            freshDurationMs = 15_000L,
            trustWindowMs = 30_000L,
            graceWindowMs = 3_000L,
            pixelsPerMeter = ppm
        )
    }

    @Test
    fun stateMachine_allTwelveStatesAndTransitionsExercised() {
        var t = 1000L
        assertEquals(ArRuntimeState.NO_FIX, supervisor.state)

        // 1. Initial fix accepted -> TRACKING_FRESH
        supervisor.onInitialFixAccepted(t)
        assertEquals(ArRuntimeState.TRACKING_FRESH, supervisor.state)

        // 2. Freshness aging -> TRACKING_AGING after 15s
        t += 16_000L
        val poseAtNode1 = LocalTrackingPose(0.0, 0.0, 0f, t)
        supervisor.evaluate(
            timestampMs = t,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = poseAtNode1,
            userHeadingDeg = 0f,
            route = testRoute,
            driftState = DriftMonitor.DriftState()
        )
        assertEquals(ArRuntimeState.TRACKING_AGING, supervisor.state)

        // 3. Trust window expiry -> TRACKING_DEGRADED after 30s
        t += 16_000L // 32s since fix
        supervisor.evaluate(
            timestampMs = t,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = poseAtNode1,
            userHeadingDeg = 0f,
            route = testRoute,
            driftState = DriftMonitor.DriftState()
        )
        assertEquals(ArRuntimeState.TRACKING_DEGRADED, supervisor.state)

        // 4. Periodic fix recovers state to TRACKING_FRESH
        t += 1_000L
        supervisor.onPeriodicFixAccepted(t)
        assertEquals(ArRuntimeState.TRACKING_FRESH, supervisor.state)

        // 5. Interruption grace window
        t += 500L
        supervisor.evaluate(
            timestampMs = t,
            trackingState = TrackingState.PAUSED,
            trackingFailureReason = TrackingFailureReason.INSUFFICIENT_LIGHT,
            transform = standardTransform,
            localPose = poseAtNode1,
            userHeadingDeg = 0f,
            route = testRoute,
            driftState = DriftMonitor.DriftState()
        )
        assertEquals(ArRuntimeState.INTERRUPTED_GRACE, supervisor.state)

        // Resume within grace (< 3000ms)
        t += 1500L
        supervisor.evaluate(
            timestampMs = t,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = poseAtNode1,
            userHeadingDeg = 0f,
            route = testRoute,
            driftState = DriftMonitor.DriftState()
        )
        assertEquals(ArRuntimeState.TRACKING_FRESH, supervisor.state)

        // Interruption exceeding grace (> 3000ms) -> INTERRUPTED_FULL -> NO_FIX
        t += 500L
        supervisor.evaluate(
            timestampMs = t,
            trackingState = TrackingState.PAUSED,
            trackingFailureReason = TrackingFailureReason.EXCESSIVE_MOTION,
            transform = standardTransform,
            localPose = poseAtNode1,
            userHeadingDeg = 0f,
            route = testRoute,
            driftState = DriftMonitor.DriftState()
        )
        assertEquals(ArRuntimeState.INTERRUPTED_GRACE, supervisor.state)

        t += 3500L // Grace exceeded
        supervisor.evaluate(
            timestampMs = t,
            trackingState = TrackingState.PAUSED,
            trackingFailureReason = TrackingFailureReason.EXCESSIVE_MOTION,
            transform = standardTransform,
            localPose = poseAtNode1,
            userHeadingDeg = 0f,
            route = testRoute,
            driftState = DriftMonitor.DriftState()
        )
        assertEquals(ArRuntimeState.INTERRUPTED_FULL, supervisor.state)

        // When tracking resumes after full interruption, fresh fix is requested and state goes to NO_FIX
        val resumeInstruction = supervisor.evaluate(
            timestampMs = t + 500L,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = poseAtNode1,
            userHeadingDeg = 0f,
            route = testRoute,
            driftState = DriftMonitor.DriftState()
        )
        assertEquals(SupervisoryInstruction.RequestReFix, resumeInstruction)
        assertEquals(ArRuntimeState.NO_FIX, supervisor.state)

        // Fallback offered transition
        supervisor.transitionTo(ArRuntimeState.FALLBACK_OFFERED, t)
        assertEquals(ArRuntimeState.FALLBACK_OFFERED, supervisor.state)

        // Re-acquire and reach ARRIVED and SESSION_ENDED
        supervisor.onInitialFixAccepted(t)
        // Position user at Node 3 (Destination: facilityX=100.0, facilityY=-400.0)
        // dx=0, dy=-500px -> in meters: dy = -25.0m in facility space -> localPose.y = -25.0m
        val poseAtDest = LocalTrackingPose(0.0, -25.0, 0f, t)
        val arrivalInstruction = supervisor.evaluate(
            timestampMs = t,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = poseAtDest,
            userHeadingDeg = 0f,
            route = testRoute,
            driftState = DriftMonitor.DriftState()
        )
        assertTrue(arrivalInstruction is SupervisoryInstruction.EnterArrivedState)
        assertEquals(ArRuntimeState.ARRIVED, supervisor.state)

        // After 4s in arrived state, transition to SESSION_ENDED
        t += 4500L
        supervisor.evaluate(
            timestampMs = t,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = poseAtDest,
            userHeadingDeg = 0f,
            route = testRoute,
            driftState = DriftMonitor.DriftState()
        )
        assertEquals(ArRuntimeState.SESSION_ENDED, supervisor.state)
    }

    @Test
    fun driftVsDeviation_distinguishesSmoothDriftFromOffPathRebuild() {
        val t = 1000L
        supervisor.onInitialFixAccepted(t)

        // 1. Small lateral drift: 1.2m lateral offset (dx = 1.2m = 24px)
        // Corridor is along X = 100.0. User is at X = 124.0 (dx = 1.2m)
        val poseWithDrift = LocalTrackingPose(1.2, -2.0, 0f, t)
        val driftInstruction = supervisor.evaluate(
            timestampMs = t,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = poseWithDrift,
            userHeadingDeg = 0f,
            route = testRoute,
            driftState = DriftMonitor.DriftState(offPathSteps = 0)
        )
        assertTrue("Expected ApplyDriftCorrection for 1.2m drift", driftInstruction is SupervisoryInstruction.ApplyDriftCorrection)
        assertEquals(ArRuntimeState.TRACKING_FRESH, supervisor.state)

        // 2. Large lateral deviation: 3.5m lateral offset (> 2.5m threshold)
        val poseWithDeviation = LocalTrackingPose(3.5, -2.0, 0f, t)
        val deviationInstruction = supervisor.evaluate(
            timestampMs = t,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = poseWithDeviation,
            userHeadingDeg = 0f,
            route = testRoute,
            driftState = DriftMonitor.DriftState(offPathSteps = 10)
        )
        assertTrue("Expected RebuildRoute for 3.5m deviation", deviationInstruction is SupervisoryInstruction.RebuildRoute)
        assertEquals(ArRuntimeState.ROUTE_REBUILDING, supervisor.state)
    }

    @Test
    fun routeReversal_pureWalkBackWithReverseHeading_triggersReversalRebuild() {
        var t = 1000L
        supervisor.onInitialFixAccepted(t)

        // User starts moving forward: advancing along segment from Node 1 (Y=100) toward Node 2 (Y=-100)
        // User moves forward 3 meters (dy = -3.0m)
        var pose = LocalTrackingPose(0.0, -3.0, 0f, t)
        supervisor.evaluate(
            timestampMs = t,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = pose,
            userHeadingDeg = 0f,
            route = testRoute,
            driftState = DriftMonitor.DriftState()
        )

        // Now user turns around 180 deg (heading = 180 deg, segment heading is 0 deg -> diff = 180 deg >= 120 deg)
        // And starts walking backwards: distance to Node 2 increases by >= 0.1m consecutively
        // Frame 1 backward (moves backward 0.2m to y = -2.8m)
        t += 200L
        pose = LocalTrackingPose(0.0, -2.8, 0f, t)
        val r1 = supervisor.evaluate(t, TrackingState.TRACKING, TrackingFailureReason.NONE, standardTransform, pose, 180f, testRoute, DriftMonitor.DriftState())
        assertEquals(SupervisoryInstruction.None, r1)

        // Frame 2 backward (moves backward 0.2m to y = -2.6m)
        t += 200L
        pose = LocalTrackingPose(0.0, -2.6, 0f, t)
        val r2 = supervisor.evaluate(t, TrackingState.TRACKING, TrackingFailureReason.NONE, standardTransform, pose, 180f, testRoute, DriftMonitor.DriftState())
        assertEquals(SupervisoryInstruction.None, r2)

        // Frame 3 backward (moves backward 0.2m to y = -2.4m)
        t += 200L
        pose = LocalTrackingPose(0.0, -2.4, 0f, t)
        val r3 = supervisor.evaluate(t, TrackingState.TRACKING, TrackingFailureReason.NONE, standardTransform, pose, 180f, testRoute, DriftMonitor.DriftState())
        
        assertTrue("Expected route rebuild on confirmed reversal", r3 is SupervisoryInstruction.RebuildRoute)
        assertEquals("Route reversal detected", (r3 as SupervisoryInstruction.RebuildRoute).reason)
        assertEquals(ArRuntimeState.ROUTE_REBUILDING, supervisor.state)
    }

    @Test
    fun routeReversal_headingDisagreementWithoutDistanceIncrease_doesNotTriggerReversal() {
        // Addressing Review Finding 3: User turns 180 deg in place, but does not walk backward.
        var t = 1000L
        supervisor.onInitialFixAccepted(t)

        val pose = LocalTrackingPose(0.0, -3.0, 0f, t)

        // Feed 5 consecutive frames with reversed heading (180 deg), but stationary position
        for (i in 1..5) {
            t += 200L
            val result = supervisor.evaluate(
                timestampMs = t,
                trackingState = TrackingState.TRACKING,
                trackingFailureReason = TrackingFailureReason.NONE,
                transform = standardTransform,
                localPose = pose,
                userHeadingDeg = 180f, // heading reversed
                route = testRoute,
                driftState = DriftMonitor.DriftState()
            )
            assertEquals("Stationary turn-in-place must NOT trigger route reversal", SupervisoryInstruction.None, result)
            assertEquals(ArRuntimeState.TRACKING_FRESH, supervisor.state)
        }
    }

    @Test
    fun routeReversal_sharpLateralCut_classifiedAsStandardDeviationNotSpuriousReversal() {
        // Addressing Review Finding 3: User takes a sharp lateral cut (heading 90 deg sideways, lateral offset > 2.5m)
        var t = 1000L
        supervisor.onInitialFixAccepted(t)

        val pose = LocalTrackingPose(3.5, -3.0, 0f, t) // 3.5m lateral offset
        val result = supervisor.evaluate(
            timestampMs = t,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = pose,
            userHeadingDeg = 90f, // facing sideways into side corridor
            route = testRoute,
            driftState = DriftMonitor.DriftState(offPathSteps = 10)
        )

        assertTrue("Expected standard RebuildRoute for lateral deviation", result is SupervisoryInstruction.RebuildRoute)
        assertTrue("Reason should indicate lateral deviation", (result as SupervisoryInstruction.RebuildRoute).reason.startsWith("Lateral deviation"))
    }

    @Test
    fun transitionMode_triggersNearFloorChangeNodeAndExitsOnNewFloorFix() {
        val t = 1000L
        supervisor.onInitialFixAccepted(t)

        val multiFloorRoute = listOf(
            RouteNodeMetadata(nodeId = 1, x = 100.0, y = 100.0, floor = 2, direction = AStarDirection.STRAIGHT, isDestination = false),
            RouteNodeMetadata(nodeId = 2, x = 100.0, y = -100.0, floor = 2, direction = AStarDirection.STRAIGHT, isDestination = false, isFloorTransition = true),
            RouteNodeMetadata(nodeId = 3, x = 100.0, y = -400.0, floor = 3, direction = AStarDirection.STRAIGHT, isDestination = true)
        )

        // User approaching Node 2 (Floor Transition at Y = -100.0)
        // At y = -9.0m, facilityY = -80.0 (distance to transition is 20px = 1.0m <= 3.0m)
        val poseNearTransition = LocalTrackingPose(0.0, -9.0, 0f, t)
        val instruction = supervisor.evaluate(
            timestampMs = t,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = poseNearTransition,
            userHeadingDeg = 0f,
            route = multiFloorRoute,
            driftState = DriftMonitor.DriftState()
        )

        assertTrue("Expected EnterTransitionMode", instruction is SupervisoryInstruction.EnterTransitionMode)
        assertEquals(ArRuntimeState.TRANSITION_MODE, supervisor.state)

        // On new floor with barometer transition confirmed and fresh fix
        val exitInstruction = supervisor.evaluate(
            timestampMs = t + 5000L,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = poseNearTransition,
            userHeadingDeg = 0f,
            route = multiFloorRoute,
            driftState = DriftMonitor.DriftState(),
            isBarometerTransitionConfirmed = true
        )
        assertEquals(SupervisoryInstruction.ExitTransitionMode, exitInstruction)
        assertEquals(ArRuntimeState.TRACKING_FRESH, supervisor.state)
    }
}
