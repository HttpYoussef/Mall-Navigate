# Light mode & the app colour-token system

`wayfinder:map` — local-markdown tracker. Children live in `issues/`.

## Destination

A **locked light-mode design spec + colour-token architecture**, written to `docs/Theming/`,
plus a set of **batched migration tickets** ready to hand to an implementer. Reaching the end
means: the light palette is chosen and contrast-verified, the token system (how ~30 screens stop
hardcoding colours) is fully specified down to the Compose API surface, dark-mode token values are
captured (redesign-free), a lint gate to stop regressions is decided, and the migration is sliced
into implementer-sized tickets each with acceptance criteria. **Plan-only** — no screen code is
changed by this map; the migration tickets are the handoff boundary.

## Notes

- **Domain**: Android / Jetpack Compose / Material 3. Theme code: `app/src/main/java/com/example/mallar/ui/theme/`
  (`Theme.kt`, `Color.kt`, `Type.kt`). Screens: `app/src/main/java/com/example/mallar/ui/**`.
  Glossary: `CONTEXT.md` (note "Localization" = physical position-fixing, **not** i18n; "App
  language" is the i18n concept — neither is this effort).
- **Branch**: `feat/colors-light-modes`, stacked on the unmerged `feat/app-localization` (draft
  PR #1). Independent of i18n work; no rebase pressure, not pushing soon.
- **Current state (charting recon)**: `MallARTheme` defines proper M3 `lightColorScheme` /
  `darkColorScheme` switched by an `AppPreferences.isDarkMode` boolean (no follow-system). Almost
  nothing uses it: ~567 hardcoded colour literals across 30 UI files; only 3 files ever read
  `MaterialTheme.colorScheme`. Screens hardcode brand colours or roll a per-screen palette
  (`rememberHomeColorScheme(isDarkMode)` in `HomeSharedComponents.kt`, copied to
  Home/Offers/Vouchers/Destination). 7 screens never branch on `isDarkMode` at all → effectively
  light-only. Three "brand" accents float around: Teal `#1A8C8C`, purple `#9D5CFF` (`DesignPurple`,
  drives Home flow), gold `#C39D51` (`BrandSecondary`, ~unused).
- **Plan, don't do**: default wayfinder plan-only mode holds. Every ticket below resolves a
  *decision*; none change screen code. The migration batch tickets that Ticket 05 produces are
  **not** wayfinder decision tickets — they run as ordinary `agy` delegate-and-review cycles
  (orchestrator reviews each diff), and this map only tracks their status if the user wants
  progress reflected here.
- **Adversarial debate — done** (2026-09-10). Round 1 (2026-09-09): all external CLIs were down,
  so a self-review pass ran — [self-review-findings.md](self-review-findings.md) — folding in
  Tickets 06 + 07, narrowing Ticket 01, tightening scope language. Round 2 (2026-09-10): `codex`
  ran the full independent debate on all 7 tickets — [codex-debate-findings.md](codex-debate-findings.md)
  — 15 findings, all folded in as ticket refinements: **Ticket 03 split into 03 (palette/tokens)
  + 08 (Compose API + component matrix)**; Ticket 02 outputs a route manifest + classification;
  Ticket 06 gains the route-aware system-bar contract, synchronous mode bootstrap, and
  authoritative-mode decision; Ticket 07 gains the fixture-harness contract and owns the
  before-screenshots; a core-flow milestone added to Ticket 05. Locked decisions were not
  reopened.
- **MAP LOCKED — 2026-09-10** (user sign-off). Both adversarial rounds complete, nothing else
  owed. Re-opening a locked decision needs a fresh user call, not an in-session edit.
- **CHARTING COMPLETE — 2026-09-10.** All 8 tickets resolved along 02 → 06 → 03 → 08 → 04 → 05
  (01, 07 parallel). Spec written: `docs/Theming/README.md` + `palette.md` +
  `migration-tickets.md`. The map is now in its **execution phase** — see the RESUME POINT and
  the Migration progress ledger. This map's plan-only mandate is fulfilled; the batch tickets are
  the handoff boundary and `agy` runs them under orchestrator review.
- **▶ RESUME POINT (2026-09-13)**: **ALL 8 TICKETS RESOLVED. CHARTING COMPLETE.** Spec:
  `docs/Theming/README.md` (§1–§10) + `palette.md` + `migration-tickets.md` (13 batch tickets
  00–12). **Executing the migration, in progress.** **Batches 00-07 are landed AND
  user-signed-off-on-device** (commits `1473c4a`, `c7b9178`, `0eb210b`, `20e074e`, `2b90473`,
  `c7a2f64`, `b5ec8f5`, `d0f4a94` + their ledger-update commits). **CORE-FLOW MILESTONE COMPLETE**
  — light mode is correct on every screen a regular shopper touches (Auth, Home, Offers/Vouchers,
  Destination, Profile/Settings/Saved). **Dispatching Batch 08 (Parking suite —
  `ParkingHomeScreen.kt`, `ParkingCameraScreen.kt`, `ParkingScanResultScreen.kt`,
  `ParkingMapScreen.kt` — the single biggest hotspot, 54 raw literals across 4 files)** to `agy`
  per `docs/Theming/migration-tickets.md` § "Batch 08". The working loop for every batch
  (established over 00-07, keep doing this):
  1. write a brief grounded in the exact current file contents (grep/read first — don't assume
     the migration-tickets.md summary is precise enough on its own; past batches needed real
     line numbers and exact legacy-constant lists);
  2. flag any "legacy constants still used by other unmigrated files" risk explicitly in the
     brief (this has mattered twice: `Color.kt` in Batch 00, `HomeSharedComponents.kt`'s design
     tokens in Batch 02) — never let `agy` delete a shared constant still referenced elsewhere;
  3. dispatch via `node "<agy-delegate skill dir>/scripts/relay.mjs" --brief ... --cd "<repo>"
     --model gemini-3.8-flash-high --effort high --out-dir <scratchpad>/agy-batchNN
     --timeout 30m`, `run_in_background: true`;
  4. on completion: read `result.json`'s `finalMessage` + `touchedFiles`, then **actually read the
     diff** (`git diff -w <files>`) — don't just trust the self-reported gate outcomes;
  5. independently re-run the 5 gates yourself (`compileDebugKotlin`, `testDebugUnitTest`,
     `compileDebugAndroidTestKotlin`, `checkThemeColors`, `lintDebug`) — every batch so far has
     been clean-ish but Batches 01 and 02 each had one small defect worth fixing (an unsafe
     `Activity` cast in Batch 01; a wrong default token in Batch 02) that only surfaced from
     reading the diff, not from the gate output;
  6. commit the verified diff yourself with a message describing what changed + what you fixed;
  7. update the "Migration progress" ledger below (`landed (<hash>) — awaiting device sign-off`),
     commit that too;
  8. tell the user what to check on-device for that specific batch, and **wait for their
     sign-off before dispatching the next batch** — do not auto-proceed even though the pattern
     is now well-established.
  Nothing is pushed. `config/theme-migration-allowlist.txt` shrinks per batch; do not touch a
  file's allowlist line unless that batch's edit actually changed its match count.
- **Sync mode bootstrap — IN SCOPE** (user sign-off, resolves codex C4): the foundation ticket
  reads the persisted dark-mode preference synchronously before the first themed frame. Ticket 06
  §6 specifies *how*; that it happens is settled.
- **Execution after Ticket 05**: the user wants the migration itself delegated to `agy` and the
  orchestrator to run the queue to completion. Note this stays a delegate-and-review loop — `agy`
  writes each batch, the orchestrator reviews the diff and lands it. The *decision* tickets
  (01, 03, 04, 06, 07, 08) are HITL and still need the user in the loop; only Ticket 02 (AFK) and
  the post-05 migration batches are `agy` work.
- Skills each session should consult: `grilling` + `domain-modeling` (for the spec / API ticket),
  `prototype` (for the palette ticket). No research tickets — Material 3 + this codebase are the
  only sources and both are in hand.
- **Core-flow milestone** (codex C10): the migration is ~13 implementer sessions, not ~8.
  Ticket 05 defines a checkpoint — foundation + shared renderers + Home-flow + Profile/Settings +
  XML + system-bars = "light mode correct on the screens users actually see". The long tail
  (Parking, Localization, Navigation chrome, bespoke-dark screens) lands after. The all-screen
  destination stays; the milestone is where the effort is usable if it pauses.

### Settled while charting (the frame every ticket inherits)

- **What's wrong**: light mode is both *broken* (dark colours bleed through, unreadable text,
  light-only screens) and *inconsistent* (every screen its own ad-hoc palette).
- **Scope**: build one central colour-token system that fixes light mode; dark mode adopts the
  same tokens and benefits, but dark-mode visual polish is not chased this round. All 30 screens
  in scope for token migration; core user flow (Home, Destination, Offers, Vouchers, Profile,
  Settings, Parking, Navigation) prioritised.
- **Token architecture**: **hybrid** — one palette definition feeds both an M3 `ColorScheme`
  (keeps stock M3 components correct) and a small `MallColors` extension via `CompositionLocal`
  for app-specific slots (card bg, accent glow, border, divider, always-dark surfaces, textSub).
- **Accent**: **Teal `#1A8C8C` is the primary accent; purple `DesignPurple` is retired.** Gold
  stays available as a sparing secondary if the palette prototype wants it.
- **Mode switching**: unchanged this round — the manual `AppPreferences.isDarkMode` boolean stays.
- **Migration strategy**: foundation ticket (build the token system + migrate 2-3 pilot screens)
  then area-batched migration tickets, each one implementer session, each verified on-device in
  both modes. A lint gate bans raw colour literals in `ui/` outside the theme package.
- **Always-dark surfaces**: camera/AR screens (`UnifiedNavigationScreen`, `ParkingCameraScreen`,
  `LogoScanScreen`) stay dark in light mode — the token system models an `alwaysDark`/scrim set.
  Splash screen is decided with the palette prototype.
- **Dark values**: consolidated onto the new tokens in the same spec. The existing dark colours
  are themselves inconsistent (`DeepNavyBg #06131A`, `DarkBackground #121218`, `nav_surface
  #0A0F1E`, `ai_card #0D1B2A` …); where old code used mismatched darks for the same role the
  token value wins and minor dark-mode shifts are accepted. Not a dark-mode *redesign* — dark
  polish stays out of scope — but not literally value-preserving either. Migration converts both
  modes in one pass.
- **Accessibility**: **WCAG AA is a hard acceptance bar** — 4.5:1 body text, 3:1 large text / UI
  components / borders. Decorative elements (glows, faint dividers) exempt.
- **Deliverable**: a `docs/Theming/` spec as the single source of truth + batched migration
  tickets that reference it. No code stubs from this map — the foundation ticket's implementer
  writes `Theme.kt` / `MallColors`.
- **Palette prototype fidelity**: swatch + token table plus Home and Profile mocked in the
  proposed light theme, for one round of user reaction before the palette locks.

## Decisions so far

<!-- one line per closed ticket; zoom the link for detail -->

- [Ticket 02 — screen + XML colour inventory](issues/02-screen-color-inventory.md): resolved
  (delegated to `agy`, orchestrator-verified). Deliverable
  [screen-inventory.md](screen-inventory.md). Real literal count is **198** in `ui/**` (214
  app-wide), not ~567. **`res/values/colors.xml` (71 colours) is 100% dead — delete, don't
  migrate.** `themes.xml` hardcodes `#06131A`, no `values-night/`. Only 3 files use
  `MaterialTheme.colorScheme`; only 2 touch `isAppearanceLightStatusBars`. Scope +1:
  `voice/VoiceAssistantOverlay.kt` is in; `ar/render/GuidanceVisualFactory.kt` (3D) is out. Live
  AA failures found: `CyanGlow` text ≈1.2:1, green `#4CAF50` ≈2.5:1. 13-batch migration grouping
  proposed (Ticket 05 finalises).

- [Ticket 06 — XML theme + system-bar strategy](issues/06-xml-theme-and-system-bar-strategy.md):
  resolved via grilling. `themes.xml` window/bar colours become **runtime-owned** by `MallARTheme`
  (Compose `isDarkMode` is the sole mode source; XML pinned to light, no `values-night`, no
  `setDefaultNightMode`). **`colors.xml` deleted whole** (100% dead). Splash keeps one fixed
  brand background. **Route-aware system-bar contract**: theme sets the default; an *always-dark
  route* opts out via a `DarkSystemBars()` composable that restores on navigation away.
  Edge-to-edge **not** adopted (keep manual `statusBarsPadding`). First-frame flash fixed by a
  synchronous `AppPreferences.init` in `MainActivity.onCreate` before `setContent`. New glossary
  term *always-dark route* added to `CONTEXT.md`.

- [Ticket 01 — design light palette + values](issues/01-design-light-palette-and-tokens.md):
  resolved via an HTML prototype the user reviewed live
  ([artifact](https://claude.ai/code/artifact/d1ad4154-cb08-4d70-aee4-2942bae2aef5),
  [asset](assets/01-light-palette-prototype.html)). **~26-token set locked** (full table + light/
  dark hex + contrast in the ticket answer). Teal splits 3 ways: `brandTeal #1A8C8C` (identity/
  large only), `accent #107C7A` (fills), `accentText #0A6360` (teal text/links). **Purple + the
  bespoke indigo `#5847E8` retired.** Light mode = **flat surfaces + one shadow**, glow animations
  off in light (kept in dark). Splash = `#0F5F5F` deep teal, both modes. `DestinationSelectionScreen`
  violet **flattened to teal**. Tokens are **opaque** (only `scrim` carries alpha).

- [Ticket 07 — visual-regression verification](issues/07-visual-regression-verification.md):
  resolved via grilling. **Manual QA, no screenshot library** (colour-only migration, no CI).
  One automated guardrail added by the foundation ticket: a JVM contrast/`ColorScheme`-completeness
  unit test in `testDebugUnitTest`. **Per-batch on-device visual sign-off is the user's** (Q3=b) —
  orchestrator runs code gates + reviews the diff, user runs the screens on device both modes
  against a 6-point checklist before the next batch. `before/` baseline captured lazily per batch.

- [Ticket 03 — palette + token spec](issues/03-palette-and-token-spec.md): resolved via grilling
  (2 rounds). Spec written to **`docs/Theming/README.md` + `docs/Theming/palette.md`**.
  **30 `MallColors` tokens** (Ticket 01's 26 + `imagePlaceholder`, `imageErrorSurface`,
  `overlayScrimGradient`, `focusRing`). **Full 36-role M3 `ColorScheme` map** — `primary =
  accentText` (AA-safe dark teal), `primaryContainer = accent` (vibrant, for FAB), pale-teal
  `secondaryContainer` nav pill, `tertiary` folded onto teal, M3 `outline` = a dedicated 3:1
  grey (≠ decorative `border` → `outlineVariant`), `surfaceTint` transparent (no tonal
  elevation). `.copy(alpha=)` on tokens banned in feature code. Dark consolidation: 4 winners,
  no per-screen exceptions. Verification: one JVM test asserts every contrast pair + all 36 roles
  `!= Unspecified` + `brandTeal` large-only. **Unblocks Ticket 08.**

- [Ticket 08 — Compose API + component-state contract](issues/08-compose-api-and-component-contract.md):
  resolved via grilling (2 rounds). Written to `docs/Theming/README.md` §7 + §8.
  `MallColors` = `@Immutable data class`, 30 members named exactly as the tokens; read via
  `MallTheme.colors.<token>` (accessor object, mirrors `MaterialTheme.colorScheme`).
  `LocalMallColors = staticCompositionLocalOf { MallLightColors }` (non-crashing preview default).
  `MallARTheme(content)` stays content-only, must not regress the `0a09460` typography memo.
  **`rememberHomeColorScheme` + `HomeColorScheme` deleted outright** (no adapter; 6 call sites
  rewritten per-batch; codex C6 stale-key bug moot). Component matrix: themed
  `RippleConfiguration(accent)`; 3 elevation levels (`flat`/`raised`/`overlay`) as theme-package
  `Dp`+shadow constants, not `MallColors`; glow shadows off in light; disabled → `textDisabled` +
  M3 `disabledContainerColor`, no runtime alpha; `LocalTextSelectionColors` from `accent`; custom
  scrims → `scrim.copy(alpha = 0.4f)` (the one sanctioned token alpha). **Unblocks Ticket 04.**

- [Ticket 04 — colour-literal lint gate](issues/04-color-literal-lint-gate.md): resolved via
  grilling (1 round). Spec in `docs/Theming/README.md` §9. **Tool = a Gradle regex task
  `checkThemeColors`** (no new plugin), `dependsOn check`, new explicit gate command. Bans literal
  `Color(...)`, named `Color.*` constants, gradient-list literals, `.copy(alpha=)` on literals **or
  tokens** (only `scrim` exempt), Kotlin `@color/` refs. `Color.Transparent` allowed. Exempt:
  `ui/theme/**`, tests, `// theme-lint:allow` marker. `OfferItem.tint` stays data-owned + exempt
  (text over it must sit on a token scrim). Rollout = checked-in
  `config/theme-migration-allowlist.txt` each batch shrinks; **Ticket 05's final "Lint-gate flip"
  batch deletes it + hard-errors.** **Unblocks Ticket 05.**

- [Ticket 05 — define the migration batch tickets](issues/05-define-migration-batch-tickets.md):
  resolved (user signed off the slicing). Deliverable
  [`docs/Theming/migration-tickets.md`](../../docs/Theming/migration-tickets.md) — **13 sequential
  `agy` batch tickets 00–12**, execution model, gate set (`compileDebugKotlin` +
  `testDebugUnitTest` + `compileDebugAndroidTestKotlin` + `checkThemeColors` + `lintDebug`),
  per-screen QA checklist, master token-mapping table, dependency graph, core-flow milestone at
  Batch 07. Reconciled agy's `screen-inventory.md §4` grouping against the locked tickets
  (colors.xml deleted not repurposed; no values-night; `rememberHomeColorScheme` deleted;
  contrast test + Elevation constants + `checkThemeColors` land in Batch 00/12). Pilots =
  `LanguageScreen` + `ProfileScreen` (codex C11 resolution). **Charting complete.**

_Frontier: **none — charting done.** Execution phase: dispatch Batch 00 to `agy`. Track below._

## Migration progress

<!-- update after each batch: pending → agy-drafted → in-review → landed → signed-off -->

| Batch | Scope | Status |
|---|---|---|
| 00 | Foundation (theme pkg, MallColors, contrast test, checkThemeColors, pilots Language+Profile) | **signed off** (`1473c4a`) |
| 01 | XML + system-bars + bootstrap (delete colors.xml, sync init, DarkSystemBars) | **signed off** (`c7b9178`) |
| 02 | Shared renderers (StoreLogo, HomeSharedComponents; delete rememberHomeColorScheme) | **signed off** (`0eb210b`) |
| 03 | Auth & onboarding (auth/ ×5) | **signed off** (`20e074e`) |
| 04 | Core Home (Homescreen.kt) | **signed off** (`2b90473`) |
| 05 | Offers & Vouchers | **signed off** (`c7a2f64`) |
| 06 | Destination (flatten Dsel* violet) | **signed off** (`b5ec8f5`) |
| 07 | Profile / Settings / Saved — **core-flow milestone** | **signed off** (`d0f4a94`) |
| 08 | Parking suite ×4 (54 literals) | pending |
| 09 | Localization / first-run / camera ×5 | pending |
| 10 | Navigation HUD & map chrome ×2 | pending |
| 11 | Bespoke-dark overlays (ChatBottomSheet, StoreDetail, VoiceAssistant) | pending |
| 12 | Lint-gate hard-error flip | pending |

## Not yet specified

_All charted fog has graduated:_
- _Glass/glow aesthetic → **resolved** by Ticket 01 (flat + elevation; glow off in light)._
- _Splash → **resolved** by Ticket 01 (`#0F5F5F`) + Ticket 06 (one fixed background)._
- _Status colours → **resolved** by Ticket 03 (paired fg/bg tokens in `docs/Theming/README.md` §3)._
- _`DestinationSelectionScreen` violet → **resolved** by Ticket 01 (flatten to teal)._

_Nothing left in the fog — the way to the destination is charted end to end._

## Out of scope

- **Follow-system / three-way theme switching** (Follow system / Light / Dark). A settings-UX
  feature, not a colour-correctness fix. The token system won't preclude adding it later.
- **Dark-mode visual polish / QA sweep.** The spec captures dark token values, but making dark
  mode *look great* is a separate effort.
- **Running the migration.** This map is plan-only; the batched migration tickets are the
  handoff boundary, executed by `agy` and reviewed by the orchestrator.
- **Dark-mode consolidation redesign.** Picking the single winning value where old darks
  conflict is in scope (see Notes); actually re-designing the dark theme's look is not.
- **Follow-system theme switching**, **notification / widget colours** (the app has neither a
  live widget nor themed notifications today).
- **AR scene-material colours** (codex C14): the 3D arrow / marker material colours rendered in
  the ARCore scene stay out of scope — camera-only rendering. This does **not** exempt Compose
  chrome or the 2D map-mode position/route indicators drawn over the navigation screen; those are
  in scope as normal screen colour.
