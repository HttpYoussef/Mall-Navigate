# App Language (Localization) — Subsystem Overview

This folder is the architecture record for MallAR's **App Language** subsystem: the code and
conventions that let the whole UI render in a chosen language, with full right‑to‑left (RTL)
layout for Arabic, and that make adding another language a small, mechanical change.

"App language" is a deliberate term. In this codebase **"localization" means the AR
logo‑scan position‑fixing feature** (`LogoScanScreen`, `LocalizationConfirmScreen`) — nothing
to do with translation. When you mean translating the UI, say **app language** /
**localization effort** / **i18n**. See `CONTEXT.md` → "App language", "Supported language",
"Canonical category".

This document is the entry point for anyone — human or AI — working on translation, RTL, a
new language, or a string‑externalization change. **Read it before opening any other file
here.**

---

## What shipped

- The Android **per‑app language API** (AndroidX AppCompat 1.7.0): the user picks a language
  in‑app; the OS persists it; `Activity.recreate()` re‑renders. No custom locale plumbing.
- **English + Arabic** ship. Arabic is Egyptian colloquial. Full RTL.
- **Spanish and French are modelled in code but not shipped** — no `values-es/` /
  `values-fr/`. Adding one later is ~2 files + 2 config lines (see `Adding_A_Language.md`).
- Every user‑facing string in the app is a resource with an Arabic twin, enforced by a lint
  gate. Exceptions (assistant replies, logo‑scan screens) are deliberate and listed.
- A small set of pure, tested **primitives** in `com.example.mallar.data` handle language
  resolution, Western‑digit formatting, bidi isolation, floor labels, category labels, and
  timestamps.

Delivered as branch `feat/app-localization` (draft PR #1), spec + 13 tickets in
`.scratch/i18n/`.

---

## Documentation structure

| Document | Read it when | Status |
|---|---|---|
| `README.md` (this file) | First. Always. | Current |
| `App_Language_Architecture.md` | You need the whole picture: the mechanism, the layers, the seam, where language is resolved and applied. **Self‑contained source of truth.** | **Current — source of truth** |
| `Localization_Conventions.md` | You are adding or editing a string, a plural, a number, a `nativeCanvas` label, or touching RTL layout. The rules you must follow. | Current |
| `Component_Map.md` | You need to know which file does what: every primitive with its signature, every wired screen, every deliberate exclusion. | Current |
| `Adding_A_Language.md` | You are shipping Spanish, French, or any new language. Step‑by‑step. | Current |
| `Testing_Localization.md` | You are QA‑ing a language, RTL, or a string change — real locale, pseudolocales, API matrix. | Current |
| `Decisions_And_Rationale.md` | You want to know *why* it is built this way, or whether something is in scope. Locked decisions + rejected alternatives + out‑of‑scope list. | Current |
| `Known_Gaps_And_Followups.md` | Before you assume something is finished. Open QA items, deferred work, the pending `main` merge. | Living — update it |

---

## Recommended reading order

**Implementer (adding a string, fixing an RTL bug):**
1. This README
2. `Localization_Conventions.md`
3. `Component_Map.md` (skim for the primitive you need)

**Adding a language:**
1. This README
2. `Adding_A_Language.md`
3. `App_Language_Architecture.md` (the "Language resolution" section)

**Reviewer / architect (changing the mechanism):**
1. This README
2. `App_Language_Architecture.md` in full
3. `Decisions_And_Rationale.md`
4. `Known_Gaps_And_Followups.md`

---

## Source of truth

**`App_Language_Architecture.md` governs.** If any other document here, any ticket in
`.scratch/i18n/`, or any code comment appears to conflict with it, it wins — or it is stale
and should be fixed. The `.scratch/i18n/` spec and tickets are the *historical* planning
record; this folder is the *maintained* record.

---

## Maintenance rules

- **A string change** (add/edit/remove a key, both catalogs) needs no doc change here.
- **A new primitive, a new convention, or a new deliberate exclusion** → update
  `Component_Map.md` and, if it is a rule, `Localization_Conventions.md`.
- **A change to the mechanism** (how language is resolved, applied, persisted, or migrated;
  the AppCompat dependency; the seam types) → update `App_Language_Architecture.md` and add an
  entry to `Decisions_And_Rationale.md`. This is the kind of change that should also become an
  ADR under `docs/adr/` — none exist yet; this subsystem's AppCompat migration is the
  strongest candidate for `0001`.
- **An open item is closed, or a new gap is found** → update `Known_Gaps_And_Followups.md`.
- Keep `App_Language_Architecture.md` self‑contained. A reader should never *need* another
  file to start work.
