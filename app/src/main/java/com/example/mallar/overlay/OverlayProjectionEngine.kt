package com.example.mallar.overlay

import android.util.Log
import com.example.mallar.data.GraphNode
import kotlin.math.*

private const val TAG = "OverlayProjectionEngine"

// ─────────────────────────────────────────────────────────────────────────────
/**
 * OverlayProjectionEngine
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * PURPOSE
 * -------
 * Convert indoor 2D map coordinates (GraphNode.x, GraphNode.y) into
 * screen-space positions for the camera overlay WITHOUT using ARCore anchors.
 *
 * APPROACH: Pseudo-AR Screen-Space Projection
 * -------------------------------------------
 * 1. Transform each path node from map space into a "local navigation frame"
 *    centred on the user's estimated position.
 * 2. Rotate that local frame by the user's heading to get camera-relative
 *    coordinates (camera forward = positive Y axis).
 * 3. Apply perspective projection to simulate depth (scale inversely with distance).
 * 4. Clamp and map to screen pixel coordinates.
 *
 * COORDINATE SYSTEMS
 * ------------------
 *   MAP SPACE: GraphNode.x increases rightward (East), .y increases downward (South).
 *   NAV LOCAL: centred on user, X = East, Y = North (Y flipped from map).
 *   CAM LOCAL: rotated so camera forward = (0, 1). X = right of camera, Y = ahead.
 *   SCREEN:    (0,0) = top-left. X = rightward, Y = downward.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
class OverlayProjectionEngine(
    /** Screen width in pixels. */
    var screenW: Float = 1080f,
    /** Screen height in pixels. */
    var screenH: Float = 1920f,
    /** Effective camera horizontal field-of-view in degrees (typical ≈ 68°). */
    var fovDeg: Float = 68f
) {

    // ── Projection tuning constants ───────────────────────────────────────────

    /** Maximum render distance in map pixels (≈10 m at PX_TO_M 0.05). */
    private val MAX_RENDER_DIST_PX = 400f

    /** Minimum render distance — nodes closer than this are nearly at the user. */
    private val MIN_RENDER_DIST_PX = 10f

    /** Reference distance for 1:1 scale projection (controls how "big" near nodes appear). */
    private val REFERENCE_DIST_PX = 200f

    /** Maximum visible angle from camera forward (half-angle). Nodes outside → clamped. */
    private val VISIBLE_HALF_ANGLE_DEG = 85f

    // ─────────────────────────────────────────────────────────────────────────
    // Core projection entry point
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Project a list of path nodes into screen-space overlay points.
     *
     * @param pathNodes         Ordered list of path nodes on the current route.
     * @param userMapX          User's estimated X in map pixel coordinates.
     * @param userMapY          User's estimated Y in map pixel coordinates.
     * @param headingDeg        User's heading in degrees, clockwise from North, [0,360).
     * @param lookaheadStartIdx The first node index to project (nodes before this are passed).
     *
     * @return List of [ProjectedPoint] ready for the Canvas overlay renderer.
     */
    fun project(
        pathNodes: List<GraphNode>,
        userMapX: Float,
        userMapY: Float,
        headingDeg: Float,
        lookaheadStartIdx: Int = 0
    ): List<ProjectedPoint> {
        val result = mutableListOf<ProjectedPoint>()

        // We will project at most MAX_PROJECTED_NODES nodes ahead
        val endIdx = minOf(pathNodes.size, lookaheadStartIdx + MAX_PROJECTED_NODES)

        for (i in lookaheadStartIdx until endIdx) {
            val node = pathNodes[i]
            val proj = projectSingle(
                nodeMapX   = node.x.toFloat(),
                nodeMapY   = node.y.toFloat(),
                userMapX   = userMapX,
                userMapY   = userMapY,
                headingDeg = headingDeg,
                nodeIndex  = i,
                totalNodes = pathNodes.size
            ) ?: continue
            result.add(proj)
        }

        return result
    }

    /**
     * Project a single map point. Returns null if the point is behind the user
     * or too far to render.
     */
    fun projectSingle(
        nodeMapX: Float,
        nodeMapY: Float,
        userMapX: Float,
        userMapY: Float,
        headingDeg: Float,
        nodeIndex: Int = 0,
        totalNodes: Int = 1
    ): ProjectedPoint? {
        // ── Step 1: Map → Nav-local frame ─────────────────────────────────────
        // Map Y increases downward (South), so North is −Y in map space.
        val localEast  =  (nodeMapX - userMapX)   // positive = East of user
        val localNorth = -(nodeMapY - userMapY)   // positive = North of user (flip map Y)

        val distPx = sqrt(localEast * localEast + localNorth * localNorth)

        if (distPx < MIN_RENDER_DIST_PX) return null   // essentially at the user
        if (distPx > MAX_RENDER_DIST_PX) return null   // too far to render

        // ── Step 2: Rotate into camera-relative frame ─────────────────────────
        // Camera forward is the direction the user is heading.
        // Heading is measured clockwise from North → rotate by -headingDeg.
        //
        //   camX = localEast  * cos(-θ) - localNorth * sin(-θ)  = east*cos(θ) + north*sin(θ)
        //   camY = localEast  * sin(-θ) + localNorth * cos(-θ)  = -east*sin(θ) + north*cos(θ)
        //
        // camY > 0 = in front of camera (camera forward = +Y)
        // camX > 0 = to the right of camera
        //
        val headingRad = Math.toRadians(headingDeg.toDouble()).toFloat()
        val sinH = sin(headingRad)
        val cosH = cos(headingRad)

        val camX = localEast * cosH + localNorth * sinH
        val camY = -localEast * sinH + localNorth * cosH

        // ── Step 3: Cull behind camera ─────────────────────────────────────────
        // camY < 0 means the node is behind the user. We allow a small tolerance
        // to avoid flickering right at the 90° edge.
        if (camY < -0.1f * distPx) return null   // more than 10% behind → skip

        // ── Step 4: Compute angle from camera forward axis ────────────────────
        val angleFromForward = Math.toDegrees(atan2(camX.toDouble(), camY.toDouble())).toFloat()
        val isBehind = abs(angleFromForward) > VISIBLE_HALF_ANGLE_DEG

        // ── Step 5: Perspective scale (inverse distance) ──────────────────────
        // Nodes far away appear smaller. Use clamped hyperbolic scale.
        val effectiveDist = distPx.coerceAtLeast(MIN_RENDER_DIST_PX)
        val perspectiveScale = (REFERENCE_DIST_PX / effectiveDist).coerceIn(0.3f, 2.5f)

        // ── Step 6: Alpha fade — distant points fade out ──────────────────────
        val alpha = when {
            distPx < REFERENCE_DIST_PX * 0.5f -> 1f
            distPx < MAX_RENDER_DIST_PX * 0.7f -> 1f - (distPx - REFERENCE_DIST_PX * 0.5f) / (MAX_RENDER_DIST_PX * 0.3f) * 0.4f
            else -> 0.6f
        }.coerceIn(0f, 1f)

        // ── Step 7: Map camX/camY to screen coordinates ───────────────────────
        // Screen centre = (screenW/2, screenH/2).
        // Horizontal: use tangent of FOV to map camX → pixels.
        //   screenX = screenW/2 + (camX / camY) * focalLength
        //   focalLength = (screenW/2) / tan(fovDeg/2)
        //
        // Vertical: nodes ahead of user project lower on screen (they appear
        // on the floor in front). We simulate floor projection by biasing Y
        // toward the bottom half. The vertical FOV is estimated from aspect ratio.

        val focalLengthX = (screenW / 2f) / tan(Math.toRadians(fovDeg / 2.0)).toFloat()
        val aspectRatio  = screenH / screenW
        val fovVDeg      = fovDeg * aspectRatio   // rough vertical FOV
        val focalLengthY = (screenH / 2f) / tan(Math.toRadians(fovVDeg / 2.0)).toFloat()

        val screenX: Float
        val screenY: Float

        if (camY <= 0f || isBehind) {
            // Node is behind or at 90° — project it to the edge of the screen
            // so the user gets a cue to turn around.
            val edgeX = if (camX >= 0) screenW - 40f else 40f
            screenX = edgeX
            screenY = screenH * 0.5f   // mid screen for edge cues
        } else {
            // Normal forward projection
            val rawSx = screenW / 2f + (camX / camY) * focalLengthX

            // Vertical floor simulation: nodes on the ground plane project below
            // the camera horizon. We model the ground-plane pitch assuming the
            // phone is held at ~45° tilt (common walking posture). The node is
            // at ground level so it always projects below the horizon.
            // Simplified: just push Y toward bottom proportional to 1/distance.
            val groundBias = screenH * 0.1f + (REFERENCE_DIST_PX / effectiveDist) * screenH * 0.15f
            val rawSy = screenH * 0.5f + groundBias

            screenX = rawSx.coerceIn(20f, screenW - 20f)
            screenY = rawSy.coerceIn(screenH * 0.3f, screenH * 0.85f)
        }

        return ProjectedPoint(
            screenX          = screenX,
            screenY          = screenY,
            distancePx       = distPx,
            perspectiveScale = perspectiveScale,
            alpha            = alpha,
            isAhead          = !isBehind && camY > 0f,
            camX             = camX,
            camY             = camY,
            nodeIndex        = nodeIndex,
            isFinalNode      = nodeIndex == totalNodes - 1,
            angleFromForward = angleFromForward
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Direction arrow computation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Compute which direction the user must turn to face the next path segment.
     *
     * @param nextNodeMapX   Map X of the next waypoint.
     * @param nextNodeMapY   Map Y of the next waypoint.
     * @param userMapX       User map X.
     * @param userMapY       User map Y.
     * @param headingDeg     Current user heading (0=North, clockwise).
     *
     * @return [TurnInfo] with the turn angle and direction label.
     */
    fun computeTurnInfo(
        nextNodeMapX: Float,
        nextNodeMapY: Float,
        userMapX: Float,
        userMapY: Float,
        headingDeg: Float
    ): TurnInfo {
        // Bearing from user to next node (clockwise from North)
        val dx = nextNodeMapX - userMapX
        val dy = -(nextNodeMapY - userMapY)   // flip map Y → North
        val targetBearing = (Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).toFloat() + 360f) % 360f

        // Angular offset from current heading to target bearing
        var delta = targetBearing - headingDeg
        while (delta >  180f) delta -= 360f
        while (delta < -180f) delta += 360f

        val direction = when {
            abs(delta) < STRAIGHT_THRESHOLD_DEG -> OverlayTurnDirection.STRAIGHT
            delta > 0f                           -> OverlayTurnDirection.RIGHT
            else                                 -> OverlayTurnDirection.LEFT
        }

        return TurnInfo(
            angleDeg        = delta,
            direction       = direction,
            targetBearingDeg = targetBearing
        )
    }

    companion object {
        /** Maximum number of path nodes to project ahead at once. */
        const val MAX_PROJECTED_NODES = 12
        /** Angle threshold (degrees) to classify as straight vs. a turn. */
        const val STRAIGHT_THRESHOLD_DEG = 20f
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Data classes for projection results
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single path node projected into screen space.
 *
 * @param screenX          X coordinate in screen pixels.
 * @param screenY          Y coordinate in screen pixels.
 * @param distancePx       Distance from user to this node in map pixels.
 * @param perspectiveScale Scale factor based on distance (larger = nearer).
 * @param alpha            Opacity [0,1]. Far nodes fade.
 * @param isAhead          True if the node is in the forward hemisphere.
 * @param camX             Camera-relative X (positive = right).
 * @param camY             Camera-relative Y (positive = ahead).
 * @param nodeIndex        Index in the original path node list.
 * @param isFinalNode      True if this is the destination node.
 * @param angleFromForward Signed angle from camera forward to this node (°).
 */
data class ProjectedPoint(
    val screenX: Float,
    val screenY: Float,
    val distancePx: Float,
    val perspectiveScale: Float,
    val alpha: Float,
    val isAhead: Boolean,
    val camX: Float,
    val camY: Float,
    val nodeIndex: Int,
    val isFinalNode: Boolean,
    val angleFromForward: Float
)

/** Turn direction for the camera overlay. */
enum class OverlayTurnDirection { STRAIGHT, LEFT, RIGHT, U_TURN }

/**
 * Result of turn direction computation.
 *
 * @param angleDeg         Signed turn angle (positive = right, negative = left).
 * @param direction        Classified turn direction.
 * @param targetBearingDeg Absolute bearing toward the next waypoint.
 */
data class TurnInfo(
    val angleDeg: Float,
    val direction: OverlayTurnDirection,
    val targetBearingDeg: Float
)
