# AR Implementation Understanding — MallAR AR Subsystem

## Executive Summary

The MallAR AR subsystem is a hybrid navigation engine designed to provide "Live View" style indoor guidance. It leverages ARCore's native Visual-Inertial Odometry (VIO) for continuous, high-frequency local tracking and a landmark-recognition pipeline for occasional, event-driven global corrections. This two-tier approach ensures the guidance (arrows/chevrons) remains world-locked and physically attached to the floor without requiring external infrastructure or backend dependencies. The architecture is structured into eight distinct layers with strict ownership and a single-writer model, integrated into the existing MallAR application via a set of well-defined adapters and extensions.

---

## Documentation Understanding

The subsystem is organized into an eight-layer architecture, each with specific, non-overlapping responsibilities:

1.  **Sensor Layer**: Responsible for providing raw hardware streams (Camera, IMU, etc.). In our implementation, this layer is "virtual" as we reuse existing sensor listener ownership in the application.
2.  **ARCore Session Layer**: Owns the ARCore session, providing VIO pose, plane detection, and anchor primitives. It is the sole authority for device pose in AR space.
3.  **Sensor Fusion / PDR Layer**: Wraps the existing `NavigationSessionManager` to produce a relative-displacement estimate used as a corroborating signal and a tracking-loss fallback.
4.  **Localization Layer**: The sole owner of the "Facility Transform" (the mapping between ARCore space and the mall's coordinate system). It manages initial fix acquisition and periodic re-fixes using a Fix Validation Gate.
5.  **Route/Path Layer**: Manages the navigation route. It takes a one-time snapshot of the navigation state via an adapter to maintain architectural purity.
6.  **Anchor Management Layer**: Translates the route into ARCore-space anchors, handling their lifecycle (sliding window), rebase applications, and smooth correction interpolation.
7.  **Rendering Layer**: Responsible for drawing the world-locked chevrons and markers using `ArSceneView` (SceneView/Filament), superseding the legacy overlay system.
8.  **Drift & Recovery Layer**: A supervisory layer that classifies divergence as "drift" (smoothly corrected) or "deviation" (re-routes) and manages recovery states (grace windows for interruptions).

The system operates on two cycles: a **Fast Loop** (frame-by-frame rendering driven by ARCore) and a **Slow Loop** (event-driven re-fixes and drift classification).

---

## Existing Project Understanding

The current MallAR project is a mature Android application structured as follows:

*   **Package Structure**: Organized by feature/layer (`ui`, `data`, `navigation`, `ml`, `overlay`).
*   **Navigation Subsystem**: Centralized in `com.example.mallar.navigation`. It includes `NavigationSessionManager`, which orchestrates `IndoorPositionTracker`, `SensorFusionManager`, `StepTracker`, and `DriftMonitor`.
*   **Global State**: `NavigationState` (an `object` in `LogoScanScreen.kt`) acts as a global mutable singleton holding the active path, destination, and starting point.
*   **Recognition Subsystem**: Located in `com.example.mallar.ml`. The `LocalizationEngine` produces `LocalizationResult` objects based on landmark detection.
*   **Legacy AR Implementation**: Found in `com.example.mallar.overlay`. It uses CameraX with a custom `Canvas` overlay for rendering, which is now deprecated.
*   **Dependencies**: Uses ARCore (optional), CameraX, and SceneView/Filament (already in build config but not yet integrated into the main navigation flow).
*   **Routing**: Uses an on-device A* pathfinding engine integrated into the `MallGraphRepository`.

---

## Architecture vs Current Project

The approved architecture identifies and resolves several key gaps in the current project:

### Existing Reusable Components
*   **Pathfinding Engine**: The A* implementation and graph repository are fully reusable.
*   **Sensor Fusion Infrastructure**: `NavigationSessionManager` and its IMU-processing collaborators are reused via Layer 3.
*   **Landmark Recognition**: The `LogoDetector` and `LocalizationEngine` remain the core of global corrections.
*   **Pre-navigation Flow**: `LogoScanScreen` is retained for initial-fix acquisition.

### Components Requiring Modification
*   **`LocalizationResult`**: Requires an additive `landmarkCount` field to support the provisional/confirmed fix-confidence tiering.
*   **Manifest**: The `android.hardware.camera.ar` requirement must be changed from `required="true"` to `required="false"` to resolve the conflict with ARCore's optionality.

### Components Requiring Replacement
*   **Legacy Overlay Rendering**: The entire `overlay/` package (ProjectionEngine, NavigationEngine, etc.) is replaced by the new Rendering Layer (Layer 7) using `ArSceneView`.
*   **Drift/Relocalization Decision Logic**: While the measurement from `DriftMonitor` is reused, its decision-making (the callback trigger) is replaced by the Drift & Recovery Layer (Layer 8).

### Components to be Removed
*   **`CameraOverlayView` and `CameraOverlayManager`**: These will be deleted in favor of the `ArSceneView` integration.

---

## Major Modules

### 1. NavigationSessionInputAdapter (Boundary)
*   **Purpose**: Bridges the global `NavigationState` to the AR subsystem.
*   **Responsibilities**: Performs a single snapshot read of `NavigationState` at session start.
*   **Dependencies**: `NavigationState`.
*   **Interactions**: Produces an immutable snapshot for the Route/Path Layer.

### 2. Localization Layer (Layer 4)
*   **Purpose**: Maintains the Facility Transform.
*   **Responsibilities**: Proximity-gated re-fix triggers, Fix Validation Gate, confidence tiering.
*   **Dependencies**: `LocalizationEngine`, ARCore Session.
*   **Interactions**: Receives camera frames from ARCore; writes validated transforms to Anchor Management.

### 3. Anchor Management Layer (Layer 6)
*   **Purpose**: Maps route nodes to world-locked anchors.
*   **Responsibilities**: Sliding window lifecycle, smooth correction interpolation, origin rebasing.
*   **Dependencies**: ARCore Session, Route/Path Layer.
*   **Interactions**: Consumes poses from ARCore; provides anchor transforms to the Rendering Layer.

---

## Runtime Flow

1.  **Launch & Init**: User selects a destination. `LogoScanScreen` (CameraX) acquires the initial fix.
2.  **Transition to AR**: Navigation starts. `NavigationSessionInputAdapter` takes the snapshot. CameraX is released; ARCore session starts.
3.  **Steady State (Fast Loop)**: ARCore updates pose → Layer 6 updates anchor transforms → Layer 7 renders chevrons in `ArSceneView`.
4.  **Steady State (Slow Loop)**: User approaches a landmark (proximity gate) → Layer 4 requests a frame from ARCore → Recognition runs → Fix Validation Gate approves → Layer 6 interpolates the correction.
5.  **Supervision**: Layer 8 monitors `DriftMonitor` (divergence). If deviation is detected, Layer 5 requests a re-route from the A* engine; anchors are rebuilt.
6.  **Arrival**: Proximity to destination node triggers the "Arrived" state; session terminates.

---

## Engineering Constraints

1.  **Single Writer Principle**: Only one layer may own and write to a specific state (e.g., Layer 4 owns the Facility Transform).
2.  **No New Sensor Listeners**: Subsystem must consume `StateFlow<NavSessionState>` from the existing manager.
3.  **One-Way Data Flow**: Data flows from sensors/ARCore toward rendering. Supervisors (Layer 8) issue instructions, they don't mutate state.
4.  **No Global State Dependencies**: Except for the `NavigationSessionInputAdapter`, no module may read `NavigationState`.
5.  **Sequential Camera Access**: ARCore and CameraX must never run concurrently.
6.  **No Network Usage**: Implementation must remain entirely offline.

---

## Testing Understanding

Validation is phase-based and evidence-driven:
*   **Phase-Specific Validation**: Each of the 10 roadmap phases has a specific matrix in the Test Plan (e.g., proving the single-read guarantee in Phase 2).
*   **Regression Testing**: Every phase requires re-verifying previous dependencies (e.g., Phase 6 must re-verify Phase 4's world-locking).
*   **Failure Scenarios**: Validation includes "negative" tests like induced sensor glitches, lighting degradation, and deliberate user deviation.
*   **Evidence Standard**: Success is defined by recorded logs, instrumented counts, or video evidence, not subjective appraisal.

---

## Potential Risks

1.  **`NavigationState` Mutation**: While the adapter snapshots the state, external changes to the global `NavigationState` (e.g., by the Voice assistant) could cause desync if not handled by the adapter's lifecycle.
2.  **Camera Handoff Latency**: The sequential release of CameraX and initialization of ARCore may introduce a visual "hiccup" or delay in starting navigation.
3.  **Reflective Surfaces**: Mall environments often have reflective floors/glass, which could destabilize ARCore plane detection. The "fixed-height offset" fallback in Layer 7 is critical.
4.  **Landmark Density**: Sparse signage in certain corridors may lead to "Aging" or "Degraded" tracking states.

---

## Documentation Ambiguities

No documentation ambiguity detected.

---

## Overall Readiness

1.  **Do you completely understand the architecture?** Yes.
2.  **Do you completely understand the engineering specification?** Yes.
3.  **Do you completely understand the roadmap?** Yes.
4.  **Do you completely understand the testing strategy?** Yes.
5.  **Do you completely understand the implementation playbook?** Yes.
6.  **Do you understand how the approved architecture integrates into the current Android project?** Yes.
7.  **Is any architectural information missing that would prevent implementation?** No.
