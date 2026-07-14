package com.example.mallar.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mallar.data.AStarPath
import com.example.mallar.data.GraphNode
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.navigation.NavMode
import com.example.mallar.navigation.NavSessionState
import com.example.mallar.navigation.NavigationFloorState
import com.example.mallar.navigation.NavigationSessionManager
import com.example.mallar.navigation.NavigationModeSelection
import com.example.mallar.navigation.OrientationManager
import com.example.mallar.navigation.OrientationUiState
import com.example.mallar.voice.LocalIntentParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.mallar.ui.localization.NavigationState

class UnifiedNavigationViewModel : ViewModel() {

    init {
        // SINGLETON RESET FIX: fresh instance for every new navigation trip.
        // Must happen BEFORE navState evaluates NavigationSessionManager.instance
        NavigationSessionManager.reset()
    }

    private val sessionManager: NavigationSessionManager
        get() = NavigationSessionManager.instance

    val navState: StateFlow<NavSessionState> = sessionManager.sessionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavSessionState())

    private val _poseEnabled = MutableStateFlow(false)
    val poseEnabled: StateFlow<Boolean> = _poseEnabled.asStateFlow()

    // Orientation phase: runs once, before normal navigation is shown.
    // See OrientationManager for details; kept independent of NavigationSessionManager
    // so the existing navigation pipeline requires no behavioural changes.
    private val orientationManager = OrientationManager()
    val orientationState: StateFlow<OrientationUiState> = orientationManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrientationUiState())

    companion object {
        private const val POSE_GRACE_MS = 1500L
    }

    init {
        setupRerouteCallback()
        startSession()
        enablePoseAfterGrace()
        viewModelScope.launch {
            navState.collect { state ->
                if (!state.isPausedForFloorTransition) {
                    NavigationState.currentFloor = state.currentFloor
                }
            }
        }
    }

    private fun startSession() {
        val path  = NavigationState.aStarPath ?: return
        val graph = MallGraphRepository.loadedGraph ?: return

        val nodes    = path.nodeIds.mapNotNull { id -> graph.nodes.firstOrNull { it.id == id } }
        val destName = nodes.lastOrNull()?.shopName ?: NavigationState.selectedPlace?.brand ?: ""

        if (nodes.size >= 2) {
            sessionManager.initialize(nodes, destName)
            NavigationState.currentFloor = NavigationFloorState.currentFloor

            // Apply the user's AR/Map choice after initialising
            if (NavigationState.startWithAr) {
                sessionManager.switchMode(NavMode.CAMERA)
            }

            // Orientation phase: face the first leg of the route before the
            // existing navigation UI (step tracking / AR / map) takes over.
            orientationManager.start(nodes)
        }
    }

    private fun enablePoseAfterGrace() {
        viewModelScope.launch {
            delay(POSE_GRACE_MS)
            _poseEnabled.value = true
        }
    }

    private fun setupRerouteCallback() {
        sessionManager.onRerouteNeeded = {
            viewModelScope.launch { performReroute() }
        }
        sessionManager.onArrived = {
            /* arrival state is set inside sessionManager — UI reads it */
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

    fun onHeadingUpdated(azimuthDeg: Float) {
        // Orientation phase consumes the same compass stream the existing
        // navigation pipeline already uses — no new sensor listener needed.
        if (orientationManager.state.value.active) {
            orientationManager.onHeadingUpdated(azimuthDeg)
        }
        sessionManager.onHeadingUpdated(azimuthDeg)
    }

    fun onStep(totalSteps: Long)             = sessionManager.onStep(totalSteps)
    fun onLogoDetected(node: GraphNode)      = sessionManager.onLogoDetected(node)
    fun setScreenSize(w: Float, h: Float)    = sessionManager.setScreenSize(w, h)

    /**
     * Recompute A* from the user's current path segment to a new store (voice mid-trip).
     */
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

    /** Voice: explicit start store → end store (e.g. from Zara to Bershka). */
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
        // Destroy session when user leaves navigation screen.
        // Next startSession() call will reset() and create a fresh instance.
        sessionManager.destroy()
    }
}