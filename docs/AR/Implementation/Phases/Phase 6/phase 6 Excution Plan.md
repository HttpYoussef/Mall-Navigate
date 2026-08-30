# Phase 6 Execution Plan — Anchor Management Core Lifecycle

## Summary

Implement Module 6 as the bridge between the Phase 5 facility transform and the existing SceneView/ARCore rendering surface. Replace the Phase 4 diagnostic cube in live Camera mode with route-derived AR markers while deferring deviation recovery, Transition Mode, Arrival, and full rendering polish to later phases.

## Implementation changes

- Add a testable anchor-window planner and `AnchorManagementLayer`.
- Consume `RoutePathLayer` metadata and the latest validated `FacilityTransform`.
- Maintain 10 anchors ahead and 2 trailing by default, never exceeding the configured maximum.
- Create and prune ARCore anchors only at the beginning of an AR frame; detach/destroy removed anchors after their fade-out.
- Convert facility coordinates into ARCore-local positions using the Phase 5 transform and a fixed floor-relative height.
- Use standard markers for ordinary nodes and distinct turn markers for direction changes of approximately 120° or more.
- Add frame-driven transparent-material fade-in/fade-out transitions.
- Consume accepted transform updates with a monotonic version at the fast-cycle frame boundary.
- Apply corrections over eight frames by default; coalesce multiple pending fixes to the latest accepted transform.
- Remove the Phase 4 diagnostic cube from the live Camera-mode path while retaining its source for diagnostic use.
- Destroy and detach all managed anchors during wrapper disposal.

## Safe reliability addition

Use generation tokens during frame-boundary reconciliation so stale asynchronous updates cannot mutate a newer anchor window. Stress-test this with rapid route-window and transform changes before enabling the live integration.

## Tests and verification

- Window bounds and ahead/trailing selection on short, medium, and long routes.
- Turn-marker selection around the 120° threshold.
- Facility-to-local placement calculations.
- Multi-frame correction interpolation and latest-fix coalescing.
- Stale-generation rejection and idempotent anchor disposal.
- Randomized rapid reconciliation stress tests.
- `:app:testDebugUnitTest`.
- `:app:assembleDebug`.
- Galaxy S22 Ultra validation for world-locking, bounded marker count, smooth correction, turn markers, lifecycle switching, and absence of ARCore crashes/leaks.

## Explicit exclusions

Phase 6 does not implement deviation classification, route rebuilding, Transition Mode, Arrival, Module 8 recovery decisions, render-level pose smoothing, plane-confidence fallback, deprecated overlay deletion, or frozen-document changes.
