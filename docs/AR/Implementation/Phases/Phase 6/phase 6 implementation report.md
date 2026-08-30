# Phase 6 Implementation Report

## Status

Phase 6 — Anchor Management Core Lifecycle — is implemented and integrated with the existing Phase 5 localization transform and the manager-owned ARCore session. The implementation preserves the approved architecture and keeps deviation recovery, Transition Mode, Arrival, and later rendering polish outside this phase.

## Requirements implemented

- A bounded route-derived anchor window maintains up to 10 nodes ahead and 2 trailing nodes, with a configurable maximum of 15 active anchors.
- Ordinary route nodes use standard cyan markers; direction changes at approximately 120° or more use larger amber turn markers.
- Facility coordinates are converted into ARCore-local coordinates through the validated Phase 5 `FacilityTransform`.
- Anchors are created from the manager-owned ARCore `Session` at the AR frame boundary and are updated only while tracking is valid.
- Marker fade-in/fade-out is frame-driven, and accepted localization corrections are interpolated over eight frames.
- Transform revisions are consumed monotonically; the latest accepted transform is coalesced into the active correction.
- The Phase 4 diagnostic cube is no longer created in the live Camera-mode path; its diagnostic source remains available.
- Anchor planning and placement are deterministic and testable without a device.

## Main implementation files

- `app/src/main/java/com/example/mallar/ar/AnchorManagementLayer.kt`
  - Anchor window planning, turn classification, facility/local conversion, and correction interpolation.
- `app/src/main/java/com/example/mallar/ar/ArAnchorRenderer.kt`
  - SceneView/Filament marker ownership, ARCore anchor creation, bounded reconciliation, fade transitions, correction application, and teardown handling.
- `app/src/main/java/com/example/mallar/ar/ui/ArSceneViewWrapper.kt`
  - Shared-session frame integration, Phase 5 transform consumption, lifecycle/surface gating, and persistent Map↔AR SceneView hosting.
- `app/src/main/java/com/example/mallar/ar/ui/ManagedARSceneView.kt`
  - Choreographer frame gate and early surface-inactivation notification.
- `app/src/main/java/com/example/mallar/ui/navigation/UnifiedNavigationScreen.kt`
  - Supplies route metadata and keeps the same native AR surface mounted while Map mode pauses and hides it.
- `app/src/main/java/com/example/mallar/ar/LocalizationLayer.kt`
  - Exposes the monotonic transform revision consumed by the fast anchor cycle.
- `app/src/test/java/com/example/mallar/ar/AnchorManagementLayerTest.kt`
  - Bounds, short-route, turn-threshold, coordinate, interpolation, and randomized reconciliation tests.

## Lifecycle and reliability corrections

Device logcat exposed two native teardown races during validation. These were corrected without adding a second camera/session path:

- Surface invisibility gates SceneView frames before ARCore pause and performs native anchor/material cleanup while Filament is still valid.
- `ArCoreSessionManager` serializes lifecycle operations and makes session creation, resume, pause, and destroy idempotent. Duplicate `ARSession.pause()` calls cannot enter native ARCore teardown.
- Map↔AR retains one SceneView/session bridge instead of destroying and rebinding native SceneView state on every mode switch.
- Voice functionality remains enabled in the live navigation screen. It is an independent existing feature and is not treated as a Phase 6 scope violation.

## Scope and frozen-file audit

- No frozen engineering, roadmap, architecture, or testing documents under `docs/AR/` were modified.
- No deviation classification, route rebuilding, Transition Mode, Arrival, Module 8 recovery, or new localization/camera architecture was introduced.
- CameraX remains the owner of the scan camera until disposal; ARCore remains the sole AR camera/session source during navigation.

## Automated verification

Completed successfully:

- `:app:testDebugUnitTest`
- `:app:assembleDebug`

The debug APK assembles successfully and the Phase 6 unit/stress suite passes. Device validation remains required for repeated Galaxy S22 Ultra Camera↔Map↔AR runs, world-locking, bounded marker count, correction smoothness, and crash-free lifecycle behavior.

## Device validation update

The developer reports that the current build is working correctly on the Galaxy S22 Ultra, including the previously failing Map↔AR transition. This is recorded as user-reported device evidence. Exact run counts, timestamps, video, and Logcat capture were not supplied, so those evidence details remain unverified.

## Deliberate reliability additions

The generation-based planner reconciliation, randomized route stress tests, persistent SceneView bridge, serialized ARCore lifecycle, and early native teardown ordering are contained reliability improvements. They do not expand Phase 6 into later navigation behavior or alter the frozen architecture.

## Source evidence excerpts

The report now includes actual implementation excerpts from the files built in the workspace.

### `AnchorManagementLayer.kt`

```kotlin
val start = (nearest - config.trailingCount).coerceAtLeast(0)
val end = (nearest + config.aheadCount).coerceAtMost(route.lastIndex)
return AnchorWindowPlan(
    active = route.subList(start, end + 1).mapIndexed { i, node ->
        AnchorSpec(node, if (isTurn(route, start + i)) AnchorKind.TURN else AnchorKind.STANDARD)
    },
    generation = generation(route, start, end),
    nodeIds = route.subList(start, end + 1).map { it.nodeId }
)
```

### `ArAnchorRenderer.kt`

```kotlin
if (plan.generation != lastPlanGeneration) {
    reconcile(sceneView, session, frame, cameraPose, localPose, transform, transformRevision, plan)
    lastPlanGeneration = plan.generation
}
anchors.values.forEach { managed ->
    managed.marker.materialInstance.setColor(managed.materialColor.copy(alpha = managed.alpha))
    val correction = managed.correction.step()
    managed.marker.position = Position(correction.xMeters.toFloat(), correction.yMeters.toFloat(), correction.zMeters.toFloat())
}
```

### `ManagedARSceneView.kt`

```kotlin
override fun onFrame(frameTimeNanos: Long) {
    if (!isPaused) super.onFrame(frameTimeNanos)
}
override fun onWindowVisibilityChanged(visibility: Int) {
    if (visibility != VISIBLE) { isPaused = true; onRenderSurfaceActiveChanged?.invoke(false) }
    super.onWindowVisibilityChanged(visibility)
    if (visibility == VISIBLE) onRenderSurfaceActiveChanged?.invoke(true)
}
```

### Lifecycle integration

`ArCoreSessionManager.pause()` is synchronized and returns unless the state is `RESUMED`; `ArSceneViewWrapper` pauses the frame gate before pausing ARCore and retains the same SceneView/session bridge across Map↔AR.

### `ArCoreSessionManager.kt` modified lifecycle section

```kotlin
fun pause() = synchronized(lifecycleLock) {
    if (_lifecycleState.value != LifecycleState.RESUMED) return@synchronized
    sessionRef.get()?.let { session ->
        session.pause()
        _lifecycleState.value = LifecycleState.PAUSED
    }
}
fun destroy() = synchronized(lifecycleLock) {
    sessionRef.getAndSet(null)?.let { session ->
        session.close()
        _lifecycleState.value = LifecycleState.DESTROYED
    } ?: run { _lifecycleState.value = LifecycleState.DESTROYED }
}
```

### `ArSceneViewWrapper.kt` and `UnifiedNavigationScreen.kt` modified sections

```kotlin
LaunchedEffect(active) {
    if (active) { sessionManager.resume(); sceneView.isPaused = false }
    else { sceneView.isPaused = true; sessionManager.pause() }
}

ArSceneViewWrapper(
    sessionManager = viewModel.arCoreSessionManager,
    localizationLayer = viewModel.localizationLayer,
    routePathLayer = viewModel.routePathLayer,
    active = isCameraMode
)
```

Voice remains wired independently through `VoiceAssistantManager`, `NavigationSessionVoiceCoordinator`, and the navigation voice control in `UnifiedNavigationScreen.kt`; it is not disabled by Phase 6.

## Raw automated verification excerpt

```text
> Task :app:testDebugUnitTest
> Task :app:assembleDebug

BUILD SUCCESSFUL in 1m 15s
43 actionable tasks: 13 executed, 30 up-to-date
```
