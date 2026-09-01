package com.example.mallar.ar.ui

import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mallar.ar.ArAnchorRenderer
import com.example.mallar.ar.ArCoreSessionManager
import com.example.mallar.ar.CandidateFix
import com.example.mallar.ar.LocalTrackingPose
import com.example.mallar.ar.LocalizationLayer
import com.example.mallar.ar.RoutePathLayer
import com.example.mallar.ar.supervision.ArRuntimeState
import com.example.mallar.ar.supervision.DriftRecoverySupervisor
import com.example.mallar.ar.supervision.SupervisoryInstruction
import com.example.mallar.data.GraphNode
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.data.PlaceRepository
import com.example.mallar.ml.LocalizationEngine
import com.example.mallar.ml.LogoDetector
import com.example.mallar.navigation.DriftMonitor
import com.google.ar.core.TrackingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * ArSceneViewWrapper
 * ─────────────────────────────────────────────────────────────────────────────
 * 
 * Compose wrapper for the SceneView/Filament AR surface.
 * Handles sequential camera handoff and Android lifecycle integration.
 * Phase 8 integrates Module 8 DriftRecoverySupervisor into frame update loop.
 */
@Composable
fun ArSceneViewWrapper(
    modifier: Modifier = Modifier,
    sessionManager: ArCoreSessionManager,
    localizationLayer: LocalizationLayer? = null,
    routePathLayer: RoutePathLayer? = null,
    initialStartNode: GraphNode? = null,
    initialHeadingDeg: Float? = null,
    active: Boolean = true,
    supervisor: DriftRecoverySupervisor? = null,
    driftState: DriftMonitor.DriftState? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activeState = androidx.compose.runtime.rememberUpdatedState(active)

    val sceneView = remember {
        ManagedARSceneView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    val scope = rememberCoroutineScope()
    val detector = remember(context) { lazy { LogoDetector.getInstance(context) } }
    val places = remember(context) { lazy { PlaceRepository.load(context) } }
    val graph = remember { MallGraphRepository.loadedGraph }
    val detectedTier = remember(context) { com.example.mallar.ar.DeviceTier.detect(context) }
    val anchorRenderer = remember(context, detectedTier) { 
        ArAnchorRenderer(
            context = context,
            config = com.example.mallar.ar.AnchorWindowConfig.forTier(detectedTier)
        ) 
    }
    var lastDiagnosticLogMs = remember { 0L }

    // Android Lifecycle Integration (Finding 2)
    DisposableEffect(lifecycleOwner) {
        // Surface visibility can change before Compose dispatches ON_PAUSE or
        // removes this view (notably when switching Camera -> Map). Stop the
        // producer and ARCore session at that earlier boundary as well.
        sceneView.onRenderSurfaceActiveChanged = { active ->
            if (!active) {
                sceneView.isPaused = true
                sessionManager.pause()
                anchorRenderer.disposeForSurfaceTeardown()
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (activeState.value) {
                        sessionManager.resume()
                        sceneView.isPaused = false
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    sceneView.isPaused = true
                    sessionManager.pause()
                }
                else -> {}
            }
        }

        sceneView.onSessionUpdated = { _, frame ->
            val camera = frame.camera
            val frameNow = SystemClock.elapsedRealtime()
            sessionManager.updateTrackingState(
                camera.trackingState,
                camera.trackingFailureReason
            )

            // Phase 5 slow localization cycle. The camera image is copied from
            // this ARCore frame, then recognition runs off the render thread.
            if (localizationLayer != null && graph != null && camera.trackingState == TrackingState.TRACKING) {
                val pose = camera.pose
                val now = frameNow
                val localPose = LocalTrackingPose(
                    xMeters = pose.tx().toDouble(),
                    yMeters = pose.tz().toDouble(),
                    headingDeg = 0f,
                    timestampMs = now
                )
                if (localizationLayer.transform == null && initialStartNode != null) {
                    localizationLayer.initializeFromScan(initialStartNode, initialHeadingDeg, localPose, now)
                    supervisor?.onInitialFixAccepted(now)
                    Log.d("ArSceneViewWrapper", "Initialized transform from start node #${initialStartNode.id} (${initialStartNode.x}, ${initialStartNode.y})")
                }
                if (localizationLayer.beginPeriodicRefix(now, localPose)) {
                    val imageSnapshot = sessionManager.copyCameraImage(frame)
                    if (imageSnapshot == null) {
                        localizationLayer.cancelPeriodicRefix()
                        supervisor?.onFixRejected(now)
                    } else {
                        scope.launch {
                            try {
                                val candidate: CandidateFix? = withContext(Dispatchers.Default) {
                                    val bitmap = imageSnapshot.toBitmap() ?: return@withContext null
                                    try {
                                        CandidateFix.from(
                                            LocalizationEngine.estimatePose(
                                                bitmap,
                                                detector.value,
                                                graph,
                                                places.value
                                            )
                                        )
                                    } finally {
                                        bitmap.recycle()
                                    }
                                }
                                if (candidate != null) {
                                    localizationLayer.completePeriodicRefix(candidate, localPose, now)
                                    supervisor?.onPeriodicFixAccepted(now)
                                } else {
                                    localizationLayer.cancelPeriodicRefix()
                                    supervisor?.onFixRejected(now)
                                }
                            } catch (_: Exception) {
                                localizationLayer.cancelPeriodicRefix()
                                supervisor?.onFixRejected(now)
                            }
                        }
                    }
                }
            }

            // Phase 8 Supervisory Layer evaluation
            if (supervisor != null) {
                val qx = camera.pose.qx()
                val qy = camera.pose.qy()
                val qz = camera.pose.qz()
                val qw = camera.pose.qw()
                val sinyCosp = 2.0 * (qw * qy + qx * qz)
                val cosyCosp = 1.0 - 2.0 * (qy * qy + qz * qz)
                val cameraYawDeg = Math.toDegrees(atan2(sinyCosp, cosyCosp)).toFloat()
                val userFacilityHeadingDeg = (localizationLayer?.transform?.headingDeg ?: 0f) + cameraYawDeg

                val instruction = supervisor.evaluate(
                    timestampMs = frameNow,
                    trackingState = camera.trackingState,
                    trackingFailureReason = camera.trackingFailureReason,
                    transform = localizationLayer?.transform,
                    localPose = localPoseFor(camera.pose, frameNow),
                    userHeadingDeg = userFacilityHeadingDeg,
                    route = routePathLayer?.getRouteMetadata() ?: emptyList(),
                    driftState = driftState ?: DriftMonitor.DriftState()
                )

                when (instruction) {
                    is SupervisoryInstruction.EnterTransitionMode -> anchorRenderer.setTransitionMode(true)
                    is SupervisoryInstruction.ExitTransitionMode -> anchorRenderer.setTransitionMode(false)
                    is SupervisoryInstruction.EnterArrivedState -> {
                        val session = sessionManager.getSession()
                        if (session != null && localizationLayer?.transform != null) {
                            anchorRenderer.setArrivedMode(true, instruction.destinationNode, sceneView, session, localizationLayer.transform)
                        }
                    }
                    is SupervisoryInstruction.RebuildRoute -> {
                        routePathLayer?.recalculateFromFacilityPosition(instruction.facilityX, instruction.facilityY)
                        anchorRenderer.notifyRouteRebuilt()
                        supervisor.onRouteRebuildComplete(frameNow)
                    }
                    is SupervisoryInstruction.ApplyDriftCorrection -> {
                        // Handled smoothly by anchorRenderer's correction interpolator
                    }
                    is SupervisoryInstruction.RequestReFix -> {
                        // Next periodic refix opportunity will be prioritized
                    }
                    SupervisoryInstruction.None -> {}
                }

                anchorRenderer.setTrackingDegraded(supervisor.state == ArRuntimeState.TRACKING_DEGRADED)
            }

            // Phase 6 fast-cycle anchor management. This runs only at the frame
            // boundary and consumes the latest validated Module 4 transform.
            if (routePathLayer != null && localizationLayer?.transform != null && camera.trackingState == TrackingState.TRACKING) {
                val transform = localizationLayer.transform
                val session = sessionManager.getSession()
                if (transform != null && session != null) {
                    anchorRenderer.update(
                        sceneView = sceneView,
                        session = session,
                        frame = frame,
                        cameraPose = camera.pose,
                        localPose = localPoseFor(camera.pose, frameNow),
                        transform = transform,
                        transformRevision = localizationLayer.transformRevision,
                        route = routePathLayer.getRouteMetadata()
                    )
                }
            }

            // Periodic diagnostic log every 2 seconds
            if (camera.trackingState == TrackingState.TRACKING && frameNow - lastDiagnosticLogMs >= 2000L) {
                lastDiagnosticLogMs = frameNow
                Log.d(
                    "ArSceneViewWrapper",
                    "Diagnostics: transformReady=${localizationLayer?.transform != null}, " +
                        "routeNodes=${routePathLayer?.getRouteMetadata()?.size ?: 0}, " +
                        "activeAnchors=${anchorRenderer.activeAnchorCount}, " +
                        "supervisorState=${supervisor?.state}, " +
                        "initialStartNodeId=${initialStartNode?.id}"
                )
            }
        }

        // Install the manager-owned session before observing the lifecycle.
        // addObserver() can synchronously dispatch ON_RESUME for an already
        // active host, so the shared session must be ready first.
        try {
            val session = sessionManager.createSession()
            if (session != null) {
                sceneView.setManagedSession(session)
            }
        } catch (e: Exception) {
            Log.e("ArSceneViewWrapper", "Error establishing managed ARSession", e)
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            sceneView.onSessionUpdated = null
            sceneView.isPaused = true
            sceneView.onRenderSurfaceActiveChanged = null
            anchorRenderer.dispose()
            sceneView.clearManagedSession()
            lifecycleOwner.lifecycle.removeObserver(observer)
            sessionManager.pause()
        }
    }

    // Map mode keeps this exact SceneView/session pair mounted, but pauses
    // frame production. This avoids rebinding a previously torn-down native
    // SceneView bridge when returning to AR mode.
    androidx.compose.runtime.LaunchedEffect(active) {
        if (active) {
            sessionManager.resume()
            sceneView.isPaused = false
        } else {
            sceneView.isPaused = true
            sessionManager.pause()
        }
    }

    AndroidView(
        factory = { sceneView },
        modifier = modifier.alpha(if (active) 1f else 0f)
    )
}

private fun localPoseFor(pose: com.google.ar.core.Pose, timestampMs: Long): LocalTrackingPose =
    LocalTrackingPose(
        xMeters = pose.tx().toDouble(),
        yMeters = pose.tz().toDouble(),
        headingDeg = 0f,
        timestampMs = timestampMs
    )
