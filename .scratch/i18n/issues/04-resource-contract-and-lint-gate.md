# 04: Resource contract and MissingTranslation lint gate

**What to build:** The string-resource conventions every later batch must follow are documented,
the existing Arabic file is brought to full parity with the base file, and the build now fails
if any Arabic translation is missing. After this ticket, no externalization batch can land
without shipping its Arabic strings.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] A short conventions doc (e.g. `.scratch/i18n/resource-contract.md`): key-naming scheme;
      `%1$s`-only interpolation (never `%d` / `%.0f` — they render Arabic-Indic digits under
      `values-ar`); `<plurals>` for grammar-varying counts; `xliff:g` markup around brands,
      numbers, units, codes; `<!-- -->` translator comments where context is needed;
      English-only strings that must not appear in `values-ar`.
- [ ] The ~11 keys currently missing from `values-ar` are added (mall-picker title / subtitle /
      "coming soon" / retry, `store_inside_mall`, and any others that exist in `values/` but not
      `values-ar`), in Egyptian colloquial Arabic.
- [ ] Unused placeholder resources removed from both `values/` and `values-ar/`:
      `user_first_name`, `user_last_name`, `joined_time`.
- [ ] `lint { error += "MissingTranslation" }` (or equivalent `lintOptions`) enabled in the app
      Gradle config.
- [ ] One-time manual validation recorded: temporarily remove a `values-ar` key → `./gradlew
      lintDebug` fails → restore it → passes.
- [ ] `./gradlew lintDebug` passes on the completed `values-ar`; existing gates
      (`compileDebugKotlin`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin`) green.
