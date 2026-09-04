# 09: Externalize — profile, saved places, settings/preferences

**What to build:** The account surface is fully translated and RTL-correct in Arabic: the
profile screen, its Preferences section, and saved places. Switching to Arabic here shows no
English text.

**Blocked by:** 05, 06.

**Status:** ready-for-agent

- [ ] Every user-facing literal on these screens is a resource with an Egyptian-Arabic
      translation, including the currently-hardcoded parts of `ProfileScreen` that sit alongside
      the already-resource-backed rows.
- [ ] Saved-places entries ("<category> · Inside Mall", floor lines) use the ticket-06
      mapping/helper.
- [ ] The Preferences section (dark-mode row, the Language row/entry point from ticket 03) reads
      correctly in Arabic and RTL.
- [ ] `contentDescription`s on profile actions (edit, change/remove photo, sign out) localized.
- [ ] These routes pass the pseudolocale check (`en_XA`) and the RTL check (`ar_XB` + real
      `ar-EG`, light and dark).
- [ ] `./gradlew compileDebugKotlin`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin`,
      `lintDebug` green.
