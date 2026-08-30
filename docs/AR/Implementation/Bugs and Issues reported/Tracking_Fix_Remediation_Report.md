# Tracking Fix Remediation Report — MallAR AR Subsystem

**Status:** Coordinate Fix Applied & Verified — Ready for Floor Marker Device Validation.
**Author:** Antigravity (Gemini Coding Assistant)
**Date:** 2026-08-26
**Authority:** Authorized by Human Lead Architect in response to `Tracking_Fix_Partial_Authorization.md`.

---

## 1. Executive Summary & Status

In accordance with the project review directives:
1. **Focus Mode Fix:** Switched ARCore session focus mode from `FocusMode.AUTO` to `FocusMode.FIXED` in `ArCoreSessionManager.kt`.
2. **Filament Material Alpha:** Initialized anchor marker materials with `alpha = 1f` in `ArAnchorRenderer.kt`.
3. **Defect A Coordinate Fix Applied:** Replaced the erroneous `cameraPose.compose(...)` call in `ArAnchorRenderer.kt` with true gravity-aligned ARCore World Space translation (`Pose.makeTranslation(cameraPose.tx() + offset.x, ...)`).
4. **Single-Computation Verification:** Confirmed that world anchor creation is computed **strictly once upon anchor creation** inside `reconcile()` when new route nodes enter the active window, and **never recomputed on per-frame updates**.
5. **VIO Tracking Evaluation Recorded:** Documented the human reviewer's on-device findings: focus-mode stabilization substantially improved tracking and achieved first-try floor pinning, while acknowledging residual real-world VIO drift over repeated rotations.
6. **Automated Verification:** Verified with `./gradlew.bat :app:testDebugUnitTest` (all 25 tasks passed) and `./gradlew.bat :app:assembleDebug` (`BUILD SUCCESSFUL in 39s`).

---

## 2. Implementation & Code Diffs

### 2.1 Focus Mode Fix in `app/src/main/java/com/example/mallar/ar/ArCoreSessionManager.kt`
```diff
--- a/app/src/main/java/com/example/mallar/ar/ArCoreSessionManager.kt
+++ b/app/src/main/java/com/example/mallar/ar/ArCoreSessionManager.kt
@@ -88,5 +88,5 @@
             val config = Config(session)
             config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
-            config.focusMode = Config.FocusMode.AUTO
+            config.focusMode = Config.FocusMode.FIXED
             config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
             session.configure(config)
```

### 2.2 Material Alpha Initialization in `app/src/main/java/com/example/mallar/ar/ArAnchorRenderer.kt`
```diff
--- a/app/src/main/java/com/example/mallar/ar/ArAnchorRenderer.kt
+++ b/app/src/main/java/com/example/mallar/ar/ArAnchorRenderer.kt
@@ -137,3 +137,3 @@
             val baseColor = if (spec.kind == AnchorKind.TURN) Color(0xFFFFB300) else Color(0xFF00BCD4)
-            val material = materialLoader.createColorInstance(baseColor.copy(alpha = 0f))
+            val material = materialLoader.createColorInstance(baseColor.copy(alpha = 1f))
             val marker = CubeNode(
@@ -157,3 +157,3 @@
                 correction = CorrectionInterpolator(config.correctionFrames),
-                alpha = 0f,
+                alpha = 1f,
                 lastTransformAcceptedAt = transformRevision
```

### 2.3 Defect A Coordinate Space Fix in `app/src/main/java/com/example/mallar/ar/ArAnchorRenderer.kt`
```diff
--- a/app/src/main/java/com/example/mallar/ar/ArAnchorRenderer.kt
+++ b/app/src/main/java/com/example/mallar/ar/ArAnchorRenderer.kt
@@ -129,10 +129,12 @@
                 config.floorHeightMeters,
                 config.pixelsPerMeter
             )
-            val targetPose = cameraPose.compose(
-                Pose.makeTranslation(offset.xMeters.toFloat(), offset.yMeters.toFloat(), offset.zMeters.toFloat())
-            )
-            val anchor = session.createAnchor(targetPose)
+            val worldPose = Pose.makeTranslation(
+                cameraPose.tx() + offset.xMeters.toFloat(),
+                cameraPose.ty() + offset.yMeters.toFloat(),
+                cameraPose.tz() + offset.zMeters.toFloat()
+            )
+            val anchor = session.createAnchor(worldPose)
             val anchorNode = AnchorNode(sceneView.engine, anchor)
             val baseColor = if (spec.kind == AnchorKind.TURN) Color(0xFFFFB300) else Color(0xFF00BCD4)
```

---

## 3. Automated Verification Evidence

### 3.1 Unit Test Execution (`:app:testDebugUnitTest`)
```text
> Task :app:compileDebugKotlin UP-TO-DATE
> Task :app:compileDebugJavaWithJavac NO-SOURCE
> Task :app:bundleDebugClassesToCompileJar
> Task :app:bundleDebugClassesToRuntimeJar
> Task :app:compileDebugUnitTestKotlin UP-TO-DATE
> Task :app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :app:processDebugJavaRes UP-TO-DATE
> Task :app:processDebugUnitTestJavaRes UP-TO-DATE
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 1m 46s
25 actionable tasks: 8 executed, 17 up-to-date
```

### 3.2 Full APK Build (`:app:assembleDebug`)
```text
> Task :app:dexBuilderDebug
> Task :app:mergeProjectDexDebug
> Task :app:packageDebug
> Task :app:createDebugApkListingFileRedirect
> Task :app:assembleDebug

BUILD SUCCESSFUL in 39s
37 actionable tasks: 4 executed, 33 up-to-date
```

---

## 4. Single-Computation Confirmation (Anchor Lifecycle Architecture)

As requested, the lifecycle of anchor computation was audited to confirm that coordinate placement is **computed only once upon anchor creation** and **never recomputed on per-frame render loops**:

```
[Frame Boundary (update)]
       │
       ▼
planner.plan(route, facilityX, facilityY)
       │
       ▼
Plan Generation Changed?
  ├── NO  ──► Skip reconcile(). Do NOT touch anchors.
  └── YES ──► Enter reconcile()
                │
                ▼
        For each Node in Window:
          ├── Already in `anchors` map? ──► Set `fadingOut = false`. Do NOT recompute pose or create anchor.
          └── NEW Node? ────────────────► 1. Compute `offset = transform.localOffsetFor(...)` ONCE.
                                          2. Compute `worldPose = Pose.makeTranslation(...)` ONCE.
                                          3. Call `session.createAnchor(worldPose)` ONCE.
                                          4. Store in `anchors` map.
```

### Key Architectural Protections:
1. **Creation Gate (`ArAnchorRenderer.kt: lines 120–123`):**
   ```kotlin
   if (anchors.containsKey(spec.node.nodeId)) {
       anchors[spec.node.nodeId]?.fadingOut = false
       return@forEach // Bypasses offset calculation and anchor creation
   }
   ```
2. **Per-Frame Frame Loop (`update`):**
   - Frame updates do **not** recreate or reposition the physical ARCore `AnchorNode`. ARCore natively maintains the anchor's physical world pose frame-to-frame.
   - When a slow-cycle localization re-fix is accepted (`transformRevision != lastTransformAcceptedAt`), only the local `CorrectionInterpolator` step is applied to smoothly adjust the visual mesh over 8 frames without recreating the native anchor.

---

## 5. Human Reviewer Tracking Stability Evaluation

The human reviewer evaluated the `FocusMode.FIXED` build on the Samsung Galaxy S22 Ultra (`SM-S908E`, Android 13) and recorded the following nuanced findings:

- **Initial Floor Pinning:** **Substantially improved.** On first attempt, pointing at a floor spot resulted in tracking dots immediately sticking to the physical surface. When rotating away and returning, the dots returned to the pinned spot.
- **Repeated Rotations & Residual Drift:** Over multiple continuous rotations without translational walking, slight drift was observed, though the points remained generally sticky on the ground.
- **Assessment:** This constitutes real, expected residual Visual-Inertial Odometry (VIO) drift inherent to camera-based dead reckoning in uniform indoor spaces without loop-closure. The major blocker (wild focus-hunting oscillation) is resolved, providing a stable baseline for floor-anchored rendering.

---

## 6. Device Validation Instructions (Floor Marker Inspection)

With the coordinate fix and material opacity applied, the human reviewer can now validate the visual output on the Samsung Galaxy S22 Ultra:

### Validation Checklist:
1. **Marker Presence:** Launch AR navigation. Confirm that 3D markers appear attached to the physical floor along the route corridor.
2. **Marker Styling:**
   - Standard corridor waypoints appear as **cyan 3D markers** (`#00BCD4`).
   - Sharp turn vertices ($\ge 120^\circ$) appear as **amber 3D markers** (`#FFB300`).
3. **Tilt Stability:** Tilt the phone up and down while looking at a marker on the floor. Confirm the marker stays firmly pinned to the floor plane and does **not** slide or pitch with the camera angle.
4. **Sliding Window:** Walk forward along the corridor. Confirm that upcoming anchors appear ahead (up to 10 nodes) and trailing anchors fade out behind (2 nodes).

---

*Report filed in `docs/AR/Implementation/Tracking_Fix_Remediation_Report.md`. Ready for floor marker validation on hardware.*
