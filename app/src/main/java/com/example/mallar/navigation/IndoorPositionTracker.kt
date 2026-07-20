package com.example.mallar.navigation

import android.util.Log
import com.example.mallar.data.GraphNode
import com.example.mallar.data.MallGraph
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

private const val TAG = "IndoorPositionTracker"

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * IndoorPositionTracker
 * ─────────────────────────────────────────────────────────────────────────────
 * ROLE: Thread-safe Unified Constraint Engine.
 * 
 * Manages the transition from raw sensor movement (PDR) to map-constrained 
 * coordinates.
 */
class IndoorPositionTracker(
    private val mallGraph: MallGraph,
    startNode: GraphNode,
    private val pxPerMetre: Float = NavConfig.PIXELS_PER_METER
) {

    data class PositionState(val x: Double, val y: Double)

    private val position = AtomicReference(PositionState(startNode.x, startNode.y))

    val posX: Double get() = position.get().x
    val posY: Double get() = position.get().y

    var currentHeadingDeg: Float = 0f
    var stepCount: Long = 0L
        private set

    var isOnPath: Boolean = true
        private set

    var deviationPx: Double = 0.0
        private set

    var onPositionUpdated: ((posX: Double, posY: Double) -> Unit)? = null

    /** Hard reset of the position (Thread-safe). */
    fun relocalize(node: GraphNode) {
        position.set(PositionState(node.x, node.y))
        isOnPath = true
        deviationPx = 0.0
        onPositionUpdated?.invoke(node.x, node.y)
    }

    data class ConstraintResult(
        val snappedX: Double,
        val snappedY: Double,
        val isOnPath: Boolean,
        val deviationPx: Double,
        val bestSegmentIdx: Int
    )

    /**
     * ATOMIC STEP UPDATE:
     * Combines PDR propagation and constraint snapping into one atomic action
     * to prevent race conditions with the ML relocalization thread.
     */
    fun onStep(strideM: Float, path: List<GraphNode>, currentSegmentIdx: Int): ConstraintResult {
        stepCount++
        val stridePx = strideM * pxPerMetre
        val headingRad = Math.toRadians(currentHeadingDeg.toDouble())

        // Use a loop with compareAndSet to ensure the update is based on the LATEST state
        while (true) {
            val current = position.get()
            
            // 1. Raw PDR propagation
            val rawX = current.x + stridePx * sin(headingRad)
            val rawY = current.y - stridePx * cos(headingRad)

            // 2. Multi-tier snapping
            val result = calculateConstraints(rawX, rawY, path, currentSegmentIdx)

            // 3. Atomic commit
            if (position.compareAndSet(current, PositionState(result.snappedX, result.snappedY))) {
                this.isOnPath = result.isOnPath
                this.deviationPx = result.deviationPx
                onPositionUpdated?.invoke(result.snappedX, result.snappedY)
                return result
            }
            // If CAS failed, someone else (likely ML) updated the position; loop and re-calculate.
        }
    }

    private fun calculateConstraints(
        rawX: Double, 
        rawY: Double, 
        path: List<GraphNode>, 
        currentSegmentIdx: Int
    ): ConstraintResult {
        // Tier 1: Route Snap
        val routeSnap = findNearestPointOnPath(rawX, rawY, path, currentSegmentIdx)
        if (routeSnap != null && routeSnap.distance <= NavConfig.SNAP_THRESHOLD_PX) {
            return ConstraintResult(routeSnap.x, routeSnap.y, true, routeSnap.distance, routeSnap.segmentIdx)
        }

        // Tier 2: Global Snap (with spatial pruning)
        val globalSnap = findNearestPointInGraph(rawX, rawY)
        if (globalSnap != null && globalSnap.distance <= NavConfig.REROUTE_THRESHOLD_PX) {
            return ConstraintResult(globalSnap.x, globalSnap.y, false, globalSnap.distance, currentSegmentIdx)
        }

        // Tier 3: Deviation (Accept raw pos to show the user they are lost)
        return ConstraintResult(rawX, rawY, false, globalSnap?.distance ?: 999.0, currentSegmentIdx)
    }

    private data class SnapMatch(val x: Double, val y: Double, val distance: Double, val segmentIdx: Int = 0)

    private fun findNearestPointOnPath(px: Double, py: Double, path: List<GraphNode>, startIdx: Int): SnapMatch? {
        if (path.size < 2) return null
        var best: SnapMatch? = null
        val lookahead = 6
        val endIdx = (startIdx + lookahead).coerceAtMost(path.size - 2)

        for (i in startIdx..endIdx) {
            val match = projectToSegment(px, py, path[i], path[i+1], i)
            if (best == null || match.distance < best.distance) best = match
        }
        return best
    }

    private fun findNearestPointInGraph(px: Double, py: Double): SnapMatch? {
        val nodeMap = mallGraph.nodes.associateBy { it.id }
        var best: SnapMatch? = null
        
        // SCALABILITY FIX: Prune search to edges within a reasonable radius of the point
        val searchRadius = NavConfig.GLOBAL_MATCH_THRESHOLD_PX * 2.5

        for (edge in mallGraph.edges) {
            val a = nodeMap[edge.from] ?: continue
            val b = nodeMap[edge.to] ?: continue
            
            // Pruning: skip segments that are entirely too far away
            if (abs(a.x - px) > searchRadius && abs(b.x - px) > searchRadius) continue
            if (abs(a.y - py) > searchRadius && abs(b.y - py) > searchRadius) continue

            val match = projectToSegment(px, py, a, b, 0)
            if (best == null || match.distance < best.distance) best = match
        }
        return best
    }

    private fun projectToSegment(px: Double, py: Double, a: GraphNode, b: GraphNode, segIdx: Int): SnapMatch {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val abLen2 = abx * abx + aby * aby
        if (abLen2 < 1e-6) return SnapMatch(a.x, a.y, sqrt((px-a.x).pow(2) + (py-a.y).pow(2)), segIdx)

        val t = (((px - a.x) * abx + (py - a.y) * aby) / abLen2).coerceIn(0.0, 1.0)
        val sx = a.x + t * abx
        val sy = a.y + t * aby
        val dist = sqrt((px - sx).pow(2) + (py - sy).pow(2))
        return SnapMatch(sx, sy, dist, segIdx)
    }
}
