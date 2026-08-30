# Phase 5 Implementation Report

## Status

Implementation complete for Phase 5 — Localization Layer (Module 4). The implementation follows the Phase 5 scope in the AR Implementation Roadmap and preserves the approved/frozen architecture and engineering boundaries.

## Requirements checked

- Facility transform is owned by `LocalizationLayer` and updated only after Fix Validation Gate approval.
- The existing pre-navigation scan result is consumed through `NavigationSessionSnapshot`; no second initial localization flow was introduced.
- Periodic re-fix attempts are proximity-gated against landmark graph nodes, checked at a 1-second cadence, and throttled to one attempt per 4 seconds.
- Recognition uses an image copied from the manager-owned ARCore `Frame`; no second camera session or CameraX path is created for AR navigation.
- Recognition runs off the render thread and has a single-flight guard so overlapping attempts cannot occur.
- `landmarkCount == 1` produces a provisional fix; `landmarkCount >= 2` produces a confirmed fix.
- Every candidate passes displacement plausibility, graph plausibility, and tier-appropriate tolerance checks before changing the transform.
- Every accepted fix rebases the local tracking origin through a new `FacilityTransform`.
- Rejected candidates leave the existing transform unchanged and are counted for future recovery handling.

## Main implementation files

- `app/src/main/java/com/example/mallar/ar/LocalizationLayer.kt`
  - `FacilityTransform`, `CandidateFix`, confidence tiers, Fix Validation Gate, proximity/throttle scheduler, and Module 4 state holder.
- `app/src/main/java/com/example/mallar/ar/ArCoreSessionManager.kt`
  - Immutable ARCore camera-image copy for periodic recognition, with the image closed before asynchronous processing.
- `app/src/main/java/com/example/mallar/ar/ui/ArSceneViewWrapper.kt`
  - Shared-session frame handoff, local pose capture, background recognition invocation, and safe completion/cancellation of re-fix attempts.
- `app/src/main/java/com/example/mallar/ar/model/ArDataModels.kt`
  - Snapshot fields for the initial heading and AR-start state.
- `app/src/main/java/com/example/mallar/ar/NavigationSessionInputAdapter.kt`
  - Single snapshot boundary read for Phase 5 initialization data.
- `app/src/main/java/com/example/mallar/ui/navigation/UnifiedNavigationViewModel.kt`
  - Creates the session-scoped localization layer and supplies the initial scan node/heading.
- `app/src/main/java/com/example/mallar/ui/navigation/UnifiedNavigationScreen.kt`
  - Passes the localization layer into the Camera-mode AR wrapper.
- `app/src/test/java/com/example/mallar/ar/LocalizationLayerTest.kt`
  - Tests confidence tiers, rejected displacement, rebasing, proximity/throttle behavior, and concurrent single-flight stress.

## Frozen-file and scope audit

- The frozen architecture, engineering specification, roadmap, testing plan, and implementation playbook under `docs/AR/` were not modified.
- No changes were made to `ArCoreSessionManager` ownership semantics, navigation algorithms, route geometry, anchor/rendering behavior, or Phase 6+ functionality.
- The Phase 4 diagnostic cube remains diagnostic only; it is intentionally not driven by the Phase 5 transform.
- The existing CameraX scan flow remains the owner of the camera until disposal; ARCore remains the sole camera source after navigation starts.

## Verification

Completed successfully:

- `./gradlew.bat :app:testDebugUnitTest --console=plain`
- `./gradlew.bat :app:assembleDebug --console=plain`

The automated tests pass and the debug APK assembles successfully. Device validation is still required for the roadmap's live attempt log: confirm that real recognition attempts remain inside the configured proximity radius and never violate the throttle interval during repeated Galaxy S22 Ultra sessions.

## Deliberate extension

The single-flight recognition guard and concurrent stress test were added as a contained reliability improvement. They do not change the frozen architecture or Phase 5 trigger model; they prevent asynchronous recognition overlap and ensure a failed image/recognition attempt releases the scheduler safely.

## Source evidence excerpts

The following are actual excerpts from the source and test files used by the build.

### `LocalizationLayer.kt`

```kotlin
class ReFixScheduler(...) {
    private val inFlight = AtomicBoolean(false)
    fun tryStart(facilityX: Double, facilityY: Double, nowMs: Long): Boolean {
        if (!isNearLandmark(facilityX, facilityY)) return false
        if (lastAttemptMs != Long.MIN_VALUE && nowMs - lastAttemptMs < throttleMs) return false
        if (!inFlight.compareAndSet(false, true)) return false
        lastAttemptMs = nowMs
        return true
    }
    fun finish() { inFlight.set(false) }
}

@Synchronized
fun completePeriodicRefix(candidate: CandidateFix, localPose: LocalTrackingPose, nowMs: Long): FixValidationDecision {
    return try { gate.validateAndApply(candidate, localPose, nowMs) } finally { scheduler.finish() }
}
```

### `LocalizationLayerTest.kt`

```kotlin
@Test
fun `scheduler stress allows one concurrent attempt`() {
    val scheduler = ReFixScheduler(listOf(graph.nodes[0]))
    val pool = Executors.newFixedThreadPool(8)
    val latch = CountDownLatch(8)
    var successes = 0
    val lock = Any()
    repeat(8) {
        pool.execute {
            if (scheduler.tryStart(0.0, 0.0, 1_000L)) synchronized(lock) { successes++ }
            latch.countDown()
        }
    }
    latch.await()
    pool.shutdownNow()
    assertEquals(1, successes)
}
```

### `ArSceneViewWrapper.kt` integration

```kotlin
if (localizationLayer.beginPeriodicRefix(now, localPose)) {
    val imageSnapshot = sessionManager.copyCameraImage(frame)
    if (imageSnapshot == null) localizationLayer.cancelPeriodicRefix()
    else scope.launch(Dispatchers.Default) { /* recognize copied image; complete gate */ }
}
```

### Raw automated verification excerpt

```text
> Task :app:testDebugUnitTest
> Task :app:assembleDebug

BUILD SUCCESSFUL in 1m 15s
43 actionable tasks: 13 executed, 30 up-to-date
```
+

## Full source appendix — remaining reviewed files

### LocalizationLayer.kt

```kotlin
package com.example.mallar.ar

import android.util.Log
import com.example.mallar.data.GraphNode
import com.example.mallar.data.LocalizationResult
import com.example.mallar.data.MallGraph
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.navigation.NavConfig
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin

/** Confidence tier used by Module 4's Fix Validation Gate. */
enum class FixConfidenceTier {
    PROVISIONAL,
    CONFIRMED;

    companion object {
        fun fromLandmarkCount(count: Int): FixConfidenceTier =
            if (count >= 2) CONFIRMED else PROVISIONAL
    }
}

/** A frame-independent local pose used by the localization layer and its tests. */
data class LocalTrackingPose(
    val xMeters: Double,
    val yMeters: Double,
    val headingDeg: Float,
    val timestampMs: Long
)

/** The sole Module 4-owned mapping between AR local space and facility coordinates. */
data class FacilityTransform(
    val facilityX: Double,
    val facilityY: Double,
    val headingDeg: Float,
    val localOrigin: LocalTrackingPose,
    val tier: FixConfidenceTier,
    val acceptedAtMs: Long
) {
    fun facilityPosition(localPose: LocalTrackingPose, pixelsPerMeter: Double = NavConfig.PIXELS_PER_METER.toDouble()): Pair<Double, Double> {
        val dx = localPose.xMeters - localOrigin.xMeters
        val dy = localPose.yMeters - localOrigin.yMeters
        val heading = Math.toRadians(headingDeg.toDouble())
        val mapDx = (dx * cos(heading) - dy * sin(heading)) * pixelsPerMeter
        val mapDy = (dx * sin(heading) + dy * cos(heading)) * pixelsPerMeter
        return facilityX + mapDx to facilityY + mapDy
    }
}

data class CandidateFix(
    val facilityX: Double?,
    val facilityY: Double?,
    val headingDeg: Float?,
    val landmarkCount: Int,
    val bestStartNode: GraphNode?
) {
    val tier: FixConfidenceTier
        get() = FixConfidenceTier.fromLandmarkCount(landmarkCount)

    companion object {
        fun from(result: LocalizationResult): CandidateFix = CandidateFix(
            facilityX = result.estimatedMapX,
            facilityY = result.estimatedMapY,
            headingDeg = result.estimatedHeadingDeg,
            landmarkCount = result.landmarkCount,
            bestStartNode = result.bestStartNode
        )
    }
}

enum class FixRejectionReason {
    MISSING_POSITION,
    GRAPH_IMPLAUSIBLE,
    DISPLACEMENT_IMPLAUSIBLE,
    TIER_TOLERANCE_EXCEEDED
}

sealed class FixValidationDecision {
    data class Accepted(val transform: FacilityTransform) : FixValidationDecision()
    data class Rejected(val reason: FixRejectionReason) : FixValidationDecision()
}

/**
 * Validates candidate fixes before they can modify the facility transform.
 * All state mutation is synchronized and the gate is intentionally independent
 * of AR rendering, so Phase 5 can be verified without anchors.
 */
class FixValidationGate(
    private val graph: MallGraph,
    private val pixelsPerMeter: Double = NavConfig.PIXELS_PER_METER.toDouble(),
    private val maxImpliedSpeedMps: Double = 4.0,
    private val graphTolerancePx: Double = NavConfig.GLOBAL_MATCH_THRESHOLD_PX * 1.5,
    private val provisionalTolerancePx: Double = NavConfig.GLOBAL_MATCH_THRESHOLD_PX * 1.5,
    private val confirmedTolerancePx: Double = NavConfig.GLOBAL_MATCH_THRESHOLD_PX
) {
    companion object {
        private const val TAG = "FixValidationGate"
    }

    @Volatile
    var currentTransform: FacilityTransform? = null
        private set

    @Synchronized
    fun validateAndApply(
        candidate: CandidateFix,
        localPose: LocalTrackingPose,
        nowMs: Long = localPose.timestampMs
    ): FixValidationDecision {
        val x = candidate.facilityX
        val y = candidate.facilityY
        if (x == null || y == null || !x.isFinite() || !y.isFinite()) {
            return reject(FixRejectionReason.MISSING_POSITION)
        }

        val nearest = MallGraphRepository.findNearestNode(graph, x, y)
        if (nearest == null || hypot(nearest.x - x, nearest.y - y) > graphTolerancePx) {
            return reject(FixRejectionReason.GRAPH_IMPLAUSIBLE)
        }
        candidate.bestStartNode?.let { candidateNode ->
            if (graph.nodes.none { it.id == candidateNode.id }) {
                return reject(FixRejectionReason.GRAPH_IMPLAUSIBLE)
            }
        }

        val previous = currentTransform
        if (previous != null) {
            val elapsedSec = ((nowMs - previous.acceptedAtMs).coerceAtLeast(1L)) / 1000.0
            val displacementM = hypot(x - previous.facilityX, y - previous.facilityY) / pixelsPerMeter
            if (displacementM / elapsedSec > maxImpliedSpeedMps) {
                return reject(FixRejectionReason.DISPLACEMENT_IMPLAUSIBLE)
            }
        }

        val tierTolerance = if (candidate.tier == FixConfidenceTier.PROVISIONAL) {
            provisionalTolerancePx
        } else {
            confirmedTolerancePx
        }
        if (hypot(nearest.x - x, nearest.y - y) > tierTolerance) {
            return reject(FixRejectionReason.TIER_TOLERANCE_EXCEEDED)
        }

        val transform = FacilityTransform(
            facilityX = x,
            facilityY = y,
            headingDeg = candidate.headingDeg ?: previous?.headingDeg ?: localPose.headingDeg,
            localOrigin = localPose,
            tier = candidate.tier,
            acceptedAtMs = nowMs
        )
        currentTransform = transform
        Log.d(TAG, "Accepted ${candidate.tier} fix at ($x,$y), landmarks=${candidate.landmarkCount}")
        return FixValidationDecision.Accepted(transform)
    }

    @Synchronized
    fun reset() {
        currentTransform = null
    }

    @Synchronized
    internal fun seed(transform: FacilityTransform) {
        currentTransform = transform
    }

    private fun reject(reason: FixRejectionReason): FixValidationDecision.Rejected {
        Log.d(TAG, "Rejected candidate fix: $reason")
        return FixValidationDecision.Rejected(reason)
    }
}

/** Proximity/throttle gate with a single-flight guard for asynchronous recognition. */
class ReFixScheduler(
    private val landmarkNodes: List<GraphNode>,
    private val radiusMeters: Double = 6.0,
    private val pixelsPerMeter: Double = NavConfig.PIXELS_PER_METER.toDouble(),
    private val throttleMs: Long = 4_000L
) {
    private val inFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var lastAttemptMs = Long.MIN_VALUE

    fun isNearLandmark(facilityX: Double, facilityY: Double): Boolean = landmarkNodes.any { node ->
        hypot(node.x - facilityX, node.y - facilityY) / pixelsPerMeter <= radiusMeters
    }

    fun tryStart(facilityX: Double, facilityY: Double, nowMs: Long): Boolean {
        if (!isNearLandmark(facilityX, facilityY)) return false
        if (lastAttemptMs != Long.MIN_VALUE && nowMs - lastAttemptMs < throttleMs) return false
        if (!inFlight.compareAndSet(false, true)) return false
        lastAttemptMs = nowMs
        return true
    }

    fun finish() {
        inFlight.set(false)
    }

    fun reset() {
        lastAttemptMs = Long.MIN_VALUE
        inFlight.set(false)
    }
}

/**
 * Module 4 state holder. It owns the transform and is the only component that
 * can publish an accepted correction. Recognition/camera plumbing calls
 * [beginPeriodicRefix] and [completePeriodicRefix] from its slow cycle.
 */
class LocalizationLayer(
    private val graph: MallGraph,
    landmarkNodes: List<GraphNode> = graph.nodes.filter { it.shopName != null },
    private val gate: FixValidationGate = FixValidationGate(graph),
    private val scheduler: ReFixScheduler = ReFixScheduler(landmarkNodes)
) {
    companion object {
        private const val TAG = "LocalizationLayer"
        private const val PROXIMITY_CHECK_INTERVAL_MS = 1_000L
    }

    private var consecutiveRejections = 0
    private var lastProximityCheckMs = Long.MIN_VALUE
    private var revision = 0L

    val transform: FacilityTransform?
        get() = gate.currentTransform

    val rejectionCount: Int
        get() = consecutiveRejections

    /** Monotonic accepted-transform revision consumed at the Module 6 frame boundary. */
    val transformRevision: Long
        get() = revision

    /** Seeds the transform from the already-accepted pre-navigation scan fix. */
    @Synchronized
    fun initializeFromScan(
        startNode: GraphNode,
        initialHeadingDeg: Float?,
        localPose: LocalTrackingPose,
        nowMs: Long = localPose.timestampMs
    ) {
        gate.seed(
            FacilityTransform(
                facilityX = startNode.x,
                facilityY = startNode.y,
                headingDeg = initialHeadingDeg ?: localPose.headingDeg,
                localOrigin = localPose,
                tier = FixConfidenceTier.CONFIRMED,
                acceptedAtMs = nowMs
            )
        )
        revision++
        consecutiveRejections = 0
        scheduler.reset()
        lastProximityCheckMs = Long.MIN_VALUE
        Log.d(TAG, "Initial scan fix consumed at node ${startNode.id}")
    }

    fun beginPeriodicRefix(nowMs: Long, localPose: LocalTrackingPose): Boolean {
        val current = transform ?: return false
        if (lastProximityCheckMs != Long.MIN_VALUE &&
            nowMs - lastProximityCheckMs < PROXIMITY_CHECK_INTERVAL_MS
        ) return false
        lastProximityCheckMs = nowMs
        val (x, y) = current.facilityPosition(localPose)
        return scheduler.tryStart(x, y, nowMs)
    }

    /** Releases a camera-copy/recognition attempt that could not produce a result. */
    fun cancelPeriodicRefix() {
        scheduler.finish()
    }

    @Synchronized
    fun completePeriodicRefix(
        candidate: CandidateFix,
        localPose: LocalTrackingPose,
        nowMs: Long = localPose.timestampMs
    ): FixValidationDecision {
        return try {
            gate.validateAndApply(candidate, localPose, nowMs).also { decision ->
                if (decision is FixValidationDecision.Accepted) {
                    revision++
                    consecutiveRejections = 0
                } else {
                    consecutiveRejections++
                }
            }
        } finally {
            scheduler.finish()
        }
    }

    fun reset() {
        synchronized(this) {
            gate.reset()
            scheduler.reset()
            consecutiveRejections = 0
            lastProximityCheckMs = Long.MIN_VALUE
            revision = 0L
        }
    }

}


```

### UnifiedNavigationViewModel.kt

```kotlin
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
import com.example.mallar.ar.ArCoreSessionManager
import com.example.mallar.ar.LocalizationLayer
import com.example.mallar.ar.NavigationSessionInputAdapter
import com.example.mallar.ar.RoutePathLayer
import com.example.mallar.ui.localization.NavigationState

/**
 * UnifiedNavigationViewModel
 * â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

    val arCoreSessionManager = ArCoreSessionManager(context)

    /** Module 9 performs the sole subsystem boundary read for this session. */
    private val navigationSnapshot = NavigationSessionInputAdapter.takeSnapshot()
    val routePathLayer: RoutePathLayer? = navigationSnapshot?.let {
        MallGraphRepository.loadedGraph?.let { graph -> RoutePathLayer(it, graph) }
    }
    val localizationLayer: LocalizationLayer? = MallGraphRepository.loadedGraph?.let { graph ->
        LocalizationLayer(graph)
    }
    val initialLocalizationStartNode: GraphNode? = navigationSnapshot?.startNodeId?.let { id ->
        MallGraphRepository.loadedGraph?.nodes?.firstOrNull { it.id == id }
    }
    val initialLocalizationHeading: Float? = navigationSnapshot?.initialHeadingDeg

    val navState: StateFlow<NavSessionState> = sessionManager.sessionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavSessionState())

    private val _poseEnabled = MutableStateFlow(false)
    val poseEnabled: StateFlow<Boolean> = _poseEnabled.asStateFlow()

    private val orientationManager = OrientationManager()
    val orientationState: StateFlow<OrientationUiState> = orientationManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrientationUiState())

    // â”€â”€ Sensors (Owned by VM, gated by Screen Lifecycle) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

    // â”€â”€ Lifecycle Gating â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
        val graph = MallGraphRepository.loadedGraph ?: return
        val snapshot = navigationSnapshot ?: return

        val nodes    = snapshot.pathNodeIds.mapNotNull { id -> graph.nodes.firstOrNull { it.id == id } }
        val destName = snapshot.destinationName

        if (nodes.size >= 2) {
            sessionManager.initialize(nodes, destName)
            if (snapshot.startWithAr) {
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


```

### ArDataModels.kt

```kotlin
package com.example.mallar.ar.model

import com.example.mallar.data.AStarDirection
import com.example.mallar.data.NavInstruction

/**
 * â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 * AR Subsystem Data Contracts
 * â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 * 
 * Immutable data models as defined in AR Engineering Specification Â§5.
 */

/**
 * Module 9 Output / Module 5 Input.
 * An immutable, session-scoped snapshot of the global navigation state.
 */
data class NavigationSessionSnapshot(
    val destinationName: String,
    val startNodeId: Int,
    val pathNodeIds: List<Int>,
    val instructions: List<NavInstruction>,
    val initialHeadingDeg: Float? = null,
    val startWithAr: Boolean = false
)

/**
 * Metadata for a single node on the active route.
 * Bridges facility-coordinate data with subsystem-specific attributes.
 */
data class RouteNodeMetadata(
    val nodeId: Int,
    val x: Double,
    val y: Double,
    val floor: Int,
    val direction: AStarDirection,
    val isDestination: Boolean
)

/**
 * Module 3 Output.
 * Represents a relative 2D displacement vector in facility coordinates.
 */
data class PdrDisplacement(
    val dx: Double,
    val dy: Double,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Module 3 Output.
 * Health signal for a specific sensor stream.
 */
data class SensorStalenessStatus(
    val isStale: Boolean,
    val sensorName: String
)


```

## Device validation record

The developer reports that the current build is working correctly on the Galaxy S22 Ultra, including the previously failing Map↔AR transition. This is recorded as user-reported device evidence; no additional claims are made for exact run counts, timestamps, video, or Logcat capture that were not supplied in this review cycle.
