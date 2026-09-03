# 12: Externalize — navigation-guidance (isolated, strings-only)

**What to build:** The screens a visitor uses while walking to a destination — the unified
navigation screen (AR-mode and map-mode overlays, HUD, floor-transition sheet, orientation
overlay) and the static route map — are fully translated and RTL-correct in Arabic. This is a
strings-only change with extra review scrutiny because these screens sit against the frozen AR /
navigation subsystem.

**Blocked by:** 06, 08.

**Status:** ready-for-agent

- [ ] Every user-facing literal in `UnifiedNavigationScreen` and `StaticMapScreen` becomes a
      resource with an Egyptian-Arabic translation: "Get Oriented", "Floor Change", the
      floor-transition copy, "Continue on Floor N", "Map unavailable", the Map / AR toggle
      labels, "N m", "· N min walk", the mute `contentDescription`, arrival copy, route-map
      title.
- [ ] Floor and (if displayed) category references route through the ticket-06 helper/mapping.
- [ ] Numbers (distance, ETA, floor) render Western digits via the formatter, passed as `%1$s`.
- [ ] `StaticMapScreen` `nativeCanvas` labels draw translated strings with direction-aware
      positioning; the route map keeps its real-world orientation and the AR camera view is not
      mirrored.
- [ ] **Zero** changes to `object NavigationState`'s structure, `NavigationSessionManager`, the
      pathfinding engine, `DriftMonitor`, the AR scene / `ar/` package, or the scan flow. Only
      literal → `stringResource` substitution and helper routing. The diff is reviewed against
      this constraint explicitly.
- [ ] These routes pass the pseudolocale check (`en_XA`) and the RTL check (`ar_XB` + real
      `ar-EG`, light and dark), including the HUD, floor sheet, and canvas labels.
- [ ] `./gradlew compileDebugKotlin`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin`,
      `lintDebug` green.
