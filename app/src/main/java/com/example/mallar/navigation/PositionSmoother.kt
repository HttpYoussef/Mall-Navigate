package com.example.mallar.navigation

import kotlin.math.*

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * PositionSmoother
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Prevent UI jitter by smoothing the position signal before it reaches the
 * camera overlay and map renderers.
 */
class PositionSmoother(
    /** Position lerp factor per update [0, 1]. Higher = more responsive. */
    private val posAlpha: Float = 0.78f,
    /** Heading lerp factor per update [0, 1]. */
    private val headingAlpha: Float = 0.55f
) {

    private var smoothX:       Double? = null
    private var smoothY:       Double? = null
    private var smoothHeading: Float?  = null

    fun smoothPosition(rawX: Double, rawY: Double): Pair<Double, Double> {
        val px = smoothX; val py = smoothY

        if (px == null || py == null) {
            smoothX = rawX; smoothY = rawY
            return Pair(rawX, rawY)
        }

        val dx = rawX - px; val dy = rawY - py
        val dist = sqrt(dx * dx + dy * dy)

        if (dist > NavConfig.SMOOTHING_JUMP_THRESHOLD_PX) {
            smoothX = rawX; smoothY = rawY
            return Pair(rawX, rawY)
        }

        val newX = px + posAlpha * dx
        val newY = py + posAlpha * dy
        smoothX = newX; smoothY = newY
        return Pair(newX, newY)
    }

    fun smoothHeading(rawDeg: Float): Float {
        val prev = smoothHeading ?: run {
            smoothHeading = rawDeg
            return rawDeg
        }

        var delta = rawDeg - prev
        while (delta >  180f) delta -= 360f
        while (delta < -180f) delta += 360f

        if (abs(delta) > NavConfig.HEADING_JUMP_THRESHOLD_DEG) {
            smoothHeading = rawDeg
            return rawDeg
        }

        val updated = ((prev + headingAlpha * delta) + 360f) % 360f
        smoothHeading = updated
        return updated
    }

    fun reset() {
        smoothX       = null
        smoothY       = null
        smoothHeading = null
    }
}
