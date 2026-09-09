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
4. **System-bar appearance** — where does `isAppearanceLightStatusBars` / nav-bar icon mode get
   set once, keyed on `isDarkMode`? (A `SideEffect` in `MallARTheme`, or in `MainActivity`.)
   Does this effort adopt `enableEdgeToEdge()` / `WindowCompat.setDecorFitsSystemWindows(false)`,
   or leave window-fitting exactly as-is and only fix the icon colour? Prefer the smallest change
   that makes light mode correct — no layout-inset refactor unless the inventory shows it's
   already broken.
5. **`AppCompatDelegate.setDefaultNightMode`** — is it called anywhere today? If the XML
   `DayNight` parent is going to keep mattering, the app's `isDarkMode` toggle may need to drive
   `setDefaultNightMode` too so XML and Compose agree. Decide whether that coupling is in scope
   or the XML side is simply pinned to one mode.

**Output**: the answer records the XML strategy + the system-bar strategy + the smallest-change
boundary. Feeds Ticket 03 (§6) and Ticket 05 (the XML-layer ticket).
