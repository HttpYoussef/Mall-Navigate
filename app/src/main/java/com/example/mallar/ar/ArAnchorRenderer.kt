package com.example.mallar.ar

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import com.example.mallar.ar.model.RouteNodeMetadata
import com.example.mallar.ar.render.FloorPlaneConfidenceMonitor
import com.example.mallar.ar.render.GuidanceVisualFactory
import com.example.mallar.ar.render.RenderPoseSmoother
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.material.setColor
import io.github.sceneview.math.Position
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Module 6 / Module 7 Bridge — ArAnchorRenderer
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Runtime owner for Module 6's active anchor window and Module 7's full-fidelity
 * rendering pipeline. Scene mutations are performed only from the ARSceneView
 * frame callback.
 *
 * Phase 8 integrates supervisory controls: Transition Mode, Arrival Beacon,
 * and Degraded Tracking visual state.
 */
class ArAnchorRenderer(
    private val context: Context,
    private val config: AnchorWindowConfig = AnchorWindowConfig.forTier(DeviceTier.detect(context)),
    private val planner: AnchorWindowPlanner = AnchorWindowPlanner(config),
    val poseSmoother: RenderPoseSmoother = RenderPoseSmoother(minCutoffHz = if (config.smoothingAlpha > 0.2f) 1.5 else 1.0),
    val planeConfidenceMonitor: FloorPlaneConfidenceMonitor = FloorPlaneConfidenceMonitor(),
    val visualFactory: GuidanceVisualFactory = GuidanceVisualFactory(context)
) {
    companion object {
        private const val TAG = "ArAnchorRenderer"
        private const val FADE_STEP = 1f / 8f
    }

    private data class ManagedAnchor(
        val spec: AnchorSpec,
        val anchorNode: AnchorNode,
        val marker: CubeNode,
        val materialColor: Color,
        val initialWorldX: Float,
        val initialWorldZ: Float,
        val correction: CorrectionInterpolator,
        var alpha: Float = 1f,
        var fadingOut: Boolean = false,
        var lastTransformAcceptedAt: Long = Long.MIN_VALUE
    )

    private val anchors = LinkedHashMap<Int, ManagedAnchor>()
    private var lastTransformAcceptedAt = Long.MIN_VALUE
    private var lastPlanGeneration = Long.MIN_VALUE

    // Phase 8 Supervisory States
    private var isTransitionMode: Boolean = false
    private var isArrived: Boolean = false
    private var isTrackingDegraded: Boolean = false
    private var arrivalAnchorNode: AnchorNode? = null

    val activeAnchorCount: Int
        get() = anchors.size

    fun getActiveAnchorDetails(): List<String> {
        return anchors.values.map { managed ->
            "Node#${managed.spec.node.nodeId}(kind=${managed.spec.kind}, alpha=${managed.alpha}, world=(${managed.initialWorldX}, ${managed.initialWorldZ}))"
        }
    }

    fun setTransitionMode(enabled: Boolean) {
        if (isTransitionMode == enabled) return
        isTransitionMode = enabled
        if (enabled) {
            Log.d(TAG, "Transition mode enabled: fading out active anchors.")
            anchors.values.forEach { it.fadingOut = true }
        }
    }

    fun setArrivedMode(
        arrived: Boolean,
        destinationNode: RouteNodeMetadata?,
        sceneView: ARSceneView? = null,
        session: Session? = null,
        transform: FacilityTransform? = null
    ) {
        if (isArrived == arrived) return
        isArrived = arrived
        if (arrived) {
            Log.d(TAG, "Arrived mode enabled: spawning arrival beacon.")
            anchors.values.forEach { it.fadingOut = true }
            if (destinationNode != null && sceneView != null && session != null && transform != null) {
                spawnArrivalBeacon(sceneView, session, transform, destinationNode)
            }
        } else {
            arrivalAnchorNode?.destroy()
            arrivalAnchorNode = null
        }
    }

    fun setTrackingDegraded(degraded: Boolean) {
        isTrackingDegraded = degraded
    }

    fun notifyRouteRebuilt() {
        lastPlanGeneration = Long.MIN_VALUE
    }

    fun update(
        sceneView: ARSceneView,
        session: Session,
        frame: Frame,
        cameraPose: Pose,
        localPose: LocalTrackingPose,
        transform: FacilityTransform,
        transformRevision: Long,
        route: List<RouteNodeMetadata>
    ) {
        // If in transition mode or arrived, pause regular route chevron reconciliation
        if (!isTransitionMode && !isArrived) {
            val facilityPosition = transform.facilityPosition(localPose, config.pixelsPerMeter)
            val plan = planner.plan(route, facilityPosition.first, facilityPosition.second)
            if (plan.generation != lastPlanGeneration) {
                Log.d(TAG, "Anchor plan updated: gen=${plan.generation}, routeSize=${route.size}, " +
                    "plannedActive=${plan.active.size}, currentAnchors=${anchors.size}, " +
                    "userFacilityPos=(${facilityPosition.first}, ${facilityPosition.second})")
                reconcile(sceneView, session, frame, cameraPose, localPose, transform, transformRevision, plan, route)
                lastPlanGeneration = plan.generation
            }
        }

        val transformChanged = transformRevision != lastTransformAcceptedAt
        if (transformChanged && lastTransformAcceptedAt != Long.MIN_VALUE) {
            anchors.values.forEach { managed ->
                val (newWorldX, newWorldZ) = transform.worldPositionFor(
                    managed.spec.node.x,
                    managed.spec.node.y,
                    config.pixelsPerMeter
                )
                val targetCorrection = LocalAnchorOffset(
                    (newWorldX - managed.initialWorldX).toDouble(),
                    0.0,
                    (newWorldZ - managed.initialWorldZ).toDouble()
                )
                managed.correction.begin(currentCorrection(managed), targetCorrection)
                managed.lastTransformAcceptedAt = transformRevision
            }
        }
        lastTransformAcceptedAt = transformRevision

        val maxAlpha = if (isTrackingDegraded) 0.4f else 1.0f
        val completed = mutableListOf<Int>()
        anchors.forEach { (nodeId, managed) ->
            if (managed.fadingOut) {
                managed.alpha = (managed.alpha - FADE_STEP).coerceAtLeast(0f)
                if (managed.alpha <= 0f) completed += nodeId
            } else {
                managed.alpha = (managed.alpha + FADE_STEP).coerceAtMost(maxAlpha)
            }
            managed.marker.materialInstance.setColor(managed.materialColor.copy(alpha = managed.alpha))
            val baseElevation = if (managed.spec.kind == AnchorKind.TURN) {
                GuidanceVisualFactory.ELEVATION_TURN_METERS
            } else {
                GuidanceVisualFactory.ELEVATION_STANDARD_METERS
            }
            val correction = managed.correction.step()
            managed.marker.position = Position(
                correction.xMeters.toFloat(),
                baseElevation + correction.yMeters.toFloat(),
                correction.zMeters.toFloat()
            )
        }
        completed.forEach { nodeId ->
            anchors.remove(nodeId)?.let { managed ->
                managed.anchorNode.destroy()
                Log.d(TAG, "Destroyed anchor for node $nodeId after fadeout. Remaining=${anchors.size}")
            }
        }
    }

    private fun spawnArrivalBeacon(
        sceneView: ARSceneView,
        session: Session,
        transform: FacilityTransform,
        destinationNode: RouteNodeMetadata
    ) {
        arrivalAnchorNode?.destroy()
        val (worldX, worldZ) = transform.worldPositionFor(
            destinationNode.x,
            destinationNode.y,
            config.pixelsPerMeter
        )
        val (floorY, plane) = planeConfidenceMonitor.resolveFloorElevation(
            session,
            worldX,
            worldZ,
            config.floorHeightMeters
        )
        val worldPose = Pose.makeTranslation(worldX, floorY, worldZ)
        val anchor = if (plane != null && plane.isPoseInPolygon(worldPose)) {
            plane.createAnchor(worldPose)
        } else {
            session.createAnchor(worldPose)
        }
        val anchorNode = AnchorNode(sceneView.engine, anchor)
        val beacon = visualFactory.createArrivalBeacon(sceneView.engine)
        anchorNode.addChildNode(beacon)
        sceneView.addChildNode(anchorNode)
        arrivalAnchorNode = anchorNode
        Log.d(TAG, "Spawned arrival beacon at world=($worldX, $floorY, $worldZ)")
    }

    private fun reconcile(
        sceneView: ARSceneView,
        session: Session,
        frame: Frame,
        cameraPose: Pose,
        localPose: LocalTrackingPose,
        transform: FacilityTransform,
        transformRevision: Long,
        plan: AnchorWindowPlan,
        route: List<RouteNodeMetadata>
    ) {
        val desiredIds = plan.nodeIds
        anchors.forEach { (nodeId, managed) ->
            if (nodeId !in desiredIds) managed.fadingOut = true
        }

        plan.active.forEach { spec ->
            if (anchors.containsKey(spec.node.nodeId)) {
                anchors[spec.node.nodeId]?.fadingOut = false
                return@forEach
            }

            val (worldX, worldZ) = transform.worldPositionFor(
                spec.node.x,
                spec.node.y,
                config.pixelsPerMeter
            )
            
            // Calculate next waypoint position in ARCore World Space to determine corridor tangent heading
            val (nextWorldX, nextWorldZ) = if (spec.routeIndex < route.lastIndex) {
                transform.worldPositionFor(route[spec.routeIndex + 1].x, route[spec.routeIndex + 1].y, config.pixelsPerMeter)
            } else if (spec.routeIndex > 0) {
                val (prevX, prevZ) = transform.worldPositionFor(route[spec.routeIndex - 1].x, route[spec.routeIndex - 1].y, config.pixelsPerMeter)
                worldX + (worldX - prevX) to worldZ + (worldZ - prevZ)
            } else {
                worldX to worldZ + 1.0f
            }
            val headingDeg = GuidanceVisualFactory.computeWorldHeadingDeg(worldX, worldZ, nextWorldX, nextWorldZ)

            val (floorY, plane) = planeConfidenceMonitor.resolveFloorElevation(
                session,
                worldX,
                worldZ,
                cameraPose.ty() + config.floorHeightMeters
            )
            val worldPose = Pose.makeTranslation(worldX, floorY, worldZ)
            val anchor = if (plane != null && plane.isPoseInPolygon(worldPose)) {
                plane.createAnchor(worldPose)
            } else {
                session.createAnchor(worldPose)
            }
            val anchorNode = AnchorNode(sceneView.engine, anchor)
            val baseColor = if (spec.kind == AnchorKind.TURN) GuidanceVisualFactory.COLOR_TURN else GuidanceVisualFactory.COLOR_STANDARD
            
            val marker = visualFactory.createGuidanceMarker(
                engine = sceneView.engine,
                spec = spec,
                headingDeg = headingDeg
            )
            
            anchorNode.addChildNode(marker)
            sceneView.addChildNode(anchorNode)
            anchors[spec.node.nodeId] = ManagedAnchor(
                spec = spec,
                anchorNode = anchorNode,
                marker = marker,
                materialColor = baseColor,
                initialWorldX = worldX,
                initialWorldZ = worldZ,
                correction = CorrectionInterpolator(config.correctionFrames),
                alpha = 1f,
                lastTransformAcceptedAt = transformRevision
            )
            Log.d(TAG, "Reconciled anchor node=${spec.node.nodeId} at AR world=($worldX, $floorY, $worldZ), " +
                "corridorHeading=$headingDeg, facility=(${spec.node.x}, ${spec.node.y}). Total active=${anchors.size}")
        }
    }

    private fun currentCorrection(managed: ManagedAnchor): LocalAnchorOffset {
        val position = managed.marker.position
        val baseElevation = if (managed.spec.kind == AnchorKind.TURN) {
            GuidanceVisualFactory.ELEVATION_TURN_METERS
        } else {
            GuidanceVisualFactory.ELEVATION_STANDARD_METERS
        }
        return LocalAnchorOffset(
            position.x.toDouble(),
            (position.y - baseElevation).toDouble(),
            position.z.toDouble()
        )
    }

    fun dispose() {
        arrivalAnchorNode?.destroy()
        arrivalAnchorNode = null
        anchors.clear()
        visualFactory.dispose()
        poseSmoother.reset()
        planeConfidenceMonitor.reset()
        lastPlanGeneration = Long.MIN_VALUE
        lastTransformAcceptedAt = Long.MIN_VALUE
    }

    /**
     * Releases native objects while the render surface is still alive.
     */
    fun disposeForSurfaceTeardown() {
        arrivalAnchorNode?.destroy()
        arrivalAnchorNode = null
        anchors.values.forEach { managed -> managed.anchorNode.destroy() }
        anchors.clear()
        visualFactory.dispose()
        poseSmoother.reset()
        planeConfidenceMonitor.reset()
        lastPlanGeneration = Long.MIN_VALUE
        lastTransformAcceptedAt = Long.MIN_VALUE
    }
}
