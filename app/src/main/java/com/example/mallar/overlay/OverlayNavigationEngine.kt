package com.example.mallar.overlay

import android.util.Log
import com.example.mallar.data.GraphNode
import com.example.mallar.data.MallGraph
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.navigation.IndoorPositionTracker
import com.example.mallar.navigation.StepTracker
import kotlin.math.sqrt

private const val TAG = "OverlayNavigationEngine"

// ─────────────────────────────────────────────────────────────────────────────
/**
 * OverlayNavigationEngine
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * PURPOSE
 * -------
 * The central brain of the pseudo-AR overlay navigation system.
 *
 * Replaces the ARCore-based ArrowSceneManager pipeline with a pure software
 * pipeline that uses:
 *   • IndoorPositionTracker (dead-reckoning + map matching + logo relocalization)
 *   • SensorFusionManager heading (TYPE_ROTATION_VECTOR → Kalman filtered)
 *   • StepTracker (hardware step counter → stride-based position advance)
 *   • BearingCalculator (target bearing → turn hint computation)
 *   • OverlayProjectionEngine (map → screen-space projection)
 *
 * PIPELINE (per sensor update / step event)
 * ─────────────────────────────────────────
 *   1. Advance position (IndoorPositionTracker.onStep).
 *   2. Apply map matching → snap to nearest corridor edge.
 *   3. Detect nearest path node → advance currentSegmentIdx.
 *   4. Check deviation → trigger reroute if off-path.
 *   5. Project remaining path nodes → OverlayProjectionEngine.
 *   6. Compute turn info → heading vs next-waypoint bearing.
 *   7. Notify UI via [onStateChanged] callback.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
class OverlayNavigationEngine(
    private val mallGraph: MallGraph,
    initialPath: List<GraphNode>,
    /** Scale factor: map pixels per metre. Used to convert step stride. */
    private val pxPerMetre: Float = 20f
) {

    // ── Sub-components ────────────────────────────────────────────────────────
    private var positionTracker: IndoorPositionTracker? = null
    private val projectionEngine = OverlayProjectionEngine()

    // ── Navigation state ─────────────────────────────────────────────────────
    private var currentPath: List<GraphNode> = initialPath
    private var currentSegmentIdx: Int = 0
    private var userHeadingDeg: Float = 0f
    private var isInitialized: Boolean = false

    // ── Public state (read by UI) ────────────────────────────────────────────
    val currentUserMapX: Float get() = positionTracker?.posX?.toFloat() ?: currentPath.firstOrNull()?.x?.toFloat() ?: 0f
    val currentUserMapY: Float get() = positionTracker?.posY?.toFloat() ?: currentPath.firstOrNull()?.y?.toFloat() ?: 0f
    val headingDeg: Float get() = userHeadingDeg

    /** Current projected overlay points (computed after each update). */
    var lastProjectedPoints: List<ProjectedPoint> = emptyList()
        private set

    /** Latest turn direction info (updated after each heading/step update). */
    var lastTurnInfo: TurnInfo? = null
        private set

    /** Distance remaining to destination in map pixels. */
    var remainingDistancePx: Float = totalPathDistancePx(initialPath)
        private set

    /** Whether user is on the mapped path. */
    var isOnPath: Boolean = true
        private set

    // ── Callbacks ─────────────────────────────────────────────────────────────
    /** Called whenever overlay state changes — UI should update its projection. */
    var onStateChanged: ((OverlayNavState) -> Unit)? = null

    /** Called when user deviates from the path beyond REROUTE_THRESHOLD_PX. */
    var onRerouteNeeded: (() -> Unit)? = null

    /** Called when user arrives at the destination. */
    var onArrived: (() -> Unit)? = null

    /** Called when the user passes an intermediate waypoint. */
    var onWaypointReached: ((nodeIndex: Int, node: GraphNode) -> Unit)? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Initialisation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Must be called once before the engine is used.
     * Sets up the position tracker seeded at the first path node.
     */
    fun initialize() {
        val startNode = currentPath.firstOrNull() ?: return
        positionTracker = IndoorPositionTracker(mallGraph, startNode).also { tracker ->
            tracker.onPositionUpdated = { posX, posY -> onPositionUpdated(posX, posY) }
        }
        isInitialized = true
        Log.d(TAG, "Initialized. Path=${currentPath.size} nodes, start=(${startNode.x.toInt()},${startNode.y.toInt()})")
        recomputeAndNotify()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sensor / input feeds
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Feed a new filtered heading from SensorFusionManager.
     * Called on every ROTATION_VECTOR sensor update (~50 Hz).
     */
    fun onHeadingUpdated(azimuthDeg: Float) {
        userHeadingDeg = azimuthDeg
        positionTracker?.currentHeadingDeg = azimuthDeg
        recomputeProjectionAndNotify()
    }

    /**
     * Feed a new step event from StepTracker (hardware or software).
     * Advances dead-reckoning position.
     */
    fun onStep(totalSteps: Long) {
        if (!isInitialized) return
        val stridePx = StepTracker.STRIDE_LENGTH_M * pxPerMetre
        positionTracker?.onStep(stridePx.toDouble())
        // onPositionUpdated is triggered automatically via the tracker's callback
    }

    /**
     * Feed a logo detection from MobileNet recognition.
     * Snaps the position to the detected node (eliminates dead-reckoning drift).
     */
    fun onLogoDetected(recognizedNode: GraphNode) {
        if (!isInitialized) return
        val snapped = positionTracker?.relocalize(recognizedNode) ?: false
        if (snapped) {
            Log.d(TAG, "Relocalized to '${recognizedNode.shopName}'")
            recomputeAndNotify()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rerouting
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Replace the current path with a newly computed A* path.
     * Called after [onRerouteNeeded] fires and the caller recomputes the route.
     *
     * @param newPath The new ordered list of GraphNodes from the current position to destination.
     */
    fun updatePath(newPath: List<GraphNode>) {
        if (newPath.size < 2) {
            Log.w(TAG, "Ignoring empty reroute path")
            return
        }

        currentPath       = newPath
        currentSegmentIdx = 0
        remainingDistancePx = totalPathDistancePx(newPath)

        // Re-seed the position tracker at the nearest node (not the first, which
        // may be far if the reroute starts from a mid-path node)
        val tracker = positionTracker
        if (tracker != null) {
            val nearestNode = MallGraphRepository.findNearestNode(
                mallGraph, tracker.posX, tracker.posY
            )
            if (nearestNode != null) {
                tracker.relocalize(nearestNode)
            }
        }

        Log.d(TAG, "Path updated: ${newPath.size} nodes")
        recomputeAndNotify()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Configure projection screen size (must be called when screen dimensions known)
    // ─────────────────────────────────────────────────────────────────────────

    fun setScreenSize(width: Float, height: Float, fovDeg: Float = 68f) {
        projectionEngine.screenW = width
        projectionEngine.screenH = height
        projectionEngine.fovDeg  = fovDeg
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal: position update handler (triggered by IndoorPositionTracker)
    // ─────────────────────────────────────────────────────────────────────────

    private fun onPositionUpdated(posX: Double, posY: Double) {
        updateSegmentIndex(posX, posY)
        checkWaypointArrival(posX, posY)
        checkDeviation()
        recomputeAndNotify()
    }

    private fun updateSegmentIndex(posX: Double, posY: Double) {
        val nearestIdx = nearestPathNodeIndex(posX, posY)
        if (nearestIdx > currentSegmentIdx) {
            currentSegmentIdx = nearestIdx
            Log.d(TAG, "Segment advanced to $currentSegmentIdx")
        }
    }

    private fun checkWaypointArrival(posX: Double, posY: Double) {
        // Final destination check
        val finalNode = currentPath.lastOrNull() ?: return
        val dx = finalNode.x - posX; val dy = finalNode.y - posY
        val distToFinal = sqrt(dx * dx + dy * dy)
        if (distToFinal < ARRIVAL_THRESHOLD_PX) {
            Log.d(TAG, "ARRIVED at destination!")
            onArrived?.invoke()
            return
        }

        // Intermediate waypoints
        for (i in (currentSegmentIdx + 1) until currentPath.size - 1) {
            val node = currentPath[i]
            val dx2 = node.x - posX; val dy2 = node.y - posY
            if (sqrt(dx2 * dx2 + dy2 * dy2) < ARRIVAL_THRESHOLD_PX) {
                Log.d(TAG, "Waypoint reached: node ${node.id}")
                onWaypointReached?.invoke(i, node)
                break
            }
        }
    }

    private fun checkDeviation() {
        val tracker = positionTracker ?: return
        isOnPath = tracker.isOnPath
        if (!tracker.isOnPath && tracker.deviationPx > REROUTE_THRESHOLD_PX) {
            Log.w(TAG, "Off-path deviation: ${tracker.deviationPx.toInt()}px — rerouting")
            onRerouteNeeded?.invoke()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Projection + state computation
    // ─────────────────────────────────────────────────────────────────────────

    private fun recomputeAndNotify() {
        recomputeProjectionAndNotify()
    }

    private fun recomputeProjectionAndNotify() {
        val tracker = positionTracker

        val userX = tracker?.posX?.toFloat() ?: currentPath.firstOrNull()?.x?.toFloat() ?: return
        val userY = tracker?.posY?.toFloat() ?: currentPath.firstOrNull()?.y?.toFloat() ?: return

        // ── Project remaining path nodes onto screen ───────────────────────────
        lastProjectedPoints = projectionEngine.project(
            pathNodes         = currentPath,
            userMapX          = userX,
            userMapY          = userY,
            headingDeg        = userHeadingDeg,
            lookaheadStartIdx = currentSegmentIdx
        )

        // ── Compute turn info for the next segment ────────────────────────────
        val nextNode = currentPath.getOrNull(currentSegmentIdx + 1)
        lastTurnInfo = if (nextNode != null) {
            projectionEngine.computeTurnInfo(
                nextNodeMapX = nextNode.x.toFloat(),
                nextNodeMapY = nextNode.y.toFloat(),
                userMapX     = userX,
                userMapY     = userY,
                headingDeg   = userHeadingDeg
            )
        } else null

        // ── Remaining distance ────────────────────────────────────────────────
        remainingDistancePx = computeRemainingDistance(userX, userY)

        // ── Notify UI ──────────────────────────────────────────────────────────
        onStateChanged?.invoke(
            OverlayNavState(
                projectedPoints    = lastProjectedPoints,
                turnInfo           = lastTurnInfo,
                remainingDistancePx = remainingDistancePx,
                segmentIdx         = currentSegmentIdx,
                isOnPath           = isOnPath,
                userMapX           = userX,
                userMapY           = userY,
                headingDeg         = userHeadingDeg
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun nearestPathNodeIndex(posX: Double, posY: Double): Int {
        var bestIdx = 0
        var bestDist = Double.MAX_VALUE
        for ((i, node) in currentPath.withIndex()) {
            val dx = node.x - posX; val dy = node.y - posY
            val d = sqrt(dx * dx + dy * dy)
            if (d < bestDist) { bestDist = d; bestIdx = i }
        }
        return bestIdx
    }

    private fun computeRemainingDistance(userX: Float, userY: Float): Float {
        if (currentPath.size < 2) return 0f
        var dist = 0.0
        val startIdx = currentSegmentIdx
        for (i in startIdx until currentPath.size - 1) {
            val a = currentPath[i]; val b = currentPath[i + 1]
            val dx = a.x - b.x; val dy = a.y - b.y
            dist += sqrt(dx * dx + dy * dy)
        }
        return dist.toFloat()
    }

    private fun totalPathDistancePx(path: List<GraphNode>): Float {
        var d = 0f
        for (i in 0 until path.size - 1) {
            val a = path[i]; val b = path[i + 1]
            val dx = (a.x - b.x).toFloat(); val dy = (a.y - b.y).toFloat()
            d += sqrt(dx * dx + dy * dy)
        }
        return d
    }

    companion object {
        private const val ARRIVAL_THRESHOLD_PX   = 40.0
        private const val REROUTE_THRESHOLD_PX   = 80.0   // ~4 m at 20 px/m
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Immutable overlay state snapshot consumed by the UI
// ─────────────────────────────────────────────────────────────────────────────

data class OverlayNavState(
    val projectedPoints: List<ProjectedPoint>,
    val turnInfo: TurnInfo?,
    val remainingDistancePx: Float,
    val segmentIdx: Int,
    val isOnPath: Boolean,
    val userMapX: Float,
    val userMapY: Float,
    val headingDeg: Float
)
