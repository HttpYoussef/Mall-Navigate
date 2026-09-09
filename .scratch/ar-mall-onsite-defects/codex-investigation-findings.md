# Codex investigation: AR on-site defects — findings

Read-only investigation run via `codex exec` (model `gpt-5.6-sol`, provider AgentRouter) against
this repo's AR subsystem code plus the two mall test screenshots in `Testing images/`. Full prompt
given to codex: [codex-investigation-prompt.md](codex-investigation-prompt.md).

Key claims below were spot-verified directly against the source by Claude after codex returned
them (see verification notes inline) — not taken on faith.

---

The field evidence is consistent with more than one defect. The strongest explanation is:

- the AR world/facility heading is not calibrated consistently across the CameraX scan and the ARCore world;
- the visible white-dot pattern is almost certainly SceneView's default plane visualization, not a second copy of the app arrow;
- real ARCore/VIO instability can still amplify both symptoms.

Codex could not run an on-device repro loop from this workspace, so the conclusions below are code-grounded hypotheses, not a claim of physical confirmation.

## 1. Arrow direction wrong or unstable while stationary

### Highest-ranked cause: transform heading is being used as an absolute AR-world heading

`FacilityTransform.worldPositionFor()` treats `headingDeg` as the rotation between facility coordinates and ARCore world coordinates (`AnchorManagementLayer.kt:154-164`). `ArAnchorRenderer` then derives both anchor position and marker orientation from that transform (`ArAnchorRenderer.kt:251-266`).

However:

- the initial AR local pose supplied to localization hardcodes `headingDeg = 0f` (`ArSceneViewWrapper.kt:127-131`) — **verified**: grepped, confirmed at line 130.
- the reusable `localPoseFor()` helper also hardcodes `headingDeg = 0f` (`ArSceneViewWrapper.kt:301-306`) — **verified**: confirmed at line 305.
- accepted recognition fixes copy `candidate.headingDeg` directly into the transform (`LocalizationLayer.kt:145-152`);
- `candidate.headingDeg` is the PnP/recognition heading, i.e. a facility-facing heading, not demonstrably the ARCore-world-to-facility rotation (`PnPSolver.kt:165-168`).

The code therefore appears to mix an absolute facility/compass heading with an ARCore-local frame whose yaw is not incorporated. This is especially problematic after the pre-navigation CameraX scan hands off to a new ARCore session.

A related concrete failure is the single-landmark path: single-landmark localization deliberately returns `estimatedHeadingDeg = null` (`LocalizationEngine.kt:227-245`). The initial transform then falls back to the hardcoded local heading `0f` (`LocalizationLayer.kt:244-249`). That can produce a consistently wrong arrow even though the store and route position are correct — this matches the reported "esla" case (single-landmark recognition, correct store match, correct 2D route, wrong AR arrow).

### Second cause: existing markers are translated after a fix, but not re-oriented

When a localization transform changes, `ArAnchorRenderer` computes a positional correction only (`ArAnchorRenderer.kt:141-156`) — **verified**: read the block directly, confirmed only `LocalAnchorOffset` (position) is corrected via `managed.correction.begin(...)`; there is no rotation update anywhere in that block. It never updates `marker.rotation`.

New markers use the current route tangent heading (`ArAnchorRenderer.kt:257-287`), while existing markers retain the heading from creation. If periodic recognition changes the transform, the active window can contain markers based on different orientation revisions. That explains "fixed itself, then wrong" or mixed orientation behavior.

### Third cause: anchor-window churn from stationary ARCore drift

The active route window is recomputed every frame from `facilityPosition(localPose)` (`ArAnchorRenderer.kt:128-137`). `localPose.x/z` come directly from the ARCore camera pose (`ArSceneViewWrapper.kt:225-239`).

If VIO drifts while the user is stationary, the nearest route index can change at a waypoint boundary, causing the sliding anchor window to replace visible nodes. The screenshot's chevron moving from center-right to far-left while the route card remains unchanged is compatible with that. It is not explained by `BearingCalculator`: the live AR chevron path does not call `BearingCalculator.arrowRotation`; it calls `GuidanceVisualFactory.computeWorldHeadingDeg()` (`GuidanceVisualFactory.kt:45-62`).

### Additional implementation defect: render pose smoothing is not in the fast path

`ArAnchorRenderer` owns a `RenderPoseSmoother`, but the wrapper passes the raw `camera.pose` into the render/update path and there is no call to `poseSmoother.filter()` in the AR frame loop (`ArAnchorRenderer.kt:39`, `ArSceneViewWrapper.kt:114-124`). The smoother is only reset on disposal (`ArAnchorRenderer.kt:321-327`). Thus the documented render-level damping is currently absent.

## 2. Arrows not glued to floor / separate white marker

### Most likely explanation: the white pattern is SceneView's default plane renderer

`ArSceneView` in SceneView 2.2.1 creates a `planeRenderer`, and its source default is `isEnabled = true`, `isVisible = true`. The app never sets either property in `ArSceneViewWrapper.kt` or `ManagedARSceneView.kt` — **verified**: grepped the entire `app/src/main/java` tree for `planeRenderer`/`PlaneRenderer`, zero matches anywhere. The SceneView renderer uses a textured plane visualizer, which matches the repeated small white floor dots in the photographs far better than an app guidance marker.

So the two visible elements are probably:

- app-owned blue chevron: `AnchorNode -> ImageNode`;
- SceneView-owned detected-plane visualization: `planeRenderer`.

They are not expected to move together. This is distinct from the older bug reports (`docs/ar/Implementation/Bugs and Issues reported/`) that described "raw white feature-tracking dots" — in the current SceneView integration, the verified default visualization is the plane renderer; the app also has no explicit point-cloud renderer configuration.

### App-level floor-placement risks still exist

The arrow itself is attached as a child of an ARCore anchor (`ArAnchorRenderer.kt:274-290`) — that parent-child relationship should keep the arrow and its own anchor together.

But `FloorPlaneConfidenceMonitor` selects the first plane containing the target, otherwise the nearest tracked horizontal plane (`FloorPlaneConfidenceMonitor.kt:42-53`). A nearest unrelated plane can therefore be used for a route node outside the actual floor patch.

Also, the monitor computes a damped rolling elevation but returns the undamped `planeY` whenever a plane exists (`FloorPlaneConfidenceMonitor.kt:55-69`). Reflective flooring can therefore produce vertical jitter even though it does not explain a separate white-dot overlay.

## 3. Shared or independent root cause?

They plausibly share an environmental amplifier, but not one single software cause.

Shared factors: ARCore/VIO drift or poor tracking on reflective, low-feature flooring; transform/anchor corrections triggered while stationary; missing use of render-level pose smoothing.

Independent primary causes: wrong arrow direction (coordinate-frame/heading calibration and missing marker reorientation) vs. white-dot separation (default plane visualization, plus possible plane-selection/elevation errors).

The white dots staying approximately fixed while the blue arrow changes is evidence *against* "the arrow and marker child are desynchronized" — it's much more consistent with two different render systems.

## Architectural flags (per `docs/ar/Engineering Architecture and Guidlines/README.md` versioning policy)

Potentially architectural:

- The CameraX-scan heading to ARCore-world heading relationship is not explicitly defined in the current implementation contract. Fixing only arithmetic is implementation-level; changing the meaning/ownership of `FacilityTransform.headingDeg` is an architectural contract change and should follow the versioning policy.
- Adding a new heading authority or moving heading ownership between Localization, ARCore Session, and Sensor Fusion would alter layer responsibilities and requires an architecture revision.
- Changing approved numeric policies such as anchor-window size, correction duration, or trust thresholds requires review if treated as architectural parameters.

Plain implementation/configuration defects: plane renderer left enabled; existing markers not rotated when the transform revision changes; `RenderPoseSmoother` instantiated but unused; damped floor elevation calculated but not returned; nearest-plane fallback without a validity gate; single-landmark heading falling back to hardcoded zero (assuming the transform contract remains unchanged).
