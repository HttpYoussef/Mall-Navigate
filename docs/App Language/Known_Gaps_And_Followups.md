# Known Gaps & Follow‑ups

Living document. Update it when an item closes or a new one appears. Check it before assuming
the subsystem is "done".

**`feat/app-localization` is merged into `main`** (2026‑09‑13, via `feat/colors-light-modes`,
which was stacked on top of it — both efforts landed in one fast‑forward, `cc46c1a`). PR #1
closes automatically once `main` is pushed. Work now happens directly on `main`. Latest
relevant commits: `0a09460` (theme memoization), `b301c51` (voucher content), `cc46c1a` (merge
— brings in `main`'s independent AR perf commits), `0d3c9fd` (LanguageScreen back-button label +
debug pseudolocales). **`main` is not yet pushed to `origin`** — that's the repo owner's own step.

---

## A. Resolved

### A1 — ~~Pending merge of `main` into `feat/app-localization`~~ DONE (`cc46c1a`)

The branch was missing 6 AR perf commits from `main` (incl. `ar: disable ARCore plane-debug
visualization`), which confounded any on-device AR-performance comparison against `main`. Fixed
by merging `main` into the branch (clean, zero conflicts — `UnifiedNavigationScreen.kt` and
`LogoScanScreen.kt` both auto-merged and were hand-verified to keep both sides' changes), all 5
gates green, then fast-forwarding `main` to that point. **The AR-perf confound is gone** — B3 can
now be tested for real.

---

## B. Open QA items (ticket 13 sweep — in progress, USER on device)

### B1 — ~~`LanguageScreen.kt` hardcoded `contentDescription = "Back"`~~ FIXED (`0d3c9fd`)

Now `stringResource(R.string.back)`.

### B2 — ~~Debug pseudolocales not force‑enabled~~ FIXED (`0d3c9fd`)

`app/build.gradle.kts` `buildTypes.debug { isPseudoLocalesEnabled = true }` added. `en_XA` /
`ar_XB` are now available on any debug build without touching Developer Options.

### B3 — Theme memoization (`0a09460`) needs on‑device confirmation

Committed, gates‑green, merged to `main`. The AR-perf confound (A1) is now resolved, so this is
testable for real: confirm the AR pre-trip orientation phase is smooth in Arabic on a physical
device.

### B4 — Voucher content fix (`b301c51`) needs on‑device confirmation

Open Offers + a voucher detail in Arabic; confirm title / description / expiry line / terms
are all Arabic and the offer search still matches by localized title. Committed, gates‑green,
merged to `main`, not yet device‑verified.

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
