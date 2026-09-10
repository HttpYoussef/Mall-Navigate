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

This ticket also **owns the before-screenshot baseline** (moved out of Ticket 02): capturing
`.scratch/light-mode/before/` — every route screen, both modes — is only meaningful once the
fixture approach below is decided.

**Decide:**
1. **Automated vs manual.** Adopt a screenshot-test library for the migrated screens
   (**Roborazzi** — JVM/Robolectric, or **Paparazzi** — no device), or rely on a **mandated
   manual QA checklist** per screen with before/after captures.
2. **Fixture / harness contract** (codex C9) — the blocker for *any* automated approach, and for
   consistent manual captures. The real screens depend on: Firebase auth state
   (`SignInScreen.kt:67`), camera + provider lifecycle (`LogoScanScreen.kt:257`), runtime
   permissions, `PlaceRepository` / `MallGraphRepository` data, async Coil loads, active locale,
   and infinite glow animations. There are **no `@Preview` providers or fixture builders** in the
   repo. Decide the foundation-owned harness: fake repositories + auth, a fixed route list,
   permission/camera stand-ins, fixed viewport + locale, animations disabled, and an **explicit
   list of screens that cannot be captured on the JVM** (and how those are verified instead).
3. If automated: does the foundation ticket set up the dependency + base test + CI wiring, and do
   batch tickets each add golden images? CI cost / flakiness on the existing gate set?
4. If manual: the exact checklist (per screen: both modes, AA contrast pairs measured, diff
   against `before/`, sign-off recorded where?).
5. **Coverage bar** — every migrated screen, or core-flow screens automated + the long tail on
   manual spot-check (ties to the core-flow milestone — codex C10)?
6. Who runs the check for an `agy`-executed batch — `agy` produces the captures, the orchestrator
   diffs at review time?

**Output**: the answer records the verification method + the fixture harness + who owns it + the
coverage bar; `before/` captured (or the can't-capture list). Feeds Ticket 08 and Ticket 05.
