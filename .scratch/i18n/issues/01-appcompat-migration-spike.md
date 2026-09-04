# 01: AppCompat per-app-language migration spike

**What to build:** A throwaway spike branch that de-risks the whole effort by proving the
AppCompat per-app-language approach on real devices/emulators, plus a committed findings
document that pins down the exact configuration ticket 02 will implement. No production code
lands from this ticket — only the findings doc (e.g. under `.scratch/i18n/`).

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] On a throwaway branch: `MainActivity` switched to `AppCompatActivity`, `androidx.appcompat`
      added, an AppCompat `NoActionBar` XML window theme created, `Theme.App.Starting`'s
      `postSplashScreenTheme` repointed at it, `installSplashScreen()` still called before
      `super.onCreate()`.
- [ ] `AppLocalesMetadataHolderService` declared with `autoStoreLocales="true"`; confirm the
      selected app locale persists across a force-stop on **API 24, API 32, and API 33+**.
- [ ] Confirm `AppCompatDelegate.setApplicationLocales(...)` triggers an in-place `recreate()`
      with **no task teardown**, and that Navigation-Compose restores the current route rather
      than re-entering the `splash` destination.
- [ ] Confirm a locale `recreate()` with a live process keeps `MallSession.selected` non-null so
      the `MainActivity` mall-selection redirect does **not** fire; confirm process death still
      redirects to the picker.
- [ ] Exercise the one-time legacy migration for each seeded `mallar_app_prefs` `language`
      value: absent, `"ar"`, `"en"`, and an unrecognised value — record the resulting language
      and whether a first-launch `recreate()` flashes English before Arabic.
- [ ] Observe (do not fix) what happens when the system per-app language is changed during an
      active CameraX / AR SceneView session; record whether the session survives.
- [ ] Findings doc committed: exact `appcompat` version, manifest snippet, theme parent,
      persistence behaviour per API level, migration behaviour, `recreate()`/route/splash
      behaviour, and the camera/AR observation. Explicitly state whether the ticket-02 approach
      is confirmed or needs adjustment.
