# 02: AppCompat per-app-language foundation

**What to build:** The app runs on the standard Android per-app-language API. Changing the
device's *system* per-app language for MallAR (Settings → Apps → MallAR → Language) switches the
app between English and Arabic with no task teardown, and the choice sticks across a force-stop.
A returning user's old language preference is migrated once. The old home-grown locale plumbing
is deleted. There is no in-app Language screen yet — that is ticket 03.

**Blocked by:** 01 (uses its pinned configuration and confirmed behaviour).

**Status:** ready-for-agent

- [ ] `MainActivity` extends `AppCompatActivity`; `androidx.appcompat` dependency added; an
      AppCompat `NoActionBar` window theme is the app theme; `Theme.App.Starting` still provides
      the splash and its `postSplashScreenTheme` points at the new theme; Compose Material3
      theming unchanged.
- [ ] `AppLocalesMetadataHolderService` + `autoStoreLocales="true"` declared; `res/xml/locale_config.xml`
      hand-written listing only `en` and `ar`; referenced as `android:localeConfig`.
      `android.androidResources.generateLocaleConfig` stays off/unset.
- [ ] `AppLanguageResolver` (pure Kotlin, no Android types): `supported` (English, Arabic only —
      Spanish/French modelled but excluded); `effective(explicitTags, deviceLocales)`;
      `migrationDecision(legacyValue, alreadyMigrated)` returning `FollowDevice` / `Explicit(lang)` /
      `NoOp`.
- [ ] `AppLanguagePlatform` thin adapter over `AppCompatDelegate.getApplicationLocales()` /
      `setApplicationLocales(...)` and the device locale list.
- [ ] One-time legacy migration runs early in `Application.onCreate`, guarded by a persisted
      "migration done" flag, keyed on `SharedPreferences.contains("language")` in
      `mallar_app_prefs`: absent → follow device; `"ar"` → explicit Arabic; `"en"` → explicit
      English; unrecognised → follow device. It is the only remaining reader of that key.
- [ ] Deleted: the `MainActivity.attachBaseContext` locale override and its
      `resources.updateConfiguration` calls; the `FLAG_ACTIVITY_CLEAR_TASK` relaunch in the
      profile language toggle's `onLanguageChange`; `AppPreferences.language` / `setLanguage` /
      `getLanguage` (dark-mode members stay). No steady-state code reads `mallar_app_prefs` key
      `language`.
- [ ] The pre-startup `AppPreferences` init-order crash on language toggle is gone (language no
      longer lives in `AppPreferences`).
- [ ] Unit tests for `AppLanguageResolver`: `effective(...)` for explicit-Arabic,
      explicit-English, no-explicit + Arabic device, no-explicit + unsupported device → English,
      explicit-unsupported → fallback; `migrationDecision(...)` for legacy `null`, `"ar"`,
      `"en"`, unrecognised, and `alreadyMigrated == true`.
- [ ] Manual acceptance (steps recorded): system per-app language → Arabic flips the
      already-resource-backed screens (auth, permissions, mall-picker chrome) with no splash
      replay and no mall re-pick; force-stop + relaunch stays Arabic; on API 24, 32, 33+.
- [ ] `./gradlew compileDebugKotlin`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin` green.
