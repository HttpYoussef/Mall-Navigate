# Codex debate: pre-handoff review of the ticket set

Read-only adversarial second opinion via `codex exec` (relay, `--read-only`), asked to defend or
concede six contested points about the map/ticket structure before handoff to the implementer.
Full brief: see this session's history (not persisted separately — the points are reproduced
inline below via codex's verdicts).

## Verdicts

**A. Ticket 03's gate on 04 — partial, narrowed.** The architecture-review requirement is real
(no single heading owner currently exists across Localization/ArSceneViewWrapper/SensorFusion),
so keep Ticket 03. But it should explicitly allow "no architecture revision required" as a valid
outcome, and Ticket 04's review-gate should only bind if 03's answer actually changes
ownership/contract — not unconditionally.

**B. Ticket 02 — disagree that traces are a prerequisite for the code fixes.** Tickets 04/05/06 are
all statically diagnosable (hardcoded-zero heading, position-only correction, explicit nearest-
plane fallback) — mall trace data helps validate and tune, but isn't needed to write the fixes.
Split into code instrumentation (parallelizable, no blockers) and physical trace capture (HITL,
non-blocking, lower priority).

**C. Ticket 05's hard block on 04 — partial, downgraded to a coordination note.** The rotation-sync
fix is self-contained regardless of what `headingDeg` ends up meaning; the only real risk is
Ticket 05 needing to consume whatever transform contract Ticket 04 finalizes before merge. Remove
the hard dependency, keep a compatibility note.

**D. `RenderPoseSmoother` — disagree with leaving it as fog.** It's an independently gradable,
already-scoped implementation question (wire the existing smoother into the live frame loop, or
remove it) that doesn't depend on the heading-frame outcome. Graduate it to its own ticket now.

**E. Ticket 06 — partial, reframe as secondary.** The nearest-plane fallback and the dead damped-
elevation code are real, verifiable defects, but there's no on-device evidence they specifically
caused the reported symptom (vs. the plane-debug overlay, which is the primary explanation).
Keep the ticket, but label it a plausible secondary contributor, not a confirmed root cause.

**F. Implementation-readiness of 01/05/06 — disagree, tightened.**
- Ticket 01: name the exact integration seam (`ManagedARSceneView` construction) and require
  checking the property names against the actual SceneView 2.2.1 API.
- Ticket 05: specify the rotation-recompute formula and the requirement to preserve the marker's
  base pitch, and name a test target.
- Ticket 06: "tight proximity," "compatible floor height" had no thresholds — an implementer can't
  safely invent numeric thresholds that might themselves be architectural parameters. Made
  explicit in the ticket.

## Pre-handoff change list (codex's, applied below)

- Ticket 03 edited: "no revision required" is now an explicit valid outcome; 04's gate is
  conditional on 03 actually changing ownership/contract.
- Ticket 02 split into 02 (instrumentation, no blockers) and 08 (trace capture, HITL, non-blocking).
- Ticket 05: hard `Blocked by: 04` removed, replaced with a compatibility note.
- New Ticket 09 added: wire or remove `RenderPoseSmoother`.
- Ticket 06 reframed as a secondary/plausible contributor with explicit threshold requirements.
- Tickets 01 and 05 tightened with exact seams/formulas/test targets.
- No tickets deleted.
