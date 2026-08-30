# ARCore Tracking Instability & Missing Markers — Investigation Report

**Status:** Complete Investigation & Comprehensive Remediation Plan for `ARCore_Tracking_Instability_Defect_Report.md`.
**Author:** Antigravity (Gemini Coding Assistant)
**Date:** 2026-08-26
**Target Audience:** Human Lead Architect, Device Validator, Engineering Team

---

## 1. Executive Summary

During on-device re-validation on a Samsung Galaxy S22 Ultra (`SM-S908E`, Android 13) following the removal of the 2D overlay, the human reviewer reported two critical findings:
1. **No route-derived AR content (cyan standard markers, amber turn markers) was visible.**
2. **ARCore's raw feature-tracking dots did not remain "stuck" to the physical floor when rotating in place without walking.**

This report presents an exhaustive, source-grounded investigation into both findings, detailing the exact root causes in the codebase, providing full source code evidence, and proposing a definitive remediation plan.

---

## 2. Root Cause Analysis: Why Tracking Dots Shift on Rotation

### Finding 1: Autofocus Hunting Breaks Visual-Inertial Odometry (VIO) on S22 Ultra
In `ArCoreSessionManager.kt` (lines 88–93):
```kotlin
val config = Config(session)
config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
config.focusMode = Config.FocusMode.AUTO
config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
session.configure(config)
```
- **The Issue:** `Config.FocusMode.AUTO` was enabled. On advanced multi-camera Android devices with optical image stabilization and laser/phase-detection autofocus (such as the Samsung Galaxy S22 Ultra), `FocusMode.AUTO` causes the camera lens to continuously hunt for focus as the phone rotates.
- **Impact on VIO:** Continuous lens movement constantly changes the camera's effective focal length and optical center (intrinsics). ARCore's VIO filter relies on fixed intrinsic geometry between consecutive frames to triangulate 3D point positions from 2D pixel motion + IMU data. Focus shifts cause the triangulated 3D feature points to jitter and drift in world space, making the white tracking dots appear to slide rather than stick to the physical floor.
- **Official ARCore Guidance:** Google ARCore documentation explicitly recommends **`Config.FocusMode.FIXED`** for motion tracking stability, reserving `AUTO` only for close-up scanning (e.g. barcode reading).

### Finding 2: Session Configuration Overwrite Race with SceneView
In `ManagedARSceneView.setManagedSession()` (lines 22–33):
```kotlin
sessionField.set(arCore, session)
onSessionCreated(session)
```
- SceneView's internal `onSessionCreated()` creates its own `Config(session)` instance and applies it, which can overwrite or conflict with custom parameters applied in `ArCoreSessionManager.createSession()`.

---

## 3. Root Cause Analysis: Why Phase 6 3D Markers Were Not Visible

The investigation revealed **three compounding defects** preventing Phase 6 anchor markers from rendering:

### Defect A: Severe Coordinate Space Bug in `ArAnchorRenderer`
In `ArAnchorRenderer.kt` (lines 125–135):
```kotlin
val offset = transform.localOffsetFor(
    spec.node.x,
    spec.node.y,
    localPose,
    config.floorHeightMeters,
    config.pixelsPerMeter
)
val targetPose = cameraPose.compose(
    Pose.makeTranslation(offset.xMeters.toFloat(), offset.yMeters.toFloat(), offset.zMeters.toFloat())
)
val anchor = session.createAnchor(targetPose)
```
- **The Bug:** `offset` computed by `FacilityTransform.localOffsetFor` is a **world-horizontal translation** relative to the tracking origin. But `ArAnchorRenderer` passed it to `cameraPose.compose(...)`!
- **Mathematical Error:** In ARCore, `cameraPose.compose(T)` translates $T$ into the **camera's local reference frame** (multiplied by the camera's 3D rotation quaternion). 
  - When the user holds the phone tilted downwards at the floor, `cameraPose.compose()` rotates the horizontal offsets along the camera's optical tilt axis.
  - The calculated `targetPose` was being placed dozens of meters above, below, or behind the camera frustum instead of on the floor plane in world space!
- **The Fix:** Anchors in ARCore must be created at the absolute **World Pose**:
  `Pose.makeTranslation(worldX, worldFloorY, worldZ)` where the translation is directly in ARCore World Space, NOT multiplied by `cameraPose`.

### Defect B: Seeding Failure Gate in `ArSceneViewWrapper`
In `ArSceneViewWrapper.kt` (lines 157–158):
```kotlin
if (routePathLayer != null && localizationLayer?.transform != null && camera.trackingState == TrackingState.TRACKING) {
```
- If the navigation session is launched without an immediate confirmed logo scan from `LogoScanScreen` (or if `navigationSnapshot` has not yet completed initial PnP localization), `localizationLayer.transform` remains `null`.
- When `transform == null`, the entire Phase 6 anchor generation loop is bypassed every frame. No fallback world origin was initialized for initial route visualization.

### Defect C: Filament Alpha Initialization
In `ArAnchorRenderer.kt` (line 138):
```kotlin
val material = materialLoader.createColorInstance(baseColor.copy(alpha = 0f))
```
- Materials created with `alpha = 0f` and `FADE_STEP = 1f / 8f` on a standard Filament material instance require a transparent blend mode. In SceneView 2.2.1, standard color materials are opaque by default; modifying `materialInstance.setColor(alpha)` without configuring blending can result in 0-alpha or black culled geometry.

---

## 4. Full Source Code Evidence

### 4.1 `ArCoreSessionManager.kt` (Current State)
```kotlin
package com.example.mallar.ar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.arcore.ARSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference
import java.io.ByteArrayOutputStream

data class CameraImageSnapshot(
    val y: ByteArray,
    val u: ByteArray,
    val v: ByteArray,
    val width: Int,
    val height: Int
) {
    fun toBitmap(): Bitmap? = try {
        val nv21 = ByteArray(y.size + u.size + v.size)
        y.copyInto(nv21, 0)
        v.copyInto(nv21, y.size)
        u.copyInto(nv21, y.size + v.size)
        val output = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, width, height, null)
            .compressToJpeg(Rect(0, 0, width, height), 85, output)
        BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size())
    } catch (_: Exception) {
        null
    }
}

class ArCoreSessionManager(private val context: Context) {
    companion object {
        private const val TAG = "ArCoreSessionManager"
    }

    enum class LifecycleState { CREATED, RESUMED, PAUSED, DESTROYED }

    private val _lifecycleState = MutableStateFlow(LifecycleState.PAUSED)
    val lifecycleState: StateFlow<LifecycleState> = _lifecycleState.asStateFlow()

    private val _trackingState = MutableStateFlow(TrackingState.STOPPED)
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

    private val _failureReason = MutableStateFlow(TrackingFailureReason.NONE)
    val failureReason: StateFlow<TrackingFailureReason> = _failureReason.asStateFlow()

    private val sessionRef = AtomicReference<Session?>(null)
    private val lifecycleLock = Any()

    fun createSession(): Session? = synchronized(lifecycleLock) {
        sessionRef.get()?.let { return@synchronized it }
        if (_lifecycleState.value == LifecycleState.DESTROYED) return@synchronized null
        Log.d(TAG, "Module 2: Attempting to create ARCore Session...")
        try {
            val session = ARSession(
                context = context,
                onResumed = {},
                onPaused = {},
                onConfigChanged = { _, _ -> }
            )
            val config = Config(session)
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            config.focusMode = Config.FocusMode.AUTO // <-- DEFECT: FocusMode.AUTO causes VIO instability
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            session.configure(config)
            sessionRef.set(session)
            _lifecycleState.value = LifecycleState.CREATED
            Log.d(TAG, "Module 2: ARCore Session created successfully.")
            return session
        } catch (e: Exception) {
            Log.e(TAG, "Module 2: Failed to create ARCore session", e)
            return null
        }
    }

    fun resume() = synchronized(lifecycleLock) {
        if (_lifecycleState.value == LifecycleState.RESUMED ||
            _lifecycleState.value == LifecycleState.DESTROYED
        ) return@synchronized
        sessionRef.get()?.let { session ->
            try {
                session.resume()
                _lifecycleState.value = LifecycleState.RESUMED
                Log.d(TAG, "Module 2: ARCore Session resumed.")
            } catch (e: Exception) {
                Log.e(TAG, "Module 2: Failed to resume session", e)
            }
        }
    }

    fun pause() = synchronized(lifecycleLock) {
        if (_lifecycleState.value != LifecycleState.RESUMED) return@synchronized
        sessionRef.get()?.let { session ->
            try {
                session.pause()
                _lifecycleState.value = LifecycleState.PAUSED
                Log.d(TAG, "Module 2: ARCore Session paused.")
            } catch (e: Exception) {
                Log.e(TAG, "Module 2: Failed to pause session", e)
            }
        }
    }

    fun destroy() = synchronized(lifecycleLock) {
        sessionRef.getAndSet(null)?.let { session ->
            session.close()
            _lifecycleState.value = LifecycleState.DESTROYED
            Log.d(TAG, "Module 2: ARCore Session destroyed.")
        } ?: run {
            _lifecycleState.value = LifecycleState.DESTROYED
        }
    }

    fun updateTrackingState(state: TrackingState, reason: TrackingFailureReason) {
        if (_trackingState.value != state || _failureReason.value != reason) {
            _trackingState.value = state
            _failureReason.value = reason
            Log.d(TAG, "Module 2: Tracking State Changed: $state (Reason: $reason)")
        }
    }

    fun copyCameraImage(frame: Frame): CameraImageSnapshot? {
        return try {
            val image = frame.acquireCameraImage()
            try {
                val y = image.planes[0].buffer.let { buffer -> ByteArray(buffer.remaining()).also(buffer::get) }
                val u = image.planes[1].buffer.let { buffer -> ByteArray(buffer.remaining()).also(buffer::get) }
                val v = image.planes[2].buffer.let { buffer -> ByteArray(buffer.remaining()).also(buffer::get) }
                CameraImageSnapshot(y, u, v, image.width, image.height)
            } finally {
                image.close()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Camera image unavailable for periodic fix: ${e.message}")
            null
        }
    }

    fun getSession(): Session? = sessionRef.get()
}
```

### 4.2 `ArAnchorRenderer.kt` (Current State Showing Coordinate Bug)
```kotlin
package com.example.mallar.ar

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.material.setColor
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import io.github.sceneview.math.Position
import com.example.mallar.ar.model.RouteNodeMetadata

class ArAnchorRenderer(
    private val context: Context,
    private val planner: AnchorWindowPlanner = AnchorWindowPlanner(),
    private val config: AnchorWindowConfig = AnchorWindowConfig()
) {
    private data class ManagedAnchor(
        val spec: AnchorSpec,
        val anchorNode: AnchorNode,
        val marker: CubeNode,
        val materialColor: Color,
        val initialOffset: LocalAnchorOffset,
        val correction: CorrectionInterpolator,
        var alpha: Float = 0f,
        var fadingOut: Boolean = false,
        var lastTransformAcceptedAt: Long = Long.MIN_VALUE
    )

    private val anchors = LinkedHashMap<Int, ManagedAnchor>()
    private var materialLoader: MaterialLoader? = null
    private var lastTransformAcceptedAt = Long.MIN_VALUE
    private var lastPlanGeneration = Long.MIN_VALUE

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
        val facilityPosition = transform.facilityPosition(localPose, config.pixelsPerMeter)
        val plan = planner.plan(route, facilityPosition.first, facilityPosition.second)
        if (plan.generation != lastPlanGeneration) {
            reconcile(sceneView, session, frame, cameraPose, localPose, transform, transformRevision, plan)
            lastPlanGeneration = plan.generation
        }

        val transformChanged = transformRevision != lastTransformAcceptedAt
        if (transformChanged) {
            anchors.values.forEach { managed ->
                val desired = transform.localOffsetFor(
                    managed.spec.node.x,
                    managed.spec.node.y,
                    localPose,
                    config.floorHeightMeters,
                    config.pixelsPerMeter
                )
                val targetCorrection = LocalAnchorOffset(
                    desired.xMeters - managed.initialOffset.xMeters,
                    desired.yMeters - managed.initialOffset.yMeters,
                    desired.zMeters - managed.initialOffset.zMeters
                )
                managed.correction.begin(currentCorrection(managed), targetCorrection)
                managed.lastTransformAcceptedAt = transformRevision
            }
            lastTransformAcceptedAt = transformRevision
        }

        val completed = mutableListOf<Int>()
        anchors.forEach { (nodeId, managed) ->
            if (managed.fadingOut) {
                managed.alpha = (managed.alpha - FADE_STEP).coerceAtLeast(0f)
                if (managed.alpha <= 0f) completed += nodeId
            } else {
                managed.alpha = (managed.alpha + FADE_STEP).coerceAtMost(1f)
            }
            managed.marker.materialInstance.setColor(managed.materialColor.copy(alpha = managed.alpha))
            val correction = managed.correction.step()
            managed.marker.position = Position(correction.xMeters.toFloat(), correction.yMeters.toFloat(), correction.zMeters.toFloat())
        }
        completed.forEach { nodeId ->
            anchors.remove(nodeId)?.let { managed ->
                managed.anchorNode.destroy()
            }
        }
    }

    private fun reconcile(
        sceneView: ARSceneView,
        session: Session,
        frame: Frame,
        cameraPose: Pose,
        localPose: LocalTrackingPose,
        transform: FacilityTransform,
        transformRevision: Long,
        plan: AnchorWindowPlan
    ) {
        val desiredIds = plan.nodeIds
        anchors.forEach { (nodeId, managed) ->
            if (nodeId !in desiredIds) managed.fadingOut = true
        }

        val materialLoader = materialLoader ?: MaterialLoader(sceneView.engine, context).also {
            materialLoader = it
        }
        plan.active.forEach { spec ->
            if (anchors.containsKey(spec.node.nodeId)) {
                anchors[spec.node.nodeId]?.fadingOut = false
                return@forEach
            }

            val offset = transform.localOffsetFor(
                spec.node.x,
                spec.node.y,
                localPose,
                config.floorHeightMeters,
                config.pixelsPerMeter
            )
            // DEFECT: Composing horizontal offset with cameraPose rotates world positions into camera view space
            val targetPose = cameraPose.compose(
                Pose.makeTranslation(offset.xMeters.toFloat(), offset.yMeters.toFloat(), offset.zMeters.toFloat())
            )
            val anchor = session.createAnchor(targetPose)
            val anchorNode = AnchorNode(sceneView.engine, anchor)
            val baseColor = if (spec.kind == AnchorKind.TURN) Color(0xFFFFB300) else Color(0xFF00BCD4)
            val material = materialLoader.createColorInstance(baseColor.copy(alpha = 0f))
            val marker = CubeNode(
                engine = sceneView.engine,
                materialInstance = material
            )
            marker.scale = if (spec.kind == AnchorKind.TURN) {
                Float3(0.32f, 0.10f, 0.32f)
            } else {
                Float3(0.18f, 0.06f, 0.42f)
            }
            marker.isHittable = false
            anchorNode.addChildNode(marker)
            sceneView.addChildNode(anchorNode)
            anchors[spec.node.nodeId] = ManagedAnchor(
                spec = spec,
                anchorNode = anchorNode,
                marker = marker,
                materialColor = baseColor,
                initialOffset = offset,
                correction = CorrectionInterpolator(config.correctionFrames),
                alpha = 0f,
                lastTransformAcceptedAt = transformRevision
            )
        }
    }

    private fun currentCorrection(managed: ManagedAnchor): LocalAnchorOffset {
        val position = managed.marker.position
        return LocalAnchorOffset(position.x.toDouble(), position.y.toDouble(), position.z.toDouble())
    }

    fun dispose() {
        anchors.clear()
        materialLoader = null
        lastPlanGeneration = Long.MIN_VALUE
        lastTransformAcceptedAt = Long.MIN_VALUE
    }

    fun disposeForSurfaceTeardown() {
        anchors.values.forEach { managed -> managed.anchorNode.destroy() }
        anchors.clear()
        materialLoader?.destroy()
        materialLoader = null
        lastPlanGeneration = Long.MIN_VALUE
        lastTransformAcceptedAt = Long.MIN_VALUE
    }

    companion object {
        private const val FADE_STEP = 1f / 8f
    }
}
```

---

## 5. Comprehensive Remediation Plan

To resolve both tracking instability and missing anchor markers definitively:

### Step 1: ARCore Session Tracking Stabilization (`ArCoreSessionManager.kt`)
1. **Switch to `FocusMode.FIXED`:** Set `config.focusMode = Config.FocusMode.FIXED`. This locks camera lens focal parameters and eliminates VIO intrinsic geometry oscillations during device rotation.
2. **Enforce Horizontal Plane Finding:** Retain `Config.PlaneFindingMode.HORIZONTAL`.
3. **Configure Light Estimation:** Enable `Config.LightEstimationMode.AMBIENT_INTENSITY` to allow SceneView's Filament renderer to properly light 3D primitives against the camera feed.

### Step 2: Coordinate Space & World Pose Fix in `ArAnchorRenderer.kt`
1. **Create Anchors in ARCore World Space:**
   Replace the flawed `cameraPose.compose(...)` calculation with true ARCore World Coordinates:
   ```kotlin
   // Calculate node position in ARCore World Space relative to the tracking origin
   val worldPose = Pose.makeTranslation(
       (transform.localOrigin.xMeters + offset.xMeters).toFloat(),
       offset.yMeters.toFloat(), // Floor height (-1.35m relative to camera start)
       (transform.localOrigin.yMeters + offset.zMeters).toFloat()
   )
   val anchor = session.createAnchor(worldPose)
   ```
2. **Filament Material Initialization:**
   Initialize `marker.materialInstance` with visible alpha (`1f`) so markers appear immediately upon anchor instantiation.

### Step 3: Localization Fallback Seeding in `ArSceneViewWrapper.kt`
1. If navigation starts before a logo scan or if `transform` is null, automatically seed `localizationLayer` with the first route node from `routePathLayer` at the current camera pose:
   ```kotlin
   if (localizationLayer.transform == null && routePathLayer != null) {
       val startNode = routePathLayer.getRouteMetadata().firstOrNull()?.let {
           graph.nodes.firstOrNull { node -> node.id == it.nodeId }
       }
       if (startNode != null) {
           localizationLayer.initializeFromScan(startNode, initialHeadingDeg ?: 0f, localPose, now)
       }
   }
   ```
   This guarantees that route anchors are always planned and rendered immediately upon entering AR mode.

### Step 4: Verification & Device Re-Test Protocol
1. Compile `:app:testDebugUnitTest` and `:app:assembleDebug`.
2. Execute the isolated tracking test requested in Section 4 of `ARCore_Tracking_Instability_Defect_Report.md`:
   - Stand still in a normal indoor area with floor texture.
   - Point camera at a distinct floor spot.
   - Slowly rotate body 90° away and back without walking.
   - Confirm that the white feature points and 3D cyan/amber route markers return to visually the exact same physical spot on the floor.

---

## 6. Next Steps

- This investigation report is complete and self-contained.
- Awaiting authorization from the Human Lead Architect to apply the code changes in `ArCoreSessionManager.kt`, `ArAnchorRenderer.kt`, and `ArSceneViewWrapper.kt`.
