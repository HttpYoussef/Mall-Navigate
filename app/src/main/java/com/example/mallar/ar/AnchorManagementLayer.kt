package com.example.mallar.ar

import com.example.mallar.ar.model.RouteNodeMetadata
import com.example.mallar.navigation.NavConfig
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class AnchorKind { STANDARD, TURN }

enum class DeviceTier {
    STANDARD,
    CONSTRAINED;

    companion object {
        fun detect(context: android.content.Context): DeviceTier {
            val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
            val isLowRam = am?.isLowRamDevice == true || totalRamGb < 4.0
            return if (isLowRam) CONSTRAINED else STANDARD
        }
    }
}

data class AnchorWindowConfig(
    val aheadCount: Int = 10,
    val trailingCount: Int = 2,
    val maxActiveAnchors: Int = 15,
    val turnAngleThresholdDeg: Double = 120.0,
    val correctionFrames: Int = 8,
    val floorHeightMeters: Float = -1.35f,
    val pixelsPerMeter: Double = NavConfig.PIXELS_PER_METER.toDouble(),
    val smoothingAlpha: Float = 0.15f,
    val recognitionThrottleMs: Long = 3000L
) {
    companion object {
        fun forTier(tier: DeviceTier): AnchorWindowConfig = when (tier) {
            DeviceTier.STANDARD -> AnchorWindowConfig(
                aheadCount = 10,
                trailingCount = 2,
                maxActiveAnchors = 15,
                smoothingAlpha = 0.15f,
                recognitionThrottleMs = 3000L
            )
            DeviceTier.CONSTRAINED -> AnchorWindowConfig(
                aheadCount = 5,
                trailingCount = 1,
                maxActiveAnchors = 8,
                smoothingAlpha = 0.25f,
                recognitionThrottleMs = 5000L
            )
        }
    }
}

data class AnchorSpec(
    val node: RouteNodeMetadata,
    val kind: AnchorKind,
    val routeIndex: Int
)

data class AnchorWindowPlan(
    val generation: Long,
    val currentRouteIndex: Int,
    val active: List<AnchorSpec>
) {
    val nodeIds: Set<Int> get() = active.mapTo(LinkedHashSet()) { it.node.nodeId }
}

/** Pure route-window and turn-marker planner used by Module 6. */
class AnchorWindowPlanner(
    private val config: AnchorWindowConfig = AnchorWindowConfig()
) {
    private var generation = 0L
    private var lastSignature: List<Pair<Int, AnchorKind>>? = null

    fun plan(route: List<RouteNodeMetadata>, facilityX: Double, facilityY: Double): AnchorWindowPlan {
        if (route.isEmpty()) {
            if (lastSignature != emptyList<Pair<Int, AnchorKind>>()) generation++
            lastSignature = emptyList()
            return AnchorWindowPlan(generation, 0, emptyList())
        }

        val currentIndex = route.indices.minByOrNull { index ->
            hypot(route[index].x - facilityX, route[index].y - facilityY)
        } ?: 0

        val first = max(0, currentIndex - config.trailingCount)
        val last = min(route.lastIndex, currentIndex + config.aheadCount)
        val active = (first..last).map { index ->
            val node = route[index]
            AnchorSpec(node, markerKind(route, index), index)
        }.take(config.maxActiveAnchors)

        val signature = active.map { it.node.nodeId to it.kind }
        if (signature != lastSignature) generation++
        lastSignature = signature
        return AnchorWindowPlan(generation, currentIndex, active)
    }

    fun markerKind(route: List<RouteNodeMetadata>, index: Int): AnchorKind {
        if (index <= 0 || index >= route.lastIndex) return AnchorKind.STANDARD

        val previous = route[index - 1]
        val current = route[index]
        val next = route[index + 1]
        val incomingX = current.x - previous.x
        val incomingY = current.y - previous.y
        val outgoingX = next.x - current.x
        val outgoingY = next.y - current.y
        val incomingLength = hypot(incomingX, incomingY)
        val outgoingLength = hypot(outgoingX, outgoingY)
        if (incomingLength == 0.0 || outgoingLength == 0.0) return AnchorKind.STANDARD

        val cosine = ((incomingX * outgoingX) + (incomingY * outgoingY)) /
            (incomingLength * outgoingLength)
        val angle = Math.toDegrees(acos(cosine.coerceIn(-1.0, 1.0)))
        return if (angle >= config.turnAngleThresholdDeg) AnchorKind.TURN else AnchorKind.STANDARD
    }
}

data class LocalAnchorOffset(
    val xMeters: Double,
    val yMeters: Double,
    val zMeters: Double
)

/** Converts a facility node into the current ARCore-local coordinate frame. */
fun FacilityTransform.localOffsetFor(
    targetFacilityX: Double,
    targetFacilityY: Double,
    localPose: LocalTrackingPose,
    floorHeightMeters: Float = -1.35f,
    pixelsPerMeter: Double = NavConfig.PIXELS_PER_METER.toDouble()
): LocalAnchorOffset {
    val current = facilityPosition(localPose, pixelsPerMeter)
    val mapDx = (targetFacilityX - current.first) / pixelsPerMeter
    val mapDy = (targetFacilityY - current.second) / pixelsPerMeter
    val heading = Math.toRadians(headingDeg.toDouble())
    val localX = mapDx * cos(heading) + mapDy * sin(heading)
    val localZ = -mapDx * sin(heading) + mapDy * cos(heading)
    return LocalAnchorOffset(localX, floorHeightMeters.toDouble(), localZ)
}

/**
 * Calculates the exact, deterministic ARCore World Coordinates (X, Z) for a facility node.
 * This is 100% invariant and independent of transient camera poses.
 */
fun FacilityTransform.worldPositionFor(
    targetFacilityX: Double,
    targetFacilityY: Double,
    pixelsPerMeter: Double = NavConfig.PIXELS_PER_METER.toDouble()
): Pair<Float, Float> {
    val mapDx = (targetFacilityX - facilityX) / pixelsPerMeter
    val mapDy = (targetFacilityY - facilityY) / pixelsPerMeter
    val headingRad = Math.toRadians(headingDeg.toDouble())
    val worldX = localOrigin.xMeters + (mapDx * cos(headingRad) + mapDy * sin(headingRad))
    val worldZ = localOrigin.yMeters + (-mapDx * sin(headingRad) + mapDy * cos(headingRad))
    return worldX.toFloat() to worldZ.toFloat()
}

/** Deterministic multi-frame interpolation for accepted Module 4 corrections. */
class CorrectionInterpolator(
    private val frameCount: Int = 8
) {
    private var start = LocalAnchorOffset(0.0, 0.0, 0.0)
    private var target = start
    private var frame = frameCount

    val isActive: Boolean get() = frame < frameCount

    fun begin(start: LocalAnchorOffset, target: LocalAnchorOffset) {
        this.start = start
        this.target = target
        frame = 0
    }

    fun step(): LocalAnchorOffset {
        if (!isActive) return target
        frame = min(frame + 1, frameCount)
        val t = frame.toDouble() / frameCount.coerceAtLeast(1)
        return LocalAnchorOffset(
            xMeters = start.xMeters + ((target.xMeters - start.xMeters) * t),
            yMeters = start.yMeters + ((target.yMeters - start.yMeters) * t),
            zMeters = start.zMeters + ((target.zMeters - start.zMeters) * t)
        )
    }

    fun reset(value: LocalAnchorOffset = LocalAnchorOffset(0.0, 0.0, 0.0)) {
        start = value
        target = value
        frame = frameCount
    }
}
