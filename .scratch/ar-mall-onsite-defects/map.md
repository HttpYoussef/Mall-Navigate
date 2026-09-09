# AR on-site (mall) navigation defects

## Destination

Diagnose and fix the two AR-rendering defects found in the first-ever real on-site mall test of
the AR indoor-navigation subsystem: (1) the guidance arrow points the wrong direction and drifts
even while the user stands still, and (2) the arrow doesn't stay planted on the floor as the user
walks, and appears visually decoupled from a separate white marker overlay. The 2D map/route and
landmark recognition worked correctly on-site — this effort is scoped to the AR-rendering layer
only. Reaching the destination means: both symptoms are fixed, verified against a fresh on-site
mall retest, and any fix that changes the AR subsystem's architectural contract has gone through
this repo's required architecture-review cycle.

## Notes

- **Domain**: `app/src/main/java/com/example/mallar/ar/` and
  `app/src/main/java/com/example/mallar/navigation/` (Kotlin, Android, ARCore + SceneView 2.2.1).
  Governance: `docs/ar/Engineering Architecture and Guidlines/README.md` (frozen source of truth
  `AR_Subsystem_Redesign_Final.md`; a "major design change" — layer responsibility, ownership
  boundary, state-machine transition, mandatory policy, or numeric architectural parameter —
  requires a full stress-test/approval-review cycle before landing; tuning/implementation fixes
  don't).
- **Standing record**: `docs/ar/Implementation/Phases/Phase 9/Final_System_Acceptance_Sign_Off.md`
  explicitly deferred on-site mall validation and warns that any defect found during real mall
  testing should not be assumed to belong to whatever phase was most recently worked — read before
  assuming a fix is scoped to one file.
- **Plan, don't do — overridden.** This effort's destination is "diagnose and fix," not a
  spec/decision handoff, per explicit user instruction. Task tickets on this map mean *make the
  actual code change*, not just decide what to do. Grilling/research tickets still only decide.
- **Second opinion already taken, twice.** Per user instruction, a full read-only investigation
  was delegated to the `codex` CLI and its key claims were independently spot-verified against the
  source before charting this map. Full findings:
  [codex-investigation-findings.md](codex-investigation-findings.md) (prompt:
  [codex-investigation-prompt.md](codex-investigation-prompt.md)). Every ticket below traces back
  to that document — zoom into it for the file:line evidence behind each ticket's question. Once
  charted, the ticket set itself was put back to codex for an adversarial read-only debate before
  handoff to the implementer; see [codex-debate-findings.md](codex-debate-findings.md) — it split
  Ticket 02, loosened Ticket 05's blocking, reframed Ticket 06, and graduated the
  `RenderPoseSmoother` question into Ticket 09.
- **Evidence**: two on-site screenshots from the same AR session (seconds apart, same route/
  distance metadata) at `Testing images/` in the repo root — show the arrow jumping position/
  rotation while a separate white-dot floor pattern stays fixed.
- Skills to consult: "grilling" + "domain-modeling" for the architecture-contract ticket; no
  research tickets on this map (the codex investigation + direct code verification already
  settled the relevant facts).

## Decisions so far

- [Ticket 01 — disable plane debug visualization](issues/01-disable-plane-debug-visualization.md):
  resolved and committed (`64775a3`). `planeRenderer.isEnabled`/`.isVisible` both set false in
  `ArSceneViewWrapper.kt`; confirmed no other code depended on it.
- [Ticket 09 — wire or remove RenderPoseSmoother](issues/09-wire-or-remove-render-pose-smoother.md):
  resolved and committed (`940f112`). Removed as dead code rather than wired in — stabilization is
  already covered by ARCore's VIO tracking plus `CorrectionInterpolator`; wiring it in risked
  double-filtering and leaking smoothing into placement logic.
- [Ticket 06 — harden floor-plane selection](issues/06-harden-floor-plane-selection.md): resolved
  and committed (`d1f28bb`). Added a 5.0m max-distance gate on the nearest-plane fallback and fixed
  the return to send damped elevation instead of raw `planeY`; judged as ordinary tuning, not an
  architectural parameter. Remains a plausible secondary contributor, not confirmed root cause.
- [Ticket 02 — add AR instrumentation](issues/02-add-ar-instrumentation.md): resolved and committed
  (`5f5c7c2`). Diagnostic logging (no behavior change) across `ArAnchorRenderer`,
  `ArSceneViewWrapper`, `LocalizationLayer`, `FloorPlaneConfidenceMonitor`, ready for
  [Ticket 08](issues/08-capture-onsite-mall-traces.md)'s on-site traces.
- [Ticket 05 — reconcile marker rotation](issues/05-reconcile-marker-rotation.md): resolved and
  committed (`026864c`). Marker rotation now recomputed and updated alongside position on every
  transform correction; added testability seams (internal visibility, nullable `update()` params)
  to make this testable headlessly — verified production call site unaffected. Three new tests.

All five code-implementable frontier tickets (01, 02, 05, 06, 09) are now resolved and committed.
- [Ticket 03 — settle the heading-frame contract](issues/03-settle-heading-frame-contract.md):
  resolved via live grilling (no code yet). `headingDeg` stays the ARCore-world↔facility rotation,
  but must now be computed by measuring the real ARCore camera yaw at accept-time instead of
  hardcoding `0f`; the first-fix-of-session single-landmark case ("esla") should use the *already-
  existing* `FixConfidenceTier.PROVISIONAL` (currently bypassed via a hardcoded `CONFIRMED` in
  `initializeFromScan`), not a new mechanism. Confirmed: no architecture revision required — both
  fixes land inside the existing contract. [Ticket 04](issues/04-fix-heading-frame-calibration.md)
  is now unblocked for direct implementation.

- [Ticket 04 — fix heading-frame calibration](issues/04-fix-heading-frame-calibration.md): resolved
  and committed (`1d0ca78`). Real ARCore yaw now measured and reconciled into `headingDeg`;
  first-fix tier now classified by actual landmark count instead of hardcoded `CONFIRMED`. Delegate
  left the UI call site unwired (default `initialLandmarkCount = 0` would have silently
  misclassified every first fix) — closed directly by the orchestrator before landing.

Remaining open: [Ticket 07](issues/07-onsite-mall-retest.md) (unblocked — 01, 04, 05, 06, 09 all
resolved) and [Ticket 08](issues/08-capture-onsite-mall-traces.md) (HITL, non-blocking, whenever
mall access is available). Both are on-site/physical — nothing left for a coding agent to do on
this map.

- [Delegate to codex before charting](codex-investigation-findings.md): investigated both
  symptoms against the real source and the mall screenshots; findings independently verified by
  Claude (grep + direct file reads) before this map was created.
- [Plan sign-off](codex-investigation-findings.md#3-shared-or-independent-root-cause): user
  approved the 6-item fix boundary as-is, expanded below into 7 tickets (the heading-frame fix
  item was split into a contract-decision ticket + an implementation ticket).
- [Pre-handoff debate](codex-debate-findings.md): before delegating to the implementer, codex
  reviewed the ticket set adversarially — Ticket 02 split into instrumentation (02) + non-blocking
  trace capture (08); Ticket 05's hard block on 04 downgraded to a coordination note since the
  rotation fix is self-contained; Ticket 06 reframed as a plausible secondary contributor, not a
  confirmed cause, with explicit threshold-ownership language added; `RenderPoseSmoother` graduated
  from fog into Ticket 09. No tickets were deleted.

## Not yet specified

- Whether the numeric anchor-window size / correction-interpolation duration need retuning once
  real churn data comes in from [Ticket 08](issues/08-capture-onsite-mall-traces.md) — can't tell
  yet whether current values are wrong or just starved of good input data.

## Out of scope

- (none yet)
