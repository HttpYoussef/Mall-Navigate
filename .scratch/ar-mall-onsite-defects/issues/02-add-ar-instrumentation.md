Type: task
Status: resolved

## Question

Add targeted diagnostic logging to the AR frame loop so later tickets can tell transform/anchor-
window churn apart from raw ARCore/VIO drift instead of guessing from screenshots. Code-only —
no on-site work; see [Ticket 08](08-capture-onsite-mall-traces.md) for the physical trace capture
this instrumentation is for.

**Log per frame** (or on each relevant event): ARCore camera pose position + yaw, transform
revision id, `FacilityTransform.headingDeg`, the landmark-recognition candidate heading (when a
fix is accepted/rejected), the active route index used by the anchor-window planner, active anchor
node ids, and `FloorPlaneConfidenceMonitor` plane-selection/confidence output. Relevant call sites:
`ArAnchorRenderer.kt` (`update`/`reconcile`), `ArSceneViewWrapper.kt`, `LocalizationLayer.kt`,
`FloorPlaneConfidenceMonitor.kt` — see
[codex-investigation-findings.md](../codex-investigation-findings.md) for the specific line
references already identified.

Independent of every other ticket on this map — none of tickets 01/04/05/06/09 require trace data
to implement (they're all statically diagnosable from the code itself, per
[codex-debate-findings.md, point B](../codex-debate-findings.md)); this instrumentation is for
validating those fixes and for [Ticket 08](08-capture-onsite-mall-traces.md), not a prerequisite
for writing them.

## Answer

Logging added (delegated to agy) across all four call sites: `ArAnchorRenderer`'s existing anchor-
plan-updated log extended with camera pose, transform revision, correction-triggered flag, route
index range, and active/window anchor node ids; `ArSceneViewWrapper`'s periodic diagnostic
tightened to 1/sec with local pose + heading + transform revision added;
`LocalizationLayer` logs candidate heading on every accept/reject decision; `FloorPlaneConfidenceMonitor`
logs plane-selection category (contained/nearest-fallback/none) + confidence, only on change.
No behavior changes — verified by tracing the `FloorPlaneConfidenceMonitor` refactor (the only file
requiring real restructuring) against its pre-logging logic; return values are identical for every
branch. Compile + full unit test suite pass, independently re-verified. Committed at `5f5c7c2`.
