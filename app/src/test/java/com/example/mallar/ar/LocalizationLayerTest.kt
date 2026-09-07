package com.example.mallar.ar

import android.util.Log
import com.example.mallar.data.GraphEdge
import com.example.mallar.data.GraphNode
import com.example.mallar.data.MallGraph
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class LocalizationLayerTest {
    private val graph = MallGraph(
        nodes = listOf(
            GraphNode(1, 0.0, 0.0, 2, null, "A", null),
            GraphNode(2, 40.0, 0.0, 2, null, "B", null),
            GraphNode(3, 80.0, 0.0, 2, null, "C", null)
        ),
        edges = listOf(GraphEdge(1, 2), GraphEdge(2, 3))
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `single landmark fix is provisional and multi landmark fix is confirmed`() {
        val gate = FixValidationGate(graph)
        val pose = LocalTrackingPose(0.0, 0.0, 90f, 1_000L)

        val provisional = gate.validateAndApply(
            CandidateFix(40.0, 0.0, null, 1, graph.nodes[1]), pose
        )
        assertTrue(provisional is FixValidationDecision.Accepted)
        assertEquals(FixConfidenceTier.PROVISIONAL, (provisional as FixValidationDecision.Accepted).transform.tier)

        val confirmed = gate.validateAndApply(
            CandidateFix(80.0, 0.0, 90f, 2, graph.nodes[2]),
            pose.copy(timestampMs = 20_000L),
            20_000L
        )
        assertTrue(confirmed is FixValidationDecision.Accepted)
        assertEquals(FixConfidenceTier.CONFIRMED, (confirmed as FixValidationDecision.Accepted).transform.tier)
    }

    @Test
    fun `implausible displacement is rejected without changing transform`() {
        val gate = FixValidationGate(graph)
        val first = gate.validateAndApply(
            CandidateFix(0.0, 0.0, 0f, 2, graph.nodes[0]),
            LocalTrackingPose(0.0, 0.0, 0f, 1_000L)
        )
        assertTrue(first is FixValidationDecision.Accepted)

        val rejected = gate.validateAndApply(
            CandidateFix(80.0, 0.0, 0f, 2, graph.nodes[2]),
            LocalTrackingPose(0.0, 0.0, 0f, 1_100L),
            1_100L
        )
        assertEquals(
            FixRejectionReason.DISPLACEMENT_IMPLAUSIBLE,
            (rejected as FixValidationDecision.Rejected).reason
        )
        assertEquals(0.0, gate.currentTransform?.facilityX)
    }

    @Test
    fun `scheduler enforces proximity throttle and single flight`() {
        val scheduler = ReFixScheduler(listOf(graph.nodes[0]))
        assertFalse(scheduler.tryStart(200.0, 200.0, 0L))
        assertTrue(scheduler.tryStart(0.0, 0.0, 1_000L))
        assertFalse(scheduler.tryStart(0.0, 0.0, 1_001L))
        scheduler.finish()
        assertFalse(scheduler.tryStart(0.0, 0.0, 4_999L))
        assertTrue(scheduler.tryStart(0.0, 0.0, 5_000L))
    }

    @Test
    fun `scheduler stress allows one concurrent attempt`() {
        val scheduler = ReFixScheduler(listOf(graph.nodes[0]))
        val pool = Executors.newFixedThreadPool(8)
        val latch = CountDownLatch(8)
        var successes = 0
        val lock = Any()
        repeat(8) {
            pool.execute {
                if (scheduler.tryStart(0.0, 0.0, 1_000L)) {
                    synchronized(lock) { successes++ }
                }
                latch.countDown()
            }
        }
        latch.await()
        pool.shutdownNow()
        assertEquals(1, successes)
    }

    @Test
    fun `accepted transform rebases local movement into facility coordinates`() {
        val gate = FixValidationGate(graph)
        val layer = LocalizationLayer(graph, listOf(graph.nodes[0]), gate, ReFixScheduler(listOf(graph.nodes[0])))
        layer.initializeFromScan(
            startNode = graph.nodes[0],
            initialHeadingDeg = 0f,
            localPose = LocalTrackingPose(0.0, 0.0, 0f, 0L)
        )
        val position = layer.transform!!.facilityPosition(
            LocalTrackingPose(1.0, 0.0, 0f, 1_000L)
        )
        assertEquals(4.48, position.first, 0.001)
        assertEquals(0.0, position.second, 0.001)
    }

    @Test
    fun `fresh candidate heading subtracts local tracking pose yaw`() {
        val gate = FixValidationGate(graph)
        val pose = LocalTrackingPose(0.0, 0.0, 35f, 1_000L)

        // Candidate compass heading = 120f, ARCore camera yaw = 35f
        // Reconciled transform heading = 120f - 35f = 85f
        val decision = gate.validateAndApply(
            CandidateFix(40.0, 0.0, 120f, 2, graph.nodes[1]),
            pose
        )
        assertTrue(decision is FixValidationDecision.Accepted)
        val transform = (decision as FixValidationDecision.Accepted).transform
        assertEquals(85f, transform.headingDeg, 0.001f)
    }

    @Test
    fun `fallback to previous heading does not subtract local pose yaw again`() {
        val gate = FixValidationGate(graph)
        val pose1 = LocalTrackingPose(0.0, 0.0, 30f, 1_000L)

        // First fix: candidate heading = 100f, pose yaw = 30f => transform heading = 70f
        val firstDecision = gate.validateAndApply(
            CandidateFix(0.0, 0.0, 100f, 2, graph.nodes[0]),
            pose1
        )
        assertTrue(firstDecision is FixValidationDecision.Accepted)
        assertEquals(70f, (firstDecision as FixValidationDecision.Accepted).transform.headingDeg, 0.001f)

        // Second fix: candidate heading is null, pose yaw has changed to 50f
        // Must reuse previous reconciled heading (70f), NOT 70f - 50f = 20f
        val pose2 = LocalTrackingPose(0.0, 0.0, 50f, 2_000L)
        val secondDecision = gate.validateAndApply(
            CandidateFix(0.0, 0.0, null, 2, graph.nodes[0]),
            pose2,
            2_000L
        )
        assertTrue(secondDecision is FixValidationDecision.Accepted)
        val secondTransform = (secondDecision as FixValidationDecision.Accepted).transform
        assertEquals(70f, secondTransform.headingDeg, 0.001f)
    }

    @Test
    fun `initializeFromScan classifies tier dynamically from landmark count and reconciles heading`() {
        val gate1 = FixValidationGate(graph)
        val layer1 = LocalizationLayer(graph, listOf(graph.nodes[0]), gate1, ReFixScheduler(listOf(graph.nodes[0])))

        // 1-landmark scan should result in PROVISIONAL tier and subtract yaw from heading
        layer1.initializeFromScan(
            startNode = graph.nodes[0],
            initialHeadingDeg = 150f,
            localPose = LocalTrackingPose(0.0, 0.0, 40f, 1_000L),
            landmarkCount = 1
        )
        val transform1 = layer1.transform
        assertTrue(transform1 != null)
        assertEquals(FixConfidenceTier.PROVISIONAL, transform1!!.tier)
        assertEquals(110f, transform1.headingDeg, 0.001f)

        val gate2 = FixValidationGate(graph)
        val layer2 = LocalizationLayer(graph, listOf(graph.nodes[0]), gate2, ReFixScheduler(listOf(graph.nodes[0])))

        // 2-landmark scan should result in CONFIRMED tier and subtract yaw from heading
        layer2.initializeFromScan(
            startNode = graph.nodes[0],
            initialHeadingDeg = 90f,
            localPose = LocalTrackingPose(0.0, 0.0, 25f, 2_000L),
            landmarkCount = 2
        )
        val transform2 = layer2.transform
        assertTrue(transform2 != null)
        assertEquals(FixConfidenceTier.CONFIRMED, transform2!!.tier)
        assertEquals(65f, transform2.headingDeg, 0.001f)

        // Null initialHeadingDeg falls back to localPose.headingDeg without subtraction
        val gate3 = FixValidationGate(graph)
        val layer3 = LocalizationLayer(graph, listOf(graph.nodes[0]), gate3, ReFixScheduler(listOf(graph.nodes[0])))
        layer3.initializeFromScan(
            startNode = graph.nodes[0],
            initialHeadingDeg = null,
            localPose = LocalTrackingPose(0.0, 0.0, 30f, 3_000L),
            landmarkCount = 3
        )
        val transform3 = layer3.transform
        assertTrue(transform3 != null)
        assertEquals(FixConfidenceTier.CONFIRMED, transform3!!.tier)
        assertEquals(30f, transform3.headingDeg, 0.001f)
    }
}
