package com.example.mallar.navigation

import android.util.Log
import kotlin.math.*

private const val TAG = "DriftMonitor"

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * DriftMonitor
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Continuously measures how much the dead-reckoning estimate has drifted and
 * decides whether periodic visual relocalization is needed.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class DriftMonitor {

    enum class DriftLevel { OK, WARNING, CRITICAL }

    data class DriftState(
        val level: DriftLevel              = DriftLevel.OK,
        val offPathSteps: Int              = 0,
        val stepsSinceReloc: Int           = 0,
        val headingStdDev: Float           = 0f,
        val lastImpossibleStepAt: Long     = 0L,
        val relocNeeded: Boolean           = false,
        val relocReason: String            = ""
    )

    @Volatile var driftState = DriftState()
        private set

    /** Called when drift crosses the CRITICAL threshold (rate-limited). */
    var onRelocalizationNeeded: ((reason: String) -> Unit)? = null

    private var offPathSteps       = 0
    private var stepsSinceReloc    = 0
    private val headingWindow      = ArrayDeque<Float>(HEADING_WINDOW_SIZE)
    private var lastRelocRequestMs = 0L

    private var prevX: Double? = null
    private var prevY: Double? = null

    fun onStep(
        posX: Double,
        posY: Double,
        isOnPath: Boolean,
        deviationPx: Double,
        headingDeg: Float
    ) {
        stepsSinceReloc++

        // 1. Off-path counter
        if (isOnPath) {
            offPathSteps = 0
        } else {
            offPathSteps++
        }

        // 2. Impossible-movement check
        val pX = prevX; val pY = prevY
        var impossibleStep = false
        if (pX != null && pY != null) {
            val dx = posX - pX; val dy = posY - pY
            val dist = sqrt(dx * dx + dy * dy)
            // Use config for impossible step distance
            if (dist > MAX_STEP_DIST_PX) {
                Log.w(TAG, "Impossible step: dist=${dist.toInt()}px rejected")
                impossibleStep = true
            }
        }
        prevX = posX; prevY = posY

        // 3. Heading variance tracking
        if (headingWindow.size >= HEADING_WINDOW_SIZE) headingWindow.removeFirst()
        headingWindow.addLast(headingDeg)
        val stdDev = angularStdDev(headingWindow.toList())

        // 4. Classify drift level
        val level = when {
            offPathSteps  >= OFF_PATH_CRITICAL ||
            stepsSinceReloc >= MAX_STEPS_BEFORE_RELOC ||
            stdDev >= NavConfig.HEADING_STABILITY_CRITICAL_DEG ||
            impossibleStep -> DriftLevel.CRITICAL

            offPathSteps  >= OFF_PATH_WARNING ||
            stdDev >= (NavConfig.HEADING_STABILITY_CRITICAL_DEG * 0.5f) -> DriftLevel.WARNING

            else -> DriftLevel.OK
        }

        val reason = when {
            impossibleStep               -> "Sensor glitch detected"
            offPathSteps >= OFF_PATH_CRITICAL -> "Off corridor for ${offPathSteps} steps"
            stepsSinceReloc >= MAX_STEPS_BEFORE_RELOC -> "No visual anchor for ${stepsSinceReloc} steps"
            stdDev >= NavConfig.HEADING_STABILITY_CRITICAL_DEG -> "Compass unstable (σ=${stdDev.toInt()}°)"
            else -> ""
        }

        val now = System.currentTimeMillis()
        val relocNeeded = level == DriftLevel.CRITICAL &&
                (now - lastRelocRequestMs) > RELOC_COOLDOWN_MS

        if (relocNeeded) {
            lastRelocRequestMs = now
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
    }

    fun onRelocalized() {
        offPathSteps    = 0
        stepsSinceReloc = 0
        headingWindow.clear()
        prevX = null; prevY = null
        driftState = DriftState()
        Log.d(TAG, "Drift counters reset")
    }

    private fun angularStdDev(angles: List<Float>): Float {
        if (angles.size < 2) return 0f
        val sinMean = angles.sumOf { sin(Math.toRadians(it.toDouble())) } / angles.size
        val cosMean = angles.sumOf { cos(Math.toRadians(it.toDouble())) } / angles.size
        val R = sqrt(sinMean * sinMean + cosMean * cosMean)
        val stdRad = if (R >= 1.0) 0.0 else sqrt(-2.0 * ln(R))
        return Math.toDegrees(stdRad).toFloat()
    }

    companion object {
        private const val OFF_PATH_WARNING         = 5
        private const val OFF_PATH_CRITICAL        = 12
        private const val MAX_STEPS_BEFORE_RELOC   = 80 // Increased for better PDR tolerance
        private const val MAX_STEP_DIST_PX         = 40.0
        private const val HEADING_WINDOW_SIZE      = 15
        private const val RELOC_COOLDOWN_MS: Long  = 20_000L
    }
}
