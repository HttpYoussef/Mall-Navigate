package com.example.mallar.navigation

import android.util.Log
import com.example.mallar.data.GraphNode
import com.example.mallar.data.MallGraph
import kotlin.math.*

private const val TAG = "IndoorPositionTracker"

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * IndoorPositionTracker
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * PURPOSE
 * -------
 * Maintain a best-estimate position for the user inside the mall using
 * dead reckoning + map-matching + periodic logo-detection relocalization.
 *
 * ── DEAD RECKONING ────────────────────────────────────────────────────────────
 * After each detected step, the estimated position advances in the direction
 * of the user's current heading by STRIDE_LENGTH_M metres:
 *
 *   newX = oldX + stride * sin(headingRad)   (East component)
 *   newY = oldY − stride * cos(headingRad)   (North component, Y flipped)
 *
 * WHY sin/cos?
 *   Heading is measured clockwise from North.
 *   East  (heading=90°)  → sin(90°)=1, cos(90°)=0  → pure X advance. ✓
 *   South (heading=180°) → sin(180°)=0, cos(180°)=-1 → Y increases (+). ✓
 *   North (heading=0°)   → sin(0°)=0, cos(0°)=1 → Y decreases (−). ✓
 *   (Map Y increases downward, so North movement → Y decreases.)
 *
 * Drift accumulates because each step is a small error. Map-matching and
 * logo relocalization are applied to keep the estimate on valid paths.
 *
 * ── MAP MATCHING ──────────────────────────────────────────────────────────────
 * After every step we project the dead-reckoning position onto the nearest
 * graph edge (segment). The projected position is:
 *
 *   t = clamp((AP · AB) / |AB|², 0, 1)
 *   P_snap = A + t * AB
 *
 * where A and B are the endpoints of the nearest segment, P is the user
 * position, and · denotes dot product.
 *
 * The "nearest segment" is found by minimising the perpendicular distance
 * from P to each edge in the graph. If the perpendicular distance exceeds
 * MAP_MATCH_THRESHOLD_PX, the position is NOT snapped (user may have
 * genuinely deviated to seek rerouting).
 *
 * Benefits:
 *   • Corridors act as rails — lateral drift is eliminated.
 *   • Impossible positions (e.g. through walls) are prevented.
 *   • Drift in the along-path direction still accumulates but is corrected
 *     by logo relocalization.
 *
 * ── LOGO RELOCALIZATION ────────────────────────────────────────────────────────
 * When the camera recognises a store logo (via TFLite embeddings), the UI
 * calls relocalize(recognizedNode). We then:
 *   1. Find the nearest graph node to the recognised store (the "landmark").
 *   2. SNAP the estimated position directly to that node.
 *   3. Reset dead-reckoning drift.
 *
 * This is called periodic visual relocalization: it re-anchors the dead-
 * reckoning trajectory every time a known landmark is seen, bounding drift.
 *
 * ── POSITION STATE ────────────────────────────────────────────────────────────
 * [posX, posY]: current estimated position in map pixel coordinates.
 * On first use, the position is seeded from the start node of the path.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
class IndoorPositionTracker(
    /** The full mall graph (needed for map matching across all edges). */
    private val mallGraph: MallGraph,
    /** Starting position in map pixel coordinates. */
    startNode: GraphNode
) {

    // ── Current estimated position (map pixel coordinates) ───────────────────
    var posX: Double = startNode.x
        private set
    var posY: Double = startNode.y
        private set

    // ── Heading source (degrees, clockwise from North) ────────────────────────
    /** Latest user heading — updated externally from SensorFusionManager. */
    var currentHeadingDeg: Float = 0f

    // ── Map matching state ────────────────────────────────────────────────────
    private var currentSegmentFrom: GraphNode? = null
    private var currentSegmentTo:   GraphNode? = null

    // ── Relocalisation history ────────────────────────────────────────────────
    private var lastRelocNode: GraphNode? = null
    private var relocCount:    Int = 0

    // ── Public state ──────────────────────────────────────────────────────────
    /** Total steps counted this session. */
    var stepCount: Long = 0L
        private set

    /** Whether the latest map-match found a valid nearby segment. */
    var isOnPath: Boolean = true
        private set

    /** Perpendicular deviation distance from the nearest path segment (pixels). */
    var deviationPx: Double = 0.0
        private set

    // ── Callbacks ─────────────────────────────────────────────────────────────
    /** Called after every position update. Receives updated (posX, posY). */
    var onPositionUpdated: ((posX: Double, posY: Double) -> Unit)? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Dead reckoning step
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Advance the estimated position by one step in the current heading direction.
     *
     * Call this from StepTracker.onStep.
     *
     * MATH:
     *   headingRad = currentHeadingDeg * π / 180
     *   Δx =  stride * sin(headingRad)   [East component, map +X = East]
     *   Δy = −stride * cos(headingRad)   [North component, map +Y = South so negate]
     *
     * Units: stride is in map pixels (StepTracker.STRIDE_LENGTH_M × px_per_metre).
     * For simplicity we work in map pixels; the caller sets stridePx accordingly.
     */
    fun onStep(stridePx: Double) {
        stepCount++

        val headingRad = Math.toRadians(currentHeadingDeg.toDouble())

        posX += stridePx * sin(headingRad)
        posY -= stridePx * cos(headingRad)   // map Y increases downward → negate

        // Apply map matching to snap to nearest corridor
        applyMapMatching()

        onPositionUpdated?.invoke(posX, posY)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Map matching
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Project the current position onto the nearest graph segment and snap
     * to it if within [MAP_MATCH_THRESHOLD_PX].
     *
     * ALGORITHM (segment projection):
     *   For each edge (A → B) in the graph:
     *     AB = B - A
     *     AP = P - A
     *     t  = (AP · AB) / (|AB|²),  clamped to [0, 1]
     *     closest_point = A + t * AB
     *     deviation     = |P - closest_point|
     *
     * We keep the edge with minimum deviation.
     * If min deviation < MAP_MATCH_THRESHOLD_PX, snap P to closest_point.
     */
    private fun applyMapMatching() {
        val nodeMap = mallGraph.nodes.associateBy { it.id }

        var minDeviation  = Double.MAX_VALUE
        var snappedX      = posX
        var snappedY      = posY
        var bestFrom: GraphNode? = null
        var bestTo:   GraphNode? = null

        for (edge in mallGraph.edges) {
            val a = nodeMap[edge.from] ?: continue
            val b = nodeMap[edge.to]   ?: continue

            val abx = b.x - a.x
            val aby = b.y - a.y
            val abLen2 = abx * abx + aby * aby
            if (abLen2 < 1.0) continue   // degenerate edge

            val apx = posX - a.x
            val apy = posY - a.y

            // Parameterise along edge: t ∈ [0, 1]
            val t = ((apx * abx + apy * aby) / abLen2).coerceIn(0.0, 1.0)

            val closestX = a.x + t * abx
            val closestY = a.y + t * aby

            val devX = posX - closestX
            val devY = posY - closestY
            val dev  = sqrt(devX * devX + devY * devY)

            if (dev < minDeviation) {
                minDeviation = dev
                snappedX     = closestX
                snappedY     = closestY
                bestFrom     = a
                bestTo       = b
            }
        }

        deviationPx = minDeviation

        if (minDeviation <= MAP_MATCH_THRESHOLD_PX) {
            posX = snappedX
            posY = snappedY
            isOnPath              = true
            currentSegmentFrom    = bestFrom
            currentSegmentTo      = bestTo
        } else {
            // User has deviated off the path — do NOT snap (reroute instead)
            isOnPath = false
            Log.w(TAG, "Off-path deviation ${minDeviation.toInt()}px > threshold")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logo relocalization
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Snap the position to [recognizedNode] when the camera detects its logo.
     *
     * WHY THIS WORKS:
     *   Dead reckoning drift grows with √N (hardware) or N (software) steps.
     *   After recognising a store logo the user's position is precisely known —
     *   it's at or very near that store. Snapping eliminates all accumulated drift.
     *
     * Idempotent: if [recognizedNode] is the same as the last relocalisation
     * node, this is a no-op (camera may see the same logo for many frames).
     *
     * @return true if relocalisation was applied, false if skipped.
     */
    fun relocalize(recognizedNode: GraphNode): Boolean {
        if (recognizedNode.id == lastRelocNode?.id) return false  // already applied

        Log.d(TAG, "Relocalize → node ${recognizedNode.id} '${recognizedNode.shopName}' " +
                "(${recognizedNode.x.toInt()}, ${recognizedNode.y.toInt()})")

        posX          = recognizedNode.x
        posY          = recognizedNode.y
        lastRelocNode = recognizedNode
        relocCount++
        isOnPath      = true

        onPositionUpdated?.invoke(posX, posY)
        return true
    }

    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        /**
         * Maximum perpendicular distance from the nearest edge before we consider
         * the user "off-path" and skip snapping.
         * Typical corridor width = 40–100 px on a 1000-px floor plan → use 60.
         */
        const val MAP_MATCH_THRESHOLD_PX: Double = 60.0
    }
}
