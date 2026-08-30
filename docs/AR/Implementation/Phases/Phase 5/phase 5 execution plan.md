# Phase 5 Execution Plan — Localization Layer

## Objective

Implement Module 4 as the sole owner of the validated facility transform between the existing scan result, ARCore local tracking, and later route rendering. Preserve the manager-owned ARCore session and the existing CameraX scan handoff.

## Planned implementation

- Add `LocalizationLayer` with `FacilityTransform`, confidence tiers, transform revisioning, and reset/initialization boundaries.
- Add a Fix Validation Gate that rejects missing, graph-implausible, displacement-implausible, and tolerance-exceeding candidates before transform mutation.
- Consume the accepted pre-navigation scan result once through the existing navigation session snapshot.
- Add periodic re-fix scheduling near landmark nodes at a one-second proximity-check cadence and four-second attempt throttle.
- Copy the ARCore camera image from the manager-owned frame, close it before asynchronous processing, and run recognition off the render thread.
- Keep recognition single-flight so overlapping background attempts cannot mutate localization state concurrently.
- Rebase the local tracking origin only after an accepted candidate and expose a monotonic revision for the fast rendering cycle.
- Integrate the layer through `ArSceneViewWrapper` and `UnifiedNavigationViewModel` without creating another camera/session path.

## Tests and evidence

- Unit-test confidence tiers, validation rejection/acceptance, rebasing, scheduler cadence/throttle, and single-flight behavior.
- Run `:app:testDebugUnitTest` and `:app:assembleDebug` with raw output recorded in the implementation report.
- Device validation remains a separate acceptance requirement and must be recorded before Phase 6/7 sign-off.

## Explicit exclusions

Phase 5 does not implement route deviation recovery, Transition Mode, Arrival, anchor-window rendering, or changes to frozen `docs/AR` architecture documents.
