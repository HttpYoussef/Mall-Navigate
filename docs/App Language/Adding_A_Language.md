# Adding a Language

How to ship a new UI language (Spanish, French, or anything else). The framework was built so
this is small and mechanical.

> **Why es/fr aren't already shipped.** Dropping a `values-es/` file makes Android
> auto‑select it on any device whose locale is Spanish — *even for strings you haven't
> translated yet*, which then fall back to English, giving a half‑Spanish app in the wild with
> no way to gate it. So a language ships **only when its catalog is complete**. `es` and `fr`
> are modelled in `AppLanguage` and the framework is proven by Arabic; finishing one is the
> steps below.

---

## Step 0 — decide register and get the translations

- Arabic is Egyptian colloquial. Spanish / French were speced as **formal register** — confirm
  with the product owner.
- You need a translation for **every** key in `app/src/main/res/values/strings.xml`
  (~349 strings + 3 plurals). Get the full set before touching code — a partial catalog can't
  ship (see the box above) and can't pass lint if you add the `<locale>` line early.
- Plurals: know your language's CLDR plural categories. Spanish = `one` + `other`. French =
  `one` + `many` + `other` (the `many` category is used for large numbers). Arabic uses all
  six. Provide exactly the categories your language needs.

---

## Step 1 — the catalog

Create `app/src/main/res/values-<lang>/strings.xml` (e.g. `values-es/`, `values-fr/`).

- Copy `values/strings.xml` verbatim as the skeleton — same root tag (keep the
  `xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2"` declaration), same key order, same
  section comments.
- Translate every `<string>` value and every `<plurals>` item.
- Keep `app_name` / `app_wordmark` = `MallAR` (untranslated, every language).
- Follow `Localization_Conventions.md`:
  - `%1$s` placeholders stay `%1$s` — never renumber, never convert to `%d`.
  - Keep every `<xliff:g>` span intact around brands / numbers / codes.
  - Western digits only, in text and in `expiration`‑style copy.
  - Escape `'` as `\'`, `&` as `&amp;`.
  - Plural `<item>` values needing a leading/trailing space must be `"…"`‑quoted (aapt strips
    otherwise).

---

## Step 2 — register the language (3 code edits)

| # | File | Change |
|---|---|---|
| 1 | `app/src/main/res/xml/locale_config.xml` | Add `<locale android:name="<lang>"/>`. |
| 2 | `app/src/main/java/com/example/mallar/data/AppLanguageResolver.kt` | Add the enum value to `supported`: `listOf(ENGLISH, ARABIC, SPANISH)`. (`AppLanguage.SPANISH` / `.FRENCH` already exist. A brand‑new language also needs a new enum constant with its `code` and **autonym** — its name in its own language, e.g. `"Español"`, `"Français"`, `"Deutsch"`.) |
| 3 | — | That's it. The Language screen renders from `AppLanguageResolver.supported`; `MallARTheme` and every screen already work off `AppLanguage`. |

No other code changes. The Language screen row, the resolution logic, migration, persistence,
and the system per‑app‑language picker all pick it up automatically.

---

## Step 3 — typography

- **Latin‑script language (es, fr, de, …):** nothing to do. `fontFamilyFor()` returns
  `FontFamily.SansSerif` for anything that isn't Arabic. Only check that expansion (Step 5)
  doesn't clip.
- **A new non‑Latin / complex‑script language (e.g. another RTL language, or CJK):** you must
  bundle a font and extend `ui/theme/Type.kt`:
  - Add the `.ttf` under `app/src/main/res/font/`.
  - Add a `FontFamily` like `CairoFontFamily`.
  - Extend `fontFamilyFor(language)` with a branch for it.
  - If it's RTL: the platform handles layout mirroring from the locale; re‑do the RTL parts
    of `Testing_Localization.md`. Follow the same "physical space is never mirrored" rule.

---

## Step 4 — RTL (only for a new RTL language)

Arabic already exercised every RTL path. For another RTL language (Hebrew, Farsi, Urdu):
- Layout mirroring is automatic from the locale — no code.
- Re‑verify the manual‑fix spots listed in `Localization_Conventions.md` §8: phone/OTP fields
  forced LTR, `nativeCanvas` label alignment on the parking map + static route map, `LazyRow`
  carousels, sign‑in/up slide direction.
- Do **not** touch map/camera/AR geometry.

For an LTR language there is no RTL work at all.

---

## Step 5 — test

Run the full `Testing_Localization.md` pass for the new language:
- Real device set to the new locale — walk the route inventory, no English islands.
- `en_XA` pseudolocale is still your expansion check (it pads ~30–50%); confirm no clipping —
  French and German in particular run long.
- API 24 / 32 / 34: first‑launch resolution, in‑app switch, system switch, force‑stop
  persistence.
- Dark mode + the new language on the dense screens.

---

## Step 6 — gates + commit

```
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin lintDebug --console=plain -q
```

`lintDebug` will fail if the new catalog is missing **any** key that `values/` has — that is
the completeness gate doing its job. Fix the gaps, don't suppress it.

Add a unit‑test case to `AppLanguageResolverTest.kt`: the new language is in `supported` in
the right position; `effective()` picks it for an explicit tag and for a device locale;
an unsupported device locale still falls to English.

Commit both the catalog and the 2–3 registration edits together.

---

## If you must ship an *interim* untranslated locale

Speced but explicitly **not** part of the original effort. If product ever wants a locale
visible before its catalog is done, the agreed mechanism is
`tools:ignore="MissingTranslation"` on that catalog file's root `<resources>` tag, verified
with `lintDebug`. Treat it as tech debt with a tracking issue — a user on that locale sees
English fallbacks.
