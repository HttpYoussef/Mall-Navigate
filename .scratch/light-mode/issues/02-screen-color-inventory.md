Type: task
Status: open
Blocked by: none

## Question

Catalogue how every screen **and every XML resource** currently handles colour, so the migration
can be sliced into implementer-sized batches, no light-mode breakage is missed, and Tickets 01,
06, 07 have the shapes they need.

AFK — this is code reading, no decision. It is the sole frontier ticket; run it first.

**Part A — Compose files.** For every `*.kt` file under
`app/src/main/java/com/example/mallar/ui/**`, first **classify** it (codex C10):
- **route screen** — a top-level destination in the nav graph,
- **shared renderer** — a composable used by 2+ screens (`HomeSharedComponents`, `StoreLogo`,
  `ChatBottomSheet`, …),
- **non-rendering support** — ViewModel, state holder, pure helper (no colour surface).

Publish a **route manifest** (nav-graph destination → file) alongside the table. Then per file
produce a row:
- File + rough screen/component name + classification.
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

Screenshot capture is **not** in this ticket — it moved to Ticket 07 (fixture harness + baseline).

**Then produce:**
1. `.scratch/light-mode/screen-inventory.md` — the route manifest + full Part A table (grouped
   by classification) + Part B tables.
2. A proposed **batch grouping** for migration, sized honestly (codex C10 — expect ~13 implementer
   sessions once foundation + XML + lint-flip are counted, not ~8):
   - **route-screen batches** by area (Home flow, Auth/onboarding, Parking, Navigation/AR chrome,
     Profile/Settings, Localization),
   - **shared-renderer batch(es)** — called out separately because they gate the screens that use
     them (codex C11),
   - **bespoke-dark batches** — AI assistant, Chatbot sheet, StoreDetail (own full dark palettes),
   - **XML-layer batch**,
   - non-rendering support files listed as **no-op** (no migration).
   Give each batch a rough size (file count, literal count) so each is plausibly one session.
3. A **surprises list** for the map's fog — anything that isn't a simple literal-to-token swap:
   status-colour groups + their fg/bg pairs (feeds Ticket 03, codex C14), `OfferItem.tint`-style
   data-owned colours (codex C13), AR scene-material colours vs map-mode Compose indicators
   (codex C14), `.copy(alpha=)` faked dark mode + alpha-bearing "tokens" (feeds Ticket 03,
   codex C7), renderers shared across light + always-dark screens, the `DayNight` parent implying
   `values-night` is expected (codex C15).

**Output**: `screen-inventory.md` + route manifest linked from this ticket; the answer records the
batch grouping and surprises list. Unblocks Tickets 01, 06, 07; feeds 03, 05, 08.
