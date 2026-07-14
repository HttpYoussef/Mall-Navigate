package com.example.mallar.navigation

import com.example.mallar.data.GraphNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * OrientationUiState
 * ─────────────────────────────────────────────────────────────────────────────
 * Snapshot the orientation phase exposes to the UI.
 *
 * Kept deliberately generic (bearing + heading + classified [direction]) rather
 * than text-only, so a future AR arrow renderer can consume [targetBearingDeg]
 * and [headingDeg] directly via [BearingCalculator.arrowRotation] — the same
 * math the existing in-navigation AR arrow already uses — without needing any
 * change to this class or to the navigation flow that drives it.
 */
data class OrientationUiState(
    /** True while the orientation phase is being shown and gating navigation. */
    val active: Boolean = false,
    /** Human-readable guidance: "Turn Left" / "Turn Right" / "Turn Around" / "Face Forward". */
    val instruction: String = "",
    /** Classified turn direction (reuses the same enum as the rest of navigation). */
    val direction: TurnDirection = TurnDirection.STRAIGHT,
    /** Absolute bearing (0-360, clockwise from North) the user must face. */
    val targetBearingDeg: Float = 0f,
    /** Most recent live compass heading fed into the manager. */
    val headingDeg: Float = 0f,
    /** True once the user has held a matching heading for [holdMillis]. */
    val isAligned: Boolean = false
)

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * OrientationManager
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Runs once, at the very start of a navigation trip, before the existing
 * navigation pipeline (step tracking / AR / map) is presented to the user.
 *
 * It answers a single question — "which way must the user physically turn to
 * face the first leg of the route?" — using only the first two nodes of the
 * already-computed path and the live compass heading. It does not touch the
 * graph, the A* result, or localization; it only reads two [GraphNode]s that
 * were already produced by the existing pathfinding pipeline.
 *
 * Usage:
 *   1. Call [start] once the route (pathNodes) is available.
 *   2. Feed every compass update through [onHeadingUpdated].
 *   3. Observe [state]; when `state.active == false` (it flips automatically
 *      once alignment is confirmed), resume the existing navigation UI as-is.
 */
class OrientationManager(
    private val alignThresholdDeg: Float = BearingCalculator.TURN_THRESHOLD_DEG,
    /** Heading must stay within threshold for this long before we confirm alignment,
     *  so a brief pass-through swing of the compass doesn't falsely trigger a start. */
    private val holdMillis: Long = 600L,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val _state = MutableStateFlow(OrientationUiState())
    val state: StateFlow<OrientationUiState> = _state.asStateFlow()

    private var targetBearing: Float = 0f
    private var alignedSinceMs: Long? = null

    /**
     * Begin the orientation phase for the first leg of [pathNodes].
     * No-op (phase stays inactive) if the path doesn't have at least two nodes.
     */
    fun start(pathNodes: List<GraphNode>) {
        if (pathNodes.size < 2) {
            _state.value = OrientationUiState(active = false)
            return
        }
        targetBearing = BearingCalculator.mapBearing(pathNodes[0], pathNodes[1])
        alignedSinceMs = null
        _state.value = OrientationUiState(
            active = true,
            targetBearingDeg = targetBearing,
            direction = TurnDirection.STRAIGHT,
            instruction = "",
            isAligned = false
        )
    }

    /**
     * Feed a live compass heading (degrees, clockwise from North) while the
     * orientation phase is active. Ignored once the phase is no longer active.
     *
     * @return true once alignment has just been confirmed (state.active flips to false).
     */
    fun onHeadingUpdated(headingDeg: Float): Boolean {
        val current = _state.value
        if (!current.active) return false

        val delta = BearingCalculator.arrowRotation(targetBearing, headingDeg)
        val direction = BearingCalculator.turnHint(delta, alignThresholdDeg)

        val now = nowMs()
        val withinThreshold = direction == TurnDirection.STRAIGHT
        alignedSinceMs = when {
            !withinThreshold -> null
            alignedSinceMs == null -> now
            else -> alignedSinceMs
        }
        val justAligned = withinThreshold &&
            alignedSinceMs != null &&
            (now - alignedSinceMs!!) >= holdMillis

        _state.update {
            it.copy(
                headingDeg = headingDeg,
                direction = direction,
                instruction = instructionFor(direction, delta),
                isAligned = justAligned,
                active = !justAligned // hand control back to normal navigation once aligned
            )
        }
        return justAligned
    }

    /** Explicitly dismiss the orientation phase (e.g. user taps "Skip"). */
    fun finish() {
        _state.update { it.copy(active = false) }
    }

    private fun instructionFor(direction: TurnDirection, delta: Float): String = when (direction) {
        TurnDirection.STRAIGHT -> "Face Forward"
        TurnDirection.RIGHT    -> if (abs(delta) > 150f) "Turn Around" else "Turn Right"
        TurnDirection.LEFT     -> if (abs(delta) > 150f) "Turn Around" else "Turn Left"
    }
}
