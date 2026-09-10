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
  owed. Charting is done. From here the map is *worked*: one ticket at a time along the critical
  path 02 → 06 → 03 → 08 → 04 → 05 (01, 07 parallel). Re-opening a locked decision needs a fresh
  user call, not an in-session edit.
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

_Frontier now: **Tickets 01, 07** (parallel). Critical path: 01 → 03 → 08 → 04 → 05
(03 also needs 06 ✓; 07 feeds 05)._

## Not yet specified

- **Light-mode treatment of the "glass / glow" aesthetic.** The Home flow leans hard on dark
  glassmorphism (translucent cards, animated accent glow). What replaces that on a white surface —
  solid cards + elevation? a tinted surface? keep a subtle glow? — can't be decided until the
  palette prototype (Ticket 01) exists. Graduates from 01/03.
- **Splash screen light/dark.** The visual call is deferred to the palette prototype (brand
  moment — may stay dark in both modes); the mechanism (`windowSplashScreenBackground` +
  `values-night`) is Ticket 06. Graduates from 01 + 06.

_(Status colours are no longer fog — codex C14 moved them to a Ticket 03 deliverable: under a
hard AA bar, `RedAccent` / `GreenArrow` / `SuccessGreen` / `ErrorRed` are visible chrome and need
resolved foreground/background token pairs before migration.)_

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
