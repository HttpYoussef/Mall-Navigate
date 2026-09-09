Type: task
Status: resolved

## Question

Fix `ArAnchorRenderer` so that when a transform revision is accepted, every already-placed
marker's rotation is recomputed and corrected together with its position — not position-only.

**Verified in code**: on a transform change, `ArAnchorRenderer.kt:141-157` only computes a
positional `LocalAnchorOffset` and calls `managed.correction.begin(...)`; it never touches
`marker.rotation`. Meanwhile brand-new markers get a fresh heading baked in at creation
(`ArAnchorRenderer.kt:257-287`, via `GuidanceVisualFactory.computeWorldHeadingDeg`). So after a
mid-session recognition fix, the active anchor window can hold a mix of markers built under
different heading revisions simultaneously — this is the concrete mechanism behind the tester's
"it fixed itself, then pointed in the wrong direction" and "several session of trial" reports.
Full detail:
[codex-investigation-findings.md § 1, second cause](../codex-investigation-findings.md#second-cause-existing-markers-are-translated-after-a-fix-but-not-re-oriented).

**Not hard-blocked on [Ticket 04](04-fix-heading-frame-calibration.md)** — the fix is self-
contained regardless of how `headingDeg` is ultimately sourced (per
[codex-debate-findings.md, point C](../codex-debate-findings.md)) and can be implemented and
tested in parallel with it. The only coordination point: if this lands before Ticket 04, its
rotation-recompute must be re-checked against whatever transform contract Ticket 04 finalizes
before both are merged together.

**Implementation detail**: markers are created with `marker.rotation = Rotation(-90f, headingDeg,
0f)` (`GuidanceVisualFactory.kt:97`) — the `-90f` X pitch lays the decal flat on the floor and must
be preserved; only the Y component (`headingDeg`) changes. On a transform revision, recompute each
active marker's heading the same way it's computed at creation
(`ArAnchorRenderer.kt:257-266`, via `GuidanceVisualFactory.computeWorldHeadingDeg` against the
marker's next/prev route waypoint under the *new* transform) and set
`managed.marker.rotation = Rotation(-90f, newHeadingDeg, 0f)` alongside the existing positional
correction in the `transformChanged` block (`ArAnchorRenderer.kt:141-157`).

**Test target**: there is no existing test file for `ArAnchorRenderer` — create
`app/src/test/java/com/example/mallar/ar/ArAnchorRendererTest.kt` (matching the convention of
sibling files like `AnchorManagementLayerTest.kt` in the same directory), asserting an existing
marker's position and rotation update together on a transform revision.

## Answer

Fixed (delegated to agy, second attempt — first attempt was killed mid-run by a system low-memory
event and its partial, incomplete edit was discarded). Extracted the heading calculation into
`computeMarkerHeadingDeg()`, shared between marker creation and the transform-correction path; the
`transformChanged` block now recomputes and sets `marker.rotation = Rotation(-90f, newHeadingDeg,
0f)` alongside the existing positional correction. Applied immediately (no rotation-interpolation
convention exists in this file to match).

**Surfaced design decision**: constructing a real `AnchorNode`/`ARSceneView` in a host-JVM unit
test triggers SceneView's native `filament-jni` loading, so implementing the requested test
required testability seams: `ManagedAnchor`/`anchors` widened from `private` to `internal`, two
read-only accessors added (`getMarker`/`getMarkerRotation`), and `update()`'s
`sceneView`/`session`/`frame` parameters gained nullable defaults. Verified (orchestrator): `frame`
was already unused inside `update()`/`reconcile()`, so its nullability is inert; the one real call
site (`ArSceneViewWrapper.kt:236-239`) still passes non-null `sceneView`/`session` explicitly, so
production behavior is unchanged — the null-skip path is test-only. Grep-verified nothing else
referenced the widened members.

Three new tests in `ArAnchorRendererTest.kt`, using real `FacilityTransform` geometry (not just
mocked return values): an interior marker's rotation updates on a transform change, a terminal
(last-route-index) marker's rotation updates via its previous-waypoint tangent, and a same-revision
call touches neither rotation nor correction. Compile + full `ar` package test suite pass,
independently re-verified. Committed at `026864c`.
