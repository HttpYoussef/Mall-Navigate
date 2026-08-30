# No AR Content Rendering — Investigation Required (Phase 8 Review Held Pending This)

**Status:** Blocking. Phase 8's implementation report is not being reviewed for acceptance yet — its own §6 device-validation protocol is entirely prospective (six steps, zero completed, zero observed results), and reviewing Module 8's supervisory logic before confirming Module 6/7's actual output is even visible would be testing the wrong layer first.

---

## What Was Reported

Testing at home (explicitly not in the mall), after a successful logo scan:
- White ARCore feature-tracking dots are visible and re-triangulate/shift with each step (expected, raw tracking noise).
- **No chevrons, no anchor markers, no arrival beacon — nothing from the Phase 6/7 rendering pipeline is visible at any point.**

This is confirmed to occur *after* a successful scan, which rules out "Module 4 never produced a transform" as the explanation on its own.

---

## Two Distinct Hypotheses — Do Not Assume Either, Investigate Both

**Hypothesis 1 — anchors are being created, but at coordinates far outside the camera's view.** The mall's route-graph coordinates are authored against the physical mall. A successful scan at home establishes a valid ARCore-to-facility transform, but the *destination* route nodes Module 6 tries to render still live in mall-corridor coordinate space. Depending on where the scan matched in the graph, anchors could be placed meters or tens of meters from the user's actual physical position at home — technically correct, invisible in practice, and not a code defect.

**Hypothesis 2 — anchors are never actually being created at all**, independent of location, meaning the Module 4 → Module 5 → Module 6 handoff itself has a defect (e.g., `RoutePathLayer` not receiving/resolving the route after the scan, `ArAnchorRenderer`'s window planner never receiving a non-empty spec list, or a silent exception in anchor creation).

These have different implications and different fixes. Do not report back with a single confident explanation without evidence distinguishing them.

---

## Required Investigation

1. **Add or confirm existing logging that reports, every frame or on every window-plan change, the actual count of active anchors `ArAnchorRenderer` believes it should be rendering** (not whether ARCore itself is tracking — the anchor *count*, from the anchor-management layer's own state).
2. **Reproduce the reported scenario** (successful home scan, enter AR navigation) and capture that count directly.
3. **If the count is non-zero:** this confirms Hypothesis 1. Additionally log or report the computed distance between the user's current facility-space position (post-scan) and the nearest active anchor's facility-space position, to quantify how far off-screen the content actually is.
4. **If the count is zero:** this confirms Hypothesis 2. Trace why — does `RoutePathLayer` have a non-empty route after the scan? Does the window planner receive it? Is `ArAnchorRenderer.reconcile()` being called at all, and does it throw or silently no-op?
5. Report findings with real log output, not a narrative conclusion.

---

## What This Does Not Change

Phase 8's code-level review (state machine, reversal logic, drift/deviation math) remains separately trackable and was largely verified sound against the approved v3 plan on inspection. It is not being rejected — it is being held, because accepting supervisory logic over a rendering layer whose basic visibility hasn't been confirmed on any device puts the review in the wrong order. Once this investigation resolves the rendering-visibility question, Phase 8's own device validation (§6 of its report) still needs to actually be performed and reported, not just planned.
