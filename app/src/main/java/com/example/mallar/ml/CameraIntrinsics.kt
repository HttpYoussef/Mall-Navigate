package com.example.mallar.ml

import kotlin.math.atan2
import kotlin.math.tan

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * CameraIntrinsics
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Describes the pinhole camera model used for converting image-space logo
 * coordinates into real-world bearing angles.
 *
 *   u = fx * (X_cam / Z_cam) + cx      (horizontal projection)
 *   v = fy * (Y_cam / Z_cam) + cy      (vertical projection)
 *
 * For the indoor 2-D PnP solver we only need [fx] and [cx] to compute the
 * horizontal bearing of a detected landmark:
 *
 *   α = atan2(u_pixels - cx, fx)       (radians, positive = right of centre)
 *
 * These intrinsics are estimated from the image dimensions and a typical
 * Android camera horizontal field-of-view.  Calibration-quality intrinsics
 * from Camera2 API would improve accuracy further, but are not required for
 * indoor corridor-scale navigation.
 */
data class CameraIntrinsics(
    /** Horizontal focal length in pixels. */
    val fx: Float,
    /** Vertical focal length in pixels. */
    val fy: Float,
    /** Principal-point x (horizontal image centre in pixels). */
    val cx: Float,
    /** Principal-point y (vertical image centre in pixels). */
    val cy: Float
) {

    /**
     * Convert a normalised image X coordinate [0, 1] to a horizontal bearing
     * angle relative to the camera optical axis (radians).
     *
     * Positive angle → landmark is to the RIGHT of centre.
     * Negative angle → landmark is to the LEFT of centre.
     */
    fun horizontalBearing(imageXNorm: Float, imageWidth: Int): Float {
        val pixelX = imageXNorm * imageWidth
        return atan2(pixelX - cx, fx)
    }

    /**
     * Convert a normalised image Y coordinate [0, 1] to a vertical bearing
     * angle relative to the camera optical axis (radians).
     *
     * Positive angle → landmark is BELOW centre (camera tilted up = sign further away).
     */
    fun verticalBearing(imageYNorm: Float, imageHeight: Int): Float {
        val pixelY = imageYNorm * imageHeight
        return atan2(pixelY - cy, fy)
    }

    companion object {

        /**
         * Estimate intrinsics from image resolution and a typical Android camera
         * horizontal field-of-view.
         *
         * Most rear cameras have 60–80° HFoV.  68° is a conservative mid-range
         * estimate for the common 1080p portrait frame that CameraX delivers.
         *
         * @param imageWidth    Full frame width in pixels (before any cropping).
         * @param imageHeight   Full frame height in pixels.
         * @param hFovDeg       Estimated horizontal field-of-view in degrees.
         */
        fun estimate(
            imageWidth:  Int,
            imageHeight: Int,
            hFovDeg:     Float = 68f
        ): CameraIntrinsics {
            val fx = (imageWidth / 2f) / tan(Math.toRadians(hFovDeg / 2.0)).toFloat()
            // Assume square pixels; fy ≈ fx scaled by aspect ratio
            val fy = fx * (imageHeight.toFloat() / imageWidth.toFloat())
            return CameraIntrinsics(
                fx = fx,
                fy = fy,
                cx = imageWidth  / 2f,
                cy = imageHeight / 2f
            )
        }
    }
}
