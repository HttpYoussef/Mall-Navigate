# Phase 6 Revision Log — MallAR AR Subsystem

| Finding | Root Cause | Correction | Files Changed | Validation | Final Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Map→AR native crash** | Map mode destroyed SceneView and AR mode rebound the manager-owned session to a new native bridge. | Keep one SceneView/session bridge mounted; pause and hide it in Map mode, then resume it in AR mode. | `ArSceneViewWrapper.kt`, `UnifiedNavigationScreen.kt` | Unit tests; debug build; Galaxy S22 Ultra retest | RESOLVED |
| **Camera→Map Filament teardown crash** | Phase 6 native anchor/material resources survived until Filament engine teardown. | Gate frames first and release anchor/material resources from the early surface-inactivation callback. | `ManagedARSceneView.kt`, `ArSceneViewWrapper.kt`, `ArAnchorRenderer.kt` | Native logcat sequence audit; debug build | RESOLVED |
| **Duplicate ARCore pause crash** | Surface callback, lifecycle pause, and Compose disposal could call `ARSession.pause()` more than once during teardown. | Serialize session lifecycle operations and make pause/resume/destroy transitions idempotent. | `ArCoreSessionManager.kt` | Unit tests; native backtrace audit; debug build | RESOLVED |
| **Phase 6 anchor-window integration** | No bounded route-to-AR marker layer existed after Phase 5 localization. | Added route-window planner, turn classification, local placement, bounded reconciliation, fade transitions, and correction interpolation. | `AnchorManagementLayer.kt`, `ArAnchorRenderer.kt`, `ArSceneViewWrapper.kt` | `AnchorManagementLayerTest`; full unit suite; debug build | RESOLVED |
| **Voice review clarification** | Voice was temporarily disabled during an earlier crash investigation, but it is an approved existing product feature. | Restored live voice initialization, controls, recognition, and TTS coordination; no AR lifecycle code depends on voice. | `UnifiedNavigationScreen.kt` | Compile and unit/build verification | RESOLVED |

## Final self-review

- [x] Phase 6 anchor management is integrated with the Phase 5 transform.
- [x] Single-session ownership is preserved.
- [x] Map↔AR and surface teardown ordering is hardened.
- [x] Duplicate native lifecycle calls are guarded.
- [x] Voice remains enabled as an approved existing feature and is separate from Phase 6 anchor/lifecycle scope.
- [x] Automated tests pass and the debug APK assembles.
- [ ] Fresh recorded device evidence for the complete Phase 6 validation matrix remains outstanding.
