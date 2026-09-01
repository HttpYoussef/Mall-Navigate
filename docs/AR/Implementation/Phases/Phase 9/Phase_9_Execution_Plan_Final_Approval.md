# Phase 9 Execution Plan Final Approval — MallAR AR Subsystem

**Subject:** `Phase_9_Execution_Plan_v3.md`
**Compared against:** `Phase_9_Execution_Plan_Review.md`.

---

## Verification

**Correction 1 (ELEVATOR/STAIRS collapse) — resolved.** `NavigationTurnDirection` now has explicit `ELEVATOR`/`STAIRS` cases with distinct English/Arabic voice cues, not folded into `STRAIGHT`.

**Correction 2 (dead `detect()` code) — resolved.** Call site shown directly in `ArSceneViewWrapper.kt`: `DeviceTier.detect(context)` feeding `AnchorWindowConfig.forTier(...)` at live construction, not just defined in isolation.

**Correction 3 (device-level Reliability evidence) — resolved as a commitment, with one thing flagged for the completion report, not for this approval.** No real constrained-tier hardware exists yet in this project — "Physical/Throttled AVD test device" is a placeholder. This is acceptable at the planning stage. The Phase 9 completion report must identify the actual device or throttled configuration used, with real evidence, not restate "dual-tier confirmed" without naming what was tested — flagged now so it isn't a fourth correction round later.

---

## Final Decision

**APPROVED**

**May Gemini begin Phase 9 implementation? YES**

**Phase 9 execution is authorized.**

This is the final roadmap phase and formally closes Final System Acceptance. The deferred mall-testing risk, recorded at Phase 8 acceptance and restated in this plan's §2, remains outstanding and unresolved by this approval — it is a decision the reviewer has made knowingly, not a gap this phase is expected to close.
