Type: task
Status: open
Blocked by: none

## Question

Catalogue how every screen **and every XML resource** currently handles colour, so the migration
can be sliced into implementer-sized batches, no light-mode breakage is missed, and Tickets 01,
06, 07 have the shapes they need.

AFK — this is code reading, no decision. It is the sole frontier ticket; run it first.

**Part A — Compose screens.** For every `*.kt` file under
`app/src/main/java/com/example/mallar/ui/**` produce a row:
- File + rough screen/component name.
- Colour sources used: raw `Color(0x…)` literals (count), `Color.White`/`.Black`/`.Gray`,
  named constants from `Color.kt` / `HomeSharedComponents.kt`, `MaterialTheme.colorScheme.*`,
  `rememberHomeColorScheme`, XML `@color/…` refs, `Brush`/gradient literals, `.copy(alpha=…)`
  on literals.
- Does it branch on `isDarkMode` / `isSystemInDarkTheme`? (yes / partial / no)
- Does it touch `isAppearanceLightStatusBars` / `WindowCompat` / `statusBarsPadding` / edge-to-edge?
- Light-mode verdict: **works**, **broken** (what breaks — white-on-white, dark bleed,
  unreadable text, invisible system-bar icons), or **light-only** (never handled dark).
- Camera/AR/always-dark surface? Own bespoke dark palette (`ai_*`, `chat_*`, `store_detail_*`)?
- Non-chrome colour concerns (status colours, brand tints on data, AR scene materials).

**Part B — XML layer.** Inventory:
- `res/values/themes.xml` — `Theme.MallAR` (`windowBackground`/`statusBarColor`/
  `navigationBarColor` all hardcoded `#06131A`), `Theme.App.Starting` splash (`#06131A`),
  parent `Theme.AppCompat.DayNight.NoActionBar`. No `values-night/`.
- `res/values/colors.xml` — ~80 colours. For each: where it's referenced (layout XML, drawable,
  theme, Kotlin `@color/`), or **dead**. Group the per-screen clusters (`nav_*`, `ai_*`,
  `chat_*`, `camera_*`, `home_*`, `auth_gradient_*`, `store_detail_*`, `path_*`).
- Any `res/layout/`, `res/drawable/`, `res/menu/` files with hardcoded colour.

**Then produce:**
1. `.scratch/light-mode/screen-inventory.md` — the full Part A table + Part B tables.
2. **Before-screenshots** of every screen in the current build, both light and dark mode, saved
   under `.scratch/light-mode/before/` — the regression baseline Tickets 01, 05, 07 rely on.
   (If a device/emulator build isn't available to this session, say so and list exactly which
   screens still need capturing.)
3. A proposed **batch grouping** for migration — by area (Home flow, Auth/onboarding, Parking,
   Navigation/AR, Profile/Settings, Localization, shared components) **plus separate harder
   batches** for the bespoke-dark-palette screens (AI assistant, Chatbot sheet, StoreDetail) and
   the XML layer — with a rough per-batch size (file count, literal count) so each is plausibly
   one implementer session.
4. A **surprises list** for the map's fog — anything that isn't a simple literal-to-token swap
   (status-colour groups, AR material colours, `.copy(alpha=)` faked dark mode, components shared
   across light + always-dark screens, `DayNight` parent implying `values-night` is expected).

**Output**: `screen-inventory.md` + `before/` linked from this ticket; the answer records the
batch grouping and surprises list. Unblocks Tickets 01, 06, 07; feeds 03 and 05.
