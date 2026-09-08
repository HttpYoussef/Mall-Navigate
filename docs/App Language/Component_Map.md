# Component Map — every file the App Language subsystem owns or touches

Paths are from repo root. Signatures are current as of branch `feat/app-localization`.

---

## 1. Mechanism / wiring

| File | Role |
|---|---|
| `gradle/libs.versions.toml` | `appcompat = "1.7.0"`; `androidx-appcompat` catalog entry. |
| `app/build.gradle.kts` | `implementation(libs.androidx.appcompat)`; `lint { error += "MissingTranslation" }`. |
| `app/src/main/AndroidManifest.xml` | `android:localeConfig="@xml/locale_config"`, `android:supportsRtl="true"`, the `AppLocalesMetadataHolderService` block with `autoStoreLocales=true`. |
| `app/src/main/res/xml/locale_config.xml` | Shippable languages: `en`, `ar`. **Add a `<locale>` line here per new language.** |
| `app/src/main/res/values/themes.xml` | `Theme.MallAR` → `Theme.AppCompat.DayNight.NoActionBar`; `Theme.App.Starting` → `postSplashScreenTheme=@style/Theme.MallAR`. |
| `app/src/main/java/com/example/mallar/MainActivity.kt` | `class MainActivity : AppCompatActivity()`; `installSplashScreen()` before `super.onCreate()`; `setContent { MallARTheme { … } }`; `composable("language") { LanguageScreen(onBackClick = …) }`; the `MallSession.selected == null` lifecycle gate (leave as‑is). |
| `app/src/main/java/com/example/mallar/MallARApplication.kt` | `LegacyLanguageMigration.runOnce(this)` first line of `onCreate()`. |
| `app/src/main/java/com/example/mallar/data/AppPreferences.kt` | Dark mode only. **No `language` field** (removed). |
| `gradle.properties` | `-XX:MaxMetaspaceSize=768m` so `lintDebug` doesn't OOM. |

---

## 2. Pure seam — `com.example.mallar.data`

### `AppLanguageResolver.kt`
- `enum class AppLanguage(code, autonym)` — `ENGLISH, ARABIC, SPANISH, FRENCH`;
  `AppLanguage.fromTag(tag: String?): AppLanguage?`.
- `sealed interface MigrationDecision { FollowDevice; Explicit(language); NoOp }`.
- `object AppLanguageResolver`:
  - `val supported: List<AppLanguage> = [ENGLISH, ARABIC]` — **the user‑selectable set. Add a
    language here to ship it.**
  - `fun effective(explicitTags, deviceLocales): AppLanguage`.
  - `fun migrationDecision(legacyValue: String?, alreadyMigrated: Boolean): MigrationDecision`.
- Tested: `app/src/test/java/com/example/mallar/data/AppLanguageResolverTest.kt`.

### `AppLanguagePlatform.kt` (Android adapter, not tested)
- `currentTags()`, `deviceLocales(context?)`, `currentLanguage(context?)`, `apply(AppLanguage)`,
  `applyFollowDevice()`.
- ⚠ `currentLanguage()` → binder IPC on API 33+. Don't call per‑recomposition.

### `LegacyLanguageMigration.kt`
- `object` with `runOnce(context)`. Prefs file `"mallar_app_prefs"`, keys `"language"` (legacy
  value) + `"language_migrated"` (guard). Delegates the decision to
  `AppLanguageResolver.migrationDecision`.

### `WesternDigits.kt`
- `object`. `format(Int)`, `format(Long)`, `format(Double, decimals = 0)`, `percent(Int)`.
  `Locale.US`‑fixed.
- Tested: `WesternDigitsTest.kt`.

### `BidiFormatting.kt`
- `object BidiFormatting { FSI; PDI; isolate(String): String }`.
- `fun String.bidiIsolated(): String` extension.
- Tested: `BidiFormattingTest.kt`.

### `FloorLabel.kt`
- `data class FloorLabel(@StringRes resId: Int, arg: String)` (+ `formattedFloor` alias).
- `fun floorLabel(floor: Int): FloorLabel` → `R.string.floor_label` + `WesternDigits.format`.
- `@Composable fun FloorLabel.asString()`, `fun FloorLabel.format(context)`.
- `@Composable fun floorDisplayLabel(floor: Int): String`, `fun floorDisplayLabel(floor, context)`.
- `R.string.floor_label` = `"Floor %1$s"` / `"الطابق %1$s"`.

### `StoreCategory.kt`
- `object StoreCategory` — the 5 raw‑key constants + `CANONICAL_KEYS` + `displayRes(rawKey)`.
- `fun categoryDisplayRes(rawKey: String?): Int?` — raw → `@StringRes`; whitespace‑normalized;
  `"Food"` → Dining; `null` for unknown.
- `@Composable fun categoryDisplayLabel(rawKey: String?): String?`.
- Keys: `category_fashion`, `category_jewellery`, `category_perfumes_cosmetics`,
  `category_dining`, `category_pharmacy`, plus `category_all` for the "All" chip.

### `Timestamps.kt`
- `object Timestamps { fun format(epochMillis: Long): String }` — `yyyy-MM-dd HH:mm`,
  `Locale.US`.
- Tested: `TimestampsTest.kt` (incl. `0L → "1970-01-01 00:00"`).

---

## 3. Typography — `com.example.mallar.ui.theme`

| File | Contents |
|---|---|
| `Type.kt` | `val CairoFontFamily` (4 weights via `FontVariation.Settings`, all from `R.font.cairo`); `fun fontFamilyFor(AppLanguage): FontFamily` (Cairo for Arabic, else `SansSerif`); `fun typographyFor(FontFamily): Typography` and `typographyFor(AppLanguage)`; `val Typography` (the base SansSerif set). |
| `Theme.kt` | `@Composable fun MallARTheme(content)`. Resolves `activeFontFamily` once via `remember(context)`, derives `typography` via `remember(activeFontFamily)`, provides both to `MaterialTheme` + `LocalTextStyle`. **Commit `0a09460` made this memoized — do not un‑memoize.** |
| `Color.kt` | Palette. Not language‑specific. |
| `app/src/main/res/font/cairo.ttf` | Bundled Cairo variable font (SIL OFL, ~600 KB). |

Tested: `app/src/test/java/com/example/mallar/ui/theme/TypographyTest.kt`.

---

## 4. Language screen — `com.example.mallar.ui.language`

- `LanguageScreen.kt`:
  - `@Composable fun LanguageScreen(onBackClick, modifier)` — route wrapper; resolves current
    via `AppLanguagePlatform.currentLanguage(context)`, `onSelect = { AppLanguagePlatform.apply(it) }`.
  - `@Composable fun LanguageScreenContent(languages, current, onSelect, onBackClick, modifier)`
    — stateless, testable. One row per language, autonym text, check icon on the active row,
    `Modifier.semantics { selected = isSelected }`.
  - ⚠ Row `contentDescription = "Back"` is still a hardcoded literal — QA finding, see
    `Known_Gaps_And_Followups.md`.
- Route: `"language"` in `MainActivity`, reached from Profile → Preferences → Language.
- Tested: `app/src/androidTest/java/com/example/mallar/ui/language/LanguageScreenTest.kt`.

---

## 5. Resource catalogs

- `app/src/main/res/values/strings.xml` — ~349 `<string>` + 3 `<plurals>`. Source of truth.
- `app/src/main/res/values-ar/strings.xml` — key‑for‑key parity, Egyptian colloquial.
- Sectioned by screen with `<!-- … -->` headers; a `<!-- Ticket 08 … -->` block groups the
  browsing‑surface keys; a `<!-- Ticket 12: Navigation guidance -->` block in `values-ar`.

---

## 6. Screens wired to `stringResource` (the externalization tickets)

| Area | Files | Ticket |
|---|---|---|
| Auth / onboarding / permissions / splash | `ui/splash/SplashScreen.kt`, `ui/auth/WelcomeScreen.kt`, `PhoneAuthScreen.kt`, `OtpVerifyScreen.kt`, `SignInScreen.kt`, `SignUpScreen.kt`, `ui/localization/PermissionsScreen.kt` | 07 |
| Mall picker | `ui/mall/MallSelectionScreen.kt` (already externalized pre‑effort; `MallSelectionScreenTest` asserts English) | 07 |
| Home / shared / offers / vouchers / store detail / destination | `ui/home/Homescreen.kt`, `HomeSharedComponents.kt`, `OffersScreen.kt`, `StoreDetailScreen.kt`, `VoucherDetailsScreen.kt`, `ui/home/DestinationSelectionScreen.kt`, `DestinationSearchScreen.kt`, `DestinationCategoryScreen.kt` | 08 |
| Profile / saved places | `ui/profile/ProfileScreen.kt`, `SavedPlacesScreen.kt` | 09 |
| Parking | `ui/parking/ParkingHomeScreen.kt`, `ParkingCameraScreen.kt`, `ParkingScanResultScreen.kt`, `ParkingMapScreen.kt` | 10 |
| Assistant chrome only | `ui/…/ChatBottomSheet.kt`, `voice/VoiceAssistantOverlay.kt` | 11 |
| Navigation guidance (strings‑only, extra scrutiny) | `ui/navigation/UnifiedNavigationScreen.kt`, `ui/navigation/StaticMapScreen.kt` | 12 |
| Voucher placeholder **content** (post‑QA fix) | `data/Voucher.kt` (`@StringRes` fields), `ui/home/OffersScreen.kt`, `ui/home/VoucherDetailsScreen.kt` | post‑QA (`b301c51`) |

`data/Voucher.kt`: `discountTitleRes / descriptionRes / expirationDateRes / termsRes` are
`@StringRes Int`. `storeBrand` / `logoAssetPath` / `category` / `floor` are raw. The
`OffersScreen` search filter resolves titles via `context.getString` in a keyed
`remember(voucherTitles) { derivedStateOf { … } }` — because that block is not `@Composable`.

Deleted as cleanup: `ui/profile/SettingsScreen.kt` (dead). Placeholder resources
`user_first_name` / `user_last_name` / `joined_time` (+ ar twins) removed.

---

## 7. Deliberate exclusions (do NOT externalize / touch)

| Thing | Why | Where recorded |
|---|---|---|
| `ui/localization/LogoScanScreen.kt`, `LocalizationConfirmScreen.kt` | Frozen by AR roadmap. Left English. | Architecture §8, Decisions |
| `com.example.mallar.ar` package | AR scene renders no text; zero changes. | Architecture §8 |
| `object NavigationState` (in `LogoScanScreen.kt`), `NavigationSessionManager`, pathfinding, `DriftMonitor` | Structure frozen. Fields are read, never changed, by nav‑guidance strings. | Architecture §8 |
| `ChatSystem.kt` (`detectCategory`, keyword dicts, generated replies), `SmartResponseEngine`, `NavigationVoiceController`, `LocalIntentParser`, STT/TTS | Assistant engine out of scope. Only chrome localized. | Decisions |
| `NavigationState.preferArabicVoice` stale‑locale bug | Known voice defect, recorded not fixed. | Decisions |
| Phone auth `+20` / E.164 | Egypt‑only product constraint. | Decisions |
| Debug `"BUG"` toggle labels | Pure‑debug. | Conventions §1 |
