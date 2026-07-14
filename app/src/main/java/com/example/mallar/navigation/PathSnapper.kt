package com.example.mallar.navigation

import android.util.Log
import com.example.mallar.data.GraphNode
import kotlin.math.*

private const val TAG = "PathSnapper"

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * PathSnapper
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * PURPOSE
 * -------
 * Constrain every dead-reckoning position update to lie on (or very close to)
 * the active A* path rather than any reachable graph edge.
 *
 * This is a stronger guarantee than the existing [IndoorPositionTracker]
 * map-matching, which snaps to the *nearest graph edge globally*.  That can
 * snap onto a parallel corridor, a side-passage, or a T-junction branch that
 * is NOT on the user's intended route.
 *
 * PathSnapper only considers the segments **on the current path**, so drift is
 * always constrained to the intended corridor even when adjacent corridors are
 * spatially closer.
 *
 * ALGORITHM
 * ---------
 * For each active path segment [A → B] starting from [currentSegmentIdx]:
 *
 *   t  = clamp( (AP · AB) / |AB|² , 0, 1 )
 *   Pₛ = A + t * AB          ← projected point on segment
 *   d  = |P − Pₛ|            ← perpendicular deviation
 *
 * We search the [lookaheadWindow] segments ahead of the current index to avoid
 * snapping backward.  The segment with minimum deviation is chosen.
 *
 * If d ≤ SNAP_THRESHOLD_PX → return the snapped position.
 * If d > SNAP_THRESHOLD_PX → return the original position (off-path signal).
 *
 * SMOOTHING
 * ---------
 * A lerp factor [smoothAlpha] blends toward the snapped position instead of
 * jumping:
 *   smoothed = old + smoothAlpha * (snapped − old)
 *
 * α is kept high so each footstep visibly moves the dot; low α here stacks with
 * [PositionSmoother] and feels like delayed teleporting.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
class PathSnapper(
    /** How far (px) from the nearest path edge before we stop snapping. */
    private val snapThresholdPx: Double = SNAP_THRESHOLD_PX,
    /** Lerp factor for smooth snap approach [0, 1]. 1 = instant jump. */
    private val smoothAlpha: Float = 0.88f,
    /** How many segments ahead of current index to consider (prevents backward snap). */
    private val lookaheadWindow: Int = 6
) {

    // ── Result type ───────────────────────────────────────────────────────────

    data class SnapResult(
        val snappedX: Double,
        val snappedY: Double,
        /** Perpendicular deviation from the path (px). */
        val deviationPx: Double,
        /** True when deviation ≤ snapThresholdPx (snapping was applied). */
        val isOnPath: Boolean,
        /** Path segment index that gave the best snap. */
        val bestSegmentIdx: Int
    )

    // ── Internal lerp state ───────────────────────────────────────────────────

    private var smoothX: Double? = null
    private var smoothY: Double? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Main API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Snap [posX], [posY] to the nearest point on the path starting from
     * [currentSegmentIdx], then lerp toward that snap point.
     *
     * Call once per step (same rate as dead reckoning).
     */
    fun snap(
        posX: Double,
        posY: Double,
        pathNodes: List<GraphNode>,
        currentSegmentIdx: Int
    ): SnapResult {

        // Safety: need at least 2 nodes for a segment
        if (pathNodes.size < 2) {
            return SnapResult(posX, posY, 0.0, true, currentSegmentIdx)
        }

        var minDev         = Double.MAX_VALUE
        var bestSnappedX   = posX
        var bestSnappedY   = posY
        var bestSegmentIdx = currentSegmentIdx

        val startIdx = currentSegmentIdx.coerceAtLeast(0)
        val endIdx   = (startIdx + lookaheadWindow).coerceAtMost(pathNodes.size - 2)

        for (i in startIdx..endIdx) {
            val a = pathNodes[i]
            val b = pathNodes[i + 1]

            val abx    = b.x - a.x
            val aby    = b.y - a.y
            val abLen2 = abx * abx + aby * aby
            if (abLen2 < 1e-6) continue       // degenerate segment

            val apx = posX - a.x
            val apy = posY - a.y

            val t = ((apx * abx + apy * aby) / abLen2).coerceIn(0.0, 1.0)

            val snapX = a.x + t * abx
            val snapY = a.y + t * aby

            val devX = posX - snapX
            val devY = posY - snapY
            val dev  = sqrt(devX * devX + devY * devY)

            if (dev < minDev) {
                minDev         = dev
                bestSnappedX   = snapX
                bestSnappedY   = snapY
                bestSegmentIdx = i
            }
        }

        val onPath = minDev <= snapThresholdPx

        if (onPath) {
            // Lerp the position smoothly toward the snap target
            val prevX = smoothX ?: posX
            val prevY = smoothY ?: posY

            val lerpedX = prevX + smoothAlpha * (bestSnappedX - prevX)
            val lerpedY = prevY + smoothAlpha * (bestSnappedY - prevY)

            smoothX = lerpedX
            smoothY = lerpedY

            return SnapResult(lerpedX, lerpedY, minDev, true, bestSegmentIdx)
        } else {
            // Off-path — keep raw position, reset lerp state
            smoothX = null
            smoothY = null
            Log.w(TAG, "Off path: deviation=${minDev.toInt()}px > threshold=${snapThresholdPx.toInt()}px")
            return SnapResult(posX, posY, minDev, false, bestSegmentIdx)
        }
    }

    /** Reset lerp history (call when position is relocated externally). */
    fun reset() {
        smoothX = null
        smoothY = null
    }

    companion object {
        /** Default snap threshold: typical indoor corridor is 40–100 map pixels wide. */
        const val SNAP_THRESHOLD_PX = 70.0
    }
}
