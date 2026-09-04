# 07: Externalize — auth, onboarding, permissions, mall-picker, splash

**What to build:** The launch-and-entry surface is fully translated and RTL-correct in Arabic:
the splash screen, the mall picker, the welcome/onboarding screen, sign-in, sign-up, OTP/verify,
and the permissions screen. Switching to Arabic on any of these shows no English text and no
broken mirroring.

**Blocked by:** 04, 05.

**Status:** ready-for-agent

- [ ] Every user-facing literal on these screens is a resource with an Egyptian-Arabic
      translation: `Text`, button/label/hint/placeholder text, dialog and toast copy,
      error/empty states, and `contentDescription`s. Pure-debug strings excluded.
- [ ] `SplashScreen` text ("Navigate", "through anywhere", "INDOOR NAVIGATION", etc.)
      externalized; the entrance animation and any corner marks are RTL-correct.
- [ ] Numbers in this surface (OTP length, phone digits, country code) render Western digits via
      the formatter and are passed as `%1$s`; phone + OTP fields stay LTR; the `+20` prefix
      shows as `+20`.
- [ ] Mixed Arabic + Latin runs (phone number with prefix) use the bidi helper.
- [ ] Dead `ui/profile/SettingsScreen.kt` deleted.
- [ ] These routes pass a pseudolocale check (`en_XA`: no unaccented text; expanded text not
      clipped) and an RTL check (`ar_XB` + real `ar-EG`, light and dark): mirrored chrome,
      correct alignment, logical slide direction in the sign-in/sign-up flow.
- [ ] `./gradlew compileDebugKotlin`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin`,
      `lintDebug` green.
