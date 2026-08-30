package com.example.mallar.ar.render

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.example.mallar.ar.AnchorKind
import com.example.mallar.ar.AnchorSpec
import com.example.mallar.ar.model.RouteNodeMetadata
import com.google.android.filament.Engine
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Float4
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Module 7 — Full Fidelity 3D Guidance Visual Factory
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Constructs Live-View-style directional floor chevrons, amber turn markers,
 * and prominent emerald green arrival beacons.
 * Computes orientation strictly in ARCore World Space to align geometry with
 * physical corridor hallways.
 */
class GuidanceVisualFactory(
    private val context: Context
) {
    companion object {
        val COLOR_STANDARD = Color(0xFF00BCD4) // High-contrast Cyan
        val COLOR_TURN = Color(0xFFFFB300)     // High-visibility Amber
        val COLOR_ARRIVAL = Color(0xFF4CAF50)  // High-visibility Emerald Green for Arrival Beacon

        const val ELEVATION_STANDARD_METERS = 0.025f
        const val ELEVATION_TURN_METERS = 0.040f
        const val ELEVATION_ARRIVAL_METERS = 0.60f

        val SCALE_STANDARD = Float3(0.20f, 0.05f, 0.45f)
        val SCALE_TURN = Float3(0.35f, 0.08f, 0.35f)
        val SCALE_ARRIVAL = Float3(0.40f, 1.20f, 0.40f)

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
     * Creates a styled, path-oriented 3D guidance marker for an anchor.
     */
    fun createGuidanceMarker(
        engine: Engine,
        spec: AnchorSpec,
        headingDeg: Float
    ): CubeNode {
        val loader = getOrCreateMaterialLoader(engine)
        val baseColor = if (spec.kind == AnchorKind.TURN) COLOR_TURN else COLOR_STANDARD
        val materialInstance = loader.createColorInstance(baseColor.copy(alpha = 1f))

        val marker = CubeNode(
            engine = engine,
            materialInstance = materialInstance
        )

        marker.scale = if (spec.kind == AnchorKind.TURN) SCALE_TURN else SCALE_STANDARD
        val baseElevation = if (spec.kind == AnchorKind.TURN) ELEVATION_TURN_METERS else ELEVATION_STANDARD_METERS
        marker.position = Position(0f, baseElevation, 0f)
        marker.rotation = Rotation(0f, headingDeg, 0f)
        marker.isHittable = false

        return marker
    }

    /**
     * Creates a prominent 3D emerald green beacon indicating destination arrival.
     */
    fun createArrivalBeacon(engine: Engine): CubeNode {
        val loader = getOrCreateMaterialLoader(engine)
        val materialInstance = loader.createColorInstance(COLOR_ARRIVAL.copy(alpha = 1f))

        val beacon = CubeNode(
            engine = engine,
            materialInstance = materialInstance
        )
        beacon.scale = SCALE_ARRIVAL
        beacon.position = Position(0f, ELEVATION_ARRIVAL_METERS, 0f)
        beacon.rotation = Rotation(0f, 0f, 0f)
        beacon.isHittable = false

        return beacon
    }

    fun dispose() {
        materialLoader?.destroy()
        materialLoader = null
    }
}
