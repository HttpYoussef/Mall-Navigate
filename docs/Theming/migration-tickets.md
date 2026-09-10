# MallAR Light-Mode Migration — Batch Tickets

**Status:** ready for handoff (Ticket 05, 2026-09-10). Charting complete.
**Spec:** `docs/Theming/README.md` (§1–§9) + `docs/Theming/palette.md`. Every rule below is
grounded there — this file slices the work, it does not re-decide anything.
**Inventory:** `.scratch/light-mode/screen-inventory.md` (route manifest, per-file literal
counts, Part B XML audit).

---

## Execution model

Each batch is **one `agy` delegate-and-review cycle** (`agy-delegate` skill), run strictly
sequentially:

1. Orchestrator writes the batch brief from this ticket + carries forward what the previous
   batch settled.
2. `agy` (`gemini-3.8-flash-high`) implements in its own conversation. It does **not** commit.
3. Orchestrator reviews the diff against the brief, re-runs the gate set, runs guard skills.
4. Orchestrator commits on `feat/colors-light-modes`. Nothing is pushed.
5. **User runs the affected screens on a device, both modes, against the QA checklist below,
   and signs the batch off** before the next batch starts (Ticket 07 Q3=b). The orchestrator
   does not self-approve the visuals.

No bulk dispatch. The gating order (00 → {01, 02} → area batches → 12) is a hard dependency.

### Gate set (every batch)

```
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest \
          :app:compileDebugAndroidTestKotlin :app:checkThemeColors lintDebug
```

`checkThemeColors` (added in Batch 00) fails only on entries **not** in
`config/theme-migration-allowlist.txt` until Batch 12 flips it to a plain hard-error.
Compose `androidTest` is code-review-only (no CI emulator).

### Per-screen QA checklist (Ticket 07 — user runs this on device)

- [ ] Light mode: background, cards, text all from the token set — no dark bleed, no
      white-on-white, no invisible text.
- [ ] Light mode: status-bar + nav-bar icons visible (dark icons on light chrome).
- [ ] Dark mode: unchanged or improved vs the `before/` capture — no new regressions.
- [ ] Every text/icon element meets AA against its actual background (spot-check the
      inventory-flagged ones: `CyanGlow`, greens, `textSecondary`, teal-as-text).
- [ ] Primary action uses `accent`; teal text/links use `accentText`; no `brandTeal` as small text.
- [ ] `agy` diff introduced zero new raw `Color(0x…)` / `Color.White` etc. in the batch's files.

`before/` baseline is captured lazily: photograph each screen's current state on the device
immediately before its batch runs → `.scratch/light-mode/before/<batch>/`.

### Token-mapping rules (apply in every batch)

| Old pattern | New |
|---|---|
| `LightBg` / `#F7F9FA` / `home_surface` / screen root bg | `MallTheme.colors.screenBackground` |
| `LightCardBg` / `GlassCardBg` / `Color.White` as a card | `MallTheme.colors.surface` |
| search field / input well bg | `MallTheme.colors.surfaceSunken` |
| `Color(0xFF1A1A1A)` / `#1A1A2E` / `TextPrimary` body text | `MallTheme.colors.textPrimary` |
| `#888EA8` / `MutedTextSub*` / `TextSecondary` caption | `MallTheme.colors.textSecondary` |
| `DesignPurple #9D5CFF`, `Dsel* #5847E8`, `ParkingPurple` accent | `MallTheme.colors.accent` (fill) / `accentText` (text/icon) |
| `Teal #1A8C8C` as a **fill** behind white | `MallTheme.colors.accent` |
| `Teal` / `DarkTeal` as **text / icon / link** | `MallTheme.colors.accentText` |
| `Teal` as a large brand mark / splash | `MallTheme.colors.brandTeal` |
| `#4CAF50` / `GreenArrow` / `SuccessGreen` fill | `success` + `onSuccess` |
| green as text | `successText` |
| `#E53935` / `RedAccent` / `ErrorRed` fill | `error` + `onError` |
| red as text / favourite heart | `errorText` |
| amber / `#FFA000` "coming soon" | `warningText` |
| `CyanGlow #19D3E6` badge text | `accentText` (AA fix — was ≈1.2:1) |
| `Color.Black.copy(0.05f)` / faint border | `MallTheme.colors.border` |
| `Color.White.copy(0.08f)` inset line on dark card | `MallTheme.colors.hairlineOverlay` |
| list-row separator | `MallTheme.colors.divider` |
| `Color.Black.copy(0.4f/0.45f)` custom scrim | `MallTheme.colors.scrim.copy(alpha = 0.4f)` |
| `StoreLogoContainer` `background(Color.White)` | `MallTheme.colors.imagePlaceholder` |
| Coil error / empty state bg | `MallTheme.colors.imageErrorSurface` |
| camera / AR HUD dark chrome | `scrimSurface` / `scrimCard` + `onScrim` / `onScrimMuted` |
| `rememberHomeColorScheme(isDarkMode).X` | `MallTheme.colors.<mapped token>` (helper deleted) |
| `.copy(alpha = …)` to signal disabled | `textDisabled` / M3 `disabledContainerColor` |
| stock M3 component colour | leave it — `MaterialTheme.colorScheme` is correct after Batch 00 |

Anything genuinely un-tokenisable (QR canvas, brand-logo vector, `OfferItem.tint` placeholder
data) gets a trailing `// theme-lint:allow <reason>` and is listed in the batch's acceptance
notes.

---

## Dependency graph

```
Batch 00 Foundation
 ├── Batch 01 XML + system-bars + bootstrap
 ├── Batch 02 Shared renderers (gating)
 │    ├── Batch 04 Home
 │    ├── Batch 05 Offers & Vouchers
 │    └── Batch 06 Destination
 ├── Batch 03 Auth              (needs 01 for system-bars)
 ├── Batch 07 Profile/Settings  ── completes CORE-FLOW MILESTONE (with 03–06)
 ├── Batch 08 Parking
 ├── Batch 09 Localization / first-run / camera
 ├── Batch 10 Navigation HUD & map chrome
 └── Batch 11 Bespoke-dark overlays
        └── Batch 12 Lint-gate flip   (needs allowlist empty → all of 01–11)
```

## Core-flow milestone (codex C10)

**Batches 00 → 01 → 02 → 03 → 04 → 05 → 06 → 07.** At this checkpoint "light mode is correct on
every screen a regular shopper touches" — Home, Auth, Offers, Search, Destination, Profile,
Settings, plus the XML/system-bar layer and shared renderers. The long tail (Parking,
Localization, Navigation chrome, bespoke-dark overlays) sequences after and is allowed to land
later. The all-screen destination stays; this is the point where the effort is *usable* if it
pauses. A fuller device pass happens here and again at all-screens-complete.

---

## Batch 00 — Foundation

**Files:** `ui/theme/Color.kt` (rewrite), `ui/theme/Theme.kt` (rewrite), new
`ui/theme/MallColors.kt`, new `ui/theme/Elevation.kt`, new
`app/src/test/java/com/example/mallar/ui/theme/ThemeContrastTest.kt`, `app/build.gradle.kts` +
new `config/theme-migration-allowlist.txt`, pilots `ui/language/LanguageScreen.kt` +
`ui/profile/ProfileScreen.kt`.

**Scope:**
1. `Color.kt` → the raw palette values from `README` §3 + §5 as `internal val`s (light + dark
   sets). No semantic names leak out of the theme package.
2. `MallColors.kt` → the `@Immutable data class` (README §7.1), `MallLightColors` /
   `MallDarkColors` instances, `LocalMallColors = staticCompositionLocalOf { MallLightColors }`,
   `object MallTheme { val colors … }` (README §7.2).
3. `Theme.kt` → build both M3 `ColorScheme`s from the full 36-role map (README §5,
   `surfaceTint = Color.Transparent`); `MallARTheme(content)` stays content-only, keeps the
   `0a09460` font/typography memo, adds `CompositionLocalProvider` for `LocalMallColors` +
   themed `RippleConfiguration(accent)` + `LocalTextSelectionColors` (README §8); keeps/adds the
   system-bar `SideEffect` scaffold (Batch 01 fills the route-aware part).
4. `Elevation.kt` → the 3 levels from README §8 as `Dp` + shadow-colour constants.
5. `ThemeContrastTest.kt` → README §6: every `palette.md` fg/bg pair meets target; all 36 roles
   `!= Color.Unspecified` and equal the table for both schemes; `brandTeal` large-text-only;
   decorative tokens explicitly exempt.
6. `checkThemeColors` Gradle task (README §9) + `theme-migration-allowlist.txt` seeded with the
   current `path:linecount` for every file in Batches 01–11.
7. Pilots: `LanguageScreen` + `ProfileScreen` fully migrated to `MallTheme.colors.*` /
   `MaterialTheme.colorScheme` — both already read M3, so they prove the token system without a
   shared-renderer dependency (codex C11 resolution: no half-migrated dep in foundation).

**Acceptance:** gate set green; contrast test green; `checkThemeColors` runs and passes against
its fresh allowlist; both pilots verified on device both modes vs the checklist; no cold-start
theme flash in dark mode (synchronous pref read lands in Batch 01, but the pilot path must not
regress).

**Size:** ~7 files touched, ~22 literals in the theme package, 0 in pilots.

## Batch 01 — XML layer, system-bars, bootstrap

**Files:** `res/values/themes.xml`, **delete `res/values/colors.xml`**,
`app/src/main/java/com/example/mallar/MainActivity.kt`, new `DarkSystemBars()` composable
(theme package), `ui/splash/SplashScreen.kt` (system-bar call only — full migration is Batch 09).

**Scope (all from Ticket 06):**
1. `Theme.MallAR` loses hardcoded `#06131A`; window background → neutral/transparent.
   `Theme.App.Starting` splash background → `#0F5F5F` (README §3 `brandTeal`/splash). **No
   `res/values-night/` created.** XML stays pinned light; no `setDefaultNightMode`.
2. Delete `res/values/colors.xml` entirely (all 71 entries 100% dead — inventory Part B).
3. `MainActivity.onCreate` calls `AppPreferences.init(this)` **synchronously before
   `setContent`** (codex C4). Cold-start acceptance test in dark mode: no light first frame.
4. `MallARTheme` `SideEffect` sets the **default** route system-bar appearance
   (light mode → dark icons, dark mode → light icons, both bars). `DarkSystemBars()` composable
   (uses `DisposableEffect { onDispose { restore } }`) is the opt-out for always-dark routes.
   Apply `DarkSystemBars()` to `SplashScreen` this batch as the always-dark proof; the rest
   (`LogoScanScreen`, `UnifiedNavigationScreen`, `ParkingCameraScreen`, `StaticMapScreen`,
   embedded `LocalizationConfirmScreen`) get it in their own batches.
5. Edge-to-edge **not** adopted — keep manual `statusBarsPadding()` / `navigationBarsPadding()`.

**Acceptance:** gate set green; app builds with `colors.xml` gone; cold start in dark mode shows
no light flash; `MallSelectionScreen` (still unmigrated) now shows correct status-bar icons via
the new default; `SplashScreen` renders dark with visible icons and no light bleed into the next
screen; allowlist row for `colors.xml` removed.

**Size:** 4 files + 1 deletion.

## Batch 02 — Shared renderers (gating)

**Files:** `ui/components/StoreLogo.kt`, `ui/home/HomeSharedComponents.kt`.

**Scope:**
1. `StoreLogo.kt` — `StoreLogoContainer` background `Color.White` → `imagePlaceholder`; add
   Coil `placeholder`/`error` using `imageErrorSurface`; monogram fallback text `LogoPrimary
   #258799` → `accentText` (retire the third bespoke teal). Keep `crossfade(false)`.
2. `HomeSharedComponents.kt` — **delete `HomeColorScheme` + `rememberHomeColorScheme`**
   (README §7.4). Migrate `GlowingSearchBar`, `PopularStoreCard`, `StoreRow`, `CategoryChip`,
   and every shared primitive to `MallTheme.colors.*`. `DesignPurple` → `accent`/`accentText`.
   `CyanGlow`, `DeepNavyBg`, `GlassCardBg` retired. Glow animations: disabled in light mode,
   kept in dark, respect `prefers-reduced-motion` (Ticket 01).

**Acceptance:** gate set green; `rememberHomeColorScheme` has zero references (grep clean);
the 4 screens that used it (`Homescreen`, `OffersScreen`, `VoucherDetailsScreen`,
`DestinationCategoryScreen`, `DestinationSearchScreen`) still compile (they'll be fully migrated
in 04/05/06 — a temporary direct `MallTheme.colors` read at their call sites is acceptable here);
shared components verified on device inside `LanguageScreen`/`ProfileScreen` host both modes.

**Size:** 2 files, 15 raw literals, 24 `.copy(alpha)`.

## Batch 03 — Auth & onboarding

**Files:** `ui/auth/WelcomeScreen.kt`, `SignInScreen.kt`, `SignUpScreen.kt`,
`PhoneAuthScreen.kt`, `OtpVerifyScreen.kt`.

**Scope:** retire the `SignInGradient` / `DarkGradientColors` literal arrays → token-based
surfaces (`screenBackground` + `surface`, no full-teal fill in light mode); `WelcomeScreen`
buttons/text → tokens (it already toggles `isAppearanceLightStatusBars` — replace with the
default contract from Batch 01); legacy `PhoneAuthScreen` / `OtpVerifyScreen` gain dark-mode
support (retire the hardcoded teal background).

**Special:** these are currently **light-only** (`OtpVerify`, `PhoneAuth`) or **broken**
(`SignIn`, `SignUp`) — the batch adds real dark-mode branching, not just token swaps.

**Acceptance:** gate set green; all 5 screens both modes vs checklist; no teal-flood in light
mode; system-bar icons correct; allowlist rows removed.

**Size:** 5 files, 16 raw literals, 39 `.copy(alpha)`.

## Batch 04 — Core Home

**Files:** `ui/home/Homescreen.kt` (~1102 lines).

**Scope:** 12 raw literals + 47 `.copy(alpha)` → tokens; **fix the `CyanGlow` floor-badge**
(≈1.2:1 → `accentText`); `ParkingCarIllustration` dark paths (`#0F2633`, `#0A1720`) → a
light/dark vector token pair (add to `MallColors` only if reused — else local `// theme-lint:allow`
with the illustration rationale); `BottomNav` pill → `secondaryContainer` (README §5);
`sampleOffers` `OfferItem.tint` stays data-owned with `// theme-lint:allow`, and text over its
gradient sits on `overlayScrimGradient` / `scrimCard`, never on `tint` (README §9).

**Acceptance:** gate set green; Home both modes vs checklist; the floor badge is legible; the
car illustration reads on the light canvas; allowlist row removed.

**Size:** 1 file, 12 raw literals, 47 `.copy(alpha)`.

## Batch 05 — Offers & Vouchers

**Files:** `ui/home/OffersScreen.kt`, `ui/home/VoucherDetailsScreen.kt`.

**Scope:** offer card borders/backgrounds `GlassCardBg` → `surface` + `border`; `SuccessGreen`
+ redemption glow → `success` / `successText`; **QR canvas stays literal black/white**
(functional requirement — `// theme-lint:allow qr-code-contrast`).

**Acceptance:** gate set green; both screens both modes vs checklist; redemption state uses
status tokens; QR scans; allowlist rows removed.

**Size:** 2 files, 0 raw literals (named-constant heavy), 21 `.copy(alpha)`.

## Batch 06 — Destination

**Files:** `ui/destination/DestinationCategoryScreen.kt`, `DestinationSearchScreen.kt`,
`DestinationSelectionScreen.kt`.

**Scope:** **flatten the 14-token `Dsel*` "Indigo-Violet" system** (`#5847E8` / `#6D5DF6`) onto
`accent` / `accentText` / `surface*` (Ticket 01 — no distinct wayfinding hue); Category + Search
migrate off `rememberHomeColorScheme` (already deleted in Batch 02) to `MallTheme.colors.*`; the
4 gradients in Selection → flat surfaces (light) / retained subtle dark treatment.

**Acceptance:** gate set green; all 3 both modes vs checklist; no palette dissonance
Home→Destination; allowlist rows removed.

**Size:** 3 files, 14 raw literals, 24 `.copy(alpha)`.

## Batch 07 — Profile / Settings / Saved Places

**Files:** `ui/profile/SavedPlacesScreen.kt` (`ProfileScreen` + `LanguageScreen` done as
pilots in Batch 00).

**Scope:** `SavedPlacesScreen` — eliminate the 6 local `Saved*` duplicate tokens (`#258799`,
`#F7F9FA`, `#1A1A2E`, …) → `MallTheme.colors.*`; uses `StoreLogoContainer` (fixed in Batch 02)
so the white-box bug is already gone; system bars via the default contract.

**Acceptance:** gate set green; `SavedPlacesScreen` both modes vs checklist; allowlist row
removed. **→ CORE-FLOW MILESTONE complete — run the fuller device pass here.**

**Size:** 1 file, 6 raw literals, 0 `.copy(alpha)`.

## Batch 08 — Parking suite

**Files:** `ui/parking/ParkingHomeScreen.kt`, `ParkingCameraScreen.kt`,
`ParkingScanResultScreen.kt`, `ParkingMapScreen.kt`.

**Scope:** biggest hotspot — **54 raw literals** across 4 files. Eliminate duplicate `Home*`
palette defs; OCR result tags (`#FF9800` → `warningText`, `#00E676` → `successText`, `#00BCD4`
→ `accentText`); `ParkingCameraScreen` keeps the always-dark viewfinder (`scrimSurface` +
`DarkSystemBars()`) while the permission fallback adapts to light; `ParkingMapScreen` canvas +
path colours → always-dark map tokens.

**Acceptance:** gate set green; all 4 both modes vs checklist; camera chrome stays dark with
visible icons; map canvas legible; allowlist rows removed.

**Size:** 4 files, 54 raw literals, 23 `.copy(alpha)`.

## Batch 09 — Localization / first-run / camera

**Files:** `ui/localization/PermissionsScreen.kt`, `LogoScanScreen.kt`,
`LocalizationConfirmScreen.kt`, `ui/mall/MallSelectionScreen.kt`, `ui/splash/SplashScreen.kt`.

**Scope:** `MallSelectionScreen` — 9 literals + 6 local tokens → `MallTheme.colors.*`, cold-start
icon fix confirmed; `SplashScreen` — full token migration, background `#0F5F5F` = `brandTeal`
splash value, `DarkSystemBars()` (added in Batch 01) confirmed, `DesignPurple`/`DesignNavy`/
`DesignCyan` retired; `LogoScanScreen` + embedded `LocalizationConfirmScreen` — camera viewfinder
+ overlay chrome → `scrimSurface`/`scrimCard`/`onScrim`, store-selection sheets adapt to light
without dark bleed, tier colours (`#00C853`/`#FFA000`/`#E53935`) → `successText`/`warningText`/
`errorText`, `DarkSystemBars()` on both; `PermissionsScreen` — hero dark section + M3 bottom
sheet reconciled.

**Special:** `LogoScanScreen`, `LocalizationConfirmScreen`, `ParkingCameraScreen` (Batch 08) are
**always-dark routes** — checklist for these is "renders dark, icons visible, no light bleed".

**Acceptance:** gate set green; all 5 vs the appropriate checklist; cold start → MallSelection
icons visible; no white sheet flash over the camera; allowlist rows removed.

**Size:** 5 files, 31 raw literals, 28 `.copy(alpha)`.

## Batch 10 — Navigation HUD & map chrome

**Files:** `ui/navigation/UnifiedNavigationScreen.kt`, `ui/navigation/StaticMapScreen.kt`.

**Scope:** 17 raw literals → tokens; **2D Compose HUD / map indicators in scope**
(`NavBlue`/`PathColor`/`StartGreen`/`EndRed` → `accentText`/status text tokens or always-dark
HUD tokens), **3D ARCore scene materials out of scope** (codex C14); `NavCard #F0121829` +
`NavSurface #0A0F1E` → `scrimCard` / `scrimSurface`; `DarkSystemBars()` on both (always-dark
routes); status-bar icon contrast over the dark map/camera canvas fixed.

**Acceptance:** gate set green; both screens: renders dark, HUD legible, route/position
indicators visible, icons visible; allowlist rows removed.

**Size:** 2 files, 17 raw literals, 12 `.copy(alpha)`.

## Batch 11 — Bespoke-dark overlays

**Files:** `ui/chatbot/ChatBottomSheet.kt`, `ui/home/StoreDetailScreen.kt`,
`voice/VoiceAssistantOverlay.kt`.

**Scope:**
1. `ChatBottomSheet` — convert light-only (`ChatSheetBg = Color.White`) to **dual-mode**: reads
   `MallTheme.colors.*`, so it renders correctly whether hosted by Home (light/dark) or launched
   over the `LogoScanScreen` camera (no blinding white flash). 8 literals + 4 `Chat*` tokens.
2. `StoreDetailScreen` (unwired) — 3 literals + `#1A1A2E` gradient → tokens. Migrated so it
   doesn't rot / trip the lint gate.
3. `VoiceAssistantOverlay` (`voice/` package, unwired) — 13 `Ai*` literals → always-dark overlay
   tokens (`scrimSurface`/`scrimCard`/`onScrim` + status text tokens). The `checkThemeColors`
   scope is `ui/**`; this file is outside it, so add an explicit allowlist entry / extend the
   scan root to cover `voice/` for this one file.

**Acceptance:** gate set green; `ChatBottomSheet` verified from **both** Home and LogoScan, both
modes; `StoreDetail` + `VoiceAssistant` compile clean and render (manually invoked); allowlist
rows removed.

**Size:** 3 files, 24 raw literals, 12 `.copy(alpha)`.

## Batch 12 — Lint-gate flip

**Files:** `app/build.gradle.kts` (or `buildSrc`), delete
`config/theme-migration-allowlist.txt`.

**Scope:** confirm the allowlist is empty (every batch shrank it); switch `checkThemeColors`
from "fail on new entries" to a **plain hard-error** — any literal `Color(...)`, named `Color.*`
constant, gradient-list literal, `.copy(alpha)` on a literal/token, or Kotlin `@color/` ref in
`ui/**` (+ the `voice/` exception) fails the build; delete the allowlist file. Full
codebase-wide verification pass.

**Acceptance:** gate set green with the hard rule active; `grep` confirms zero raw literals in
`ui/**` outside `ui/theme/**` (except marked `// theme-lint:allow` lines, all reviewed); the
contrast test and `checkThemeColors` are both green — migration complete.

**Size:** 1–2 build files + 1 deletion.

---

## Fog resolution (Ticket 05 §6)

All of the map's "Not yet specified" items are resolved — nothing deferred silently:

| Fog item | Resolution |
|---|---|
| Glass / glow aesthetic | Ticket 01 — flat surfaces + one shadow in light; glow animations off in light, kept in dark, honour reduced-motion. Applied in Batches 02 / 04 / 06. |
| Splash | Ticket 01 + 06 — `#0F5F5F` (`brandTeal` splash value), one fixed background both modes, no `values-night`. Batches 01 (theme) + 09 (screen). |
| Status colours | Ticket 03 §3 — paired fg/bg tokens (`success`/`onSuccess`/`successText`, `error`/`onError`/`errorText`, `warningText`). Applied throughout. |
| `DestinationSelectionScreen` violet | Ticket 01 — flatten to teal. Batch 06. |
| AR scene materials | Out of scope (README §5 note / codex C14) — 3D ARCore rendering only. 2D Compose HUD + map indicators are in scope (Batch 10). |
| `OfferItem.tint` data colours | Ticket 04 / README §9 — data-owned, `// theme-lint:allow`, text over its gradient sits on a token scrim. Batch 04. |
| `colors.xml` | 100% dead — deleted whole in Batch 01, not migrated. |

## Not covered by this migration (unchanged from the map's "Out of scope")

Follow-system / 3-way theme switching · dark-mode visual *polish* (token values captured, look
not chased) · notification / widget colours (app has neither) · 3D ARCore scene-material colours.
