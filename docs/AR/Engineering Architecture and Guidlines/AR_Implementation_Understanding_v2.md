# AR Implementation Understanding v2 — MallAR AR Subsystem

## Executive Summary

The MallAR AR subsystem is a hybrid indoor navigation engine that provides high-fidelity, world-locked guidance by combining ARCore's Visual-Inertial Odometry (VIO) with event-driven landmark recognition. It is architected as an eight-layer system that separates concerns between raw sensor consumption, tracking, localization, route management, and rendering. The system is designed to be fully offline, infrastructure-free, and integrated seamlessly into the existing MallAR Android application by reusing its pathfinding, recognition, and session-management components while strictly isolating the new AR logic behind a single-read boundary adapter.

---

## Documentation Understanding

The subsystem is defined by a rigorous eight-layer architecture that ensures single-writer ownership and predictable state transitions.

### 1. Sensor Layer (Existing)
*   **Purpose**: Supplies raw hardware signals (Camera, IMU) to the system.
*   **Responsibilities**: Pass-through of raw streams; no processing or interpretation.
*   **Ownership**: Sensor listener registration is held exclusively by the existing `NavigationSessionManager`'s collaborators (`SensorFusionManager`, `StepTracker`). This subsystem registers zero new listeners.
*   **Roadmap Phase**: Phase 0 (Baseline confirmation).

### 2. ARCore Session Layer (Module 2)
*   **Purpose**: Sole authority for local tracking and the physical-world reference.
*   **Responsibilities**: Frame-by-frame VIO pose estimation, plane detection, and anchor lifecycle primitives. Provides camera image access for the Localization Layer.
*   **Runtime Behaviour**: Fast-cycle driver; updates pose every camera frame.
*   **Failure Handling**: Reports tracking-quality degradation to the Drift & Recovery Layer.
*   **Roadmap Phase**: Phase 4.

### 3. Sensor Fusion / PDR Layer (Module 3)
*   **Purpose**: Provides a corroborating motion estimate and tracking-loss fallback.
*   **Responsibilities**: Derives relative-displacement and staleness signals from the existing `NavSessionState` stream.
*   **Integration Points**: Consumes `StateFlow<NavSessionState>` from the existing application.
*   **Engineering Constraint**: Never authoritative over tracked pose; strictly corroborating.
*   **Roadmap Phase**: Phase 3.

### 4. Localization Layer (Module 4)
*   **Purpose**: Maps ARCore local tracking to facility (graph) coordinates.
*   **Responsibilities**: Periodic re-fix triggers (proximity-gated and throttled), fix-confidence tiering (landmark-count based), and the **Fix Validation Gate**. It is the **sole writer** of the Facility Transform.
*   **Corrected Understanding (Finding #2)**: Previously I stated this module "manages initial fix acquisition." This was incorrect. The **Localization Layer only consumes the initial fix** produced by the existing `LogoScanScreen` flow. It is responsible for *maintaining* and *re-grounding* the transform via periodic re-fixes during navigation, not for the first fix itself.
*   **Validation Strategy**: Verified by Phase 5 validation criteria (rejection of implausible fixes, tiering correctness).
*   **Roadmap Phase**: Phase 5.

### 5. Route/Path Layer (Module 5)
*   **Purpose**: Represent the planned navigation path in facility coordinates.
*   **Responsibilities**: Holds the route polyline and node metadata. Requests recalculations when deviation is detected.
*   **Integration Points**: Consumes a one-time snapshot from the `NavigationSessionInputAdapter`.
*   **Roadmap Phase**: Phase 2.

### 6. Anchor Management Layer (Module 6)
*   **Purpose**: Bridge between facility-coordinate route nodes and ARCore-space anchors.
*   **Responsibilities**: Maintains a sliding window (8-12 ahead, 2-3 trailing). Applies origin rebasing and **smooth (interpolated) correction**.
*   **Runtime Behaviour**: In the event of a fix (Slow Loop), it interpolates the new transform over multiple frames to prevent visual jumps.
*   **Roadmap Phase**: Phase 6.

### 7. Rendering Layer (Module 7)
*   **Purpose**: Renders the world-locked chevrons and markers.
*   **Responsibilities**: Draws geometry in an `ArSceneView`. Implements **render-level pose-noise smoothing** and floor-plane-confidence fallback.
*   **Corrected Understanding (Finding #4)**: I previously omitted the distinction between render-level smoothing and anchor correction-smoothing. **Render-level pose-noise smoothing** is a Module 7 responsibility that damps high-frequency ARCore noise per-frame. It is distinct from Module 6's **correction smoothing**, which interpolates occasional large positional updates from the Localization Layer.
*   **Roadmap Phase**: Phase 7.

### 8. Drift & Recovery Layer (Module 8)
*   **Purpose**: Supervisory logic for tracking quality and navigation correctness.
*   **Responsibilities**: Classifies divergence as **Drift** or **Deviation**. Manages the interruption grace window and environmental recovery guidance.
*   **Corrected Understanding (Finding #3)**: I previously only described the "Deviation" branch.
    *   **Drift branch**: Divergence within ~2-3m lateral bound. Instruction: **Smoothed correction** (Layer 6). No route rebuild.
    *   **Deviation branch**: Crossing graph edges or exceeding bounds. Instruction: **Route rebuild** (Layer 5) and anchor window refresh.
*   **Transition Mode (Finding #1)**: This module is responsible for triggering **Transition Mode** when approaching a floor-change node.
    *   **Purpose**: Handles physical floor changes (stairs, elevators).
    *   **Behaviour**: Pauses anchor rendering; simplified directional rendering is used. ARCore session is kept alive throughout the transition.
    *   **Exit Condition**: Mandatory validated re-fix succeeds on the new floor.
*   **Roadmap Phase**: Phase 8.

---

## Existing Project Understanding & Integration

### Integration Boundary Components
*   **NavigationSessionInputAdapter (Module 9)**: Performs a single read of the global `NavigationState` singleton at session start to satisfy the anti-global-state principle. (Phase 2).
*   **LocalizationResult Extension**: Adds `landmarkCount` to existing data structures to enable confidence tiering. (Phase 1).
*   **Manifest Policy**: Corrects the `required="true"` camera AR feature to `required="false"` while keeping ARCore optional. (Phase 0).

### Overlay Removal Strategy (Finding #5)
The existing `overlay/` package is fully superseded by the `ArSceneView` integration. However, the **staged deletion timing** is critical:
*   **Phase 4**: `ArSceneView` is hosted in the existing UI slot, but the legacy source files are **retained** as a rollback reference.
*   **Phase 9**: Full system validation is complete, and only then is the deprecated `overlay/` package **removed entirely** from the codebase.

---

## Engineering Constraints & Operating Rules

### Implementation Playbook Engagement (Finding #6)
Implementation must strictly follow the standards defined in the Playbook:

*   **Engineering Workflow**: Preparation → Implementation → Validation → Review → Commit. Sign-off must be recorded before proceeding to the next phase.
*   **Reading Order**: I have re-validated my understanding by reading the documents in the specified 1-6 order. I acknowledge that a second, phase-specific reading pass is required before starting each Roadmap phase.
*   **Git Workflow**: One branch per roadmap phase. Atomic commits within the branch. No commit may span multiple phases.
*   **AI Agent Operating Rules**:
    *   **Allowed**: Internal code organization, selecting specific values within specified ranges (e.g., 4s for a 3-5s throttle).
    *   **Forbidden**: Architectural decisions, changing state machines, resolving specification gaps by invention, editing frozen documents.
    *   **Stop Conditions**: Contradictions, missing architecture for a scenario, or failure to meet validation criteria.
*   **Clarification vs. Escalation**:
    *   **Clarification**: Human interpretation of existing text when application to a situation isn't obvious.
    *   **Escalation**: Stopping work when documents are silent, contradictory, or incorrect. Requires a new architecture version.
*   **Definition of Done**: A phase is only "done" when the project compiles, validation (and regression) passes, and independent review is signed off. System "done" requires Final System Acceptance (all 5 readiness categories satisfied).

---

## Major Subsystem Traceability

| Subsystem | Purpose | Key Responsibility | Runtime Behaviour | Failure Handling | Validation | Roadmap Phase |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **ARCore Session** | World Tracking | VIO Pose & Planes | Updates every frame (Fast Loop) | Reports degradation to Supervisor | Frame-rate & world-locking checks | Phase 4 |
| **Localization** | Facility Mapping | Transform Ownership | Periodic gated re-fixes (Slow Loop) | Discards implausible fixes | Fix Validation Gate tests | Phase 5 |
| **Anchor Mgmt** | Path Visualization | Anchor Lifecycle | Smooth interpolation of fixes | Executes re-routes | Window bound & smoothing checks | Phase 6 |
| **Rendering** | UI Presentation | ArSceneView output | Pose-noise smoothing filter | Floor-plane fallback (fixed height) | Variance reduction measurement | Phase 7 |
| **Drift & Recovery** | Supervision | Classification | Manages transitions & recovery | Grace window & manual rescan | State machine coverage tests | Phase 8 |

---

## Resolution of Readiness Findings

| Original Finding | Correct Understanding | Evidence from Documentation | Previous Incompleteness | Current Understanding |
| :--- | :--- | :--- | :--- | :--- |
| **1. Transition Mode absent** | A specific state for floor changes involving simplified rendering and mandatory re-fix. | `Spec §7`, `Redesign §8`, `Spec §11`. | Entirely omitted from the first summary. | Triggered by floor-change nodes; keeps ARCore alive but pauses anchors; requires re-fix on exit. |
| **2. Initial Fix Ownership** | Localization Layer consumes, but scan flow performs initial fix. | `Spec §3 (Module 4)`, `Redesign §9`. | Attributed "acquisition" to Layer 4 incorrectly. | `LogoScanScreen` owns the acquisition; Layer 4 only owns re-fixes during navigation. |
| **3. Drift/Deviation Branch** | Drift involves smoothed corrections; Deviation involves re-routes. | `Redesign §12`, `Spec §11`. | Described only the deviation/re-route response. | Drift is corrected smoothly by Layer 6 interpolation; Deviation triggers Layer 5 re-route. |
| **4. Pose-Noise Smoothing** | Separate mechanism from correction smoothing. | `Spec §3 (Module 7)`, `Spec §13`. | Conflated or omitted the render-level filter. | Module 7 damps frame-to-frame ARCore noise; Module 6 damps occasional fix jumps. |
| **5. Overlay Deletion Timing** | Removal happens in Phase 9, not Phase 4. | `Roadmap Phase 4`, `Roadmap Phase 9`. | Implied immediate removal in favor of SceneView. | Source code preserved until Phase 9 for rollback safety; superseded in UI in Phase 4. |
| **6. Playbook Engagement** | Strict workflow and AI operating rules must be demonstrated. | `Playbook §2-§12`. | Omitted process, Git, and AI operating constraints. | Mastered rules for phase boundaries, branch management, and stop-conditions for AI. |

---

## Final Self-Assessment

1. **Is there any architectural concept you do not completely understand?** No.
2. **Is there any engineering specification that remains unclear?** No.
3. **Is there any roadmap phase whose purpose you cannot explain?** No.
4. **Is there any validation requirement you cannot justify?** No.
5. **Is there any implementation rule you cannot explain?** No.
6. **Is there any point where you would need to invent an architectural decision?** No.

I have revalidated my understanding against the complete documentation package. I do not require any additional architectural clarification and am ready to begin implementation according to the approved roadmap.
