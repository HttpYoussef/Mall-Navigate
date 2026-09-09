Type: task
Status: open
Blocked by: 02, 03, 04, 06, 07

## Question

Turn the inventory (02), the spec + API contract (03), the lint gate (04), the XML/system-bar
strategy (06) and the verification mechanism (07) into the actual implementer ticket set — the
handoff boundary this map exists to reach. After this ticket the way is clear: nothing left to
decide, `agy` executes.

HITL — the user signs off on the ticket slicing before handoff.

**Produce `docs/Theming/migration-tickets.md` (or new `.scratch/light-mode/issues/` entries) with:**

1. **Foundation ticket** — create `ui/theme/` : the palette definition, `MallColors` +
   `LocalMallColors`, the `MallARTheme` wrapper providing M3 + `MallColors`, the token accessor,
   the centralised system-bar control from Ticket 06, wire the lint gate (per 04's rollout plan),
   migrate 2-3 pilot screens (one Home-flow, one text-heavy, one always-dark) as the reference
   implementation. Acceptance: pilots verified per Ticket 07's method in both modes, AA verified,
   existing build gates green, lint gate active (baseline or on).

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

4. **Ordering + dependency notes** — foundation first; shared-components batch before the screens
   that use them; XML-layer and always-dark screens can go anytime after foundation; a final
   ticket that flips the lint gate to hard-error once the baseline is empty.

5. **Fog resolution** — fold the map's "Not yet specified" items (glass/glow treatment, splash,
   status colours, AR materials) into either a batch ticket or an explicit out-of-scope note,
   based on what 01/02/06 concluded.

**Execution model**: the resulting tickets are run as ordinary `agy` delegate-and-review cycles
(orchestrator reviews each diff), **not** wayfinder decision tickets. The map tracks their status
only if the user wants progress reflected here.

**Output**: the migration ticket set, linked here and from `docs/Theming/`. The answer records the
final ticket list. This closes the map's charting work.
