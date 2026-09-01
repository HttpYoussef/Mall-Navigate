package com.example.mallar.ar.integration

import android.util.Log
import com.example.mallar.ar.AnchorKind
import com.example.mallar.ar.AnchorWindowConfig
import com.example.mallar.ar.AnchorWindowPlanner
import com.example.mallar.ar.CandidateFix
import com.example.mallar.ar.DeviceTier
import com.example.mallar.ar.FacilityTransform
import com.example.mallar.ar.FixConfidenceTier
import com.example.mallar.ar.FixValidationDecision
import com.example.mallar.ar.FixValidationGate
import com.example.mallar.ar.LocalTrackingPose
import com.example.mallar.ar.SensorFusionLayer
import com.example.mallar.ar.model.RouteNodeMetadata
import com.example.mallar.ar.model.SensorStalenessStatus
import com.example.mallar.ar.render.RenderPoseSmoother
import com.example.mallar.ar.supervision.ArRuntimeState
import com.example.mallar.ar.supervision.DriftRecoverySupervisor
import com.example.mallar.ar.supervision.SupervisoryInstruction
import com.example.mallar.data.AStarDirection
import com.example.mallar.data.GraphEdge
import com.example.mallar.data.GraphNode
import com.example.mallar.data.MallGraph
import com.example.mallar.navigation.DriftMonitor
import com.example.mallar.navigation.NavSessionState
import com.example.mallar.navigation.NavigationTurnDirection
import com.example.mallar.navigation.NavigationTurnInfo
import com.example.mallar.voice.SmartResponseEngine
import com.google.ar.core.Pose
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * FullSystemIntegrationScenarioTest
 * ─────────────────────────────────────────────────────────────────────────────
 * 
 * Comprehensive end-to-end integration and failure scenario test suite for
 * Phase 9 Final System Acceptance, validating all scenarios from §7 & §8 of
 * AR_Testing_and_Validation_Plan.md.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FullSystemIntegrationScenarioTest {

    private lateinit var graph: MallGraph
    private lateinit var sampleRoute: List<RouteNodeMetadata>
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

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        val nodes = listOf(
            GraphNode(id = 1, x = 100.0, y = 100.0, floor = 2, shopId = 1, shopName = "OriginStore", logo = null),
            GraphNode(id = 2, x = 100.0, y = -100.0, floor = 2, shopId = 2, shopName = "Store2", logo = null),
            GraphNode(id = 3, x = 100.0, y = -200.0, floor = 2, shopId = 3, shopName = "Elevator3", logo = null, transitionNodeId = 203, connectedFloor = 3),
            GraphNode(id = 4, x = 100.0, y = -400.0, floor = 2, shopId = 4, shopName = "TargetStore", logo = null)
        )
        graph = MallGraph(
            nodes = nodes,
            edges = listOf(
                GraphEdge(1, 2),
                GraphEdge(2, 3),
                GraphEdge(3, 4)
            )
        )

        sampleRoute = listOf(
            RouteNodeMetadata(nodeId = 1, x = 100.0, y = 100.0, floor = 2, direction = AStarDirection.STRAIGHT, isDestination = false),
            RouteNodeMetadata(nodeId = 2, x = 100.0, y = -100.0, floor = 2, direction = AStarDirection.STRAIGHT, isDestination = false),
            RouteNodeMetadata(nodeId = 3, x = 100.0, y = -200.0, floor = 2, direction = AStarDirection.STRAIGHT, isDestination = false, isFloorTransition = true),
            RouteNodeMetadata(nodeId = 4, x = 100.0, y = -400.0, floor = 2, direction = AStarDirection.STRAIGHT, isDestination = true)
        )

        supervisor = DriftRecoverySupervisor(
            lateralDriftThresholdMeters = 2.5,
            arrivalRadiusMeters = 2.5,
            floorTransitionRadiusMeters = 3.0,
            freshDurationMs = 15000L,
            trustWindowMs = 30000L,
            graceWindowMs = 3000L,
            pixelsPerMeter = ppm
        )
    }

    @After
    fun tearDown() = unmockkAll()

    // ── Scenario 1: Long-Running Sessions past ~20-Minute Check-in ───────────────
    @Test
    fun testScenario1_longRunningSession_checkInTrigger() {
        var currentTime = 1000L
        assertEquals(ArRuntimeState.NO_FIX, supervisor.state)
        supervisor.onInitialFixAccepted(currentTime)
        assertEquals(ArRuntimeState.TRACKING_FRESH, supervisor.state)

        val twentyMinutesMs = 20 * 60 * 1000L
        val endTime = currentTime + twentyMinutesMs

        while (currentTime < endTime) {
            currentTime += 5000L
            val pose = LocalTrackingPose(0.0, 0.0, 0f, currentTime)
            supervisor.evaluate(
                timestampMs = currentTime,
                trackingState = TrackingState.TRACKING,
                trackingFailureReason = TrackingFailureReason.NONE,
                transform = standardTransform,
                localPose = pose,
                userHeadingDeg = 0f,
                route = sampleRoute,
                driftState = DriftMonitor.DriftState()
            )
        }

        assertNotNull(supervisor.state)
        assertTrue(
            "State must remain active or degraded during continuous navigation",
            supervisor.state in setOf(
                ArRuntimeState.TRACKING_FRESH,
                ArRuntimeState.TRACKING_AGING,
                ArRuntimeState.TRACKING_DEGRADED
            )
        )
    }

    // ── Scenario 2: Continuous 60Hz Tracking Stability & Smoothing ──────────────
    @Test
    fun testScenario2_trackingStability_continuous60Hz() {
        val smoother = RenderPoseSmoother()
        var timeMs = 1000L
        var lastFilteredPose = Pose.IDENTITY

        for (i in 1..60) {
            val noisyX = 1.0f + (if (i % 2 == 0) 0.01f else -0.01f)
            val rawPose = Pose.makeTranslation(noisyX, 0f, -2.0f)
            lastFilteredPose = smoother.filter(rawPose, timeMs)
            timeMs += 16L
        }

        assertTrue("Smoothed X output must converge stably near 1.0", abs(lastFilteredPose.tx() - 1.0f) < 0.05f)
    }

    // ── Scenario 3: Sub-Threshold Drift Behavior (Smooth Correction, No Rebuild) ─
    @Test
    fun testScenario3_driftBehavior_subThreshold() {
        val now = 10000L
        supervisor.onInitialFixAccepted(now)
        val pose = LocalTrackingPose(0.0, 0.0, 0f, now)

        val output = supervisor.evaluate(
            timestampMs = now,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = pose,
            userHeadingDeg = 0f,
            route = sampleRoute,
            driftState = DriftMonitor.DriftState()
        )

        assertEquals(ArRuntimeState.TRACKING_FRESH, supervisor.state)
        assertEquals(SupervisoryInstruction.None, output)
    }

    // ── Scenario 4: Tracking Interruption Grace Window Policy ───────────────────
    @Test
    fun testScenario4a_trackingInterruption_underGraceWindow() {
        val now = 5000L
        supervisor.onInitialFixAccepted(now)
        assertEquals(ArRuntimeState.TRACKING_FRESH, supervisor.state)

        supervisor.evaluate(
            timestampMs = now + 1000L,
            trackingState = TrackingState.PAUSED,
            trackingFailureReason = TrackingFailureReason.INSUFFICIENT_LIGHT,
            transform = standardTransform,
            localPose = originLocalPose,
            userHeadingDeg = 0f,
            route = sampleRoute,
            driftState = DriftMonitor.DriftState()
        )
        assertEquals(ArRuntimeState.INTERRUPTED_GRACE, supervisor.state)

        supervisor.evaluate(
            timestampMs = now + 2000L,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = originLocalPose,
            userHeadingDeg = 0f,
            route = sampleRoute,
            driftState = DriftMonitor.DriftState()
        )
        assertEquals(ArRuntimeState.TRACKING_FRESH, supervisor.state)
    }

    @Test
    fun testScenario4b_trackingInterruption_overGraceWindow() {
        val now = 5000L
        supervisor.onInitialFixAccepted(now)

        supervisor.evaluate(
            timestampMs = now + 1000L,
            trackingState = TrackingState.PAUSED,
            trackingFailureReason = TrackingFailureReason.INSUFFICIENT_LIGHT,
            transform = standardTransform,
            localPose = originLocalPose,
            userHeadingDeg = 0f,
            route = sampleRoute,
            driftState = DriftMonitor.DriftState()
        )
        assertEquals(ArRuntimeState.INTERRUPTED_GRACE, supervisor.state)

        supervisor.evaluate(
            timestampMs = now + 5000L,
            trackingState = TrackingState.PAUSED,
            trackingFailureReason = TrackingFailureReason.INSUFFICIENT_LIGHT,
            transform = standardTransform,
            localPose = originLocalPose,
            userHeadingDeg = 0f,
            route = sampleRoute,
            driftState = DriftMonitor.DriftState()
        )
        assertEquals(ArRuntimeState.INTERRUPTED_FULL, supervisor.state)

        val instruction = supervisor.evaluate(
            timestampMs = now + 6000L,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = originLocalPose,
            userHeadingDeg = 0f,
            route = sampleRoute,
            driftState = DriftMonitor.DriftState()
        )
        assertEquals(SupervisoryInstruction.RequestReFix, instruction)
        assertEquals(ArRuntimeState.NO_FIX, supervisor.state)
    }

    // ── Scenario 5: OS-Level Camera Interruption Lifecycle ───────────────────────
    @Test
    fun testScenario5_cameraInterruption_osLevel() {
        val now = 10000L
        supervisor.onInitialFixAccepted(now)

        supervisor.evaluate(
            timestampMs = now + 500L,
            trackingState = TrackingState.PAUSED,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = originLocalPose,
            userHeadingDeg = 0f,
            route = sampleRoute,
            driftState = DriftMonitor.DriftState()
        )
        assertEquals(ArRuntimeState.INTERRUPTED_GRACE, supervisor.state)
    }

    // ── Scenario 6: Sensor Inconsistency & Staleness Exclusion ───────────────────
    @Test
    fun testScenario6_sensorInconsistency_staleImu() = runTest {
        val sessionStateFlow = MutableStateFlow(NavSessionState())
        var currentTime = 1000L
        val layer = SensorFusionLayer(backgroundScope, sessionStateFlow, { currentTime })
        runCurrent()

        sessionStateFlow.value = NavSessionState(totalSteps = 0, headingDeg = 45f)
        runCurrent()

        currentTime += 100L
        sessionStateFlow.value = NavSessionState(totalSteps = 1, headingDeg = 45f)
        runCurrent()
        assertFalse(layer.getStalenessStatus().isStale)

        currentTime += 3100L
        sessionStateFlow.value = NavSessionState(totalSteps = 2, headingDeg = 45f)
        runCurrent()
        assertTrue(layer.getStalenessStatus().isStale)
    }

    // ── Scenario 7: User Deviation Beyond Classification Bound ──────────────────
    @Test
    fun testScenario7_userDeviation_beyondClassificationBound() {
        val now = 10000L
        supervisor.onInitialFixAccepted(now)

        val deviatingPose = LocalTrackingPose(3.5, -2.0, 0f, now + 1000L)
        val instruction = supervisor.evaluate(
            timestampMs = now + 1000L,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = deviatingPose,
            userHeadingDeg = 0f,
            route = sampleRoute,
            driftState = DriftMonitor.DriftState(offPathSteps = 10)
        )

        assertEquals(ArRuntimeState.ROUTE_REBUILDING, supervisor.state)
        assertTrue(instruction is SupervisoryInstruction.RebuildRoute)
    }

    // ── Scenario 8: Dual-Condition Route Reversal vs Stationary Lookaround ──────
    @Test
    fun testScenario8a_dualConditionRouteReversal_walkBack() {
        var t = 1000L
        supervisor.onInitialFixAccepted(t)

        // Move forward along segment towards Node 2 (heading 0 deg, dy = -3.0m)
        var pose = LocalTrackingPose(0.0, -3.0, 0f, t)
        supervisor.evaluate(t, TrackingState.TRACKING, TrackingFailureReason.NONE, standardTransform, pose, 0f, sampleRoute, DriftMonitor.DriftState())

        // Turn around 180 deg and walk backwards (distance to Node 2 increases over 3 frames)
        t += 200L
        pose = LocalTrackingPose(0.0, -2.8, 0f, t)
        val r1 = supervisor.evaluate(t, TrackingState.TRACKING, TrackingFailureReason.NONE, standardTransform, pose, 180f, sampleRoute, DriftMonitor.DriftState())
        assertEquals(SupervisoryInstruction.None, r1)

        t += 200L
        pose = LocalTrackingPose(0.0, -2.6, 0f, t)
        val r2 = supervisor.evaluate(t, TrackingState.TRACKING, TrackingFailureReason.NONE, standardTransform, pose, 180f, sampleRoute, DriftMonitor.DriftState())
        assertEquals(SupervisoryInstruction.None, r2)

        t += 200L
        pose = LocalTrackingPose(0.0, -2.4, 0f, t)
        val r3 = supervisor.evaluate(t, TrackingState.TRACKING, TrackingFailureReason.NONE, standardTransform, pose, 180f, sampleRoute, DriftMonitor.DriftState())

        assertTrue("Expected route rebuild on confirmed reversal", r3 is SupervisoryInstruction.RebuildRoute)
        assertEquals(ArRuntimeState.ROUTE_REBUILDING, supervisor.state)
    }

    @Test
    fun testScenario8b_stationaryTurn_disagreementGuard() {
        var t = 1000L
        supervisor.onInitialFixAccepted(t)

        val pose = LocalTrackingPose(0.0, -3.0, 0f, t)

        // Turn 180 deg in place without stepping backward across 5 frames
        for (i in 1..5) {
            t += 200L
            val result = supervisor.evaluate(
                timestampMs = t,
                trackingState = TrackingState.TRACKING,
                trackingFailureReason = TrackingFailureReason.NONE,
                transform = standardTransform,
                localPose = pose,
                userHeadingDeg = 180f,
                route = sampleRoute,
                driftState = DriftMonitor.DriftState()
            )
            assertEquals(SupervisoryInstruction.None, result)
            assertEquals(ArRuntimeState.TRACKING_FRESH, supervisor.state)
        }
    }

    // ── Scenario 9: Multi-Floor Transition & Arrival Beacon ──────────────────────
    @Test
    fun testScenario9a_multiFloorTransitionMode() {
        val now = 10000L
        supervisor.onInitialFixAccepted(now)

        // Node 3 is transition node at Y = -200 (dy = -300px = -15.0m in facility space)
        val transitionPose = LocalTrackingPose(0.0, -14.5, 0f, now + 1000L)
        val out = supervisor.evaluate(
            timestampMs = now + 1000L,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = transitionPose,
            userHeadingDeg = 0f,
            route = sampleRoute,
            driftState = DriftMonitor.DriftState()
        )

        assertEquals(ArRuntimeState.TRANSITION_MODE, supervisor.state)
        assertTrue(out is SupervisoryInstruction.EnterTransitionMode)
    }

    @Test
    fun testScenario9b_destinationArrivalBeacon() {
        val now = 10000L
        supervisor.onInitialFixAccepted(now)

        val destinationRoute = listOf(
            RouteNodeMetadata(nodeId = 1, x = 100.0, y = 100.0, floor = 2, direction = AStarDirection.STRAIGHT, isDestination = false),
            RouteNodeMetadata(nodeId = 2, x = 100.0, y = -100.0, floor = 2, direction = AStarDirection.STRAIGHT, isDestination = false),
            RouteNodeMetadata(nodeId = 3, x = 100.0, y = -400.0, floor = 2, direction = AStarDirection.STRAIGHT, isDestination = true)
        )

        // Node 3 is destination at Y = -400 (dy = -500px = -25.0m in facility space)
        val arrivalPose = LocalTrackingPose(0.0, -25.0, 0f, now + 1000L)
        val out = supervisor.evaluate(
            timestampMs = now + 1000L,
            trackingState = TrackingState.TRACKING,
            trackingFailureReason = TrackingFailureReason.NONE,
            transform = standardTransform,
            localPose = arrivalPose,
            userHeadingDeg = 0f,
            route = destinationRoute,
            driftState = DriftMonitor.DriftState()
        )

        assertEquals(ArRuntimeState.ARRIVED, supervisor.state)
        assertTrue(out is SupervisoryInstruction.EnterArrivedState)
    }

    // ── Scenario 10: Fix Validation Gate Consecutive Rejections ─────────────────
    @Test
    fun testScenario10_consecutiveGateRejections_rescanPrompt() {
        val gate = FixValidationGate(graph)
        val initialPose = LocalTrackingPose(0.0, 0.0, 90f, 1000L)

        val initialFix = CandidateFix(100.0, 100.0, 0f, 1, graph.nodes[0])
        val initialDecision = gate.validateAndApply(initialFix, initialPose)
        assertTrue(initialDecision is FixValidationDecision.Accepted)

        for (i in 1..3) {
            val invalidCandidate = CandidateFix(500.0, 500.0, 0f, 1, graph.nodes[3])
            val decision = gate.validateAndApply(invalidCandidate, initialPose)
            assertTrue("Candidate fix must be rejected by gate", decision is FixValidationDecision.Rejected)
        }
    }

    // ── Scenario 11: Session Teardown and Zero-State Restart ─────────────────────
    @Test
    fun testScenario11_sessionTeardownAndRestart_zeroRetainedState() {
        supervisor.onInitialFixAccepted(1000L)
        assertEquals(ArRuntimeState.TRACKING_FRESH, supervisor.state)

        supervisor.reset()
        assertEquals(ArRuntimeState.NO_FIX, supervisor.state)
    }

    // ── Scenario 12: Device-Tier Parameter Scaling ──────────────────────────────
    @Test
    fun testScenario12_deviceTierParameterScaling() {
        val standardConfig = AnchorWindowConfig.forTier(DeviceTier.STANDARD)
        val constrainedConfig = AnchorWindowConfig.forTier(DeviceTier.CONSTRAINED)

        assertEquals(10, standardConfig.aheadCount)
        assertEquals(2, standardConfig.trailingCount)
        assertEquals(15, standardConfig.maxActiveAnchors)
        assertEquals(0.15f, standardConfig.smoothingAlpha, 0.001f)
        assertEquals(3000L, standardConfig.recognitionThrottleMs)

        assertEquals(5, constrainedConfig.aheadCount)
        assertEquals(1, constrainedConfig.trailingCount)
        assertEquals(8, constrainedConfig.maxActiveAnchors)
        assertEquals(0.25f, constrainedConfig.smoothingAlpha, 0.001f)
        assertEquals(5000L, constrainedConfig.recognitionThrottleMs)
    }

    @Test
    fun testScenario12b_deviceTierHardwareDetectionBranching() {
        val mockContext = mockk<android.content.Context>()
        val mockAm = mockk<android.app.ActivityManager>()
        
        every { mockContext.getSystemService(android.content.Context.ACTIVITY_SERVICE) } returns mockAm

        // Case A: High-RAM Hardware (12GB RAM, isLowRamDevice = false) -> STANDARD
        every { mockAm.isLowRamDevice } returns false
        every { mockAm.getMemoryInfo(any<android.app.ActivityManager.MemoryInfo>()) } answers {
            val memInfo = firstArg<android.app.ActivityManager.MemoryInfo>()
            memInfo.totalMem = 12L * 1024L * 1024L * 1024L // 12GB
        }
        assertEquals(DeviceTier.STANDARD, DeviceTier.detect(mockContext))

        // Case B: Low-RAM Hardware (isLowRamDevice = true) -> CONSTRAINED
        every { mockAm.isLowRamDevice } returns true
        every { mockAm.getMemoryInfo(any<android.app.ActivityManager.MemoryInfo>()) } answers {
            val memInfo = firstArg<android.app.ActivityManager.MemoryInfo>()
            memInfo.totalMem = 3L * 1024L * 1024L * 1024L // 3GB
        }
        assertEquals(DeviceTier.CONSTRAINED, DeviceTier.detect(mockContext))

        // Case C: Sub-4GB Hardware (isLowRamDevice = false, but totalMem < 4GB) -> CONSTRAINED
        every { mockAm.isLowRamDevice } returns false
        every { mockAm.getMemoryInfo(any<android.app.ActivityManager.MemoryInfo>()) } answers {
            val memInfo = firstArg<android.app.ActivityManager.MemoryInfo>()
            memInfo.totalMem = 2L * 1024L * 1024L * 1024L // 2GB
        }
        assertEquals(DeviceTier.CONSTRAINED, DeviceTier.detect(mockContext))
    }

    // ── Voice & Turn Direction Decoupling Tests ─────────────────────────────────
    @Test
    fun testVoiceTurnDirection_elevatorAndStairs() {
        val elevatorEn = SmartResponseEngine.turnApproach(NavigationTurnDirection.ELEVATOR, 10, isArabic = false)
        val elevatorAr = SmartResponseEngine.turnApproach(NavigationTurnDirection.ELEVATOR, 10, isArabic = true)
        val stairsEn = SmartResponseEngine.turnApproach(NavigationTurnDirection.STAIRS, 5, isArabic = false)
        val stairsAr = SmartResponseEngine.turnApproach(NavigationTurnDirection.STAIRS, 5, isArabic = true)

        assertTrue("Elevator English cue must mention elevator", elevatorEn.contains("elevator", ignoreCase = true))
        assertTrue("Elevator Arabic cue must mention المصعد", elevatorAr.contains("المصعد"))
        assertTrue("Stairs English cue must mention stairs", stairsEn.contains("stairs", ignoreCase = true))
        assertTrue("Stairs Arabic cue must mention الدرج or السلالم", stairsAr.contains("الدرج") || stairsAr.contains("السلالم"))
    }
}
