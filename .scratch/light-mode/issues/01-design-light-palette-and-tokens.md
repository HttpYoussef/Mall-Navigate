Type: prototype
Status: open
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
