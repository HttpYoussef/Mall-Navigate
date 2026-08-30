# Phase 4 Execution Plan v2 — ARCore Session and Minimal Rendering Validation

## Objective
Establish the foundational ARCore lifecycle and rendering plumbing within the live application. This phase validates the highest-risk integration point: hosting `ArSceneView` within the existing Compose-based navigation screen and ensuring a clean, sequential camera handoff from the pre-navigation scanning flow (`LogoScanScreen`).

## Exact Scope
- **Module 2 (ARCore Session Layer)** implementation:
    - Encapsulate ARCore `Session` creation, configuration, and lifecycle.
    - Provide tracking-quality state reporting (including failure reasons).
    - Enable camera image access capability for future consumption.
    - Implement horizontal upward plane detection.
- **Sequential Camera Handoff**: Ensure `LogoScanScreen` (CameraX) fully releases the camera before the AR navigation screen (ARCore) attempts to acquire it.
- **UI Integration**: Host an `ArSceneView` surface within `UnifiedNavigationScreen`, occupying the exact layout position previously held by the deprecated `PreviewView`.
- **Minimal Rendering (Module 7 subset)**: Render a single, world-locked static 3D primitive (e.g., a sphere) to visually verify tracking and world-locking stability.
- **Legacy Cleanup**: Remove instantiation of `CameraOverlayManager` and related legacy wiring from `UnifiedNavigationScreen`.
- **Lifecycle Management**: Handle session pause/resume during app backgrounding and navigation mode switches (Map ↔ Camera).

## Explicit Exclusions
- **Module 6 (Anchor Management)**: No path-derived anchors or sliding window logic.
- **Module 4 (Localization)**: No facility transform updates or re-fix triggers (Phase 5).
- **Module 5 (Route/Path)**: No 3D path geometry rendering.
- **Advanced Rendering**: No chevrons, turn markers, or pose-noise smoothing (Phase 7).

## Existing Components to Reuse/Modify

| Component | File Path | Action | Role |
|---|---|---|---|
| `UnifiedNavigationScreen` | `.../ui/navigation/UnifiedNavigationScreen.kt` | **Modify** | Host `ArSceneView`; remove `CameraOverlayManager` and `PreviewView`. |
| `LogoScanScreen` | `.../ui/localization/LogoScanScreen.kt` | **Reuse** | Ensure `onDispose` reliably releases CameraX. |
| `ArDirectionOverlay` | `.../ui/navigation/UnifiedNavigationScreen.kt` | **Preserve** | Keep the 2D UI arrow active alongside the 3D view. |
| `NavConfig` | `.../navigation/NavConfig.kt` | **Reuse** | Use existing constants for scaling/thresholds. |

## New Components

| Component | Package | Purpose |
|---|---|---|
| `ArCoreSessionManager` | `com.example.mallar.ar` | Implements Module 2; owns `ArSession`, tracks quality/failure reasons, and provides image access. |
| `ArSceneViewWrapper` | `com.example.mallar.ar.ui` | Compose wrapper for the `ArSceneView` surface via `AndroidView` interop. |
| `StaticTestObject` | `com.example.mallar.ar.render` | Minimal Module 7 component to render a fixed world-locked primitive. |

## Camera Lifecycle and Ownership Sequence
1. **User Localizes**: `LogoScanScreen` (CameraX) owns the camera.
2. **Scan Screen Exit**: `LogoScanScreen`'s `DisposableEffect` calls `unbindAll()`, fully releasing the camera.
3. **Navigation Screen Entry**: `UnifiedNavigationScreen` initializes `ArCoreSessionManager`.
4. **Sequential Activation**: `ArSession.resume()` is called *only after* the scan screen has been disposed and the UI has transitioned to `UnifiedNavigationScreen`.
5. **Session Management**: `ArSession.pause()` on `onPause` or when switching to Map mode; `resume()` on `onResume` if in Camera mode.

## Implementation Sequence

### 1. Preparation
- **Sign-Off Confirmation**: Confirm recorded Engineering Sign-Off for Phase 1, Phase 2, and Phase 3 (Testing Plan §12).
- **Narrow Reading Pass**: Re-read Phase 4 entries in `AR_Implementation_Roadmap.md` and `AR_Testing_and_Validation_Plan.md` §3 (Playbook §3).
- **Constraint Review**: Re-verify Sequential Camera Handoff and Superseded Overlay rules (Redesign §6.3).

### 2. Core Session Layer (Module 2)
- Implement `ArCoreSessionManager` with:
    - Thread-safe lifecycle state machine (CREATED, RESUMED, PAUSED, DESTROYED).
    - `StateFlow` for tracking quality and `TrackingFailureReason`.
    - Plane detection configuration (Horizontal Upward).
    - Safe camera image acquisition method.

### 3. UI and Legacy Removal
- Modify `UnifiedNavigationScreen.kt`:
    - Remove `CameraOverlayManager` instantiation and related relocalization wiring.
    - Remove the `AndroidView` containing `PreviewView`.
    - Implement `ArSceneViewWrapper` using `AndroidView` and `io.github.sceneview.ar.ArSceneView`.
    - Place `ArSceneViewWrapper` in the same layout slot as the removed components.

### 4. Minimal Rendering (Module 7 subset)
- Implement `StaticTestObject` renderer.
- On first `TRACKING` state, place an anchor at `(0, 0, -2)` in camera space (2m ahead) and attach the test primitive.

## Integration Risks
- **Camera Conflict**: Failure to fully release CameraX before ARCore starts. *Mitigation:* Explicit logging and lifecycle-aware sequencing.
- **SceneView Interop**: Compose `AndroidView` performance or lifecycle issues. *Mitigation:* Host `ArSceneView` as a `remember`ed component with careful cleanup.

## Rollback Strategy
- Restore `UnifiedNavigationScreen.kt` from git history to revert to legacy `PreviewView` and `CameraOverlayManager`.
- Delete `com.example.mallar.ar` Phase 4 additions.

## Validation Strategy

### Gemini Validation (Automated)
- **Build**: Successful compilation with `arsceneview` and Zero errors.
- **Logic**: Unit tests for `ArCoreSessionManager` lifecycle state transitions.
- **Log Audit**: Verify log sequence: `[LogoScan] Camera Released` -> `[Nav] AR Session Started`.
- **Constraint Confirmation**: Code audit verifying zero instantiation of `CameraOverlayManager`.

### Human Device Validation (Real Device Mandatory)
1. **Handoff (10-run Test)**: Perform 10 consecutive transitions from Scan to Nav. Confirm zero "Failed to open camera" errors (Testing Plan §3).
2. **World Locking**: Move/rotate the device. Confirm the static primitive remains visually fixed to its real-world position with negligible drift (Testing Plan §3).
3. **Lifecycle Survival**: Background/Foreground the app. Confirm AR tracking resumes and the primitive remains in place.
4. **Mode Switch**: Toggle Camera ↔ Map in the HUD. Confirm the camera feed and tracking stop/start without errors.
5. **Plane Detection**: Observe the SceneView plane renderer. Confirm horizontal planes are detected.
6. **Failure Reporting**: Cover the camera lens. Confirm the UI/Logs report tracking degradation/failure as expected.

## Completion Criteria
- [ ] `ArCoreSessionManager` implemented with lifecycle, quality reporting, and image access.
- [ ] `ArSceneView` hosted in `UnifiedNavigationScreen`; legacy overlay components removed.
- [ ] Camera handoff verified via 10-run physical device test.
- [ ] Static world-locked object rendered and visually stable.
- [ ] Project builds and passes all Phase 3 regression tests.
- [ ] **Human Device Validation performed and signed off by the Architect.**
