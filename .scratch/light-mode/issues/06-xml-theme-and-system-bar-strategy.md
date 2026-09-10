Type: grilling
Status: open
Blocked by: 02

## Question

Decide what happens to the **XML resource layer** and to **system-bar appearance** under the new
token system. Decision only — no code changes from this ticket; the outcome feeds Tickets 03 and
05, and the actual work lands in the XML-layer / foundation migration tickets.

**Why this exists** (from the charting adversarial pass — Tickets 01-05 were Compose-only):
- `res/values/themes.xml` → `Theme.MallAR` hardcodes `#06131A` (dark navy) for
  `android:windowBackground`, `android:statusBarColor`, `android:navigationBarColor`. Parent is
  `Theme.AppCompat.DayNight.NoActionBar` but there is **no `res/values-night/`**. In light mode
  the window and system bars stay dark regardless of Compose.
- `Theme.App.Starting` (Android 12+ splash) hardcodes `windowSplashScreenBackground` `#06131A`.
- `res/values/colors.xml` holds ~80 hardcoded colours, many dead or per-screen, no night variants.
- `isAppearanceLightStatusBars` is set in only 2 of ~30 screens (`SplashScreen` hardcodes
  `false`; `WelcomeScreen` uses `!isDarkMode`); the rest never touch it → white status-bar icons
  on a light background in light mode. `MainActivity` has no `enableEdgeToEdge()` and no central
  system-bar handling — it's `setContent { MallARTheme { Surface { NavGraph } } }`.

**Decide:**
1. **`themes.xml`** — replace the hardcoded darks with `?attr` / `@color` references resolved
   per mode? Add a `res/values-night/themes.xml`? Or set window + bar colours from Compose at
   runtime and neutralise the XML values? (`Theme.AppCompat.DayNight` + `AppCompatDelegate`
   night mode vs the app's own `isDarkMode` boolean — reconcile which one wins.)
2. **`colors.xml`** — keep as a mirror of the token palette (needed for any remaining XML /
   drawables / menus), or delete down to the minimum the inventory (Ticket 02 Part B) proves is
   still referenced?
3. **Splash** — `windowSplashScreenBackground` per mode (needs `values-night`), or accept one
   fixed brand background for the splash moment (coordinate with Ticket 01's splash rec).
4. **System-bar appearance — route/surface-aware, not global** (codex C3). A single
   `isDarkMode`-keyed `SideEffect` is wrong: in **light mode** the always-dark routes
   (`LogoScanScreen`, `UnifiedNavigationScreen`, `ParkingCameraScreen`) still render dark
   surfaces, so global light-mode icons would be dark-on-dark. Decide the **override contract**:
   how a route declares "my status/nav bar is over a dark surface", where the default lives
   (`MallARTheme`), how it's **restored on navigation away**, and that it covers **both** the
   status bar and the navigation bar. Does this effort adopt `enableEdgeToEdge()` /
   `WindowCompat.setDecorFitsSystemWindows(false)`, or leave window-fitting as-is and only fix
   icon colour? Prefer the smallest change — no layout-inset refactor unless the inventory shows
   it's already broken.
5. **One authoritative mode source** (codex C15). The XML theme inherits
   `Theme.AppCompat.DayNight`; Compose uses the manual `isDarkMode` boolean — two sources that
   can disagree. The boolean is **locked** (map Notes); decide what the XML/AppCompat side does:
   pin it to one mode, or have the `isDarkMode` toggle also drive `AppCompatDelegate
   .setDefaultNightMode` so they agree. Include a decision + acceptance case for **keyboard/IME
   appearance** (the chatbot opens an `OutlinedTextField` IME — `ChatBottomSheet.kt`) and for
   **Activity recreation** on a night-mode change.
6. **Synchronous mode bootstrap** — **IN SCOPE** (user sign-off 2026-09-10, resolves codex C4).
   `AppPreferences.isDarkMode` starts `false` and loads async on `Dispatchers.IO`; `MainActivity`
   calls `setContent` immediately, so a saved dark-mode user gets a **light first frame → dark**
   flash while the native splash stays XML-dark. *That* it's fixed is settled; this ticket decides
   *how*: SharedPreferences blocking read in `MainActivity.onCreate` before `setContent`, vs an
   `installSplashScreen` keep-condition until the pref loads, vs seeding `AppPreferences` from a
   synchronous read at construction. Pick one and specify a cold-start acceptance test (saved
   dark user, no light frame).

**Output**: the answer records the XML strategy, the route-aware system-bar contract, the
authoritative-mode decision, and the bootstrap decision + smallest-change boundary. Feeds
Ticket 03, Ticket 08, and Ticket 05.
