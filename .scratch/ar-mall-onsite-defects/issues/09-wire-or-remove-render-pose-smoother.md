Type: task
Status: resolved

## Question

`ArAnchorRenderer` owns a `RenderPoseSmoother` (`ArAnchorRenderer.kt:39`), but the live AR frame
loop passes the raw ARCore `camera.pose`/`localPoseFor(...)` straight into rendering
(`ArSceneViewWrapper.kt:114-124`, `:206-223`) — the smoother is only ever touched on disposal
(`ArAnchorRenderer.kt:321-327`, a reset, not a use). So the documented render-level damping this
class exists to provide is currently dead code.

Graduated from the map's "Not yet specified" fog into its own ticket per
[codex-debate-findings.md, point D](../codex-debate-findings.md): this is independently gradable
and doesn't depend on the heading-frame contract outcome ([Tickets 03/04](03-settle-heading-frame-contract.md)) —
`RenderPoseSmoother`'s own contract is render-level only, per its class documentation in
`render/RenderPoseSmoother.kt`.

**Decide and do**: either wire `poseSmoother.filter()` into the frame-loop pose before it reaches
rendering (naming the exact timestamp source and reset conditions — e.g. on transform revision
change, on tracking-state loss/recovery), or deliberately remove the unused smoother and document
why render-level smoothing isn't needed. Whichever you choose, prove that the *localization/anchor
placement* coordinates are not unintentionally smoothed too (that pipeline needs raw pose data;
only the rendered/visual pose should be damped, if at all).

## Answer

Removed (delegated to agy) rather than wired in. Stabilization is already handled by two
orthogonal, non-overlapping mechanisms: ARCore's own VIO tracking on the physical `Anchor`
trackables, and `CorrectionInterpolator`'s deterministic offset stepping across
`config.correctionFrames` on transform revisions. Wiring the smoother in risked double-filtering
against `CorrectionInterpolator` and — since the only integration point available was upstream in
the shared frame-pose path — risked leaking smoothing into `FloorPlaneConfidenceMonitor` and
`AnchorWindowPlanner`, both of which must consume raw pose data. Removed the dead
`poseSmoother` constructor property and its two disposal-time `reset()` calls from
`ArAnchorRenderer.kt`. `RenderPoseSmoother.kt` itself is untouched — `RenderPoseSmootherTest.kt`
and `FullSystemIntegrationScenarioTest.kt` still exercise it directly and independently.
Grep-verified (orchestrator) nothing else referenced the removed property. Compile + full unit test
suite pass, independently re-verified. Committed at `940f112`.
