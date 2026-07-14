package com.example.mallar.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mallar.data.AStarPath
import com.example.mallar.data.GraphNode
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.overlay.OverlayNavState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt
import com.example.mallar.ui.localization.NavigationState

private const val DISPLAY_SCALE = 0.05f  // map pixels → metres
private const val M_PER_MIN     = 80f

// ─────────────────────────────────────────────────────────────────────────────
data class CameraNavUiState(
    val pathNodes: List<GraphNode>       = emptyList(),
    val aStarPath: AStarPath?            = null,
    val destinationName: String          = "",
    val remainingDistanceM: Int          = 0,
    val walkMinutes: Int                 = 0,
    val segmentIdx: Int                  = 0,
    val isOnPath: Boolean                = true,
    val isRerouting: Boolean             = false,
    val isArrived: Boolean               = false,
    val waypointNotification: String?    = null,
    val currentHeadingDeg: Float         = 0f,
    val sessionStepCount: Long           = 0L
)

// ─────────────────────────────────────────────────────────────────────────────
class CameraNavigationViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraNavUiState())
    val uiState: StateFlow<CameraNavUiState> = _uiState.asStateFlow()

    init {
        loadNavigationData()
    }

    private fun loadNavigationData() {
        // Load the A* path set by the navigation flow (same source as the
        // existing ArNavigationViewModel — NavigationState singleton).
        val path  = NavigationState.aStarPath
        val graph = MallGraphRepository.loadedGraph

        if (path == null || graph == null) return

        val nodes    = path.nodeIds.mapNotNull { id -> graph.nodes.firstOrNull { it.id == id } }
        val destName = nodes.lastOrNull()?.shopName ?: ""
        val distM    = (path.totalDistancePx * DISPLAY_SCALE).roundToInt().coerceAtLeast(1)
        val mins     = (distM / M_PER_MIN).coerceAtLeast(1f).roundToInt()

        _uiState.update {
            it.copy(
                pathNodes          = nodes,
                aStarPath          = path,
                destinationName    = destName,
                remainingDistanceM = distM,
                walkMinutes        = mins
            )
        }
    }

    // ── Sensor inputs ─────────────────────────────────────────────────────────

    fun updateHeading(azimuthDeg: Float) {
        _uiState.update { it.copy(currentHeadingDeg = azimuthDeg) }
    }

    fun onStep(totalSteps: Long) {
        _uiState.update { it.copy(sessionStepCount = totalSteps) }
    }

    // ── Navigation state updates from OverlayNavigationEngine ────────────────

    fun onNavStateUpdated(state: OverlayNavState) {
        val distM = (state.remainingDistancePx * DISPLAY_SCALE).roundToInt().coerceAtLeast(0)
        val mins  = if (distM > 0) (distM / M_PER_MIN).coerceAtLeast(1f).roundToInt() else 0

        _uiState.update {
            it.copy(
                segmentIdx         = state.segmentIdx,
                isOnPath           = state.isOnPath,
                remainingDistanceM = distM,
                walkMinutes        = mins
            )
        }
    }

    // ── Rerouting ─────────────────────────────────────────────────────────────

    fun triggerReroute(onNewPathReady: (List<GraphNode>) -> Unit) {
        if (_uiState.value.isRerouting) return   // already rerouting

        _uiState.update { it.copy(isRerouting = true) }

        viewModelScope.launch {
            val graph = MallGraphRepository.loadedGraph
            val dest  = _uiState.value.pathNodes.lastOrNull()

            if (graph == null || dest == null) {
                _uiState.update { it.copy(isRerouting = false) }
                return@launch
            }

            // Find nearest node to user's current estimated map position.
            // We use the existing path's progress as a proxy for position.
            val segIdx   = _uiState.value.segmentIdx
            val nearNode = _uiState.value.pathNodes.getOrNull(segIdx)

            if (nearNode == null) {
                _uiState.update { it.copy(isRerouting = false) }
                return@launch
            }

            val newPath = MallGraphRepository.aStarByNodeId(graph, nearNode.id, dest.id)
            if (newPath != null) {
                val newNodes = newPath.nodeIds.mapNotNull { id ->
                    graph.nodes.firstOrNull { it.id == id }
                }
                if (newNodes.size >= 2) {
                    _uiState.update {
                        it.copy(
                            pathNodes = newNodes,
                            aStarPath = newPath
                        )
                    }
                    onNewPathReady(newNodes)
                }
            }

            // Brief delay before clearing rerouting state so banner is visible
            delay(1200)
            _uiState.update { it.copy(isRerouting = false) }
        }
    }

    // ── Arrival / waypoints ───────────────────────────────────────────────────

    fun onArrived() {
        _uiState.update { it.copy(isArrived = true) }
    }

    fun onWaypointReached(idx: Int, node: GraphNode) {
        val name = node.shopName ?: "Waypoint $idx"
        val msg  = "✅ Passed: $name"
        _uiState.update { it.copy(waypointNotification = msg) }

        viewModelScope.launch {
            delay(2500)
            if (_uiState.value.waypointNotification == msg) {
                _uiState.update { it.copy(waypointNotification = null) }
            }
        }
    }
}
