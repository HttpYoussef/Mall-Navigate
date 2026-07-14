package com.example.mallar.utils

import com.example.mallar.data.GraphNode

object CoordinateTransformer {

    // Configurable parameters for debugging and adjusting alignment
    var mapScaleX: Float = 1.0f
    var mapScaleY: Float = 1.0f
    var offsetX: Float = 0f
    var offsetY: Float = 0f
    var invertY: Boolean = false
    
    // Canvas dimensions (if needed for inversion)
    var canvasHeight: Float = 685f

    /**
     * Converts a raw map graph node coordinate into the actual image pixel coordinate.
     */
    fun transformX(rawX: Double): Float {
        return (rawX.toFloat() * mapScaleX) + offsetX
    }

    /**
     * Converts a raw map graph node coordinate into the actual image pixel coordinate.
     */
    fun transformY(rawY: Double): Float {
        val y = rawY.toFloat()
        val scaledY = (y * mapScaleY) + offsetY
        return if (invertY) {
            canvasHeight - scaledY
        } else {
            scaledY
        }
    }
}
