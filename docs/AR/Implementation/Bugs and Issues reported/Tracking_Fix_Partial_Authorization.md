# Tracking Instability Investigation — Partial Authorization

**Subject:** `ARCore_Tracking_Instability_Investigation_Report.md`
**Decision:** Partial authorization. Two items cleared to implement; two items held back, one on evidentiary grounds and one on architectural-scope grounds.

---

## Cleared to Implement

**Focus mode change (`FocusMode.AUTO` → `FocusMode.FIXED`).** The mechanism is correctly sourced and matches documented ARCore guidance for motion-tracking stability. Low risk, narrow, directly testable against the exact symptom reported. Approved.

**Filament material alpha initialization (Defect C).** Correctly diagnosed against SceneView's default blend behavior, consistent with the fade-in mechanism Phase 6 already implements elsewhere. Approved.

---

## Held Back

**Defect A — the coordinate-space fix (Step 2) is not approved as written.**

The report is likely right that a bug exists here — but I don't accept the specific proposed fix without one more check, and I want to be precise about why. The replacement pose (`Pose.makeTranslation(...)` with no rotation component) implicitly assumes `offset` from `transform.localOffsetFor(...)` is already expressed in the ARCore session's world frame. But `localOffsetFor` takes `localPose` as a parameter, which strongly suggests the offset it returns is relative to that local pose's *orientation*, not an already-world-frame value. If that's the case, dropping the rotation entirely doesn't fix the coordinate-space error — it replaces one wrong frame with another wrong frame that happens to look more plausible in this report's narrative.

**Before this fix is applied:** show the actual implementation of `FacilityTransform.localOffsetFor` and state plainly, with reference to that code, what coordinate frame its returned `offset` is actually expressed in. If it's genuinely already in world-frame coordinates, the proposed fix is correct and can proceed as written. If it's relative to `localPose`'s orientation, the fix needs to compose the offset with `localPose`'s rotation (or `transform`'s own reference orientation), not drop rotation entirely.

**Defect B — the localization-fallback seeding change is not authorized.**

This is not a defect fix — it's a new behavior for Module 4 (synthesizing an initial transform from route geometry when no scan-based fix has occurred) that the frozen Engineering Specification does not describe. §9 of that document is explicit that the Localization Layer *consumes* the pre-navigation scan's result; nothing in it authorizes generating a transform without one. This may well be the right call for real-world usability, but it is an architectural decision, not a bug fix, and Implementation Rule 6 requires it be escalated on its own terms — not bundled into a defect report and self-approved alongside genuine bug fixes.

**Do not implement this without separate, explicit authorization.** If a real scan-based fix is required before AR content can render at all, and that's producing the "no markers visible" symptom in ordinary use (not just this test), that's worth raising directly as its own question — is a real logo scan actually completing before this test enters AR mode, or is this fallback genuinely needed because it isn't. Answer that first, plainly, before deciding whether a fallback path is the right fix at all.

---

## Sequencing

Apply the two cleared items first. Re-run the narrow rotation-in-place test from the prior defect report — nothing else — with **only** the focus-mode and material-alpha changes applied, before touching anchor placement math at all. This isolates whether tracking instability is resolved by the focus-mode fix alone, which is the cleaner, faster, and much lower-risk thing to confirm before any coordinate-space surgery happens on top of it. If tracking is stable after just that change, the coordinate-space question can be investigated calmly, without an unstable tracking session as a confounding variable.

Report back, in your own words: after this specific, narrow change, does the same floor spot still look pinned when you rotate away and back?
