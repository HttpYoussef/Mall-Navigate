package com.example.mallar.ar.render

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.example.mallar.ar.AnchorKind
import com.example.mallar.ar.AnchorSpec
import com.google.android.filament.Engine
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ImageNode
import io.github.sceneview.node.Node
import kotlin.math.atan2

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Module 7 — Full Fidelity 3D Guidance Visual Factory
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Constructs Google Maps Live-View-style directional floor chevrons, amber turn
 * markers, and prominent emerald green arrival beacons.
 *
 * Markers are rendered as self-luminous, unlit, anti-aliased decals lying flat
 * directly on the corridor floor and oriented strictly in ARCore World Space
 * to align with physical hallway trajectories.
 */
class GuidanceVisualFactory(
    private val context: Context
) {
    companion object {
        val COLOR_STANDARD = Color(0xFF1A73E8) // Google Blue
        val COLOR_TURN = Color(0xFFFF9100)     // High-visibility Amber
        val COLOR_ARRIVAL = Color(0xFF00E676)  // High-visibility Emerald Green for Arrival Beacon

        const val ELEVATION_STANDARD_METERS = 0.020f
        const val ELEVATION_TURN_METERS = 0.025f
        const val ELEVATION_ARRIVAL_METERS = 0.030f

        // Expanded, highly visible Google Maps AR walking arrow dimensions
        val SIZE_STANDARD_METERS = Float3(0.70f, 0.70f, 0f)
        val SIZE_TURN_METERS = Float3(0.85f, 0.85f, 0f)
        val SIZE_ARRIVAL_METERS = Float3(1.10f, 1.10f, 0f)

        /**
         * Computes the heading angle (in degrees) in ARCore World Space
         * along the corridor tangent vector from (currentX, currentZ) to (nextX, nextZ).
         *
         * @return Rotation angle around Y-axis in degrees.
         */
        fun computeWorldHeadingDeg(
            currentWorldX: Float,
            currentWorldZ: Float,
            nextWorldX: Float,
            nextWorldZ: Float
        ): Float {
            val deltaX = (nextWorldX - currentWorldX).toDouble()
            val deltaZ = (nextWorldZ - currentWorldZ).toDouble()
            if (deltaX == 0.0 && deltaZ == 0.0) return 0f
            val rad = atan2(deltaX, deltaZ)
            return Math.toDegrees(rad).toFloat()
        }
    }

    private var materialLoader: MaterialLoader? = null

    fun getOrCreateMaterialLoader(engine: Engine): MaterialLoader {
        return materialLoader ?: MaterialLoader(engine, context).also { materialLoader = it }
    }

    /**
     * Creates a styled, path-oriented Google Maps-style directional floor chevron.
     */
    fun createGuidanceMarker(
        engine: Engine,
        spec: AnchorSpec,
        headingDeg: Float
    ): Node {
        val loader = getOrCreateMaterialLoader(engine)
        val bitmap = if (spec.kind == AnchorKind.TURN) {
            ArVisualAssetGenerator.getTurnChevron()
        } else {
            ArVisualAssetGenerator.getStandardChevron()
        }
        val size = if (spec.kind == AnchorKind.TURN) SIZE_TURN_METERS else SIZE_STANDARD_METERS
        val elevation = if (spec.kind == AnchorKind.TURN) ELEVATION_TURN_METERS else ELEVATION_STANDARD_METERS

        val marker = ImageNode(
            materialLoader = loader,
            bitmap = bitmap,
            size = size
        )

        marker.position = Position(0f, elevation, 0f)
        // Rotate -90 degrees around X to lay flat horizontally on the floor,
        // and around Y to point along the corridor heading.
        marker.rotation = Rotation(-90f, headingDeg, 0f)
        marker.isHittable = false

        return marker
    }

    /**
     * Creates a prominent Google Maps-style arrival target decal indicating destination arrival.
     */
    fun createArrivalBeacon(engine: Engine): Node {
        val loader = getOrCreateMaterialLoader(engine)
        val bitmap = ArVisualAssetGenerator.getArrivalBeacon()

        val beacon = ImageNode(
            materialLoader = loader,
            bitmap = bitmap,
            size = SIZE_ARRIVAL_METERS
        )
        beacon.position = Position(0f, ELEVATION_ARRIVAL_METERS, 0f)
        beacon.rotation = Rotation(-90f, 0f, 0f)
        beacon.isHittable = false

        return beacon
    }

    fun dispose() {
        materialLoader?.destroy()
        materialLoader = null
        ArVisualAssetGenerator.dispose()
    }
}
