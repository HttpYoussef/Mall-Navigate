Type: grilling
Status: resolved
Claimed by: Youssef Ibrahim (orchestrator) — grilling 2026-09-10
Blocked by: 03

## Question

Produce the **Compose API + component-state half** of the `docs/Theming/` spec — the code contract
every migration ticket targets. Split from Ticket 03 per codex C5.

HITL — API naming and the fate of the existing helpers need the user.

**Deliverables (added to `docs/Theming/`):**

1. **Compose API contract**
   - `MallColors` data class shape + `LocalMallColors` `CompositionLocal` (non-crashing default
     for previews).
   - How `MallARTheme` selects light vs dark and provides the M3 `MaterialTheme` (with the full
     role map from Ticket 03) **and** `LocalMallColors` in one wrapper.
   - Read site: `MallTheme.colors.cardBg` accessor object vs raw `LocalMallColors.current`.
   - Token property naming conventions.

2. **Fate of `rememberHomeColorScheme` + `HomeColorScheme`** (codex C6) — only 6 call sites
   (`HomeSharedComponents.kt:68`, `Homescreen.kt:114`, `OffersScreen.kt:77`,
   `VoucherDetailsScreen.kt:71`, `DestinationCategoryScreen.kt:48`, `DestinationSearchScreen.kt:40`).
   Decide: delete + replace, or a thin adapter deleted once references hit zero. **Bug to record**:
   if an adapter reads `LocalMallColors.current`, `remember(isDarkMode)` is a stale key — a
   palette/provider change with the same mode leaves stale colours. Remove the memoisation or key
   it on the token object. This decision is a **foundation-ticket detail**, not a spec blocker.

3. **Component-state matrix** (codex C8) — for each interactive/elevated surface type, the exact
   colour source in both modes:
   - ripple / `indication` (many call sites pass `indication = null` with custom press handling —
     `HomeSharedComponents.kt:219`, `DestinationSelectionScreen.kt:570`),
   - pressed / disabled policy (disabled-alpha colours in `SignInScreen.kt`, `PermissionsScreen.kt`),
   - M3 tonal elevation / `surfaceTint` in dark mode (keep, or flatten to explicit tokens),
   - shadow colour (`CardShadow = Color(0x1A000000)` used raw — `Homescreen.kt:292`),
   - dialog / `Snackbar` scrim (custom overlays in `Homescreen.kt:516-557`),
   - text-selection handle / `TextSelectionColors`.
   Goal: two screens using the same semantic token render identically.

**Output**: `docs/Theming/` API + component sections committed (docs only). The answer records
the API contract + component matrix. Unblocks Ticket 04 and feeds Ticket 05's foundation ticket.

## Answer

Resolved 2026-09-10 via grilling (2 rounds, all "agree with all"). Written to
`docs/Theming/README.md` §7 (Compose API) + §8 (component-state matrix).

### Round 1 — API surface

- **Q1 `MallColors`**: `@Immutable data class`, 30 members, names identical to the §3 token
  names, no mode suffix. `Color` for all except `overlayScrimGradient: Brush`. `shadow` is NOT a
  member (→ elevation contract). Two top-level instances `MallLightColors` / `MallDarkColors`.
- **Q2 read site**: `object MallTheme { val colors: MallColors @Composable @ReadOnlyComposable }`
  → call sites use `MallTheme.colors.<token>`. Mirrors `MaterialTheme.colorScheme`; lint-gate
  whitelistable. Never raw `LocalMallColors.current`.
- **Q3 wrapper**: `MallARTheme(content)` stays content-only (single call site, must not regress
  the `0a09460` typography memoisation). `LocalMallColors = staticCompositionLocalOf {
  MallLightColors }` — non-crashing preview default. Wrapper builds the M3 ColorScheme from
  T03 §5, picks the `MallColors` instance, provides `LocalMallColors` + themed
  `RippleConfiguration` + `LocalTextSelectionColors` around `MaterialTheme`.
- **Q4 `rememberHomeColorScheme` / `HomeColorScheme`**: **deleted outright**, no adapter. 6 call
  sites rewritten to `MallTheme.colors.*` in their own batches. Field map recorded in §7.4. The
  codex C6 stale-`remember(isDarkMode)` bug becomes moot (nothing to key).

### Round 2 — component-state matrix (full table in §8)

- **Q5 ripple / indication**: stock ripple → themed `RippleConfiguration(color = accent)`;
  custom `indication = null` + `scale()` press kept (it's motion), any colour it draws from
  `MallColors` — approved pattern, NOT a Ticket 04 violation. No hand-built ripple colours.
- **Q6 elevation**: 3 levels — `flat` (0dp) / `raised` (4dp, shadow `#0F1F2E`@10% light /
  `#000`@40% dark) / `overlay` (8dp). `Dp` + shadow constants owned by theme package, not
  `MallColors`. Glow shadows OFF in light (T01), kept dark as decorative-exempt. M3 tonal
  elevation stays killed (`surfaceTint` transparent).
- **Q7 pressed/disabled**: disabled text/icon → `textDisabled`; disabled fill → M3
  `disabledContainerColor` (`#E6EAEE` / `#232C31`); pressed overlay → `accent` @ 8% composited.
  Ban `.copy(alpha = 0.38f)`-style disabled signalling.
- **Q8 text selection**: `LocalTextSelectionColors(handleColor = accent, backgroundColor =
  accent @ 40%)`, both modes.
- **Q9 scrims**: custom overlays → `MallTheme.colors.scrim.copy(alpha = 0.4f)` (the one
  sanctioned `.copy(alpha)` on a token; standardises 0.45 → 0.40). Stock `Dialog` /
  `ModalBottomSheet` keep their built-in scrim (M3 `scrim` role = `#000000`), no added overlay.

**Unblocks Ticket 04.** Feeds Ticket 05's foundation ticket (§7 is its build target).
