# 10: Externalize — parking

**What to build:** The parking flow is fully translated and RTL-correct in Arabic: the parking
home screen, the scan-result screen, and the parking map — including the labels drawn directly
on the map canvas. The "saved at" timestamp shows Western digits in every language.

**Blocked by:** 04, 05.

**Status:** ready-for-agent

- [ ] Every user-facing literal on the parking screens is a resource with an Egyptian-Arabic
      translation.
- [ ] `ParkingMapScreen` labels currently drawn via `nativeCanvas.drawText` at fixed pixel
      offsets ("SLOW", "EXIT", "You are here", etc.) draw translated strings with
      direction-aware positioning; the map's real-world orientation is unchanged.
- [ ] The parking timestamp (currently `DateFormat.format(...)` with the ambient locale) uses a
      fixed locale-neutral numeric pattern `yyyy-MM-dd HH:mm` — Western digits, no localized
      month names, in every language.
- [ ] Any parking-level / slot numbers render Western digits via the formatter, passed as `%1$s`.
- [ ] These routes pass the pseudolocale check (`en_XA`) and the RTL check (`ar_XB` + real
      `ar-EG`, light and dark), with particular attention to the canvas labels.
- [ ] `./gradlew compileDebugKotlin`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin`,
      `lintDebug` green.
