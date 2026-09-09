Type: grilling
Status: open
Blocked by: 02

## Question

Decide how a migrated screen is proven not to have regressed, before the migration starts.
Decision only — the outcome becomes the acceptance mechanism every batch ticket in Ticket 05
references.

**Why this exists** (charting adversarial pass): Ticket 05's batches say "no visual regression
vs the pre-migration screenshot", but the repo has **no screenshot-test infrastructure** and
nothing was capturing those screenshots. 30 screens × 2 modes × hand-checking on a device is
where regressions slip through.

**Decide:**
1. **Automated vs manual.** Adopt a screenshot-test library for the migrated screens
   (**Roborazzi** — JVM/Robolectric, or **Paparazzi** — no device, no Compose-preview coupling
   issues to check), or rely on a **mandated manual QA checklist** per screen with before/after
   captures from `.scratch/light-mode/before/` (Ticket 02).
2. If automated: does the foundation ticket set it up (adds the dependency, a base test, CI
   wiring), and do batch tickets each add golden images for their screens? What's the CI cost /
   flakiness risk on this project's existing gate set?
3. If manual: the exact checklist (per screen: both modes, AA contrast pairs measured, diff
   against the before/ image, sign-off recorded where?).
4. **Coverage bar** — every migrated screen, or only the core-flow screens, with the rest on
   manual spot-check?
5. Who runs the check for an `agy`-executed batch — `agy` produces the captures, the
   orchestrator diffs them at review time?

**Output**: the answer records the verification method + who owns it + the coverage bar. Feeds
Ticket 03 (§8) and Ticket 05 (batch acceptance criteria).
