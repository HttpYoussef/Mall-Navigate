# Second opinion requested: AR navigation on-site defects

I'm planning a fix effort (not writing code yet — just diagnosing and planning) for the MallAR
Android app's AR indoor-navigation subsystem, after its first-ever real on-site test at the mall.
The whole subsystem (9 phases, extensive docs under `docs/ar/`) was previously "signed off" but
explicitly only ever validated in synthetic/home conditions — never on-site — per
`docs/ar/Implementation/Phases/Phase 9/Final_System_Acceptance_Sign_Off.md`. So today's mall test
is the first time any of this has been exercised against real corridor geometry, lighting, and
reflective flooring.

## Two symptoms reported by the tester (verbatim, lightly cleaned up)

1. **Arrow direction wrong and unstable while stationary.** Standing still in front of a store
   ("esla"/ similar) that the landmark-recognition correctly identified, the AR arrow pointed in
   the wrong direction and kept changing even though the user wasn't moving. This happened across
   several trial sessions: sometimes the arrow drifted and never stabilized; other times it
   "fixed" itself but then pointed in the wrong direction.
2. **Arrows not glued to the floor while walking; visually decoupled from a separate white marker
   overlay.** As the user walked, the arrow chevron didn't stay planted on the same floor spot/plane
   — described as "the white markers was drawn in a side and the other side the arrows was drawn."

## Photographic evidence (attached, 2 images)

Both screenshots are from the same AR session, seconds apart, same destination card overlay
("Reserved", "13m · 1 min walk" — i.e. the route/distance metadata did NOT update between the two
frames). Between the two frames:
- The arrow chevron jumped from roughly center-right of frame to the far-left edge, and its
  rotation changed noticeably.
- A separate pattern of small white dots on the floor (visible in both frames, over the
  darker/carpet-like flooring on the right side) stayed roughly in the same place while the arrow
  moved — i.e. the arrow and the dot pattern are NOT moving together, suggesting either (a) the
  dots are ARCore's own default plane/point-cloud debug visualization (unrelated to the app's
  guidance content) that happens to look like a second "marker," or (b) the app's marker/anchor
  and the arrow child node are genuinely desynced from each other.

## Relevant source (read these before answering; do not assume any prior bug-fix report is still
accurate — the docs folder contains many previously-closed reports for similar-sounding symptoms
that were only ever validated synthetically)

- `app/src/main/java/com/example/mallar/navigation/BearingCalculator.kt` — bearing/rotation math
  (map-space bearing → arrow rotation given target bearing & user heading)
- `app/src/main/java/com/example/mallar/ar/ArAnchorRenderer.kt` — anchor placement/reconciliation,
  per-node correction interpolation on transform changes, marker positioning each frame
- `app/src/main/java/com/example/mallar/ar/RoutePathLayer.kt` — route/facility-coordinate layer
- `app/src/main/java/com/example/mallar/ar/render/GuidanceVisualFactory.kt` — builds the visual
  marker nodes (arrow / turn chevrons), `computeWorldHeadingDeg`
- `app/src/main/java/com/example/mallar/ar/render/RenderPoseSmoother.kt`
- `app/src/main/java/com/example/mallar/ar/render/FloorPlaneConfidenceMonitor.kt` — floor elevation
  resolution against ARCore planes
- `app/src/main/java/com/example/mallar/ar/SensorFusionLayer.kt`
- `app/src/main/java/com/example/mallar/ar/LocalizationLayer.kt` — landmark-recognition-based
  correction (this worked correctly per the tester — it detected the store and the 2D map/route
  was correct; the defect is AR-rendering-layer only)
- `app/src/main/java/com/example/mallar/navigation/OrientationManager.kt`
- Also check whether ARCore's default `planeRenderer` / point-cloud visualization is left enabled
  anywhere (e.g. in `app/src/main/java/com/example/mallar/ar/ui/ArSceneViewWrapper.kt` or
  `ManagedARSceneView.kt`), since that could fully explain the "second white marker" as an
  unrelated debug overlay rather than an app bug.
- Skim (don't trust as still-accurate, just for context on what was tried before) the prior bug
  investigations in `docs/ar/Implementation/Bugs and Issues reported/` — especially
  `ARCore_Floor_Anchor_Parallax_and_Drift_Analysis.md`, `ARCore_Tracking_Instability_Defect_Report.md`,
  and `Tracking_Fix_Remediation_Report.md`.

## What I want from you

This is a **read-only investigation** — do not edit any files. I want:

1. Your best root-cause hypothesis (or ranked hypotheses) for each of the two symptoms, tracing
   through the actual code (cite file:line), not just architecture-doc theory.
2. Whether you think the two symptoms plausibly share one root cause (e.g. bad/unstable heading
   input feeding both the arrow-rotation math and the anchor placement) or are independent.
3. Whether the "second white marker" is most likely ARCore's own default debug visualization
   (config fix: disable it) vs. an app-level anchor desync bug (harder fix).
4. A concrete, ordered investigation-and-fix plan you'd recommend, sized into independent
   work items — I'll be turning this into tracked tickets on an issue tracker, so item boundaries
   matter (what's genuinely independent vs. sequentially blocked).
5. Flag explicitly anything you find that looks like an **architectural** issue (per this
   subsystem's own governance doc `docs/ar/Engineering Architecture and Guidlines/README.md`,
   changing a layer's responsibility, a state-machine transition, or a numeric architectural
   parameter requires a new reviewed architecture version) versus a plain tuning/implementation bug.

Be concise but specific — file:line citations over prose speculation wherever you can ground a
claim in the actual code.
