Type: grilling
Status: resolved

## Question

Decide what `FacilityTransform.headingDeg` is actually supposed to mean, and whether correcting it
is a plain implementation fix or trips this subsystem's architecture-review versioning policy.

**The problem, code-grounded**: `FacilityTransform.worldPositionFor()` treats `headingDeg` as the
rotation between facility coordinates and ARCore world coordinates
(`AnchorManagementLayer.kt:154-164`), but:
- every initial AR local pose hardcodes `headingDeg = 0f`
  (`ArSceneViewWrapper.kt:127-131` and `:301-306` — verified directly);
- accepted landmark-recognition fixes copy the recognition heading (`candidate.headingDeg`, a
  facility-facing/compass heading from `PnPSolver.kt:165-168`) straight into the transform
  (`LocalizationLayer.kt:145-152`), with no visible step reconciling it against the ARCore-world
  frame, especially across the CameraX-scan → ARCore-session handoff;
- single-landmark recognition (the tester's exact "esla" case) returns a **null** heading
  (`LocalizationEngine.kt:227-245`), which falls back to the hardcoded `0f`
  (`LocalizationLayer.kt:244-249`) — producing a wrong arrow even when the store match and 2D
  route are correct.

Full detail: [codex-investigation-findings.md § 1](../codex-investigation-findings.md#1-arrow-direction-wrong-or-unstable-while-stationary).

**Decide**:
1. What should `headingDeg` mean going forward — facility-to-ARCore-world rotation, camera/compass
   heading, or something else — and how should the CameraX-scan heading and the ARCore-session
   yaw be reconciled into it?
2. Should a single-landmark fix with no heading be accepted as absolute orientation at all, or
   should it keep the previously-trusted heading / mark orientation provisional until a stronger
   (multi-landmark/PnP) fix arrives?
3. Per `docs/ar/Engineering Architecture and Guidlines/README.md`'s versioning policy: does
   resolving (1) and (2) change a layer's responsibility or an ownership boundary (heading
   authority moving between Localization, the ARCore session, and Sensor Fusion), and therefore
   require the full architecture-review/stress-test cycle before implementation — or can the fix
   land as a correction *within* the existing contract?

Consult `docs/ar/Engineering Architecture and Guidlines/README.md` and
`docs/ar/Implementation/Phases/Phase 9/Final_System_Acceptance_Sign_Off.md` before answering (3).
This ticket only decides; [Ticket 04](04-fix-heading-frame-calibration.md) implements it.

**"No architecture revision required" is an explicitly valid answer to (3)** — don't feel pressure
to find an architectural issue just because this ticket exists. Per the debate on this map
([codex-debate-findings.md, point A](../codex-debate-findings.md)): the hardcoded-zero heading and
the null-heading fallback are implementation defects regardless of what `headingDeg` means: fixing
*those* never requires review. Only decide (3) as "yes, review required" if resolving (1) — the
frame-semantics question — actually moves heading ownership across a layer boundary (Localization
↔ ARCore session ↔ Sensor Fusion), not merely because the code is currently sloppy about it.
Ticket 04's review-gate binds only on that specific outcome.

## Answer

Grilled live with the user (2 rounds). Decisions:

**(1) `headingDeg` semantics — measure, don't assume.** `headingDeg` stays defined as the one-time
rotation between ARCore's world axes and facility axes, applied to raw ARCore displacement in
`AnchorManagementLayer.kt`'s `facilityPosition()`/`worldPositionFor()` (that part of the contract
is architecturally sound — ARCore's world frame doesn't rotate as the phone turns). The bug is that
nothing ever measures the ARCore camera's real yaw at accept-time to reconcile it against the
CameraX-scan compass heading; `ArSceneViewWrapper.kt:127-131`/`:301-306` hardcode it to `0f`
instead. Fix: capture the real ARCore camera yaw at the moment each fix is accepted, and compute
`headingDeg = scanCompassHeading - arCoreYawAtAcceptTime`. Rejected alternative: constraining the
UX so the phone can't rotate between scan and AR-session-start — fragile, silently wrong again the
moment that ordering assumption is violated by an unrelated future change.

**(2) First-fix-of-session with no heading (the "esla" case) — use the existing PROVISIONAL tier,
correctly.** Confirmed in code: `LocalizationLayer.kt:14-21` already defines
`FixConfidenceTier.PROVISIONAL` (assigned via `fromLandmarkCount`: <2 landmarks → `PROVISIONAL`),
and the Fix Validation Gate already applies tighter tolerance to `PROVISIONAL` fixes
(`LocalizationLayer.kt:136`). But `initializeFromScan` (`LocalizationLayer.kt:238-252`, the very
first fix of a session — the only accept-path with no `previous` heading to fall back to) hardcodes
`tier = FixConfidenceTier.CONFIRMED` regardless of actual landmark count. Fix: use
`FixConfidenceTier.fromLandmarkCount(...)` there like every other accept path already does — no new
mechanism needed, just stop bypassing the existing one. (Superseded scope: no extra "more
aggressive next correction" logic is needed beyond this — the existing periodic-refix/correction
pipeline already replaces a provisional transform smoothly once a stronger fix arrives.)

**(3) Architecture-review trigger — confirmed, no architecture revision required.** Both (1) and
(2) land entirely within the existing contract: (1) corrects a computation inside the layers that
already own pose-capture and transform-computation (no ownership reassignment); (2) is *applying*
the Fix Validation Gate's existing, already-approved `PROVISIONAL`-tier policy at a call site that
currently bypasses it, not changing that policy. Per
`docs/ar/Engineering Architecture and Guidlines/README.md:76`'s trigger list (layer responsibility,
ownership boundary, state-machine transition, mandatory policy, architectural numeric parameter) —
none apply. [Ticket 04](04-fix-heading-frame-calibration.md) proceeds straight to implementation.
