Type: grilling
Status: open
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
