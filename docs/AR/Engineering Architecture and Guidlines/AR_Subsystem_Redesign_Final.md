# AR Subsystem Redesign — Final Architecture

**Status:** Final. This document supersedes and replaces every prior AR subsystem design document (including the stress-tested v2 architecture) in full. It is the single authoritative specification for implementation.
**Scope:** Architecture and system design only. No code, no pseudocode, no Kotlin/Android implementation detail.
**Self-containment:** This document assumes no prior document has been read. Every architectural decision required to implement the subsystem — including its integration boundary with the existing live application — is stated here directly.
**Provenance:** The internal eight-layer architecture and runtime behavior in this document were approved without required changes by an independent engineering stress test. A subsequent architecture approval review, grounded in direct repository verification, approved the internal design ("Approved with Required Changes") but identified five mandatory integration-boundary findings that had to be resolved before implementation could be authorized. This document resolves all five. Section 23 provides full traceability from each finding to its resolution.

---

## 1. Executive Summary

MallAR's AR navigation subsystem renders floor-attached, world-locked directional guidance — arrows and turn markers that stay visually glued to the physical floor as the user walks — using a two-tier hybrid: ARCore's native visual-inertial odometry (VIO), plane detection, and anchors provide continuous, every-frame local tracking and rendering; the mall's existing store-signage recognition capability provides occasional, event-driven global correction. No custom SLAM, no physical infrastructure, and no backend/network dependency is required.

The internal architecture — an eight-layer decomposition with single-writer state ownership, a Fix Validation Gate against bad localization corrections, a proximity-gated recognition trigger, and a complete runtime state machine — was independently reviewed and found sound, with the assessment that it was ready to hand to an implementation team **as a specification for a greenfield module**. The subsequent, more rigorous approval review went further: it verified the architecture against the actual live repository and found that the document's assumptions about the *integration boundary* did not hold. The existing application has a global mutable singleton (`NavigationState`) that the architecture's own principles forbid, yet that singleton is a hard dependency of the exact localization pipeline the architecture requires reusing. The existing live rendering flow uses a different technology (CameraX + custom Canvas overlay) than the one the architecture assumed was already integrated (SceneView/Filament). The existing `NavigationSessionManager` already owns sensor fusion and drift-monitoring responsibilities that overlap two of the architecture's proposed layers. And the Android manifest contains an unresolved contradiction that determines which devices can even install the app.

This document resolves all five findings as explicit architectural decisions, integrated directly into the relevant sections below, so that an implementing engineer is not left to invent any of them. The internal architecture is unchanged from the version that was already reviewed and found sound — this revision is exclusively about closing the boundary between that architecture and the application it must live inside.

---

## 2. Design Goals

1. **Feel like Live View, indoors.** Guidance must appear world-locked and physically attached to the floor, updating smoothly as the user walks and turns.
2. **No new infrastructure.** No beacons, no markers-as-primary, no facility installation cost.
3. **No new backend dependency.** Fully offline/on-device, consistent with the rest of the application.
4. **No unproven engineering bets.** Stay inside ARCore's mature, maintained capabilities.
5. **No architectural ambiguity, including at the integration boundary.** Every decision an implementing engineer would otherwise have to invent — internal to the subsystem or at its edges with the existing application — is made explicitly in this document.
6. **Practical, not maximal.** Prefer the smallest change that closes a validated gap over a larger one; avoid speculative capability.
7. **Preserve existing, working investment wherever the review does not require otherwise.** Where the existing application already has a correct, previously-reviewed-as-sound component (the pathfinding engine, the landmark-recognition pipeline, `NavigationSessionManager`'s sensor collaborators), this architecture integrates with it rather than duplicating or replacing it.

---

## 3. Final Architecture Overview

The subsystem is organized as **eight cooperating layers** with strict, one-directional data ownership and a single explicit synchronization model bridging asynchronous work into the real-time render loop. This structure is unchanged from the version already found architecturally sound; what this revision adds is an explicit account of how each layer meets the existing application at its boundary.

```
Sensor Layer (existing: owned by NavigationSessionManager's collaborators — unchanged, not duplicated)
   │
   ▼
ARCore Session Layer ──────────────┐
   │                                │
   ▼                                │
Sensor Fusion / PDR Layer           │  (wraps existing NavigationSessionManager / DriftMonitor — §6)
   │ (corroborating signal only)    │
   ▼                                ▼
Localization Layer ──────────► Anchor Management Layer ──────► Rendering Layer
   ▲  (reuses existing recognition          ▲                    (ArSceneView, supersedes overlay/ — §6)
   │   pipeline, extended per §6)           │
Route/Path Layer ───────────────────────────┘
   ▲  (reads a one-time snapshot via the NavigationSessionInputAdapter — §6, not NavigationState directly)
   │
(existing pathfinding engine, unaffected)

Drift & Recovery Layer — supervises all of the above; owns no primary state, only reads and triggers.
```

Two update frequencies run concurrently:
- **Fast loop** (every ARCore-driven camera frame): pose update → anchor transform refresh → render.
- **Slow loop** (event-driven): a proximity-gated opportunity to re-fix, or a Drift & Recovery Layer decision — landmark recognition runs, and on success feeds a validated correction into the fast loop's next synchronization point.

---

## 4. Complete System Architecture

### Layer 1 — Sensor Layer
Raw input: camera frames, accelerometer, gyroscope, magnetometer, step-counter, and barometer. **Ownership, per §6:** this layer is not newly instantiated by this subsystem. Sensor listener registration for IMU-class sensors remains exactly where it already lives in the application — `NavigationSessionManager`'s existing collaborators (`SensorFusionManager`, `StepTracker`). This subsystem registers zero new sensor listeners.

### Layer 2 — ARCore Session Layer
Owns the ARCore session: VIO pose estimation, plane detection, and anchor lifecycle primitives; the single source of truth for device pose; reports tracking-quality state. Also owns camera image access while the session is active, which the Localization Layer consumes for periodic re-fix recognition (§6) rather than a second, conflicting camera session.

### Layer 3 — Sensor Fusion / PDR Layer
Produces a relative-displacement estimate and a sensor-liveness/corroboration signal, consumed by Layer 8 and, during tracking loss only, by Layer 6 as a fallback displacement source. **Per §6, this layer does not register independent sensor listeners** — it is a thin consumer of the existing `NavigationSessionManager`'s already-aggregated `StateFlow<NavSessionState>`, deriving its corroboration and staleness signals from that existing stream.

### Layer 4 — Localization Layer
Owns the facility transform exclusively. Responsibilities: the initial-fix acquisition policy, the proximity-gated periodic re-fix trigger, running landmark recognition (the existing on-device pipeline, extended per §6 to expose landmark count), fix-confidence tiering, and the Fix Validation Gate.

### Layer 5 — Route/Path Layer
Receives the planned route as a facility-coordinate polyline plus node metadata (turn angles, floor identifiers, destination node). **Per §6, its only input from the wider application is a one-time, session-scoped snapshot produced by the NavigationSessionInputAdapter** — it never reads the existing application's global navigation state directly, and neither does any other layer in this subsystem.

### Layer 6 — Anchor Management Layer
Converts the active portion of the route into ARCore-space anchors; owns the sliding-window lifecycle, tracking-origin rebasing, correction application, and the drift-vs-deviation distinction.

### Layer 7 — Rendering Layer
Draws floor-attached arrow/chevron geometry and turn/arrival markers at the anchors' current poses every frame, with a render-level pose-smoothing filter. **Per §6, this layer is implemented as an ArSceneView (SceneView/Filament) surface hosted within the existing navigation screen, and it fully supersedes the existing custom camera-overlay rendering pipeline** — it does not coexist with or wrap it.

### Layer 8 — Drift & Recovery Layer
The supervisory layer: classifies drift vs. deviation, manages the interruption grace window, selects environmental-condition-aware recovery UX, and triggers transition-mode and arrival states. **Per §6, this layer reuses the existing `DriftMonitor`'s raw divergence computation as one of its inputs rather than recomputing divergence independently** — it owns the classification and recovery *decision*, not the underlying measurement, where a working measurement already exists.

---

## 5. Module Responsibilities

| Layer | Owns | Reads From | Writes To | Never Does |
|---|---|---|---|---|
| 1. Sensor | Raw hardware streams (existing, unchanged ownership) | Hardware | Layers 2, 3 | Any processing/interpretation |
| 2. ARCore Session | VIO pose, plane detection, anchor primitives, tracking-quality state, camera image access | Layer 1 | Layer 6 (poses), Layer 8 (tracking state), Layer 4 (camera frames for periodic re-fix) | Facility-coordinate concepts |
| 3. Sensor Fusion/PDR | Relative-displacement estimate, sensor-liveness flag | The existing `NavigationSessionManager`'s `StateFlow<NavSessionState>` — **not** raw sensors directly | Layer 8 (corroboration signal only), Layer 6 (fallback displacement, tracking-loss only) | Register sensor listeners; directly modify the facility transform; directly trigger state transitions |
| 4. Localization | Facility transform, fix-confidence tier (now landmark-count-based, §6/§9), Fix Validation Gate | Layer 2 (pose, camera frames), Route/Path node metadata, the existing landmark-recognition pipeline (extended, §6) | Layer 6 (validated transform updates only) | Accept an unvalidated fix; run recognition off the proximity/throttle gate; perform its own independent landmark counting |
| 5. Route/Path | Facility-coordinate polyline, node metadata | The **NavigationSessionInputAdapter** (§6) — a one-time snapshot, not a live global-state read | Layers 4, 6, 8 | Read `NavigationState` directly; know anything about AR/ARCore |
| 6. Anchor Management | Active anchor window, spawn/despawn transitions, rebasing, correction application | Layers 2, 3 (fallback displacement), 4, 5, 8 (instructions) | Layer 7 | Compute drift-vs-deviation classification itself |
| 7. Rendering | Frame-level draw output, render-level pose smoothing | Layer 6 | Screen (via ArSceneView, hosted in the existing navigation screen, superseding the prior overlay pipeline) | Compute position, correction, or classification logic; coexist with the superseded overlay pipeline |
| 8. Drift & Recovery | State classification, recovery policy, mode triggers | Layers 2, 3, 4, and the existing `DriftMonitor`'s divergence output (reused, §6) | Instructions to Layers 4, 6 | Own or directly mutate primary tracking/transform state; recompute divergence measurement independently of `DriftMonitor` |

---

## 6. Integration Contract with the Existing Application

This section is the authoritative resolution of every integration-boundary finding raised during architecture review. It defines, explicitly, how this subsystem's eight layers meet the live application.

### 6.1 State input boundary — the `NavigationSessionInputAdapter`
The existing application stores the active destination, start point, and computed path in `NavigationState`, a global mutable singleton owned by the UI layer and read by twelve existing consumers across the application. This subsystem's own anti-global-state principle (§20) forbids any of its eight layers from depending on exactly this kind of shared mutable global.

**Resolution:** a single, narrow, read-only boundary component — the **NavigationSessionInputAdapter** — is the *only* point of contact between this subsystem and `NavigationState`. At the moment AR navigation begins, the adapter performs one read of `NavigationState`'s relevant fields (start node, destination node, computed path) and produces an immutable, session-scoped snapshot. Layer 5 (Route/Path) consumes that snapshot as its sole input and never touches `NavigationState` again for the remainder of the session — including during deviation-triggered route recalculation (§13), which is served by calling the existing pathfinding engine directly with the current best-estimate position, updating Layer 5's own internal snapshot, not the global singleton.

This is a deliberate choice of a narrow adapter over refactoring `NavigationState` itself. Refactoring a twelve-consumer, cross-layer global singleton into a reactive, layer-appropriate data source is a large, application-wide change disproportionate to what this subsystem needs, and it is explicitly out of scope for an architecture review of the AR subsystem specifically (§21, Engineering Trade-offs, and §22, Future Extension Points, both address this). The adapter fully satisfies this subsystem's own state-ownership principle at its boundary without requiring any change to `NavigationState`'s other eleven consumers.

### 6.2 Localization pipeline extension — landmark count exposure
The Fix Validation Gate's provisional/confirmed tiering (§9) requires knowing how many landmarks contributed to a given fix. The existing `LocalizationResult` type exposes a confidence tier (HIGH/MEDIUM/LOW) and a confidence score, but not a landmark count.

**Resolution:** the existing `LocalizationResult` type is extended with a `landmarkCount` field, populated by the existing recognition pipeline (which already internally distinguishes a single-landmark corridor-snap fallback from a multi-landmark bearing-triangulation solve, per its own existing implementation — this data already exists inside the pipeline, it is simply not currently surfaced). This is a minimal, additive, backward-compatible extension to an existing data contract — it does not modify the pipeline's detection or matching logic.

Two alternatives were considered and rejected. Mapping the existing `LocalizationTier` (a confidence-based classification) directly onto provisional/confirmed was rejected because confidence and landmark count are different concepts: a single-landmark detection can produce a high confidence score while still having poorly-constrained heading, which is precisely the case the provisional tier exists to flag. Having Layer 4 perform its own independent landmark counting was rejected because it would require Layer 4 to duplicate or second-guess internal pipeline logic it does not own, which is worse for long-term maintainability than a one-field data contract addition.

### 6.3 Rendering and camera lifecycle boundary
The architecture specifies an ArSceneView (SceneView/Filament) rendering surface. The existing live AR navigation flow uses CameraX `PreviewView` with a custom Canvas-drawn overlay (`CameraOverlayManager`/`CameraOverlayView`), and a separate, existing CameraX-based flow (`LogoScanScreen`) is used to perform the pre-navigation localization scan.

**Resolution, stated as three explicit decisions:**

1. **The existing `overlay/` package is fully superseded, not adapted or coexisting.** `OverlayProjectionEngine`, `OverlayNavigationEngine`, `CameraOverlayManager`, and `CameraOverlayView` are replaced in their entirety by Layers 6 and 7 for the AR navigation rendering path. This is not a new decision introduced by this document — it reaffirms the standing mandate that the deprecated AR subsystem is to be deleted, not preserved. `ArSceneView` is hosted within the existing navigation screen via standard Compose interop, occupying exactly the position `CameraOverlayView` previously occupied in that screen's layout.

2. **The pre-navigation localization scan (`LogoScanScreen`, CameraX-based) is retained unchanged and serves as the initial-fix mechanism (§9).** This screen already performs exactly the job Layer 4's bounded initial-fix acquisition policy requires — camera-based landmark recognition prior to navigation start — and its existing route position in the application (`logo_scan_with_dest → navigation`) is preserved. Its CameraX session is fully released before the subsequent `navigation` route initializes its ARCore session. This produces a clean, sequential (never concurrent) camera-ownership handoff: CameraX owns the camera during initial-fix acquisition; ARCore owns it from the moment AR navigation begins.

3. **Periodic re-fixes during active AR navigation (§9) consume ARCore's own camera image access, not a second CameraX session.** Once the ARCore session is active, it exclusively owns the camera device; a concurrent CameraX `PreviewView` session is not possible without conflict. The existing recognition pipeline, when invoked for a periodic re-fix, is fed frames sourced from ARCore's session rather than from a separate camera pipeline.

This closes the lifecycle boundary in both directions the review asked about: SceneView's relationship to the existing Compose navigation structure, and the relationship between the CameraX session used for scanning and the ARCore session used for live guidance are both now sequential and non-conflicting by construction, not by coincidence.

### 6.4 Sensor and drift-monitoring ownership boundary
`NavigationSessionManager` already owns `IndoorPositionTracker`, `SensorFusionManager`, `StepTracker`, and `DriftMonitor`, and exposes `StateFlow<NavSessionState>`. This subsystem's Layer 3 and Layer 8 have responsibilities that overlap these existing components.

**Resolution:**
- **Layer 3 wraps, not duplicates.** Layer 3 registers no sensor listeners of its own. It consumes `NavigationSessionManager`'s existing `StateFlow<NavSessionState>` (which already aggregates heading and step-based displacement) and derives its corroboration signal and staleness check from that stream. Sensor listener registration ownership remains entirely with `NavigationSessionManager`'s existing collaborators, unchanged — this eliminates the duplicate-listener risk by construction, since this subsystem never registers a second listener on the same sensor stream.
- **Layer 8 reuses `DriftMonitor`'s measurement, replaces its decision.** `DriftMonitor` already computes raw divergence between tracked position and expected path. Layer 8 consumes that raw divergence value as one of its inputs rather than recomputing it independently. However, `DriftMonitor`'s own existing relocalization-callback trigger is not invoked in the AR navigation context — the decision of what to do about a given divergence (apply a smoothed correction, classify as deviation and rebuild the route, or request a re-fix) is owned exclusively by Layer 8's classification logic (§12), replacing `DriftMonitor`'s simpler pre-existing decision path for the duration of an active AR session. This gives a single, unambiguous decision-maker and eliminates the conflicting-drift-detection-logic risk the review identified.
- **`NavSessionState` is bridged, not replaced.** Layer 8 reads `NavSessionState` (unmodified) as one of several inputs — alongside Layer 2's ARCore tracking state and Layer 4's fix history — and maps its relevant fields into its own classification decision. `NavSessionState` itself is not restructured, and no other consumer of `NavSessionState` elsewhere in the application is affected.

### 6.5 Consistency correction
The Module Responsibilities table (§5) explicitly lists Layer 3 as a read-source for Layer 6 (fallback displacement during tracking loss), aligning it with the corresponding statement in §9 — this closes a minor internal inconsistency flagged during review, where a prior version of this table omitted that relationship.

---

## 7. Data Flow

1. **Pre-navigation localization (unchanged existing flow):** the existing `LogoScanScreen` CameraX-based flow performs the initial landmark scan; on success, `NavigationState` is populated (start node, destination node, path) exactly as it is today.
2. **AR session start:** the `NavigationSessionInputAdapter` (§6.1) performs its one-time read of `NavigationState`, producing the session-scoped snapshot Layer 5 consumes. Layer 2 initializes the ARCore session; the prior CameraX session is released.
3. **Route materialization:** Layer 6 converts the upcoming portion of Layer 5's route into ARCore-space anchors, using the transform established by the initial fix (already available, since the initial fix already happened in step 1 — no separate "first AR-session fix" is required).
4. **Steady-state fast loop:** Layer 2 updates pose every frame → Layer 6 re-projects active anchors → Layer 7 (ArSceneView) applies render-level smoothing and draws.
5. **Steady-state slow loop:** Layer 4 checks proximity to known landmarks at a low-frequency interval; on a gated opportunity, it requests a frame via Layer 2's camera image access (§6.3), runs recognition, and — on a validated, landmark-count-tiered fix — produces a correction that Layer 6 applies smoothly and uses to rebase the tracking origin.
6. **Continuous supervision:** Layer 8 reads Layer 2's tracking state, Layer 3's corroboration signal (itself derived from the existing `NavSessionState` stream), and `DriftMonitor`'s raw divergence output, and issues instructions accordingly.
7. **Termination:** destination arrival or a deliberate user exit, both explicit terminal transitions (§10).

---

## 8. Runtime State Flow

Unchanged from the internally-approved design:

```
[No Fix] --(bounded acquisition window, existing LogoScanScreen flow, §9)--> [Acquiring]
[Acquiring] --successful validated fix--> [Tracking: Fresh]
[Acquiring] --acquisition window expires, no fix--> [Fallback Offered] (2D map available; user may keep trying)

[Tracking: Fresh] --time/distance elapses without re-fix--> [Tracking: Aging]
[Tracking: Aging] --successful validated re-fix--> [Tracking: Fresh]
[Tracking: Aging] --trust window exceeded, no re-fix available--> [Tracking: Degraded]
[Tracking: Degraded] --successful validated re-fix--> [Tracking: Fresh]

[Any Tracking state] --divergence classified as DRIFT--> smoothed correction applied, state unchanged
[Any Tracking state] --divergence classified as DEVIATION--> [Route Rebuilding] --new route materialized (via existing pathfinding engine, §6.1)--> [Tracking state unchanged, unaffected]

[Any Tracking state] --approaching a floor-change node--> [Transition Mode]
[Transition Mode] --new floor reached + mandatory validated re-fix succeeds--> [Tracking: Fresh]

[Any Tracking state] --ARCore reports tracking interruption--> [Interrupted: Grace] (under grace threshold)
[Interrupted: Grace] --tracking resumes within grace window--> [Tracking state as it was, unchanged]
[Interrupted: Grace] --grace window exceeded--> [Interrupted: Full]
[Interrupted: Full] --tracking resumes--> [No Fix] (fresh fix mandatory)

[Any Tracking state] --user deliberately exits AR--> [Session Ended]
[Session Ended] --user resumes AR navigation--> [No Fix] (a new session, not a resumption of stale state)

[Any Tracking state] --destination proximity satisfied--> [Arrived] --brief display period--> [Session Ended]
```

Rendering continues in every state except `[No Fix]`, `[Acquiring]`, and `[Session Ended]`.

---

## 9. Localization Strategy

**Initial fix.** Performed by the existing, unchanged `LogoScanScreen` flow prior to AR navigation start (§6.3) — this satisfies the bounded-acquisition requirement (target: 15–20 seconds of active scanning, with escalating guidance and an immediate manual "use the 2D map instead" option) using an existing, already-functioning screen rather than a new mechanism.

**Continuous tracking between fixes.** ARCore's VIO is trusted for all frame-to-frame updates; no recognition runs except as triggered below.

**Periodic re-fix — proximity-gated, throttled, and camera-boundary-aware.** Recognition is triggered only when the current best-estimate position falls within a defined proximity radius (target: 5–8 meters) of a known landmark, checked via a cheap position comparison (target: every 1–2 seconds), and throttled to a minimum interval between attempts (target: every 3–5 seconds). Per §6.3, these attempts are served from ARCore's own camera image access, never a second camera session.

**Fix-confidence tiering — now landmark-count-based (resolves the integration gap).** A fix is *provisional* if the extended `LocalizationResult.landmarkCount` (§6.2) is 1, and *confirmed* if it is 2 or more. Provisional fixes use a wider Fix Validation Gate tolerance and a partial trust-window reset; confirmed fixes use the tightest tolerance and a full reset.

**Fix Validation Gate.** Unchanged: displacement plausibility (target: reject if implied speed exceeds roughly 3–4× a brisk walking pace), graph plausibility (via existing corridor-snapping), and confidence-tier-appropriate tolerance. A fix that fails is discarded outright; three or more consecutive rejections prompt a manual rescan.

**Tracking-origin rebasing.** Every accepted fix rebases subsequent transform math relative to itself rather than session start, bounding long-traversal error accumulation. A forced-priority signal is raised if roughly 15–20 meters of VIO-only travel occurs with no natural re-fix opportunity.

---

## 10. Tracking Strategy

ARCore's VIO is the sole authority for continuous pose. The Sensor Fusion/PDR Layer's estimate — now explicitly sourced from the existing `NavigationSessionManager`'s `StateFlow<NavSessionState>` rather than independent sensor listeners (§6.4) — serves two roles only: a corroboration signal requiring sustained (3+ consecutive comparison windows) disagreement before contributing a single weighted input to Layer 8's decision-making, and a tracking-loss fallback displacement source. It never directly modifies tracked pose or the facility transform. A lightweight staleness heuristic, applied to the same existing sensor stream, excludes a flat-lined reading from the corroboration signal.

---

## 11. Anchor Management

Unchanged from the internally-approved design: a bounded sliding window (target: 8–12 anchors ahead, 2–3 trailing), fade-in/fade-out spawn/despawn transitions, tracking-origin rebasing at every accepted fix, and a distinct turn-in-place marker for direction changes exceeding roughly 120°. Cloud Anchors are not used; all anchors remain local to the current ARCore session.

---

## 12. Drift-vs-Deviation Classification and Recovery

The Drift & Recovery Layer's classification rule is unchanged: divergence that remains on the current planned path (within a lateral bound of roughly 2–3 meters, without crossing to a different graph edge) is drift, corrected smoothly by Layer 6; divergence that crosses to a different edge, exceeds the bound, or persists directionally for more than a short dwell time is deviation, triggering a route rebuild via the existing pathfinding engine (§6.1) and a deliberate, visible anchor-window rebuild. Per §6.4, the underlying divergence measurement is `DriftMonitor`'s existing computation; the classification and resulting action are owned exclusively by Layer 8.

---

## 13. Rendering Strategy

World-locked, floor-attached chevron rendering with animated flow toward the destination, hosted via **ArSceneView within the existing navigation screen, fully superseding the `overlay/` package** (§6.3). Render-level pose smoothing damps ARCore's normal frame-to-frame pose noise, distinct from Layer 6's drift-correction smoothing. Floor-plane-confidence monitoring provides a fixed-height-offset fallback for unstable (e.g., reflective) floor readings. Transition Mode replaces full AR rendering with a simplified directional instruction during floor changes, keeping the ARCore session alive but pausing anchor rendering. Arrival is rendered as a distinct destination marker, replacing turn-by-turn chevrons, with a defined terminal transition to session end.

---

## 14. Navigation Integration

Layer 5 consumes a facility-coordinate polyline and node metadata from the existing pathfinding engine, obtained exclusively via the one-time `NavigationSessionInputAdapter` snapshot (§6.1) — never a live read of the global navigation state. Deviation-triggered recalculation (§12) calls the same existing pathfinding engine directly with the current best-estimate position, updating Layer 5's own internal state rather than the global singleton. Floor transitions are existing graph nodes; Transition Mode (§13) wraps them without altering how the graph represents them, using the existing barometer signal as a corroborating (not authoritative) detection aid. Destination arrival uses proximity (target: 2–3 meters) to the existing destination graph node.

---

## 15. Failure Recovery

Unchanged from the internally-approved design: an interruption grace window (target: ~3 seconds) distinguishes transient interruptions (state retained) from sustained ones (fresh fix mandatory); OS-level backgrounding is handled identically by duration; each named environmental stress condition (poor lighting, reflective floors, blank-wall/ceiling pointing, crowded/dynamic obstacles) is handled via existing ARCore signals plus the small, targeted additions in §13, without new dedicated tracking subsystems; a sensor-staleness heuristic (§10) excludes stuck readings from the corroboration signal; initial-fix failure is fully covered by the existing `LogoScanScreen` flow's own bounded behavior (§9).

---

## 16. Device Compatibility & Manifest Policy

**Finding:** the existing manifest declares the AR camera feature (`android.hardware.camera.ar`) as required, while simultaneously declaring ARCore itself as optional — a direct contradiction that determines whether the app is even visible to users on non-ARCore-capable devices via the Play Store.

**Resolution — mandated policy:** the AR camera feature requirement is set to **not required**. This is the only choice consistent with every other decision in this architecture: a 2D map mode is treated throughout this document as the ultimate, always-available fallback (§8's `[Fallback Offered]` state, §15's failure recovery), and the architecture never treats AR as a hard requirement for the application to function. The ARCore optionality declaration, already correctly set to optional, remains unchanged — the fix is a single-attribute alignment, not a new policy.

**Stated product-level consequence, made explicit as part of this mandate:** MallAR remains installable and usable, via the 2D map, on devices without ARCore support. AR navigation is an enhanced mode layered on top of a fully-functional non-AR experience, not a gate on distribution. This resolves the device-compatibility ambiguity the review identified — the architecture's device-reach assumptions are now verifiable and consistent with the manifest as mandated.

---

## 17. Performance Strategy

Unchanged numeric targets from the internally-approved design:

| Parameter | Target |
|---|---|
| Render/update rate | Matches ARCore's native driven frame rate |
| Active anchor window | 8–12 ahead, 2–3 trailing |
| Recognition trigger | Proximity-gated (5–8 m), never continuous outside the existing bounded initial-acquisition flow |
| Recognition throttle | 3–5 seconds minimum between attempts |
| Proximity check frequency | Every 1–2 seconds |
| Initial-fix acquisition window | 15–20 seconds (served by the existing `LogoScanScreen` flow) |
| Interruption grace window | ~3 seconds |
| Tracking-origin forced-rebase distance | ~15–20 meters |
| Drift-vs-deviation lateral bound | ~2–3 meters |
| Turn-marker angle threshold | ~120° |
| Destination-arrival radius | ~2–3 meters |
| Sensor staleness window | ~2 seconds |
| Long-session check-in trigger | ~20 minutes |

Device-tier-aware tuning (a coarse two-tier parameter model) remains as specified previously — unaffected by this revision.

---

## 18. Scalability Strategy

Unchanged: floor-tagged anchors and Transition Mode fully specify multi-floor support; facility-agnostic design depends only on ARCore plus a per-facility landmark database and graph, both existing product artifacts; Layer 7's now-explicit ArSceneView contract (§6.3, §13) makes future rendering-style changes credibly Layer-7-only; the Localization Layer's decoupled contract permits a future commercial indoor-VPS service to replace the landmark-recognition mechanism without touching Layers 5–8.

---

## 19. Required Libraries & SDKs

No new technology is introduced by this revision. ARCore, the existing SceneView/Filament dependency (already declared in the build configuration, now given an explicit live-integration point per §6.3), the existing on-device landmark-recognition pipeline (extended additively per §6.2), and the standard Android sensor framework (consumed via the existing `NavigationSessionManager`, per §6.4, not newly) remain the complete dependency set.

---

## 20. Engineering Trade-offs

Carried forward from the internally-approved design (no custom SLAM; no beacon/UWB infrastructure; no Cloud Anchors/backend; PDR as corroboration-only), plus one new trade-off introduced by this revision:

- **A narrow adapter over a `NavigationState` refactor (§6.1).** This architecture deliberately does not require refactoring the existing global singleton before AR implementation can begin, even though the singleton itself remains an acknowledged architectural weakness of the wider application. The trade-off: this subsystem's own state-ownership principle is fully satisfied at its boundary, but the underlying singleton persists for its other eleven consumers, unresolved. This is judged the correct scope boundary for an AR-subsystem architecture review — fixing the wider application's global-state pattern is a separate, larger initiative, tracked as a future extension point (§22) rather than a precondition for this subsystem.

---

## 21. Remaining Accepted Risks

Unchanged from the internally-approved design, reaffirmed here: landmark-density-dependent accuracy in sparsely-signed corridors; the sensor-staleness check being a heuristic rather than a guarantee; environmental-condition handling being proportionate rather than exhaustive for extreme/compound conditions; turn/waypoint marker visual styling being deliberately left to visual design; the coarse two-tier device model; and no multi-user or cross-session persistence, consistent with the no-backend design goal. One risk is added by this revision: **`NavigationState`'s underlying architectural weakness is not resolved by this document** (§20) — accepted as out of scope for this subsystem, with the boundary fully contained by the adapter.

---

## 22. Future Extension Points

Unchanged list (long-session thermal-aware mode, Cloud Anchors, promoted Depth API occlusion, finer-grained device-tier tuning, a future commercial indoor-VPS provider, denser landmark placement or marker-based calibration aids), plus: **a future refactor of `NavigationState` into a reactive, layer-appropriate data source**, which would allow the `NavigationSessionInputAdapter` (§6.1) to be simplified or removed, tracked explicitly as a follow-on improvement to the wider application rather than a requirement of this subsystem.

---

## 23. Mandatory Review Resolution Matrix

| Finding ID | Decision | Resolution | Engineering Justification | Status |
|---|---|---|---|---|
| **M1** — Undefined integration contract with `NavigationState` | Accepted | Introduced the `NavigationSessionInputAdapter` (§6.1): a single, narrow, read-only boundary component that performs one snapshot read of `NavigationState` at session start; Layer 5 and all other layers never read the global singleton directly. | A full refactor of a twelve-consumer, cross-layer global singleton is disproportionate in scope to what this subsystem requires and is explicitly outside the boundary of an AR-subsystem architecture review. A narrow adapter fully satisfies this subsystem's own anti-global-state principle at its edge without requiring changes to the singleton's other consumers, and is the simplest change that resolves the contradiction. | Resolved |
| **M2** — `LocalizationResult` does not expose landmark count | Accepted | Extended the existing `LocalizationResult` type with an additive `landmarkCount` field, populated by data the existing recognition pipeline already computes internally; provisional/confirmed tiering (§9) now keys directly off this field. | An additive, backward-compatible data-contract extension is the minimal change that supplies the missing information without touching the pipeline's detection/matching logic. Mapping the existing confidence tier instead was rejected as semantically incorrect (confidence and landmark count are different concepts); independent counting in Layer 4 was rejected as unnecessary duplication of logic the pipeline already owns. | Resolved |
| **M3** — SceneView integration assumed but not present in live code | Accepted | Specified three explicit decisions (§6.3): the existing `overlay/` package is fully superseded (not adapted or coexisting) by Layers 6–7; the existing CameraX-based `LogoScanScreen` flow is retained unchanged as the initial-fix mechanism, with its camera session released before the ArSceneView-owned ARCore session begins; periodic re-fixes during active AR navigation consume ARCore's own camera image access rather than a second camera session. | Superseding `overlay/` reaffirms the standing mandate that the deprecated AR subsystem is to be deleted, not adapted. Retaining the existing `LogoScanScreen` flow preserves working, previously-reviewed functionality rather than duplicating it, consistent with the instruction to preserve existing decisions wherever possible. Routing periodic re-fixes through ARCore's own camera access is a technical necessity, not a preference — a concurrent second camera session is not possible once ARCore owns the device. | Resolved |
| **M4** — Manifest contradiction between AR requirement and ARCore optionality | Accepted | Mandated that the AR camera feature requirement be set to not-required, aligning it with the existing (unchanged) optional ARCore declaration. | This is the only choice consistent with every other decision in the architecture, which treats the 2D map as an always-available fallback and never treats AR as a hard requirement for the app to function. Choosing the alternative (mandatory AR) would have required removing the 2D fallback, contradicting multiple other approved design decisions. | Resolved |
| **M5** — Overlapping sensor/drift ownership with `NavigationSessionManager` | Accepted | Specified that Layer 3 wraps the existing `NavigationSessionManager`'s `StateFlow<NavSessionState>` rather than registering independent sensor listeners (eliminating duplicate-listener risk by construction), and that Layer 8 reuses `DriftMonitor`'s existing raw divergence computation while owning the classification/recovery decision exclusively, with `DriftMonitor`'s own prior relocalization-callback path not invoked during active AR sessions. `NavSessionState` is bridged as an input, not replaced or restructured. | Reusing existing, previously-reviewed-as-sound components (`NavigationSessionManager`'s collaborators, `DriftMonitor`) is preferable to duplicating their function, consistent with the instruction to preserve existing working investment. Giving Layer 8 sole ownership of the recovery decision — while still consuming the existing divergence measurement — is the minimal change needed to eliminate the specific race-condition risk (conflicting drift-detection logic) the review identified. | Resolved |

**All five mandatory findings are resolved. No mandatory finding remains open. Per the approval report's stated path to authorization, this document is ready for implementation.**
