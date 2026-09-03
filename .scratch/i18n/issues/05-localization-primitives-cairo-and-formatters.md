# 05: Localization primitives — Cairo font, Western-digit formatter, bidi helper

**What to build:** The shared building blocks the externalization batches depend on: Arabic text
renders in a bundled Arabic typeface (Cairo) while Latin text is unchanged; there is one helper
that formats any number to Western digits; and there is one helper that bidi-isolates a value
embedded in a right-to-left string.

**Blocked by:** 02 (the locale-driven `FontFamily` needs to know the active App language).

**Status:** ready-for-agent

- [ ] Cairo (SIL OFL) font files bundled; license/attribution recorded where the repo keeps
      third-party notices.
- [ ] Locale-driven typography: when the active App language is Arabic the `FontFamily` resolves
      to Cairo (regular / medium / bold mapped to Cairo weights); for every other App language
      the `FontFamily` is exactly today's `FontFamily.SansSerif`. Cairo is **not** a blanket
      fallback that could alter Latin rendering.
- [ ] A `WesternDigits` (or similarly named) formatter: given an `Int` / `Long` / `Double` /
      percentage, returns a string using digits `0-9` regardless of the ambient locale
      (fixed `Locale.ROOT` / `Locale.US` numeric formatting). Unit-tested, including that it
      emits `"2"` not `"٢"` when the default locale is Arabic.
- [ ] A bidi-isolation helper that wraps an interpolated value (brand name, number, code) for
      safe display inside an Arabic string.
- [ ] Manual acceptance (recorded): the already-Arabic screens now render in Cairo; a
      side-by-side of an English screen before/after shows no change.
- [ ] `./gradlew compileDebugKotlin`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin`,
      `lintDebug` green.
