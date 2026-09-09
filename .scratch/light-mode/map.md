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
- **Adversarial debate status** (2026-09-09): codex (router budget exhausted), grok (free-tier
  limit), gemini (client deprecated), kimi (not logged in) are all unavailable. A self-review
  adversarial pass was run instead and its non-architectural findings folded in (new Tickets 06
  + 07; Ticket 01 narrowed; scope language tightened) — see
  [self-review-findings.md](self-review-findings.md). **The map is deliberately left UNLOCKED**
  — a real independent debate (codex, on everything) still owed once access is restored.
- Skills each session should consult: `grilling` + `domain-modeling` (for the spec / API ticket),
  `prototype` (for the palette ticket). No research tickets — Material 3 + this codebase are the
  only sources and both are in hand.

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

_(none yet — charting complete. Frontier is **Ticket 02** (screen + XML colour inventory);
Tickets 01, 06, 07 unblock once 02 is resolved.)_

## Not yet specified

- **Light-mode treatment of the "glass / glow" aesthetic.** The Home flow leans hard on dark
  glassmorphism (translucent cards, animated accent glow). What replaces that on a white surface —
  solid cards + elevation? a tinted surface? keep a subtle glow? — can't be decided until the
  palette prototype (Ticket 01) exists. Graduates from 01/03.
- **Splash screen light/dark.** The visual call is deferred to the palette prototype (brand
  moment — may stay dark in both modes); the mechanism (`windowSplashScreenBackground` +
  `values-night`) is Ticket 06. Graduates from 01 + 06.
- **Semantic status colours.** `RedAccent` / `GreenArrow` / `SuccessGreen` / `ErrorRed` — whether
  they need light-mode-specific variants and their own token group surfaces during the screen
  inventory (Ticket 02).
- **AR in-world render colours.** The actual 3D arrow / marker material colours in the AR scene
  (not Compose chrome) — whether they're touched at all. Tentatively out of scope; confirm after
  the inventory.

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
