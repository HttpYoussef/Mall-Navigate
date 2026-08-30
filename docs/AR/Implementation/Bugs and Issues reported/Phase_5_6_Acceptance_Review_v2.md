# Phase 5 & 6 Acceptance Review — Revised — MallAR AR Subsystem

**Subject artifacts:** `phase_5_execution_plan.md`, `phase_5_implementation_report.md` (with source excerpts), `phase_6_implementation_report.md` (with source excerpts), `phase_6_revision_log.md`.
**Compared against:** `Phase_5_6_Acceptance_Review.md` (prior rejection, five findings).

---

## Finding-by-Finding Verification

**Finding 1 — No source code shown for either phase.**
**Partially resolved, and I want to be precise about the boundary.** Both reports now include real excerpts, not full files. What's shown is enough to independently verify specific claims:

- `ReFixScheduler.tryStart()` — traced directly: checks proximity, checks throttle via `lastAttemptMs`, then gates entry with `inFlight.compareAndSet(false, true)` — a genuine atomic single-flight guard, not a cosmetic one. `finish()` releases it. This is correct, minimal, and does what the report claims.
- The stress test launches 8 threads against one scheduler and asserts `successes == 1`. That's a real, meaningful concurrency test — `compareAndSet` is exactly the right primitive for the property being tested, and the test is structured to actually exercise contention rather than just call the method sequentially eight times.
- `ArSceneViewWrapper`'s periodic-refix integration snippet shows the image being copied and released (`sessionManager.copyCameraImage(frame)`) with a cancellation path if the copy fails (`localizationLayer.cancelPeriodicRefix()`) — consistent with the claimed "close before async processing, no second camera path."
- `AnchorManagementLayer`'s window-planning snippet is directly checkable against the plan's bounds (10 ahead / 2 trailing, turn classification via `isTurn`) and is a plausible, correct implementation of that logic.
- `ArAnchorRenderer`'s reconciliation snippet correctly gates on `plan.generation != lastPlanGeneration` before reconciling — this is the stale-generation-rejection mechanism the Phase 6 plan required, shown as real code, not asserted.
- `ManagedARSceneView`'s `onFrame`/`onWindowVisibilityChanged` override is the most important snippet in this whole submission, given it's the direct fix for the exact bug class that crashed Phase 4 — traced below in Finding 3.

What remains genuinely unverified: these are excerpts, not complete files. I cannot rule out that surrounding code not shown contradicts what these fragments suggest, and several files (`LocalizationLayer.kt`'s full `FixValidationGate`, `NavigationSessionInputAdapter.kt`, `ArDataModels.kt`, `UnifiedNavigationViewModel.kt` in full) are listed but have no excerpt at all. This is a real, substantial improvement over the prior submission — not yet the "full file" standard every phase since Phase 1's second round has otherwise met. Treated as sufficient for the specific claims excerpted, insufficient as a blanket "everything claimed is verified."

**Finding 2 — No Phase 5 execution plan existed.**
**Fully resolved.** `phase_5_execution_plan.md` is now present, and — checked directly — the implementation report's claims track it closely: the 1-second cadence and 4-second throttle, the single snapshot consumption, the single-flight requirement, and the explicit exclusions (deviation, Transition Mode, Arrival, anchor rendering) are all present in the plan and correctly not exceeded in the report. The single-flight guard, previously flagged as an undisclosed addition, is explicitly named in the plan itself ("Keep recognition single-flight so overlapping background attempts cannot mutate localization state concurrently") — it was planned, not a self-certified afterthought. This resolves that concern too.

**Finding 3 — Phase 6 modifies `ArCoreSessionManager.kt`'s lifecycle semantics again, the file responsible for the Phase 4 crash, with no shown code.**
**Resolved, and independently traced against the original Phase 4 root cause, not just accepted because code now exists.**

The Phase 4 crash was: SceneView's own Choreographer frame callback kept calling `session.update()` after the view was hidden and the camera pipeline had already stopped, because `sceneView.isPaused` and the session's own pause state weren't synchronized against real view-visibility events.

The Phase 6 fix, traced directly:
```kotlin
override fun onWindowVisibilityChanged(visibility: Int) {
    if (visibility != VISIBLE) { isPaused = true; onRenderSurfaceActiveChanged?.invoke(false) }
    super.onWindowVisibilityChanged(visibility)
    if (visibility == VISIBLE) onRenderSurfaceActiveChanged?.invoke(true)
}
```
This sets `isPaused = true` directly inside the visibility callback, before calling `super`, and independently of any `Session.pause()` call happening elsewhere — meaning the frame gate now reacts to the real Android view-visibility signal itself, not to a separately-sequenced lifecycle event that could race against it. This is a more direct fix than the Phase 4 patch was: it removes the dependency on `pause()` being called in the right order relative to view teardown, by making the render-loop gate self-sufficient from the visibility signal alone.

The `ArCoreSessionManager.pause()`/`destroy()` snippet adds `synchronized(lifecycleLock)` and an early-return guard (`if (_lifecycleState.value != LifecycleState.RESUMED) return`), directly preventing the "Duplicate ARCore pause crash" the revision log names — a real, traceable fix for a real, named failure mode, not a vague "hardening" claim.

This is a case where showing the actual code changed the verdict materially: the fix is not just plausible, it's demonstrably a more robust design than what shipped in Phase 4, because it collapses two previously-separate synchronization concerns (view visibility, session lifecycle) into one that can't drift apart.

**Finding 4 — Voice disabled without a confirmed request.**
**Resolved, and the correction itself is a good sign, not just the outcome.** The Phase 6 report now states voice "remains enabled... is not disabled by Phase 6," with the actual wiring shown (`VoiceAssistantManager`, `NavigationSessionVoiceCoordinator`, live navigation voice control all present in `UnifiedNavigationScreen.kt`). The revision log adds direct, honest context: voice was temporarily disabled during the earlier crash investigation — plausible, given isolating variables during a live crash hunt is reasonable practice — and has been restored, with the log explicitly correcting its own prior mischaracterization ("Voice review clarification"). Naming and correcting your own prior report's inaccuracy, rather than quietly changing the claim, is exactly the behavior this project needs more of, not less.

**Finding 5 — Fresh device validation outstanding.**
**Still outstanding, and correctly still marked as such.** Both revision logs leave this unchecked, honestly. This is not treated as a new failure to add to this list — it is the one requirement carried forward unchanged, and it remains the actual gate before either phase is fully accepted.

---

## Phase 5 Status

**Conditionally verified.** Every specific claim checked against its shown excerpt holds up. The plan now exists and the implementation tracks it. The single-flight addition is no longer an undisclosed extension — it was planned. Remaining gap: several claimed files have no excerpt at all (full `FixValidationGate`, `NavigationSessionInputAdapter.kt`, `ArDataModels.kt` changes, `UnifiedNavigationViewModel.kt`).

## Phase 6 Status

**Conditionally verified, code-level.** The two additional native crash fixes are no longer opaque claims — the actual mechanism is shown and independently confirmed to correctly address the named failure modes, including a direct, traceable improvement on the Phase 4 fix. The voice-disabling concern is resolved with an honest correction, not a defensive one. The anchor-management core logic (window planning, generation-gated reconciliation) is shown and correct. Remaining gap: same as Phase 5 — excerpts, not full files, for several listed components, and device validation is still outstanding by the submission's own admission.

---

## Remaining Blockers

1. Fresh, real Galaxy S22 Ultra device validation for the full Phase 6 matrix — world-locking, bounded marker count, correction smoothness, and specifically repeated Camera↔Map↔AR cycling to confirm the `ManagedARSceneView`/`ArCoreSessionManager` fixes hold under real, repeated use, not just the specific crash sequence already seen once.
2. Full (not excerpted) source for `LocalizationLayer.kt`'s complete `FixValidationGate`, `UnifiedNavigationViewModel.kt`, and `ArDataModels.kt`'s Phase 5 additions — the excerpts shown are sufficient to verify the specific mechanisms discussed above, not sufficient to certify nothing outside them is wrong.

---

## Required Corrections

1. Perform and report the outstanding device validation matrix for Phase 6, in your own words, before either phase is treated as fully closed.
2. Provide full source (not excerpts) for the remaining unshown files at the next natural review point — this is not required to block progress on its own, given how much of the higher-risk code is now verified, but should not be allowed to remain permanently unshown either.

---

## Final Recommendation

**ACCEPTED WITH REQUIRED CORRECTIONS**

This is a substantial, verified improvement over the prior submission — four of five original findings are resolved on genuine evidence, including the highest-risk one (the repeat modification to `ArCoreSessionManager.kt`), which was independently traced against the actual Phase 4 root cause and found to be a real, more robust fix. The remaining gap is narrower and lower-risk than what blocked the prior round: full-file coverage for a handful of files, and device confirmation that both submissions themselves already flag as outstanding.

---

## Safe to proceed to the next authorized phase?

**Not yet, but close.** The code-level risk that justified the prior rejection — an unverified repeat change to the exact file responsible for a real production crash — is now resolved on the merits, not just on trust. What remains is the device validation both reports honestly admit is still outstanding. That confirmation, not another documentation cycle, is what should immediately precede authorization to Phase 7.
