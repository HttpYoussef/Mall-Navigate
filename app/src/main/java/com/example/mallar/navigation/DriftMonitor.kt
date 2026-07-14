package com.example.mallar.navigation

import android.util.Log
import kotlin.math.*

private const val TAG = "DriftMonitor"

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * DriftMonitor
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * PURPOSE
 * -------
 * Continuously measures how much the dead-reckoning estimate has drifted and
 * decides whether periodic visual relocalization is needed.
 *
 * This avoids the current binary "off-path OR on-path" model by giving the
 * navigation engine a sliding confidence score it can act on gracefully.
 *
 * TRACKED SIGNALS
 * ---------------
 * 1. OFF-PATH DURATION
 *    How many consecutive off-path steps have accumulated.
 *    Triggered when PathSnapper.SnapResult.isOnPath == false for N steps.
 *
 * 2. HEADING INCONSISTENCY
 *    Short-term vs long-term compass heading variance.
 *    A standard deviation > HEADING_INSTABILITY_DEG across a sliding window
 *    indicates compass interference or gyro drift.
 *
 * 3. STEP ACCUMULATION
 *    After MAX_STEPS_BEFORE_RELOC steps without a visual anchor, drift
 *    compounds linearly.  The monitor flags this proactively.
 *
 * 4. IMPOSSIBLE MOVEMENT
 *    A single step advancing more than MAX_STEP_DIST_PX is physically
 *    impossible (user didn't teleport) — indicates a sensor glitch.
 *    The step is rejected and flagged.
 *
 * OUTPUT
 * ------
 * [driftState] is updated after every relevant event.
 * [onRelocalizationNeeded] is fired at most once per [RELOC_COOLDOWN_MS] to
 * prevent hammering the user with camera prompts.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
class DriftMonitor {

    // ─────────────────────────────────────────────────────────────────────────
    // Public state
    // ─────────────────────────────────────────────────────────────────────────

    enum class DriftLevel { OK, WARNING, CRITICAL }

    data class DriftState(
        val level: DriftLevel              = DriftLevel.OK,
        val offPathSteps: Int              = 0,
        val stepsSinceReloc: Int           = 0,
        val headingStdDev: Float           = 0f,
        val lastImpossibleStepAt: Long     = 0L,
        /** True when relocalization was requested this cycle. */
        val relocNeeded: Boolean           = false,
        /** Human-readable reason for any prompt shown to user. */
        val relocReason: String            = ""
    )

    @Volatile var driftState = DriftState()
        private set

    /** Called when drift crosses the CRITICAL threshold (rate-limited). */
    var onRelocalizationNeeded: ((reason: String) -> Unit)? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Internal state
    // ─────────────────────────────────────────────────────────────────────────

    private var offPathSteps       = 0
    private var stepsSinceReloc    = 0
    private val headingWindow      = ArrayDeque<Float>(HEADING_WINDOW_SIZE)
    private var lastRelocRequestMs = 0L

    // Previous position — used for impossible-movement detection
    private var prevX: Double? = null
    private var prevY: Double? = null

    // ─────────────────────────────────────────────────────────────────────────
    // API called by NavigationSessionManager
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call after each dead-reckoning step with the latest path-snap result
     * and sensor heading.
     *
     * @param posX          Current estimated X (after snap).
     * @param posY          Current estimated Y (after snap).
     * @param isOnPath      Whether PathSnapper accepted this position.
     * @param deviationPx   Perpendicular distance from path.
     * @param headingDeg    Current sensor heading in degrees.
     */
    fun onStep(
        posX: Double,
        posY: Double,
        isOnPath: Boolean,
        deviationPx: Double,
        headingDeg: Float
    ) {
        stepsSinceReloc++

        // ── 1. Off-path counter ───────────────────────────────────────────────
        if (isOnPath) {
            offPathSteps = 0
        } else {
            offPathSteps++
        }

        // ── 2. Impossible-movement check ──────────────────────────────────────
        val pX = prevX; val pY = prevY
        var impossibleStep = false
        if (pX != null && pY != null) {
            val dx = posX - pX; val dy = posY - pY
            val dist = sqrt(dx * dx + dy * dy)
            if (dist > MAX_STEP_DIST_PX) {
                Log.w(TAG, "Impossible step: dist=${dist.toInt()}px > ${MAX_STEP_DIST_PX}px — rejected")
                impossibleStep = true
            }
        }
        prevX = posX; prevY = posY

        // ── 3. Heading variance tracking ──────────────────────────────────────
        if (headingWindow.size >= HEADING_WINDOW_SIZE) headingWindow.removeFirst()
        headingWindow.addLast(headingDeg)
        val stdDev = angularStdDev(headingWindow.toList())

        // ── 4. Classify drift level ───────────────────────────────────────────
        val level = when {
            offPathSteps  >= OFF_PATH_CRITICAL ||
            stepsSinceReloc >= MAX_STEPS_BEFORE_RELOC ||
            stdDev >= HEADING_INSTABILITY_CRITICAL_DEG ||
            impossibleStep -> DriftLevel.CRITICAL

            offPathSteps  >= OFF_PATH_WARNING ||
            stdDev >= HEADING_INSTABILITY_WARNING_DEG -> DriftLevel.WARNING

            else -> DriftLevel.OK
        }

        val reason = when {
            impossibleStep               -> "Sensor glitch detected"
            offPathSteps >= OFF_PATH_CRITICAL -> "Off corridor for ${offPathSteps} steps"
            stepsSinceReloc >= MAX_STEPS_BEFORE_RELOC -> "No visual anchor for ${stepsSinceReloc} steps"
            stdDev >= HEADING_INSTABILITY_CRITICAL_DEG -> "Compass unstable (σ=${stdDev.toInt()}°)"
            else -> ""
        }

        val now = System.currentTimeMillis()
        val relocNeeded = level == DriftLevel.CRITICAL &&
                (now - lastRelocRequestMs) > RELOC_COOLDOWN_MS

        if (relocNeeded) {
            lastRelocRequestMs = now
            Log.d(TAG, "🔴 Relocalization needed: $reason")
            onRelocalizationNeeded?.invoke(reason)
        }

        driftState = DriftState(
            level                  = level,
            offPathSteps           = offPathSteps,
            stepsSinceReloc        = stepsSinceReloc,
            headingStdDev          = stdDev,
            lastImpossibleStepAt   = if (impossibleStep) now else driftState.lastImpossibleStepAt,
            relocNeeded            = relocNeeded,
            relocReason            = reason
        )

        if (level != DriftLevel.OK) {
            Log.d(TAG, "Drift=$level offPath=$offPathSteps steps=$stepsSinceReloc stdDev=${stdDev.toInt()}°")
        }
    }

    /**
     * Call when visual relocalization succeeds — resets all drift counters.
     */
    fun onRelocalized() {
        offPathSteps    = 0
        stepsSinceReloc = 0
        headingWindow.clear()
        prevX = null; prevY = null
        driftState = DriftState()
        Log.d(TAG, "✅ Drift counters reset after relocalization")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Math helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Circular standard deviation for a list of headings in degrees. */
    private fun angularStdDev(angles: List<Float>): Float {
        if (angles.size < 2) return 0f
        val sinMean = angles.sumOf { sin(Math.toRadians(it.toDouble())) } / angles.size
        val cosMean = angles.sumOf { cos(Math.toRadians(it.toDouble())) } / angles.size
        // R = mean resultant length; stdDev ≈ sqrt(-2 * ln(R)) in radians
        val R = sqrt(sinMean * sinMean + cosMean * cosMean)
        val stdRad = if (R >= 1.0) 0.0 else sqrt(-2.0 * ln(R))
        return Math.toDegrees(stdRad).toFloat()
    }

    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        /** Steps off-path before WARNING. */
        private const val OFF_PATH_WARNING         = 5

        /** Steps off-path before CRITICAL relocalization request. */
        private const val OFF_PATH_CRITICAL        = 12

        /** Steps without visual anchor before requesting relocalization. */
        private const val MAX_STEPS_BEFORE_RELOC   = 60   // ~45 m at 0.75 m/step

        /** Single-step distance above which we flag impossible movement (px). */
        private const val MAX_STEP_DIST_PX         = 35.0  // ~1.75 m at 20 px/m

        /** Heading σ beyond which we issue a WARNING. */
        private const val HEADING_INSTABILITY_WARNING_DEG  = 20f

        /** Heading σ beyond which we request CRITICAL relocalization. */
        private const val HEADING_INSTABILITY_CRITICAL_DEG = 40f

        /** Sliding window size for heading variance (steps). */
        private const val HEADING_WINDOW_SIZE = 15

        /** Minimum time between consecutive relocalization requests (ms). */
        private const val RELOC_COOLDOWN_MS: Long = 30_000L   // 30 s
    }
}
