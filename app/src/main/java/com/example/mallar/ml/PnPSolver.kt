package com.example.mallar.ml

import android.util.Log
import com.example.mallar.data.GraphNode
import com.example.mallar.data.MallGraph
import kotlin.math.*

private const val TAG = "PnPSolver"

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * PnPSolver  —  Pure-Kotlin Bearing Triangulation
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * PURPOSE
 * -------
 * Replace the naive weighted-centroid position estimate with a proper
 * camera-pose solver that uses actual image-space landmark coordinates.
 *
 * WHY NOT OpenCV solvePnP()?
 * --------------------------
 * OpenCV requires native (.so) libraries not bundled in this project.
 * This implementation delivers equivalent accuracy for a 2-D indoor map
 * (single floor, no elevation changes) using only pure Kotlin.
 *
 * MATHEMATICAL MODEL (pinhole camera, horizontal plane)
 * -----------------------------------------------------
 * Camera at position (Cx, Cy) in map-pixel coordinates, facing heading θ
 * (degrees clockwise from map North, same convention as bearing).
 *
 * For a landmark at world position (Xi, Yi):
 *   ψᵢ = atan2(Xi − Cx,  −(Yi − Cy))    world bearing from camera to landmark
 *   αᵢ = intrinsics.horizontalBearing(imageXᵢ, frameWidth)   image bearing
 *   Relationship:  αᵢ ≈ ψᵢ − θ          (modulo 2π)
 *
 * SOLVING STRATEGY — Graph-Constrained Bearing Search
 * ----------------------------------------------------
 * 1. Sample candidate positions from all corridor nodes in the graph
 *    within a bounding radius of the detected landmark cluster.
 * 2. For each candidate (Cx, Cy):
 *      a. Compute world bearing ψᵢ to each landmark.
 *      b. Estimate heading: θ = circularMean(ψᵢ − αᵢ).
 *      c. Compute reprojection error: RMS angular mismatch.
 *      d. Distance validation: reject if any landmark is behind camera
 *         (ψᵢ − θ outside ±90°) or implausibly far (> MAX_LANDMARK_DIST_PX).
 * 3. Return the candidate with the lowest reprojection error.
 *
 * Complexity: O(N_candidates × N_landmarks), typically ~200 × 5 = 1 000 ops.
 * ─────────────────────────────────────────────────────────────────────────────
 */
object PnPSolver {

    /** Result of a successful pose solve. */
    data class PoseResult(
        /** Estimated camera position in map-pixel coordinates. */
        val cameraX: Double,
        val cameraY: Double,
        /** Estimated camera heading, degrees clockwise from map North [0, 360). */
        val headingDeg: Float,
        /** RMS angular reprojection error in degrees (lower = better). */
        val reprojectionErrorDeg: Float,
        /** Number of landmarks that were consistent (within [INLIER_THRESHOLD_DEG]). */
        val inlierCount: Int
    )

    // ── Tuneable constants ─────────────────────────────────────────────────────

    /** Maximum distance (map px) from landmark cluster centre to consider a candidate. */
    private const val SEARCH_RADIUS_PX = 250.0

    /** Maximum plausible distance (map px) from camera to any single landmark. */
    private const val MAX_LANDMARK_DIST_PX = 400.0

    /** A landmark is an inlier if its angular error is below this threshold (deg). */
    private const val INLIER_THRESHOLD_DEG = 15f

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Attempt to solve camera pose from N image-space / world-space correspondences.
     *
     * @param imagePoints  Normalised [0,1] image coordinates of each landmark (imageX, imageY).
     * @param worldPoints  Map-pixel world coordinates of each landmark (mapX, mapY).
     * @param intrinsics   Camera intrinsics (see [CameraIntrinsics.estimate]).
     * @param graph        Full mall graph — corridor nodes are used as candidate positions.
     * @param frameWidth   Camera frame width in pixels (same frame used for detection).
     * @return [PoseResult] or null if no valid pose found.
     */
    fun solve(
        imagePoints: List<Pair<Float, Float>>,
        worldPoints: List<Pair<Double, Double>>,
        intrinsics:  CameraIntrinsics,
        graph:       MallGraph,
        frameWidth:  Int
    ): PoseResult? {
        require(imagePoints.size == worldPoints.size)
        if (imagePoints.isEmpty()) return null

        // Compute image-space horizontal bearing for each landmark
        val imageBearingsRad: List<Float> = imagePoints.map { (ix, _) ->
            intrinsics.horizontalBearing(ix, frameWidth)
        }

        // Cluster centre — used to prune distant candidate nodes
        val clusterX = worldPoints.sumOf { it.first  } / worldPoints.size
        val clusterY = worldPoints.sumOf { it.second } / worldPoints.size

        // Gather candidate positions from corridor nodes near the cluster
        val candidates: List<GraphNode> = graph.nodes.filter { node ->
            val dx = node.x - clusterX
            val dy = node.y - clusterY
            sqrt(dx * dx + dy * dy) <= SEARCH_RADIUS_PX
        }

        if (candidates.isEmpty()) {
            Log.w(TAG, "No corridor candidates within ${SEARCH_RADIUS_PX}px of cluster")
            return null
        }

        Log.d(TAG, "Scoring ${candidates.size} corridor candidates")

        var bestError = Float.MAX_VALUE
        var bestResult: PoseResult? = null

        for (node in candidates) {
            val Cx = node.x
            val Cy = node.y

            // World bearing from this candidate to each landmark
            val worldBearingsRad: List<Float> = worldPoints.map { (Wx, Wy) ->
                atan2((Wx - Cx).toFloat(), -(Wy - Cy).toFloat())
            }

            // Distance validation: reject if any landmark is too far or behind camera
            val distances = worldPoints.map { (Wx, Wy) ->
                sqrt((Wx - Cx).pow(2) + (Wy - Cy).pow(2))
            }
            if (distances.any { it > MAX_LANDMARK_DIST_PX || it < 1.0 }) continue

            // Estimate heading: θ = circularMean(ψᵢ − αᵢ)
            val headingEstimates = worldBearingsRad.zip(imageBearingsRad).map { (psi, alpha) ->
                psi - alpha
            }
            val theta = circularMeanRad(headingEstimates)

            // Reject if landmarks are behind the camera (bearing offset > 90°)
            val allInFront = worldBearingsRad.all { psi ->
                val relAngle = normalizePi((psi - theta).toDouble())
                abs(relAngle) <= Math.PI / 2.0
            }
            if (!allInFront) continue

            // Reprojection error: RMS angular mismatch between observed and predicted α
            var sumSqErr = 0f
            var inliers  = 0
            for (i in imageBearingsRad.indices) {
                val predictedAlpha = normalizePi((worldBearingsRad[i] - theta).toDouble()).toFloat()
                val errorRad = abs(normalizePi((predictedAlpha - imageBearingsRad[i]).toDouble()).toFloat())
                sumSqErr += errorRad * errorRad
                if (Math.toDegrees(errorRad.toDouble()).toFloat() < INLIER_THRESHOLD_DEG) inliers++
            }
            val rmsRad = sqrt(sumSqErr / imageBearingsRad.size)
            val rmsDeg = Math.toDegrees(rmsRad.toDouble()).toFloat()

            if (rmsRad < bestError) {
                bestError = rmsRad
                val headingDeg = normaliseBearing(Math.toDegrees(theta.toDouble()).toFloat())
                bestResult = PoseResult(Cx, Cy, headingDeg, rmsDeg, inliers)
            }
        }

        bestResult?.let {
            Log.d(TAG, "Best pose: (%.1f, %.1f) heading=%.1f° err=%.2f° inliers=${it.inlierCount}/${imagePoints.size}".format(
                it.cameraX, it.cameraY, it.headingDeg, it.reprojectionErrorDeg
            ))
        } ?: Log.w(TAG, "PnPSolver: no valid pose found")

        return bestResult
    }

    // ── Distance filtering ─────────────────────────────────────────────────────

    /**
     * Estimates the approximate distance (map pixels) from the camera to a
     * landmark using the vertical image position and known camera geometry.
     *
     * Assumes:
     *   - Camera height above floor ≈ 1.5 m
     *   - Store-sign height above floor ≈ 2.2 m  →  sign is 0.7 m above camera
     *   - Map scale: 1 map pixel ≈ 0.05 m  →  vertical offset ≈ 14 map pixels
     *
     * @param imageYNorm   Normalised vertical image position [0 = top, 1 = bottom].
     * @param intrinsics   Camera intrinsics.
     * @param frameHeight  Frame height in pixels.
     * @return Estimated distance in map pixels, or null if estimate is invalid.
     */
    fun estimateDistancePx(
        imageYNorm:  Float,
        intrinsics:  CameraIntrinsics,
        frameHeight: Int
    ): Double? {
        val SIGN_HEIGHT_ABOVE_CAMERA_M = 0.7   // metres
        val MAP_SCALE_M_PER_PX        = 0.2   // metres per map pixel
        val signHeightPx               = SIGN_HEIGHT_ABOVE_CAMERA_M / MAP_SCALE_M_PER_PX  // 14 px

        // Vertical pixel offset (sign above image centre = negative v offset)
        val pixelY = imageYNorm * frameHeight
        val vOffset = intrinsics.cy - pixelY   // positive when sign is above centre

        if (vOffset <= 0f) return null   // sign below camera horizon — invalid

        // Distance = fy * signHeightPx / vOffset
        return (intrinsics.fy * signHeightPx / vOffset).toDouble()
    }

    // ── Math helpers ───────────────────────────────────────────────────────────

    /** Circular mean of a list of angles in radians. */
    private fun circularMeanRad(angles: List<Float>): Float {
        val sinSum = angles.sumOf { sin(it.toDouble()) }
        val cosSum = angles.sumOf { cos(it.toDouble()) }
        return atan2(sinSum, cosSum).toFloat()
    }

    /** Normalise angle to (−π, π]. */
    private fun normalizePi(angle: Double): Double {
        var a = angle % (2 * PI)
        if (a > PI)  a -= 2 * PI
        if (a < -PI) a += 2 * PI
        return a
    }

    /** Normalise bearing to [0, 360). */
    private fun normaliseBearing(deg: Float): Float {
        var d = deg % 360f
        if (d < 0) d += 360f
        return d
    }
}
