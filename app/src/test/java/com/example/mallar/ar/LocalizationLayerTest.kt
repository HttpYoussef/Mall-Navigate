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
}
