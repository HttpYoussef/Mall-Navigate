package com.example.mallar.navigation

import kotlin.math.*

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * PositionSmoother
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * PURPOSE
 * -------
 * Prevent UI jitter by smoothing the position signal before it reaches the
 * camera overlay and map renderers.
 *
 * Indoor dead reckoning + path snapping can introduce small position jumps:
 *  • Snap target shifts when segment boundary changes (corner rounding)
 *  • Heading noise propagates into X/Y components
 *  • Map-matching occasionally flips between two nearby edges
 *
 * This smoother applies two complementary techniques:
 *
 * 1. EXPONENTIAL LERP  (default for position)
 *    x_out = x_out + α * (x_in − x_out)
 *    α is tuned for **responsiveness** (each step should move the dot visibly).
 *    Too low α stacked after PathSnapper causes multi-step lag then jump when
 *    [JUMP_THRESHOLD_PX] bypass fires.
 *
 * 2. EXPONENTIAL LERP  (for heading)
 *    Same formula, but with circular (shortest-arc) delta to avoid wrap-around
 *    artifacts at the 0°/360° boundary.
 *
 * POSITION VALIDITY
 * -----------------
 * Large position jumps (e.g. after a relocalization) must not be smoothed —
 * the user actually moved. If |Δ| > JUMP_THRESHOLD_PX, the smoother resets
 * its history and accepts the new value instantly.
 *
 * HEADING VALIDITY
 * ----------------
 * Heading changes > HEADING_JUMP_DEG are accepted instantly (genuine turn).
 * Smaller changes are lerped to reduce oscillation while walking straight.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
class PositionSmoother(
    /** Position lerp factor per update [0, 1]. Higher = more responsive (preferred for walking). */
    private val posAlpha: Float = 0.78f,
    /** Heading lerp factor per update [0, 1]. */
    private val headingAlpha: Float = 0.55f
) {

    // ── State ─────────────────────────────────────────────────────────────────

    private var smoothX:       Double? = null
    private var smoothY:       Double? = null
    private var smoothHeading: Float?  = null

    // ─────────────────────────────────────────────────────────────────────────
    // Position
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Smooth a new (rawX, rawY) position.
     * Returns the smoothed (x, y) pair.
     */
    fun smoothPosition(rawX: Double, rawY: Double): Pair<Double, Double> {
        val px = smoothX; val py = smoothY

        if (px == null || py == null) {
            // Cold start
            smoothX = rawX; smoothY = rawY
            return Pair(rawX, rawY)
        }

        val dx = rawX - px; val dy = rawY - py
        val dist = sqrt(dx * dx + dy * dy)

        if (dist > JUMP_THRESHOLD_PX) {
            // Large jump — accept instantly (relocalization or reroute)
            smoothX = rawX; smoothY = rawY
            return Pair(rawX, rawY)
        }

        val newX = px + posAlpha * dx
        val newY = py + posAlpha * dy
        smoothX = newX; smoothY = newY
        return Pair(newX, newY)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Heading
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Smooth a new heading angle (degrees, [0, 360)).
     * Uses circular arithmetic to avoid wrap-around jumps at the 0/360 boundary.
     */
    fun smoothHeading(rawDeg: Float): Float {
        val prev = smoothHeading ?: run {
            smoothHeading = rawDeg
            return rawDeg
        }

        // Shortest angular delta (−180, +180]
        var delta = rawDeg - prev
        while (delta >  180f) delta -= 360f
        while (delta < -180f) delta += 360f

        if (abs(delta) > HEADING_JUMP_DEG) {
            // Genuine large turn — accept instantly
            smoothHeading = rawDeg
            return rawDeg
        }

        val updated = ((prev + headingAlpha * delta) + 360f) % 360f
        smoothHeading = updated
        return updated
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Control
    // ─────────────────────────────────────────────────────────────────────────

    /** Reset all history — call after relocalization. */
    fun reset() {
        smoothX       = null
        smoothY       = null
        smoothHeading = null
    }

    companion object {
        /** Position jump above which we bypass smoothing (instant accept). */
        private const val JUMP_THRESHOLD_PX  = 80.0   // ~4 m at 20 px/m

        /** Heading jump above which we bypass smoothing (genuine turn). */
        private const val HEADING_JUMP_DEG   = 60f
    }
}
