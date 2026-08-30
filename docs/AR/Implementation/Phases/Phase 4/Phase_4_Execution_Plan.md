# Phase 4 Execution Plan — ARCore Session and Minimal Rendering Validation

## Objective
Establish the foundational ARCore lifecycle and rendering plumbing within the live application. This phase validates the highest-risk integration point: hosting `ArSceneView` within the existing Compose-based navigation screen and ensuring a clean, sequential camera handoff from the pre-navigation scanning flow (`LogoScanScreen`).

## Exact Scope
- **Module 2 (ARCore Session Layer)** implementation: Encapsulate ARCore `Session` creation, configuration, and lifecycle management.
- **Sequential Camera Handoff**: Ensure `LogoScanScreen` (CameraX) fully releases the camera before the AR navigation screen (ARCore) attempts to acquire it.
- **UI Integration**: Host an `ArSceneView` surface within `UnifiedNavigationScreen`, replacing the legacy `PreviewView` layer when in Camera mode.
- **Minimal Rendering (Module 7 subset)**: Render a single, world-locked static 3D primitive (e.g., a sphere or cube) to visually verify tracking and world-locking stability.
- **Lifecycle Management**: Handle session pause/resume during app backgrounding and navigation mode switches (Map ↔ Camera).

## Explicit Exclusions
- **Module 6 (Anchor Management)**: No path-derived anchors or sliding window logic.
- **Module 4 (Localization)**: No facility transform updates or re-fix triggers (Phase 5).
- **Module 5 (Route/Path)**: No 3D path geometry rendering.
- **Advanced Rendering**: No chevrons, turn markers, or pose-noise smoothing (Phase 7).

## Existing Components to Reuse/Modify

| Component | File Path | Action | Role |
|---|---|---|---|
| `UnifiedNavigationScreen` | `.../ui/navigation/UnifiedNavigationScreen.kt` | **Modify** | Host the new `ArSceneView` surface. |
| `LogoScanScreen` | `.../ui/localization/LogoScanScreen.kt` | **Reuse** | Ensure `onDispose` reliably releases CameraX. |
| `ArDirectionOverlay` | `.../ui/navigation/UnifiedNavigationScreen.kt` | **Preserve** | Keep the 2D UI arrow active alongside the 3D view. |
| `NavConfig` | `.../navigation/NavConfig.kt` | **Reuse** | Use existing constants for scaling/thresholds. |

## New Components

| Component | Package | Purpose |
|---|---|---|
| `ArCoreSessionManager` | `com.example.mallar.ar` | Implements Module 2; owns the `ArSession` and tracks tracking quality. |
| `ArSceneViewWrapper` | `com.example.mallar.ar.ui` | Compose wrapper for the `ArSceneView` (Filament-based) surface. |
| `StaticTestObject` | `com.example.mallar.ar.render` | Trivial Module 7 subset to render a fixed world-locked primitive. |

## Camera Lifecycle and Ownership Sequence
1. **User Localizes**: `LogoScanScreen` acquires camera via `ProcessCameraProvider` (CameraX).
2. **User Confirms**: Navigation to `UnifiedNavigationScreen` triggers.
3. **Scan Screen Exit**: `LogoScanScreen`'s `DisposableEffect` calls `unbindAll()`, releasing the camera resource.
4. **Navigation Screen Entry**: `UnifiedNavigationScreen` initializes `ArCoreSessionManager`.
5. **AR Acquisition**: `ArSession.resume()` is called *only after* the previous screen is disposed.
6. **Backgrounding**: `ArSession.pause()` on `onPause`, `resume()` on `onResume`.

## Integration Flow
- `UnifiedNavigationViewModel` initializes `ArCoreSessionManager`.
- The screen UI observes the `NavMode`. When `CAMERA` mode is active, the `ArSceneViewWrapper` is composed.
- On first tracking frame, `StaticTestObject` is placed at a fixed position relative to the camera start pose (e.g., 2m ahead).

## Implementation Sequence
1. **Core Lifecycle**: Implement `ArCoreSessionManager` with basic lifecycle methods (create, resume, pause, destroy).
2. **Compose Surface**: Implement `ArSceneViewWrapper` using `AndroidView` interop with `io.github.sceneview.ar.ArSceneView`.
3. **Screen Integration**: Update `UnifiedNavigationScreen` to swap the standard `PreviewView` with `ArSceneViewWrapper` when in Camera mode.
4. **Handoff Hardening**: Add explicit logging to both `LogoScanScreen` and `ArCoreSessionManager` to verify non-concurrent camera access.
5. **Static Primitive**: Implement a trivial renderer that places a 3D sphere in the scene upon reaching `TRACKING` state.

## Integration Risks
- **Resource Contention**: CameraX and ARCore might conflict if the release/acquire timing overlaps. *Mitigation:* Explicit lifecycle-aware guards and logging.
- **Initialization Latency**: `ArSceneView` setup might cause a frame drop. *Mitigation:* Lazy-initialize the surface only when switching to Camera mode.
- **Device Incompatibility**: ARCore might not be available on all devices. *Mitigation:* Use the already configured manifest "optional" flag and add a runtime availability check.

## Rollback Strategy
- Revert changes to `UnifiedNavigationScreen.kt` to restore legacy `PreviewView`.
- Delete the `com.example.mallar.ar` implementation files for Phase 4.

## Validation Strategy

### Gemini Validation (Automated)
- **Build Verification**: Ensure project compiles with `arsceneview` and new classes.
- **Logic Tests**: Unit test `ArCoreSessionManager` state transitions (e.g., correct `ArSession` state after `resume()`/`pause()`).
- **Log Audit**: Verify that "Camera Released" from `LogoScan` always precedes "AR Session Resumed" in simulated transitions.
- **Static Analysis**: Confirm no use of deprecated `overlay/` components in the new path.

### Human Device Validation (Real Device Required)
1. **Handoff Verification**: Transition from Scan to Nav. Confirm the camera feed initializes in Nav without "Failed to open camera" errors.
2. **Session Lifecycle**: Background the app while in AR mode, then resume. Confirm tracking recovers.
3. **World Locking**: Place the static sphere. Walk around it, rotate the device. Confirm the sphere remains fixed to its physical location, not the screen.
4. **Mode Switching**: Toggle between Map and Camera in the HUD. Confirm the camera feed stops/starts cleanly.
5. **Visual Evidence**: Record a short video showing the sphere world-locked in the mall environment.

## Completion Criteria
- [ ] `ArCoreSessionManager` correctly handles `ArSession` lifecycle.
- [ ] `ArSceneView` is hosted in `UnifiedNavigationScreen` during Camera mode.
- [ ] Camera handoff from `LogoScanScreen` is error-free.
- [ ] A static 3D primitive is rendered and world-locked.
- [ ] Project builds and runs on a physical AR-capable device.
- [ ] **Human Device Validation performed and signed off by the Architect.**
