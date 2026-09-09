Type: grilling
Status: open
Blocked by: 03

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
4. **Severity + CI** — error (build fails) vs warning; which Gradle task; whether it blocks the
   existing gates (`compileDebugKotlin`, `lintDebug`, `testDebugUnitTest`).
5. **Rollout** — the gate can't go green until every screen is migrated. The answer must name a
   **concrete checked-in baseline artifact** (e.g. `detekt-baseline.xml`, or a
   `theme-migration-allowlist.txt` of remaining files) that each migration batch shrinks, not a
   vague "turn it on later" — plus which ticket flips it to hard-error once the baseline is empty.

**Output**: the answer records tool + rule spec + rollout plan. Referenced by the foundation
migration ticket (Ticket 05 output).
