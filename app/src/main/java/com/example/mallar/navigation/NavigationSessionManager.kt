package com.example.mallar.navigation

import android.util.Log
import com.example.mallar.data.GraphNode
import com.example.mallar.data.MallGraph
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.overlay.OverlayProjectionEngine
import com.example.mallar.overlay.ProjectedPoint
import com.example.mallar.overlay.TurnInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "NavSessionManager"

enum class NavMode { MAP, CAMERA }

enum class NavigationModeSelection { AUTO, AR, MAP }

/**
 * Snapshot of the current navigation state.
 */
data class NavSessionState(
    val pathNodes: List<GraphNode>    = emptyList(),
    val segmentIdx: Int               = 0,
    val currentFloor: Int             = 2,
    val isPausedForFloorTransition: Boolean = false,
    val pendingFloorTransition: FloorTransitionHelper.PathFloorTransition? = null,
    val destinationName: String       = "",
    val userMapX: Float               = 0f,
    val userMapY: Float               = 0f,
    val headingDeg: Float             = 0f,
    val remainingDistanceM: Int       = 0,
    val walkMinutes: Int              = 0,
    val mode: NavMode                 = NavMode.MAP,
    val modeSelection: NavigationModeSelection = NavigationModeSelection.AUTO,
    val projectedPoints: List<ProjectedPoint> = emptyList(),
    val turnInfo: TurnInfo?           = null,
    val isRerouting: Boolean          = false,
    val isArrived: Boolean            = false,
    val isOnPath: Boolean             = true,
    val waypointMessage: String?      = null,
    val totalSteps: Long              = 0L,
    val driftLevel: DriftMonitor.DriftLevel = DriftMonitor.DriftLevel.OK,
    val relocReason: String?          = null
)

/**
 * NavigationSessionManager
 * 
 * Orchestrates the navigation trip. Managed as an instance by a ViewModel.
 */
class NavigationSessionManager(
    private val mallGraph: MallGraph,
    private val pxPerMetre: Float = NavConfig.PIXELS_PER_METER
) {

    private val _sessionState = MutableStateFlow(NavSessionState())
    val sessionState: StateFlow<NavSessionState> = _sessionState.asStateFlow()

    private var positionTracker: IndoorPositionTracker? = null
    private val projectionEngine = OverlayProjectionEngine()
    private val driftMonitor     = DriftMonitor()
    private val smoother         = PositionSmoother()

    private var pathTransitions: List<FloorTransitionHelper.PathFloorTransition> = emptyList()
    private var completedTransitionCount = 0

    var onRerouteNeeded: (() -> Unit)? = null
    var onArrived: (() -> Unit)? = null
    var onRelocalizationNeeded: ((reason: String) -> Unit)? = null
    var onFloorTransitionReached: ((FloorTransitionHelper.PathFloorTransition) -> Unit)? = null

    /**
     * Prepares the session with a calculated path.
     */
    fun initialize(pathNodes: List<GraphNode>, destinationName: String) {
        if (pathNodes.size < 2) {
            Log.w(TAG, "Path too short — cannot initialise")
            return
        }

        val startNode = pathNodes.first()
        positionTracker = IndoorPositionTracker(mallGraph, startNode, pxPerMetre)

        driftMonitor.onRelocalizationNeeded = { reason ->
            _sessionState.update { it.copy(relocReason = reason) }
            onRelocalizationNeeded?.invoke(reason)
        }

        pathTransitions = FloorTransitionHelper.scanPathTransitions(pathNodes)
        completedTransitionCount = 0

        val distM = (totalPathDistancePx(pathNodes) / pxPerMetre).roundToInt().coerceAtLeast(1)
        val mins  = (distM / 80f).coerceAtLeast(1f).roundToInt()
        val startFloor = startNode.floor

        _sessionState.update {
            it.copy(
                pathNodes          = pathNodes,
                destinationName    = destinationName,
                userMapX           = startNode.x.toFloat(),
                userMapY           = startNode.y.toFloat(),
                remainingDistanceM = distM,
                walkMinutes        = mins,
                currentFloor       = startFloor,
                isPausedForFloorTransition = false,
                pendingFloorTransition = null,
            )
        }
        NavigationFloorState.currentFloor = startFloor

        Log.d(TAG, "Initialised: ${pathNodes.size} nodes, dest=$destinationName")
        recomputeForCurrentMode()
    }

    /**
     * Cleanup resources when the trip ends.
     */
    fun destroy() {
        positionTracker = null
        onRerouteNeeded = null
        onArrived       = null
        onRelocalizationNeeded = null
        pathTransitions = emptyList()
        Log.d(TAG, "Session Destroyed")
    }

    fun confirmFloorTransition() {
        val state = _sessionState.value
        val pending = state.pendingFloorTransition ?: return
        val path = state.pathNodes
        val arrive = path.getOrNull(pending.arriveNodeIndex) ?: return

        completedTransitionCount++
        positionTracker?.relocalize(arrive)
        smoother.reset()

        _sessionState.update {
            it.copy(
                isPausedForFloorTransition = false,
                pendingFloorTransition     = null,
                currentFloor               = pending.toFloor,
                segmentIdx                 = pending.arriveNodeIndex,
                userMapX                   = arrive.x.toFloat(),
                userMapY                   = arrive.y.toFloat(),
                isOnPath                   = true,
            )
        }
        NavigationFloorState.currentFloor = pending.toFloor
        recomputeForCurrentMode()
    }

    fun onHeadingUpdated(azimuthDeg: Float) {
        positionTracker?.currentHeadingDeg = azimuthDeg
        _sessionState.update { it.copy(headingDeg = azimuthDeg) }
        if (_sessionState.value.mode == NavMode.CAMERA) {
            recomputeForCurrentMode()
        }
    }

    fun onStep(totalSteps: Long, strideLengthM: Float) {
        val tracker = positionTracker ?: return
        val state = _sessionState.value
        
        if (state.isPausedForFloorTransition) {
            _sessionState.update { it.copy(totalSteps = totalSteps) }
            return
        }

        // 1. Calculate next position state atomically
        val snapResult = tracker.onStep(
            strideM = strideLengthM,
            path = state.pathNodes,
            currentSegmentIdx = state.segmentIdx
        )

        // 2. Process updates
        processNavigationUpdate(snapResult, totalSteps)
    }

    private fun processNavigationUpdate(
        snapResult: IndoorPositionTracker.ConstraintResult,
        totalSteps: Long
    ) {
        val state = _sessionState.value
        val path  = state.pathNodes

        driftMonitor.onStep(
            posX        = snapResult.snappedX,
            posY        = snapResult.snappedY,
            isOnPath    = snapResult.isOnPath,
            deviationPx = snapResult.deviationPx,
            headingDeg  = state.headingDeg
        )
        val drift = driftMonitor.driftState

        val transition = FloorTransitionHelper.pendingTransition(
            path = path,
            transitions = pathTransitions,
            completedCount = completedTransitionCount,
            segmentIdx = snapResult.bestSegmentIdx,
            userX = snapResult.snappedX,
            userY = snapResult.snappedY,
        )
        if (transition != null) {
            _sessionState.update {
                it.copy(
                    isPausedForFloorTransition = true,
                    pendingFloorTransition     = transition,
                    userMapX                   = snapResult.snappedX.toFloat(),
                    userMapY                   = snapResult.snappedY.toFloat(),
                    segmentIdx                 = snapResult.bestSegmentIdx,
                    totalSteps                 = totalSteps
                )
            }
            onFloorTransitionReached?.invoke(transition)
            return
        }

        val (smoothX, smoothY) = smoother.smoothPosition(snapResult.snappedX, snapResult.snappedY)
        val headingForSmooth = positionTracker?.currentHeadingDeg ?: state.headingDeg
        val smoothHeading = smoother.smoothHeading(headingForSmooth)

        val finalNode = path.lastOrNull()
        if (finalNode != null) {
            val dx = finalNode.x - smoothX; val dy = finalNode.y - smoothY
            if (sqrt(dx * dx + dy * dy) < NavConfig.ARRIVAL_THRESHOLD_PX) {
                if (!state.isArrived) {
                    _sessionState.update { it.copy(isArrived = true, totalSteps = totalSteps) }
                    onArrived?.invoke()
                }
                return
            }
        }

        if (!snapResult.isOnPath && snapResult.deviationPx > NavConfig.REROUTE_THRESHOLD_PX && !state.isRerouting) {
            _sessionState.update { it.copy(isRerouting = true, isOnPath = false) }
            onRerouteNeeded?.invoke()
        }

        val remainPx = computeRemainingDistancePx(path, snapResult.bestSegmentIdx, smoothX, smoothY)
        val remainM  = (remainPx / pxPerMetre).roundToInt().coerceAtLeast(0)
        val mins     = if (remainM > 0) (remainM / 80f).coerceAtLeast(1f).roundToInt() else 0
        val activeFloor = path.getOrNull(snapResult.bestSegmentIdx)?.floor ?: state.currentFloor

        _sessionState.update {
            it.copy(
                userMapX           = smoothX.toFloat(),
                userMapY           = smoothY.toFloat(),
                headingDeg         = smoothHeading,
                segmentIdx         = snapResult.bestSegmentIdx,
                remainingDistanceM = remainM,
                walkMinutes        = mins,
                isOnPath           = snapResult.isOnPath,
                currentFloor       = activeFloor,
                driftLevel         = drift.level,
                relocReason        = if (drift.relocNeeded) drift.relocReason else it.relocReason,
                totalSteps         = totalSteps
            )
        }
        NavigationFloorState.currentFloor = activeFloor
        recomputeForCurrentMode()
    }

    fun onLogoDetected(node: GraphNode) {
        positionTracker?.relocalize(node)
        smoother.reset()
        driftMonitor.onRelocalized()
        _sessionState.update { it.copy(relocReason = null, driftLevel = DriftMonitor.DriftLevel.OK) }
        recomputeForCurrentMode()
    }

    fun switchMode(newMode: NavMode) {
        if (_sessionState.value.mode == newMode) return
        _sessionState.update { it.copy(mode = newMode) }
        if (newMode == NavMode.CAMERA) recomputeForCurrentMode()
    }

    fun setModeSelection(selection: NavigationModeSelection) {
        if (_sessionState.value.modeSelection == selection) return
        _sessionState.update { it.copy(modeSelection = selection) }
        when (selection) {
            NavigationModeSelection.AUTO -> { }
            NavigationModeSelection.AR -> switchMode(NavMode.CAMERA)
            NavigationModeSelection.MAP -> switchMode(NavMode.MAP)
        }
    }

    fun updatePath(newPath: List<GraphNode>) {
        if (newPath.size < 2) return
        val tracker = positionTracker
        if (tracker != null) {
            val nearNode = MallGraphRepository.findNearestNode(mallGraph, tracker.posX, tracker.posY)
            if (nearNode != null) {
                tracker.relocalize(nearNode)
                smoother.reset()
            }
        }
        pathTransitions = FloorTransitionHelper.scanPathTransitions(newPath)
        completedTransitionCount = 0
        val floor = newPath.firstOrNull()?.floor ?: 2
        _sessionState.update {
            it.copy(
                pathNodes = newPath,
                segmentIdx = 0,
                isRerouting = false,
                currentFloor = floor,
                isPausedForFloorTransition = false,
                pendingFloorTransition = null,
            )
        }
        NavigationFloorState.currentFloor = floor
        recomputeForCurrentMode()
    }

    fun setRerouting(active: Boolean) {
        _sessionState.update { it.copy(isRerouting = active) }
    }

    fun setScreenSize(w: Float, h: Float, fovDeg: Float = 68f) {
        projectionEngine.screenW = w
        projectionEngine.screenH = h
        projectionEngine.fovDeg  = fovDeg
        recomputeForCurrentMode()
    }

    fun setWaypointMessage(msg: String?) {
        _sessionState.update { it.copy(waypointMessage = msg) }
    }

    private fun recomputeForCurrentMode() {
        val state = _sessionState.value
        val path = state.pathNodes
        if (path.size < 2) return

        val seg = state.segmentIdx
        val nextNode = path.getOrNull(seg + 1)
        val turnInfo = if (nextNode != null) {
            projectionEngine.computeTurnInfo(
                nextNodeMapX = nextNode.x.toFloat(),
                nextNodeMapY = nextNode.y.toFloat(),
                userMapX     = state.userMapX,
                userMapY     = state.userMapY,
                headingDeg   = state.headingDeg
            )
        } else null

        if (state.mode == NavMode.CAMERA) {
            val projected = projectionEngine.project(
                pathNodes         = path,
                userMapX          = state.userMapX,
                userMapY          = state.userMapY,
                headingDeg        = state.headingDeg,
                lookaheadStartIdx = seg
            )
            _sessionState.update {
                it.copy(projectedPoints = projected, turnInfo = turnInfo)
            }
        } else {
            _sessionState.update { it.copy(turnInfo = turnInfo) }
        }
    }

    private fun computeRemainingDistancePx(
        path: List<GraphNode>, fromIdx: Int, userX: Double, userY: Double
    ): Float {
        if (path.size < 2 || fromIdx >= path.size - 1) return 0f
        var d = 0.0
        for (i in fromIdx until path.size - 1) {
            val a = path[i]; val b = path[i + 1]
            d += sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
        }
        return d.toFloat()
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
}
