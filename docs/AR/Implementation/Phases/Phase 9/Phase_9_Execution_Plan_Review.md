# Phase 9 Execution Plan Review — MallAR AR Subsystem

**Subject:** `Phase_9_Execution_Plan_v2.md`, informed by `Phase_9_Execution_Plan_Debate_and_Refinement.md`.
**Compared against:** frozen Roadmap §Phase 9, Testing & Validation Plan §5/§6/§7/§8/§11, accepted Phase 8 state.

---

## Assessment

The self-debate document's central finding — that blind-deleting `overlay/` would break live `OverlayTurnDirection` callers in the voice subsystem — is a real, valuable catch, and the resulting `NavigationTurnDirection` decoupling is the correct shape of fix: migrate and verify compilation, then delete, not delete-and-hope. Deletion scope (four `overlay/` files, four legacy/prototype screen files) matches exactly what every prior phase has consistently identified as deferred-to-Phase-9. The device-tier factory values match Roadmap §14's stated coarse two-tier model. The scenario test matrix is genuinely comprehensive against §7/§8's named list, with deterministic virtual-time injection correctly proposed for the 20-minute/3-second time-based cases — a direct, well-applied lesson from Phase 8's own review history.

Three required corrections.

**1. `NavigationTurnDirection.fromAStarDirection` maps both `ELEVATOR` and `STAIRS` to `STRAIGHT`.** This discards exactly the distinction Module 8's `TRANSITION_MODE` exists to surface, and voice guidance at a floor transition is a meaningful place for a user to need "take the elevator/stairs" rather than "go straight." Add explicit handling (even if `NavigationTurnDirection` needs an added case, or these are routed differently at the call site) before this is implemented — do not let this ship silently folded into `STRAIGHT`.

**2. `DeviceTier.detect()` is defined but never shown to be called anywhere in the live construction path of `AnchorManagementLayer` or `ArAnchorRenderer`.** As written, this is dead code — the app will run Standard Tier regardless of actual hardware. Either show the call site where a live `AnchorManagementLayer` is constructed using `AnchorWindowConfig.forTier(DeviceTier.detect(context))`, or state plainly that only the config values are being delivered this phase and tier detection wiring is a separate, explicit follow-up — do not let the completion report claim device-tier tuning is "implemented" if only half of it is reachable.

**3. The Final System Acceptance Matrix's Reliability Readiness row cites only the scenario test suite and on-device timing as evidence.** The frozen Testing & Validation Plan §11 requires this category confirmed "on at least one representative device from each of the two defined tiers." A deterministic virtual-time test suite is the right tool for validating the *logic*, but it cannot substitute for the device-level confirmation the frozen document specifically requires for this category — and this is the phase that formally closes Final System Acceptance, so this gap can't be waved through here of all places. State explicitly that Reliability Readiness requires physical confirmation on a constrained-tier device (or a device deliberately throttled/emulated to represent one) in addition to the Standard Tier S22 Ultra, not unit tests alone.

---

## On the Deferred Mall-Testing Risk

Section 2's explicit restatement of the deferred mall-testing risk from Phase 8 is the right way to carry it forward — visible, not buried. This reviewer repeats what was already recorded at Phase 8 acceptance: given Phase 9 formally closes Final System Acceptance, any facility-specific defect found later cannot be cleanly attributed to a single phase once this closure is signed. That risk is unchanged by this plan and remains the reviewer's, not the plan's, to carry.

---

## Final Decision

**APPROVED WITH REQUIRED CHANGES**

**May Gemini begin Phase 9 implementation? NO**

Mandatory corrections: (1) fix or explicitly justify the `ELEVATOR`/`STAIRS` → `STRAIGHT` voice-cue collapse; (2) show `DeviceTier.detect()`'s actual call site or clearly scope down the claim; (3) state explicitly that Reliability Readiness requires physical confirmation on a second, constrained-tier-representative device, not unit tests alone.
