# Overlay Investigation — Acceptance and Remediation Authorization

**Subject:** `Phase_4_6_Overlay_Investigation_Report.md`
**Compared against:** `Phase_4_6_Legacy_Overlay_Defect_Report.md` and the actual `UnifiedNavigationScreen.kt` source shown in this report.

---

## Verification

The investigation's central claim is independently confirmed against the actual composition code, not accepted on the report's narrative alone: `ArDirectionOverlay(state = state, alpha = camAlpha)` is composed unconditionally, with only an alpha fade — never gated behind `isCameraMode` the way `ArSceneViewWrapper` is. It has been present, unconditionally, since Phase 4. This directly and completely explains the human reviewer's observation: the arrow and the 3D content were never sequential or conflicting removal states, they were simultaneously present the entire time.

The distinction drawn between `ArDirectionOverlay` (a live Compose UI component, correctly reported as removed-and-never-reintroduced in every prior phase's `CameraOverlayManager`/`CameraOverlayView`/`OverlayNavigationEngine` audit) and the deprecated `overlay/` package proper is accurate. Every prior phase's claim that the deprecated package was removed remains true and is not being walked back — this was always a different component, in the same file, doing a visually similar job, which is exactly why it was missed by source-level review that was correctly checking for the right classes and simply didn't have reason to scrutinize a component the Phase 4 plan had explicitly authorized to remain.

The root-cause attribution to the Phase 4 execution plan's own "Preserve" instruction is accurate and accepted without qualification. That instruction was approved in this review chain; the planning-stage assumption that a "2D UI arrow" would be a minor, non-conflicting HUD element rather than a full-screen, unconditionally-rendered chevron with live turn-distance text was the actual gap, not a fabrication or a missed removal.

---

## On the Remediation Plan

Full removal of `ArDirectionOverlay` from the composition hierarchy, rather than gating it behind a condition or reducing its size/prominence, is the correct fix — not a compromise. It is also the only fix consistent with the architecture's own stated goal that AR guidance be world-locked and floor-attached, not screen-space. Retaining any version of a permanent center-screen 2D arrow would leave the same fundamental conflict this defect surfaced, just reduced rather than resolved.

---

## Decision

**ACCEPTED, PENDING DEVICE CONFIRMATION.**

The investigation is thorough, the root cause is independently verified against real source rather than taken on trust, and the fix is architecturally correct. This is not, on its own, sufficient to close the defect — per this project's standard since Phase 1, a code change removing a rendering component requires the same device confirmation any other rendering change has required, not a unit-test-and-build pass alone (Step 3 of the remediation plan) as the final word.

**Before this defect is closed:**
1. Apply the removal exactly as described (delete the composition call and the composable definition).
2. Run the four-item device validation protocol this report itself proposes in Section 6, Step 4 — on the actual Galaxy S22 Ultra, by the human reviewer directly, not summarized on their behalf.
3. The human reviewer should report, in their own words: whether the arrow and turn-banner are genuinely gone; whether the 3D floor markers (cyan standard, amber turn) are now visible and, this time, actually confirmable as the sole source of directional guidance; and whether the destination card and mode toggle remain intact and unobstructed.

This is also the first point since Phase 4 where the human reviewer will be able to actually evaluate the accumulated Phase 5 and Phase 6 rendering work visually — the anchors, the turn markers, the correction smoothing — since it was never actually visible on its own until the layer sitting on top of it is gone. Treat this next device session as validating not just the removal, but the first real look at Phases 5 and 6's actual output.
