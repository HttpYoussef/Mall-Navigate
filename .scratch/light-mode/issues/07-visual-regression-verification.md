Type: grilling
Status: resolved
Claimed by: Youssef Ibrahim (orchestrator) — grilling 2026-09-10
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

## Answer

Resolved 2026-09-10 via grilling.

1. **Manual QA, not an automated screenshot library.** The migration is colour-only (literal →
   token, no layout change) so visual diffs are trivial to eyeball. Standing up Roborazzi/Paparazzi
   would mean adding the framework plus fakes for Firebase auth / CameraX / permissions / repos
   across 25 screens, with no CI to run goldens against — a multi-day yak-shave dwarfing the
   migration. Rejected.

2. **One automated guardrail (foundation ticket adds it):** a **JVM unit test** in
   `app/src/test/…/ui/theme/` (runs in the existing `testDebugUnitTest` gate, no Android/emulator):
   - every token fg/bg pair meets its target contrast ratio (AA: 4.5:1 body, 3:1 large/UI;
     decorative tokens exempt and listed explicitly),
   - the M3 `ColorScheme` produced by `MallARTheme` has **zero unspecified roles** (codex C1),
   - `brandTeal` is asserted to be *large-text-only* (documented threshold, not a silent pass).
   This is the codex C1/C6 guardrail in executable form.

3. **Per-batch on-device visual sign-off is the USER's** (Q3 = b). For each migration batch: the
   orchestrator reviews the `agy` diff + runs the code gates (`:app:compileDebugKotlin`,
   `:app:testDebugUnitTest` incl. the new contrast test, `:app:compileDebugAndroidTestKotlin`,
   `lintDebug`); then the **user runs the affected screens on a device**, both modes, against the
   checklist below, and signs the batch off before the next one starts. No batch lands visually
   unverified; the orchestrator does not self-approve the visuals.

4. **Coverage + baseline.** Every route screen: a **before** + **after** capture, **both modes**,
   checked against the per-screen AA checklist. The 3 always-dark screens (`LogoScanScreen`,
   `UnifiedNavigationScreen`, `ParkingCameraScreen` — plus `StaticMapScreen`, `SplashScreen`) get
   only: "renders dark, system-bar icons visible, no light bleed". The **before/ baseline is
   captured lazily per batch** — capture each screen's current state on the device immediately
   before its batch runs, save under `.scratch/light-mode/before/<batch>/`. No bulk capture now.

**Per-screen QA checklist** (batch tickets reference this):
- [ ] Light mode: screen background, cards, text all from the token set — no dark bleed, no
      white-on-white, no invisible text.
- [ ] Light mode: status-bar + nav-bar icons visible (dark icons on light chrome).
- [ ] Dark mode: unchanged or improved vs the before/ capture — no new regressions.
- [ ] Every text/icon element meets AA against its actual background (spot-check the ones the
      inventory flagged: `CyanGlow`, greens, `textSecondary`, teal-as-text).
- [ ] Primary action uses `accent`; teal text/links use `accentText`; no `brandTeal` as small text.
- [ ] `agy` diff introduced zero new raw `Color(0x…)` / `Color.White` etc. in the batch's files.

**Milestone sign-offs** (from the core-flow milestone): a fuller device pass at (a) core-flow
complete, (b) all-screens complete.

**Feeds**: Ticket 08 (§ verification rules), Ticket 05 (batch acceptance criteria = the checklist
above + the code gates).
