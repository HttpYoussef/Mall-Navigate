# Light-mode map — codex adversarial debate (2026-09-10)

Independent adversarial review by `codex` (`codex exec`, read-only, effort:high) on the full
revised map + all 7 tickets + `self-review-findings.md`, grounded in the real source. Prompt:
`scratchpad/codex-debate-prompt-v2.md`. 1.04M tokens.

Per user instruction: fold in every finding that is **not** a change to business logic,
callbacks, or the locked architecture. All 15 are ticket / spec-coverage gaps — no code change —
so all 15 are folded in as decision-ticket refinements. Locked decisions (hybrid architecture,
Teal primary, manual `isDarkMode`, AA bar, batched migration) were not reopened.

| # | Tag | Finding | Disposition |
|---|-----|---------|-------------|
| C1 | MISSING | `lightColorScheme(...)` in `Theme.kt:16-40` sets only ~10 M3 roles; `primaryContainer`, `tertiary`, `error`, `outline`, `scrim`, inverse + surface-container roles keep Material purple defaults → leak into dialogs, text fields, snackbars, selection, elevated surfaces. | **Ticket 03** — complete M3-role mapping table + "no library default remains" check. |
| C2 | MISSING | M3 uses `colorScheme.primary` for text/icons/outlines/tabs, not just fills. Teal `#1A8C8C` fails AA as text on white. | **Ticket 03** — decide whether M3 `primary` = the darkened AA-safe teal, and how fills get the brighter value without stock components picking the wrong one. |
| C3 | MISSING | Ticket 06's "system-bar icons keyed on `isDarkMode` alone" breaks always-dark routes: in light mode `LogoScanScreen` / `UnifiedNavigationScreen` still render dark surfaces → dark icons on dark. | **Ticket 06** — route/surface-level system-bar override contract, restore-on-navigate, both bars. |
| C4 | RISK | `AppPreferences.isDarkMode` starts `false`, inits async on `Dispatchers.IO`; `MainActivity` `setContent`s immediately → dark user sees a light first frame then dark; native splash stays XML-dark. | **Ticket 06** — decision: synchronous mode bootstrap before first themed frame + cold-start test. Flagged to user (implies a small startup change). |
| C5 | SPLIT | Ticket 03 blocked by 4 tickets (01,02,06,07); 04 by 03. Over-serial. | **Rewired** — 02 inventory de-scoped from screenshots (→ 07); 03 split into 03 (palette/tokens) + 08 (API/component contract); 03 now blocked by 01,02,06 only; 07 no longer a hard blocker of 03. |
| C6 | MERGE | `rememberHomeColorScheme` has only 6 call sites — its fate shouldn't block the whole API spec. Bug note: if an adapter reads `LocalMallColors.current`, `remember(isDarkMode)` is a stale key. | **Ticket 08** — call-site fate is a foundation-ticket detail; record the memoization-key warning. |
| C7 | MISSING | Current "tokens" bake in alpha (`GlassCardBg.copy(alpha=0.45f)`, `Color.White.copy(0.08f)`); screens add mode-dependent alpha to cards/borders/glows/gradients. Opaque semantic colours change rendering; alpha-bearing tokens make contrast backdrop-dependent (breaks AA measurement). | **Ticket 03** — opaque-base vs compositing-token rules; `.copy(alpha)` behaviour; contrast measured *after* compositing. |
| C8 | MISSING | No component-state contract: ripple/indication (`indication = null` custom press handling), pressed/disabled alpha, explicit shadow colours (`CardShadow`), custom dialog overlays, M3 tonal elevation in dark, text-selection handles. | **Ticket 08** — component matrix so two screens on the same token render identically. |
| C9 | MISSING | Ticket 07 only asks Paparazzi/Roborazzi vs manual. Real screens need Firebase, camera lifecycle, permissions, repo data, async Coil, locale, animated glows. No `@Preview` providers / fixture builders exist. | **Ticket 07** — foundation-owned fixture harness (fake repos/auth, fixed routes, permission/camera stand-ins, fixed viewport/locale, animations off) + explicit "can't capture on JVM" handling. |
| C10 | SCOPE | "Every `*.kt` under `ui/**`" pulls in ViewModels, `Color.kt`, `Type.kt`. Ticket 05's 7 areas + 3 bespoke + foundation + XML + lint-flip ≈ 13 sessions, not "~8". | **Ticket 02** split output into route screens / shared renderers / non-rendering support + route manifest. **Map + Ticket 05** — core-flow milestone, long tail explicitly deferred (all-screen destination retained). |
| C11 | REWIRE | Ticket 05 puts a Home pilot in the foundation but also says shared-components batch precedes screens — Home depends on `HomeSharedComponents` + `StoreLogo` (hardcoded `Color.White` container). Contradiction. | **Ticket 05** — migrate shared primitives inside the foundation pilot, or move the Home pilot after the shared batch. |
| C12 | MISSING | `StoreLogo` forces `Color.White` container even in dark, `crossfade(false)`; `StoreDetail` uses crossfade. No placeholder/error surfaces. | **Ticket 03** — loading / error / crossfade background tokens per mode. |
| C13 | MISSING | `OfferItem.tint` is arbitrary UI colour composited into offer gradients — not a logo tint, so Ticket 04's "brand-logo tint" exemption doesn't cover it. | **Ticket 04** — decide: data-owned colour with its own contrast constraint, or a semantic role. |
| C14 | SCOPE | If AA is a hard bar, status colours (`RedAccent`, `SuccessGreen`, nav status) can't stay fog — they're visible chrome. AR exemption is too broad: map-mode indicators are Compose chrome, not camera rendering. | **Map** — status colours graduate from fog to a Ticket 03 deliverable (fg/bg pairs); AR out-of-scope narrowed to camera-only 3D rendering. |
| C15 | MISSING | XML theme inherits `Theme.AppCompat.DayNight`; Compose uses a manual boolean → two mode sources. IME/keyboard appearance, config recreation not covered. | **Ticket 06** — one authoritative mode source (reconcile the DayNight parent with the locked `isDarkMode` boolean — not changing the boolean, deciding what XML does) + keyboard-visible acceptance case. |

**codex most confident: C1, C3, C4.**
