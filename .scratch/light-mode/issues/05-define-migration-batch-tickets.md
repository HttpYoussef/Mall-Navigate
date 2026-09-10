Type: task
Status: resolved
Claimed by: Youssef Ibrahim (orchestrator) 2026-09-10
Blocked by: 02, 03, 04, 06, 07, 08

## Question

Turn the inventory (02), the palette/token spec (03), the API + component contract (08), the lint
gate (04), the XML/system-bar strategy (06) and the verification mechanism (07) into the actual
implementer ticket set — the handoff boundary this map exists to reach. After this ticket the way
is clear: nothing left to decide, `agy` executes.

HITL — the user signs off on the ticket slicing before handoff.

**Produce `docs/Theming/migration-tickets.md` (or new `.scratch/light-mode/issues/` entries) with:**

1. **Foundation ticket** — create `ui/theme/` : the palette definition + full M3 role map
   (Ticket 03), `MallColors` + `LocalMallColors`, the `MallARTheme` wrapper, the token accessor,
   the route-aware system-bar contract + synchronous mode bootstrap (Ticket 06), the verification
   fixture harness (Ticket 07), wire the lint gate (Ticket 04). **Pilot-screen ordering fix**
   (codex C11): the Home pilot depends on `HomeSharedComponents` + `StoreLogo` (hardcoded
   `Color.White`), and Ticket 5§4 also says shared renderers migrate before their screens — so
   the foundation ticket **either** migrates the shared primitives it needs as part of itself,
   **or** the Home pilot moves to the first post-shared-batch ticket. Pick one; the foundation
   can't be acceptance-complete with a half-migrated shared dependency. Pilots: one Home-flow,
   one text-heavy, one always-dark. Acceptance: pilots verified per Ticket 07 in both modes, AA
   verified, cold-start flash test passes, existing build gates green, lint gate active.

2. **XML-layer ticket** — `themes.xml`, `colors.xml`, `values-night` (per Ticket 06's decision),
   splash theme, dead-colour deletion. Sized as its own session.

3. **Area batch tickets** — one per group from 02's batch grouping (Home flow, Auth/onboarding,
   Parking, Navigation/AR, Profile/Settings, Localization, shared components) **plus dedicated
   tickets** for the bespoke-dark-palette screens (AI assistant, Chatbot sheet, StoreDetail).
   Each ticket:
   - exact file list + current literal count,
   - the token mapping rules from 03 to apply,
   - always-dark / bespoke-dark screens flagged with their special handling,
   - acceptance criteria: zero raw literals (lint passes for those files), both modes verified
     per Ticket 07, AA contrast checklist run, no regression vs the `.scratch/light-mode/before/`
     baseline, existing build gates green,
   - sized to one implementer session.

4. **Ordering + dependency notes** — foundation first; shared-renderer batch(es) before the
   screens that use them; XML-layer and always-dark screens anytime after foundation; a final
   ticket flips the lint gate to hard-error once the baseline is empty.

5. **Honest sizing + core-flow milestone** (codex C10). Ticket 02's grouping is expected to come
   out around **~13 implementer sessions** (foundation + shared + ~6 route-area + ~3 bespoke-dark
   + XML + lint-flip), not ~8. Define an explicit **core-flow milestone**: foundation + shared +
   Home-flow + Profile/Settings + XML + system-bars = "light mode correct on the screens users
   actually see", with the long tail (Parking, Localization, Navigation chrome, bespoke-dark)
   sequenced after and allowed to land later. The all-screen destination stays; the milestone is
   the checkpoint where the effort is *usable* if it pauses.

6. **Fog resolution** — fold the map's "Not yet specified" items (glass/glow treatment, splash,
   status colours, AR materials) into either a batch ticket or an explicit out-of-scope note,
   based on what 01/02/03/06 concluded.

**Execution model**: the resulting tickets are run as ordinary `agy` delegate-and-review cycles
(orchestrator reviews each diff), **not** wayfinder decision tickets. The map tracks their status
only if the user wants progress reflected here.

**Output**: the migration ticket set, linked here and from `docs/Theming/`. The answer records the
final ticket list. This closes the map's charting work.

## Answer

Resolved 2026-09-10. User signed off on the batch slicing (Q1–Q3 "agreed on all").

**Deliverable**: [`docs/Theming/migration-tickets.md`](../../../docs/Theming/migration-tickets.md)
— execution model, gate set, per-screen QA checklist, master token-mapping table, dependency
graph, core-flow milestone, **13 full batch tickets (00–12)** each with file list + literal
counts + special-handling flags + acceptance criteria, and the fog-resolution table.

**Reconciled the agy-proposed grouping (`screen-inventory.md` §4) against the locked tickets:**
- Batch 01 **deletes `colors.xml`** and creates **no `values-night/`** (Ticket 06 — agy had
  "repurpose" + "create values-night").
- Batch 02 **deletes `rememberHomeColorScheme`/`HomeColorScheme` outright**, call sites read
  `MallTheme.colors.*` not raw `LocalMallColors.current` (Ticket 08 §7.4).
- Batch 00 also lands the **JVM contrast test** (Ticket 07 §6) and the **`Elevation` constants**
  (Ticket 08 §8); Batch 12 is the **`checkThemeColors` Gradle regex task** flip, not detekt
  (Ticket 04).

**Q1 pilots (codex C11)**: Batch 00 pilots = `LanguageScreen` + `ProfileScreen` (both already
consume M3, zero shared-renderer dependency — prove the token system with no half-migrated dep
in foundation). Always-dark contract proven in Batch 01 (`SplashScreen` + `DarkSystemBars()`);
Home-flow proof lands naturally at Batch 04.

**Q2**: the map tracks batch status — "Migration progress" ledger added to `map.md` (13 rows,
status each).

**Q3**: strictly sequential — one `agy` dispatch, one review, one commit, one device sign-off
per batch; gating order 00 → {01, 02} → area batches → 12 is a hard dependency.

**This closes the map's charting work.** From here: `agy` executes the batches, orchestrator
reviews + commits, user signs off each on device. Nothing pushed.
