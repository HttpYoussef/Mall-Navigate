Type: grilling
Status: resolved
Claimed by: Youssef Ibrahim (orchestrator) — grilling 2026-09-10
Blocked by: 08

## Question

Decide the mechanism that stops raw colour literals from creeping back into `ui/` after the
migration, and specify it precisely enough for the foundation ticket to wire it in.

HITL — tooling choice + strictness is the user's call. Small ticket.

**Decide:**
1. **Tool** — Android Lint custom rule, Detekt (`ForbiddenMethodCall` / regex), ktlint custom
   rule, or a plain CI grep. The i18n effort added a `lint { error += "MissingTranslation" }`
   gate (see `.scratch/i18n/issues/04-...`) — mirror that approach if Android Lint fits.
2. **What's banned** — must explicitly cover all of: `androidx.compose.ui.graphics.Color(...)`
   with a literal arg; `Color.White` / `Color.Black` / `Color.Gray` / named `Color.*` constants;
   **colour literals inside `Brush.linearGradient` / `verticalGradient` `listOf(...)` lists**;
   **`.copy(alpha = …)` applied to a literal**; and a decision on **XML `@color/…` references
   from Kotlin** (ban, or allow only from an approved token-backed set). Inside `ui/` only.
3. **What's exempt** — the `ui/theme/` package, `@Preview` code, genuinely non-themeable colour
   (a fixed brand-logo tint on data), test code. How exemptions are marked (`@Suppress`, path
   allowlist, an annotation).
   - **`OfferItem.tint` and similar data-owned colours** (codex C13) — `OfferItem.tint`
     (`Homescreen.kt:65-79`) is an arbitrary UI colour composited into offer gradients, **not** a
     logo tint, so the "brand-logo tint" exemption above does not cover it. Decide: does it stay a
     data-owned colour with its own contrast constraint (and how is that enforced), or become a
     semantic theme role? The lint rule's exemption wording depends on this answer.
4. **Severity + CI** — error (build fails) vs warning; which Gradle task; whether it blocks the
   existing gates (`compileDebugKotlin`, `lintDebug`, `testDebugUnitTest`).
5. **Rollout** — the gate can't go green until every screen is migrated. The answer must name a
   **concrete checked-in baseline artifact** (e.g. `detekt-baseline.xml`, or a
   `theme-migration-allowlist.txt` of remaining files) that each migration batch shrinks, not a
   vague "turn it on later" — plus which ticket flips it to hard-error once the baseline is empty.

**Output**: the answer records tool + rule spec + rollout plan. Referenced by the foundation
migration ticket (Ticket 05 output).

## Answer

Resolved 2026-09-10 via grilling (1 round, all "agree with all"). Full spec in
`docs/Theming/README.md` §9.

- **Q1 tool**: a **Gradle regex verification task `checkThemeColors`** (`buildSrc`/
  `build.gradle.kts`, no new plugin), `dependsOn` from `check`. Chosen over Detekt custom rule /
  Android Lint `Detector` — matches the "small ticket" + no-CI reality; the migration is finite
  and afterward the gate is just a regression guard. (Detekt is the better long-term pick if more
  custom rules are wanted; user chose the light option.)
- **Q2 banned** (in `ui/**` minus `ui/theme/**`): literal `Color(...)`; named `Color.White/.Black/
  .Gray/.LightGray/.DarkGray/.Red/.Green/.Blue/.Cyan/.Magenta/.Yellow`; colour literals inside
  gradient `listOf`; `.copy(alpha=)` on a literal **or a `MallColors` token** (only
  `scrim.copy(alpha=)` allowed); hardcoded XML hex read from Kotlin **and `@color/` refs from
  Kotlin** (banned entirely — `colors.xml` is deleted). **`Color.Transparent` allowed** ("no
  paint", not a value).
- **Q3 exempt**: `ui/theme/**`, `**/test/**`, `**/androidTest/**`, and a trailing
  `// theme-lint:allow <reason>` marker (greppable; not `@Suppress` — scan isn't AST-aware).
  `@Preview` NOT blanket-exempt. **`OfferItem.tint` (codex C13)**: stays data-owned + marker-
  exempt; spec constraint — text over a `tint` gradient sits on a token scrim, never on `tint`;
  exemption self-removes when offers get real data (colour moves out of `ui/`).
- **Q4 severity**: hard **error** once baseline empty; until then fails only on *new* entries.
  New explicit gate command `:app:checkThemeColors` alongside the existing four; not hooked into
  `compileDebugKotlin`/`lintDebug`.
- **Q5 rollout**: checked-in **`config/theme-migration-allowlist.txt`** (`path:linecount` per
  unmigrated file, ~30 from Ticket 02). Each batch deletes its files' lines (a batch that
  doesn't shrink it is incomplete). **Ticket 05's final "Lint-gate flip" batch deletes the
  allowlist + switches the task to plain hard-error.**

**Unblocks Ticket 05.**
