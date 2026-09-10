# Light-mode map — adversarial review findings (2026-09-09)

External reviewers all unavailable this session: **codex** (router budget pool exhausted),
**grok** (free-tier usage limit — cut out mid-review both attempts, but did start flagging the
XML-themes / status-bar gap and the always-dark set), **gemini** (client deprecated — "migrate
to Antigravity"), **kimi** (not logged in / no model configured).

So this is a **self-review** adversarial pass (weaker independence). An independent codex debate
on the whole map is still owed once access is restored — the map is deliberately left UNLOCKED.

## Findings and disposition

Per user instruction, only findings that **don't** change business logic, callbacks, or the
locked hybrid architecture were folded in.

| # | Tag | Finding | Disposition |
|---|-----|---------|-------------|
| F1 | MISSING | XML resource layer uncovered — `themes.xml` hardcodes `#06131A` for window + system bars, splash theme hardcodes it, `colors.xml` ~80 colours no `values-night`, `DayNight` parent with no night resources. | **Folded in** — new Ticket 06; Ticket 02 Part B; Ticket 03 §6; Ticket 05 XML-layer ticket. |
| F2 | MISSING | System-bar icon appearance (`isAppearanceLightStatusBars`) set in only 2/30 screens; `MainActivity` has no central handling → invisible light-mode status-bar icons. | **Folded in** — Ticket 06 §4 (decision only: smallest change, no forced inset refactor). |
| F3 | SPLIT | Ticket 01 bundled palette *values* (user-driven) with token-*list* definition (architecture, needs inventory). | **Folded in** — Ticket 01 narrowed to values + prototype; token list moved to Ticket 03 §1. |
| F4 | REWIRE | 01 and 02 not cleanly parallel — 01's Home/Profile mock needs 02's surface/scrim shapes. | **Folded in** — Ticket 01 now `Blocked by: 02`. Frontier is Ticket 02 alone. |
| F5 | MISSING | Nothing owned "how do we prove a screen didn't regress" — no screenshot infra, batches hand-wave it. | **Folded in** — new Ticket 07; Ticket 02 captures `before/` screenshots. |
| F6 | RISK | Teal `#1A8C8C` ≈ 3.4:1 on white — fails AA as text/icon (fine as a fill). | **Folded in** — Ticket 01 design constraint: token set needs a darkened `accentOnLight` (~`#0F6B6B`). |
| F7 | SCOPE | "Dark values, redesign-free" is self-contradictory — existing darks disagree (`#06131A` / `#121218` / `#0A0F1E` / `#0D1B2A`). | **Folded in** — map Notes + Out-of-scope language: consolidation onto tokens, token wins, minor dark shifts accepted; dark *redesign* out of scope. |
| F8 | OK-BUT | Lint gate must handle gradient lists, `.copy(alpha=)` on literals, XML `@color/` refs; baseline needs a concrete checked-in file. | **Folded in** — Ticket 04 §2 + §5 expanded. |
| F9 | OK-BUT | AI-assistant / Chatbot / StoreDetail carry their own full dark palettes — not trivial swaps. | **Folded in** — Ticket 02 + Ticket 05 pre-allocate them as dedicated harder batches. |
| F10 | OK-BUT | Plan-only vs `agy` execution boundary fuzzy. | **Folded in** — map Notes + Ticket 05: migration tickets run as `agy` delegate-and-review cycles, not wayfinder decision tickets. |

Nothing was rejected as wrong; nothing required a business-logic / callback / architecture change.

## Done

The independent codex adversarial debate ran 2026-09-10 on all 7 tickets —
[codex-debate-findings.md](codex-debate-findings.md). 15 further findings, all folded in.
Ticket 03 was split into 03 + 08 as a result (now 8 tickets).
