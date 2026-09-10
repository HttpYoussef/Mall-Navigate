Type: grilling
Status: open
Blocked by: 01, 02, 06

## Question

Produce the **palette + token-system half** of the `docs/Theming/` spec: the named token list,
every value, and the colour-model rules. The Compose API surface and the component-state contract
are **Ticket 08** (split out per codex C5 — this was too much for one session).

HITL — the token list and the M3-role decisions need the user.

**Deliverables:**

1. **The named token list** — decided here against Ticket 02's inventory. Start from what
   `rememberHomeColorScheme` already needs (bg, cardBg, textMain, textSub, accent, border) and
   extend only as far as real screen shapes demand: screen bg, surface/card, elevated surface,
   textPrimary/Secondary/Disabled, **accent (fill)** + **accentOnLight (text/icon)** per Ticket
   01's AA constraint, onAccent, border, divider, scrim, an `alwaysDark` sub-set for camera/AR
   chrome, and **status pairs** (success/error/warning foreground + background — codex C14, these
   are visible chrome and cannot stay fog under a hard AA bar). Every token is a migration cost.

2. **Full M3 `ColorScheme` role mapping** (codex C1) — a table mapping *every* M3 role
   (`primary`, `onPrimary`, `primaryContainer`, `secondary`, `tertiary`, `error`, `errorContainer`,
   `outline`, `outlineVariant`, `scrim`, `surface`, `surfaceVariant`, `surfaceContainer*`,
   `inverseSurface`, `inverseOnSurface`, `inversePrimary` …) to a palette value for light and
   dark. `Theme.kt:16-40` currently sets ~10 roles; the rest are Material purple defaults that
   leak into dialogs, text fields, snackbars, selection, elevated surfaces. Spec must require
   "no library default remains" as a checkable item.

3. **`primary` vs accentOnLight** (codex C2) — M3 uses `colorScheme.primary` for text, icons,
   outlines, tabs and controls, not only fills. Teal `#1A8C8C` fails AA as text on white. Decide:
   is M3 `primary` the darkened AA-safe teal, with the brighter teal used only as an explicit
   fill token? How do stock M3 components (Button, Tab, Switch) end up with the right variant?

4. **Alpha / compositing rules** (codex C7) — today's "tokens" bake in alpha
   (`GlassCardBg.copy(alpha = 0.45f)`, `Color.White.copy(0.08f)`); screens add more mode-dependent
   alpha to cards, borders, glows, gradients. Decide: which tokens are **opaque base** colours
   and which are **compositing** colours (alpha over a named backdrop); define `.copy(alpha)`
   policy; require contrast to be measured **after compositing**, not on the nominal value.

5. **Palette table** — every token + M3 role, light hex, dark hex, the backdrop it's measured
   against, contrast ratio, AA pass/fail. All text/UI must pass.

6. **Image-surface tokens** (codex C12) — loading / error / crossfade background per mode, so
   `StoreLogo` (currently forced `Color.White` even in dark) and `StoreDetail` (crossfade)
   stop diverging.

7. **Dark consolidation** — where Ticket 02 found the same role using mismatched darks
   (`#06131A` / `#121218` / `#0A0F1E` / `#0D1B2A`), pick the winning value (map Notes: token
   wins, minor dark shift accepted).

**Output**: `docs/Theming/README.md` + palette files committed on the branch (docs only). The
answer records the token list + the M3-role and alpha decisions. Unblocks Ticket 08.
