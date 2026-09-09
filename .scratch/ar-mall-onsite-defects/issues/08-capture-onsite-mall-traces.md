Type: task
Status: open

## Question

Capture two real on-site traces at the mall using the instrumentation from
[Ticket 02](02-add-ar-instrumentation.md), for validating the fixes in this map and tuning any
numeric thresholds they introduce. HITL — requires physical mall access — and explicitly
**non-blocking**: per [codex-debate-findings.md, point B](../codex-debate-findings.md), nothing
else on this map is gated on this ticket. Do it whenever mall access is available, in parallel
with the code tickets.

1. A stationary 30-second trace standing in front of a single-landmark storefront (reproduce the
   "esla" case) — the scenario where the arrow drifted/pointed wrong while not moving.
2. A slow walk trace along the reserved route, covering the reflective-flooring stretch, to
   capture the "not glued to floor" symptom. Do this after
   [Ticket 01](01-disable-plane-debug-visualization.md) lands so the trace isn't confused by the
   plane-debug overlay.

Save both traces into `.scratch/ar-mall-onsite-defects/traces/` and summarize what they show in
the resolution.
