# AR Implementation Roadmap — MallAR AR Subsystem

**Status:** Authoritative execution plan. Derived exclusively from `AR_Subsystem_Redesign_Final.md` (frozen architecture) and `AR_Engineering_Specification.md` (frozen engineering contract). Neither document is modified or reinterpreted here — this roadmap sequences already-decided work; it does not decide anything new.
**Audience:** A senior Android engineering team implementing the subsystem for the first time.
**Purpose:** Remove every "what do we build next" decision from the implementation process. Each phase is self-contained, independently verifiable, and ordered so that no phase depends on work that has not yet been validated.

---

## Overall Roadmap

Ten phases, ordered by dependency and by risk — the highest-novelty, highest-integration-risk work (ARCore/SceneView inside the existing live application) is deliberately pulled forward and isolated behind a trivial validation target, rather than left until the end where a failure would be expensive to diagnose.

```
Phase 0 — Environment, Manifest, and Dependency Baseline
Phase 1 — Data Contract Extension (Recognition Pipeline)
Phase 2 — Integration Boundary Foundation (Module 9 + Module 5)
Phase 3 — Sensor Fusion Boundary (Module 3)
Phase 4 — ARCore Session and Minimal Rendering Validation (Module 2 + minimal Module 7)
Phase 5 — Localization Layer (Module 4)
Phase 6 — Anchor Management: Core Lifecycle (Module 6, correction path only)
Phase 7 — Rendering Layer: Full Fidelity (Module 7, complete)
Phase 8 — Drift & Recovery Layer (Module 8) + Deviation / Transition / Arrival Integration
Phase 9 — Full System Integration, Hardening, and Failure-Scenario Validation
```

Dependency graph (an arrow means "must be complete before"):

```
Phase 0 ─┐
Phase 1 ─┼──► Phase 5 ──► Phase 6 ──► Phase 7 ──► Phase 8 ──► Phase 9
Phase 2 ─┤        ▲
Phase 3 ─┤        │
Phase 4 ─┴────────┘
```

Phases 0, 1, 2, and 3 have no dependency on one another and may be executed in any order, or concurrently by separate engineers, without violating this roadmap — see Integration Strategy. Phase 4 depends on nothing but Phase 0. From Phase 5 onward, the sequence is strictly linear: each phase requires every phase before it.

---

## Phase 0 — Environment, Manifest, and Dependency Baseline

**Objective.** Establish the non-negotiable groundwork every later phase depends on, with zero subsystem-specific logic.

**Why This Phase Exists.** Every subsequent phase assumes a correct manifest and a confirmed, compileable dependency set. Resolving this first means no later phase can be blocked or invalidated by a foundational configuration error discovered mid-implementation.

**Scope.**
- Included: setting the AR camera feature requirement to not-required in the application manifest, aligned with the existing ARCore-optionality declaration; confirming the ARCore and SceneView dependencies are present, correctly configured, and compile cleanly; confirming the existing systems this subsystem will integrate with (the pathfinding engine, `NavigationSessionManager` and its collaborators, `DriftMonitor`, the pre-navigation scan flow, `LocalizationResult`, `NavigationState`) are present, unmodified, and building successfully.
- Excluded: any AR-specific class, screen, or logic; any change to the recognition pipeline; any rendering work.

**Prerequisites.** None. This is the first phase.

**Components.** Manifest configuration only. No architectural module from the Engineering Specification is implemented in this phase.

**Dependencies.**
- Internal: none.
- External: the application's existing build configuration; ARCore and SceneView as already-declared dependencies.

**Deliverables.** A manifest with the AR feature requirement corrected; a written confirmation (e.g., a build log or checklist) that every existing system this roadmap depends on later is present and compiles.

**Validation Criteria.**
- The manifest's AR feature requirement and ARCore optionality declaration are mutually consistent (both permit installation on non-ARCore devices).
- A clean build succeeds with no changes beyond the manifest.
- Each existing dependency system listed above is confirmed present at its expected location in the codebase.

**Exit Criteria.** Manifest change merged and built successfully; dependency confirmation checklist complete.

**Risks.**
- *Risk:* the manifest change interacts unexpectedly with existing Play Store metadata or distribution configuration outside the codebase. *Mitigation:* treat this as a configuration-only change validated against a build artifact, not against store-listing behavior, which is outside this roadmap's scope.

---

## Phase 1 — Data Contract Extension (Recognition Pipeline)

**Objective.** Add the additive `landmarkCount` field to `LocalizationResult`, sourced from data the existing recognition pipeline already computes internally.

**Why This Phase Exists.** This unlocks Module 4's fix-confidence tiering later (Phase 5), and is deliberately done now, in isolation, against the existing (non-AR) localization flow — so that if anything regresses, it is trivially attributable to this one additive change rather than entangled with any AR-specific work that doesn't yet exist.

**Scope.**
- Included: extending `LocalizationResult` with a `landmarkCount` field; populating it from the pipeline's existing internal computation (the pipeline already distinguishes a single-landmark corridor-snap fallback from a multi-landmark bearing-triangulation solve — this phase surfaces that distinction, it does not compute anything new).
- Excluded: any change to the pipeline's detection or matching logic; any consumption of the new field (that begins in Phase 5).

**Prerequisites.** None beyond Phase 0's confirmation that the existing recognition pipeline is present and building.

**Components.** The existing recognition pipeline's output type only. No Engineering Specification module is implemented in this phase.

**Dependencies.**
- Internal: none.
- External: the existing recognition pipeline, unmodified except for this additive field.

**Deliverables.** `LocalizationResult` exposing `landmarkCount`; the existing (pre-AR) localization flow continuing to build and behave identically to before this change in every respect other than the new field being populated.

**Validation Criteria.**
- The existing pre-AR localization flow's existing behavior is unchanged (no regression in the current, already-shipping scan-to-localize experience).
- `landmarkCount` is populated correctly for both a single-landmark and a multi-landmark detection, verified against known inputs.

**Exit Criteria.** Field added, populated correctly, existing localization flow regression-free.

**Risks.**
- *Risk:* the pipeline's internal landmark-count data is harder to surface than assumed (e.g., it is discarded before the point where `LocalizationResult` is constructed). *Mitigation:* if this is discovered, it is a specification-completeness gap, not an implementation problem to solve ad hoc — escalate per the Implementation Rules rather than inventing an alternate source for the count.

---

## Phase 2 — Integration Boundary Foundation (Module 9 + Module 5)

**Objective.** Implement the subsystem's only two points of contact with existing navigation/route data: the `NavigationSessionInputAdapter` (Module 9) and the Route/Path Layer (Module 5).

**Why This Phase Exists.** These modules have no dependency on ARCore, sensors, or rendering. Building and proving them first establishes "how the subsystem gets in" before any of "what happens once it's in" exists, and validates the architecture's core anti-global-state boundary (a single, narrow read of `NavigationState`) as early and as cheaply as possible.

**Scope.**
- Included: the one-time snapshot read of `NavigationState` at AR session start; the immutable session-scoped snapshot it produces; Module 5's facility-coordinate route and node-metadata representation, populated from that snapshot; the capability (not yet triggered) to request a route recalculation from the existing pathfinding engine.
- Excluded: any ARCore, anchor, or rendering concept; the live trigger for route recalculation (that arrives with Module 8 in Phase 8) — this phase implements the capability, not its activation.

**Prerequisites.** Phase 0 complete (confirms `NavigationState` and the pathfinding engine are present and unmodified).

**Components.** Module 9, Module 5.

**Dependencies.**
- Internal: none from this roadmap.
- External: `NavigationState` (read-only, exactly once per session); the existing pathfinding engine (consumed, unmodified).

**Deliverables.** A working adapter that produces exactly one snapshot per session start; a Route/Path representation consumable by later modules without any of them needing to know `NavigationState` exists.

**Validation Criteria.**
- `NavigationState` is read exactly once per session start, verified by instrumentation or test double — never re-read afterward by this phase's code.
- The produced route representation is verified equivalent to what the existing pathfinding engine returned for a known start/destination pair.

**Exit Criteria.** Adapter and Route/Path Layer both pass their validation criteria in isolation (no ARCore session required to test this phase).

**Risks.**
- *Risk:* other, unrelated parts of the existing application mutate `NavigationState` between the adapter's read and the rest of the session's lifetime, in ways this subsystem does not expect. *Mitigation:* this is exactly why the architecture mandates a one-time snapshot rather than a live read — validate that the snapshot, once taken, is provably immutable for the rest of the session regardless of what happens to the original `NavigationState` afterward.

---

## Phase 3 — Sensor Fusion Boundary (Module 3)

**Objective.** Implement Module 3 as a listener-free consumer of `NavigationSessionManager`'s existing `StateFlow<NavSessionState>`, producing a corroboration signal and a sensor-staleness flag.

**Why This Phase Exists.** Like Phase 2, this has no ARCore dependency and can be fully built and validated in isolation. Doing it now — before Module 8 exists to consume its output — proves the "zero new sensor listeners" constraint early, when a violation is cheap to catch and correct.

**Scope.**
- Included: subscribing to the existing `StateFlow<NavSessionState>`; deriving a relative-displacement estimate and a corroboration signal from it; the staleness heuristic (flagging a reading that hasn't changed across the defined window despite other signals indicating motion).
- Excluded: any consumption of this module's output (Module 8, Phase 8, and Module 6's tracking-loss fallback path, Phase 6/8) — this phase produces the signal, it does not yet have a consumer.

**Prerequisites.** Phase 0 complete (confirms `NavigationSessionManager` is present and unmodified).

**Components.** Module 3.

**Dependencies.**
- Internal: none from this roadmap.
- External: `NavigationSessionManager` and its existing collaborators (read-only).

**Deliverables.** A working Module 3 producing a corroboration signal and a staleness flag from live application data, with zero new `SensorEventListener` registrations anywhere in its implementation.

**Validation Criteria.**
- A static analysis or manual audit confirms no new sensor listener is registered by this phase's code.
- The staleness heuristic correctly flags a synthetically frozen input within the defined window during confirmed motion, and correctly does not flag a normally-varying input.

**Exit Criteria.** Module 3 passes both validation criteria in isolation.

**Risks.**
- *Risk:* `NavigationSessionManager`'s existing `StateFlow<NavSessionState>` does not update at a frequency sufficient for a meaningful corroboration comparison. *Mitigation:* if discovered, this is a data-availability gap against the frozen specification's assumption, not a reason to add a new listener — escalate rather than working around it by duplicating sensor access.

---

## Phase 4 — ARCore Session and Minimal Rendering Validation

**Objective.** Initialize the ARCore session inside the existing navigation screen; host a minimal ArSceneView surface in the position previously occupied by the deprecated overlay pipeline; validate the camera-lifecycle handoff from the existing pre-navigation scan flow; render a single trivial, static, world-locked test object.

**Why This Phase Exists.** This is the highest-risk, most novel integration point in the entire subsystem — SceneView hosted inside the existing Compose navigation screen, and a clean, non-conflicting camera handoff from the existing CameraX-based scan flow, had not been proven in the live application prior to this roadmap. Isolating this risk behind a trivial rendering target (one static object, not real path data) means that if this phase reveals a problem, it is cheap and fast to diagnose, because nothing else has been built on top of it yet. This phase deliberately does not wait for Module 4, 5, or 6 to exist — it validates the rendering/camera plumbing on its own.

**Scope.**
- Included: ARCore session initialization and teardown, positioned correctly relative to the existing scan flow's camera session (sequential, never concurrent); ArSceneView hosted via Compose interop in place of the previously deprecated `CameraOverlayView`; a single static test anchor, placed once, used only to visually confirm world-locking behaves correctly as the device moves.
- Excluded: any real, path-derived anchor; any correction logic; any Module 4, 5, 6, or 8 behavior; plane-detection-driven floor height (a fixed test height is acceptable for this phase's narrow purpose).

**Prerequisites.** Phase 0 complete (manifest and dependencies confirmed).

**Components.** Module 2; a minimal subset of Module 7 (rendering surface hosting only, not its full visual/smoothing feature set).

**Dependencies.**
- Internal: none from this roadmap (does not require Phases 1–3 to be complete).
- External: ARCore SDK; SceneView/Filament; the existing pre-navigation scan flow's camera session (for the handoff-sequencing validation specifically).

**Deliverables.** A live ARCore session running inside the existing navigation screen; a visually confirmed world-locked static object; a confirmed, non-conflicting camera-session handoff from the existing scan flow.

**Validation Criteria.**
- The ARCore session initializes successfully every time the existing scan flow's camera session has fully released beforehand — no camera-conflict error is observed across repeated manual or instrumented runs.
- The static test object remains visually stable at its real-world location as the device is moved and rotated (a direct, observable check of world-locking, independent of any path/route logic).
- The previously deprecated overlay pipeline's hosting slot in the existing screen is now occupied by ArSceneView; the deprecated pipeline is no longer instantiated in the live navigation flow (its source files may still exist in the repository at this point — see Phase 9 for removal).

**Exit Criteria.** Camera handoff and world-locking both pass validation on at least one representative physical device, not solely in an emulator.

**Risks.**
- *Risk:* SceneView-in-Compose hosting or the ARCore/CameraX handoff proves materially harder than assumed, on par with the exact integration risk the architecture review flagged. *Mitigation:* this is precisely why this phase exists this early and in isolation — a difficulty discovered here blocks only this phase, not five phases' worth of accumulated work. If a genuine architectural gap is found (not merely an implementation difficulty), escalate per the Implementation Rules rather than improvising a different integration pattern than the one the Engineering Specification defines.
- *Risk:* device-specific ARCore behavior differs meaningfully across the hardware the validation is performed on. *Mitigation:* this phase's exit criteria require at least one physical device; broader device-tier validation is deferred to Phase 9, where it belongs alongside the rest of the hardening work.

---

## Phase 5 — Localization Layer

**Objective.** Implement the facility transform, initial-fix consumption from the existing scan flow, the proximity-gated and throttled periodic re-fix trigger, fix-confidence tiering, and the Fix Validation Gate.

**Why This Phase Exists.** This is the first phase where the subsystem's actual localization intelligence exists. It requires Module 2 (for camera image access and pose, Phase 4) and Module 5 (for route/node metadata to gate proximity against, Phase 2) to already exist, and requires Phase 1's `landmarkCount` field for tiering. It is sequenced before Module 6 so that the transform's correctness can be validated on its own — inspected directly — before any anchor ever moves on screen because of it, isolating localization correctness from rendering correctness.

**Scope.**
- Included: the facility transform and its exclusive ownership by this module; consuming the existing scan flow's initial fix; the proximity gate and throttle for periodic re-fixes; recognition invocation via Module 2's camera image access; fix-confidence tiering keyed on `landmarkCount`; the three-part Fix Validation Gate (displacement plausibility, graph plausibility, tier-appropriate tolerance); tracking-origin rebasing on every accepted fix.
- Excluded: any anchor creation, correction rendering, or visible response to a fix — Module 6 does not yet consume this module's output.

**Prerequisites.** Phase 1 (landmark count), Phase 2 (route/node metadata, snapshot), Phase 4 (ARCore pose and camera image access) all complete.

**Components.** Module 4.

**Dependencies.**
- Internal: Modules 2, 5 (Phases 4, 2); the `landmarkCount` field (Phase 1).
- External: the existing recognition pipeline; existing corridor-snapping/graph-plausibility logic already used elsewhere in the application's positioning code.

**Deliverables.** A Module 4 implementation that produces validated facility-transform updates, inspectable via logging/instrumentation, without yet driving any visible rendering.

**Validation Criteria.**
- A candidate fix implying an implausible displacement is verifiably rejected and never reaches the transform.
- A single-landmark fix is verifiably tagged provisional and a multi-landmark fix confirmed, with measurably different tolerance/trust-window behavior between the two.
- No recognition attempt occurs outside the proximity gate or faster than the throttle interval, verified by instrumentation over a test session.

**Exit Criteria.** All three validation criteria pass using logged/inspected transform state — visual confirmation is not required at this phase, since Module 6 does not yet exist to render it.

**Risks.**
- *Risk:* the proximity gate's target radius proves too narrow or too wide against real facility landmark density, discovered only once real-world testing begins. *Mitigation:* this is a parameter-tuning concern within the range the Engineering Specification already states (a target, not a fixed constant) — adjusting it within that stated range is not a new architectural decision; adjusting the underlying trigger *model* (e.g., reverting to vision-triggered detection) would be, and must be escalated rather than implemented directly.

---

## Phase 6 — Anchor Management: Core Lifecycle

**Objective.** Implement the sliding-window anchor lifecycle (creation, spawn/despawn fade, pruning), tracking-origin rebasing consumption, smoothed correction application, and turn-angle-based anchor-type selection — consuming Module 4's validated transform and Module 5's route, but not yet the deviation/rebuild path.

**Why This Phase Exists.** This is the first phase where real, path-derived AR guidance becomes visible on screen — the core "does this feel world-locked and smooth" validation point for the whole subsystem. Deviation/rebuild logic is deliberately excluded here and deferred to Phase 8, because it depends on Module 8's classification, which does not yet exist; narrowing this phase's scope to drift-only correction keeps its validation surface focused and its risk isolated from Module 8's supervisory complexity.

**Scope.**
- Included: the bounded sliding window (target size range as specified); fade-in/fade-out spawn and despawn transitions; tracking-origin rebasing applied at every accepted fix from Module 4; smoothed (multi-frame interpolated) correction application; selection of a turn-marker-type anchor at nodes exceeding the defined turn-angle threshold versus a standard chevron-type anchor elsewhere.
- Excluded: deviation classification and route-rebuild execution (Phase 8); Transition Mode and Arrival anchor states (Phase 8, since both are triggered by Module 8).

**Prerequisites.** Phase 4 (ARCore pose, anchor primitives), Phase 5 (validated transform) complete.

**Components.** Module 6 (correction path only).

**Dependencies.**
- Internal: Modules 2, 4, 5 (Phases 4, 5, 2).
- External: ARCore's anchor lifecycle primitives.

**Deliverables.** A working sliding-window anchor system, visibly rendering real path-derived content (via Phase 4's minimal rendering surface, extended here to consume real anchor data instead of the Phase 4 static test object) that moves and corrects smoothly as a live localization session runs.

**Validation Criteria.**
- The active anchor count never exceeds the specified window bound, verified by instrumentation.
- A correction is observably applied over more than one frame — never as an instantaneous change to a previously-rendered anchor pose.
- A node exceeding the turn-angle threshold visibly produces a distinct anchor type from a standard path segment.

**Exit Criteria.** All three validation criteria confirmed via a live, on-device localization-and-tracking session — this is the first phase requiring end-to-end, on-device validation rather than isolated/logged inspection.

**Risks.**
- *Risk:* correction smoothing that looks acceptable in isolated testing feels jarring or too slow in real walking conditions. *Mitigation:* tune the interpolation duration within the range the specification allows; if the specification's own bound proves insufficient in either direction, this is a specification-completeness finding to escalate, not a reason to redesign the smoothing mechanism independently.

---

## Phase 7 — Rendering Layer: Full Fidelity

**Objective.** Complete Module 7 beyond Phase 4's trivial proof: full chevron/arrow and turn-marker visual treatment for Module 6's real anchor content, render-level pose-noise smoothing, and the floor-plane-confidence fallback for unstable (e.g., reflective) floor readings.

**Why This Phase Exists.** This phase depends on Module 6 (Phase 6) producing real, varied anchor content — building full rendering fidelity any earlier would mean rendering against fabricated or stub data, which risks masking integration bugs between Module 6 and Module 7 rather than catching them. Sequencing it immediately after Phase 6 means rendering polish is validated against the real anchor lifecycle it will ship with, not a simplified stand-in.

**Scope.**
- Included: full chevron and turn-marker visual rendering; render-level pose-noise smoothing (distinct from Module 6's correction smoothing); floor-plane-confidence monitoring and the fixed-height-offset fallback.
- Excluded: Transition Mode's simplified rendering and the Arrival visual state — both are triggered by Module 8, not yet implemented.

**Prerequisites.** Phase 6 complete.

**Components.** Module 7 (complete, excluding Transition Mode and Arrival rendering).

**Dependencies.**
- Internal: Module 6 (Phase 6).
- External: none beyond what Phase 4 already established.

**Deliverables.** A visually complete AR guidance rendering, matching the architecture's world-locked, floor-attached, Live-View-style intent, exercised against real anchor data from a live session.

**Validation Criteria.**
- Render-level pose smoothing measurably reduces frame-to-frame anchor-position variance relative to unfiltered pose output, verified by comparison on identical recorded/replayed motion.
- The floor-plane-confidence fallback is observed to engage when plane detection is deliberately destabilized (e.g., a reflective test surface) and produces stable rendering height in that condition.

**Exit Criteria.** Both validation criteria confirmed on at least one physical device under both a normal and a deliberately destabilized floor-plane condition.

**Risks.**
- *Risk:* the render-level smoothing filter and Module 6's correction smoothing interact in an unintended way (e.g., compounding into visible lag). *Mitigation:* validate the two mechanisms both individually and together in this phase specifically, since they are architecturally distinct but visually adjacent — a defect here is an implementation bug in one of the two filters, not evidence the architectural separation between them was wrong.

---

## Phase 8 — Drift & Recovery Layer + Deviation / Transition / Arrival Integration

**Objective.** Implement Module 8's classification (drift vs. deviation, reusing `DriftMonitor`'s raw measurement), the interruption grace-window policy, environmental-condition-aware guidance selection, and the transition-mode and arrival triggers — wiring its instructions into Module 6 (route rebuild) and Module 4 (re-fix requests), and completing Module 7's Transition Mode and Arrival rendering.

**Why This Phase Exists.** Module 8 supervises across Modules 2, 3, 4, and 6 simultaneously, so it is the last piece of core functionality to implement — every module it supervises must already exist and be individually validated, or Module 8's own testing would be conflated with subordinate-module defects rather than isolated to supervisory logic itself. This is also the phase where the full runtime state machine becomes real and testable end-to-end for the first time.

**Scope.**
- Included: drift-vs-deviation classification against `DriftMonitor`'s reused divergence measurement; the interruption grace-window state transitions (`Interrupted: Grace` / `Interrupted: Full`); environmental-condition-aware recovery guidance selection; Transition Mode triggering on floor-change-node approach and its exit condition; Arrival triggering on destination-proximity satisfaction; explicitly disabling `DriftMonitor`'s own relocalization-callback path for the duration of an active AR session; wiring Module 8's instructions into Module 4 (re-fix requests) and Module 6 (correction vs. rebuild execution); completing Module 7's Transition Mode and Arrival visual states.
- Excluded: nothing further within core functional scope — this phase completes the module set defined by the Engineering Specification.

**Prerequisites.** Phases 3 (corroboration signal), 5 (fix history), 6 (anchor rebuild execution target), 7 (rendering states to trigger) all complete.

**Components.** Module 8; the remaining portions of Modules 4, 6, and 7 that depend on Module 8's instructions.

**Dependencies.**
- Internal: Modules 2, 3, 4, 6, 7 (Phases 3–7).
- External: `DriftMonitor` (its raw divergence output, reused; its own callback path explicitly disabled for AR sessions).

**Deliverables.** A fully wired, end-to-end runtime state machine matching the Engineering Specification's state table in full, including drift correction, deviation-triggered rebuild, floor transitions, interruption recovery, and arrival.

**Validation Criteria.**
- Every state and every transition in the Engineering Specification's runtime state machine is exercised at least once in a controlled test session, with the observed behavior matching the specified entry/exit/transition conditions.
- `DriftMonitor`'s own relocalization-callback path is verifiably not invoked during an active AR session (confirmed via instrumentation, not by inspection alone).
- A deviation event and a drift event, triggered under controlled conditions, produce visually and temporally distinguishable outcomes (a visible rebuild versus an invisible smoothed correction).

**Exit Criteria.** Full state-machine coverage confirmed; no unclassified divergence event observed across the controlled test session.

**Risks.**
- *Risk:* the drift-vs-deviation classification's boundary conditions (the lateral-bound and dwell-time thresholds) misclassify real-world scenarios at their edges. *Mitigation:* tune within the specified target ranges; a misclassification pattern that cannot be resolved within those ranges is a specification-completeness finding to escalate, not a reason to alter the classification rule's logic independently.
- *Risk:* supervisory logic spanning four other modules proves harder to test exhaustively than a single-module phase. *Mitigation:* this is precisely why every module it supervises was fully validated in isolation first (Phases 3–7) — Phase 8's testing burden is scoped to the classification and instruction-issuing logic itself, not to re-validating its subordinates.

---

## Phase 9 — Full System Integration, Hardening, and Failure-Scenario Validation

**Objective.** Exercise the complete, assembled subsystem end-to-end against every named failure scenario in the Engineering Specification, across the device-tier parameter model, and remove the previously deprecated overlay pipeline from the codebase entirely.

**Why This Phase Exists.** This phase adds no new module capability — every module already exists and has been individually validated by Phase 8's completion. Its purpose is cross-module, scenario-driven validation (a real floor transition, a real tracking-loss-and-recovery cycle, a real deviation followed by a real re-route, in combination rather than isolation) and final cleanup, which is only meaningful once nothing further is being actively built underneath it.

**Scope.**
- Included: exercising every failure scenario defined in the Engineering Specification (tracking loss, localization failure, camera interruption, sensor inconsistency, user deviation, route recalculation, session restart, each named environmental condition); device-tier parameter validation across representative standard- and constrained-tier hardware; removal of the previously deprecated overlay pipeline's source files, now that its live usage has been fully superseded since Phase 4 and no rollback dependency on it remains; a final audit against every acceptance criterion in the Engineering Specification.
- Excluded: any new module functionality — none is added in this phase.

**Prerequisites.** Phase 8 complete.

**Components.** All modules, exercised together rather than individually.

**Dependencies.**
- Internal: every prior phase.
- External: representative physical test devices spanning the standard/constrained tier split.

**Deliverables.** A subsystem that passes every failure scenario defined in the Engineering Specification, a codebase with the deprecated overlay pipeline fully removed, and a completed cross-reference confirming every acceptance criterion in the Engineering Specification is met.

**Validation Criteria.**
- Every failure scenario in the Engineering Specification's failure-handling table produces its specified required behavior, observed directly, not inferred.
- No reference to the deprecated overlay pipeline remains anywhere in the codebase.
- Every acceptance criterion listed in the Engineering Specification is independently confirmed and recorded.

**Exit Criteria.** All validation criteria met; the Engineering Specification's Implementation Readiness Checklist items are all independently reconfirmed against the finished implementation, not merely against the specification's own prose.

**Risks.**
- *Risk:* a compound scenario (e.g., a floor transition occurring during a drift event) reveals an interaction the specification did not anticipate. *Mitigation:* if the specification does not define a required behavior for a genuinely novel combination, this is an architectural gap to escalate, not a scenario to resolve with an invented behavior — see Implementation Rules.
- *Risk:* device-tier validation surfaces that the coarse two-tier model is insufficient for the actual device spread encountered. *Mitigation:* this is explicitly named in the frozen architecture as a future extension point (finer-grained device-tier tuning); it does not block this phase's completion against the current two-tier model, and any decision to move beyond it is out of this roadmap's scope.

---

## Integration Strategy

- **Phases 0–3 are independent of one another** and may be reordered or parallelized across multiple engineers without violating this roadmap, since none of them depends on ARCore, rendering, or each other's output. The sequential numbering above reflects a safe default ordering (cheapest and lowest-risk first), not a hard requirement among these four.
- **Phase 4 is deliberately pulled forward** ahead of Modules 4, 5 (functionally), 6, and 8, specifically because it is the highest-integration-risk work in the subsystem. It is validated against a trivial rendering target so that a failure here is isolated and cheap to diagnose, rather than discovered only after four phases of dependent work have already been built on top of an unproven foundation.
- **No component is integrated into the live, user-facing navigation flow before its own phase's validation criteria are met.** The existing overlay pipeline's hosting slot is replaced in Phase 4, but the overlay pipeline's source is not deleted until Phase 9 — preserving a rollback path during every phase where the new rendering path is still being built out, without leaving stale, conflicting rendering code active in the live flow at any point after Phase 4.
- **Module 8, the most cross-cutting component, is integrated last (Phase 8)** and only after every module it supervises (2, 3, 4, 6, 7) has already been individually validated — this avoids the specific failure mode the architecture reviews warned about: supervisory logic tested against still-changing subordinate modules produces ambiguous, hard-to-attribute test failures.
- **The pre-navigation scan flow (`LogoScanScreen`) is never modified by this roadmap.** It is treated as a stable, external dependency from Phase 0 onward; no phase includes work on it.

---

## Build Strategy

- **Continuous compilation.** The project must build successfully after every phase, with no exceptions. A phase is not complete if it leaves the project in a non-building state, even temporarily.
- **Incremental integration.** Each phase's deliverable is integrated into the shared codebase as a complete, working unit at the end of that phase — not as a partial, in-progress change spanning multiple phases. Phases 0–3 in particular should each land as an independently mergeable, independently buildable change.
- **Dependency management.** No phase introduces a dependency (library, SDK, existing-system integration) beyond what its own Dependencies section lists. If a phase appears to require a dependency not already named in the Engineering Specification, that is a signal to escalate, not to add it silently.
- **Regression prevention.** Every phase's validation criteria must be re-confirmed (not merely assumed to still hold) before the next phase begins, since later phases build directly on earlier ones' correctness. Phase 1's explicit requirement that the existing pre-AR localization flow remains regression-free is the template for this: any phase touching or depending on existing application behavior must positively reconfirm that behavior is unchanged, not just assume it.

---

## Testing Strategy

| Phase | Test Now | Do Not Yet Test | Success Indicator |
|---|---|---|---|
| 0 | Manifest consistency; existing-dependency presence | Any subsystem behavior | Clean build; checklist complete |
| 1 | `landmarkCount` correctness; existing scan-flow regression | Fix-confidence tiering (no consumer yet) | Field populated correctly; zero regression in existing flow |
| 2 | Single-read guarantee on `NavigationState`; route-data equivalence to the existing pathfinding engine's output | Any AR/rendering behavior | Snapshot immutability and route equivalence confirmed |
| 3 | Zero-new-listener guarantee; staleness heuristic correctness | Consumption of the corroboration signal (no consumer yet) | Static/manual audit clean; staleness detection correct on synthetic input |
| 4 | Camera-session handoff; world-locking of a static test object | Any path-derived rendering, correction, or classification | Stable static object across device movement; no camera conflict, on physical hardware |
| 5 | Fix Validation Gate rejection behavior; provisional/confirmed tiering; proximity-gate/throttle correctness | Any visible rendering response to a fix | Logged/inspected transform state matches every validation criterion |
| 6 | Anchor window bound; correction smoothing (multi-frame, never instant); turn-marker selection | Deviation/rebuild; Transition Mode; Arrival | On-device visual confirmation of smooth, bounded, real anchor behavior |
| 7 | Render-level smoothing effect; floor-plane-confidence fallback under a destabilized surface | Transition Mode and Arrival rendering (no trigger yet) | Measurable variance reduction; stable rendering under a reflective test condition |
| 8 | Full state-machine coverage; drift-vs-deviation distinguishability; `DriftMonitor` callback disablement | Nothing deferred — this phase completes core functional scope | Every state/transition exercised; no unclassified divergence event |
| 9 | Every named failure scenario; device-tier spread; complete deprecated-code removal | Nothing — this is the final validation phase | Every Engineering Specification acceptance criterion independently reconfirmed |

---

## Implementation Rules

1. **Never modify the frozen architecture or engineering specification.** If either document appears wrong, incomplete, or ambiguous during implementation, that is an escalation, not an invitation to reinterpret it locally.
2. **Never combine multiple phases into one unit of work.** Each phase lands as its own complete, independently validated change.
3. **The project must compile after every completed phase**, with no exceptions for "temporary" broken states.
4. **No phase may begin until its prerequisite phases have met their exit criteria.** Prerequisites are not a formality — a phase started early against an unvalidated dependency inherits that dependency's unverified risk.
5. **Every phase must satisfy its stated validation criteria before the next phase begins.** Validation criteria are objective and observable; "it seems to work" is not sufficient evidence of phase completion.
6. **If implementation requires a new architectural decision, stop and escalate rather than inventing a solution.** This applies equally to a genuine specification gap and to a case where the specified approach turns out to be harder to implement than expected — difficulty is not, by itself, grounds for a local design change.
7. **Keep changes isolated to the current phase's declared scope.** A change that happens to touch code outside the current phase's Components list should be treated as evidence the phase boundary needs review, not as an opportunity to make an unplanned improvement while already in that code.
8. **Do not modify any existing system this roadmap treats as a dependency** (the pathfinding engine, `NavigationSessionManager` and its collaborators, `DriftMonitor`'s core logic, the pre-navigation scan flow, `NavigationState`'s structure) beyond the single additive change explicitly scoped in Phase 1.
9. **Do not delete the deprecated overlay pipeline before Phase 9.** Its live usage is superseded in Phase 4, but its source remains as a rollback reference until the subsystem it replaces has been fully validated end-to-end.
10. **Tuning a numeric parameter within the range the Engineering Specification states is not an architectural decision; changing the underlying mechanism a parameter belongs to is.** When in doubt about which category a change falls into, treat it as the latter and escalate.
11. **A phase's validation criteria must be re-confirmed, not assumed, before building on top of it** — see Build Strategy's regression-prevention rule.
12. **No phase's implementation may depend on a dependency not listed in that phase's own Dependencies section.** An unlisted dependency discovered mid-implementation is escalated, not silently added.
