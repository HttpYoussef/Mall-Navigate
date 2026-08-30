# Phase 8 Execution Plan Review — MallAR AR Subsystem

**Subject:** `Phase_8_Execution_Plan.md`
**Compared against:** frozen architecture, Engineering Specification §7/§8, `Phase_7_Acceptance_Report.md`.

---

## Assessment

Route-reversal handling is explicitly addressed and correctly attributed to the Phase 7 acceptance carry-forward — this is the right response to that finding. State machine, drift/deviation split, grace window, Transition Mode, Arrival are all present and match the frozen spec's parameters. `DriftMonitor.onRelocalizationNeeded` silencing is correctly scoped per §6.4. Single supervisory decision-maker is preserved; Module 6/7 only execute instructions, don't decide.

Three required corrections.

**1. Numeric mismatch with frozen documents.** Plan uses 2.5m drift-vs-deviation bound and 2.5m arrival radius. Frozen target for both is 2–3m — 2.5m is inside that range, not a violation, but the plan states these as fixed values rather than tunable parameters within the stated range, contrary to how every prior phase has treated these numbers. State explicitly that these are the chosen values within the frozen range, not new constants.

**2. `pixelsPerMeter: Double = 20.0` is a magic number duplicating `NavConfig.PIXELS_PER_METER`, which Phase 7 already sourced from `NavConfig` directly.** Reintroducing a separate hardcoded constant in `DriftRecoverySupervisor` risks the two values silently diverging. Must source from `NavConfig` the same way Phase 6/7 did, or justify why not.

**3. Route-reversal detection is defined with two separate, independently-sufficient triggers (movement-angle ≥120°, OR 3-window increasing distance) with no stated interaction between them and no test for the case where they disagree.** Add a test case where angle indicates reversal but distance-trend doesn't (e.g., a sharp lateral cut, not a true walk-back) to confirm this doesn't false-positive on a legitimate off-route turn that Deviation-by-lateral-bound should classify instead.

---

## Final Decision

**APPROVED WITH REQUIRED CHANGES**

**May Gemini begin Phase 8 implementation? NO**

Corrections: (1) state 2.5m values as chosen-within-range, not new constants; (2) source `pixelsPerMeter` from `NavConfig`; (3) add a disagreement test case for the two reversal triggers.
