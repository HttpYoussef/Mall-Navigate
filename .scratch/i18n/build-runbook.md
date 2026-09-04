# App-localization build runbook (continuation guide)

This file is the single source of truth for continuing the localization build across
`/compact` boundaries. Update the **Status** table after every commit.

Effort spec: `.scratch/i18n/spec.md`. Tickets: `.scratch/i18n/issues/01..13-*.md`.
Decision record + rationale: agent memory `app-localization-effort.md`.

---

## Status

Branch: **`feat/app-localization`** (off `main` @ `e4ad8b7`). Nothing pushed. Nothing device-verified.

| # | Ticket | State | Commit |
|---|--------|-------|--------|
| 04 | Resource contract + MissingTranslation lint gate | ✅ committed | `6bc341c` |
| 02 | AppCompat per-app-language foundation | ✅ committed | `e6824b6` |
| 05 | Localization primitives: Cairo + formatters | ✅ committed | `c26866c` |
| 06 | Canonical categories + floor helper | ✅ committed | `5213294` |
| 03 | In-app Language screen | ✅ committed | `314936a` |
| 07 | Externalize: auth/onboarding/permissions/mall-picker/splash | ✅ committed | `0548242` |
| 08 | Externalize: home/destination/offers/vouchers/store-detail | ⬜ blocked by 05✅ 06 | — |
| 09 | Externalize: profile/saved-places/settings | ⬜ blocked by 05✅ 06 | — |
| 10 | Externalize: parking | ⬜ blocked by 04✅ 05✅ | — |
| 11 | Externalize: assistant chrome | ⬜ blocked by 04✅ 05✅ | — |
| 12 | Externalize: navigation-guidance | ⬜ blocked by 06, 08 | — |
| 01 | AppCompat migration spike | ⬜ **USER on device** — not an agy task | — |
| 13 | RTL + localization QA sweep | ⬜ **USER on device** | — |

**Recommended remaining order for agy:** ~~06~~ → ~~03~~ → ~~07~~ → **10** → 11 → 08 → 09 → 12.
(03/07/10/11 are all unblocked once 06 lands; 08/09 need 06; 12 needs 06+08.)

---

## Per-ticket loop (the mechanical part)

1. **Write a brief** to `<scratchpad>/brief-NN.txt` (see "Brief template" below).
   Scratchpad dir: `C:\Users\youss\AppData\Local\Temp\claude\C--Users-youss-Downloads-MallAR-main-22-MallAR-main\<session>\scratchpad\`
   (any session's scratchpad is fine; briefs are disposable).
2. **Dispatch** (background):
   ```
   node "C:/Users/youss/.claude/skills/agy-delegate/scripts/relay.mjs" \
     --brief "<path to brief-NN.txt>" \
     --cd "C:/Users/youss/Downloads/MallAR-main 22/MallAR-main" \
     --model "gemini-3.8-flash-high" --timeout 55m
   ```
   Run with `run_in_background: true`. Wait for the completion notification.
   Model id is `gemini-3.8-flash-high` — do NOT pass a separate `--effort` (effort is baked in;
   passing it errors). agy headless writes require `command(*)` in
   `~/.gemini/antigravity-cli/settings.json` `permissions.allow` — already set this session.
3. **Read** `<session>/tasks/<id>.output` — the agy final report (files touched, gate results,
   non-brief changes, device-verification gaps).
4. **Review — do not trust the self-report:**
   - `git diff <files>` — did it do what the brief asked, nothing more?
   - Re-run ALL FOUR gates yourself:
     ```
     ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin lintDebug --console=plain -q
     ```
     (Git Bash, repo root. ~2-4 min. Exit 0 = all pass. lint writes to
     `app/build/reports/lint-results-debug.html`.)
   - Check for leftover/dangling refs to anything deleted (`grep -rn`).
   - Surface (don't silently keep) any non-brief change agy made.
5. **Commit** only the ticket's files (not `.idea/`, `.agents/`, `.claude/`, `AGENTS.md`,
   `docs/agents/`, `skills-lock.json` — those are pre-existing, unrelated, leave untracked/unstaged).
   Commit message format:
   ```
   i18n: <short title> (ticket NN)

   - <bullets>

   Device-verification: <what still needs a device>.

   Drafted via agy-delegate (gemini-3.8-flash-high); orchestrator re-ran gates:
   compileDebugKotlin, testDebugUnitTest, compileDebugAndroidTestKotlin, lintDebug green.

   Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
   Claude-Session: https://claude.ai/code/session_0129sHcUpNePBWLx3932egJt
   ```
6. **Update the Status table above**, then move to the next ticket.

---

## Brief template (what every brief must contain)

- **Task header**: "Ticket NN — <title>. You are implementing ONE ticket. You will NOT commit.
  Do exactly this ticket, nothing more."
- **Read first**: `.scratch/i18n/spec.md` (name the relevant sections), the ticket file
  `.scratch/i18n/issues/NN-*.md`, `.scratch/i18n/resource-contract.md`, and the specific
  source files.
- **Current state**: concrete facts (file paths, line ranges, what's hardcoded, counts).
- **What to change**: numbered, specific. For string externalization: "every user-facing literal
  → `stringResource` + a `values-ar` Egyptian-colloquial translation" (the lint gate will fail
  the build if any `values-ar` key is missing).
- **Do NOT**: never touch `object NavigationState` / the `ar/` package / `NavigationSessionManager`
  / the scan flow / `ChatSystem.kt` category logic / voice engine; don't add es/fr resource files;
  don't commit or branch; only touch what the ticket is about.
- **Gate commands**: the four above, "run yourself before reporting".
- **Report contract**: files changed + why; public APIs; every call site touched; gate
  pass/fail; non-brief changes + why; **acceptance criteria that need a device (list for the human)**.

---

## Reusable primitives already landed (use these, don't reinvent)

- `com.example.mallar.data.WesternDigits` — `format(Int/Long)`, `format(Double, decimals)`,
  `percent(Int)`. Locale.US-fixed → always 0-9. Use for EVERY number shown in the UI; pass the
  result into a `%1$s` placeholder (NEVER `%d` in a resource string — renders Arabic-Indic under
  `values-ar`).
- `com.example.mallar.data.BidiFormatting.isolate(String)` / `String.bidiIsolated()` — FSI/PDI
  wrap for a Latin brand/number embedded in an Arabic string.
- `com.example.mallar.data.AppLanguagePlatform` — `currentLanguage()`, `apply(AppLanguage)`,
  `applyFollowDevice()`, `currentTags()`, `deviceLocales()`.
- `com.example.mallar.data.AppLanguageResolver` — pure; `supported` = [ENGLISH, ARABIC];
  `effective()`, `migrationDecision()`. `AppLanguage` enum has ENGLISH/ARABIC/SPANISH/FRENCH
  (es/fr modelled, NOT in `supported`, NOT shipped).
- `com.example.mallar.ui.theme` — `fontFamilyFor(AppLanguage)`, `typographyFor(...)`,
  `CairoFontFamily`. `MallARTheme` already swaps to Cairo under Arabic + propagates via
  `LocalTextStyle`. New screens need no font work.
- Ticket 06 adds: a `categoryKey → @StringRes` display map and a `floorLabel(Int)` helper —
  once landed, externalization tickets 08/09/12 use those instead of local category/floor literals.

## Gotchas / notes

- **Lint gate is armed**: any key added to `values/strings.xml` without a `values-ar/` twin fails
  `lintDebug`. Every externalization batch must ship its Arabic strings.
- `Theme.MallAR` parent is now `Theme.AppCompat.DayNight.NoActionBar`. The app does its own dark
  mode via Compose (`AppPreferences.isDarkMode`). Ticket 01 spike should sanity-check status-bar
  icon behaviour on device. Not a blocker for the build.
- `R.string.language` is currently unused (ticket 02 removed the old toggle). Ticket 03's Language
  screen re-uses it — do not delete it.
- `gradle.properties` metaspace was raised (256m→768m / heap→2048m) so `lintDebug` doesn't OOM.
- Egyptian colloquial Arabic for all `values-ar` strings; flagged for native review later (spec Q6).
- Windows Git Bash; line-ending warnings (LF→CRLF) on commit are harmless.
- The logo-scan screens (`LogoScanScreen.kt`, `LocalizationConfirmScreen.kt`) are OUT OF SCOPE
  (frozen by AR roadmap) — never externalize them in any ticket.

## Carry-forward items (things a later ticket must pick up)

- ~~**Ticket 07 brief must add**: `+20` E.164 comment in `ui/auth/`~~ — DONE in ticket 07
  (`0548242`): comment added at all 4 construction sites (PhoneAuth, SignIn x2, SignUp).
- **Ticket 07 note**: agy's relay was KILLED after it finished its edits (no result.json / report).
  Orchestrator recovered: reviewed the full diff, confirmed no hardcoded literals remained, ran all
  4 gates (green), fixed one 1-space indent typo in `SignUpScreen.kt`, committed. agy went beyond
  the brief on RTL (correctly): forced LTR on phone/OTP fields + keypads via `LocalLayoutDirection`,
  mirrored the splash corner mark. `phoneDisplay` param in `SignInOtpPhase` is dead (pre-existing).
- **Ticket 06 non-brief changes** (accepted): `DestinationSelectionScreen` category-tile width
  104dp→88dp and swapped 3 category icons (Diamond/Spa/LocalPharmacy) — consequence of the new
  5-category set. `Voucher`/`OfferItem` "Ground Floor"+"1st Floor" both collapsed to `floor = 1`
  (pre-existing demo-data ambiguity). Watch for tile-label clipping ("Perfumes & Cosmetics") in
  ticket 13 QA.
- **Ticket 06 orchestrator fixes** (agy left 3 compile errors — its report was truncated):
  re-added a `Color` import in `DestinationCategoryScreen`, updated 2 missed `Voucher.floorLabel`
  → `floorDisplayLabel(voucher.floor)` call sites (`OffersScreen`, `VoucherDetailsScreen`), and
  routed the `OffersScreen` filter chips through `categoryDisplayLabel`. **Lesson: agy's relay
  can exit 0 with a truncated/empty report and broken code — ALWAYS run the 4 gates yourself.**

## agy behaviour observed

gemini-3.8-flash-high has produced clean, on-brief diffs so far, runs the gates itself, and
surfaces its own non-brief changes + device-verification gaps in the report. Trust but re-verify
every gate. It tends to add small redundant convenience overloads — harmless, let them pass.
