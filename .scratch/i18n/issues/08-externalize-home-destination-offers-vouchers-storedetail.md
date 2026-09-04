# 08: Externalize — home, destination, offers, vouchers, store detail

**What to build:** The browsing surface is fully translated and RTL-correct in Arabic: the home
screen and its shared components, destination selection, the offers list, voucher details, and
store detail. Category chips and floor references use the shared helpers, so they read
consistently and update when the language changes.

**Blocked by:** 05, 06.

**Status:** ready-for-agent

- [ ] Every user-facing literal on these screens is a resource with an Egyptian-Arabic
      translation: `Text`, labels, hints/placeholders, section headers, dialog/toast copy,
      error/empty states, `contentDescription`s. Pure-debug strings excluded.
- [ ] Category and floor labels on these screens come from the ticket-06 mapping/helper (not
      local literals); "Store · Inside Mall" and similar composed strings are resource templates
      fed localized parts.
- [ ] Any counts / distances / numeric values render Western digits via the formatter, passed as
      `%1$s`; `<plurals>` used where a count drives grammar (e.g. "N results").
- [ ] `LazyRow` carousels on home / destination / offers scroll in the correct direction under
      RTL; card and row alignment mirrored.
- [ ] These routes pass the pseudolocale check (`en_XA`) and the RTL check (`ar_XB` + real
      `ar-EG`, light and dark).
- [ ] `./gradlew compileDebugKotlin`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin`,
      `lintDebug` green.
