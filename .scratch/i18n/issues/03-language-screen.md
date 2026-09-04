# 03: In-app Language screen

**What to build:** A visitor can open Settings → Preferences → Language and choose the app
language from a list showing each language in its own name. The choice applies immediately, in
place — the visitor stays where they were, keeps their selected mall, and the splash does not
replay. The old two-chip EN/عربي toggle is gone.

**Blocked by:** 02.

**Status:** ready-for-agent

- [ ] New Compose screen + `NavHost` route (e.g. `"language"`), reached from the Preferences
      section of `ProfileScreen`. The old inline `LanguageToggleRow` is removed.
- [ ] One row per `AppLanguageResolver.supported` entry, each showing the language's autonym
      (`English`, `العربية`; `Español` / `Français` appear only if/when enabled later). The
      active language is visibly marked.
- [ ] Selecting a row applies the language via `AppLanguagePlatform` / `setApplicationLocales`;
      the resulting `recreate()` re-renders the app in the new language.
- [ ] Back action from the Language screen returns to `ProfileScreen`.
- [ ] Lifecycle-gate acceptance: an ordinary locale `recreate()` with a live `MallSession` does
      **not** redirect to mall-selection, and no new code path allows Home/Profile/Language with
      `MallSession.selected == null`. The existing process-death redirect still works.
- [ ] Thin Compose UI test (`app/src/androidTest`, pattern of `MallSelectionScreenTest`): renders
      one row per supported language with its autonym; tapping a row invokes the selection
      callback with that language; the current language is marked.
- [ ] Manual acceptance (recorded): switch language while on Home and while deep in the app →
      same screen afterwards, mall not re-picked, no splash replay.
- [ ] `./gradlew compileDebugKotlin`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin` green.
