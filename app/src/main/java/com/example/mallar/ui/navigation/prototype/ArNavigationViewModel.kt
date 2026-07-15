package com.example.mallar.ui.navigation.prototype

/**
 * PROTOTYPE IMPLEMENTATION
 *
 * Experimental AR navigation screen.
 *
 * Development was paused before completion.
 *
 * This file is intentionally preserved because the
 * future AR navigation system will likely reuse
 * concepts from this implementation.
 *
 * Not used in production.
 */






/*
package com.example.mallar.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mallar.ar.NavigationPhase
import com.example.mallar.ar.StartResult
import com.example.mallar.ui.ar.TurnHint
import com.example.mallar.navigation.TurnDirection
import com.example.mallar.data.AStarDirection
import com.example.mallar.data.AStarPath
import com.example.mallar.data.GraphNode
import com.example.mallar.data.MallGraphRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val DISPLAY_SCALE = 0.05f
private const val M_PER_MIN = 80f

data class StepItem(
    val label: String,
    val isWaypoint: Boolean,
    val nodeIndex: Int?
)

// ─────────────────────────────────────────────────────────────────────────────
data class ArNavigationUiState(
    val pathNodes: List<GraphNode> = emptyList(),
    val aStarPath: AStarPath? = null,
    val destinationNode: GraphNode? = null,
    val distanceM: Int = 0,
    val walkMinutes: Int = 0,
    val statusText: String = "📷 Point at the floor and move slowly…",
    val segmentIndex: Int = 0,
    val showRouteSheet: Boolean = false,
    val showStoreCard: Boolean = true,
    val reachedNotification: String? = null,
    val routeSteps: List<StepItem> = emptyList(),
    val currentInstructionLabel: String = "",
    val orientationTurnHint: TurnHint = TurnHint.STRAIGHT,

    // ── Phase ─────────────────────────────────────────────────────────────────
    val navigationPhase: NavigationPhase = NavigationPhase.SCANNING,
    val startPinReady: Boolean = false,

    */
/**
     * Distance (m) from the camera to the start-pin anchor.
     * Shown so the user knows how far they still need to walk.
     *//*

    val distToStartPinM: Float = Float.MAX_VALUE,

    // ── Manual calibration ────────────────────────────────────────────────────
    */
/**
     * True while the user is in MANUAL_CALIBRATION phase and needs to
     * point their phone toward the destination.
     *//*

    val awaitingManualDirectionConfirm: Boolean = false,

    // ── Debug overlay (optional — toggle via showDebugOverlay) ────────────────
    val showDebugOverlay: Boolean = false,
    val debugRawHeadingDeg: Float = 0f,
    val debugCalculatedBearing: Float = 0f,
    val debugAppliedOffset: Float = 0f,

    // ── Step tracking ───────────────────────────────────────────────────────────────
    */
/** Total steps counted this navigation session (hardware or software). *//*

    val sessionStepCount: Long = 0L,
    */
/** Estimated distance walked via dead-reckoning (metres). *//*

    val sessionDistanceM: Float = 0f
)

// ─────────────────────────────────────────────────────────────────────────────
class ArNavigationViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ArNavigationUiState())
    val uiState: StateFlow<ArNavigationUiState> = _uiState.asStateFlow()

    init {
        initializePathData()
    }

    private fun initializePathData() {
        val path  = NavigationState.aStarPath
        val graph = MallGraphRepository.loadedGraph

        if (path == null || graph == null) {
            updateStatusText("❌ No navigation path loaded")
            return
        }

        val nodes    = path.nodeIds.mapNotNull { id -> graph.nodes.firstOrNull { it.id == id } }
        val destNode = nodes.lastOrNull()

        val distM   = ((path.totalDistancePx) * DISPLAY_SCALE).roundToInt().coerceAtLeast(1)
        val walkMin = (distM / M_PER_MIN).coerceAtLeast(1f).roundToInt()

        val steps            = buildStepItems(nodes, path)
        val instructionLabel = buildInstructionLabel(nodes, path, 0)

        _uiState.update {
            it.copy(
                pathNodes               = nodes,
                aStarPath               = path,
                destinationNode         = destNode,
                distanceM               = distM,
                walkMinutes             = walkMin,
                routeSteps              = steps,
                currentInstructionLabel = instructionLabel
            )
        }
    }

    // ── Status / generic ──────────────────────────────────────────────────────

    fun updateStatusText(newText: String) {
        _uiState.update { it.copy(statusText = newText) }
    }

    fun toggleRouteSheet() {
        _uiState.update { it.copy(showRouteSheet = !it.showRouteSheet) }
    }

    fun closeStoreCard() {
        _uiState.update { it.copy(showStoreCard = false) }
    }

    fun toggleDebugOverlay() {
        _uiState.update { it.copy(showDebugOverlay = !it.showDebugOverlay) }
    }

    // ── Debug values (pushed from AR frame handler) ───────────────────────────

    fun updateDebugValues(rawHeading: Float, bearing: Float, offset: Float) {
        _uiState.update {
            it.copy(
                debugRawHeadingDeg     = rawHeading,
                debugCalculatedBearing = bearing,
                debugAppliedOffset     = offset
            )
        }
    }

    // ── Phase transitions ─────────────────────────────────────────────────────

    */
/** Called once the anchor + start pin are placed. *//*

    fun onStartPinPlaced() {
        _uiState.update {
            it.copy(
                navigationPhase = NavigationPhase.AWAITING_USER,
                startPinReady   = true,
                statusText      = "📍 Walk to the green pin and tap \"I'm Here\""
            )
        }
    }

    */
/** Called every frame with camera distance from start-pin anchor. *//*

    fun updateDistToStartPin(distM: Float) {
        _uiState.update { it.copy(distToStartPinM = distM) }
    }

    */
/**
     * Called from the "I'm Here" button once the user has walked to the pin.
     * Transitions to MANUAL_CALIBRATION when close enough.
     *//*

    fun onUserConfirmedStart(result: StartResult) {
        when (result) {
            is StartResult.Ok -> {
                _uiState.update {
                    it.copy(
                        navigationPhase              = NavigationPhase.MANUAL_CALIBRATION,
                        awaitingManualDirectionConfirm = true,
                        statusText                   = "🎯 Point your phone toward the destination, then tap \"Confirm Direction\""
                    )
                }
            }
            is StartResult.TooFar -> {
                val meters = result.distanceM.roundToInt()
                updateStatusText("⚠️ You are ${meters}m from the start pin — move closer and try again")
            }
            is StartResult.Error -> {
                updateStatusText("❌ ${result.reason}")
            }
        }
    }

    */
/** Called when the manager successfully locks the manual calibration. *//*

    fun onManualCalibrationConfirmed() {
        _uiState.update {
            it.copy(
                navigationPhase                = NavigationPhase.NAVIGATING,
                awaitingManualDirectionConfirm = false,
                statusText                     = "🎯 Follow the arrows to your destination!"
            )
        }
    }

    */
/** Called when manual calibration fails (wrong phase, etc.). *//*

    fun onManualCalibrationFailed() {
        updateStatusText("⚠️ Calibration failed — please try again")
    }

    // ── Arrival ───────────────────────────────────────────────────────────────

    fun onNodeReached(reachedIdx: Int) {
        val nodes = _uiState.value.pathNodes
        val path  = _uiState.value.aStarPath
        val currentSeg = _uiState.value.segmentIndex

        if (reachedIdx <= currentSeg) return

        val nodeName  = nodes.getOrNull(reachedIdx)?.shopName ?: "Waypoint $reachedIdx"
        val isArrived = reachedIdx >= nodes.size - 1

        val notificationText = if (isArrived) {
            updateStatusText("🏁 You have arrived!")
            "🏁 Arrived at $nodeName!"
        } else {
            "✅ Reached: $nodeName"
        }

        val remainingDistM = if (path != null && !isArrived) {
            var remainPx = 0.0
            for (i in reachedIdx until nodes.size - 1) {
                val a = nodes.getOrNull(i) ?: continue
                val b = nodes.getOrNull(i + 1) ?: continue
                val dx = a.x - b.x; val dy = a.y - b.y
                remainPx += kotlin.math.sqrt(dx * dx + dy * dy)
            }
            (remainPx * DISPLAY_SCALE).roundToInt().coerceAtLeast(1)
        } else if (isArrived) 0 else _uiState.value.distanceM

        val remainingMins =
            if (remainingDistM > 0) (remainingDistM / M_PER_MIN).coerceAtLeast(1f).roundToInt()
            else 0

        _uiState.update {
            it.copy(
                segmentIndex        = reachedIdx,
                reachedNotification = notificationText,
                distanceM           = remainingDistM,
                walkMinutes         = remainingMins
            )
        }

        viewModelScope.launch {
            delay(2500)
            if (_uiState.value.reachedNotification == notificationText) {
                _uiState.update { it.copy(reachedNotification = null) }
            }
        }

        updateInstructionLabel(reachedIdx)
    }

    // ── Orientation & Rerouting ───────────────────────────────────────────────

    fun updateOrientationGuidance(hint: TurnHint) {
        if (_uiState.value.orientationTurnHint != hint) {
            _uiState.update { it.copy(orientationTurnHint = hint) }
        }
    }

    */
/**
     * Called from StepTracker.onStep via DisposableEffect in the Screen.
     *
     * Updates session step count and dead-reckoning distance in the UI.
     * The actual position advance is handled by IndoorPositionTracker
     * (via NavigationEngine.onStep) in a full integration.
     * Here we just persist the stats for display.
     *//*

    fun onStepDetected(totalSteps: Long, distanceMetres: Float) {
        _uiState.update {
            it.copy(
                sessionStepCount = totalSteps,
                sessionDistanceM = distanceMetres
            )
        }
    }

    fun onRerouteTriggered(userArX: Float, userArZ: Float, onNewPathReady: (List<GraphNode>) -> Unit) {
        val graph = MallGraphRepository.loadedGraph ?: return
        val dest  = _uiState.value.destinationNode ?: return

        // 1. Find nearest graph node to the user's AR position
        // Map AR space back to map space?
        // Wait, AR->Map is hard. Simplest way: just notify UI we deviated.
        // Actually, we can use the currently closest node from the path as a fallback.
        // For now, let's just log or show a message.
        updateStatusText("🔄 Rerouting...")

        // TODO: Map AR (x,z) back to Map (x,y) to find nearest node, then A* to dest.
        // This requires the inverse of transformer.toArLocal().
    }

    // ── Instruction building ──────────────────────────────────────────────────

    private fun updateInstructionLabel(segmentIndex: Int) {
        val nodes = _uiState.value.pathNodes
        val path  = _uiState.value.aStarPath
        _uiState.update {
            it.copy(currentInstructionLabel = buildInstructionLabel(nodes, path, segmentIndex))
        }
    }

    private fun buildInstructionLabel(
        pathNodes: List<GraphNode>,
        aStarPath: AStarPath?,
        segmentIndex: Int
    ): String {
        if (pathNodes.size < 2) return "▲  Head to Next Stop"
        val nextNode = pathNodes.getOrNull(segmentIndex + 1)
        val step     = aStarPath?.steps?.getOrNull(segmentIndex)
        return when (step?.direction) {
            AStarDirection.LEFT     -> "↰  Turn Left"
            AStarDirection.RIGHT    -> "↱  Turn Right"
            AStarDirection.STRAIGHT -> "↑  Go Straight"
            AStarDirection.ARRIVED  -> "🏁  You Arrived!"
            else -> "▲  Head to ${nextNode?.shopName ?: "Next Stop"}"
        }
    }

    private fun buildStepItems(
        pathNodes: List<GraphNode>,
        aStarPath: AStarPath?
    ): List<StepItem> {
        val items = mutableListOf<StepItem>()
        if (pathNodes.isEmpty()) return items

        val instructions = (aStarPath?.steps ?: emptyList())
            .filter { it.direction != AStarDirection.ARRIVED }

        items += StepItem(pathNodes[0].shopName ?: "Start", isWaypoint = true, nodeIndex = 0)

        instructions.forEachIndexed { instrIdx, instr ->
            val nextInstrNodeIdx =
                instructions.getOrNull(instrIdx + 1)?.nodeIndex ?: pathNodes.size - 1

            for (ni in instr.nodeIndex + 1 until nextInstrNodeIdx) {
                val n = pathNodes.getOrNull(ni) ?: continue
                if (n.shopName != null) {
                    items += StepItem(n.shopName, isWaypoint = true, nodeIndex = ni)
                }
            }

            val distM = (instr.distancePx * DISPLAY_SCALE).roundToInt().coerceAtLeast(1)
            val dirText = when (instr.direction) {
                AStarDirection.LEFT     -> "↰  Turn Left"
                AStarDirection.RIGHT    -> "↱  Turn Right"
                AStarDirection.STRAIGHT -> "↑  Go Straight  (~${distM}m)"
                else                    -> null
            }
            if (dirText != null) {
                items += StepItem(dirText, isWaypoint = false, nodeIndex = null)
            }
        }

        val lastInstrNodeIdx = instructions.lastOrNull()?.nodeIndex ?: 0
        for (ni in lastInstrNodeIdx + 1 until pathNodes.size - 1) {
            val n = pathNodes.getOrNull(ni) ?: continue
            if (n.shopName != null) {
                items += StepItem(n.shopName, isWaypoint = true, nodeIndex = ni)
            }
        }

        if (pathNodes.size >= 2) {
            items += StepItem(
                pathNodes.last().shopName ?: "Destination",
                isWaypoint = true,
                nodeIndex  = pathNodes.size - 1
            )
        }

        return items
    }
}*/
