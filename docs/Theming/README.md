# MallAR Theming — Colour Token System

**Status:** spec locked — palette + tokens + M3 role map (Ticket 03), Compose API + component
matrix (Ticket 08). 2026-09-10. Migration not started.
**Scope of this doc:** the palette, the named token list, the colour-model rules, the full
Material 3 `ColorScheme` role map (§1–§6), the Compose API surface (§7), and the
component-state matrix (§8). The migration batch tickets are Ticket 05.

Planning trail: `.scratch/light-mode/map.md` and its issue tickets.

---

## 1. Why this exists

Recon found ~198 raw `Color(0x…)` literals across the `ui/` package, only 3 sites reading
`MaterialTheme.colorScheme`; `rememberHomeColorScheme` is a per-screen copied palette (6 call
sites); 7 screens never handle dark mode; `res/values/themes.xml` + `colors.xml` hardcode a
dark window with no `values-night`. Light mode is inconsistent and fails WCAG AA in several
places (`#888EA8` secondary text ≈ 3.0:1, `CyanGlow #19D3E6` floor-badge text ≈ 1.2:1,
`GreenArrow #4CAF50` ≈ 2.5:1).

The fix is one central palette feeding **both**:

- the Material 3 `ColorScheme` (so stock components stop rendering Material-purple defaults), and
- a `MallColors` `CompositionLocal` extension for app-specific slots M3 has no role for
  (Ticket 08 defines the Kotlin surface).

## 2. Hard rules

- **WCAG AA is a ship gate.** 4.5:1 for body text, 3:1 for large text (≥ 18.66px bold / ≥ 24px),
  UI component boundaries, and focus indicators. Failures do not ship. Contrast is measured
  **after compositing** against the named backdrop, never on the nominal ARGB value.
- **Tokens are opaque.** The only alpha-bearing values are `scrim`, `overlayScrimGradient`, and
  one hairline overlay — each recorded with its backdrop and post-composite ratio.
- **`.copy(alpha = …)` on a colour token or literal is banned in feature code.** A translucent
  value that is actually needed becomes a *named composited token* in the theme file. The only
  allowed `.copy(alpha)` is on `scrim`/overlay tokens and on genuinely decorative,
  non-contrast-bearing state overlays (a ~4% press highlight) — and those route through the
  `MallColors` state tokens Ticket 08 defines, not raw literals. Ticket 04 enforces this.
- **Light mode is flat.** Solid `surface` + one resting shadow. No glass, no tinted card fills.
  The `rememberInfiniteTransition` glow animations are disabled in light mode (kept in dark) and
  respect `prefers-reduced-motion`.
- **`isDarkMode` (the `AppPreferences` boolean) is the sole source of truth for mode.** No
  follow-system, no `values-night`. XML is pinned light; `MallARTheme` owns the runtime
  (Ticket 06).
- **No Material default may remain** in the `ColorScheme`. Every one of the 36 roles in §5 is
  set explicitly and asserted `!= Color.Unspecified` by the contrast test (§6).

## 3. The named token list — 30 tokens

Light / Dark. Every text and UI token lists its worst-case contrast in `palette.md`.

### Surfaces

| Token | Role | Light | Dark |
|---|---|---|---|
| `screenBackground` | app background (Scaffold) | `#F7F9FB` | `#0E1418` |
| `surface` | cards, sheets, nav bar | `#FFFFFF` | `#161D22` |
| `surfaceSunken` | search field, input well, muted fill | `#EEF1F4` | `#10161A` |

Light mode has **no elevated-surface token** — elevation is the resting shadow, not a lighter
fill. Dark mode's M3 `surfaceContainer*` ladder (§5) provides component surface steps by value.

### Text

| Token | Role | Light | Dark | Notes |
|---|---|---|---|---|
| `textPrimary` | headings, body | `#151A1E` | `#E8ECEF` | 16.6:1 on `surface` |
| `textSecondary` | captions, metadata | `#5A6672` | `#9BA8B0` | 5.9:1 — replaces `#888EA8` (3.0:1) |
| `textDisabled` | disabled / placeholder | `#9AA5AF` | `#6B7780` | decorative, AA-exempt |

### Brand teal — split three ways (Ticket 01)

| Token | Role | Light | Dark | Notes |
|---|---|---|---|---|
| `brandTeal` | logo, splash, large decorative fills | `#1A8C8C` | `#2FA3B8` | 4.1:1 — **large text only**, never small text/icons |
| `accent` | interactive fill (button / toggle / FAB / active tab) | `#107C7A` | `#3FB2AC` | white on it 5.1:1 |
| `accentText` | teal as text / icon / link on a light surface | `#0A6360` | `#5BC9C2` | 7.0:1 on `surface` |
| `onAccent` | text / icon on the `accent` fill | `#FFFFFF` | `#06201F` | |

`brandTeal` fails AA as small text — the contrast test asserts it is used large-text-only.
Purple `DesignPurple #9D5CFF` and the bespoke indigo `#5847E8` (DestinationSelectionScreen)
and the bespoke `LogoPrimary #258799` (StoreLogo monogram) are **retired** — the monogram
uses `accentText`.

### Lines

| Token | Role | Light | Dark | Notes |
|---|---|---|---|---|
| `border` | decorative card / input hairline | `#E2E7EC` | `#2A343A` | decorative, AA-exempt; maps to M3 `outlineVariant` |
| `borderStrong` | focused input outline | = `accent` | = `accent` | 3.1:1 on ground |
| `divider` | list-row separators | `#EAEEF1` | `#232C31` | decorative |
| `focusRing` | keyboard-focus outline | = `accentText` | = `accentText` | distinct from `borderStrong` for a11y |

### Status — foreground + background pairs (codex C14)

| Token | Role | Light | Dark | Notes |
|---|---|---|---|---|
| `success` / `onSuccess` | parking saved, redeemed (fill) | `#1E7A3E` / `#FFFFFF` | `#3EA55F` / `#06201F` | white on it 5.4:1 — replaces `#4CAF50` (2.5:1) |
| `successText` | green as text on a surface | `#136B32` | `#6FCB89` | 5.9:1 |
| `error` / `onError` | failed scan, destructive (fill) | `#C0362C` / `#FFFFFF` | `#E06A60` / `#1A0E0D` | white on it 5.5:1 — replaces `#E53935` |
| `errorText` | error text, favourite heart | `#B0271F` | `#EE9089` | 6.1:1 |
| `warningText` | "coming soon", caution | `#8A5A00` | `#D8A24B` | 5.9:1 |

### Always-dark chrome (camera / AR HUD / 2D map / splash)

| Token | Role | Value (both modes) | Notes |
|---|---|---|---|
| `scrimSurface` | dark chrome over the camera | `#0E1A1F` | identical in both themes |
| `scrimCard` | panels on the AR HUD | `#182830` | |
| `onScrim` / `onScrimMuted` | text on scrim | `#F2F5F6` / `#9BAAB2` | 13:1 / 4.6:1 |

### Alpha-bearing (the only three)

| Token | Role | Value | Backdrop | Post-composite |
|---|---|---|---|---|
| `scrim` | dialog / sheet backdrop | `#000000` @ 40% | screen content | dims to AA-safe for `onScrim` overlay text |
| `overlayScrimGradient` | image-into-card bottom fade | `surface` @ 0 → 90% | `surface` | text sits only in the ≥ 85% region |
| `hairlineOverlay` | 1px inset line on dark cards | `#FFFFFF` @ 8% | `surface` dark | decorative only |

### Image surfaces (codex C12)

| Token | Role | Light | Dark | Notes |
|---|---|---|---|---|
| `imagePlaceholder` | logo plate, image loading bg | `#FFFFFF` | `#F0F2F4` | stays near-white both modes — brand logo PNGs assume a light plate |
| `imageErrorSurface` | Coil error / empty state bg | `#EEF1F4` | `#10161A` | = `surfaceSunken` |

`StoreLogoContainer` currently hardcodes `Color.White` (dark mode included) — it moves to
`imagePlaceholder`. `StoreDetail`'s crossfade background moves to `imagePlaceholder`; its
monogram fallback text uses `accentText`.

### Elevation

| Token | Role | Light | Dark |
|---|---|---|---|
| `shadow` | resting card elevation | `0 2px 12px -2px rgba(15,31,46,.10)` | opaque/darker equivalent |

Ticket 08 formalises `shadow` as discrete elevation levels.

## 4. Dark-mode consolidation

Recon found the same role using mismatched dark values. Winners (from Ticket 01's dark column,
confirmed in Ticket 03 grilling):

| Role | Old disagreeing values | Winner |
|---|---|---|
| app background | `#06131A` `#121218` `#0A0F1E` `#0D1B2A` | `screenBackground` `#0E1418` |
| card / elevated surface | `#0D1E26` `#1E1E2A` `#282838` `#0D1B2A` | `surface` `#161D22`; elevated → M3 `surfaceContainerHigh` `#1F282E` |
| secondary text | `#8BA3AD` `#9898A8` `#888EA8` | `textSecondary` `#9BA8B0` |
| divider | `#363648` | `divider` `#232C31` |

Net effect: dark mode shifts very slightly cooler and becomes uniform. Accepted (map Notes:
"token wins, minor dark shift accepted"). No per-screen exceptions.

## 5. Material 3 `ColorScheme` role map — all 36 roles

`Theme.kt` currently sets ~10 roles; the rest render Material-purple defaults that leak into
dialogs, text fields, snackbars, selection handles and elevated surfaces. Every role below is
set explicitly. Key decisions:

- **`primary` = `accentText`** (the AA-safe dark teal). M3 routes `primary` into text, icons,
  outlines, tab labels, switch tracks and controls far more than into fills — text must never
  fail AA. Our own vibrant CTAs bind `MallColors.accent` directly, so they lose nothing.
- **`primaryContainer` = `accent`** (the vibrant teal) — this is what `FloatingActionButton` and
  `FilledTonalButton` render, so stock FABs still come out vibrant.
- **`secondaryContainer`** is the `NavigationBar` selected-pill: a pale-teal internal pair.
- **`tertiary`** family is folded onto teal + neutral (no third brand hue).
- **`outline`** is a dedicated 3:1 boundary grey — *not* the decorative `border` token, which
  maps to `outlineVariant`.
- **`surfaceTint` = `Color.Transparent`** both modes — elevation is shadow-only, no M3 tonal tint.

| M3 role | Light | Dark | Source / note |
|---|---|---|---|
| `primary` | `#0A6360` | `#5BC9C2` | `accentText` |
| `onPrimary` | `#FFFFFF` | `#06201F` | `onAccent` |
| `primaryContainer` | `#107C7A` | `#3FB2AC` | `accent` — vibrant, for FAB / tonal |
| `onPrimaryContainer` | `#FFFFFF` | `#06201F` | `onAccent` |
| `inversePrimary` | `#2FA3B8` | `#0A6360` | teal readable on `inverseSurface` |
| `secondary` | `#107C7A` | `#3FB2AC` | `accent` |
| `onSecondary` | `#FFFFFF` | `#06201F` | `onAccent` |
| `secondaryContainer` | `#DCEBEA` | `#123B39` | pale-teal nav pill — internal pair |
| `onSecondaryContainer` | `#0A6360` | `#5BC9C2` | `accentText` |
| `tertiary` | `#0A6360` | `#5BC9C2` | folded onto teal |
| `onTertiary` | `#FFFFFF` | `#06201F` | `onAccent` |
| `tertiaryContainer` | `#EEF1F4` | `#10161A` | `surfaceSunken` |
| `onTertiaryContainer` | `#151A1E` | `#E8ECEF` | `textPrimary` |
| `background` | `#F7F9FB` | `#0E1418` | `screenBackground` |
| `onBackground` | `#151A1E` | `#E8ECEF` | `textPrimary` |
| `surface` | `#FFFFFF` | `#161D22` | `surface` |
| `onSurface` | `#151A1E` | `#E8ECEF` | `textPrimary` |
| `surfaceVariant` | `#EEF1F4` | `#10161A` | `surfaceSunken` |
| `onSurfaceVariant` | `#5A6672` | `#9BA8B0` | `textSecondary` (5.4:1 on `surfaceVariant` — AA) |
| `surfaceTint` | `#00000000` | `#00000000` | transparent — no tonal elevation |
| `inverseSurface` | `#151A1E` | `#E8ECEF` | dark chip / snackbar in light |
| `inverseOnSurface` | `#F2F5F6` | `#151A1E` | `onScrim` / `textPrimary` |
| `surfaceBright` | `#FFFFFF` | `#1F282E` | |
| `surfaceDim` | `#E6EAEE` | `#0A0F12` | |
| `surfaceContainerLowest` | `#FFFFFF` | `#0A0F12` | |
| `surfaceContainerLow` | `#F7F9FB` | `#12191E` | |
| `surfaceContainer` | `#EEF1F4` | `#161D22` | |
| `surfaceContainerHigh` | `#E8ECEF` | `#1F282E` | |
| `surfaceContainerHighest` | `#E2E7EC` | `#253038` | |
| `error` | `#C0362C` | `#E06A60` | `error` |
| `onError` | `#FFFFFF` | `#1A0E0D` | `onError` |
| `errorContainer` | `#F9DEDB` | `#5C201B` | |
| `onErrorContainer` | `#B0271F` | `#EE9089` | `errorText` (5.7:1 / dark equiv) |
| `outline` | `#8A939C` | `#6B7780` | 3:1 boundary grey — verified 3.11:1 / 3.72:1 |
| `outlineVariant` | `#E2E7EC` | `#2A343A` | `border` token |
| `scrim` | `#000000` | `#000000` | M3 applies its own alpha at the use site |

### Stock-component outcomes

| Component | Result |
|---|---|
| `Button` (filled) | dark teal `#0A6360` + white — 7:1 |
| `TextButton` / `OutlinedButton` / `Tab` / `Switch` / `Checkbox` / `RadioButton` | `#0A6360` on `surface` — 7:1 |
| `FloatingActionButton` / `FilledTonalButton` | vibrant `#107C7A` + white — 5.1:1 |
| `Snackbar` | `#151A1E` bg · `#F2F5F6` text · `#2FA3B8` action |
| `NavigationBar` selected | pale-teal pill `#DCEBEA` + `#0A6360` icon |
| `OutlinedTextField` (unfocused) | `#8A939C` outline — 3.11:1 |
| dialogs / `ModalBottomSheet` scrim | `#000000` @ M3 default alpha |

## 6. Verification contract (feeds Ticket 07)

A single JVM unit test in `app/src/test/java/com/example/mallar/ui/theme/` (runs in the existing
`testDebugUnitTest` gate — no emulator):

1. Every foreground/background token pair in `palette.md` meets its target ratio (AA: 4.5:1 body,
   3:1 large / UI / focus). Decorative tokens (`textDisabled`, `border`, `divider`,
   `hairlineOverlay`) are exempt and listed explicitly in the test.
2. Every one of the 36 M3 roles in §5 is `!= Color.Unspecified` and equals this table for both
   schemes — the machine-checkable "no library default remains".
3. `brandTeal` is asserted large-text-only (documented threshold, not a silent pass).

## 7. Compose API contract (Ticket 08)

The code surface every migration ticket targets. The foundation ticket writes it; nothing here
is a code stub.

### 7.1 `MallColors`

An `@Immutable data class` — one property per §3 token, **names identical to the token names**.
30 members. `Color` for all except `overlayScrimGradient: Brush`. `shadow` is **not** a member
(see §8, elevation). Two top-level instances `MallLightColors` / `MallDarkColors`.

```kotlin
@Immutable
data class MallColors(
    val screenBackground: Color, val surface: Color, val surfaceSunken: Color,
    val textPrimary: Color, val textSecondary: Color, val textDisabled: Color,
    val brandTeal: Color, val accent: Color, val accentText: Color, val onAccent: Color,
    val border: Color, val borderStrong: Color, val divider: Color, val focusRing: Color,
    val success: Color, val onSuccess: Color, val successText: Color,
    val error: Color, val onError: Color, val errorText: Color, val warningText: Color,
    val scrimSurface: Color, val scrimCard: Color, val onScrim: Color, val onScrimMuted: Color,
    val scrim: Color,                    // opaque #000000; the one sanctioned .copy(alpha) target
    val imagePlaceholder: Color, val imageErrorSurface: Color,
    val overlayScrimGradient: Brush, val hairlineOverlay: Color,
)
```

### 7.2 `LocalMallColors` + `MallTheme`

```kotlin
val LocalMallColors = staticCompositionLocalOf { MallLightColors }   // non-crashing preview default

object MallTheme {
    val colors: MallColors
        @Composable @ReadOnlyComposable get() = LocalMallColors.current
}
```

`staticCompositionLocalOf` (not `compositionLocalOf`): the value only changes on a mode flip,
which tears down the composition anyway. Default is `MallLightColors` so `@Preview` and any
composable outside `MallARTheme` render in a real palette rather than crashing. Read site is
**always `MallTheme.colors.<token>`** — never raw `LocalMallColors.current`, never
`MaterialTheme.colorScheme` for an app-specific slot. (`MaterialTheme.colorScheme` stays correct
for stock M3 components, which read it internally.)

### 7.3 `MallARTheme` wrapper

Signature stays **`@Composable fun MallARTheme(content: @Composable () -> Unit)`** — single call
site (`MainActivity`). It must not regress the memoised font/typography resolution (commit
`0a09460`). It now also:

1. builds the M3 `ColorScheme` from §5 (`isDarkMode ? darkRoleMap : lightRoleMap`),
2. picks `isDarkMode ? MallDarkColors : MallLightColors`,
3. provides `LocalMallColors`, a themed `RippleConfiguration` (§8), and
   `LocalTextSelectionColors` (§8) around `MaterialTheme`,
4. keeps the `SideEffect` that drives system-bar colours (Ticket 06).

### 7.4 `rememberHomeColorScheme` / `HomeColorScheme` — deleted

Both are removed outright (not adapter-wrapped). The 6 call sites
(`HomeSharedComponents.kt:68`, `Homescreen.kt:114`, `OffersScreen.kt:77`,
`VoucherDetailsScreen.kt:71`, `DestinationCategoryScreen.kt:48`, `DestinationSearchScreen.kt:40`)
are rewritten to `MallTheme.colors.*` in their own migration batches. Field mapping:
`bg→screenBackground`, `cardBg→surface`, `textMain→textPrimary`, `textSub→textSecondary`,
`accent→accent`, `border→border`. The codex C6 `remember(isDarkMode)` stale-key bug becomes
moot — there is no cache to key.

### 7.5 Naming conventions

- token property: the semantic role, camelCase, no mode suffix (`textSecondary`, never
  `textSecondaryLight`). Mode lives in *which instance* is provided.
- `on<X>` = content colour for the `X` fill.
- `<X>Text` = that hue used as a foreground on a neutral surface (AA-tuned), distinct from the
  `<X>` fill.

## 8. Component-state matrix (Ticket 08)

Goal: two screens using the same semantic token render identically in a given mode.

| Surface / state | Colour source — light | Colour source — dark |
|---|---|---|
| **Ripple** (stock M3) | themed `RippleConfiguration(color = accent)` provided in `MallARTheme` | same, `accent` dark |
| **Custom press** (`indication = null` + `scale()`) | motion kept as-is; any colour drawn (overlay, border) from `MallColors`. Approved pattern — not a Ticket 04 violation | same |
| **Pressed-state overlay** (custom components) | `accent` @ 8% over base (composited, decorative-exempt) | same |
| **Disabled text / icon** | `textDisabled` | `textDisabled` |
| **Disabled fill** (button container) | M3 `ButtonColors.disabledContainerColor` = `#E6EAEE` | `#232C31` |
| **Elevation — flat** (default surface) | 0dp, no shadow | 0dp |
| **Elevation — raised** (card / nav bar / sheet) | 4dp, shadow `#0F1F2E` @ 10% | 4dp, shadow `#000000` @ 40% |
| **Elevation — overlay** (dialog / menu / snackbar) | 8dp, shadow `#0F1F2E` @ 14% | 8dp, shadow `#000000` @ 48% |
| **M3 tonal elevation** | off — `surfaceTint = Transparent`; step via `surfaceContainer*` | off |
| **Glow shadow** (`ambientColor = accent…`) | **removed in light mode** (Ticket 01) | kept, `accent`, decorative-exempt |
| **Text selection** | `LocalTextSelectionColors(handleColor = accent, backgroundColor = accent @ 40%)` | same |
| **Dialog / sheet scrim** (custom overlay) | `MallTheme.colors.scrim.copy(alpha = 0.4f)` — the one sanctioned `.copy(alpha)` on a token | same |
| **Dialog / sheet scrim** (stock M3 `Dialog` / `ModalBottomSheet`) | M3 built-in, driven by `scrim` role `#000000` — no hand-rolled overlay added | same |
| **Focus ring** | `focusRing` (= `accentText`), ≥ 3:1 | same |

Elevation levels are `Dp` + shadow-colour constants owned by the theme package (the foundation
ticket decides `object Elevation { … }` vs params) — **not** `MallColors` members.

Rules the lint gate (Ticket 04) enforces from this section:
- no hand-built ripple colour — `RippleConfiguration` or nothing;
- no `.copy(alpha = …)` to signal disabled — use the disabled tokens;
- custom scrims go through `MallTheme.colors.scrim`, not `Color.Black.copy(...)`.

## 9. What this unblocks

- **Ticket 04** — the colour-literal lint gate (bans what §2 + §8 forbid).
- **Ticket 05** — the migration batch tickets; §7 is the foundation ticket's build target.
