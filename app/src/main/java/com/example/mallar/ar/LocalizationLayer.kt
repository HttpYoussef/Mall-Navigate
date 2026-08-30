package com.example.mallar.ar

import android.util.Log
import com.example.mallar.data.GraphNode
import com.example.mallar.data.LocalizationResult
import com.example.mallar.data.MallGraph
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.navigation.NavConfig
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin

/** Confidence tier used by Module 4's Fix Validation Gate. */
enum class FixConfidenceTier {
    PROVISIONAL,
    CONFIRMED;

    companion object {
        fun fromLandmarkCount(count: Int): FixConfidenceTier =
            if (count >= 2) CONFIRMED else PROVISIONAL
    }
}

/** A frame-independent local pose used by the localization layer and its tests. */
data class LocalTrackingPose(
    val xMeters: Double,
    val yMeters: Double,
    val headingDeg: Float,
    val timestampMs: Long
)

/** The sole Module 4-owned mapping between AR local space and facility coordinates. */
data class FacilityTransform(
    val facilityX: Double,
    val facilityY: Double,
    val headingDeg: Float,
    val localOrigin: LocalTrackingPose,
    val tier: FixConfidenceTier,
    val acceptedAtMs: Long
) {
    fun facilityPosition(localPose: LocalTrackingPose, pixelsPerMeter: Double = NavConfig.PIXELS_PER_METER.toDouble()): Pair<Double, Double> {
        val dx = localPose.xMeters - localOrigin.xMeters
        val dy = localPose.yMeters - localOrigin.yMeters
        val heading = Math.toRadians(headingDeg.toDouble())
        val mapDx = (dx * cos(heading) - dy * sin(heading)) * pixelsPerMeter
        val mapDy = (dx * sin(heading) + dy * cos(heading)) * pixelsPerMeter
        return facilityX + mapDx to facilityY + mapDy
    }
}

data class CandidateFix(
    val facilityX: Double?,
    val facilityY: Double?,
    val headingDeg: Float?,
    val landmarkCount: Int,
    val bestStartNode: GraphNode?
) {
    val tier: FixConfidenceTier
        get() = FixConfidenceTier.fromLandmarkCount(landmarkCount)

    companion object {
        fun from(result: LocalizationResult): CandidateFix = CandidateFix(
            facilityX = result.estimatedMapX,
            facilityY = result.estimatedMapY,
            headingDeg = result.estimatedHeadingDeg,
            landmarkCount = result.landmarkCount,
            bestStartNode = result.bestStartNode
        )
    }
}

enum class FixRejectionReason {
    MISSING_POSITION,
    GRAPH_IMPLAUSIBLE,
    DISPLACEMENT_IMPLAUSIBLE,
    TIER_TOLERANCE_EXCEEDED
}

sealed class FixValidationDecision {
    data class Accepted(val transform: FacilityTransform) : FixValidationDecision()
    data class Rejected(val reason: FixRejectionReason) : FixValidationDecision()
}

/**
 * Validates candidate fixes before they can modify the facility transform.
 * All state mutation is synchronized and the gate is intentionally independent
 * of AR rendering, so Phase 5 can be verified without anchors.
 */
class FixValidationGate(
    private val graph: MallGraph,
    private val pixelsPerMeter: Double = NavConfig.PIXELS_PER_METER.toDouble(),
    private val maxImpliedSpeedMps: Double = 4.0,
    private val graphTolerancePx: Double = NavConfig.GLOBAL_MATCH_THRESHOLD_PX * 1.5,
    private val provisionalTolerancePx: Double = NavConfig.GLOBAL_MATCH_THRESHOLD_PX * 1.5,
    private val confirmedTolerancePx: Double = NavConfig.GLOBAL_MATCH_THRESHOLD_PX
) {
    companion object {
        private const val TAG = "FixValidationGate"
    }

    @Volatile
    var currentTransform: FacilityTransform? = null
        private set

    @Synchronized
    fun validateAndApply(
        candidate: CandidateFix,
        localPose: LocalTrackingPose,
        nowMs: Long = localPose.timestampMs
    ): FixValidationDecision {
        val x = candidate.facilityX
        val y = candidate.facilityY
        if (x == null || y == null || !x.isFinite() || !y.isFinite()) {
            return reject(FixRejectionReason.MISSING_POSITION)
        }

        val nearest = MallGraphRepository.findNearestNode(graph, x, y)
        if (nearest == null || hypot(nearest.x - x, nearest.y - y) > graphTolerancePx) {
            return reject(FixRejectionReason.GRAPH_IMPLAUSIBLE)
        }
        candidate.bestStartNode?.let { candidateNode ->
            if (graph.nodes.none { it.id == candidateNode.id }) {
                return reject(FixRejectionReason.GRAPH_IMPLAUSIBLE)
            }
        }

        val previous = currentTransform
        if (previous != null) {
            val elapsedSec = ((nowMs - previous.acceptedAtMs).coerceAtLeast(1L)) / 1000.0
            val displacementM = hypot(x - previous.facilityX, y - previous.facilityY) / pixelsPerMeter
            if (displacementM / elapsedSec > maxImpliedSpeedMps) {
                return reject(FixRejectionReason.DISPLACEMENT_IMPLAUSIBLE)
            }
        }

        val tierTolerance = if (candidate.tier == FixConfidenceTier.PROVISIONAL) {
            provisionalTolerancePx
        } else {
            confirmedTolerancePx
        }
        if (hypot(nearest.x - x, nearest.y - y) > tierTolerance) {
            return reject(FixRejectionReason.TIER_TOLERANCE_EXCEEDED)
        }

        val transform = FacilityTransform(
            facilityX = x,
            facilityY = y,
            headingDeg = candidate.headingDeg ?: previous?.headingDeg ?: localPose.headingDeg,
            localOrigin = localPose,
            tier = candidate.tier,
            acceptedAtMs = nowMs
        )
        currentTransform = transform
        Log.d(TAG, "Accepted ${candidate.tier} fix at ($x,$y), landmarks=${candidate.landmarkCount}")
        return FixValidationDecision.Accepted(transform)
    }

    @Synchronized
    fun reset() {
        currentTransform = null
    }

    @Synchronized
    internal fun seed(transform: FacilityTransform) {
        currentTransform = transform
    }

    private fun reject(reason: FixRejectionReason): FixValidationDecision.Rejected {
        Log.d(TAG, "Rejected candidate fix: $reason")
        return FixValidationDecision.Rejected(reason)
    }
}

/** Proximity/throttle gate with a single-flight guard for asynchronous recognition. */
class ReFixScheduler(
    private val landmarkNodes: List<GraphNode>,
    private val radiusMeters: Double = 6.0,
    private val pixelsPerMeter: Double = NavConfig.PIXELS_PER_METER.toDouble(),
    private val throttleMs: Long = 4_000L
) {
    private val inFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var lastAttemptMs = Long.MIN_VALUE

    fun isNearLandmark(facilityX: Double, facilityY: Double): Boolean = landmarkNodes.any { node ->
        hypot(node.x - facilityX, node.y - facilityY) / pixelsPerMeter <= radiusMeters
    }

    fun tryStart(facilityX: Double, facilityY: Double, nowMs: Long): Boolean {
        if (!isNearLandmark(facilityX, facilityY)) return false
        if (lastAttemptMs != Long.MIN_VALUE && nowMs - lastAttemptMs < throttleMs) return false
        if (!inFlight.compareAndSet(false, true)) return false
        lastAttemptMs = nowMs
        return true
    }

    fun finish() {
        inFlight.set(false)
    }

    fun reset() {
        lastAttemptMs = Long.MIN_VALUE
        inFlight.set(false)
    }
}

/**
 * Module 4 state holder. It owns the transform and is the only component that
 * can publish an accepted correction. Recognition/camera plumbing calls
 * [beginPeriodicRefix] and [completePeriodicRefix] from its slow cycle.
 */
class LocalizationLayer(
    private val graph: MallGraph,
    landmarkNodes: List<GraphNode> = graph.nodes.filter { it.shopName != null },
    private val gate: FixValidationGate = FixValidationGate(graph),
    private val scheduler: ReFixScheduler = ReFixScheduler(landmarkNodes)
) {
    companion object {
        private const val TAG = "LocalizationLayer"
        private const val PROXIMITY_CHECK_INTERVAL_MS = 1_000L
    }

    private var consecutiveRejections = 0
    private var lastProximityCheckMs = Long.MIN_VALUE
    private var revision = 0L

    val transform: FacilityTransform?
        get() = gate.currentTransform

    val rejectionCount: Int
        get() = consecutiveRejections

    /** Monotonic accepted-transform revision consumed at the Module 6 frame boundary. */
    val transformRevision: Long
        get() = revision

    /** Seeds the transform from the already-accepted pre-navigation scan fix. */
    @Synchronized
    fun initializeFromScan(
        startNode: GraphNode,
        initialHeadingDeg: Float?,
        localPose: LocalTrackingPose,
        nowMs: Long = localPose.timestampMs
    ) {
        gate.seed(
            FacilityTransform(
                facilityX = startNode.x,
                facilityY = startNode.y,
                headingDeg = initialHeadingDeg ?: localPose.headingDeg,
                localOrigin = localPose,
                tier = FixConfidenceTier.CONFIRMED,
                acceptedAtMs = nowMs
            )
        )
        revision++
        consecutiveRejections = 0
        scheduler.reset()
        lastProximityCheckMs = Long.MIN_VALUE
        Log.d(TAG, "Initial scan fix consumed at node ${startNode.id}")
    }

    fun beginPeriodicRefix(nowMs: Long, localPose: LocalTrackingPose): Boolean {
        val current = transform ?: return false
        if (lastProximityCheckMs != Long.MIN_VALUE &&
            nowMs - lastProximityCheckMs < PROXIMITY_CHECK_INTERVAL_MS
        ) return false
        lastProximityCheckMs = nowMs
        val (x, y) = current.facilityPosition(localPose)
        return scheduler.tryStart(x, y, nowMs)
    }

    /** Releases a camera-copy/recognition attempt that could not produce a result. */
    fun cancelPeriodicRefix() {
        scheduler.finish()
    }

    @Synchronized
    fun completePeriodicRefix(
        candidate: CandidateFix,
        localPose: LocalTrackingPose,
        nowMs: Long = localPose.timestampMs
    ): FixValidationDecision {
        return try {
            gate.validateAndApply(candidate, localPose, nowMs).also { decision ->
                if (decision is FixValidationDecision.Accepted) {
                    revision++
                    consecutiveRejections = 0
                } else {
                    consecutiveRejections++
                }
            }
        } finally {
            scheduler.finish()
        }
    }

    fun reset() {
        synchronized(this) {
            gate.reset()
            scheduler.reset()
            consecutiveRejections = 0
            lastProximityCheckMs = Long.MIN_VALUE
            revision = 0L
        }
    }

}
