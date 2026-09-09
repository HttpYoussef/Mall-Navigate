Type: task
Status: resolved

## Question

Disable ARCore/SceneView's default plane-detection debug visualization
(`planeRenderer.isEnabled` / `.isVisible`), which is almost certainly the "second white marker"
the tester saw drawn independently of the app's own guidance arrow.

**Why this is believed to be the cause, not the app's arrow/anchor logic**: `app/src/main/java`
has zero references to `planeRenderer`/`PlaneRenderer` anywhere (grep-verified). SceneView 2.2.1's
`ArSceneView` creates a `planeRenderer` defaulted to `isEnabled = true`, `isVisible = true`, and
nothing in `ArSceneViewWrapper.kt` or `ManagedARSceneView.kt` ever touches it. Its textured plane
visualizer (small dots tiled across a detected surface) matches the tester's screenshots far
better than the app's own single blue chevron. See
[codex-investigation-findings.md § 2](../codex-investigation-findings.md#2-arrows-not-glued-to-floor--separate-white-marker).

**Do**: set `planeRenderer.isEnabled = false` (and/or `.isVisible = false`) in the `.apply { }`
block where `ManagedARSceneView(context)` is constructed — `ArSceneViewWrapper.kt:64-69` — the
property is inherited from `io.github.sceneview.ar.ARSceneView`, which `ManagedARSceneView`
extends (`ManagedARSceneView.kt:14`). Confirm against the actual SceneView 2.2.1 API surface at
compile time (the property names/defaults above are from codex's read of the library source, not
yet compiler-checked in this repo) and confirm the app doesn't rely on the plane renderer for
anything else (e.g. a debug/dev build flag) before disabling unconditionally.

This is independent of every other ticket on this map — do it first since it removes a confusing
visual from every later on-site retest.

## Answer

Confirmed via compiled-API inspection (delegated to agy): SceneView 2.2.1's `ARSceneView` exposes
`planeRenderer: PlaneRenderer` with both `isEnabled` and `isVisible` properties; `isEnabled = false`
skips per-frame plane hit-testing and visualizer updates entirely, `isVisible = false` hides any
still-tracked visualizers. Both set to `false` in the `.apply { }` block on
`ManagedARSceneView(context)` at `ArSceneViewWrapper.kt:64-71`. Re-confirmed zero other references
to `planeRenderer`/`PlaneRenderer` anywhere in the app before disabling — nothing depended on it.
`compileDebugKotlin` passes; independently re-verified by the orchestrator. Committed at `64775a3`.
