# 13: RTL and localization QA sweep (acceptance)

**What to build:** A full-app verification pass in Arabic and under pseudolocales, across the
API matrix and both themes, that confirms the effort's goal is met — the whole interface (minus
the deliberately-excluded logo-scan flow) is translated, mirrored, and readable — and fixes any
clipping / mirroring / stale-string defects it turns up.

**Blocked by:** 03, 07, 08, 09, 10, 11, 12.

**Status:** ready-for-agent

- [ ] Walk the full route/overlay inventory from the spec in real `ar-EG`: every `NavHost`
      route, the chat sheet, the voice overlay, the parking/static-map canvases, the navigation
      HUD and floor sheet. Confirm no English islands (excluding the logo-scan and
      localization-confirm screens, which are out of scope).
- [ ] Repeat under `ar_XB` (mirroring stress) and `en_XA` (expansion / un-externalized-string
      stress). Confirm: mirrored chrome and alignment; correct bidi in mixed Arabic+Latin;
      phone/OTP fields LTR with Western digits; stable map orientation; no clipped or truncated
      text at expanded length; `LazyRow` scroll direction.
- [ ] Repeat the high-risk routes in dark mode + Arabic.
- [ ] Run on API 24, 32, and 33+: language resolution on first launch, in-app switch, system
      per-app-language switch, persistence across force-stop.
- [ ] On a physical device: change the system per-app language during an active scan / AR
      navigation session and record the outcome (observation only — a fix is out of scope).
- [ ] Fix defects found that are in scope (clipping, mirroring, alignment, stale strings after a
      locale change, missing translations). File anything out of scope (e.g. an AR-session
      corruption) as a separate issue.
- [ ] Final gates green: `compileDebugKotlin`, `testDebugUnitTest`,
      `compileDebugAndroidTestKotlin`, `lintDebug`.
