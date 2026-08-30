package com.example.mallar.ar.supervision

import com.example.mallar.ar.FacilityTransform
import com.example.mallar.ar.LocalTrackingPose
import com.example.mallar.ar.model.RouteNodeMetadata
import com.example.mallar.navigation.DriftMonitor
import com.example.mallar.navigation.NavConfig
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import kotlin.math.*

enum class ArRuntimeState {
    NO_FIX,
    ACQUIRING,
    TRACKING_FRESH,
    TRACKING_AGING,
    TRACKING_DEGRADED,
    ROUTE_REBUILDING,
    TRANSITION_MODE,
    INTERRUPTED_GRACE,
    INTERRUPTED_FULL,
    FALLBACK_OFFERED,
    ARRIVED,
    SESSION_ENDED
}

enum class EnvironmentalGuidance {
    NONE,
    INSUFFICIENT_LIGHT,
    EXCESSIVE_MOTION,
    POINT_AT_SURROUNDINGS,
    MANUAL_RESCAN_SUGGESTED
}

sealed class SupervisoryInstruction {
    object None : SupervisoryInstruction()
    data class ApplyDriftCorrection(val lateralOffsetPx: Double) : SupervisoryInstruction()
    data class RebuildRoute(val facilityX: Double, val facilityY: Double, val reason: String) : SupervisoryInstruction()
    data class EnterTransitionMode(val targetFloor: Int) : SupervisoryInstruction()
    object ExitTransitionMode : SupervisoryInstruction()
    data class EnterArrivedState(val destinationNode: RouteNodeMetadata) : SupervisoryInstruction()
    object RequestReFix : SupervisoryInstruction()
}

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Module 8 — Drift & Recovery Supervisory Layer
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Supervises tracking quality and navigation correctness.
 * Owns no primary tracking or transform state.
 *
 * Parameters are initialized to chosen values within the frozen target ranges:
 * - lateralDriftThresholdMeters: 2.5m (target range: 2.0 - 3.0m)
 * - arrivalRadiusMeters: 2.5m (target range: 2.0 - 3.0m)
 * - floorTransitionRadiusMeters: 3.0m (target: ~3m)
 * - graceWindowMs: 3000ms (target: ~3s)
 */
class DriftRecoverySupervisor(
    val lateralDriftThresholdMeters: Double = 2.5,
    val arrivalRadiusMeters: Double = 2.5,
    val floorTransitionRadiusMeters: Double = 3.0,
    val freshDurationMs: Long = 15_000L,
    val trustWindowMs: Long = 30_000L,
    val graceWindowMs: Long = 3_000L,
    val pixelsPerMeter: Double = NavConfig.PIXELS_PER_METER.toDouble()
) {
    var state: ArRuntimeState = ArRuntimeState.NO_FIX
        private set

    var environmentalGuidance: EnvironmentalGuidance = EnvironmentalGuidance.NONE
        private set

    private var stateEnteredAtMs: Long = 0L
    private var lastValidFixAtMs: Long = 0L
    private var interruptedAtMs: Long = 0L
    private var stateBeforeInterruption: ArRuntimeState = ArRuntimeState.TRACKING_FRESH
    private var consecutiveRejectionCount: Int = 0
    private var reverseWalkDwellCount: Int = 0
    private var lastDistanceToNextNodePx: Double = Double.MAX_VALUE
    private var lastObservedNodeIndex: Int = 0

    fun onInitialFixAccepted(nowMs: Long) {
        lastValidFixAtMs = nowMs
        consecutiveRejectionCount = 0
        transitionTo(ArRuntimeState.TRACKING_FRESH, nowMs)
    }

    fun onPeriodicFixAccepted(nowMs: Long) {
        lastValidFixAtMs = nowMs
        consecutiveRejectionCount = 0
        if (state == ArRuntimeState.TRACKING_AGING || state == ArRuntimeState.TRACKING_DEGRADED) {
            transitionTo(ArRuntimeState.TRACKING_FRESH, nowMs)
        }
    }

    fun onFixRejected(nowMs: Long) {
        consecutiveRejectionCount++
        if (consecutiveRejectionCount >= 3) {
            environmentalGuidance = EnvironmentalGuidance.MANUAL_RESCAN_SUGGESTED
        }
    }

    fun evaluate(
        timestampMs: Long,
        trackingState: TrackingState,
        trackingFailureReason: TrackingFailureReason,
        transform: FacilityTransform?,
        localPose: LocalTrackingPose,
        userHeadingDeg: Float,
        route: List<RouteNodeMetadata>,
        driftState: DriftMonitor.DriftState,
        isBarometerTransitionConfirmed: Boolean = false
    ): SupervisoryInstruction {
        // Evaluate environmental guidance
        updateEnvironmentalGuidance(trackingFailureReason)

        // 1. Interruption handling
        if (trackingState != TrackingState.TRACKING) {
            if (state != ArRuntimeState.INTERRUPTED_GRACE && state != ArRuntimeState.INTERRUPTED_FULL && state != ArRuntimeState.NO_FIX) {
                stateBeforeInterruption = state
                interruptedAtMs = timestampMs
                transitionTo(ArRuntimeState.INTERRUPTED_GRACE, timestampMs)
            } else if (state == ArRuntimeState.INTERRUPTED_GRACE) {
                if (timestampMs - interruptedAtMs > graceWindowMs) {
                    transitionTo(ArRuntimeState.INTERRUPTED_FULL, timestampMs)
                }
            }
            return SupervisoryInstruction.None
        } else {
            // Tracking is TRACKING
            if (state == ArRuntimeState.INTERRUPTED_GRACE) {
                // Resolved within grace window
                transitionTo(stateBeforeInterruption, timestampMs)
            } else if (state == ArRuntimeState.INTERRUPTED_FULL) {
                transitionTo(ArRuntimeState.NO_FIX, timestampMs)
                return SupervisoryInstruction.RequestReFix
            }
        }

        if (transform == null || route.isEmpty()) {
            return SupervisoryInstruction.None
        }

        val (facilityX, facilityY) = transform.facilityPosition(localPose, pixelsPerMeter)

        // 2. Floor Transition Check
        val upcomingFloorTransition = route.firstOrNull { it.isFloorTransition }
        if (upcomingFloorTransition != null) {
            val distToTransitionPx = hypot(upcomingFloorTransition.x - facilityX, upcomingFloorTransition.y - facilityY)
            if (distToTransitionPx <= floorTransitionRadiusMeters * pixelsPerMeter) {
                if (state != ArRuntimeState.TRANSITION_MODE) {
                    transitionTo(ArRuntimeState.TRANSITION_MODE, timestampMs)
                    return SupervisoryInstruction.EnterTransitionMode(upcomingFloorTransition.floor)
                }
            }
        }

        // 3. Destination Arrival Check (Only valid on destination floor when no transition remains ahead)
        val destinationNode = route.lastOrNull()
        if (destinationNode != null && upcomingFloorTransition == null) {
            val distToDestPx = hypot(destinationNode.x - facilityX, destinationNode.y - facilityY)
            if (distToDestPx <= arrivalRadiusMeters * pixelsPerMeter) {
                if (state != ArRuntimeState.ARRIVED && state != ArRuntimeState.SESSION_ENDED) {
                    transitionTo(ArRuntimeState.ARRIVED, timestampMs)
                    return SupervisoryInstruction.EnterArrivedState(destinationNode)
                }
            }
        }

        if (state == ArRuntimeState.ARRIVED) {
            if (timestampMs - stateEnteredAtMs > 4_000L) {
                transitionTo(ArRuntimeState.SESSION_ENDED, timestampMs)
            }
            return SupervisoryInstruction.None
        }

        if (state == ArRuntimeState.TRANSITION_MODE) {
            if (isBarometerTransitionConfirmed && consecutiveRejectionCount == 0 && (timestampMs - lastValidFixAtMs) < freshDurationMs) {
                transitionTo(ArRuntimeState.TRACKING_FRESH, timestampMs)
                return SupervisoryInstruction.ExitTransitionMode
            }
            return SupervisoryInstruction.None
        }

        // 4. Freshness & Trust Window Aging
        val fixAgeMs = timestampMs - lastValidFixAtMs
        if (state == ArRuntimeState.TRACKING_FRESH && fixAgeMs > freshDurationMs) {
            transitionTo(ArRuntimeState.TRACKING_AGING, timestampMs)
        } else if (state == ArRuntimeState.TRACKING_AGING && fixAgeMs > trustWindowMs) {
            transitionTo(ArRuntimeState.TRACKING_DEGRADED, timestampMs)
        }

        // 5. Drift vs Deviation vs Route-Reversal Classification
        val (closestIndex, lateralDistPx) = computePolylineDistance(route, facilityX, facilityY)
        val lateralDistMeters = lateralDistPx / pixelsPerMeter

        // Check for Route Reversal (Dual Condition: Heading >= 120 deg AND Distance Trend)
        val isWalkingBackwards = checkRouteReversal(route, closestIndex, facilityX, facilityY, userHeadingDeg)
        if (isWalkingBackwards) {
            transitionTo(ArRuntimeState.ROUTE_REBUILDING, timestampMs)
            return SupervisoryInstruction.RebuildRoute(facilityX, facilityY, "Route reversal detected")
        }

        // Check for Lateral Deviation
        if (lateralDistMeters > lateralDriftThresholdMeters || driftState.offPathSteps >= 8) {
            transitionTo(ArRuntimeState.ROUTE_REBUILDING, timestampMs)
            return SupervisoryInstruction.RebuildRoute(facilityX, facilityY, "Lateral deviation ($lateralDistMeters m)")
        }

        // Check for Drift (Within Bounds)
        if (lateralDistMeters > 0.3) {
            return SupervisoryInstruction.ApplyDriftCorrection(lateralDistPx)
        }

        return SupervisoryInstruction.None
    }

    private fun checkRouteReversal(
        route: List<RouteNodeMetadata>,
        closestIndex: Int,
        facilityX: Double,
        facilityY: Double,
        userHeadingDeg: Float
    ): Boolean {
        if (closestIndex >= route.lastIndex) return false
        val currentNode = route[closestIndex]
        val nextNode = route[closestIndex + 1]

        // 1. Calculate corridor segment forward heading in facility space
        val dx = nextNode.x - currentNode.x
        val dy = nextNode.y - currentNode.y
        if (dx == 0.0 && dy == 0.0) return false
        val routeSegmentHeadingDeg = Math.toDegrees(atan2(dx, -dy)).toFloat()

        // 2. Angular difference between user heading and forward segment tangent
        val angleDiff = abs(normalizeAngleDeg(userHeadingDeg - routeSegmentHeadingDeg))
        val isHeadingReversed = angleDiff >= 120f

        // 3. Distance to next forward node
        val distToNextPx = hypot(nextNode.x - facilityX, nextNode.y - facilityY)
        val isDistanceIncreasing = distToNextPx > lastDistanceToNextNodePx + (0.1 * pixelsPerMeter)

        if (closestIndex == lastObservedNodeIndex) {
            // Both heading is counter-directed AND distance to upcoming node is increasing
            if (isHeadingReversed && isDistanceIncreasing) {
                reverseWalkDwellCount++
            } else if (!isHeadingReversed) {
                // User turned back forward: immediately reset counter
                reverseWalkDwellCount = 0
            } else {
                reverseWalkDwellCount = max(0, reverseWalkDwellCount - 1)
            }
        } else {
            reverseWalkDwellCount = 0
        }

        lastObservedNodeIndex = closestIndex
        lastDistanceToNextNodePx = distToNextPx

        return reverseWalkDwellCount >= 3
    }

    private fun normalizeAngleDeg(angleDeg: Float): Float {
        var a = angleDeg % 360f
        if (a > 180f) a -= 360f
        if (a < -180f) a += 360f
        return a
    }

    private fun computePolylineDistance(
        route: List<RouteNodeMetadata>,
        px: Double,
        py: Double
    ): Pair<Int, Double> {
        var minDistance = Double.MAX_VALUE
        var closestSegmentIndex = 0

        for (i in 0 until route.lastIndex) {
            val n1 = route[i]
            val n2 = route[i + 1]
            val dist = pointToSegmentDistance(px, py, n1.x, n1.y, n2.x, n2.y)
            if (dist < minDistance) {
                minDistance = dist
                closestSegmentIndex = i
            }
        }
        return closestSegmentIndex to minDistance
    }

    private fun pointToSegmentDistance(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) return hypot(px - x1, py - y1)
        val t = ((px - x1) * dx + (py - y1) * dy) / lenSq
        val clampedT = t.coerceIn(0.0, 1.0)
        val projX = x1 + clampedT * dx
        val projY = y1 + clampedT * dy
        return hypot(px - projX, py - projY)
    }

    private fun updateEnvironmentalGuidance(reason: TrackingFailureReason) {
        environmentalGuidance = when (reason) {
            TrackingFailureReason.INSUFFICIENT_LIGHT -> EnvironmentalGuidance.INSUFFICIENT_LIGHT
            TrackingFailureReason.EXCESSIVE_MOTION -> EnvironmentalGuidance.EXCESSIVE_MOTION
            TrackingFailureReason.INSUFFICIENT_FEATURES -> EnvironmentalGuidance.POINT_AT_SURROUNDINGS
            else -> if (consecutiveRejectionCount >= 3) EnvironmentalGuidance.MANUAL_RESCAN_SUGGESTED else EnvironmentalGuidance.NONE
        }
    }

    fun transitionTo(newState: ArRuntimeState, nowMs: Long) {
        state = newState
        stateEnteredAtMs = nowMs
    }

    fun onRouteRebuildComplete(nowMs: Long) {
        reverseWalkDwellCount = 0
        lastDistanceToNextNodePx = Double.MAX_VALUE
        transitionTo(ArRuntimeState.TRACKING_FRESH, nowMs)
    }

    fun reset() {
        state = ArRuntimeState.NO_FIX
        environmentalGuidance = EnvironmentalGuidance.NONE
        stateEnteredAtMs = 0L
        lastValidFixAtMs = 0L
        interruptedAtMs = 0L
        consecutiveRejectionCount = 0
        reverseWalkDwellCount = 0
        lastDistanceToNextNodePx = Double.MAX_VALUE
    }
}
