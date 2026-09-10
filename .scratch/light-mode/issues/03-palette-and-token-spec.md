Type: grilling
Status: resolved
Claimed by: Youssef Ibrahim (orchestrator) — grilling 2026-09-10
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

## Answer

Resolved 2026-09-10 via grilling (2 rounds, all answers "agree with all"). Spec written to
`docs/Theming/README.md` + `docs/Theming/palette.md`.

### Round 1 — token list, primary teal, alpha, dark consolidation

- **Q1 token list**: accept Ticket 01's 26 + **4 additions** = **30 tokens**. Added:
  `imagePlaceholder` (`#FFFFFF` / `#F0F2F4` — logo plate, stays near-white both modes; the one
  that unblocks touching `StoreLogo`), `imageErrorSurface` (= `surfaceSunken`),
  `overlayScrimGradient` (`surface` 0→90%, composited), `focusRing` (= `accentText`, distinct
  from `borderStrong` for a11y).
- **Q2 M3 `primary`** = `accentText #0A6360 / #5BC9C2` (the AA-safe dark teal). M3 routes
  `primary` into text/icon/outline/tab/switch far more than fills; our vibrant CTAs bind
  `MallColors.accent` directly so lose nothing.
- **Q3 `.copy(alpha=)` policy**: tokens opaque (except `scrim`, `overlayScrimGradient`,
  `hairlineOverlay`); `.copy(alpha=)` on a token/literal banned in feature code — a real
  translucent need becomes a named composited token; contrast measured post-composite. This is
  what makes Ticket 04's lint rule non-arbitrary.
- **Q4 dark consolidation**: 4 winners confirmed (bg `#0E1418`, card `#161D22`, secondary text
  `#9BA8B0`, divider `#232C31`). No per-screen exceptions. Dark shifts slightly cooler + uniform.
- **Q5 third bespoke teal**: retire `LogoPrimary #258799` (StoreLogo monogram) → `accentText`.

### Round 2 — full M3 `ColorScheme` role map (36 roles)

- **Q6** `primaryContainer` = `accent` (vibrant) so stock `FAB`/`FilledTonalButton` stay vibrant.
- **Q7** `secondaryContainer` (NavigationBar selected-pill) = pale-teal internal pair
  `#DCEBEA` / `#123B39` + `accentText` icon.
- **Q8** `tertiary` family folded onto teal + neutral (no third brand hue).
- **Q9** M3 `outline` = dedicated 3:1 boundary grey `#8A939C` / `#6B7780` (verified 3.11:1 /
  3.72:1) — **not** the decorative `border` token, which maps to `outlineVariant`.
- **Q10** `surfaceTint = Color.Transparent` both modes — elevation is shadow-only.
- **Q11** `background` (`screenBackground`) vs `surface` kept distinct in the M3 map.
- Full 36-role table + stock-component outcomes: `docs/Theming/README.md` §5.

### Verification contract (feeds Ticket 07 / 08)

One JVM test in `app/src/test/.../ui/theme/`: (1) every fg/bg pair in `palette.md` meets its
target; decorative tokens explicitly exempt; (2) all 36 M3 roles `!= Color.Unspecified` and
equal the §5 table for both schemes ("no library default remains"); (3) `brandTeal` asserted
large-text-only.

### Deliverables — where they landed

| # | Deliverable | Location |
|---|---|---|
| 1 | Named token list (30) | README §3 |
| 2 | Full M3 role map (36) | README §5 + palette.md |
| 3 | `primary` vs `accentText` + stock-component variants | README §5 (Q2/Q6) + outcomes table |
| 4 | Alpha / compositing rules | README §2 + §3 "Alpha-bearing" + palette.md "Composited values" |
| 5 | Palette table w/ contrast | `docs/Theming/palette.md` |
| 6 | Image-surface tokens | README §3 "Image surfaces" (Q1) |
| 7 | Dark consolidation | README §4 (Q4) |

**Unblocks Ticket 08.**
