# On-device test plan — Arabic localization (tickets 01 + 13)

Covers the two remaining tickets. Everything here needs an emulator or phone; none of it
can run in CI. Work top to bottom. When something is wrong, note the **screen name + what
you saw** and bring it back — clipping / mirroring / alignment / stale strings / missing
translations are in-scope fixes; AR-session breakage is a separate issue.

App id: `com.example.mallar`  ·  launcher: `com.example.mallar/.MainActivity`
minSdk 24 · targetSdk 36 · shipped languages: English + Arabic (`res/xml/locale_config.xml`)

---

## 0. Get the branch

```bash
git fetch origin
git checkout feat/app-localization
```

Or check out PR #1 (https://github.com/HttpYoussef/Mall-Navigate/pull/1).

---

## 1. Emulators to create

Android Studio → **Device Manager → Create Virtual Device** → Pixel 6 (or any phone), then
one AVD per row:

| AVD | System image | Why |
|-----|--------------|-----|
| API 24 | Android 7.0 (Nougat) | lowest supported; Cairo is a variable font, API 24–25 only render the default weight |
| API 32 | Android 12L | last version **without** the system per-app-language picker → exercises the `AppLocalesMetadataHolderService` persistence path |
| API 34 | Android 14 (Google APIs) | has the per-app-language picker in Settings |

Notes:
- ARCore does not run on a stock emulator. This branch touches **no** AR code and the AR /
  logo-scan screens are out of QA scope, so emulators are fine for everything except the
  one "observe AR session during language switch" line in §5 — that needs a physical
  ARCore phone.
- Turn on pseudolocales once per AVD: **Settings → System → Developer options →
  Languages → enable** — then *English (XA)* and *العربية (XB)* appear in the language list.

---

## 2. Build & install

Emulator running, then from repo root:

```bash
./gradlew :app:installDebug        # Git Bash / PowerShell: .\gradlew.bat :app:installDebug
```

Or hit **Run ▶** in Android Studio. To target a specific device when several are attached:

```bash
adb devices                        # list ids
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 3. The three ways to change language (know all three)

| Method | Where | What should happen |
|--------|-------|--------------------|
| **In-app** | Profile → Preferences → **Language** → العربية | screen redraws **in place** in Arabic + RTL. **No splash replay.** Mall stays selected. Back stack intact. This is an `AppCompatDelegate.setApplicationLocales` → `recreate()`. |
| **System per-app** (API 33+) | Settings → Apps → MallAR → Language → العربية | same as above on next resume |
| **System device locale** | Settings → System → Languages → add العربية → drag to top | on a **fresh install with no explicit choice**, app follows this. If you already picked a language in-app, that choice wins. |

Reset to "no explicit choice" between migration tests:

```bash
adb shell pm clear com.example.mallar
```

---

## 4. adb cheat-sheet

```bash
# force-stop (keeps data) then relaunch
adb shell am force-stop com.example.mallar
adb shell monkey -p com.example.mallar -c android.intent.category.LAUNCHER 1

# kill the process (simulates process death, keeps data)
adb shell am kill com.example.mallar

# wipe all app data (fresh-install state)
adb shell pm clear com.example.mallar

# what locale did AppCompat persist? (API < 33 path)
adb shell run-as com.example.mallar cat \
  files/androidx.appcompat.app.AppCompatDelegate.application_locales_record_file ; echo

# is the holder service enabled? (autoStoreLocales flips this on at runtime pre-33)
adb shell dumpsys package com.example.mallar | grep -i AppLocalesMetadataHolderService

# inspect the legacy pref + migration flag
adb shell run-as com.example.mallar cat shared_prefs/mallar_app_prefs.xml ; echo

# launch straight into a pseudolocale for one run
adb shell am start -n com.example.mallar/.MainActivity
# (then switch via Settings; there is no reliable per-launch locale extra)

# screenshot / screen record for bug reports
adb exec-out screencap -p > bug.png
adb shell screenrecord /sdcard/bug.mp4    # Ctrl+C to stop, then: adb pull /sdcard/bug.mp4
```

---

## 5. Ticket 01 — AppCompat foundation (retroactive validation)

Ticket 02 is already implemented; this pass confirms it holds up on real OS versions.
**Run every row on API 24, 32, and 34.**

| # | Test | Steps | Pass |
|---|------|-------|------|
| 1.1 | Locale persists across force-stop | set app to Arabic (in-app) → `am force-stop` → relaunch | still Arabic, still RTL |
| 1.2 | Locale persists across reboot | Arabic → `adb reboot` → open app | still Arabic |
| 1.3 | In-app switch = in-place recreate | be on Home/Profile → switch to العربية | same screen area redraws; **no splash**; mall still selected (no bounce to mall-picker); press back — stack is intact |
| 1.4 | Process death still redirects | navigate deep → `am kill` → relaunch | lands on **mall selection** (expected cold-start redirect), not a broken Home |
| 1.5 | Holder service enabled pre-33 | API 24/32: after first Arabic switch, run the `dumpsys` grep | service shows `enabled=true` (or the record file from the `run-as cat` exists and contains `ar`) |
| 1.6 | Migration — never set | `pm clear` → **device** language English → open | app in English, follows device |
| 1.7 | Migration — never set, Arabic device | `pm clear` → device language العربية first → open | app in Arabic from first frame, **no English flash** before it swaps |
| 1.8 | Migration — legacy `"ar"` | install `main` build, open, pick Arabic in old UI → `git checkout feat/app-localization && ./gradlew :app:installDebug` (installs over, keeps data) → open | Arabic, and `mallar_app_prefs.xml` now has `language_migrated=true` |
| 1.9 | Migration — legacy `"en"` | same but pick English in old build | English (honours the past explicit choice even if device is Arabic) |
| 1.10 | Migration runs once | after 1.8, switch to English in-app → force-stop → relaunch | stays English (migration doesn't re-fire and re-apply `"ar"`) |
| 1.11 | AR session vs language switch (**physical ARCore phone only**, observe) | start AR navigation → pull down notification shade → change MallAR's per-app language | just record: does the AR camera/session survive the `recreate()`, or does it need re-scan? Not a bug to fix — it's a known risk to document. |
| 1.12 | Status-bar icons | dark mode + light mode, both languages | status-bar icon contrast is correct (Theme parent is now `Theme.AppCompat.DayNight.NoActionBar`) |

Write findings into `.scratch/i18n/01-spike-findings.md`: appcompat version, the manifest
service snippet, per-API persistence result, migration behaviour, recreate/route/splash
behaviour, the AR observation. If all rows pass, that doc is two paragraphs.

---

## 6. Ticket 13 — RTL + localization QA sweep

### Pass A — real `ar-EG`, light mode, API 34

Set device language to العربية (Egypt). Walk **every** screen and confirm no English text
leaked through and the layout mirrored:

- Splash → mall picker → welcome → sign in / sign up / **OTP** → permissions
- Home (greeting, offers **carousel**, favourites, parking hero card) → offers list →
  voucher details → store detail
- Destination selection / search / category
- Profile → **Language screen** → Preferences → Saved places
- Parking: home → camera → scan result → **map**
- Navigation: HUD, **Map/AR toggle**, floor-transition sheet, "Get Oriented" overlay,
  static route map
- Chat sheet + voice overlay **chrome** (buttons, labels, status text, mic
  contentDescription — the assistant's typed replies staying English is expected/accepted)

**Excluded — do not flag:** `LogoScanScreen`, `LocalizationConfirmScreen` (frozen by AR
roadmap).

Specific watch-items (these are the known-risk spots):

| Area | What to check |
|------|---------------|
| Back arrows / chevrons | point **right** in RTL; rows flip; section headers right-aligned |
| `LazyRow` carousels (home offers, destination categories, offers) | first item on the **right**, scroll starts from the right |
| Phone field + OTP boxes + numeric keypads | stay **left-to-right**; digits are 0–9 not ٠–٩; `+20` shows as `+20` |
| Parking map canvas labels | "SLOW" / "EXIT" / "أنت هنا" readable, not clipped off the lane; **map image itself not mirrored** |
| Static route map canvas | pin labels + "خريطة المسار · الطابق ١" readable; start/end markers not swapped in space |
| All numbers | distances, "٥ دقائق مشي" → Western digits (`5`), "128 spaces", floor numbers, scan-result timestamp (`yyyy-MM-dd HH:mm`, ASCII) |
| Mixed Arabic + Latin | brand names / spot codes inside Arabic sentences read correctly (bidi isolation) — no jumbled punctuation |
| Sign-in / sign-up | the slide transition between phases goes the right direction for RTL |

### Pass B — pseudolocales (API 34)

| Locale | Set to | Looking for |
|--------|--------|-------------|
| `en_XA` | English (XA) | every string should render like `[Ĥéļļö Wörld one two]`. **Plain, un-accented English text = a missed `stringResource`** — note the screen. Also: text is ~30–50% longer here, so check nothing clips or truncates. |
| `ar_XB` | العربية (XB) | hard RTL / bidi stress. Anything still laid out LTR is a mirroring bug. |

### Pass C — dark mode + Arabic

Re-check the visually dense screens only: Home, Navigation HUD, Parking map, Voucher
details, Store detail — in dark mode + Arabic.

### Pass D — API matrix

Repeat just the **language-resolution** checks (first-launch follow-device, in-app switch,
system switch, force-stop persistence) on **API 24** and **API 32**. On API 24–25 also
eyeball the **Cairo font** on headings — variable-font weights aren't available, so bold
headings look lighter than on API 34. Acceptable as long as it's legible and not falling
back to a system serif.

### Exit criteria

- No English islands in `ar-EG` (excluding the two frozen screens)
- No un-accented strings in `en_XA`
- Chrome mirrors correctly; maps/camera geometry do **not**
- Phone/OTP LTR + Western digits everywhere
- No clipped/truncated text in `en_XA`
- Locale survives force-stop on all three API levels
- `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin lintDebug` still green

File anything out of scope (e.g. AR session corruption on locale change) as its own issue;
fix in-scope defects and re-run the gates before marking ticket 13 done and flipping PR #1
out of draft.
