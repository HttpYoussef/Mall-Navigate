# AR Testing & Validation Plan — MallAR AR Subsystem

**Status:** Authoritative quality assurance reference. Derived exclusively from `AR_Subsystem_Redesign_Final.md`, `AR_Engineering_Specification.md`, and `AR_Implementation_Roadmap.md`. None of those documents is modified, reinterpreted, or extended here — this plan defines only how their already-frozen requirements are verified.
**Audience:** Senior Android engineers, QA engineers, and AI implementation agents responsible for approving or rejecting phase completion.
**Governing rule:** Where a frozen document specifies a numeric target, that number is the validation target — no substitute number is introduced here. Where a category requested by this plan (e.g., raw memory or battery ceilings) has no numeric target in any frozen document, that gap is stated explicitly as such, not filled in. Inventing a new hard target here would itself be a new engineering decision, which this document is not permitted to make.

---

## 1. Executive Summary

**Purpose.** This document defines how every phase of the AR subsystem implementation is objectively verified, and what conditions must hold before the finished subsystem is considered production-ready. It exists to remove judgment calls from phase approval: a phase either meets its defined, measurable criteria or it does not.

**Role within the implementation lifecycle.** This plan sits alongside, not after, implementation — each roadmap phase is validated using this document as it completes, not retroactively at the end. It is the mechanism by which the Implementation Roadmap's own rule ("every phase must satisfy its stated validation criteria before the next phase begins") is made concrete and checkable, and the final authority for whether the whole subsystem is ready to ship, per §11–§12.

---

## 2. Validation Philosophy

- **A phase is approved only against evidence, never against confidence.** "It appeared to work during manual testing" is not evidence; a recorded, repeatable observation against a stated criterion is.
- **Every criterion in this document traces to a number, a named condition, or a named behavior already present in the frozen architecture, specification, or roadmap.** Where no such number exists, this plan says so explicitly rather than manufacturing one, consistent with the roadmap's own rule against inventing engineering decisions during implementation.
- **A successful implementation is one where every phase's validation criteria pass independently, and the full assembled system additionally passes the cross-cutting scenarios defined in §7–§8 that no single phase could exercise alone.** Passing every phase in isolation is necessary but not sufficient — §9 (Regression Validation) and Phase 9's full-system validation exist precisely because phase-local success does not, by itself, guarantee system-level correctness.
- **Validation is repeatable by construction.** Every procedure in §3 is written so that a different engineer, running it again against the same build, obtains the same pass/fail result. A procedure that depends on who runs it is not an acceptable procedure under this plan.
- **Absence is validated as rigorously as presence.** Where the architecture requires something to *not* happen (no backend call, no duplicate sensor listener, no instantaneous correction snap, no reference to the deprecated overlay pipeline), this plan treats confirming that absence as a first-class validation activity, not an afterthought.

---

## 3. Phase Validation Matrix

Each entry below governs the corresponding Implementation Roadmap phase. Fields not listed here (Objective, Prerequisites, Components, Dependencies, Deliverables, Risks) are owned by the roadmap and are not repeated; this matrix adds exactly what the roadmap does not define — procedure and failure criteria.

### Phase 0 — Environment, Manifest, and Dependency Baseline
- **Validation Objective:** Confirm the manifest and dependency baseline are correct before any subsystem code exists.
- **Components Covered:** Manifest configuration; ARCore/SceneView dependency presence; existing-system presence.
- **Preconditions:** None.
- **Validation Procedure:** (1) Inspect the manifest's AR feature requirement and ARCore optionality declaration together; (2) build the project with no code changes beyond the manifest; (3) confirm each existing dependency system (pathfinding engine, `NavigationSessionManager` and collaborators, `DriftMonitor`, the pre-navigation scan flow, `LocalizationResult`, `NavigationState`) is present at its expected location and compiles.
- **Expected Behaviour:** Manifest declarations are mutually consistent; build succeeds; every listed existing system is present.
- **Acceptance Criteria:** All items in step 3 confirmed present and compiling; build in step 2 succeeds with zero errors or warnings introduced.
- **Failure Criteria:** Any manifest inconsistency remains; the build fails; any listed existing system is missing, relocated unexpectedly, or fails to compile.
- **Exit Criteria:** Matches the roadmap's Phase 0 exit criteria — manifest change merged and built; dependency checklist complete.

### Phase 1 — Data Contract Extension (Recognition Pipeline)
- **Validation Objective:** Confirm the additive `landmarkCount` field is correct and introduces zero regression to the existing localization flow.
- **Components Covered:** `LocalizationResult`; the existing recognition pipeline (read-only with respect to its detection/matching logic).
- **Preconditions:** Phase 0 complete.
- **Validation Procedure:** (1) Execute the existing pre-AR localization flow against a known single-landmark scenario and confirm `landmarkCount == 1`; (2) execute it against a known multi-landmark scenario and confirm `landmarkCount >= 2`; (3) run the existing flow's own pre-existing test/verification process (unchanged) and confirm it passes identically to its pre-change baseline.
- **Expected Behaviour:** The field reflects the pipeline's already-existing internal single/multi-landmark distinction; no other behavior of the existing flow changes.
- **Acceptance Criteria:** Steps 1 and 2 both produce the expected count; step 3 shows zero deviation from the pre-change baseline.
- **Failure Criteria:** The field is populated incorrectly for either scenario, or any existing-flow behavior deviates from its pre-change baseline.
- **Exit Criteria:** Field added, correctly populated, zero regression confirmed.

### Phase 2 — Integration Boundary Foundation (Module 9 + Module 5)
- **Validation Objective:** Confirm the single-read guarantee on `NavigationState` and route-data correctness.
- **Components Covered:** Module 9, Module 5.
- **Preconditions:** Phase 0 complete.
- **Validation Procedure:** (1) Instrument or otherwise observe every read of `NavigationState` across a full simulated session (start → route change → session end) and count them; (2) compare the produced route representation against the existing pathfinding engine's direct output for an identical start/destination pair; (3) mutate `NavigationState` after the snapshot is taken and confirm the subsystem's held route data does not change as a result.
- **Expected Behaviour:** Exactly one read occurs, at session start; the route representation matches the pathfinding engine's output exactly; post-snapshot mutation of `NavigationState` has no effect on already-held data.
- **Acceptance Criteria:** Read count in step 1 equals exactly one; route equivalence in step 2 confirmed; immutability in step 3 confirmed.
- **Failure Criteria:** Any read of `NavigationState` beyond the single session-start read; any divergence from the pathfinding engine's output; any observed effect from post-snapshot mutation.
- **Exit Criteria:** All three validation criteria pass; no ARCore session is required to reach this exit.

### Phase 3 — Sensor Fusion Boundary (Module 3)
- **Validation Objective:** Confirm zero new sensor listener registration and correct staleness detection.
- **Components Covered:** Module 3.
- **Preconditions:** Phase 0 complete.
- **Validation Procedure:** (1) Perform a static code audit (or equivalent instrumentation-based check) confirming no `SensorEventListener`-class registration exists in Module 3's implementation; (2) feed a synthetically frozen input into the consumed `StateFlow<NavSessionState>` stream while other signals indicate motion, and confirm the staleness flag activates within the defined window; (3) confirm the flag does not activate under normal, varying input.
- **Expected Behaviour:** No independent sensor access exists; staleness is detected correctly and only when genuinely present.
- **Acceptance Criteria:** Audit in step 1 finds zero new listeners; step 2 flags within the specified window; step 3 produces no false positive.
- **Failure Criteria:** Any new listener found; staleness not detected within the window, or a false positive under normal variation.
- **Exit Criteria:** All three criteria pass in isolation, without an ARCore session.

### Phase 4 — ARCore Session and Minimal Rendering Validation
- **Validation Objective:** Confirm a clean, non-conflicting camera handoff and correct world-locking of a static test object, on physical hardware.
- **Components Covered:** Module 2; minimal Module 7 (hosting only).
- **Preconditions:** Phase 0 complete.
- **Validation Procedure:** (1) Run the existing pre-navigation scan flow to completion, allow its camera session to fully release, then initialize the ARCore session, repeated across at least ten consecutive runs on at least one physical device; (2) place a single static test object and physically move/rotate the device around the space for a sustained period, observing whether the object's apparent real-world position remains fixed; (3) confirm the previously deprecated overlay pipeline is no longer instantiated anywhere in the live navigation flow.
- **Expected Behaviour:** No camera-conflict error across all ten runs; the test object visually holds its real-world position throughout device movement; the deprecated pipeline is absent from the live flow.
- **Acceptance Criteria:** Zero camera-conflict failures across the ten-run sample; the test object's observed drift from its true position stays visually negligible throughout the movement test; the deprecated pipeline's absence from the live flow is confirmed by code inspection.
- **Failure Criteria:** Any camera-conflict error in the ten-run sample; observable drift of the static object inconsistent with world-locking; the deprecated pipeline still instantiated anywhere in the live flow.
- **Exit Criteria:** Matches the roadmap's Phase 4 exit criteria — both checks pass on at least one physical device, not solely an emulator.

### Phase 5 — Localization Layer
- **Validation Objective:** Confirm the Fix Validation Gate, fix-confidence tiering, and the proximity/throttle trigger all behave exactly as specified, via inspected/logged state.
- **Components Covered:** Module 4.
- **Preconditions:** Phases 1, 2, 4 complete.
- **Validation Procedure:** (1) Construct a candidate fix implying a displacement exceeding the specified plausibility bound relative to elapsed time, and confirm it is rejected before reaching the transform; (2) construct a single-landmark and a separate multi-landmark fix and confirm each is tagged provisional and confirmed respectively, with measurably different tolerance/trust-window behavior; (3) run a live or simulated session and log every recognition attempt's timestamp and the device's position at that moment, confirming every attempt occurred inside the proximity radius and no two attempts occurred closer together than the throttle interval.
- **Expected Behaviour:** Implausible fixes never reach the transform; tiering behaves distinctly by landmark count; every recognition attempt is proximity-gated and throttle-compliant.
- **Acceptance Criteria:** Step 1's fix is confirmed rejected; step 2's tiers are confirmed distinct; step 3's full attempt log shows zero violations of the proximity or throttle bounds.
- **Failure Criteria:** An implausible fix reaches the transform; tiering is not measurably distinct; any logged attempt violates the proximity gate or throttle interval.
- **Exit Criteria:** All three criteria confirmed via inspected/logged transform state; visual rendering confirmation is not required at this phase.

### Phase 6 — Anchor Management: Core Lifecycle
- **Validation Objective:** Confirm the anchor window bound, multi-frame correction smoothing, and turn-marker selection, on-device, against real localization output.
- **Components Covered:** Module 6 (correction path only).
- **Preconditions:** Phases 4, 5 complete.
- **Validation Procedure:** (1) Run a live on-device session and log the active anchor count continuously, confirming it never exceeds the specified window bound; (2) trigger a correction (via a controlled drift scenario) and confirm, via frame-by-frame observation or recorded video, that the correction is applied across more than one frame, never as a single-frame jump; (3) route the session past a node exceeding the specified turn-angle threshold and confirm a visually distinct anchor type renders there compared to a standard path segment.
- **Expected Behaviour:** Anchor count bounded at all times; corrections are smooth, never instantaneous; sharp turns render distinctly.
- **Acceptance Criteria:** Step 1's logged count never exceeds the bound; step 2's correction is confirmed multi-frame; step 3's distinct rendering is confirmed present.
- **Failure Criteria:** Any observed anchor-count excursion beyond the bound; any single-frame correction jump; no visual distinction at a qualifying turn node.
- **Exit Criteria:** All three criteria confirmed via a live, on-device session — the first phase in this roadmap requiring end-to-end, on-device validation.

### Phase 7 — Rendering Layer: Full Fidelity
- **Validation Objective:** Confirm render-level pose smoothing and the floor-plane-confidence fallback, on-device, under both normal and deliberately destabilized conditions.
- **Components Covered:** Module 7 (complete, excluding Transition Mode and Arrival).
- **Preconditions:** Phase 6 complete.
- **Validation Procedure:** (1) Record identical device motion twice, once with render-level smoothing enabled and once with it disabled (or measured against raw pose output as a baseline), and compare frame-to-frame anchor-position variance between the two; (2) present a deliberately reflective or otherwise plane-detection-destabilizing surface to the device and observe whether rendered content height remains stable rather than jittering with the unstable plane estimate.
- **Expected Behaviour:** Smoothing measurably reduces variance relative to the unfiltered baseline; the floor-plane fallback engages and holds a stable render height under the destabilized condition.
- **Acceptance Criteria:** Step 1 shows a measurable variance reduction; step 2 shows stable render height under the destabilized condition, confirmed on at least one physical device.
- **Failure Criteria:** No measurable variance reduction; visible jitter persists under the destabilized floor condition.
- **Exit Criteria:** Both criteria confirmed on physical hardware under both the normal and destabilized condition.

### Phase 8 — Drift & Recovery Layer + Deviation / Transition / Arrival Integration
- **Validation Objective:** Confirm full runtime state-machine coverage, correct drift-vs-deviation distinguishability, and `DriftMonitor` callback disablement.
- **Components Covered:** Module 8; the Module 4/6/7 portions that depend on it.
- **Preconditions:** Phases 3, 5, 6, 7 complete.
- **Validation Procedure:** (1) Walk through every state and every transition defined in the Engineering Specification's runtime state machine, in a controlled test session, recording the observed entry/exit condition against the specified one for each; (2) instrument `DriftMonitor`'s relocalization-callback path and confirm it is never invoked during an active AR session, across the same controlled session; (3) trigger a controlled drift scenario and a separate controlled deviation scenario, and confirm the two produce observably different outcomes — a smoothed, invisible correction for drift versus a visible, deliberate route rebuild for deviation.
- **Expected Behaviour:** Every specified state/transition is reachable and behaves as specified; the legacy callback path is never invoked; drift and deviation are visually and temporally distinguishable.
- **Acceptance Criteria:** 100% of the state machine's defined states and transitions are exercised and match specification; zero invocations of the legacy callback path; drift and deviation outcomes are confirmed distinguishable.
- **Failure Criteria:** Any state or transition unreachable or mismatched against specification; any invocation of the legacy callback path; drift and deviation producing indistinguishable or misclassified outcomes.
- **Exit Criteria:** Full state-machine coverage confirmed; no unclassified divergence event observed across the controlled session.

### Phase 9 — Full System Integration, Hardening, and Failure-Scenario Validation
- **Validation Objective:** Confirm every named failure scenario, device-tier behavior, and complete removal of the deprecated pipeline, at full-system scope.
- **Components Covered:** All modules, exercised together.
- **Preconditions:** Phase 8 complete.
- **Validation Procedure:** Execute each scenario in the Engineering Specification's failure-handling table as a distinct, individually-recorded test case (see §7–§8 below for the full scenario list and expected behavior per scenario); execute the full scenario set against at least one representative standard-tier and one representative constrained-tier device; perform a repository-wide search confirming zero remaining references to the deprecated overlay pipeline; cross-check every acceptance criterion in the Engineering Specification against the finished implementation.
- **Expected Behaviour:** Every scenario produces its specified required behavior on both device tiers; the deprecated pipeline is entirely absent from the codebase; every specification acceptance criterion is independently confirmed.
- **Acceptance Criteria:** 100% of scenarios pass on both device tiers; zero remaining references to the deprecated pipeline; 100% of specification acceptance criteria independently reconfirmed.
- **Failure Criteria:** Any scenario fails on either device tier; any remaining reference to the deprecated pipeline; any specification acceptance criterion not independently confirmable against the finished implementation.
- **Exit Criteria:** Matches §11–§12 below — this phase's completion is equivalent to Final System Acceptance.

---

## 4. Functional Validation

| Feature Area | Measurable Acceptance Criteria |
|---|---|
| **Tracking** | Device pose updates at ARCore's native frame rate with no artificial cap; tracking-quality state transitions are observable within one frame of ARCore reporting them (per Engineering Specification, Module 2 acceptance criteria). |
| **Localization** | No candidate fix reaches the facility transform without passing all three Fix Validation Gate checks; fix-confidence tiering is measurably distinct by landmark count; recognition attempts are 100% proximity-gated and throttle-compliant outside the bounded initial-acquisition window. |
| **Navigation** | The facility-coordinate route representation is equivalent to the existing pathfinding engine's direct output for an identical input; a deviation-triggered recalculation produces a route equivalent to a direct call to the same existing engine with the same current position. |
| **Anchor Management** | Active anchor count never exceeds the specified window bound; every correction is multi-frame, never instantaneous; turn-angle-qualifying nodes render a distinct anchor type 100% of the time. |
| **Rendering** | Render-level smoothing measurably reduces pose-noise variance versus unfiltered output; the floor-plane-confidence fallback engages under a destabilized-plane condition and holds stable render height; the deprecated overlay pipeline is never instantiated in the live flow from Phase 4 onward. |
| **Session Lifecycle** | Every state in the Engineering Specification's runtime state machine is reachable and behaves per its specified entry/exit conditions; a deliberate exit always results in re-entry at `No Fix` on resume, with zero retained state, confirmed by instrumentation. |
| **Recovery** | An interruption resolved within the grace window (~3 s) produces zero fresh-fix requirement; one exceeding it always produces a fresh-fix requirement; three or more consecutive Fix Validation Gate rejections produce a manual-rescan prompt. |
| **User Interaction** | Manual "use the 2D map instead" is available and functional at any point during the initial-acquisition window, not gated behind its timeout; the acquisition window's escalating guidance is observed to change at the specified point within the window. |

---

## 5. Integration Validation

| Integration Point | Expected Behaviour |
|---|---|
| **Navigation Integration** | `NavigationState` is read exactly once per session, exclusively by Module 9; the existing pathfinding engine is invoked for both the initial route and any deviation-triggered recalculation, with no divergence from its own standalone output; `NavigationSessionManager`'s `StateFlow<NavSessionState>` and `DriftMonitor`'s divergence output are consumed read-only, with `DriftMonitor`'s own callback path confirmed disabled during active AR sessions. |
| **Recognition Integration** | The existing recognition pipeline's detection/matching logic is unmodified (confirmed via a diff/code-review check against the pre-implementation baseline); the additive `landmarkCount` field is the pipeline's only observable change; periodic re-fix recognition is served from ARCore's own camera image access, never a second concurrent camera session. |
| **Backend Communication** | **No backend or network dependency exists anywhere in this subsystem.** Validation for this category consists of confirming this absence: a repository-wide search for HTTP/network client usage within the subsystem's implementation returns zero matches, and the subsystem functions correctly with the device's network connectivity disabled throughout a full test session. |
| **UI Integration** | ArSceneView is hosted within the existing navigation screen via standard Compose interop, in the position previously occupied by the deprecated overlay view; Module 8's guidance/state (degraded-confidence indication, environmental prompts, manual-rescan prompt, 2D-map fallback offer, arrival display) is observed to surface correctly to the presented UI at the moment each is triggered. |
| **Sensor Integration** | Zero new `SensorEventListener` registrations exist anywhere in the subsystem's implementation (confirmed by code audit); all IMU-derived signals consumed by Module 3 are verifiably sourced from `NavigationSessionManager`'s existing aggregated stream. |
| **Android Lifecycle Integration** | An OS-level camera interruption (app backgrounded, another app claims the camera, permission revoked) is handled identically to an ARCore-reported tracking interruption of the corresponding duration, per the grace-window policy; a deliberate app exit during AR navigation and a subsequent resume both produce the specified terminal/re-entry behavior with no retained state. |

---

## 6. Performance Validation

| Metric | Frozen Target | Validation Method |
|---|---|---|
| Frame rate | Matches ARCore's native driven rate; no artificial cap | Measure rendered frame rate on-device and confirm it is not capped below ARCore's own reported update rate. |
| Active anchor limit | 8–12 ahead, 2–3 trailing | Continuous instrumentation logging of active anchor count during a live session; confirm the bound is never exceeded. |
| Recognition throttle | Minimum 3–5 s between attempts | Log recognition-attempt timestamps during a live session; confirm no interval falls below the minimum. |
| Proximity check frequency | Every 1–2 s | Log proximity-check timestamps; confirm interval compliance. |
| Initial-fix acquisition window / "startup" (time to first fix) | 15–20 s before fallback is offered | Time the interval from AR-navigation entry to either a successful fix or the fallback offer; confirm it falls within the specified window. |
| Interruption grace window | ~3 s | Time a controlled interruption-and-recovery cycle; confirm the grace/full boundary matches the specified value. |
| Tracking-origin forced-rebase distance | ~15–20 m | Log cumulative uncorroborated VIO travel distance; confirm the forced-priority signal activates within the specified range. |
| Long-session check-in trigger | ~20 min | Run a sustained session and confirm the check-in trigger activates at the specified duration. |
| **Latency (frame-to-render, beyond frame-rate matching)** | **Not independently specified in any frozen document.** | Not validated against a hard millisecond target. Frame-rate compliance (above) is the frozen document's only latency-adjacent requirement; a separate latency ceiling is out of this plan's scope to define. |
| **Memory usage** | **Not independently specified.** | Not validated against a hard ceiling. Memory behavior is bounded indirectly by the anchor-count and recognition-frequency limits above; validation confirms those bounds are respected, not a separately-defined memory number. Any anomalous memory growth observed during testing is an escalation, not a pass/fail criterion under this plan. |
| **CPU usage** | **Not independently specified.** | Same treatment as memory usage — bounded indirectly by the specified behavioral limits, not by an independent CPU percentage target. |
| **GPU usage** | **Not independently specified.** | Same treatment — the frozen documents specify lightweight-geometry rendering intent qualitatively, not a GPU budget in frame-time or utilization terms. |
| **Battery consumption** | **Not independently specified.** | Same treatment — no mAh-per-hour or percentage-per-session target exists in any frozen document; the ~20-minute long-session check-in trigger is the only frozen battery-adjacent behavior, and is validated as a timing behavior (above), not as a consumption figure. |
| **Thermal behaviour** | **Not independently specified; explicitly named as a deferred future extension point in the frozen architecture.** | Not validated against a target in this plan, consistent with the architecture's own explicit deferral. Observed thermal throttling during Phase 9 testing is recorded as a future-extension-point finding, not a pass/fail criterion. |

**Governing note for this section:** the five metrics marked "not independently specified" are requested by this plan's required structure but are not present as hard numeric targets in any frozen document. Defining new hard ceilings for them here would itself be a new engineering decision, which this plan is not authorized to make. Where testing surfaces a real concern in one of these categories, the correct action is escalation (per the roadmap's Implementation Rules), not a locally-invented pass/fail threshold.

---

## 7. Reliability Validation

| Scenario | Validation Approach | Expected Behaviour |
|---|---|---|
| **Long-running sessions** | Run a session past the ~20-minute check-in trigger without interruption. | The check-in trigger activates at the specified duration; no crash, memory-related failure, or tracking-quality collapse is observed attributable to session duration alone. |
| **Tracking stability** | Walk a defined route under normal conditions, logging pose continuity. | No unexplained tracking-quality drop outside the named environmental/motion conditions covered elsewhere in this plan. |
| **Drift behaviour** | Induce a controlled small positional divergence within the lateral bound. | Classified as drift; corrected smoothly, no route rebuild. |
| **Tracking recovery** | Induce a controlled tracking interruption, varying its duration around the ~3 s grace boundary. | Interruptions under the boundary retain state and resume without a fresh fix; interruptions over it require one. |
| **Camera interruption** | Trigger an OS-level camera interruption (another app claims the camera; permission toggled) of varying duration. | Identical behavior to the corresponding-duration tracking interruption, per the grace-window policy. |
| **Sensor inconsistency** | Feed a synthetically frozen sensor input during confirmed motion. | Flagged as stale within the specified window; excluded from the corroboration signal; no independent state transition forced by this condition alone. |
| **User deviation** | Deliberately navigate off the planned route beyond the classification bound. | Classified as deviation; route recalculated via the existing pathfinding engine; visible anchor-window rebuild occurs. |
| **Route recalculation** | Trigger a deviation and observe the resulting recalculated route. | The recalculated route is equivalent to a direct call to the existing pathfinding engine with the same current position and destination. |
| **Session restart** | Deliberately exit AR navigation, then resume. | No state retained; resumption re-enters at `No Fix` / Initialization; behaves identically to a first-time session start. |

---

## 8. Edge Case Validation

| Scenario | Expected Behaviour Per Frozen Documents |
|---|---|
| **Poor lighting** | Handled via ARCore's own tracking-quality/failure-reason reporting feeding standard recovery guidance; no bespoke mechanism exists or is expected beyond this generic path. |
| **Reflective floors** | Module 7's floor-plane-confidence fallback (fixed-height offset) engages when plane detection is repeatedly re-estimating within a short window. |
| **Crowded environments** | Rendering occlusion remains a device-capability-gated enhancement, unaffected by crowd density; tracking-quality degradation from crowd-dominated frames is handled by ARCore's own inherent feature-tracking behavior; recognition attempts are never wasted on crowd-obstructed frames because triggering is proximity-gated, not vision-gated. |
| **Fast movement** | No bespoke mechanism is defined for this condition beyond ARCore's own generic tracking-quality reporting and the standard interruption grace-window/recovery path; validation confirms the generic mechanism holds under this condition, since the frozen documents do not define anything more specific to it. |
| **Rapid device rotation** | Same treatment as fast movement — validated against the generic tracking-quality/interruption mechanism, since no bespoke rotation-specific handling is defined in the frozen documents. |
| **Temporary camera obstruction** | Treated as a tracking interruption; grace-window policy applies exactly as for any other interruption cause. |
| **Tracking loss** | Grace-window policy (~3 s boundary) governs transient-versus-sustained treatment, per §7 above. |
| **Interrupted navigation (app backgrounded mid-session)** | Identical treatment to any other tracking interruption of the corresponding duration. |
| **Invalid localization (a candidate fix that should be rejected)** | Must be caught by the Fix Validation Gate before reaching the transform; three or more consecutive rejections trigger a manual-rescan prompt. |
| **Unexpected Android lifecycle events (permission revoked mid-session, Activity recreated)** | Treated as a camera/tracking interruption of whatever duration the event actually causes; no distinct mechanism beyond the interruption-duration policy is defined or expected. |

**Note on scope:** several scenarios above (fast movement, rapid rotation, temporary obstruction, unexpected lifecycle events) do not have a bespoke, dedicated mechanism in the frozen architecture — they are explicitly handled by the same generic tracking-quality/interruption pathway used for every other cause of degraded tracking. Validating them means confirming that generic pathway holds up under these specific conditions, not discovering or inventing a distinct mechanism that does not exist in the frozen documents.

---

## 9. Regression Validation

**General rule.** On completion of any phase, every prior phase whose output the new phase directly consumes must have its own validation criteria re-confirmed, not merely assumed to still hold — a later phase's implementation can silently invalidate an earlier phase's guarantee (for example, a Phase 8 change to instruction-issuing logic could inadvertently cause Module 6 to violate Phase 6's "never instantaneous correction" criterion).

**Minimum regression set per phase:**

| Completed Phase | Must Re-Confirm |
|---|---|
| Phase 1 | Phase 0 (build health) |
| Phase 2 | Phase 0 |
| Phase 3 | Phase 0 |
| Phase 4 | Phase 0 |
| Phase 5 | Phase 1 (`landmarkCount` correctness), Phase 2 (route data still consumed correctly), Phase 4 (camera handoff still clean now that Module 4 actively uses ARCore's camera image access) |
| Phase 6 | Phase 4 (world-locking still holds now that real, moving anchors exist, not just a static test object), Phase 5 (transform correctness under live anchor consumption) |
| Phase 7 | Phase 6 (anchor window/correction behavior unaffected by full rendering fidelity) |
| Phase 8 | Phase 3 (corroboration signal still correctly gated as non-authoritative), Phase 5 (fix history still correctly feeding classification), Phase 6 (correction-vs-rebuild execution still correctly separated), Phase 7 (rendering states correctly triggered, not just capable of being triggered) |
| Phase 9 | All of Phases 0–8, in full, as the basis for Final System Acceptance |

**Regression evidence standard.** A re-confirmation is not satisfied by "no known issues were reported" — it requires re-executing the original phase's validation procedure (§3) and recording a fresh pass, exactly as when that phase was first completed.

---

## 10. Phase Completion Checklist

**Applies uniformly to every phase (0–9).** A phase is not complete until every item below is checked, with recorded evidence for each — not by assertion.

- [ ] Every item in this phase's Phase Validation Matrix entry (§3) — Validation Procedure executed, Acceptance Criteria met, Failure Criteria absent.
- [ ] Every applicable row in Functional Validation (§4), Integration Validation (§5), Performance Validation (§6), Reliability Validation (§7), and Edge Case Validation (§8) that this phase's Components Covered makes newly testable has been executed and passed.
- [ ] The project builds successfully with zero errors, per the Implementation Roadmap's continuous-compilation rule.
- [ ] No dependency was introduced beyond what this phase's roadmap entry lists.
- [ ] The Regression Validation minimum set for this phase (§9) has been re-confirmed with fresh, recorded evidence.
- [ ] No change was made to the frozen architecture, Engineering Specification, or Implementation Roadmap.
- [ ] No architectural decision was made during this phase's implementation; any encountered ambiguity was escalated, not resolved locally.
- [ ] Engineering Sign-Off (§12) has been obtained.

---

## 11. Final System Acceptance

The subsystem is production-ready only when all five readiness categories below are satisfied, corresponding to Phase 9's completion.

- **Functional Readiness:** Every row in §4 (Functional Validation) passes at full-system scope, not merely within its originating phase in isolation.
- **Performance Readiness:** Every frozen numeric target in §6 is met on-device; every metric marked "not independently specified" has been observed and recorded (even without a pass/fail threshold), so that the absence of a hard target is a documented fact, not an unexamined gap.
- **Reliability Readiness:** Every scenario in §7 passes on at least one representative device from each of the two defined device tiers (standard, constrained).
- **Integration Readiness:** Every row in §5 (Integration Validation) passes, including the confirmed absence of backend/network dependency and the confirmed absence of any remaining reference to the deprecated overlay pipeline.
- **Maintainability Readiness:** Every acceptance criterion in the Engineering Specification (§13 of that document) is independently reconfirmed against the finished implementation; the Engineering Specification's Implementation Readiness Checklist items are all reconfirmed as built, not merely as specified.

**Final System Acceptance is achieved only when all five categories are simultaneously satisfied.** Partial satisfaction (e.g., four of five categories) does not constitute acceptance under this plan.

---

## 12. Engineering Sign-Off Criteria

Sign-off is a binary, evidence-based gate — never a subjective judgment call.

**A phase is approved if and only if:**
1. Every item in that phase's Phase Completion Checklist (§10) is checked, with recorded evidence attached to each item (a log, a measurement, a build artifact, an instrumented count — not a verbal or written assertion alone).
2. No Failure Criteria condition, as defined in that phase's §3 entry, was observed at any point during validation.
3. The Regression Validation minimum set (§9) for that phase shows fresh, passing evidence — not carried-over evidence from an earlier point in development.

**A phase is rejected if any single one of the above is not met.** There is no partial-credit or conditional-approval state under this plan — a rejected phase returns to implementation and is re-submitted for the same sign-off process once corrected; it does not proceed to unblock the next phase in the interim.

**Final System Acceptance (§11) is approved if and only if all five readiness categories are independently confirmed with recorded evidence, using the same evidence standard as individual phase sign-off.** No individual, however senior, approves Final System Acceptance on judgment in place of this evidence — the criteria in §11, fully satisfied and recorded, are the approval.
