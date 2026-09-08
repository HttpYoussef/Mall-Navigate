# Decisions & Rationale

Why the App Language subsystem is built the way it is. Locked decisions, rejected
alternatives, and the out‑of‑scope list. If you want to change one of these, that's a **major
change** (Architecture §9) — update the architecture doc and open an ADR.

Planning history: `.scratch/i18n/spec.md` + two Codex read‑only design debates (2026‑09‑03).
Fuller rationale in agent memory `app-localization-effort.md`.

---

## Locked decisions

### D1 — Use the AndroidX per‑app language API, migrate to `AppCompatActivity`

Adopt `androidx.appcompat` + `AppCompatDelegate.setApplicationLocales` +
`AppLocalesMetadataHolderService`. Migrate the single Activity `ComponentActivity` →
`AppCompatActivity`, switch the window theme to an AppCompat parent.

**Why:** it is the platform‑standard mechanism, gives free integration with the system
per‑app‑language settings page, and AppCompat handles persistence on every API level itself.

**Rejected:**
- *Custom `attachBaseContext` / `ContextWrapper` plumbing* (what the app had) — deprecated
  `updateConfiguration`, an init‑order race with `AppPreferences`, a `CLEAR_TASK` relaunch on
  switch, and it doesn't integrate with system settings. Deleted.
- *`LocaleManager` only (API 33+)* — leaves 24–32 unsolved.
- *"`ContextWrapper` without `AppCompatActivity`"* — confirmed in debate it does **not** work
  for Compose on API 24–32.

**Cost accepted:** `AppCompatActivity` instead of `ComponentActivity` (still a
`ComponentActivity` descendant — Compose/CameraX/SceneView unaffected); an AppCompat XML
window theme; a legacy‑Arabic user may see one extra `recreate()` on the first post‑update
launch.

This is the strongest candidate for the repo's first ADR (`docs/adr/0001`). None exist yet.

### D2 — Ship English + Arabic only; model es/fr but don't ship them

`values-es/` / `values-fr/` are **not** created. `AppLanguage` has `SPANISH` / `FRENCH` but
they're not in `AppLanguageResolver.supported`.

**Why:** a `values-<lang>/` file makes Android auto‑select that locale on a matching device
*regardless of `localeConfig`*, and untranslated keys then fall back to English — a
half‑translated app in the wild with no gate. A language ships only when its catalog is
complete. The framework is proven by Arabic + the resource contract + pseudolocales; adding
es/fr later is ~2 files + 2 lines (`Adding_A_Language.md`).

### D3 — Arabic is Egyptian colloquial

The malls are in Cairo. Match the existing `values-ar` tone (conversational, not MSA). New
keys are an AI/human first pass; a professional native review is a tracked follow‑up, not a
blocker.

### D4 — Western digits (0–9) in every language; no locale number/date formatting

**Why:** `stringResource(id, arg)` formats `%d` in the active resource locale → Arabic‑Indic
digits under `values-ar`. Rather than fight that everywhere, the rule is: `%1$s` only, every
number pre‑formatted with `WesternDigits`. Timestamps use a fixed `yyyy-MM-dd HH:mm` pattern
(no localized month names). Arabic‑Indic digits, locale‑aware number/date/currency formatting:
explicitly out of scope.

### D5 — `AppLanguageResolver` pure, `AppLanguagePlatform` thin

All decision logic (effective language, migration outcome) is pure Kotlin, unit‑tested
directly. Android APIs are a thin adapter that is not unit‑tested (exercised on device). Tests
assert outcomes, never which API was called.

### D6 — One‑time legacy migration in `Application.onCreate`, keyed on `contains("language")`

Runs once, guarded by a flag, before the first Activity. The old code only *writes* the
`"language"` key on an explicit `setLanguage()`, so `contains()` distinguishes "user chose
English" (`"en"` → honour it) from "never chose" (absent → follow device). Never re‑runs, so
it never fights a later system‑settings change.

### D7 — The `MallSession.selected == null` lifecycle gate stays exactly as‑is

No "recreation bypass". A locale `recreate()` keeps `MallSession.selected` non‑null → no
redirect; process death nulls it → redirect to picker (intended). Adding a bypass would let
Home/Profile/Language render with no mall selected.

### D8 — Cairo via locale‑driven typography, not a blanket fallback

Cairo (SIL OFL) is applied **only** under Arabic via `fontFamilyFor()`. Cairo ships Latin
glyphs, so making it a global fallback would silently change Latin rendering. Latin‑script
languages keep `FontFamily.SansSerif`.

### D9 — Physical space is never mirrored

Route‑map geometry, camera preview, AR world geometry, pan/zoom math: unchanged by
`LayoutDirection`. Only overlays, controls, and text on top are localized / bidi‑treated /
direction‑aware. `nativeCanvas` labels flip `Paint.Align` and offset sign only.

### D10 — Canonical categories = the 5 runtime graph keys; display‑only mapping

`Fashion, Jewellery, Perfumes& Cosmetics (sic), Dining, Pharmacy`. The raw key stays
everywhere it's used for filtering / matching / nav routes; a presentation‑only
`categoryKey → @StringRes` map produces the label. Code‑only names ("Entertainment",
"Services", "Cafés") are dropped from display. `ChatSystem.detectCategory` and its keyword
dictionaries are **not** touched — assistant intent parsing, frozen.

### D11 — Floors: one `Floor %1$s` formatter from an `Int`

"Ground/First Floor" and "Level 1/2" naming removed. `Voucher` / `OfferItem` hold `floor: Int`;
the label is derived at composition time (never cached in `remember(id)` — stale after a
locale `recreate()`). Named floors can return with real multi‑mall data.

### D12 — Navigation‑guidance screens externalized in one isolated, last, strings‑only ticket

`UnifiedNavigationScreen` + `StaticMapScreen`. No change to `NavigationState` structure, the
session manager, pathfinding, drift monitoring, or the AR scene. Literal → `stringResource`
only, plus routing the floor/category copy those screens display through the shared helpers.

### D13 — Translation‑drift gate = `lint { error += "MissingTranslation" }`

`values/` is the complete source of truth; `values-ar` must match or the build fails. No
Spanish/French exemption mechanism is configured because none ship.

### D14 — No screenshot / instrumented‑locale test infrastructure

No Paparazzi / Roborazzi. QA is automated gates + unit/UI tests + a manual on‑device pass +
debug pseudolocales.

---

## Out of scope (do not re‑add without a scope change)

- **The logo‑scan entry flow** (`LogoScanScreen.kt` — also hosts `object NavigationState` —
  and `LocalizationConfirmScreen.kt`). Frozen by the AR roadmap; a resource‑only edit needs
  explicit AR‑owner authorization. Left English; a tiny separately‑authorized follow‑up can
  localize them.
- **The voice / chat assistant engine.** STT/TTS, `isArabic` detection, `SmartResponseEngine`,
  `NavigationVoiceController`, `ChatSystem` (`detectCategory`, keyword dicts, generated
  replies), intent parsing. Only the assistant **chrome** is localized. Accepted: an Arabic‑UI
  visitor may see English typed replies; es/fr visitors get English assistant behaviour.
- **Shipping Spanish / French** (D2).
- **Professional native review** of the Arabic (existing + new keys). A follow‑up pass.
- **`object NavigationState` structure changes**, and its stale `preferArabicVoice` locale
  capture — recorded as a known voice defect, not fixed here.
- **Safe teardown / recovery for a language change during an active camera/AR session.** The
  spike observes; a fix is a future effort.
- **Internationalizing phone auth.** `+20` Egypt prefix and E.164 construction stay.
- **Locale‑aware number / date / currency formatting; Arabic‑Indic digits** (D4).
- **A richer store‑category taxonomy; re‑tagging `mall_graph.json`; named floors** — the
  multi‑mall data‑layer effort (agent memory `mall-selection-spec`).
- **Screenshot / instrumented‑locale test infra** (D14).
- **Play Store store‑listing localization** — a separate task before market launch.

---

## Side findings (separate issues, not this subsystem)

- `AndroidManifest.xml` declares `android.hardware.camera.ar` as `required="true"` while
  ARCore metadata marks AR optional — contradicts the AR roadmap's Phase 0. Own ticket.
