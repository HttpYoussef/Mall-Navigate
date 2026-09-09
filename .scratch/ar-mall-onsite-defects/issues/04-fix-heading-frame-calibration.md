Type: task
Status: resolved

## Question

Implement the two fixes settled in [Ticket 03](03-settle-heading-frame-contract.md) — confirmed to
need no architecture revision, both land inside the existing contract:

**(1) Measure real ARCore yaw at accept-time instead of assuming zero.**
`ArSceneViewWrapper.kt:131-136` constructs every `LocalTrackingPose` (passed into
`initializeFromScan`/`beginPeriodicRefix`/`completePeriodicRefix`, and stored as
`FacilityTransform.localOrigin`) with `headingDeg = 0f`. The real ARCore camera yaw is already
being computed a few lines later at `ArSceneViewWrapper.kt:181-187` (`cameraYawDeg`, via quaternion
atan2) for the supervisor/diagnostic-log path — it's just computed too late to use here. Move that
computation above the `localPose` construction and use it: `headingDeg = cameraYawDeg` in the
`LocalTrackingPose` at line 131-136 (there's a second, near-identical hardcoded-zero
`LocalTrackingPose` construction in the standalone `localPoseFor()` helper around line 301-306 —
give it the same treatment; it takes `camera.pose` as a parameter so it can compute its own yaw the
same way).

Then in `LocalizationLayer.kt`, wherever a candidate/compass heading is accepted into a
`FacilityTransform.headingDeg` (`FixValidationGate.validateAndApply` around line 145-152, and
`LocalizationLayer.initializeFromScan`'s `gate.seed(...)` around line 244-252), the stored
`headingDeg` must become the *reconciled* rotation — the candidate's compass heading minus the
ARCore yaw captured in `localPose.headingDeg` at that same instant — not the raw candidate heading.
Reason through the existing fallback chain at line 148
(`candidate.headingDeg ?: previous?.headingDeg ?: localPose.headingDeg`) carefully: `previous?.headingDeg`
is already a *reconciled* value from an earlier accept, so it must NOT have the current
`localPose.headingDeg` subtracted from it again — only a fresh `candidate.headingDeg` needs the
subtraction applied.

**(2) Stop hardcoding `CONFIRMED` tier on the first fix of a session.**
`LocalizationLayer.initializeFromScan` (~line 244-252) hardcodes `tier = FixConfidenceTier.CONFIRMED`
in its `gate.seed(...)` call regardless of how many landmarks were actually seen. Use
`FixConfidenceTier.fromLandmarkCount(landmarkCount)` instead (the same classification every other
accept path already uses via `CandidateFix.tier`). The landmark count needs threading in from the
pre-navigation scan, all the way from where it's first known to `initializeFromScan`'s call site:

1. `com/example/mallar/ui/localization/LogoScanScreen.kt:92`/`:100` — `NavigationState` already
   has `estimatedHeadingDeg`; add a sibling `estimatedLandmarkCount: Int = 0`.
2. `LogoScanScreen.kt:438` — where `NavigationState.estimatedHeadingDeg = locResult.estimatedHeadingDeg`
   is set, also set `NavigationState.estimatedLandmarkCount = locResult.landmarkCount` (the same
   `LocalizationResult` already has `landmarkCount`).
3. `com/example/mallar/ar/model/ArDataModels.kt:18-25` (`NavigationSessionSnapshot`) — add
   `initialLandmarkCount: Int = 0` alongside `initialHeadingDeg`.
4. `NavigationSessionInputAdapter.kt:40-47` (`takeSnapshot()`) — populate it from
   `NavigationState.estimatedLandmarkCount`.
5. Thread it from the snapshot into `ArSceneViewWrapper`'s `initialHeadingDeg` parameter
   (`ArSceneViewWrapper.kt:56`) as a sibling `initialLandmarkCount: Int = 0` parameter, and into the
   `initializeFromScan(...)` call at `ArSceneViewWrapper.kt:138`.
6. `LocalizationLayer.initializeFromScan`'s signature (~line 238-252) takes the landmark count and
   passes `FixConfidenceTier.fromLandmarkCount(landmarkCount)` instead of the hardcoded `CONFIRMED`.

Touch `ArSceneViewWrapper.kt`, `LocalizationLayer.kt`, `ArDataModels.kt`,
`NavigationSessionInputAdapter.kt`, and `LogoScanScreen.kt` (only the two narrow spots named
above — do not otherwise touch the scan screen's UI/flow). Leave
`AnchorManagementLayer.kt`'s `facilityPosition()`/`worldPositionFor()` math untouched — that part of
the contract is already correct; only the inputs feeding `headingDeg` were wrong.

Existing tests to check/extend: `LocalizationLayerTest.kt`, `AnchorManagementLayerTest.kt`.

## Answer

Both fixes implemented (delegated to agy) exactly as specified: real ARCore yaw now measured via
the existing quaternion-atan2 computation (moved earlier in the frame loop) and used for
`LocalTrackingPose.headingDeg`; `FixValidationGate.validateAndApply`/`initializeFromScan` reconcile
a fresh candidate heading by subtracting it, via an explicit named `reconciledHeadingDeg`
intermediate that correctly does NOT re-subtract when falling back to `previous.headingDeg` (which
is already reconciled). `initializeFromScan` now uses `FixConfidenceTier.fromLandmarkCount(...)`
instead of hardcoded `CONFIRMED`, with the landmark count threaded from `LogoScanScreen` →
`NavigationState` → `NavigationSessionSnapshot` → `NavigationSessionInputAdapter`.

**Gap found and closed by the orchestrator**: the implementer correctly stayed within the brief's
listed touch-scope, but that scope omitted the actual UI call site
(`UnifiedNavigationViewModel.kt`/`UnifiedNavigationScreen.kt`) — without it,
`ArSceneViewWrapper`'s new `initialLandmarkCount` parameter would silently default to `0` in
production, misclassifying every first fix as `PROVISIONAL` regardless of real landmark count (a
different wrong behavior than the bug being fixed, not a no-op). Completed the wiring directly: two
small additions mirroring the existing `initialLocalizationHeading` plumbing.

Three new tests in `LocalizationLayerTest.kt` covering fresh-candidate subtraction, no-double-
subtraction on the previous-heading fallback, and tier classification by landmark count. Full
compile + unit test suite pass, independently re-verified after closing the wiring gap. Committed
at `1d0ca78`.
