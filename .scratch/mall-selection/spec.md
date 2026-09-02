# Spec: Mall Selection Screen

Status: ready-for-human
Effort charted via `/wayfinder` on 2026-09-02, then adversarially reviewed with
Codex (read-only) and revised. No wayfinder map — the build fits one agent
session.

## Summary

Add a **mall selection screen** as the first interactive screen after the splash
screen. It lets the user pick which mall they are visiting from a fixed list of
three: **City Stars**, **City Centre Almaza**, **Mall of Egypt**. Only City Stars
is functional today; the other two render as inert "Coming soon" cards.

This is an **interim, screen-only** change. It deliberately does **not** build a
multi-mall data layer — all existing mall data (graph, embeddings, floor plans,
logos) stays hardwired to City Stars. The screen exists so the multi-mall data
work can land later without also having to retrofit the entry flow.

## Hard constraints on this change

- **No existing callback signatures change.** No changes to business logic in any
  existing screen, ViewModel, repository, or manager.
- The **only** edits to existing files are:
  1. `MainActivity.kt` — nav-graph wiring (new route + redirect the post-splash
     navigation target). Unavoidable: a screen cannot be inserted into a flow
     without touching the navigation between its neighbours. Existing branching
     logic (`isFirstLaunch` → `welcome`/`home`, `markNotFirstLaunch()`) is
     **relocated verbatim, not modified**.
  2. `Homescreen.kt` — one `Text` element (the already-agreed subtitle change).
- `NavigationState`, `StartupCoordinator`, `ProfileScreen` logout, and every
  other existing file are **not touched**.
- Everything else is new files.

## Scope decisions (from the wayfinder grilling + Codex review)

| # | Decision |
|---|----------|
| Tracker | Local markdown (`.scratch/mall-selection/`); `gh` CLI is not installed on this machine. |
| Scope | Picker screen only. No changes to `StartupCoordinator`, repositories, or asset loading. |
| Current mall | The existing `mall_navigation_map_v1.0.json` etc. **is City Stars.** |
| Frequency | Picker shows on **every cold launch**, right after splash. |
| Persistence | **None.** Selection is per-session, held in memory only. Not written to SharedPreferences. |
| Flow position | Between `splash` and everything else, on every launch. On first launch it comes **before** `welcome` (before auth), not after. |
| Coming-soon malls | Greyed-out, "Coming soon" badge, **not tappable**. No toast / dialog. |
| Selected-mall storage | **New `MallSession` object** (NOT `NavigationState` — see "Why not NavigationState"). Observable-backed. |
| Post-selection display | Home header subtitle changes from `"Discover your favorite brands"` to `"City Stars · Discover your favorite brands"`. |
| Card visuals | Text only: title + location subtitle + a generic Material storefront icon. No mall photos (none exist in the project). |
| Back behaviour | No back button in the UI. System back on the picker exits the app (it is a root screen). |
| Data load timing | Unchanged. Splash still preloads City Stars and only advances on `StartupState.Success`. The picker guards its own selection action against startup state (see §3). |
| Localization | **English only** for now. Arabic string resources are a follow-up. |
| Logout | Unchanged. Logout routes to `welcome` without re-passing the picker; `MallSession.selected` keeps its value. Acceptable — logout is account scope, not mall scope. |

## Why not `NavigationState`

`docs/AR/Engineering Architecture and Guidlines/AR_Implementation_Roadmap.md`
rule 8 explicitly forbids modifying `NavigationState`'s structure beyond the one
additive change scoped to AR Phase 1. `AR_Subsystem_Redesign_Final.md` §6/§20
documents `NavigationState` as a global-mutable-singleton anti-pattern the
architecture works around (via `NavigationSessionInputAdapter`'s one-time
snapshot read) precisely because it already has too many consumers. Adding a
field to it contradicts that documented direction.

A standalone `MallSession` object has none of that baggage, is not read by the AR
subsystem, and keeps this feature fully additive.

## Domain model

New term added to `CONTEXT.md`:

- **Mall** — one shopping centre the app can navigate. Exactly one Mall is
  *active* per app session, chosen on the mall selection screen. Today only the
  **City Stars** Mall has navigation data; **City Centre Almaza** and **Mall of
  Egypt** are declared but not yet navigable.
- **Mall session** — the in-memory, per-launch record of which Mall the user
  picked. Not persisted.

## Target flow

```
First launch:   splash ──▶ mall_selection ──▶ welcome ──▶ (auth / skip) ──▶ home
Returning:      splash ──▶ mall_selection ──▶ home
OS-restored:    (task restored past the picker) ──▶ gate redirects ──▶ mall_selection ──▶ home
```

`mall_selection` always sits directly after `splash`. It replaces `splash` as
the screen the splash pops to.

## Implementation outline

### 1. Mall model — `app/src/main/java/com/example/mallar/data/Mall.kt` (new)

```kotlin
enum class Mall(val hasNavigationData: Boolean) {
    CITY_STARS(hasNavigationData = true),
    CITY_CENTRE_ALMAZA(hasNavigationData = false),
    MALL_OF_EGYPT(hasNavigationData = false),
}
```

- The enum holds **identifiers only**. All display text (name, location,
  "Coming soon") lives in `res/values/strings.xml`, mapped by enum constant in
  the screen (`@StringRes` lookup or a small `when`).
- `hasNavigationData` is named for what it means — "this app build ships data for
  this mall" — not an intrinsic property. When the real multi-mall data layer
  lands, availability moves there and this flag is removed.

### 2. Mall session holder — `app/src/main/java/com/example/mallar/data/MallSession.kt` (new)

```kotlin
object MallSession {
    private val _selected = MutableStateFlow<Mall?>(null)
    val selected: StateFlow<Mall?> = _selected.asStateFlow()
    fun select(mall: Mall) { _selected.value = mall }
}
```

- Observable so the Home subtitle recomposes correctly (Codex NB1: a plain `var`
  would render stale if Home stays composed).
- Process-global; lost on process death — handled by the gate in §4.
- No `reset()`. Nothing clears it (logout included).

### 3. Mall selection screen — `app/src/main/java/com/example/mallar/ui/mall/MallSelectionScreen.kt` (new)

Signature: `MallSelectionScreen(startupState: StartupState, onMallSelected: (Mall) -> Unit, onRetry: () -> Unit)`

- Heading, e.g. "Choose your mall", plus an explanatory subtitle:
  "Where are you navigating this visit?" (Codex Q1 — the no-persistence choice is
  otherwise confusing).
- A card per `Mall.entries`:
  - `hasNavigationData == true`: full-colour, tappable, calls `onMallSelected(mall)`.
  - `hasNavigationData == false`: reduced alpha, "Coming soon" badge,
    `enabled = false`, no ripple.
  - Card content: generic storefront icon (`Icons.Outlined.Storefront`, marked
    decorative — no contentDescription) + mall name + location.
- **Accessibility** (Codex NB5): each card exposes a merged semantics node that
  announces name + location +, for unavailable malls, "Coming soon" + disabled
  state. Verify text contrast in both themes.
- **Startup-state guard** (Codex #2): the selectable card's tap is only enabled
  when `startupState == StartupState.Success`. While `Loading`/`Idle`, show a
  small inline spinner on/under the City Stars card and ignore taps. On
  `StartupState.Error`, show a "Retry" affordance that calls `onRetry` (wired in
  §4 to the existing `StartupCoordinator.retry` — the same call `SplashScreen`
  already makes; no new business logic).
- No `TopAppBar` back button. No `BackHandler` — default back behaviour (pop the
  back stack) exits the app because the picker is the only entry after the splash
  pop.
- Respect dark mode the same way sibling screens do.

### 4. Nav graph — `MainActivity.kt` (only wiring; no logic change)

**a. Redirect the post-splash target.** `navigateAfterSplash` currently decides
`welcome` vs `home` and navigates there. Change it to navigate to
`mall_selection`, popping `splash`:

```kotlin
val navigateAfterSplash: () -> Unit = {
    navController.navigate("mall_selection") {
        popUpTo("splash") { inclusive = true }
    }
}
```

**b. New route.** The `welcome`/`home` branch (verbatim, including the
`isFirstLaunch.value` check) moves into the picker's callback:

```kotlin
composable("mall_selection") {
    MallSelectionScreen(
        startupState = startupState,               // already in scope in MallARNavGraph
        onRetry = { StartupCoordinator.retry(context) },   // same call SplashScreen makes
        onMallSelected = { mall ->
            MallSession.select(mall)
            val destination = if (isFirstLaunch.value) "welcome" else "home"
            navController.navigate(destination) {
                popUpTo("mall_selection") { inclusive = true }
            }
        }
    )
}
```

`markNotFirstLaunch()` stays exactly where it is (welcome / sign-in / sign-up
callbacks) — untouched.

**c. Lifecycle gate** (Codex #1). Compose Navigation restores the back stack
across process death, so Android can recreate the task directly at `home`,
skipping `splash` and the picker, with `MallSession.selected` null. Add a small
additive guard at the `NavHost` level:

```kotlin
val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
val selectedMall by MallSession.selected.collectAsState()
LaunchedEffect(currentRoute, selectedMall) {
    if (selectedMall == null &&
        currentRoute != null &&
        currentRoute != "splash" &&
        currentRoute != "mall_selection"
    ) {
        navController.navigate("mall_selection") {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }
}
```

- Depends only on `MallSession.selected` — **not** on `StartupState`, so it
  cannot deadlock after logout (logout leaves `StartupCoordinator` `Idle`, but
  the gate doesn't care, and `MallSession.selected` is still set post-logout
  anyway, so the gate stays dormant).
- On a normal cold launch the gate never fires (splash → picker directly).
- Worst residual case: process death *while the picker is visible* + a startup
  failure on re-init → user sits on the picker with a Retry button. Acceptable
  for an interim screen.

### 5. Home header — `Homescreen.kt` (~line 194, one `Text`)

```kotlin
val selectedMall by MallSession.selected.collectAsState()
// ...
Text(
    text = selectedMall?.let { stringResource(mallNameRes(it)) + " · " }.orEmpty() +
        "Discover your favorite brands",
    color = currentTextMain.copy(alpha = 0.7f),
    fontSize = 14.sp
)
```

No other change to `Homescreen.kt`. `"Hello, $userName 👋"` untouched.

### 6. Strings — `res/values/strings.xml` (English only)

`mall_select_title`, `mall_select_subtitle`, `mall_coming_soon`, and one
name + location pair per mall:

| Enum constant | Name | Location |
|---|---|---|
| `CITY_STARS` | City Stars | Nasr City, Cairo |
| `CITY_CENTRE_ALMAZA` | City Centre Almaza | Heliopolis, Cairo |
| `MALL_OF_EGYPT` | Mall of Egypt | 6th of October, Giza |

No `values-ar` entries in this spec.

## Tests — `app/src/androidTest/.../MallSelectionScreenTest.kt` (new, additive)

Compose UI tests:

- Available (City Stars) card invokes `onMallSelected(Mall.CITY_STARS)` on tap
  when `startupState == Success`.
- Coming-soon cards are disabled in semantics and do **not** invoke the callback.
- Selectable card does not invoke the callback while `startupState == Loading`.
- `StartupState.Error` shows Retry and it invokes `onRetry`.
- Disabled cards expose "Coming soon" in their semantics.

Navigation-level checks (may stay manual if instrumented nav testing is not set
up): first-launch → `welcome`; returning → `home`; Activity recreation on the
picker keeps you on the picker; simulated null `MallSession` on a restored `home`
redirects to the picker.

## Out of scope (explicitly not in this effort)

- Multi-mall data layer: per-mall graph / embeddings / floor plans / logos, and
  making `StartupCoordinator` + repositories mall-aware.
- Real navigation data for City Centre Almaza and Mall of Egypt.
- Persisting the selected mall across sessions, or a "switch mall" entry point
  from home / profile.
- Arabic localization of the picker.
- Any change to offers, parking, or chatbot to be mall-aware.
- Any change to `NavigationState`, `StartupCoordinator`, or the logout flow.
- Deep-link handling — `initialIntentData` is captured but unused, and the
  manifest declares only `LAUNCHER`; there are no deep links to preserve or break.

## Validation

- Cold launch (first install): splash → picker → tap City Stars → welcome →
  skip → home shows "City Stars · Discover your favorite brands".
- Cold launch (returning user): splash → picker → tap City Stars → home.
- Almaza / Mall of Egypt cards are visibly disabled and do nothing on tap; a
  screen reader announces them as "Coming soon", disabled.
- System back on the picker exits the app.
- Dark mode renders the picker correctly.
- Selecting a mall before startup finishes is blocked with a spinner; a startup
  error shows Retry.
- Kill the app process from `home`, relaunch from Recents → lands back on the
  picker (gate), not a mall-less home.
- Existing City Stars navigation, logo scan, AR, parking behave exactly as before
  once past the picker.
