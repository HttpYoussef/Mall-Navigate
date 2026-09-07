package com.example.mallar.ar.render

import android.util.Log
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Module 7 — Floor Plane Confidence Monitor & Elevation Fallback
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Continuously evaluates horizontal floor plane tracking stability.
 * Provides smooth floor elevation snapping under normal conditions, and engages
 * a stabilized, exponentially-damped fixed-height fallback under reflective or
 * featureless floor conditions to eliminate vertical bouncing.
 */
class FloorPlaneConfidenceMonitor(
    private val minConfidenceAreaM2: Float = 0.50f,
    private val elevationDampingAlpha: Float = 0.05f,
    private val maxFallbackDistanceMeters: Float = DEFAULT_MAX_FALLBACK_DISTANCE_METERS
) {
    companion object {
        private const val TAG = "FloorPlaneConfidenceMonitor"
        const val DEFAULT_MAX_FALLBACK_DISTANCE_METERS = 5.0f
    }

    private var rollingFloorElevation: Float? = null
    private var lastConfidenceScore: Float = 0f
    private var lastPlaneSelection: String? = null
    private var lastSelectedPlane: Plane? = null
    private var lastLoggedConfidenceScore: Float? = null

    val confidenceScore: Float get() = lastConfidenceScore

    /**
     * Resolves the true physical floor height at a target world coordinate (worldX, worldZ).
     *
     * @param session The active ARCore session.
     * @param worldX Target X coordinate in ARCore World Space.
     * @param worldZ Target Z coordinate in ARCore World Space.
     * @param fallbackElevation Default elevation if no planes have ever been detected.
     * @return Pair of (resolvedFloorY, matchingPlaneOrNull)
     */
    fun resolveFloorElevation(
        session: Session,
        worldX: Float,
        worldZ: Float,
        fallbackElevation: Float
    ): Pair<Float, Plane?> {
        val planes = session.getAllTrackables(Plane::class.java)
        val horizontalPlanes = planes.filter {
            it.type == Plane.Type.HORIZONTAL_UPWARD_FACING && it.trackingState == TrackingState.TRACKING
        }

        val maxDistanceSq = maxFallbackDistanceMeters * maxFallbackDistanceMeters
        val containedPlane = horizontalPlanes.firstOrNull { plane ->
            plane.isPoseInPolygon(Pose.makeTranslation(worldX, plane.centerPose.ty(), worldZ))
        }
        val (matchingPlane, planeSelection) = if (containedPlane != null) {
            containedPlane to "contained"
        } else {
            val nearestPlane = horizontalPlanes.minByOrNull { plane ->
                val dx = plane.centerPose.tx() - worldX
                val dz = plane.centerPose.tz() - worldZ
                dx * dx + dz * dz
            }?.takeIf { plane ->
                val dx = plane.centerPose.tx() - worldX
                val dz = plane.centerPose.tz() - worldZ
                (dx * dx + dz * dz) <= maxDistanceSq
            }
            if (nearestPlane != null) {
                nearestPlane to "nearest-fallback"
            } else {
                null to "none"
            }
        }

        val resultElevation: Float
        if (matchingPlane != null) {
            val planeY = matchingPlane.centerPose.ty()
            val planeArea = computeEstimatedPolygonArea(matchingPlane)
            
            lastConfidenceScore = if (planeArea >= minConfidenceAreaM2) 1.0f else 0.5f
            
            // Update rolling floor elevation with exponential damping
            val currentRolling = rollingFloorElevation ?: planeY
            val dampedElevation = currentRolling * (1f - elevationDampingAlpha) + planeY * elevationDampingAlpha
            rollingFloorElevation = dampedElevation
            
            resultElevation = dampedElevation
        } else {
            lastConfidenceScore = 0.0f
            val fallbackY = rollingFloorElevation ?: fallbackElevation
            resultElevation = fallbackY
        }

        if (planeSelection != lastPlaneSelection || matchingPlane != lastSelectedPlane || lastConfidenceScore != lastLoggedConfidenceScore) {
            lastPlaneSelection = planeSelection
            lastSelectedPlane = matchingPlane
            lastLoggedConfidenceScore = lastConfidenceScore
            try {
                Log.d(
                    TAG,
                    "Floor plane resolved: selection=$planeSelection, confidenceScore=$lastConfidenceScore"
                )
            } catch (_: Throwable) {
                // Defensive catch for unit tests where android.util.Log is not mocked
            }
        }

        return resultElevation to matchingPlane
    }

    private fun computeEstimatedPolygonArea(plane: Plane): Float {
        val polygon = plane.polygon ?: return 0f
        val vertexCount = polygon.limit() / 2
        if (vertexCount < 3) return 0f

        var area = 0f
        for (i in 0 until vertexCount) {
            val x1 = polygon.get(i * 2)
            val z1 = polygon.get(i * 2 + 1)
            val nextIdx = (i + 1) % vertexCount
            val x2 = polygon.get(nextIdx * 2)
            val z2 = polygon.get(nextIdx * 2 + 1)
            area += (x1 * z2 - x2 * z1)
        }
        return kotlin.math.abs(area) * 0.5f
    }

    fun reset() {
        rollingFloorElevation = null
        lastConfidenceScore = 0f
        lastPlaneSelection = null
        lastSelectedPlane = null
        lastLoggedConfidenceScore = null
    }
}
