Type: task
Status: resolved

## Question

Harden `FloorPlaneConfidenceMonitor`'s plane selection and elevation output, which currently risks
vertical jitter and mis-placed floor anchors on reflective mall flooring.

**Status: plausible secondary contributor, not a confirmed root cause.** The map's primary
explanation for "arrows not glued to the ground" is the plane-debug overlay
([Ticket 01](01-disable-plane-debug-visualization.md)); this ticket hardens a real, independently-
verifiable defect that *could* also produce floor-attachment symptoms, but there's no on-device
evidence (yet) that it specifically caused the reported session. Don't treat resolving this as
proof the original bug is fixed — [Ticket 07](07-onsite-mall-retest.md) is the actual proof.

**Verified in code**: `FloorPlaneConfidenceMonitor.kt:47-48` already does a containment check
first (`isPoseInPolygon`), but if nothing contains the point it falls back
(`FloorPlaneConfidenceMonitor.kt:49-53`) to the *nearest* tracked horizontal plane by raw distance
with **no maximum-distance cap** — a route node can be anchored to an unrelated plane arbitrarily
far away. Separately, `FloorPlaneConfidenceMonitor.kt:55-65` computes a damped
`rollingFloorElevation` but the `return` on line 65 sends back the *undamped* `planeY`, so the
damping logic never reaches a caller. Full detail:
[codex-investigation-findings.md § 2, app-level floor-placement risks](../codex-investigation-findings.md#app-level-floor-placement-risks-still-exist).

**Do**:
1. Add an explicit maximum-distance threshold (meters) gating the nearest-plane fallback; when no
   plane both is trackable and falls within it, use a clearly-defined fallback (e.g. the existing
   `fallbackElevation` parameter) instead of an arbitrary distant plane.
2. Return `rollingFloorElevation` (post-update) instead of `planeY` on line 65.

**Threshold ownership**: the exact meters value in (1) is not specified here on purpose — pick and
justify it in the resolution (e.g. against typical mall corridor/storefront widths). If the
assigned reviewer would treat that number as one of this subsystem's approved numeric parameters
under `docs/ar/Engineering Architecture and Guidlines/README.md`'s versioning policy, flag it for
review rather than landing it silently — don't assume a threshold is "just tuning" without
checking.

Independent of the heading-frame work ([Tickets 03-05](03-settle-heading-frame-contract.md)) — can
proceed in parallel.

## Answer

Both fixes implemented (delegated to agy): added `maxFallbackDistanceMeters` (default **5.0m**,
constructor-overridable, `DEFAULT_MAX_FALLBACK_DISTANCE_METERS`) gating the nearest-plane fallback
via squared-distance `takeIf`; when nothing qualifies, `matchingPlane` is `null` and the existing
no-plane branch runs (`fallbackElevation`/`rollingFloorElevation`, confidence 0). The elevation
return now sends the post-update `dampedElevation` instead of raw `planeY`.

**Threshold judgment call (orchestrator, not escalated to architecture review)**: 5.0m is ordinary
tuning of a floor-attachment heuristic, not one of the numeric parameters
`docs/ar/Engineering Architecture and Guidlines/README.md` calls out (anchor-window size,
correction duration, trust thresholds) — it's a local default with a documented rationale
(corridor/storefront widths) and a constructor override, not a fixed architectural contract value.
Flagging here per the ticket's own instruction rather than silently deciding; revisit if
[Ticket 08](08-capture-onsite-mall-traces.md)'s traces suggest 5.0m is wrong for this mall's actual
corridor geometry.

Three new unit tests added to `FloorPlaneConfidenceMonitorTest.kt` covering: far-plane rejection,
near-plane acceptance, and the exact damped-vs-raw elevation math. Full `render` package test suite
(10 tests) and compile pass, independently re-verified by the orchestrator. Committed at `d1f28bb`.
