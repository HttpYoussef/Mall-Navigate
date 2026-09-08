# Known Gaps & Follow‑ups

Living document. Update it when an item closes or a new one appears. Check it before assuming
the subsystem is "done".

Status as of branch `feat/app-localization` (draft PR #1). Latest relevant commits:
`0a09460` (theme memoization), `b301c51` (voucher content), `15f5aef` (runbook).

---

## A. Blocking / needs a decision

### A1 — Pending merge of `main` into `feat/app-localization`

`feat/app-localization` was cut from `main` at `e4ad8b7`. Local `main` has since advanced with
**6 AR commits** the branch does not have, including:

```
64775a3 ar: disable ARCore plane-debug visualization      ← frame-rate relevant
d1f28bb ar: harden floor-plane selection / damped elevation
940f112 ar: remove unused RenderPoseSmoother
026864c ar: sync marker rotation with position
1d0ca78 ar: measure real ARCore yaw at accept-time
5f5c7c2 ar: diagnostic logging for transform/anchor churn
```

**Consequence:** the on‑device report "AR orientation phase is slower on the branch than on
`main`" is **confounded** — the branch is missing AR perf work, not (only) carrying a
localization regression. `MallARTheme` memoization (`0a09460`) was a real fix regardless, but
the comparison can't be trusted until the branch has those 6 commits.

**Action:** `git merge main` into `feat/app-localization` (a clean merge — verified once
locally, `UnifiedNavigationScreen.kt` auto‑merges), re‑run the 4 gates, then re‑test the AR
orientation phase in Arabic. Held pending the repo owner's go (they asked not to merge/push
until the feature is otherwise complete). `origin/main` is still at `e4ad8b7`; the 6 commits
are local‑only.

---

## B. Open QA items (ticket 13 sweep — in progress, USER on device)

### B1 — `LanguageScreen.kt` hardcoded `contentDescription = "Back"`

`app/src/main/java/com/example/mallar/ui/language/LanguageScreen.kt`, the top‑bar back button
in `LanguageScreenContent` — `contentDescription = "Back"` is a literal. Should be
`stringResource(R.string.back)` (the key exists). Small fix, do it in the next string pass.

### B2 — Debug pseudolocales not force‑enabled

The spec wanted debug builds to enable `en_XA` / `ar_XB` automatically
(`buildTypes.debug { isPseudoLocalesEnabled = true }` in `app/build.gradle.kts`). It is **not
set**. QA currently has to enable them via Developer Options or `adb`. Add it to make the
pseudolocale pass frictionless.

### B3 — Theme memoization (`0a09460`) needs on‑device confirmation

The fix is committed + pushed and gates‑green, but "orientation phase is back to `main`'s
smoothness in Arabic" has **not** been confirmed on a physical device (and can't be cleanly,
until A1 — the merge — lands). Confirm after the merge.

### B4 — Voucher content fix (`b301c51`) needs on‑device confirmation

Open Offers + a voucher detail in Arabic; confirm title / description / expiry line / terms
are all Arabic and the offer search still matches by localized title. Committed, gates‑green,
not yet device‑verified. **Not pushed** (held per owner instruction).

### B5 — Ticket 01 (AppCompat spike) never formally executed

Ticket 02 shipped the AppCompat config; the spike that was meant to *precede* it (API 24/32/34
persistence, `recreate()` route/splash behaviour, legacy‑migration matrix, camera/AR
observation, a committed findings doc) was not run as a discrete step. It is now **retroactive
validation** — fold it into the ticket‑13 device pass (`Testing_Localization.md` §2a) and
write a short findings note.

### B6 — Full ticket‑13 route walk incomplete

The device sweep (real `ar-EG` every route, `en_XA`/`ar_XB`, dark mode, API matrix) is
in progress, not signed off. `Testing_Localization.md` §2 is the checklist.
`MallSelectionScreen` is already externalized but `MallSelectionScreenTest` asserts the
**English** strings — expected, don't "fix" the test to Arabic.

---

## C. Deferred by design (own effort / ticket when picked up)

### C1 — Ship Spanish / French
Modelled, not shipped. `Adding_A_Language.md` is the runbook. Needs complete catalogs +
formal‑register sign‑off.

### C2 — Professional native Arabic review
Existing `values-ar` + all new keys (auth, home, parking, nav, voucher content) are an
AI/human first pass. A native Egyptian‑Arabic reviewer should sweep the catalog.

### C3 — Localize the logo‑scan screens
`LogoScanScreen.kt`, `LocalizationConfirmScreen.kt` — left English, frozen by the AR roadmap.
A tiny separately‑authorized ticket with AR‑owner sign‑off.

### C4 — Assistant replies in Arabic
The assistant **engine** is out of scope; an Arabic user sees English typed replies. A future
effort if product wants it — touches `ChatSystem` / `SmartResponseEngine` / STT‑TTS.

### C5 — Language change during an active camera/AR session
`recreate()` may drop the AR session. The spike only *observes*. A safe teardown/restore is a
future effort — do not attempt it as part of a language task.

### C6 — `NavigationState.preferArabicVoice` stale‑locale capture
Known voice defect, recorded not fixed (`object NavigationState` structure is frozen).

### C7 — First ADR for the AppCompat migration
`docs/adr/0001-appcompat-per-app-language.md` — none exist yet. Decision D1 in
`Decisions_And_Rationale.md` is the content.

---

## D. Minor / cleanup

- `LanguageScreen` route wrapper calls `AppLanguagePlatform.currentLanguage(context)`
  unremembered. Harmless (that screen isn't in a hot recomposition loop) but inconsistent with
  the `MallARTheme` fix — wrap in `remember(context)` if you're in the file anyway.
- New `values-ar` plural items with leading spaces are `"…"`‑quoted to survive aapt stripping
  (`nav_walk_minutes`). Keep that pattern for any future spaced plural.
