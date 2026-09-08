# Localization Conventions (the rules)

Follow these exactly when adding or editing any user‑facing text, number, or layout. The
build gate (`lintDebug` → `MissingTranslation`) catches parity mistakes; the rest is on you.

Canonical short form of the resource contract also lives at
`.scratch/i18n/resource-contract.md`. This file supersedes it where they differ.

---

## 1. Every user‑facing string is a resource

`Text`, labels, placeholders / hints, dialog & button text, error / empty‑state copy, toasts,
and **`contentDescription`s** all become `stringResource(...)` / `context.getString(...)`.

**Do not externalize:** pure‑debug strings (`"BUG"` toggle labels, log text, test
assertions). Leave those hardcoded.

**Never externalize** (frozen — see Architecture §8): `LogoScanScreen.kt`,
`LocalizationConfirmScreen.kt`, anything in `com.example.mallar.ar`, assistant‑engine
generated text.

---

## 2. Two catalogs, always in parity

- `app/src/main/res/values/strings.xml` — **the source of truth.** Must be complete.
- `app/src/main/res/values-ar/strings.xml` — must match **key‑for‑key**. A missing key either
  way fails `./gradlew lintDebug`.
- Add the key to **both files in the same commit**, in the matching section.
- Do **not** create `values-es/` or `values-fr/` (see `Adding_A_Language.md` for why, and for
  how to do it properly when the time comes).

---

## 3. Key naming

- `lower_snake_case`.
- Prefix by screen / area: `home_`, `parking_`, `nav_`, `auth_`, `otp_`, `mall_`, `voucher_`,
  `dsel_` (destination selection), `profile_`, `saved_`, `permissions_`, `splash_`, `chat_`,
  `voice_`, `store_`, `offers_`.
- Shared templates that several screens reuse get a generic name: `store_category_floor`,
  `distance_meters`, `floor_label`, `category_all`, `store_fallback`.

---

## 4. Interpolation — `%1$s` only, never `%d` / `%.0f`

`stringResource(id, arg)` formats numeric placeholders **in the active resource locale**.
Under `values-ar` a `%d` renders Arabic‑Indic digits (٠‑٩). MallAR shows Western digits (0‑9)
in every language.

Rule: **every placeholder is `%1$s` / `%2$s` …**, and every number is pre‑formatted to a
Western‑digit `String` with `WesternDigits` (below) before it goes into the placeholder.

```xml
<!-- WRONG -->  <string name="x">%1$d spaces</string>
<!-- RIGHT -->  <string name="x"><xliff:g id="count" example="128">%1$s</xliff:g> spaces</string>
```
```kotlin
stringResource(R.string.x, WesternDigits.format(count))
```

---

## 5. Numbers, digits, dates — `com.example.mallar.data`

| Need | Use | Notes |
|---|---|---|
| Any integer / long shown in UI | `WesternDigits.format(Int)` / `format(Long)` | `Locale.US`‑fixed, always 0‑9. |
| Decimal | `WesternDigits.format(Double, decimals = N)` | period separator. |
| Percentage | `WesternDigits.percent(Int)` → `"45%"` | |
| Floor number | `floorDisplayLabel(floor: Int)` (`@Composable`) or `floorDisplayLabel(floor, context)` | → `Floor 2` / `الطابق 2`. Never build a floor string by hand. |
| App‑generated timestamp | `Timestamps.format(epochMillis: Long)` | fixed pattern `yyyy-MM-dd HH:mm`, no localized month names, Western digits in every language. |
| A number embedded in Arabic sentence text | wrap it: `WesternDigits.format(n).bidiIsolated()` | see §7. |

`values-ar` `country_code` is `+20` (Western). No Arabic‑Indic digits anywhere in `values-ar`.

---

## 6. Plurals

Use `<plurals>` for any count / duration string whose grammar varies by language.

- English needs `one` + `other`.
- Arabic needs **`zero` `one` `two` `few` `many` `other`** (all six).
- Resolve with `pluralStringResource(R.plurals.x, count, WesternDigits.format(count))`.
- **aapt strips leading / trailing whitespace** from `<item>` values. If the string must
  start or end with a space (e.g. `"  •  5 min walk"`), wrap the whole value in double quotes:
  `<item quantity="one">"  •  "<xliff:g id="c">%1$s</xliff:g>" min walk"</item>`.

Current plurals: `duration_minutes`, `dcat_store_count`, `nav_walk_minutes`.

---

## 7. Bidi isolation — `BidiFormatting`

When a Latin brand name, a number, a code, or a `%`‑value is interpolated **into Arabic
sentence text**, wrap it so it renders as its own directional run and does not scramble the
surrounding RTL:

```kotlin
stringResource(R.string.from_label, place.brand.bidiIsolated())
```

`String.bidiIsolated()` / `BidiFormatting.isolate(s)` wraps in FSI (U+2068) … PDI (U+2069).
Apply it to: interpolated brand / store names, parking spot codes, phone fragments, any
number sitting inside an Arabic string. Do **not** apply it to a whole standalone string that
is already all one direction.

---

## 8. RTL

The platform mirrors layout automatically once the locale is RTL. Rely on it:

- Use `Row`, `start` / `end` (not `left` / `right`) padding & alignment, and
  `Icons.AutoMirrored.Filled.*` (not `Icons.Default.ArrowBack` — deprecated and non‑mirroring).
- Only fix code that **forces** a direction. Known patterns already applied:
  - **Phone / OTP / numeric fields and keypads:** forced LTR via
    `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)` so digits
    and `+20` read correctly.
  - **`nativeCanvas.drawText` labels** (parking map, static route map): resolve the string
    **outside** the `Canvas` draw block (draw blocks are not `@Composable`), compute
    `val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl` once, then flip
    `Paint.Align` (`RIGHT` under RTL) and the label's dx offset sign. **The pin / marker
    geometry does not change.**
- **Never mirror** map geometry, the camera preview, AR world geometry, or pan/zoom gesture
  math. Physical space is stable regardless of `LayoutDirection`.
- `LazyRow` carousels (home offers, destination categories, offers list) mirror scroll
  direction automatically — verify, don't force.

---

## 9. Categories & floors — `com.example.mallar.data`

**Canonical categories** are the 5 raw keys the mall graph carries. Constants in
`StoreCategory`:
```
FASHION="Fashion"  JEWELLERY="Jewellery"  PERFUMES_COSMETICS="Perfumes& Cosmetics" (no space, sic)
DINING="Dining"  PHARMACY="Pharmacy"
```
- **Keep the raw key** everywhere it is used for filtering / matching / nav routes.
- **Display only** goes through the mapping:
  - `categoryDisplayRes(rawKey: String?): Int?` — raw key → `@StringRes`, or `null` if
    unknown. Normalizes whitespace; aliases `"Food"` → Dining.
  - `categoryDisplayLabel(rawKey: String?): String?` (`@Composable`) — resolved label or null.
- Category names that exist only in code, not in the data ("Entertainment", "Services",
  "Cafés", "Food & Dining") are **dropped from display surfaces**. Don't reintroduce them.
- Floor: always `floorDisplayLabel(...)`. Demo data (`Voucher`, `OfferItem`) holds
  `floor: Int`; the label is derived **at composition time**, never cached in
  `remember(id)` (would go stale after a locale `recreate()`).

---

## 10. `xliff:g` and comments

- Wrap non‑translatable spans — brands, numbers, units, codes — in
  `<xliff:g id="…" example="…">%1$s</xliff:g>`.
- The root `<resources>` tag must declare
  `xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2"` (it already does in both files).
- Put an `<!-- … -->` comment directly above any key whose meaning or grammatical context is
  ambiguous to a translator (e.g. a bare word that could be noun or verb).
- Brand wordmark: `app_name` / `app_wordmark` = `MallAR` in every language, with a
  `<!-- Do NOT translate -->` comment.

---

## 11. Arabic register

**Egyptian colloquial** (the malls are in Cairo). Match the tone of the existing `values-ar`
entries — conversational, not Modern Standard Arabic. New keys are an AI/human first pass; a
native review pass is a tracked follow‑up (`Known_Gaps_And_Followups.md`), not a blocker.

---

## 12. Escaping (Android XML)

- Apostrophe: `\'` (or wrap the value in `"…"`).
- `&` → `&amp;`, `<` → `&lt;`.
- `\n` for a newline, `\@` / `\?` if a value starts with `@` or `?`.
- A lone `%` is fine in a string with **no** format args (e.g. `"20% OFF"` / `"خصم 20%"`) —
  already proven in `home_offer_20_off`. If the string has `%1$s`, a literal percent must be
  `%%`.
