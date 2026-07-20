package com.example.mallar.ui.navigation

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mallar.data.AStarPath
import com.example.mallar.data.GraphNode
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.navigation.*
import com.example.mallar.voice.LocalIntentParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.mallar.ui.localization.NavigationState

/**
 * UnifiedNavigationViewModel
 * ─────────────────────────────────────────────────────────────────────────────
 * PRODUCTION READY - MOVEMENT TRACKING COMPLETED
 * 
 * Manages the lifecycle of a navigation session and its associated sensors.
 */
class UnifiedNavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    val sessionManager: NavigationSessionManager = NavigationSessionManager(
        mallGraph = MallGraphRepository.loadedGraph 
            ?: throw IllegalStateException("MallGraph not loaded before starting navigation")
    )

    val navState: StateFlow<NavSessionState> = sessionManager.sessionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavSessionState())

    private val _poseEnabled = MutableStateFlow(false)
    val poseEnabled: StateFlow<Boolean> = _poseEnabled.asStateFlow()

    private val orientationManager = OrientationManager()
    val orientationState: StateFlow<OrientationUiState> = orientationManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrientationUiState())

    // ── Sensors (Owned by VM, gated by Screen Lifecycle) ──────────────────────
    private val stepTracker = StepTracker(context)
    private val barometerManager = BarometerManager(context)
    private val sensorFusionManager = SensorFusionManager(context)

    companion object {
        private const val POSE_GRACE_MS = 1500L
    }

    init {
        setupCallbacks()
        startSession()
        enablePoseAfterGrace()
        
        viewModelScope.launch {
            var lastPauseState = false
            navState.collect { state ->
                if (!state.isPausedForFloorTransition) {
                    NavigationState.currentFloor = state.currentFloor
                }
                
                // One-shot barometer reset when transition triggers
                if (state.isPausedForFloorTransition && !lastPauseState) {
                    barometerManager.resetBaseline()
                }
                lastPauseState = state.isPausedForFloorTransition
            }
        }
    }

    // ── Lifecycle Gating ─────────────────────────────────────────────────────

    /** Call from UI DisposableEffect to start sensors only when visible. */
    fun resumeSensors() {
        // 1. Heading (Sensor Fusion)
        sensorFusionManager.onHeadingChanged = { azimuth, _ ->
            if (orientationManager.state.value.active) {
                orientationManager.onHeadingUpdated(azimuth)
            }
            sessionManager.onHeadingUpdated(azimuth)
        }
        
        // 2. Steps (PDR)
        stepTracker.onStep = { total, stride, _ ->
            sessionManager.onStep(total, stride)
            
            val state = navState.value
            if (state.isPausedForFloorTransition && state.pendingFloorTransition != null) {
                checkAutoFloorTransition(state.pendingFloorTransition)
            }
        }
        
        sensorFusionManager.start()
        stepTracker.start()
        barometerManager.start()
    }

    /** Call from UI DisposableEffect to stop sensors when hidden. */
    fun pauseSensors() {
        sensorFusionManager.stop()
        stepTracker.stop()
        barometerManager.stop()
    }

    private fun checkAutoFloorTransition(transition: FloorTransitionHelper.PathFloorTransition) {
        if (!barometerManager.isAvailable) return
        
        val delta = barometerManager.relativeAltitudeDelta
        val requiredDelta = if (transition.toFloor > transition.fromFloor) {
            NavConfig.AUTO_FLOOR_CONFIRM_THRESHOLD_M
        } else {
            -NavConfig.AUTO_FLOOR_CONFIRM_THRESHOLD_M
        }

        val directionCorrect = if (requiredDelta > 0) delta > requiredDelta else delta < requiredDelta
        
        if (directionCorrect) {
            confirmFloorTransition()
        }
    }

    private fun startSession() {
        val path  = NavigationState.aStarPath ?: return
        val graph = MallGraphRepository.loadedGraph ?: return

        val nodes    = path.nodeIds.mapNotNull { id -> graph.nodes.firstOrNull { it.id == id } }
        val destName = nodes.lastOrNull()?.shopName ?: NavigationState.selectedPlace?.brand ?: ""

        if (nodes.size >= 2) {
            sessionManager.initialize(nodes, destName)
            if (NavigationState.startWithAr) {
                sessionManager.switchMode(NavMode.CAMERA)
            }
            orientationManager.start(nodes)
        }
    }

    private fun enablePoseAfterGrace() {
        viewModelScope.launch {
            delay(POSE_GRACE_MS)
            _poseEnabled.value = true
        }
    }

    private fun setupCallbacks() {
        sessionManager.onRerouteNeeded = {
            viewModelScope.launch { performReroute() }
        }
    }

    fun confirmFloorTransition() {
        sessionManager.confirmFloorTransition()
        NavigationState.currentFloor = NavigationFloorState.currentFloor
    }

    private suspend fun performReroute() {
        sessionManager.setRerouting(true)

        val graph = MallGraphRepository.loadedGraph ?: run {
            sessionManager.setRerouting(false); return
        }
        val dest = navState.value.pathNodes.lastOrNull() ?: run {
            sessionManager.setRerouting(false); return
        }
        val segIdx   = navState.value.segmentIdx
        val nearNode = navState.value.pathNodes.getOrNull(segIdx) ?: run {
            sessionManager.setRerouting(false); return
        }

        val newPath = MallGraphRepository.aStarByNodeId(graph, nearNode.id, dest.id)
        if (newPath != null) {
            val newNodes = newPath.nodeIds.mapNotNull { id ->
                graph.nodes.firstOrNull { it.id == id }
            }
            if (newNodes.size >= 2) {
                sessionManager.updatePath(newNodes)
            }
        }

        delay(800)
        sessionManager.setRerouting(false)
    }

    fun switchToMap()    = sessionManager.switchMode(NavMode.MAP)
    fun switchToCamera() = sessionManager.switchMode(NavMode.CAMERA)

    fun toggleMode() {
        val current = navState.value.mode
        sessionManager.switchMode(if (current == NavMode.MAP) NavMode.CAMERA else NavMode.MAP)
    }

    fun setModeSelection(selection: NavigationModeSelection) {
        sessionManager.setModeSelection(selection)
    }

    fun onLogoDetected(node: GraphNode)   = sessionManager.onLogoDetected(node)
    fun setScreenSize(w: Float, h: Float) = sessionManager.setScreenSize(w, h)

    /** Entry point for heading updates from UI sensors. */
    fun onHeadingUpdated(azimuth: Float) {
        if (orientationManager.state.value.active) {
            orientationManager.onHeadingUpdated(azimuth)
        }
        sessionManager.onHeadingUpdated(azimuth)
    }

    fun navigateToNewDestination(shopQuery: String): AStarPath? {
        val graph = MallGraphRepository.loadedGraph ?: return null
        val resolved = LocalIntentParser.fuzzyMatchShop(shopQuery, graph)
            ?: shopQuery.trim().takeIf { it.isNotEmpty() }
            ?: return null
        val destNode = LocalIntentParser.findNodeByName(resolved, graph)
            ?: graph.nodes.firstOrNull { it.shopName?.equals(resolved, ignoreCase = true) == true }
            ?: return null

        val state = navState.value
        if (state.pathNodes.size < 2) return null
        val seg = state.segmentIdx.coerceIn(0, state.pathNodes.lastIndex)
        val startNode = state.pathNodes.getOrNull(seg) ?: return null

        val newPath = MallGraphRepository.aStarByNodeId(graph, startNode.id, destNode.id) ?: return null
        val newNodes = newPath.nodeIds.mapNotNull { id -> graph.nodes.firstOrNull { it.id == id } }
        if (newNodes.size < 2) return null

        val destLabel = destNode.shopName ?: shopQuery
        sessionManager.initialize(newNodes, destLabel)
        return newPath
    }

    fun navigateFromShopToShop(originQuery: String, destQuery: String): AStarPath? {
        val graph = MallGraphRepository.loadedGraph ?: return null
        val oName = LocalIntentParser.fuzzyMatchShop(originQuery, graph) ?: originQuery.trim()
        val dName = LocalIntentParser.fuzzyMatchShop(destQuery, graph) ?: destQuery.trim()
        val startNode = LocalIntentParser.findNodeByName(oName, graph)
            ?: graph.nodes.firstOrNull { it.shopName?.equals(oName, ignoreCase = true) == true }
            ?: return null
        val destNode = LocalIntentParser.findNodeByName(dName, graph)
            ?: graph.nodes.firstOrNull { it.shopName?.equals(dName, ignoreCase = true) == true }
            ?: return null
        val newPath = MallGraphRepository.aStarByNodeId(graph, startNode.id, destNode.id) ?: return null
        val newNodes = newPath.nodeIds.mapNotNull { id -> graph.nodes.firstOrNull { it.id == id } }
        if (newNodes.size < 2) return null
        val destLabel = destNode.shopName ?: destQuery
        sessionManager.initialize(newNodes, destLabel)
        return newPath
    }

    fun showWaypointMessage(msg: String) {
        sessionManager.setWaypointMessage(msg)
        viewModelScope.launch {
            delay(2500)
            sessionManager.setWaypointMessage(null)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pauseSensors()
        sessionManager.destroy()
    }
}
