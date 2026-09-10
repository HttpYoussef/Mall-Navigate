Type: prototype
Status: resolved
Claimed by: Youssef Ibrahim (orchestrator) — prototype 2026-09-10
Blocked by: 02

## Question

Choose the concrete colour **values** for the light palette and mock enough of the app in that
palette for the user to react once before it locks. (The token *list* — which semantic slots
exist — is decided in Ticket 03 against the inventory, not here; this ticket produces values and
a visual, and may recommend slots it finds it needs.)

**Inputs already settled** (see map Notes → "Settled while charting"):
- Hybrid architecture: one palette feeds an M3 `ColorScheme` + a `MallColors` extension.
- Teal `#1A8C8C` is the primary accent; purple `#9D5CFF` (`DesignPurple`) is retired; gold
  `#C39D51` available as a sparing secondary if the prototype wants it.
- Both light and dark values are produced (dark = consolidation of existing darks onto shared
  tokens: `DeepNavyBg #06131A`, `GlassCardBg #0D1E26`, `MutedTextSubDark #8BA3AD`, `DarkBackground
  #121218`, `DarkSurface #1E1E2A`, `DarkCard #282838`, `DarkTextPrimary #E8E8F0`, `nav_surface
  #0A0F1E`, `ai_card #0D1B2A` … — where these disagree for the same role, pick one).
- WCAG AA is a hard bar: 4.5:1 body text, 3:1 large text / UI components / borders.

**Design constraint the prototype must honour, not discover:**
- **Teal `#1A8C8C` fails AA as text/icon on white** (~3.4:1). It is fine as a *fill* behind
  white text, but teal-coloured labels, links, selected-tab text and icon buttons on a light
  surface will fail 4.5:1. The palette therefore needs a **darkened on-light accent** variant
  (around `#0F6B6B` / the existing `DarkTeal`, ~5.5:1 on white) distinct from the fill accent.
  Both values are part of the deliverable.

**Produce:**
1. **Light + dark hex value for every colour the palette needs**, each text/UI value paired with
   its measured contrast ratio against its background. AA failures do not ship.
2. A **swatch + value table** page (colour, proposed role, light value, dark value, contrast) as
   a linked asset — organised by role so Ticket 03 can turn it into the named token list.
3. **Home and Profile mocked in the proposed light theme** — Home is the dense/visual case
   (cards, search bar, offers row, bottom nav), Profile the text/list-heavy case. Any fidelity
   that communicates: annotated screenshot mock-ups, static HTML, a throwaway artifact. Enough to
   catch "this accent is wrong" / "the cards vanish on this background" / "the glass effect
   doesn't translate". Use the current-state screenshots captured in Ticket 02 as the before.
4. A recommendation on the **glass/glow aesthetic in light mode** (solid cards + elevation vs
   tinted surface vs subtle glow) and on the **Splash screen** (stay dark, or go light).

**Output**: the prototype asset(s) linked from this ticket; the answer records the locked value
set + the glass/splash recommendations + any slots the prototype found it needs. Feeds Ticket 03.

HITL — the palette locks only after the user reacts to the mock-ups.

## Answer

Resolved 2026-09-10. Prototype built as an HTML artifact (palette + token table + Home & Profile
rendered in the light palette + glass/glow + splash recs), reviewed live by the user.

**Prototype asset**: [assets/01-light-palette-prototype.html](assets/01-light-palette-prototype.html)
· published artifact <https://claude.ai/code/artifact/d1ad4154-cb08-4d70-aee4-2942bae2aef5>

### User decisions (all four accepted as proposed)

1. **Teal split into 3 roles** — accepted. `brandTeal` stays the identity mark; `accent` +
   `accentText` are the AA-tuned functional teals. Purple `DesignPurple #9D5CFF` and the bespoke
   indigo `#5847E8` are **retired**.
2. **Light mode drops the glass** — accepted. Flat `surface` + one real shadow + teal on the
   primary action only. The `rememberInfiniteTransition` glow animations are **disabled in light
   mode** (kept in dark), and respect reduced-motion. Optional static 6% accent wash behind the
   Home hero card only — implementer's discretion, drop-if-in-doubt.
3. **Splash** — `#0F5F5F` (deep brand teal / `DarkTeal`), one fixed background both modes, white
   logo. Replaces the arbitrary `#06131A` navy.
4. **`DestinationSelectionScreen` violet** — **flatten fully to the teal roles**. No distinct
   wayfinding hue.

### Locked token set — ~26 tokens (feeds Ticket 03 palette table + Ticket 08 MallColors)

| Token | Role | Light | Dark | Notes |
|---|---|---|---|---|
| `screenBackground` | app background | `#F7F9FB` | `#0E1418` | cool grey, not pure white |
| `surface` | cards / sheets / nav bar | `#FFFFFF` | `#161D22` | separated by shadow, not border |
| `surfaceSunken` | search field / input well | `#EEF1F4` | `#10161A` | |
| `textPrimary` | headings, body | `#151A1E` | `#E8ECEF` | 16.6:1 on surface |
| `textSecondary` | captions, metadata | `#5A6672` | `#9BA8B0` | 5.9:1 on surface — replaces failing `#888EA8` (3.0:1) |
| `textDisabled` | disabled / placeholder | `#9AA5AF` | `#6B7780` | decorative, exempt |
| `brandTeal` | logo, splash, large fills | `#1A8C8C` | `#2FA3B8` | 4.1:1 — **large text only**, never small text/icons |
| `accent` | interactive fill (button/toggle/FAB/active tab) | `#107C7A` | `#3FB2AC` | white on it 5.1:1 |
| `accentText` | teal as text / icon / link on light surface | `#0A6360` | `#5BC9C2` | 7.0:1 on surface |
| `onAccent` | text/icon on `accent` fill | `#FFFFFF` | `#06201F` | |
| `border` | card / input outline | `#E2E7EC` | `#2A343A` | hairline, decorative |
| `borderStrong` | focused input | = `accent` | = `accent` | 3.1:1 on ground |
| `divider` | list-row separators | `#EAEEF1` | `#232C31` | decorative |
| `success` / `onSuccess` | parking saved, redeemed (fill) | `#1E7A3E` / `#FFFFFF` | `#3EA55F` / `#06201F` | white on it 5.4:1 — replaces `#4CAF50` (2.5:1) |
| `successText` | green as text on surface | `#136B32` | `#6FCB89` | 5.9:1 |
| `error` / `onError` | failed scan, destructive (fill) | `#C0362C` / `#FFFFFF` | `#E06A60` / `#1A0E0D` | white on it 5.5:1 — replaces `#E53935` |
| `errorText` | error text, favourite heart | `#B0271F` | `#EE9089` | 6.1:1 |
| `warningText` | "coming soon", caution | `#8A5A00` | `#D8A24B` | 5.9:1 |
| `scrimSurface` | dark chrome over camera (always-dark) | `#0E1A1F` | `#0E1A1F` | identical both themes |
| `scrimCard` | panels on the AR HUD (always-dark) | `#182830` | `#182830` | |
| `onScrim` / `onScrimMuted` | text on scrim | `#F2F5F6` / `#9BAAB2` | same | 13:1 / 4.6:1 |
| `scrim` | dialog / sheet backdrop | `#000000 @ 40%` | same | only alpha-bearing token besides one hairline |
| `shadow` | resting card elevation | `0 2px 12px -2px rgba(15,31,46,.10)` (light) | darker/opaque in dark | Ticket 08 formalises as elevation levels |

**Alpha rule** (codex C7): tokens are **opaque**. `scrim` + one hairline overlay are the only
alpha-bearing values, both measured composited. The old `X.copy(alpha=…)` "tokens" do not survive.

**Graduated from fog**: the "glass/glow" question and the splash question are now decided (above).
