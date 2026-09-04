# Spec: App-language localization (Arabic RTL, plus Spanish/French scaffolding)

**Status:** ready-for-agent
**Tracker:** local-markdown fallback (`gh` CLI not installed — see `docs/agents/issue-tracker.md`)
**Origin:** `/wayfinder` → `/grilling` → two `/codex-delegate` read-only debates, 2026-09-03.
Full decision record in agent memory `app-localization-effort.md`. Domain terms in `CONTEXT.md`
(**App language**, **Supported language**, **Canonical category**).
**Next:** `/to-tickets` to slice into tracer-bullet tickets, then delegated `/implement`.

---

## Problem Statement

MallAR is used in Cairo shopping malls, where most visitors read Arabic. The app nominally
"supports Arabic" — there is a `values-ar/` string file and a language toggle in the profile —
but in practice switching to Arabic barely changes the app:

- Roughly 70% of on-screen text is hardcoded English in Compose code, so it never translates.
- Several Arabic keys that live screens actually use are missing and silently fall back to English.
- Choosing Arabic tears down the whole task and restarts the app: the splash animation replays,
  the visitor is forced back through the mall picker, and they land on a different screen than
  the one they were on. It feels like a crash, not a language change.
- Toggling the language before startup finishes can crash the app outright.
- There is no bundled Arabic font, so Arabic text renders through inconsistent system fallback.
- Layout is not verified right-to-left; map/parking overlays draw fixed English labels.

Separately, there is demand to reach Spanish- and French-speaking visitors, but no groundwork
exists for adding languages at all — the mechanism, the resource discipline, and the RTL support
would all have to be invented first.

## Solution

Make Arabic work end-to-end and lay a reusable foundation for further languages.

From the visitor's perspective:

- On first launch the app comes up in the device's language when that language is supported
  (English or Arabic today), English otherwise.
- A visitor can open **Settings → Language** and choose a language from a list shown in each
  language's own name. The choice takes effect immediately, in place, without the app appearing
  to restart and without losing which mall they picked or where they were.
- The choice sticks across app restarts and process death. A visitor who previously had Arabic
  selected keeps Arabic after updating; one who previously chose English keeps English; one who
  never chose follows their device.
- In Arabic the entire interface is translated and laid out right-to-left, with a proper Arabic
  typeface — including the screens used while walking to a destination (the one exception is the
  logo-scan entry flow, deferred; see Out of Scope).
- Numbers, times, and phone digits stay in Western digits (0–9) in every language.
- Store categories and floor references read naturally in the chosen language.

From the team's perspective:

- Every user-facing string lives in resources. The build fails if an Arabic translation is
  missing.
- Spanish and French are prepared as a framework concern (resource contract, translator
  metadata, RTL-agnostic layout) but **no Spanish or French translation ships in this effort** and
  neither language is offered to visitors yet. Turning them on later is: add the two translated
  files, add two entries to the locale configuration and the Language screen.

## User Stories

### First launch and language resolution

1. As an Arabic-speaking visitor whose phone is set to Arabic, I want the app to open in Arabic
   the first time I launch it, so that I never see an English screen.
2. As an English-speaking visitor, I want the app to open in English, so that it matches the
   rest of my phone.
3. As a visitor whose phone is set to a language the app does not support (e.g. German), I want
   the app to open in English, so that I can still use it.
4. As a visitor whose phone is set to Spanish or French, I want the app to open in English for
   now (not a half-translated Spanish), so that the app is coherent.
5. As a returning visitor who previously chose Arabic in the old profile toggle, I want the app
   to still be in Arabic after this update, so that my preference is respected.
6. As a returning visitor who previously chose English in the old profile toggle, I want the app
   to stay in English after this update even if my phone is set to Arabic, so that my explicit
   choice is not overridden.
7. As a returning visitor who never opened the old toggle, I want the app to follow my device
   language after this update, so that a stale stored default does not override my phone.

### Changing language in the app

8. As a visitor, I want a Language option under Settings, so that I can change the app language
   without changing my whole phone.
9. As a visitor, I want each language listed in its own name and script (English, العربية,
   Español, Français), so that I can find mine even if the current UI language is foreign to me.
10. As a visitor, I want only the languages that are actually translated to be selectable
    (English and Arabic today), so that I am not offered a language that turns out to be English.
11. As a visitor, I want the language change to apply immediately, so that I see the result
    without hunting for a restart.
12. As a visitor changing language, I want to stay on the Language screen (or return to
    Settings), not be thrown back to the splash screen or the mall picker, so that it does not
    feel like the app restarted.
13. As a visitor, I want my selected mall and my place in the app preserved across a language
    change, so that I do not have to re-pick the mall or re-navigate.
14. As a visitor, I want my language choice remembered after I fully close and reopen the app,
    so that I choose once.
15. As a visitor, I want changing the language via my phone's system per-app-language settings
    to work identically to changing it in the app, so that both routes are consistent.
16. As a visitor, I want the Language screen's back action to return me to Settings, so that
    navigation behaves normally.

### Arabic completeness

17. As an Arabic-speaking visitor, I want every screen fully in Arabic — onboarding, permissions,
    auth, home, search, destination selection, offers, vouchers, profile, saved places, parking,
    the mall picker, and the splash screen — so that I never hit an English island.
18. As an Arabic-speaking visitor, I want the screens I use while walking to a destination (the
    route map, floor-change prompts, the navigation HUD, the arrival state) in Arabic, so that
    guidance is usable.
19. As an Arabic-speaking visitor, I want the spoken/typed assistant *controls and labels*
    (buttons, status text, field hints, accessibility labels) in Arabic, so that the assistant
    panel is not visibly English. (The assistant's language detection and generated replies are
    out of scope — see Out of Scope.)
20. As an Arabic-speaking visitor, I want error messages, dialogs, toasts, and empty states in
    Arabic, so that failure paths are localized too.
21. As an Arabic-speaking visitor relying on TalkBack, I want content descriptions in Arabic, so
    that the screen reader speaks my language.
22. As an Arabic-speaking visitor, I want the ~11 currently-missing Arabic strings (mall picker
    title/subtitle/"coming soon"/retry, "Store · Inside Mall") translated, so that the picker
    and profile are not partly English.

### Right-to-left layout and typography

23. As an Arabic-speaking visitor, I want the whole interface mirrored right-to-left — back
    arrows, chevrons, list alignment, drawers, tab order, sliders, screen transitions — so that
    it reads naturally.
24. As an Arabic-speaking visitor, I want Arabic text rendered in a proper Arabic typeface
    (Cairo), consistently across devices, so that text does not look broken or vary by phone.
25. As an English-speaking visitor, I want Latin text to look exactly as it does today after the
    Arabic font is added, so that the font change is invisible to me.
26. As an Arabic-speaking visitor, I want the route map and parking map to keep their real-world
    orientation (north stays north, "you are here" stays put) even though the UI is RTL, so that
    the map still matches the building.
27. As an Arabic-speaking visitor, I want labels drawn on the route map and parking map to be
    Arabic and correctly positioned, so that the maps are not the one English surface left.
28. As an Arabic-speaking visitor, I want text that mixes Arabic with a Latin brand name, a phone
    number, or a percentage to display in the right order (bidi-isolated), so that "Zara 15%"
    does not scramble.
29. As an Arabic-speaking visitor, I want phone-number and OTP entry fields to stay
    left-to-right with Western digits, while ordinary Arabic text fields are right-to-left, so
    that data entry is not confusing.

### Numbers, digits, and formats

30. As a visitor in any language, I want distances, times, percentages, result counts, OTP
    codes, and phone numbers shown in Western digits (0–9), so that they are unambiguous.
31. As an Arabic-speaking visitor, I want the country code shown as `+20`, not `+٢٠`, so that it
    is consistent with the rest of the app.
32. As a visitor, I want dates and times that the app generates (e.g. parking "saved at") shown
    with Western digits regardless of language, so that formatting does not shift under Arabic.

### Store categories and floors

33. As a visitor, I want store categories shown in my language (Fashion, Jewellery, Perfumes &
    Cosmetics, Dining, Pharmacy), so that filters and store cards are localized.
34. As a visitor, I want category labels to be consistent across the home, search, and
    destination-selection surfaces, so that the same category is not named several ways.
35. As a visitor, I want brand and store names left in their own name (Zara, Tissot), not
    translated, so that I recognize them.
36. As a visitor, I want floor references shown as "Floor 2" / "الطابق 2" (Western digit)
    through one consistent phrasing, so that "Ground Floor", "First Floor", and "Level 2" do not
    all appear for the same place.

### Developer / translator

37. As a developer, I want every user-facing string in resources with a consistent naming
    scheme, so that the codebase has one place for copy.
38. As a developer, I want the build to fail when an Arabic string is missing, so that Arabic
    cannot silently regress.
39. As a translator picking up Spanish or French later, I want placeholder markers, plural
    forms, and comments in the resource files, so that I can translate without reading the code.
40. As a developer, I want Spanish and French resource files *not* present in the build until
    they are translated, so that a Spanish/French device cannot accidentally select an
    all-English "translation".
41. As a developer, I want the dead `SettingsScreen` and the unused placeholder name/date
    resources removed, so that the codebase is not misleading.
42. As a QA tester, I want a debug build with pseudolocales enabled and a per-route RTL
    checklist, so that I can catch un-externalized strings, clipping, and mirroring bugs without
    a screenshot-test suite.
43. As a developer, I want the app-language mechanism proven on API 24, 32, and 33+ before the
    per-screen work starts, so that the whole effort is not built on an unverified foundation.

### Robustness

44. As a visitor, I want the in-app Language control to live in Settings, where it is not
    reachable while I am navigating or scanning, so that I cannot trigger a disruptive change at
    a bad moment. An OS-initiated per-app-language change during navigation behaves as a standard
    Android configuration change (a normal `recreate()`); no bespoke teardown/recovery is built,
    and if the verification spike shows `recreate()` corrupts an active camera/AR session that is
    recorded as a risk for a future effort, not fixed here.
45. As a visitor, I want dark mode and Arabic to work together, so that neither feature breaks
    the other.
46. As a visitor, I want the app to not crash if I change language during startup, so that the
    old race condition is gone.

## Implementation Decisions

### App-language mechanism

- **Adopt the Android per-app language API.** Add `androidx.appcompat:appcompat` at a version
  that supports per-app locales (AppCompat 1.6+). Migrate the single Activity from
  `ComponentActivity` to `AppCompatActivity` (a `ComponentActivity` descendant; compatible with
  Compose, CameraX, and SceneView). Provide an AppCompat-derived `NoActionBar` XML window theme
  (the current `android:Theme.Material.Light.NoActionBar` parent is invalid for
  `AppCompatActivity`); Compose Material3 theming is unaffected and stays as-is.
- **Splash:** keep `Theme.App.Starting` as a `Theme.SplashScreen` descendant, but point its
  `postSplashScreenTheme` at the new AppCompat NoActionBar theme. `installSplashScreen()` is
  already called before `super.onCreate()`.
- **Set the language** with `AppCompatDelegate.setApplicationLocales(LocaleListCompat…)`. This
  gives automatic in-place `Activity.recreate()` (no task teardown) and integration with the
  system per-app language settings page.
- **Persistence:** declare `AppLocalesMetadataHolderService` in the manifest with
  `android:enabled="false"` and the `autoStoreLocales="true"` metadata, so AppCompat persists
  the selected locale itself on **all** supported API levels (its own store below API 33, the
  framework store on 33+). No custom locale persistence is added.
- **Delete the custom locale plumbing:** the `attachBaseContext` override on the Activity, the
  deprecated `resources.updateConfiguration` calls, the `FLAG_ACTIVITY_CLEAR_TASK` relaunch in
  the profile toggle, and the *steady-state* direct read of `mallar_app_prefs` / key `language`.
- **`generateLocaleConfig` stays off.** Author `res/xml/locale_config.xml` by hand listing only
  `en` and `ar`, referenced from the manifest as `android:localeConfig`.
- **One source of truth:** after migration, AppCompat's locale store is authoritative. The
  `language` field and `setLanguage`/`getLanguage` are removed from `AppPreferences` (dark mode
  stays). Nothing else reads `mallar_app_prefs` key `language` at steady state.

### One-time legacy migration

- Runs **once**, early in `Application.onCreate` (before the first Activity is created), guarded
  by its own "migration done" flag so it never re-runs and never fights a later
  system-settings change. It is the one place still permitted to read the old `language` key.
- Logic, keyed on `SharedPreferences.contains("language")` (the old code only *writes* that key
  from an explicit `setLanguage()` — a default read never persists it):
  - key absent → set **no** explicit app locale (follow device).
  - key == `"ar"` → `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))`.
  - key == `"en"` → set an explicit English app locale (honour the past explicit choice).
  - key present but unrecognised → follow device.
- Because the migration runs before the first Activity and calls `setApplicationLocales`
  synchronously, a legacy-Arabic user's first post-update frame is Arabic (AppCompat may perform
  one `recreate()` during that first launch — acceptable, one-time).

### `AppLanguageResolver` + `AppLanguagePlatform` (primary seam)

- **`AppLanguageResolver`** — pure Kotlin, no Android types. Inputs: the explicit app
  locale tags (possibly empty), the ordered device locale list, and the set of **Supported
  language**s. Outputs:
  - `supported`: ordered list of user-selectable Supported languages. Today: English, Arabic.
    Spanish and French are modelled by the type but **not** in `supported` (no resources ship).
  - `effective(explicitTags, deviceLocales)`: the active Supported language — the explicit
    choice if present and supported, else the first supported device locale, else English.
  - `migrationDecision(legacyValue: String?, alreadyMigrated: Boolean)`: returns one of
    `FollowDevice` / `Explicit(language)` / `NoOp`, per the legacy-migration logic above.
- **`AppLanguagePlatform`** — thin Android adapter: reads `AppCompatDelegate.getApplicationLocales()`,
  calls `setApplicationLocales(...)`, exposes the device locale list. Not unit-tested; exercised
  by the spike's manual acceptance checks.
- **UI state:** a small state holder (an `object` sibling to `MallSession`, or a lightweight
  view-model) exposes the current Supported language as observable state for the Language screen
  and any subtitle that shows it, backed by `AppLanguagePlatform`.
- The old `AppPreferences` init-order race disappears: language no longer lives in
  `AppPreferences`, and the AppCompat store is read synchronously.

### Language screen (UX)

- New Compose screen + `NavHost` route (e.g. `"language"`), reached from **Settings →
  Preferences** in `ProfileScreen`, replacing the current two-chip inline toggle.
- One row per `AppLanguageResolver.supported` entry, each showing the language's **autonym**
  (its name in its own script: `English`, `العربية`; `Español`, `Français` when enabled). The
  current selection is marked. Back action returns to `ProfileScreen`.
- Selecting a row calls the platform adapter's `setApplicationLocales`; the resulting
  `recreate()` re-renders the app in the new language.
- **Lifecycle-gate acceptance contract** (do *not* add a broad "recreation bypass"): the
  existing `MainActivity` redirect that sends the user to mall-selection when
  `MallSession.selected == null` stays as-is. It already behaves correctly —
  - locale `recreate()`, same process: `MallSession.selected` is still non-null → no redirect;
  - process death: it is null → redirect to the picker, which is the intended behaviour.
  The tickets must **prove** (spike + manual check) that an ordinary locale `recreate()` with a
  live `MallSession` does not redirect, and must not introduce any path that allows Home/Profile/
  Language with no selected mall.

### String externalization

- **Every user-facing string** becomes a resource: `Text`, labels, placeholders/hints, dialog
  and button text, error/empty-state copy, toasts, and `contentDescription`s. Pure-debug strings
  (e.g. `"BUG"`, log text) are excluded.
- Scoped by a **closed route/overlay inventory** (the RTL/route inventory below is that list),
  grouped for tickets as: auth/onboarding/permissions/mall-picker; home/destination/offers/
  vouchers/profile/saved-places; parking; splash; assistant chrome; and the deferred
  navigation-guidance group.
- Consistent key-naming scheme. **All interpolation uses `%1$s`** — never `%d` / `%.0f`, because
  `stringResource(id, arg)` formats numeric placeholders in the active resource locale (Arabic
  would render Arabic-Indic digits). Every visible number is pre-formatted to a Western-digit
  string (see Numbers) and passed as `%1$s`.
- `<plurals>` for count/duration strings whose grammar varies by language.
- Translator metadata is part of the resource contract from the start: `xliff:g` placeholder
  markup around brands, numbers, units, and codes, plus `<!-- -->` comments where context is
  needed.

### Navigation-guidance screens — deferred, isolated ticket

- `UnifiedNavigationScreen` and `StaticMapScreen` are externalized in **one dedicated ticket,
  sequenced last**, reviewed with extra scrutiny.
- That ticket is **strings-only**: no change to `object NavigationState`'s structure, the
  navigation session manager, the pathfinding engine, drift monitoring, or the AR scene (per the
  AR Implementation Roadmap rules 6 & 8). Only literal → `stringResource` substitution, and
  routing the floor/category copy *that these screens display* through the shared helpers.
- The AR scene renders no text; nothing in the `ar/` package changes.

### Right-to-left and typography

- **Cairo** (SIL OFL) is bundled and applied via **locale-driven typography**: the active
  **App language** selects the `FontFamily`, so Arabic uses Cairo and Latin-script languages keep
  the current `FontFamily.SansSerif`. (Cairo ships Latin glyphs, so it must not be a blanket
  fallback that silently changes Latin rendering.) Weight mapping (regular/medium/bold → Cairo
  weights) is defined in the typography module. A short typography spike confirms Arabic and
  English rendering against acceptance screenshots.
- RTL is delivered through the platform (`supportsRtl` is on; the per-app locale drives layout
  direction). Work is the non-standard spots — see the inventory.
- **Physical-space surfaces are not mirrored:** the route map geometry, the camera preview, AR
  world geometry, and pan/zoom gesture math keep their orientation regardless of UI direction.
  Only overlays, controls, and text on top of them are localized and bidi-treated.
- Map/parking labels drawn with `nativeCanvas.drawText` at fixed pixel offsets are reworked to
  draw translated strings with direction-aware positioning.
- Interpolated values inside Arabic text (brands, numbers, %, units, codes) are wrapped with a
  bidi-isolation helper.

#### RTL / route inventory (audit every item under `ar_XB` and real `ar-EG`, in light and dark)

- Every `MainActivity` `NavHost` route: splash, mall-selection, welcome, sign-in, sign-up,
  OTP/verify, permissions, home, destination-selection, offers, voucher-details, store-detail,
  profile, saved-places, settings/preferences, **language**, parking home, parking scan-result,
  parking map, unified-navigation, static map, chat sheet, voice-assistant overlay.
- Excluded from this effort (see Out of Scope): the logo-scan screen and the localization-confirm
  screen.
- Per-item checks: mirrored chrome (back arrows, chevrons, drawer, bottom nav order); list/row
  alignment; `LazyRow` scroll direction on home, destination, and offers carousels; logical
  horizontal slide/transition direction in the sign-in / sign-up flow; correct bidi in mixed
  Arabic+Latin text; stable map orientation with translated `nativeCanvas` labels on static map,
  parking map, and the unified-navigation Canvas/HUD/floor sheet; phone & OTP fields forced LTR
  with Western digits, ordinary text fields RTL; no clipped text at French length; dark-mode +
  Arabic on every route.
- Not present in the app, so not in scope to build for: `HorizontalPager`, custom `Layout`.

### Numbers, digits, and dates

- A single **Western-digit formatter** (fixed `Locale.ROOT` / `Locale.US` numeric formatting)
  produces the string form of every app-generated number shown in the UI: floor numbers,
  scan-confidence percentage, search result counts, distance, ETA, and any interpolated count.
  These are passed into `%1$s` placeholders.
- **App-generated timestamps** (e.g. parking "saved at", currently `DateFormat.format(...)` with
  the ambient locale) use a fixed locale-neutral numeric pattern **`yyyy-MM-dd HH:mm`** — no
  localized month names, Western digits in every language.
- `values-ar` `country_code` is normalized to `+20`. Any other Arabic-Indic digits in `values-ar`
  copy are normalized to Western digits (the profile placeholder line containing "٥" is being
  deleted anyway).
- The `+20` phone-auth prefix is constructed in Kotlin in all three auth paths. **Egypt-only
  phone auth is a documented product constraint**; this effort does not internationalize the
  phone flow — it only makes the displayed prefix consistent.

### Canonical category vocabulary

- The **Canonical category** set is the five values the runtime mall graph carries: Fashion,
  Jewellery, Perfumes & Cosmetics, Dining, Pharmacy.
- The graph's stored key (literally `Perfumes& Cosmetics`, no space) stays unchanged and is used
  for **filtering / matching**. A new presentation-only mapping `categoryKey → displayStringRes`
  produces the localized label; normalization lives only there.
- Replace the category labels hardcoded in the **non-assistant display sites** (destination
  selection, home store cards, search filters) with the shared mapping. Category names that
  appear only in code, not the data ("Entertainment", "Services", "Cafés", "Food & Dining") are
  dropped from those surfaces.
- **`ChatSystem.kt` (`detectCategory`, the keyword→category dictionaries, and the chat category
  labels) is NOT touched** — it is assistant intent-parsing behaviour, frozen with the rest of
  the assistant engine. It already returns raw category keys.
- Category labels are **presentation-only**: not passed through navigation routes, not used as
  filter keys. Where a route carries a category label today, it carries the key instead.
- Re-tagging the mall data to a richer taxonomy is out of scope.

### Floor references

- One helper produces the floor label from an **integer** floor: `"Floor %1$d"` in the resource
  is replaced by `"Floor %1$s"` fed a Western-digit-formatted number → renders `Floor 2` /
  `الطابق 2`.
- `Voucher.floorLabel: String` and `OfferItem.floor: String` (hardcoded demo data holding
  `"2nd Floor"`, `"Ground Floor"`, …) become `floor: Int`; the label is derived **at
  composition time** via the helper (not inside `remember(place.id)`, which would go stale after
  a locale `recreate()`). `rememberPlaceMetadata()` is reworked so the floor/category label is
  computed on each composition.
- `Voucher.category` values outside the canonical set (`"Food"`) are normalized to the nearest
  canonical key (`"Food"` → the `Dining` key).
- Every remaining floor-label call site — destination selection, `HomeSharedComponents`
  (including its "Level 1 / Level 2" phrasing), the home offers list, the navigation
  floor-transition copy — is routed through the helper. "Ground/First Floor" and "Level N"
  naming is removed. Named floors can return with real multi-mall data.

### Translation-drift gate

- `lint { error += "MissingTranslation" }` in the app Gradle config. `values/` is the complete
  source of truth; `values-ar` must match it or the build fails.
- No Spanish/French files ship, so no exemption mechanism is configured. If a future interim
  state ever ships an untranslated locale, the agreed mechanism is
  `tools:ignore="MissingTranslation"` on that file's root `<resources>`, verified with
  `lintDebug` — not part of this effort.

### Cleanup folded in

- Delete `ui/profile/SettingsScreen.kt` (dead — Profile is the only settings route).
- Delete the unused placeholder resources `user_first_name`, `user_last_name`, `joined_time` and
  their `values-ar` counterparts.
- Complete the ~11 missing `values-ar` keys.
- The app / launcher name stays **"MallAR"** untranslated in every language.

### Sequencing (blocking edges)

1. **Spike** — AppCompat migration proven on API **24, 32, 33+**: cold launch; `AppLocalesMetadataHolderService`
   / `autoStoreLocales` persistence surviving process death and `recreate()`; the legacy
   migration for each documented case; language switch from Profile/Language; language switch
   *observed* during an active camera/AR session (facts only, no fix); `recreate()` preserving
   the Navigation-Compose route (not re-entering the `splash` destination) and the live
   `MallSession` (no picker redirect); splash behaviour on recreate. **Blocks 2–8.**
2. **Resource contract + lint gate** — key-naming scheme, `values/` completeness pass, `values-ar`
   parity, `<plurals>`, `xliff:g` + comments, the Western-digit formatter, the bidi helper, the
   `%1$s`-only rule. One-time manual validation that `lintDebug` fails on a deliberately removed
   `values-ar` key. **Blocks 4, 5, 6, 7.**
3. **Typography + RTL primitives** — Cairo packaging + locale-driven `FontFamily` + weight map;
   the map / `nativeCanvas` translated-label approach. **Blocks 6, 7.**
4. **Language screen + navigation UX** — route, autonym list, follow-device semantics, the
   one-time legacy migration wiring, the lifecycle-gate acceptance checks, removal of the old
   toggle and old plumbing. Depends on **1, 2**.
5. **Data-vocabulary cleanup** — the 5-category `categoryKey → displayStringRes` map; the floor
   helper; `Voucher`/`OfferItem` `floor: Int` + `"Food"`→`Dining`; `rememberPlaceMetadata`
   rework; drop the dead category names from display sites; document the Egypt-only phone
   constraint. Depends on **2**.
6. **Per-area string externalization** — auth/onboarding/mall/permissions; home/destination/
   offers/vouchers/profile/saved; parking; splash; assistant chrome. Each depends on **2, 3**;
   the home/destination area also depends on **5** (uses the category/floor helpers).
7. **Navigation-guidance externalization** — the isolated strings-only ticket
   (`UnifiedNavigationScreen`, `StaticMapScreen`). Depends on **2, 3, 5, 6**.
8. **RTL / localization QA** — the full route inventory above, pseudolocales, real `ar-EG`, dark
   mode, API 24/32/33+ matrix, on-device camera/AR language-switch observation. Depends on all
   implementation tickets.

## Testing Decisions

**What a good test is here:** assert externally observable behaviour, not implementation. For the
resolver that means: given explicit tags + a device locale list + the supported set,
`effective(...)` returns the right **Supported language**; `migrationDecision(...)` returns the
right outcome for each legacy case. For the vocabulary helpers: given a stored category key or a
floor integer, the right display resource id / Western-digit argument comes back. Tests do not
assert which platform API was called or how persistence is stored.

**Modules with unit tests (JVM, `app/src/test`):**

- **`AppLanguageResolver`** — `supported` contents and order (English, Arabic; not Spanish/French);
  `effective(...)` for (a) explicit Arabic, (b) explicit English, (c) no explicit + Arabic
  device, (d) no explicit + unsupported device → English, (e) explicit unsupported → falls
  through; `migrationDecision(...)` for legacy `null`, `"ar"`, `"en"`, unrecognised, and
  `alreadyMigrated == true`. Pure — no fakes needed. Prior art: `data/MallSessionTest`,
  `data/MallTest`.
- **Category display mapping** — each of the 5 canonical keys (including the unnormalized
  `Perfumes& Cosmetics`) maps to its display resource; an unknown key returns no label; the
  voucher `"Food"` alias resolves to the `Dining` key. Prior art: `data/MallTest`.
- **Floor label helper** — floor `2` → the floor string resource + Western-digit arg `"2"`;
  the Western-digit formatter emits `"2"` not `"٢"` under an Arabic default locale. Prior art:
  `data/MallTest`.

**Compose UI test (`app/src/androidTest`), thin:**

- **Language screen** — renders one row per supported language with its autonym; tapping a row
  invokes the selection callback with that language; the current language is marked. Prior art:
  `ui/mall/MallSelectionScreenTest`.

**Spike / manual acceptance (matches the "no screenshot infra" decision) — exact steps recorded
in the spike ticket:**

- Persistence: set Arabic in-app → force-stop → relaunch → still Arabic; repeat via the system
  per-app-language setting; on API 24, 32, 33+.
- Legacy migration: seed `mallar_app_prefs` with each of `{absent, "ar", "en", "xx"}` → first
  launch of the new build lands in the expected language.
- `recreate()`: change language while deep in the app (home, profile, active navigation) → same
  route afterwards, mall not re-picked; splash not replayed.
- Camera/AR: change language (system setting) during an active scan/AR session → record whether
  the session survives; no fix expected.

**Build-gate assertions (not unit tests):**

- One-time manual check during ticket 2: remove a `values-ar` key, confirm `./gradlew lintDebug`
  fails, restore it.
- `./gradlew compileDebugKotlin`, `./gradlew testDebugUnitTest`,
  `./gradlew compileDebugAndroidTestKotlin` stay green (the project's existing gates).
- Debug builds enable Android **pseudolocales** (`en_XA`, `ar_XB`); the QA pass (ticket 8) uses
  them against the route inventory to catch un-externalized strings and clipping.

## Out of Scope

- **The logo-scan entry flow** — `ui/localization/LogoScanScreen.kt` (which also hosts `object
  NavigationState`) and `ui/localization/LocalizationConfirmScreen.kt`. The AR Implementation
  Roadmap freezes the pre-navigation scan flow and instructs implementers to *stop and escalate*
  rather than modify it; a resource-only edit there needs explicit AR-owner authorization and is
  not something a ticket agent decides. Left English this effort; a tiny separately-authorized
  follow-up ticket can localize those two files later.
- **The voice / chat assistant engine.** Speech-to-text and text-to-speech, the `isArabic`
  language detection, `SmartResponseEngine`, `NavigationVoiceController`, `ChatSystem`
  (`detectCategory`, keyword dictionaries, generated replies), intent parsing. Only the assistant
  panel's *chrome* (buttons, labels, hints, status text, accessibility labels) is localized.
  Consequence, accepted: an Arabic-UI visitor may still see English typed replies;
  Spanish/French visitors get English assistant behaviour.
- **Shipping Spanish or French.** No `es`/`fr` translation is produced, no `values-es`/
  `values-fr` files are added, neither is offered in the Language screen or the locale config.
- **Professional / native review** of the Arabic — the existing Egyptian-colloquial `values-ar`
  and the new AI first-pass keys. A native review pass is a follow-up.
- **`object NavigationState`** structure changes, and its stale `preferArabicVoice` locale
  capture — recorded as a known voice defect, not fixed here.
- **Building any safe teardown / recovery** for a language change during an active camera/AR
  session. The spike observes; a fix is a future effort.
- **Internationalizing phone auth.** The `+20` Egypt prefix and E.164 construction stay.
- **Locale-aware number / date / currency formatting** and **Arabic-Indic digits** — the
  decision is Western digits everywhere.
- **A richer store-category taxonomy** and re-tagging `mall_graph.json`; **named floors** — the
  multi-mall data-layer effort (agent memory `mall-selection-spec`).
- **Screenshot / instrumented-locale test infrastructure** (Paparazzi/Roborazzi).
- **Play Store store-listing localization** — a separate, non-binary task before market launch.

## Further Notes

- **Contradicts no ADR** (none exist). The AppCompat-migration decision is a candidate for the
  repo's first ADR (hard to reverse, surprising in a Compose-only app, a real trade-off vs. the
  `LocaleManager`-only and custom-plumbing alternatives). Extract it during `/to-tickets` or as
  `docs/adr/0001-*`.
- **Two Codex read-only debates** (2026-09-03) shaped this spec. Confirmed: no other Activities /
  services / widgets / WorkManager / notification channels render strings; the five runtime
  categories are correct; AppCompat + `AppCompatActivity` is the right mechanism and the
  "ContextWrapper without `AppCompatActivity`" shortcut does not work for Compose on API 24–32;
  `postSplashScreenTheme` is compatible with the existing `SplashScreen`; the legacy `"en"` value
  *is* distinguishable (`SharedPreferences.contains`).
- **Side finding, separate ticket (not this effort):** `AndroidManifest.xml` declares
  `android.hardware.camera.ar` as `required="true"` while ARCore metadata marks AR optional —
  contradicts the AR roadmap's Phase 0.
- The full decision record and deferred/out-of-scope rationale live in agent memory
  `app-localization-effort.md`.
