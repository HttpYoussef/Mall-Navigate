# Phase 8 Execution Plan v2 Review — MallAR AR Subsystem

**Subject:** `Phase_8_Execution_Plan_v2.md`
**Compared against:** `Phase_8_Execution_Plan_Review.md`.

---

## Verification

**Correction 1 (numeric range) — resolved.** §3.1 states 2.5m explicitly as chosen midpoint within the frozen 2.0–3.0m range.

**Correction 2 (`pixelsPerMeter` magic number) — resolved.** Shown constructor sources from `NavConfig.PIXELS_PER_METER.toDouble()`.

**Correction 3 (reversal-trigger interaction/disagreement) — NOT resolved.** Prose (§3.3, step 4) describes two required conditions (heading ≥120° AND distance-trend increasing) with an explicit disagreement guard. The shown `checkRouteReversal()` implementation (lines 324–348) contains no heading/angle check at all — it triggers purely on `distToNextPx` increasing across 3 consecutive frames. The described disagreement guard cannot exist in code that only evaluates one of the two conditions. Test 4 (`routeReversal_sharpLateralCut_classifiedAsStandardDeviationNotSpuriousReversal`) as named would not actually exercise a heading/distance disagreement, since there is no heading input to disagree with.

This is not a documentation gap — it's a mismatch between the plan's own stated design and its own shown implementation. Must be reconciled: either add the heading condition to the code, or state plainly that distance-trend alone is the sole reversal signal and remove the disagreement-guard language from §3.3 and the misleading test name.

---

## Final Decision

**APPROVED WITH REQUIRED CHANGES**

**May Gemini begin Phase 8 implementation? NO**

Correction: make `checkRouteReversal()` match its own described logic — implement the heading check, or remove the claim that one exists.
