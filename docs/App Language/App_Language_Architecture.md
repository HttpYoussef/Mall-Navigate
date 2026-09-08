# App Language — Architecture (Source of Truth)

Self‑contained. You do not need to read any other file to start work. Read `README.md` for
where this sits; read `Localization_Conventions.md` before editing strings.

---

## 1. What the subsystem does

Render the entire MallAR UI in a user‑chosen language, persist that choice across process
death, and lay out RTL correctly for Arabic — without a backend, without a custom locale
engine, and without touching the AR scene, the navigation session, or the assistant engine.

Shipped languages: **English (`en`)**, **Arabic (`ar`, Egyptian colloquial, RTL)**.
Modelled but not shipped: Spanish (`es`), French (`fr`).

---

## 2. The mechanism: AndroidX per‑app language

MallAR uses the **standard Android per‑app language API**, not a homegrown one.

| Piece | Where | Purpose |
|---|---|---|
| `androidx.appcompat:appcompat` `1.7.0` | `gradle/libs.versions.toml` (`appcompat`), `app/build.gradle.kts` (`libs.androidx.appcompat`) | Provides the per‑app locale API on all supported API levels. |
| `MainActivity : AppCompatActivity()` | `app/src/main/java/com/example/mallar/MainActivity.kt` | The single Activity. `AppCompatActivity` is a `ComponentActivity` — Compose, CameraX, SceneView all still work. |
| `Theme.MallAR` → `Theme.AppCompat.DayNight.NoActionBar` | `app/src/main/res/values/themes.xml` | AppCompat‑compatible window theme (the old `android:Theme.Material.*` parent is invalid for `AppCompatActivity`). Dark mode is still done in Compose, not here. |
| `Theme.App.Starting` → `postSplashScreenTheme = @style/Theme.MallAR` | `app/src/main/res/values/themes.xml` | Splash hands off to the AppCompat theme. `installSplashScreen()` runs before `super.onCreate()`. |
| `res/xml/locale_config.xml` (`en`, `ar`) + `android:localeConfig` | `AndroidManifest.xml` `<application>` | Declares the shippable languages to the OS (enables the system per‑app‑language picker on Android 13+). Hand‑authored — `generateLocaleConfig` stays **off**. |
| `AppLocalesMetadataHolderService` + `<meta-data android:name="autoStoreLocales" android:value="true"/>` | `AndroidManifest.xml` (`android:enabled="false"`) | AppCompat persists the chosen locale itself — its own store below API 33, the framework `LocaleManager` on 33+. **No custom persistence.** |
| `android:supportsRtl="true"` | `AndroidManifest.xml` | RTL layout mirroring from the platform. |

**Applying a language:** `AppCompatDelegate.setApplicationLocales(LocaleListCompat…)`. This
performs an in‑place `Activity.recreate()` (no task teardown), and is what the system
per‑app‑language settings page calls too.

**Reading the current language:** `AppCompatDelegate.getApplicationLocales()` for the explicit
choice; `LocaleManagerCompat.getSystemLocales(context)` for the device list.

There is **no** `attachBaseContext` override, no `resources.updateConfiguration`, no
`FLAG_ACTIVITY_CLEAR_TASK` relaunch. All of that was deleted. `AppPreferences` no longer has a
`language` field (dark mode stays).

---

## 3. Language resolution — the pure seam

All non‑trivial language logic is **pure Kotlin with no Android imports**, so it is unit‑tested
directly. Android specifics live in a thin adapter.

### `AppLanguage` (enum) — `com.example.mallar.data.AppLanguageResolver.kt`
```
enum class AppLanguage(val code: String, val autonym: String) {
    ENGLISH("en","English"), ARABIC("ar","العربية"),
    SPANISH("es","Español"), FRENCH("fr","Français")
    companion object { fun fromTag(tag: String?): AppLanguage?   // "ar_EG" -> ARABIC
}
```
`es`/`fr` exist in the type so framework code compiles against a 4‑value world, but they are
**not** in `supported` and no resources ship for them.

### `AppLanguageResolver` (object, pure) — same file
```
val supported: List<AppLanguage> = [ENGLISH, ARABIC]          // the only user-selectable set
fun effective(explicitTags: List<String>, deviceLocales: List<String>): AppLanguage
    // 1st explicit tag that is supported -> else 1st device locale that is supported -> ENGLISH
fun migrationDecision(legacyValue: String?, alreadyMigrated: Boolean): MigrationDecision
```
`MigrationDecision = FollowDevice | Explicit(AppLanguage) | NoOp`.

### `AppLanguagePlatform` (object, Android adapter) — `com.example.mallar.data.AppLanguagePlatform.kt`
```
fun currentTags(): List<String>                       // from AppCompatDelegate.getApplicationLocales()
fun deviceLocales(context: Context? = null): List<String>
fun currentLanguage(context: Context? = null): AppLanguage    // = resolver.effective(currentTags(), deviceLocales(context))
fun apply(language: AppLanguage)                       // setApplicationLocales(forLanguageTags(code)) -> recreate()
fun applyFollowDevice()                                // setApplicationLocales(emptyLocaleList())
```
Not unit‑tested; exercised by on‑device acceptance checks.

> **Performance note (learned the hard way).** `currentLanguage()` makes binder calls on
> Android 13+ (`LocaleManager`). Never call it on every recomposition. `MallARTheme` resolves
> it once via `remember(context)` — see commit `0a09460`, and `Known_Gaps_And_Followups.md`.
> `LanguageScreen`'s route wrapper still calls it unremembered, which is acceptable only
> because that screen does not recompose in a hot loop.

---

## 4. One‑time legacy migration

Old builds stored the language in `SharedPreferences("mallar_app_prefs")` key `"language"`.

`com.example.mallar.data.LegacyLanguageMigration.runOnce(context)` is called **once** from
`MallARApplication.onCreate()` **before** any Activity, guarded by a `"language_migrated"`
boolean flag in the same prefs file.

| Legacy `"language"` value | Action (`AppLanguageResolver.migrationDecision`) |
|---|---|
| absent (`!prefs.contains("language")`) | `FollowDevice` → `applyFollowDevice()` |
| `"ar"` | `Explicit(ARABIC)` → `apply(ARABIC)` |
| `"en"` | `Explicit(ENGLISH)` → `apply(ENGLISH)` (honour the past explicit choice) |
| any other string | `FollowDevice` |
| (flag already set) | `NoOp` — never re‑runs, never fights a later system‑settings change |

Because it runs synchronously before the first Activity, a legacy‑Arabic user's first frame
after updating is Arabic (AppCompat may do one `recreate()` during that first launch — a
one‑time cost).

---

## 5. Layers

```
┌─────────────────────────────────────────────────────────────────────┐
│ OS / AppCompat per-app locale store        (persistence, recreate)   │
└───────────────▲─────────────────────────────────────────────────────┘
                │ setApplicationLocales / getApplicationLocales
┌───────────────┴─────────────────────────────────────────────────────┐
│ AppLanguagePlatform          (Android adapter, thin, not tested)     │
└───────────────▲─────────────────────────────────────────────────────┘
                │ effective() / migrationDecision()
┌───────────────┴─────────────────────────────────────────────────────┐
│ AppLanguageResolver + AppLanguage      (PURE, unit-tested)           │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ Presentation primitives  (com.example.mallar.data, mostly pure)     │
│   WesternDigits · BidiFormatting · FloorLabel · StoreCategory       │
│   Timestamps                                                         │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ Typography          (com.example.mallar.ui.theme)                    │
│   CairoFontFamily · fontFamilyFor() · typographyFor()               │
│   MallARTheme  — swaps FontFamily + Typography by active language    │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ Screens          (stringResource / pluralStringResource everywhere) │
│   + the Language screen (com.example.mallar.ui.language)             │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ Resources        values/strings.xml  ⟷  values-ar/strings.xml       │
│   lint { error += "MissingTranslation" }  — parity is a build gate  │
└─────────────────────────────────────────────────────────────────────┘
```

Full per‑file detail is in `Component_Map.md`.

---

## 6. Typography & RTL

- **Cairo** (SIL OFL) is bundled at `app/src/main/res/font/cairo.ttf` (a variable font).
  `CairoFontFamily` in `ui/theme/Type.kt` declares 4 weights via `FontVariation.Settings`.
- `fontFamilyFor(AppLanguage)` → `CairoFontFamily` for Arabic, `FontFamily.SansSerif`
  otherwise. Cairo ships Latin glyphs, so it must **never** be a blanket fallback — it is
  applied only under Arabic.
- `MallARTheme` (`ui/theme/Theme.kt`) reads the active language once, then provides the
  matching `Typography` to `MaterialTheme` and the matching `FontFamily` via
  `LocalTextStyle`. New screens need no font work.
- RTL comes from the platform (`supportsRtl` + the per‑app locale drives `LayoutDirection`).
  Compose auto‑mirrors `Row`, `start`/`end` padding, and `Icons.AutoMirrored.*`. Work is only
  the non‑standard spots — see `Localization_Conventions.md` → "RTL".
- **Physical space is never mirrored.** Route‑map geometry, camera preview, AR world geometry,
  and pan/zoom math keep their orientation regardless of `LayoutDirection`. Only overlays,
  controls, and text on top are localized and bidi‑treated. `nativeCanvas.drawText` labels on
  the parking map and static route map resolve their strings outside the draw block and flip
  `Paint.Align` / the anchor offset under RTL, leaving geometry untouched.

---

## 7. The lifecycle gate (do not "fix" it)

`MainActivity` redirects to mall‑selection when `MallSession.selected == null`. This is
correct as‑is and must stay:

- **Locale `recreate()`, same process:** `MallSession.selected` is still non‑null → **no
  redirect**. The user stays on the same route, in the new language.
- **Process death:** `MallSession.selected` is null (not persisted) → redirect to the picker.
  Intended.

Never add a "recreation bypass". Never introduce a path that reaches Home/Profile/Language
with no selected mall.

---

## 8. Hard boundaries (never cross without explicit AR/assistant owner sign‑off)

- `com.example.mallar.ar` package — **zero changes**, ever, for a language task.
- `object NavigationState` (lives in `ui/localization/LogoScanScreen.kt`),
  `NavigationSessionManager`, the pathfinding engine, `DriftMonitor` — structure untouched.
- `ui/localization/LogoScanScreen.kt`, `ui/localization/LocalizationConfirmScreen.kt` — frozen
  by the AR roadmap. **Left English.** Never externalize them without AR‑owner authorization.
- The voice / chat assistant **engine**: `ChatSystem.kt` (`detectCategory`, keyword
  dictionaries, generated replies), `SmartResponseEngine`, `NavigationVoiceController`,
  `LocalIntentParser`, STT/TTS locale handling. Only the assistant **chrome** (buttons,
  labels, status text, `contentDescription`) is localized. An Arabic user may see English
  typed replies — accepted.
- Phone auth: the `+20` Egypt prefix and E.164 construction stay. Not internationalized.

---

## 9. Change process

- **Minor** (a string, a plural, a new screen wired with `stringResource`, tuning a font
  weight within the existing weight map): just do it, keep both catalogs in parity, run the
  gates. No doc change here.
- **Major** (the AppCompat dependency; how language is resolved/applied/persisted/migrated;
  the seam types; adding/removing a `supported` language; the RTL policy; the
  physical‑space‑never‑mirrored rule): update this file, add to `Decisions_And_Rationale.md`,
  and open an ADR. Do not silently reinterpret this document in code.

---

## 10. The gates (run from repo root, Git Bash)

```
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin lintDebug --console=plain -q
```
Exit 0 = pass. ~3–5 min. `lintDebug` enforces `MissingTranslation` (abortOnError) — a
`values-ar` key missing its `values/` twin (or vice‑versa) fails the build.
`gradle.properties` raises `MaxMetaspaceSize` to 768m so lint does not OOM.
