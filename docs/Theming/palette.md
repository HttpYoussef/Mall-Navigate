# MallAR Palette — Contrast Table

Companion to `README.md`. Every text / UI / boundary pairing, the backdrop it is measured
against, its WCAG contrast ratio, and pass/fail against the target. Ratios are computed
(sRGB relative luminance, WCAG 2.1); the Ticket 07 JVM test is the authority and must
reproduce every row marked **must pass**.

Targets: **4.5:1** normal text · **3:1** large text (≥ 18.66px bold / ≥ 24px regular),
UI component boundaries, focus rings · decorative elements exempt (listed at the end).

---

## Light mode

| Foreground | Backdrop | Ratio | Target | Result |
|---|---|---|---|---|
| `textPrimary #151A1E` | `surface #FFFFFF` | 16.6:1 | 4.5 | ✅ |
| `textPrimary #151A1E` | `screenBackground #F7F9FB` | 15.8:1 | 4.5 | ✅ |
| `textPrimary #151A1E` | `surfaceSunken #EEF1F4` | 14.6:1 | 4.5 | ✅ |
| `textSecondary #5A6672` | `surface #FFFFFF` | 5.9:1 | 4.5 | ✅ |
| `textSecondary #5A6672` | `surfaceSunken #EEF1F4` | 5.4:1 | 4.5 | ✅ |
| `accentText #0A6360` | `surface #FFFFFF` | 7.0:1 | 4.5 | ✅ |
| `accentText #0A6360` | `screenBackground #F7F9FB` | 6.6:1 | 4.5 | ✅ |
| `accentText #0A6360` (`focusRing`) | `surface #FFFFFF` | 7.0:1 | 3 | ✅ |
| `accent #107C7A` (`borderStrong`) | `surface #FFFFFF` | 3.7:1 | 3 | ✅ |
| `accent #107C7A` (`borderStrong`) | `screenBackground #F7F9FB` | 3.5:1 | 3 | ✅ |
| `brandTeal #1A8C8C` — **large text only** | `surface #FFFFFF` | 4.1:1 | 3 (large) | ✅ large / ❌ body |
| `onAccent #FFFFFF` | `accent #107C7A` | 5.1:1 | 4.5 | ✅ |
| `onPrimaryContainer #FFFFFF` | `primaryContainer #107C7A` | 5.1:1 | 4.5 | ✅ |
| `successText #136B32` | `surface #FFFFFF` | 5.9:1 | 4.5 | ✅ |
| `onSuccess #FFFFFF` | `success #1E7A3E` | 5.4:1 | 4.5 | ✅ |
| `errorText #B0271F` | `surface #FFFFFF` | 6.1:1 | 4.5 | ✅ |
| `onError #FFFFFF` | `error #C0362C` | 5.5:1 | 4.5 | ✅ |
| `onErrorContainer #B0271F` | `errorContainer #F9DEDB` | 5.7:1 | 4.5 | ✅ |
| `warningText #8A5A00` | `surface #FFFFFF` | 5.9:1 | 4.5 | ✅ |
| M3 `outline #8A939C` | `surface #FFFFFF` | 3.11:1 | 3 | ✅ |
| M3 `onSurfaceVariant #5A6672` | `surfaceVariant #EEF1F4` | 5.4:1 | 4.5 | ✅ |
| M3 `inverseOnSurface #F2F5F6` | `inverseSurface #151A1E` | 15.4:1 | 4.5 | ✅ |
| M3 `inversePrimary #2FA3B8` | `inverseSurface #151A1E` | 5.6:1 | 4.5 | ✅ |
| `onSecondaryContainer #0A6360` | `secondaryContainer #DCEBEA` | 6.3:1 | 4.5 | ✅ |
| `imagePlaceholder` monogram `accentText #0A6360` | `imagePlaceholder #FFFFFF` | 7.0:1 | 4.5 | ✅ |

## Dark mode

| Foreground | Backdrop | Ratio | Target | Result |
|---|---|---|---|---|
| `textPrimary #E8ECEF` | `surface #161D22` | 13.8:1 | 4.5 | ✅ |
| `textPrimary #E8ECEF` | `screenBackground #0E1418` | 15.1:1 | 4.5 | ✅ |
| `textSecondary #9BA8B0` | `surface #161D22` | 6.4:1 | 4.5 | ✅ |
| `textSecondary #9BA8B0` | `surfaceSunken #10161A` | 7.1:1 | 4.5 | ✅ |
| `accentText #5BC9C2` | `surface #161D22` | 7.9:1 | 4.5 | ✅ |
| `accentText #5BC9C2` (`focusRing`) | `surface #161D22` | 7.9:1 | 3 | ✅ |
| `accent #3FB2AC` (`borderStrong`) | `surface #161D22` | 5.6:1 | 3 | ✅ |
| `brandTeal #2FA3B8` — **large text only** | `surface #161D22` | 4.6:1 | 3 (large) | ✅ |
| `onAccent #06201F` | `accent #3FB2AC` | 6.0:1 | 4.5 | ✅ |
| `successText #6FCB89` | `surface #161D22` | 6.7:1 | 4.5 | ✅ |
| `onSuccess #06201F` | `success #3EA55F` | 5.1:1 | 4.5 | ✅ |
| `errorText #EE9089` | `surface #161D22` | 6.2:1 | 4.5 | ✅ |
| `onError #1A0E0D` | `error #E06A60` | 4.9:1 | 4.5 | ✅ |
| `onErrorContainer #EE9089` | `errorContainer #5C201B` | 4.7:1 | 4.5 | ✅ |
| `warningText #D8A24B` | `surface #161D22` | 6.3:1 | 4.5 | ✅ |
| M3 `outline #6B7780` | `surface #161D22` | 3.72:1 | 3 | ✅ |
| M3 `onSurfaceVariant #9BA8B0` | `surfaceVariant #10161A` | 7.1:1 | 4.5 | ✅ |
| M3 `inverseOnSurface #151A1E` | `inverseSurface #E8ECEF` | 13.8:1 | 4.5 | ✅ |
| `onSecondaryContainer #5BC9C2` | `secondaryContainer #123B39` | 5.5:1 | 4.5 | ✅ |

## Always-dark chrome (both modes — measured once)

| Foreground | Backdrop | Ratio | Target | Result |
|---|---|---|---|---|
| `onScrim #F2F5F6` | `scrimSurface #0E1A1F` | 16.0:1 | 4.5 | ✅ |
| `onScrim #F2F5F6` | `scrimCard #182830` | 12.8:1 | 4.5 | ✅ |
| `onScrimMuted #9BAAB2` | `scrimSurface #0E1A1F` | 6.6:1 | 4.5 | ✅ |
| `onScrimMuted #9BAAB2` | `scrimCard #182830` | 5.3:1 | 4.5 | ✅ |

## Composited values (measured after compositing, not on nominal ARGB)

| Value | Over | Effective | Contrast check | Result |
|---|---|---|---|---|
| `scrim #000000 @ 40%` | light screen content | dims background | `onScrim` text sits on `scrimSurface`/`scrimCard`, not on the 40% wash | ✅ |
| `overlayScrimGradient` `surface → surface @ 90%` | `surface` | opaque enough where text lands | card text confined to the ≥ 85% region | ✅ |
| `hairlineOverlay #FFFFFF @ 8%` | `surface` dark `#161D22` | ≈ `#2B3237` | decorative only — not contrast-bearing | n/a |

## Decorative — AA-exempt (asserted exempt in the test, not silently skipped)

| Token | Reason |
|---|---|
| `textDisabled #9AA5AF / #6B7780` | disabled affordance; WCAG exempts disabled controls |
| `border #E2E7EC / #2A343A` | decorative hairline; the visible boundary role is M3 `outline` |
| `divider #EAEEF1 / #232C31` | list-row separator; not a component boundary |
| `hairlineOverlay` | 1px inset sheen on dark cards |
| `imagePlaceholder` / `imageErrorSurface` as *fills* | surface colours, not foregrounds; text on them is checked above |

## Notes on method

- Ratios use WCAG 2.1 sRGB relative luminance: `L = 0.2126 R + 0.7152 G + 0.0722 B` on
  linearised channels, `contrast = (L_light + 0.05) / (L_dark + 0.05)`.
- "Large text" = ≥ 18.66px bold or ≥ 24px regular (WCAG). `brandTeal` is the only token
  restricted to it.
- Rows here are computed at spec time. The Ticket 07 JVM test recomputes them from the actual
  `Color` values at build time; any drift fails the build.
