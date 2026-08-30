# AR Engineering Specification — MallAR AR Subsystem

**Status:** Engineering contract between the frozen architecture and the implementation team.
**Source:** Derived exclusively from the documents in `docs/AR/`, with `AR_Subsystem_Redesign_Final.md` as the authoritative architecture. Nothing in this document introduces a new architectural decision — every requirement below traces to a decision already made and frozen in that document (or to a decision named but not fully unpacked there, in which case this document supplies only the precision needed to engineer it, never a new decision).
**Frozen constraint:** The architecture is not open for reinterpretation here. Where this specification appears to add detail beyond the architecture's prose, that detail is a direct, non-discretionary elaboration of a mechanism the architecture already names (e.g., its "single explicit synchronization model") — not an invention.

---

## 1. Executive Summary

**Purpose of the subsystem.** Render floor-attached, world-locked AR navigation guidance for indoor wayfinding, using continuous ARCore visual-inertial tracking corrected by occasional, event-driven landmark-based localization fixes — without new infrastructure, without a backend, and without altering the existing pathfinding, recognition, or session-management systems it depends on.

**Engineering goals.** Translate the eight-layer architecture and its integration contract into a specification precise enough that an implementation team makes zero architectural decisions of its own: every module boundary, every interface, every data contract, every state transition, every numeric target, and every constraint on what may and may not be touched in the existing codebase is defined here.

**Implementation boundaries.** This specification covers the AR subsystem's eight architectural layers and the one integration-boundary component the architecture introduces (the `NavigationSessionInputAdapter`). It does not cover, and implementation must not modify: the existing pathfinding engine's algorithm, the existing landmark-recognition pipeline's detection/matching logic (only an additive data-contract extension is in scope), `NavigationSessionManager`'s existing sensor-listener ownership, or any part of the application outside this subsystem's defined integration points. The pre-navigation localization scan flow (`LogoScanScreen`) is treated as an existing, unmodified dependency this subsystem consumes, not as part of this subsystem's own implementation surface.

---

## 2. Engineering Overview

The subsystem operates as eight layers plus one boundary-adapter component, connected by strict one-directional ownership and two concurrently running update cycles:

- A **fast cycle**, driven by ARCore's own frame delivery, in which pose updates flow from the ARCore Session Layer through the Anchor Management Layer to the Rendering Layer every frame. This cycle is what produces continuous, world-locked visual output and never waits on any other part of the system.
- A **slow cycle**, event-driven rather than fixed-interval, in which the Localization Layer periodically attempts a landmark-based correction, validates it, and — only at a single defined synchronization point — hands a validated result to the Anchor Management Layer for smooth application on a subsequent fast-cycle frame.

A supervisory layer (Drift & Recovery) observes both cycles without participating in either directly: it reads tracking-quality signals, a corroborating sensor-fusion signal, and fix history, and issues instructions (request a re-fix, apply a correction, rebuild the route, enter transition mode, enter the arrived state) to the layers that own the corresponding action. It never performs an action itself and never holds primary state.

The subsystem's only points of contact with the rest of the application are: a single, one-time read of the existing navigation state at session start (via the adapter), consumption of the existing pathfinding engine's output, consumption of the existing recognition pipeline (extended by one additive field), consumption of the existing sensor-fusion session manager's already-aggregated state stream (never raw sensors directly), and hosting its rendering surface inside the existing navigation screen in place of the previously deprecated overlay pipeline.

---

## 3. Module Specification

### Module 1 — Sensor Layer
- **Purpose:** Supply raw hardware signals to the layers that interpret them.
- **Responsibilities:** None beyond pass-through; performs no interpretation, filtering, or fusion.
- **Inputs:** Camera frames, accelerometer, gyroscope, magnetometer, step-counter, barometer.
- **Outputs:** Raw sensor streams, delivered to Module 2 (camera) and, indirectly, to Module 3 (IMU-derived streams, via the existing session manager — see Ownership).
- **Dependencies:** Android hardware sensor framework; existing camera hardware access.
- **Public responsibilities:** None — this module exposes no contract of its own to other subsystems; it is a pass-through boundary internal to the platform.
- **Ownership:** Sensor listener registration remains entirely with the existing application's `NavigationSessionManager` collaborators (`SensorFusionManager`, `StepTracker`). This subsystem registers zero new sensor listeners.
- **Failure responsibilities:** None — hardware failure/unavailability is surfaced through Module 2's tracking-quality reporting and Module 3's staleness detection, not handled at this layer.

### Module 2 — ARCore Session Layer
- **Purpose:** Own continuous six-degree-of-freedom pose tracking and provide the physical-world reference (plane detection, anchors) that all rendered content attaches to.
- **Responsibilities:** Visual-inertial pose estimation every frame; plane detection and plane-confidence reporting; anchor lifecycle primitives; tracking-quality state reporting (including failure reason where available); camera image access while the session is active.
- **Inputs:** Camera frames and IMU streams (Module 1), consumed internally by ARCore's own fusion — this subsystem does not re-implement or intercept this fusion.
- **Outputs:** Current device pose (to Module 6); tracking-quality state (to Module 8); camera image access (to Module 4, for periodic-re-fix recognition only).
- **Dependencies:** Google Play Services for AR (ARCore).
- **Public responsibilities:** Sole source of truth for device pose and floor-plane location for the duration of an active session.
- **Ownership:** Owns the ARCore session exclusively, including exclusive ownership of the camera device while active — no other module or existing application component may hold a concurrent camera session while this module's session is active.
- **Failure responsibilities:** Reports tracking-quality degradation and loss to Module 8; does not itself decide recovery behavior.

### Module 3 — Sensor Fusion / PDR Layer
- **Purpose:** Provide a corroborating, independently-derived motion estimate and a fallback displacement source during tracking loss, without ever being authoritative over tracked pose.
- **Responsibilities:** Derive a relative-displacement estimate; monitor for stale/flat-lined sensor readings within the stream it consumes.
- **Inputs:** The existing `NavigationSessionManager`'s already-aggregated `StateFlow<NavSessionState>` — not raw sensors directly.
- **Outputs:** A corroboration signal (to Module 8, weighted, never independently authoritative); a fallback relative-displacement estimate (to Module 6, tracking-loss scenarios only).
- **Dependencies:** The existing `NavigationSessionManager` and its already-instantiated collaborators.
- **Public responsibilities:** None beyond the two outputs above; this module exposes no write access to any tracked or transform state.
- **Ownership:** Owns its own derived corroboration/staleness computation; owns no sensor listener registration and no primary tracking state.
- **Failure responsibilities:** Flags a specific sensor input as stale when it shows no change across a defined window despite other signals indicating motion, and excludes that input from the corroboration signal until it recovers. Never independently forces a state transition.

### Module 4 — Localization Layer
- **Purpose:** Establish and periodically re-ground the mapping between ARCore's local tracking reference and the facility's real-world graph coordinates.
- **Responsibilities:** Consume the initial fix (produced by the existing pre-navigation scan flow, not computed by this module); run the proximity-gated, throttled periodic re-fix trigger; invoke the existing recognition pipeline for periodic fixes; apply fix-confidence tiering; run every candidate fix through the Fix Validation Gate before it may modify the transform; perform tracking-origin rebasing on every accepted fix.
- **Inputs:** Current best-estimate position (for the proximity gate); Module 2's camera image access (for periodic-fix frames); the existing recognition pipeline's output, including the additive `landmarkCount` field.
- **Outputs:** A validated facility transform update (to Module 6, exclusively).
- **Dependencies:** The existing on-device landmark-recognition pipeline (extended per §5, Data Contracts); the existing pre-navigation scan flow, for the initial fix specifically.
- **Public responsibilities:** Sole owner and sole writer of the facility transform.
- **Ownership:** Exclusive write ownership of the facility transform. No other module may write to it under any circumstance.
- **Failure responsibilities:** Discards any candidate fix that fails the Fix Validation Gate; after three or more consecutive rejections, signals Module 8 to prompt a manual rescan rather than continuing to discard silently. Does not itself decide the initial-fix failure fallback — that behavior belongs to the existing pre-navigation scan flow, which this module consumes rather than owns.

### Module 5 — Route/Path Layer
- **Purpose:** Represent the planned route in facility coordinates, independent of any AR concept.
- **Responsibilities:** Hold the active route as a facility-coordinate polyline plus node metadata (turn angles, floor identifiers, destination node); request route recalculation from the existing pathfinding engine when instructed.
- **Inputs:** A one-time, session-scoped snapshot from Module 9 (`NavigationSessionInputAdapter`) at session start; the existing pathfinding engine's output when a recalculation is instructed by Module 8.
- **Outputs:** Facility-coordinate polyline and node metadata (to Modules 4, 6, 8).
- **Dependencies:** The existing pathfinding engine (consumed, not modified); Module 9.
- **Public responsibilities:** The sole facility-coordinate representation of the route, consumable identically by this subsystem or by the existing 2D map renderer.
- **Ownership:** Owns its own internal route state after the initial snapshot; never re-reads the existing application's global navigation state directly after session start.
- **Failure responsibilities:** None beyond deferring to the existing pathfinding engine's own error handling for route-computation failure; this module does not define new error behavior for that case.

### Module 6 — Anchor Management Layer
- **Purpose:** Bridge the facility-coordinate route into ARCore-space, rendered content.
- **Responsibilities:** Maintain a bounded sliding window of active anchors; spawn and prune anchors with a fade transition; apply tracking-origin rebasing at every accepted fix; apply corrections smoothly (interpolated, never instantaneous); execute route rebuilds when instructed by Module 8; render a distinct marker at turn-angle nodes exceeding the defined threshold.
- **Inputs:** Device pose (Module 2); fallback displacement (Module 3, tracking-loss only); validated transform updates (Module 4); route data (Module 5); instructions — apply correction, rebuild route, enter transition mode, enter arrived state — from Module 8.
- **Outputs:** Current anchor poses (to Module 7).
- **Dependencies:** Module 2's anchor lifecycle primitives.
- **Public responsibilities:** The sole translator between facility-coordinate route data and ARCore-space renderable content.
- **Ownership:** Owns the active anchor window and all anchor lifecycle decisions. Does not own the drift-vs-deviation classification decision — it only executes the correction-or-rebuild instruction Module 8 issues.
- **Failure responsibilities:** None beyond executing Module 8's recovery instructions as issued; this module does not independently decide recovery behavior.

### Module 7 — Rendering Layer
- **Purpose:** Draw floor-attached AR guidance content.
- **Responsibilities:** Draw chevron/arrow geometry and turn/arrival markers at Module 6's current anchor poses every frame; apply a render-level pose-smoothing filter to damp ARCore's normal frame-to-frame pose noise; apply the floor-plane-confidence fallback (fixed-height offset) when plane detection is unstable; render Transition Mode's simplified guidance in place of full AR content; render the arrival state.
- **Inputs:** Current anchor poses (Module 6).
- **Outputs:** Rendered frame, via an ArSceneView surface hosted inside the existing navigation screen.
- **Dependencies:** SceneView/Filament (already a declared application dependency).
- **Public responsibilities:** The sole rendering surface for AR navigation content; fully supersedes the previously deprecated custom camera-overlay rendering pipeline, which this module replaces rather than coexists with.
- **Ownership:** Owns only frame-level draw output and its own render-level smoothing filter. Computes no position, correction, or classification logic.
- **Failure responsibilities:** None beyond rendering whatever state Module 6/Module 8 currently indicate (including a degraded-confidence visual treatment when instructed); this module does not decide when a degraded treatment applies.

### Module 8 — Drift & Recovery Layer
- **Purpose:** Supervise the system's tracking-quality and navigation-correctness state, and decide the appropriate recovery action.
- **Responsibilities:** Classify divergence from the planned route as drift or deviation; manage the interruption grace-window policy; select environmental-condition-aware recovery guidance; trigger Transition Mode on approach to a floor-change node; trigger the arrived state on destination-proximity satisfaction; issue recovery instructions to Modules 4 and 6.
- **Inputs:** Tracking-quality state (Module 2); corroboration signal (Module 3); fix history (Module 4); the existing `DriftMonitor`'s raw divergence measurement (reused, not recomputed).
- **Outputs:** Instructions to Module 4 (request re-fix) and Module 6 (apply correction / rebuild route / enter transition mode / enter arrived state).
- **Dependencies:** The existing `DriftMonitor` (its raw measurement, consumed as an input).
- **Public responsibilities:** Sole owner of the drift-vs-deviation classification and all recovery-policy decisions.
- **Ownership:** Owns no primary tracking or transform state — only the classification and instruction logic. The existing `DriftMonitor`'s own relocalization-callback trigger is not invoked during an active AR session; this module's classification and instruction logic is the sole decision-maker for the duration of that session.
- **Failure responsibilities:** Owns every failure-classification decision described in §11 (Failure Handling) below.

### Module 9 — NavigationSessionInputAdapter (Integration Boundary Component)
- **Purpose:** Provide the subsystem's only point of contact with the existing application's global navigation state, satisfying the subsystem's own state-ownership constraints at its boundary.
- **Responsibilities:** Perform exactly one read of the existing application's navigation state at AR session start; convert it into an immutable, session-scoped snapshot.
- **Inputs:** The existing application's global navigation state (start node, destination node, computed path), at the single moment AR navigation begins.
- **Outputs:** An immutable snapshot (to Module 5), consumed once.
- **Dependencies:** The existing application's global navigation state, as it currently exists — this module does not require and must not trigger any change to that state's structure or its other existing consumers.
- **Public responsibilities:** The only module in this subsystem permitted to read the existing global navigation state.
- **Ownership:** Owns the read operation and the snapshot it produces. Owns nothing else.
- **Failure responsibilities:** None defined; this subsystem does not specify behavior for the existing global navigation state being absent or malformed at the moment of read, since that condition is upstream of this subsystem's boundary (see §14, Engineering Constraints).

---

## 4. System Interfaces

- **AR subsystem ↔ Navigation subsystem (existing pathfinding engine, `NavigationState`, `NavigationSessionManager`, `DriftMonitor`):** Bounded to three specific contact points, each already named above — Module 9's one-time snapshot read of `NavigationState`; Module 5's consumption of the existing pathfinding engine's route output (both at session start and on deviation-triggered recalculation); Module 3's read of `NavigationSessionManager`'s `StateFlow<NavSessionState>` and Module 8's read of `DriftMonitor`'s divergence output. No other contact point with the existing navigation subsystem exists.
- **AR subsystem ↔ Recognition subsystem (existing landmark-recognition pipeline):** Module 4 invokes the existing pipeline for periodic re-fixes, consuming its output including the additive `landmarkCount` field (§5). The existing pre-navigation scan flow (`LogoScanScreen`) separately invokes the same underlying pipeline for the initial fix, outside this subsystem's own runtime — this subsystem consumes that flow's result via the same `NavigationState` boundary Module 9 reads.
- **AR subsystem ↔ Backend:** None. No network or server-side interface exists or is introduced.
- **AR subsystem ↔ UI:** Module 7 renders via an ArSceneView surface hosted inside the existing navigation screen. Module 8's recovery/guidance decisions (degraded-confidence indication, environmental prompts, manual-rescan prompts, the 2D-map fallback offer, arrival display) surface to the UI layer as state to be presented, not as UI logic this subsystem owns.
- **AR subsystem ↔ Sensors:** Exclusively through Module 1 (pass-through, existing ownership unchanged) and Module 2 (ARCore's own internal camera/IMU fusion, independent of Module 1's IMU pass-through).
- **AR subsystem ↔ Android platform:** ARCore (Google Play Services for AR) as a mandatory runtime dependency; the application manifest's AR-feature declaration (§12); Activity/session lifecycle events (pause, resume, camera interruption), handled per the interruption-duration policy in §11.

---

## 5. Data Contracts

| Data Object | Purpose | Ownership | Lifecycle | Producer | Consumer |
|---|---|---|---|---|---|
| **Navigation Session Snapshot** | Immutable start/destination/route input to this subsystem | Module 9 (produced), Module 5 (held thereafter) | Created once at AR session start; discarded at session end | Module 9 | Module 5 |
| **Facility-Coordinate Route** | The planned path, in facility coordinates, with turn/floor/destination metadata | Module 5 | Replaced wholesale on a deviation-triggered rebuild; otherwise persists for the session | Module 5 (from the snapshot, or from a recalculation) | Modules 4, 6, 8 |
| **Facility Transform** | The mapping between ARCore's local tracking reference and facility coordinates | Module 4 (exclusive writer) | Established at the initial fix; updated (never replaced wholesale) at each accepted periodic fix; rebased at each accepted fix | Module 4 | Module 6 |
| **Candidate Fix** | A single, not-yet-validated localization result from the recognition pipeline, including its `landmarkCount` | Module 4 (transient) | Exists only between recognition completing and the Fix Validation Gate's decision | The existing recognition pipeline (extended) | Module 4 (internal to its own validation step) |
| **Validated Correction** | A Fix Validation Gate-approved update to the facility transform | Module 4 (produced), Module 6 (applied) | Exists from gate approval until Module 6 has applied it | Module 4 | Module 6 |
| **Device Pose** | Current ARCore-tracked position/orientation | Module 2 | Refreshed every frame | Module 2 | Module 6 |
| **Tracking-Quality State** | ARCore's tracking status and failure reason, where available | Module 2 | Updated continuously | Module 2 | Module 8 |
| **Corroboration Signal** | Weighted agreement/disagreement between Module 3's estimate and ARCore's tracked displacement | Module 3 | Recomputed continuously over a rolling comparison window | Module 3 | Module 8 |
| **Divergence Measurement** | Raw magnitude/direction of tracked-position divergence from the planned route | The existing `DriftMonitor` (reused) | Computed continuously | Existing `DriftMonitor` | Module 8 |
| **Active Anchor Set** | The current sliding window of rendered anchors and their poses | Module 6 | Continuously pruned/extended as the user progresses | Module 6 | Module 7 |
| **Recovery Instruction** | A directive from Module 8 to Module 4 or Module 6 (request re-fix, apply correction, rebuild route, enter transition mode, enter arrived state) | Module 8 (issued), the receiving module (executed) | Transient, issued and consumed per triggering condition | Module 8 | Modules 4, 6 |

**Conceptual only.** No class, struct, or serialization format is defined by this specification; the above describes logical objects and their ownership, not implementation types.

---

## 6. Runtime Lifecycle

```
Initialization
    ↓
Localization
    ↓
Tracking
    ↓
Navigation
    ↓
Recovery
    ↓
Termination
```

- **Initialization.** Precondition: the existing pre-navigation scan flow has already produced an initial fix and populated the existing application's navigation state (this precedes this subsystem's own runtime and is not part of it). On entering AR navigation: Module 9 performs its one-time snapshot read; Module 2 initializes the ARCore session; Module 5 materializes the initial route from the snapshot; Module 6 seeds the initial anchor window using the transform the initial fix already established.
- **Localization.** Not a single discrete stage in practice — the initial fix is already satisfied by the time this subsystem's runtime begins (see Initialization), and periodic re-fixes recur continuously throughout the Tracking and Navigation stages via Module 4's proximity-gated trigger. This stage is represented here to satisfy the requested lifecycle shape; its actual behavior is the continuously-recurring proximity-gated/throttled/validated process defined in §3 (Module 4) and §7 (state machine), not a stage the system passes through once.
- **Tracking.** The fast cycle runs continuously: Module 2 pose updates → Module 6 anchor re-projection → Module 7 rendering, every frame, for the duration of the session.
- **Navigation.** Module 5's route data drives Module 6's anchor materialization; Module 8 continuously evaluates divergence (drift vs. deviation), floor-transition proximity, and arrival proximity, issuing instructions as each condition is met.
- **Recovery.** Entered whenever Module 8 detects a condition requiring it: tracking interruption beyond or within the grace window, repeated fix rejection, environmental degradation. Recovery actions are issued to Modules 4 and 6 as described in §11; the system returns to normal Tracking/Navigation once the triggering condition resolves.
- **Termination.** Entered on destination-proximity satisfaction (via the Arrived state) or a deliberate user exit. Both are explicit, defined terminal transitions (§7) — the ARCore session, active anchors, and the session snapshot are torn down; no state is retained across termination. Resuming AR navigation afterward always re-enters at Initialization, never at a retained state.

---

## 7. Runtime State Machine

| State | Entry Conditions | Exit Conditions | Allowed Transitions | Failure Transitions |
|---|---|---|---|---|
| **No Fix** | Session start (initial fix already satisfied by the existing pre-navigation flow, per §6); or a full interruption resolving | Acquisition begins | → Acquiring | — |
| **Acquiring** | Entered from No Fix | A fix succeeds, or the acquisition window expires | → Tracking: Fresh (success); → Fallback Offered (window expiry) | → Fallback Offered |
| **Tracking: Fresh** | A validated fix (initial or re-fix) is accepted | Time/distance since the last fix exceeds the freshness bound | → Tracking: Aging | — |
| **Tracking: Aging** | Entered from Tracking: Fresh | A new validated fix is accepted, or the trust window is exceeded with none available | → Tracking: Fresh (re-fix); → Tracking: Degraded (trust window exceeded) | → Tracking: Degraded |
| **Tracking: Degraded** | Trust window exceeded with no re-fix | A validated re-fix is accepted | → Tracking: Fresh | — |
| **Route Rebuilding** | Divergence classified as deviation (any Tracking state) | New route materialized | → prior Tracking state, unaffected | — |
| **Transition Mode** | Approaching a floor-change node (any Tracking state) | New floor reached and a mandatory validated re-fix succeeds | → Tracking: Fresh | Remains in Transition Mode until the mandatory re-fix succeeds; no alternate exit is defined |
| **Interrupted: Grace** | ARCore reports a tracking interruption, any Tracking state | Tracking resumes within the grace window, or the grace window is exceeded | → prior Tracking state, unchanged (resumed in time); → Interrupted: Full (window exceeded) | → Interrupted: Full |
| **Interrupted: Full** | Grace window exceeded | Tracking resumes | → No Fix (fresh fix mandatory; no state carried over) | — |
| **Fallback Offered** | Acquisition window expired with no fix | User continues attempting acquisition, or accepts the 2D-map fallback | → Acquiring (continued attempt) | — |
| **Arrived** | Destination-proximity satisfied, any Tracking state | Brief display period elapses | → Session Ended | — |
| **Session Ended** | Arrival's display period elapses, or a deliberate user exit, any state | User resumes AR navigation | → No Fix (a new session; no retained state) | — |

Rendering (Module 7) is active in every state except **No Fix**, **Acquiring** (prior to a first fix), and **Session Ended**; in **Tracking: Degraded** and **Interrupted: Grace**, rendering continues using the best available state, visually flagged as reduced-confidence or held at the last known-good frame, respectively.

---

## 8. Communication Model

- **Ownership:** Every data object in §5 has exactly one producing owner, as stated in the Module Specification's "Owns" designation. No module reads another module's owned state by any path other than the explicit interfaces defined in §3 and §4.
- **Direction:** Communication is one-directional per relationship — Modules 1→2→6→7 (the fast cycle) and Modules 4→6 (the slow cycle's correction path) never flow in reverse. Module 8 is the single exception permitted to issue instructions "against the grain" of the primary data flow (to Modules 4 and 6), because it is explicitly a supervisory layer that owns no primary state of its own — this is a deliberate, bounded exception, not a general pattern.
- **Synchronization expectations:** The fast cycle (Module 2 → Module 6 → Module 7) executes every frame and never blocks on the slow cycle (Module 4's recognition and validation work). A result produced by the slow cycle is merged into Module 6's state only at a single, defined synchronization point per frame cycle — it is picked up cleanly at the start of the next fast-cycle iteration, never applied mid-frame. This is the specific mechanism the architecture refers to as its "single explicit synchronization model bridging asynchronous work into the real-time render loop," and it is the only point at which the fast and slow cycles interact directly.

---

## 9. Threading Model

- **Camera processing:** ARCore's own internal camera+IMU fusion (Module 2) operates on ARCore's own managed update cadence. The existing pre-navigation scan flow's camera capture (for the initial fix) is fully released before Module 2's session begins — these two camera consumers are strictly sequential, never concurrent. Periodic re-fix frame access (Module 4) is served from Module 2's camera image access, not a separate capture path.
- **Tracking:** Pose, plane, and anchor-primitive updates (Module 2) run synchronously with ARCore's frame delivery — this is the fast cycle's origin point.
- **Rendering:** Module 7's draw output is synchronous with the fast cycle, consuming Module 6's current anchor poses at the start of each frame.
- **Navigation (localization and recovery logic):** Module 4's recognition invocation and Fix Validation Gate evaluation execute asynchronously, off the fast cycle's critical path — this is the slow cycle. Module 8's supervisory evaluation is non-blocking with respect to both cycles; it reads already-published state from Modules 2, 3, and 4 rather than requiring either cycle to pause for it.
- **UI:** Consumes Module 7's rendered output and Module 8's issued guidance/state as already-computed values to display; UI-level presentation is not part of either the fast or slow cycle and must not be positioned on the critical path of either.
- **Synchronization expectations, restated for threading specifically:** each data object in §5 has exactly one writer (§8); the slow cycle's only write into fast-cycle-consumed state (the Facility Transform, applied via Module 6) occurs at the single defined synchronization point per frame, never mid-frame, and never requires the fast cycle to block waiting for it.

---

## 10. Performance Requirements

| Parameter | Target |
|---|---|
| Render/update rate | Matches ARCore's native driven frame rate; no artificial cap imposed below it |
| Active anchor window | 8–12 anchors ahead of current position, 2–3 trailing |
| Recognition trigger | Proximity-gated only (5–8 m radius), never continuous outside the bounded initial-acquisition window |
| Recognition throttle | Minimum 3–5 seconds between attempts once gated |
| Proximity/position check frequency | Every 1–2 seconds |
| Initial-fix acquisition window | 15–20 seconds before the 2D-map fallback is offered |
| Interruption grace window | ~3 seconds |
| Tracking-origin forced-rebase distance | ~15–20 meters of uncorroborated travel since the last accepted fix |
| Drift-vs-deviation lateral bound | ~2–3 meters from the planned path centerline |
| Turn-marker angle threshold | ~120° direction change |
| Destination-arrival radius | ~2–3 meters |
| Sensor staleness window | ~2 seconds of unchanged readings during detected motion |
| Long-session check-in trigger | ~20 minutes of continuous active session |
| Device-tier tuning | A coarse two-tier parameter model (standard / constrained); constrained-tier devices use shorter trust windows and wider validation tolerances than the defaults above |

Memory and battery expectations are governed by the above bounds (anchor count, recognition frequency) rather than by a separately stated numeric ceiling; no additional memory/battery target beyond what these bounds imply is defined in the frozen architecture, and none is introduced here.

---

## 11. Failure Handling

| Scenario | Required Behavior |
|---|---|
| **Tracking loss (ARCore-reported)** | If resolved within the interruption grace window (~3 s): retain existing transform/anchors, hold last known-good render frame, resume without a fresh fix. If exceeded: discard the transform, require a fresh validated fix (state → No Fix). |
| **Localization failure (initial)** | Governed entirely by the existing pre-navigation scan flow's own bounded acquisition behavior; this subsystem consumes its result and is not responsible for defining new behavior for this case. |
| **Localization failure (periodic re-fix)** | A failed or gate-rejected candidate fix is discarded; no transform change occurs. Three or more consecutive rejections trigger a manual-rescan prompt via Module 8. |
| **Camera interruption (OS-level: app backgrounded, another app claims the camera, permission revoked)** | Treated identically to an ARCore-reported tracking interruption of the corresponding duration — grace-window policy applies. |
| **Sensor inconsistency (stale/flat-lined reading)** | Module 3 excludes the affected input from the corroboration signal until it recovers; no state transition is forced by this condition alone. |
| **Sensor disagreement (Module 3 vs. Module 2)** | A single disagreement is not acted on. Only sustained disagreement across three or more consecutive comparison windows contributes one weighted input to Module 8's decision-making — it never independently forces a correction or transition. |
| **User deviation** | Module 8 classifies divergence exceeding the lateral bound, crossing to a different route edge, or persisting directionally beyond a short dwell time as deviation, not drift. Instructs Module 5 to recalculate via the existing pathfinding engine and Module 6 to rebuild its anchor window — a deliberate, visible discontinuity. |
| **Drift (non-deviation divergence)** | Corrected smoothly by Module 6, interpolated over a small number of frames; no route rebuild, no visible discontinuity. |
| **Route recalculation** | Performed exclusively via the existing pathfinding engine, called with the current best-estimate position; the existing engine's own error handling governs recalculation failure. |
| **Session restart (deliberate exit, then resume)** | No state is retained across a deliberate exit. Resumption always re-enters at No Fix / Initialization. |
| **Environmental degradation — poor lighting** | Handled via ARCore's own tracking-quality/failure-reason reporting, feeding standard recovery guidance; no additional mechanism. |
| **Environmental degradation — reflective/unstable floor plane** | Module 7's floor-plane-confidence fallback (fixed-height offset) applies when plane detection is repeatedly re-estimating within a short window. |
| **Environmental degradation — camera pointed at a blank wall/ceiling** | Distinguished from general tracking loss using device-orientation data combined with ARCore's reported failure reason, to select a more specific recovery prompt. |
| **Environmental degradation — crowded/dynamic obstacles** | Rendering occlusion remains a device-capability-gated enhancement, unaffected by this scenario. Tracking-quality degradation from crowd-dominated frames is handled by ARCore's own inherent feature-tracking behavior; wasted recognition attempts are avoided by construction, since re-fix attempts are proximity-gated rather than vision-triggered. |

---

## 12. Integration Requirements

**Required dependencies:**
- Google Play Services for AR (ARCore) — mandatory runtime dependency.
- SceneView/Filament — already declared in the application's build configuration; this specification requires its live integration into the existing navigation screen (Module 7).
- The existing on-device landmark-recognition pipeline — required as-is, plus the additive `LocalizationResult.landmarkCount` field (§5).
- The existing `NavigationSessionManager` and its collaborators (`SensorFusionManager`, `StepTracker`, `IndoorPositionTracker`, `DriftMonitor`) — required as existing, unmodified dependencies (Modules 3 and 8).
- The existing pathfinding engine — required as an existing, unmodified dependency (Module 5).
- The existing pre-navigation scan flow (`LogoScanScreen`) — required as an existing, unmodified dependency, serving as the initial-fix mechanism.

**Expected interfaces:** exactly the set defined in §4 — no additional interface with any existing system is in scope.

**Existing systems used (unmodified):** the pathfinding engine, `NavigationSessionManager` and its collaborators, `DriftMonitor`, the pre-navigation scan flow, the existing application's global navigation state (read once, via Module 9).

**Existing systems superseded:** the previously deprecated custom camera-overlay rendering pipeline (`OverlayProjectionEngine`, `OverlayNavigationEngine`, `CameraOverlayManager`, `CameraOverlayView`) — fully replaced by Modules 6 and 7, not adapted or run in parallel.

**Required contracts:**
- The application manifest's AR camera feature declaration (`android.hardware.camera.ar`) must be set to not-required, consistent with the ARCore-optionality declaration already present and with this subsystem's treatment of AR as an enhanced mode rather than a hard requirement.
- `LocalizationResult` must expose the `landmarkCount` field described in §5 before Module 4's fix-confidence tiering can be implemented.

---

## 13. Acceptance Criteria

**Module 2 (ARCore Session):**
- Tracking-quality state transitions (active/paused/stopped, with failure reason where available) are observable by Module 8 within one frame of ARCore reporting them.
- Camera device ownership is never concurrently held by any other component while this module's session is active.

**Module 3 (Sensor Fusion/PDR):**
- Zero new `SensorEventListener` registrations exist anywhere in this subsystem's implementation; all IMU-derived data is verifiably sourced from `NavigationSessionManager`'s existing `StateFlow<NavSessionState>`.
- A sensor input that shows no change for the defined staleness window (~2 s) during independently-confirmed motion is excluded from the corroboration signal within that window.

**Module 4 (Localization):**
- No candidate fix is applied to the facility transform without passing the Fix Validation Gate's displacement-plausibility and graph-plausibility checks.
- A fix with `landmarkCount == 1` is tagged provisional; a fix with `landmarkCount >= 2` is tagged confirmed; tolerance and trust-window behavior differ measurably between the two tiers.
- No recognition attempt occurs unless the proximity gate (5–8 m) is satisfied, except during the bounded initial-acquisition window (handled by the existing scan flow, outside this module).
- No two recognition attempts occur closer together than the throttle interval (3–5 s).

**Module 5 (Route/Path):**
- `NavigationState` (the existing global navigation state) is read exactly once per session, at session start, by Module 9 only; no other read of it occurs anywhere in this subsystem's implementation.

**Module 6 (Anchor Management):**
- The active anchor count never exceeds the defined window bound (8–12 ahead, 2–3 trailing).
- A correction is never applied as an instantaneous change to a previously-rendered anchor pose; it is measurably interpolated over more than one frame.
- A route rebuild (deviation response) is visually and temporally distinguishable from a drift correction in the sequence of rendered frames.

**Module 7 (Rendering):**
- The previously deprecated overlay pipeline (`CameraOverlayView`/`CameraOverlayManager`) is not instantiated anywhere in the live AR navigation flow.
- Render-level pose smoothing measurably reduces frame-to-frame anchor-position variance compared to unfiltered ARCore pose output.

**Module 8 (Drift & Recovery):**
- Every divergence event is classified as either drift or deviation; no divergence event is left unclassified or produces no instruction.
- `DriftMonitor`'s own relocalization-callback path is verifiably not invoked during an active AR session.
- An interruption resolved within the grace window produces zero fresh-fix requirement; one exceeding it always does.

**Module 9 (NavigationSessionInputAdapter):**
- Exactly one read of the existing global navigation state occurs per AR session, at session start.

---

## 14. Engineering Constraints

**Developers MUST NOT:**
- Modify the existing global navigation state's structure or any of its other existing consumers.
- Modify the existing pathfinding engine's algorithm or output format.
- Modify the existing landmark-recognition pipeline's detection or matching logic (the `landmarkCount` field addition is the only permitted change, and it is additive).
- Register any new sensor listener that duplicates a stream already owned by `NavigationSessionManager`'s existing collaborators.
- Allow any module other than Module 4 to write the facility transform.
- Allow Module 3's corroboration signal to independently force a state transition or correction without Module 8's weighted evaluation.
- Reintroduce, adapt, or run the previously deprecated overlay pipeline (`OverlayProjectionEngine`, `OverlayNavigationEngine`, `CameraOverlayManager`, `CameraOverlayView`) alongside Modules 6–7.
- Invoke `DriftMonitor`'s own relocalization-callback path during an active AR session.
- Apply a correction to rendered anchor state as an instantaneous change.
- Run landmark recognition continuously or on any trigger other than the proximity gate, outside the bounded initial-acquisition window (which is owned by the existing scan flow, not this subsystem).
- Hold a second, concurrent camera session while Module 2's ARCore session is active.
- Introduce a Cloud Anchors dependency, a backend/network dependency, or any new third-party AR/positioning SDK.

**Developers MAY extend:**
- Module 7's rendering style, provided it continues to consume Module 6's anchor-pose contract unchanged.
- Device-tier tuning granularity, beyond the coarse two-tier default, provided the underlying parameters and their meaning are unchanged.
- Module 4's localization mechanism to a future alternative provider (e.g., a commercial indoor-VPS service), provided it continues to produce the same Candidate Fix contract (§5) the Fix Validation Gate consumes.
- Depth API-based occlusion in Module 7, provided it remains device-capability-gated and does not become a hard requirement.

**Developers MUST preserve:**
- The existing pathfinding engine, unmodified.
- The existing pre-navigation scan flow (`LogoScanScreen`) and its position in the application's route sequence.
- The existing landmark-recognition pipeline's core matching logic.
- `NavigationSessionManager`'s existing sensor-listener ownership.
- The two-tier hybrid tracking approach (ARCore VIO as the sole authority over tracked pose; PDR strictly corroborating, never authoritative).
- The single-writer-per-module ownership model defined in §3 and §8.

---

## 15. Implementation Readiness Checklist

| Requirement | Status |
|---|---|
| Every module's purpose, responsibilities, inputs, outputs, dependencies, ownership, and failure responsibilities are defined | ✅ §3 |
| Every interface with an external subsystem (Navigation, Recognition, Backend, UI, Sensors, Android platform) is defined | ✅ §4 |
| Every data object exchanged between modules has a defined purpose, ownership, lifecycle, producer, and consumer | ✅ §5 |
| The full runtime lifecycle, from initialization to termination, is defined | ✅ §6 |
| Every runtime state has defined entry conditions, exit conditions, allowed transitions, and failure transitions | ✅ §7 |
| Ownership, direction, and synchronization expectations for inter-module communication are defined | ✅ §8 |
| Threading responsibilities and synchronization expectations are defined for camera, tracking, rendering, navigation, and UI work | ✅ §9 |
| Every performance target is stated as a measurable, non-qualitative value | ✅ §10 |
| Every named failure scenario has a defined required behavior | ✅ §11 |
| Every integration point with the existing application — dependencies, interfaces, reused systems, superseded systems, required contracts — is defined | ✅ §12 |
| Every module has objective, testable acceptance criteria | ✅ §13 |
| Explicit constraints on what must not change, what may be extended, and what must be preserved are defined | ✅ §14 |
| No section of this specification required inventing a decision not already present in the frozen architecture | ✅ — every requirement above traces to `AR_Subsystem_Redesign_Final.md`, or to a mechanism it names and this document only makes precise (§8's synchronization model) |

**This specification is complete. An implementation team requires no additional architectural guidance to begin work from this document.**
