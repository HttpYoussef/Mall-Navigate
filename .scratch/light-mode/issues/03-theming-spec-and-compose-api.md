Type: grilling
Status: open
Blocked by: 01, 02, 06, 07

## Question

Consolidate the locked palette values (Ticket 01), the inventory (Ticket 02), the XML/system-bar
strategy (Ticket 06) and the verification mechanism (Ticket 07) into the single source-of-truth
spec at `docs/Theming/`, **including the named token list and the exact Compose API surface** the
migration tickets will target.

HITL — the token list, API naming, and the fate of the existing helpers need the user.

**The spec (`docs/Theming/README.md` + supporting files) covers:**

1. **The named token list** — decided here, against Ticket 02's inventory. Start from what the
   codebase already needs (`rememberHomeColorScheme`: bg, cardBg, textMain, textSub, accent,
   border) and extend only as far as real screen shapes demand: screen background, surface/card,
   elevated surface, textPrimary/Secondary/Disabled, accent (fill) + accentOnLight (text/icon,
   per Ticket 01's AA constraint), onAccent, border, divider, scrim, an `alwaysDark` sub-set for
   camera/AR chrome, status (success/error/warning) if the inventory shows they're needed. Every
   token is a migration cost — keep it minimal.
2. **Palette table** — every token, light + dark hex, measured contrast ratio, AA pass/fail
   (all text/UI must pass).
3. **Architecture** — how one palette definition produces both the M3 `ColorScheme` and the
   `MallColors` extension; which concerns belong to which. (The hybrid decision itself is locked
   — see map Notes — this ticket specifies it, not re-opens it.)
4. **Compose API contract** — the decision to make here:
   - `MallColors` data class shape + `LocalMallColors` `CompositionLocal` (with a non-crashing
     default for previews).
   - How `MallARTheme` selects light vs dark from `AppPreferences.isDarkMode` and provides both
     the M3 `MaterialTheme` and `LocalMallColors` in one wrapper.
   - The read site: `MallTheme.colors.cardBg` accessor object vs raw `LocalMallColors.current`.
   - **Fate of `rememberHomeColorScheme` + `HomeColorScheme`** — delete + replace at call sites,
     or keep as a thin adapter during migration.
   - Token property naming conventions.
5. **Component notes** — cards/elevation on a light surface, bottom nav, search bar, the
   glass/glow resolution from Ticket 01, dialogs, always-dark camera chrome, status colours,
   the bespoke-dark screens (AI assistant / Chatbot / StoreDetail).
6. **System bars + XML** — fold in Ticket 06's decisions (what `themes.xml` becomes, `values-night`
   yes/no, splash, how system-bar appearance is centralised).
7. **Accessibility acceptance criteria** — the AA bar as a per-screen checklist (which contrast
   pairs to measure, what's exempt).
8. **Migration + verification rules** — no raw colour literals in `ui/` outside the theme package;
   both modes converted per screen; the verification method from Ticket 07; the lint gate
   (Ticket 04) must pass.

**Output**: `docs/Theming/` committed on the branch (docs only — no code). The answer records
the token list + API contract decisions. Unblocks Tickets 04 and 05.
