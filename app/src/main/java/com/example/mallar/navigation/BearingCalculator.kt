package com.example.mallar.navigation

import android.util.Log
import com.example.mallar.data.GraphNode
import kotlin.math.*

private const val TAG = "BearingCalculator"

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * BearingCalculator
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * PURPOSE
 * -------
 * Compute every angle and direction value needed by the navigation system from
 * first principles, with thorough mathematical documentation.
 *
 * ── COORDINATE SYSTEMS ───────────────────────────────────────────────────────
 *
 * MAP SPACE (pixel/map units)
 * ─────────────────────────
 *   • Origin:  top-left corner of the mall floor-plan image.
 *   • X-axis:  increases rightward.
 *   • Y-axis:  increases downward (image convention).
 *   • GraphNode.x, GraphNode.y are in this space.
 *
 * GEOGRAPHIC BEARING (compass)
 * ────────────────────────────
 *   • 0°   = North (map Y decreasing — upward on image).
 *   • 90°  = East  (map X increasing).
 *   • 180° = South (map Y increasing — downward on image).
 *   • 270° = West  (map X decreasing).
 *   • Always clockwise from North, range [0, 360).
 *
 * AR SPACE (ARCore anchor-local)
 * ──────────────────────────────
 *   • OpenGL convention: Y is up, +Z is "toward camera" (into the screen),
 *     −Z is forward (away from camera).
 *   • The AR world is rotated so "North" aligns with the mall map after
 *     calibration.
 *
 * ── BEARING DERIVATION ───────────────────────────────────────────────────────
 *
 * Given two consecutive GraphNodes A and B in map space:
 *   dx = B.x − A.x     (positive = East)
 *   dy = B.y − A.y     (positive = South, because Y increases downward)
 *
 * We want the bearing θ measured clockwise from North:
 *
 *   θ = atan2(dx, −dy)
 *
 * WHY atan2(dx, −dy)?
 * -------------------
 * Standard math: angle from +X axis = atan2(y, x).
 * We want angle from +Y (North), clockwise.
 * "From +Y axis" means we swap: atan2(x, y).
 * But our Y axis points DOWN (South), not up (North).
 * Flip Y: replace dy with −dy.
 * Result: atan2(dx, −dy).
 *
 * Result is in (−π, +π] radians → convert to degrees → normalise to [0,360).
 *
 * ── ARROW ROTATION FORMULA ───────────────────────────────────────────────────
 *
 *   arrowRotationDeg = targetBearing − userHeading
 *
 * WHY?
 * ----
 * The arrow's job is to point toward the target in the camera's reference
 * frame. If the user faces exactly the target direction (heading = bearing),
 * the arrow should point straight ahead (0°). If the target is 45° to the
 * right of the user's current heading, the arrow rotates +45°.
 *
 * Implementation note: always normalise the result to (−180, +180] so the
 * shortest rotation path is used (no unnecessary full-circle spin).
 *
 * ── TURN HINT LOGIC ──────────────────────────────────────────────────────────
 *
 * Given arrowRotationDeg (already normalised to −180…+180):
 *   > +TURN_THRESHOLD → user needs to turn RIGHT
 *   < −TURN_THRESHOLD → user needs to turn LEFT
 *   otherwise         → STRAIGHT
 *
 * TURN_THRESHOLD = 20° matches natural walking gait; tighter corridors may
 * need 15°, wide-open spaces may use 30°.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
object BearingCalculator {

    // ─────────────────────────────────────────────────────────────────────────
    // Map bearing (node → node)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Compute the map bearing from [from] to [to] in degrees clockwise from North.
     *
     * Coordinate note: GraphNode.x increases rightward (East),
     *                  GraphNode.y increases downward  (South).
     *
     * Formula: atan2(dx, −dy), normalised to [0, 360).
     */
    fun mapBearing(from: GraphNode, to: GraphNode): Float {
        val dx = (to.x - from.x).toFloat()
        val dy = (to.y - from.y).toFloat()           // positive = South (down on map)

        // atan2 returns (−π, +π]; East=90°, South=180°, West=270°
        val bearingRad = atan2(dx.toDouble(), (-dy).toDouble())
        val bearingDeg = Math.toDegrees(bearingRad).toFloat()

        return (bearingDeg + 360f) % 360f             // normalise to [0, 360)
    }

    /**
     * Compute the bearing from [path[segIdx]] to [path[segIdx+1]].
     * Returns 0° if indices are out of range.
     */
    fun segmentBearing(path: List<GraphNode>, segIdx: Int): Float {
        val from = path.getOrNull(segIdx)     ?: return 0f
        val to   = path.getOrNull(segIdx + 1) ?: return 0f
        return mapBearing(from, to)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Arrow rotation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Compute how much to rotate the navigation arrow so it points toward the
     * target from the user's perspective.
     *
     * [targetBearingDeg]   – clockwise-from-North bearing toward the next waypoint.
     * [userHeadingDeg]     – the device's current compass azimuth (0–360).
     *
     * Result is normalised to (−180, +180]:
     *   positive = turn clockwise  (RIGHT)
     *   negative = turn counter-clockwise (LEFT)
     *   near 0   = straight ahead
     *
     * Math:
     *   rawDelta = targetBearing − userHeading
     *   normalise to (−180, +180] by adding/subtracting 360.
     */
    fun arrowRotation(targetBearingDeg: Float, userHeadingDeg: Float): Float {
        var delta = targetBearingDeg - userHeadingDeg
        while (delta > 180f)  delta -= 360f
        while (delta < -180f) delta += 360f
        return delta
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Turn hint
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Classify whether the user should go STRAIGHT, turn LEFT, or turn RIGHT.
     *
     * [arrowRotationDeg] – output of [arrowRotation], already in (−180, +180].
     * [thresholdDeg]     – angular deadband for "straight" (default 20°).
     *
     * Algorithm:
     *   if |arrowRotation| < threshold → STRAIGHT
     *   if arrowRotation   > threshold → RIGHT (path is to the right of heading)
     *   if arrowRotation   < −threshold → LEFT
     */
    fun turnHint(arrowRotationDeg: Float, thresholdDeg: Float = TURN_THRESHOLD_DEG): TurnDirection {
        return when {
            abs(arrowRotationDeg) < thresholdDeg -> TurnDirection.STRAIGHT
            arrowRotationDeg > 0f                -> TurnDirection.RIGHT
            else                                 -> TurnDirection.LEFT
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Segment analysis
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * For a list of path nodes, compute the bearing change at each interior node.
     *
     * The bearing change at node i is:
     *   Δθ_i = bearing(i-1 → i) − bearing(i → i+1)
     *   normalised to (−180, +180].
     *
     * Positive Δθ = the path turns RIGHT at this node.
     * Negative Δθ = the path turns LEFT at this node.
     *
     * Returns a list of [PathTurn] for all interior nodes (indices 1…n-2).
     */
    fun computePathTurns(path: List<GraphNode>): List<PathTurn> {
        if (path.size < 3) return emptyList()
        val turns = mutableListOf<PathTurn>()

        for (i in 1 until path.size - 1) {
            val inBearing  = mapBearing(path[i - 1], path[i])
            val outBearing = mapBearing(path[i], path[i + 1])
            var delta = outBearing - inBearing
            while (delta > 180f)  delta -= 360f
            while (delta < -180f) delta += 360f

            val dir = when {
                abs(delta) < TURN_THRESHOLD_DEG -> TurnDirection.STRAIGHT
                delta > 0f                      -> TurnDirection.RIGHT
                else                            -> TurnDirection.LEFT
            }
            turns += PathTurn(nodeIndex = i, bearingChangeDeg = delta, direction = dir)
        }
        return turns
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Angle utilities
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Shortest signed delta from [from] to [to], in (−180, +180].
     * Positive = clockwise, negative = counter-clockwise.
     */
    fun shortestDelta(from: Float, to: Float): Float {
        var d = to - from
        while (d > 180f)  d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    /** Normalise any angle to [0, 360). */
    fun normalise360(deg: Float): Float = (deg % 360f + 360f) % 360f

    // ─────────────────────────────────────────────────────────────────────────
    /** Below this threshold the user is considered "facing straight". */
    const val TURN_THRESHOLD_DEG: Float = 20f
}

// ─────────────────────────────────────────────────────────────────────────────
/** Turn direction enum shared across navigation and UI layers. */
enum class TurnDirection { STRAIGHT, LEFT, RIGHT }

/** Describes a path turn at a specific interior node. */
data class PathTurn(
    val nodeIndex: Int,
    val bearingChangeDeg: Float,
    val direction: TurnDirection
)
