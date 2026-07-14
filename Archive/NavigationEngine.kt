package com.example.mallar.navigation

import android.util.Log
import com.example.mallar.data.GraphNode
import com.example.mallar.data.MallGraph
import com.example.mallar.data.MallGraphRepository
import kotlin.math.*

private const val TAG = "NavigationEngine"

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * NavigationEngine
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * PURPOSE
 * -------
 * Orchestrate all navigation layers into a single clean API:
 *
 *   ┌─────────────────────────────────────────────────────────┐
 *   │  Localization Layer  (IndoorPositionTracker)            │
 *   │    dead reckoning + map matching + logo relocalization  │
 *   ├─────────────────────────────────────────────────────────┤
 *   │  Sensor Fusion Layer  (SensorFusionManager)             │
 *   │    TYPE_ROTATION_VECTOR + LP filter + Kalman filter     │
 *   ├─────────────────────────────────────────────────────────┤
 *   │  Step Tracking Layer  (StepTracker)                     │
 *   │    TYPE_STEP_COUNTER or accelerometer fallback          │
 *   ├─────────────────────────────────────────────────────────┤
 *   │  Pathfinding Layer  (MallGraphRepository.aStar)         │
 *   │    A* on graph + bearing computation (BearingCalculator)│
 *   ├─────────────────────────────────────────────────────────┤
 *   │  Navigation UI Layer  (NavigationEngine output)         │
 *   │    NavigationState → consumed by ViewModel / UI         │
 *   └─────────────────────────────────────────────────────────┘
 *
 * HOW ARROW ROTATION IS COMPUTED (PER FRAME)
 * -------------------------------------------
 * 1. Find which path segment the user is currently on (by nearest node index).
 * 2. Compute targetBearingDeg = BearingCalculator.segmentBearing(path, segIdx).
 * 3. Compute userHeadingDeg from SensorFusionManager (smoothed).
 * 4. arrowRotationDeg = targetBearingDeg − userHeadingDeg (normalised to −180..+180).
 * 5. Classify: STRAIGHT / LEFT / RIGHT from arrowRotationDeg.
 *
 * This means the arrow's displayed rotation IS the angle the user must rotate
 * to face the next waypoint. It is fully dynamic — it updates every heading
 * tick, not just at waypoints.
 *
 * WHY IS THIS BETTER THAN THE OLD APPROACH?
 * ------------------------------------------
 * Old system: arrows were hard-coded as LEFT/RIGHT at fixed angles, or
 * computed from uncalibrated raw compass readings that drift and jitter.
 *
 * New system:
 *   • TYPE_ROTATION_VECTOR eliminates gyro + mag raw noise via OS Kalman filter.
 *   • Additional LP + scalar Kalman in SensorFusionManager smooths the output.
 *   • arrowRotation = targetBearing − userHeading is mathematically correct.
 *   • Map matching prevents the AR dead-reckoning position from wandering into
 *     walls, which caused wrong "closest segment" lookups.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
class NavigationEngine(
    private val mallGraph: MallGraph,
    private val pathNodes: List<GraphNode>,

    // Pixel-to-metre scale (used to convert step stride to map pixels).
    // Matches DISPLAY_SCALE already used in the app (0.05f = 1 px ≈ 0.05 m).
    private val pxPerMetre: Float = 20f   // inverse of 0.05: 1 m = 20 px
) {

    // ── Sub-components ────────────────────────────────────────────────────────
    // These are created internally; lifecycle is managed by NavigationEngine.
    // The UI connects to NavigationEngine, not to the individual managers.

    private var positionTracker: IndoorPositionTracker? = null
    private var pathTurns: List<PathTurn> = emptyList()

    // ── State ─────────────────────────────────────────────────────────────────
    private var currentSegIdx: Int = 0
    private var userHeadingDeg: Float = 0f

    private val _state = MutableNavigationState()
    val state: NavigationState get() = _state.snapshot()

    // ── Callbacks ─────────────────────────────────────────────────────────────
    /** Called whenever new navigation state is available (on calling thread). */
    var onStateUpdated: ((NavigationState) -> Unit)? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Initialisation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call once after setting [onStateUpdated] to prepare internal state.
     * [startNode] must be the first node in [pathNodes].
     */
    fun initialize(startNode: GraphNode) {
        positionTracker = IndoorPositionTracker(mallGraph, startNode).also { tracker ->
            tracker.onPositionUpdated = { posX, posY ->
                onPositionUpdated(posX, posY)
            }
        }
        pathTurns = BearingCalculator.computePathTurns(pathNodes)
        currentSegIdx = 0
        Log.d(TAG, "Initialized with ${pathNodes.size} nodes, ${pathTurns.size} turns")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sensor feeds (call from SensorFusionManager and StepTracker callbacks)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Feed a new filtered heading from SensorFusionManager.
     * Updates arrow direction immediately.
     *
     * [azimuthDeg] – clockwise from North, [0, 360), already Kalman-filtered.
     */
    fun onHeadingUpdated(azimuthDeg: Float) {
        userHeadingDeg = azimuthDeg
        positionTracker?.currentHeadingDeg = azimuthDeg
        recomputeArrowDirection()
    }

    /**
     * Feed a new step event from StepTracker.
     * Advances dead-reckoning position; map matching is applied inside IndoorPositionTracker.
     *
     * [totalSteps]     – cumulative session steps.
     * [distanceMetres] – cumulative session distance (not used here, but useful for UI).
     */
    fun onStep(totalSteps: Long, distanceMetres: Float) {
        // Convert stride length from metres → map pixels
        val stridePx = StepTracker.STRIDE_LENGTH_M * pxPerMetre
        positionTracker?.onStep(stridePx.toDouble())
        // Position update triggers onPositionUpdated callback
    }

    /**
     * Feed logo relocalization from the camera recognition pipeline.
     * Snaps dead-reckoning position to the detected store's graph node.
     *
     * [recognizedNode] – the graph node corresponding to the detected logo.
     */
    fun onLogoDetected(recognizedNode: GraphNode) {
        val snapped = positionTracker?.relocalize(recognizedNode) ?: false
        if (snapped) {
            Log.d(TAG, "Relocalized to '${recognizedNode.shopName}'")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rerouting
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Trigger A* rerouting from current estimated position to [destination].
     * Returns the new path, or null if no route found.
     */
    fun reroute(destination: GraphNode): List<GraphNode>? {
        val tracker = positionTracker ?: return null
        val nearestNode = MallGraphRepository.findNearestNode(
            mallGraph, tracker.posX, tracker.posY
        ) ?: return null

        val newPath = MallGraphRepository.aStarByNodeId(
            mallGraph, nearestNode.id, destination.id
        ) ?: return null

        val newNodes = newPath.nodeIds.mapNotNull { id ->
            mallGraph.nodes.firstOrNull { it.id == id }
        }
        Log.d(TAG, "Rerouted via node ${nearestNode.id} → ${newNodes.size} nodes")
        return newNodes
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private: position update handler
    // ─────────────────────────────────────────────────────────────────────────

    private fun onPositionUpdated(posX: Double, posY: Double) {
        val tracker = positionTracker ?: return

        // Advance segment index based on nearest path node
        val nearestIdx = tracker.nearestPathNodeIndex(pathNodes)
        if (nearestIdx > currentSegIdx) {
            currentSegIdx = nearestIdx
            Log.d(TAG, "Segment advanced to $currentSegIdx")
        }

        // Check arrival at each node
        checkArrival(posX, posY)

        // Recompute arrow direction with updated position
        recomputeArrowDirection()

        _state.posX           = posX
        _state.posY           = posY
        _state.segmentIndex   = currentSegIdx
        _state.isOnPath       = tracker.isOnPath
        _state.deviationPx    = tracker.deviationPx
        _state.stepCount      = tracker.stepCount

        onStateUpdated?.invoke(_state.snapshot())
    }

    private fun recomputeArrowDirection() {
        if (pathNodes.size < 2) return

        // Target bearing: bearing of the segment ahead of the user
        val targetBearing = BearingCalculator.segmentBearing(pathNodes, currentSegIdx)

        // Arrow rotation: how much to rotate to face the target
        val arrowRot = BearingCalculator.arrowRotation(targetBearing, userHeadingDeg)

        // Turn hint
        val hint = BearingCalculator.turnHint(arrowRot)

        _state.targetBearingDeg  = targetBearing
        _state.userHeadingDeg    = userHeadingDeg
        _state.arrowRotationDeg  = arrowRot
        _state.turnDirection     = hint
    }

    private fun checkArrival(posX: Double, posY: Double) {
        if (currentSegIdx >= pathNodes.size - 1) {
            val lastNode = pathNodes.last()
            val dx = lastNode.x - posX
            val dy = lastNode.y - posY
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < ARRIVAL_THRESHOLD_PX) {
                _state.isArrived = true
                Log.d(TAG, "ARRIVED at destination!")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        /** Distance in map pixels to consider the user arrived at a node. */
        const val ARRIVAL_THRESHOLD_PX: Double = 40.0
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Navigation state (immutable snapshot for UI consumption)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Immutable snapshot of the complete navigation state.
 * Consumed by ViewModel → Compose UI without mutation risks.
 */
data class NavigationState(
    /** Current estimated position in map pixel coordinates. */
    val posX: Double = 0.0,
    val posY: Double = 0.0,

    /** Index of the current path segment (node index the user is heading toward). */
    val segmentIndex: Int = 0,

    /** Whether user is currently on a valid path segment (map-matched). */
    val isOnPath: Boolean = true,

    /** Perpendicular deviation from nearest edge (map pixels). */
    val deviationPx: Double = 0.0,

    /** Total steps counted this session. */
    val stepCount: Long = 0L,

    /** Bearing from current segment start to segment end (0–360, clockwise from North). */
    val targetBearingDeg: Float = 0f,

    /** Current user heading from sensor fusion (0–360, clockwise from North). */
    val userHeadingDeg: Float = 0f,

    /**
     * Arrow rotation = targetBearing − userHeading, normalised to (−180, +180].
     * Positive = turn clockwise (RIGHT), negative = counter-clockwise (LEFT).
     * Use this value directly to rotate the UI arrow widget.
     */
    val arrowRotationDeg: Float = 0f,

    /** Simplified turn direction for the HUD instruction label. */
    val turnDirection: TurnDirection = TurnDirection.STRAIGHT,

    /** True once the user reaches the final destination node. */
    val isArrived: Boolean = false
)

/** Mutable working copy — converted to immutable NavigationState via snapshot(). */
private class MutableNavigationState {
    var posX:             Double         = 0.0
    var posY:             Double         = 0.0
    var segmentIndex:     Int            = 0
    var isOnPath:         Boolean        = true
    var deviationPx:      Double         = 0.0
    var stepCount:        Long           = 0L
    var targetBearingDeg: Float          = 0f
    var userHeadingDeg:   Float          = 0f
    var arrowRotationDeg: Float          = 0f
    var turnDirection:    TurnDirection  = TurnDirection.STRAIGHT
    var isArrived:        Boolean        = false

    fun snapshot() = NavigationState(
        posX             = posX,
        posY             = posY,
        segmentIndex     = segmentIndex,
        isOnPath         = isOnPath,
        deviationPx      = deviationPx,
        stepCount        = stepCount,
        targetBearingDeg = targetBearingDeg,
        userHeadingDeg   = userHeadingDeg,
        arrowRotationDeg = arrowRotationDeg,
        turnDirection    = turnDirection,
        isArrived        = isArrived
    )
}
