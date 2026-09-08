# Testing Localization

How to verify a language, an RTL change, or a string‑externalization change. There is **no
screenshot / instrumented‑locale test infrastructure** (deliberate — see Decisions). Testing
is: automated gates + unit/UI tests + a manual on‑device pass.

A longer, step‑by‑step device script (emulator setup, `adb` cheat‑sheet, the full route walk)
is at `.scratch/i18n/device-test-plan.md`. This file is the maintained summary of *what* to
check and *why*.

---

## 1. Automated — every change

```
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin lintDebug --console=plain -q
```

- `lintDebug` enforces `MissingTranslation` (abortOnError). A key in one catalog but not the
  other fails the build. This is the translation‑drift gate — never suppress it.
- Unit tests (`app/src/test`): `AppLanguageResolverTest`, `WesternDigitsTest`,
  `BidiFormattingTest`, `TimestampsTest`, `TypographyTest`.
- UI test (`app/src/androidTest`): `LanguageScreenTest`.
- A one‑time manual check (do it if you touch the lint config): delete a `values-ar` key,
  confirm `lintDebug` fails, restore it.

### What a good test is here

Assert **observable behaviour**, not implementation. For the resolver: given explicit tags +
device locales + the supported set, `effective()` / `migrationDecision()` return the right
outcome. For the vocab helpers: given a raw category key or a floor int, the right
`@StringRes` / Western‑digit arg comes back. Tests do **not** assert which platform API was
called or how persistence is stored.

---

## 2. Manual on‑device — the acceptance pass

Needs a real device or emulator. Some checks need **API 24, 32, and 34** (before/after the
system per‑app‑language picker, and the oldest supported level).

### 2a. Language resolution & persistence (run on 24 / 32 / 34)

| Check | How | Pass |
|---|---|---|
| First launch follows device | `adb shell pm clear …`; set device language; launch | app opens in that language if supported, else English |
| In‑app switch = in‑place recreate | Profile → Preferences → Language → العربية | same screen redraws in Arabic; **no splash replay**; mall still selected (no bounce to picker); back stack intact |
| Persists across force‑stop | set Arabic → `adb shell am force-stop com.example.mallar` → relaunch | still Arabic |
| Process death still redirects | go deep → `adb shell am kill …` → relaunch | lands on mall picker (intended cold‑start redirect), not a broken Home |
| System per‑app switch (33+) | Settings → Apps → MallAR → Language | applies on resume |
| Legacy migration | install an old build, pick a language, install this build over it (keeps data) | `"ar"`→Arabic, `"en"`→English even on an Arabic device, absent→device; **no English flash** before Arabic on the first frame |

### 2b. Real `ar-EG`, light mode, API 34 — the route walk

Walk **every** `NavHost` route + overlay and confirm no English leaked and layout mirrored:

splash → mall picker → welcome → sign in / sign up / OTP → permissions → home (greeting,
offers carousel, favourites, parking hero) → offers list → voucher details → store detail →
destination selection / search / category → profile → **Language screen** → preferences →
saved places → parking home / camera / scan result / map → navigation HUD + Map/AR toggle +
floor‑transition sheet + "Get Oriented" overlay → static route map → chat sheet + voice
overlay chrome.

**Excluded — do not flag:** `LogoScanScreen`, `LocalizationConfirmScreen` (frozen). English
typed assistant replies (engine out of scope).

### 2c. High‑risk spots (the ones that actually break)

| Spot | Check |
|---|---|
| Back arrows / chevrons | point **right** under RTL; rows flip; headers right‑aligned |
| `LazyRow` carousels (home offers, destination categories, offers) | first card on the **right**, scroll starts from the right |
| Phone field + OTP boxes + numeric keypads | stay **LTR**; digits 0‑9 not ٠‑٩; `+20` shows as `+20` |
| Parking‑map canvas labels ("SLOW"/"EXIT"/"أنت هنا") | readable, not clipped off the lane; **map image itself not mirrored** |
| Static route‑map canvas | pin labels + "خريطة المسار · الطابق ١" readable; start/end markers not swapped in space |
| All numbers | distances, "N min walk", "128 spaces", floor numbers, scan timestamp → Western digits |
| Mixed Arabic + Latin | brand names / spot codes inside Arabic sentences read correctly (bidi isolation), punctuation not jumbled |
| Sign‑in / sign‑up | slide transition between phases goes the right direction for RTL |
| Voucher screen | title, description, expiry line, terms all Arabic; offer search still matches by localized title |
| Cairo on API 24–25 | headings render Cairo (variable weights unavailable on those APIs — lighter is acceptable, a system serif fallback is not) |

### 2d. Pseudolocales

Enable in **Settings → System → Developer options** (they are *not* currently forced on in
`build.gradle.kts` — see `Known_Gaps_And_Followups.md`; force per‑run with `adb` or add them
as system languages).

| Locale | Looking for |
|---|---|
| `en_XA` (`English (XA)`) | every string renders like `[Ĥéļļö one two]`. **Plain un‑accented English = a missed `stringResource`.** Also: text is ~30–50% longer — nothing clips or truncates. |
| `ar_XB` (`العربية (XB)`) | hard RTL / bidi stress. Anything still laid out LTR is a mirroring bug. |

### 2e. Dark mode + Arabic

Re‑check the dense screens only: home, navigation HUD, parking map, voucher details, store
detail.

### 2f. Camera / AR + language switch (physical ARCore device, observe only)

Start AR navigation → change the app's per‑app language from the notification shade → **record**
whether the AR session survives the `recreate()` or needs a re‑scan. This is a known risk, not
a bug to fix here — file it separately if it breaks.

---

## 3. Exit criteria

- Gates green.
- No English islands in `ar-EG` (excluding the two frozen screens).
- No un‑accented strings in `en_XA`; no clipped text.
- Chrome mirrors; maps / camera / AR geometry do **not**.
- Phone/OTP LTR + Western digits everywhere.
- Locale survives force‑stop on API 24, 32, 34.
