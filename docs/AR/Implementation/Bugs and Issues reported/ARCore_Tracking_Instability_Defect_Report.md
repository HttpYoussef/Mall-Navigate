# ARCore Tracking Instability — Defect Report (Supersedes Overlay Remediation as Blocking Issue)

**Status:** New, more foundational blocking defect discovered while attempting to re-validate the overlay removal. This report does not accept `Phase_4_6_Overlay_Remediation_Report.md`'s "DEFECT CLOSED" verdict and does not authorize Phase 7 readiness.

---

## What Actually Happened During Re-Validation

The human reviewer confirmed the `ArDirectionOverlay` removal itself: the 2D screen-space arrow and turn banner are genuinely gone. That part of the prior remediation is accepted.

But the reviewer also directly reported, on being asked to look specifically at what remains on screen:
- **No route-derived AR content of any kind is visible** — no cyan standard markers, no amber turn markers, nothing shaped like the Phase 6 anchor rendering. Only ARCore's raw white feature-tracking dots are visible.
- **Those feature-tracking dots do not stay fixed to real floor locations.** Directly asked to stand still, point at a fixed part of the floor, and rotate in place without walking, the reviewer confirmed: the dots shift and are not "stuck" to the floor as the phone moves.

This second finding is the one this report treats as blocking. Feature points that do not remain anchored to real-world locations as the device moves is a direct sign that ARCore's own visual-inertial tracking is not stable in this session, on this device — not a symptom of anything Phase 5 or 6 built. This is the exact property Phase 4's static-sphere test existed to verify in the first place, before any later phase's content was built on top of it.

---

## Why `Phase_4_6_Overlay_Remediation_Report.md`'s Verdict Is Not Accepted As Written

That report's own validation table, row 2, described the white feature-tracking dots as "firmly stuck/anchored to the physical ground" and marked this **PASSED**. Direct, first-hand re-testing by the human reviewer — after this reviewer specifically questioned that row rather than accepting it — found the opposite: the dots are not stable. This reviewer is not asserting anything about how that discrepancy occurred; it is not this reviewer's place to guess whether it was a misread of a brief, uninterrupted-looking moment, a difference in what was tested, or something else. It is being stated plainly as a fact: the claim in that row does not match what was found on immediate re-test, and a defect cannot be closed on a validation row that does not hold up under a second look.

Row 3 of the same report ("Diagnostic Primitive Retirement... CONFIRMED RETIRED... PASSED") is also not an adequate substitute for confirming Phase 6's actual deliverable is present — it confirms an old test object is gone, not that the real anchor markers exist. No row in that table actually tested for cyan/amber route markers at all.

---

## What This Report Requires — In Order

**Do not attempt to fix tracking stability by guessing at a cause.** Lighting, floor surface reflectivity, camera calibration, a session-configuration regression from the Phase 4/6 lifecycle fixes, or a device-specific ARCore quirk are all plausible and this reviewer is not in a position to distinguish between them from a description. A confident-sounding explanation without real evidence is exactly the pattern this project has repeatedly had to reject.

1. **Investigate, with real evidence, whether anything in the Phase 4–6 code changes could plausibly affect ARCore's own tracking quality** — specifically the session configuration in `ArCoreSessionManager.createSession()` (plane-finding mode, focus mode, update mode) and anything in the `ManagedARSceneView`/lifecycle-synchronization work from the crash-fix and Phase 6 rounds that touches session pause/resume timing, since unstable resume sequencing can itself degrade tracking quality even without crashing. Report findings from actually reading the current state of that code, not from theorizing about what it might contain.
2. **Pull and include real ARCore-level Logcat output from a session where this was observed** — specifically any `TrackingState`/`TrackingFailureReason` transitions logged by `ArCoreSessionManager.updateTrackingState()`, which Phase 4's own implementation already logs on every change. If tracking is genuinely unstable, this log should show it directly, and this reviewer should see the actual log lines, not a description of what they presumably say.
3. **Separately and only after the above, confirm whether the Phase 6 anchor markers (`ArAnchorRenderer`) are even being created and attached during this test session at all** — since anchors created against an unstable tracking session may be silently failing or never being generated, which would explain both findings (no markers, unstable dots) as one root cause rather than two.
4. Submit findings and any proposed fix as their own report, with actual current source shown, before touching anything.

---

## Required Re-Test Once a Fix Is Proposed

A single, narrow, unambiguous test, stripped of everything else this time:

1. Stand still, in one fixed spot, in a normal-lighting indoor area with visible texture (not a blank wall).
2. Point the camera at one specific, identifiable point on the floor.
3. Rotate your body and the phone slowly, away from that point and back to it, without walking or changing position.
4. Report directly: did the same white dots return to visually the same spot on the floor when you rotated back, or were they different/shifted/gone?

No arrow, no markers, no other UI element should factor into this specific answer — this test is about tracking stability alone, isolated from every other question already investigated.

---

## Status

Phase 5 and Phase 6 remain unaccepted. The overlay-removal fix itself is accepted as correct and does not need to be revisited. Phase 7 readiness is not established — a subsystem whose foundational world-locking cannot yet be confirmed stable is not ready for additional rendering work to be layered on top of it.
