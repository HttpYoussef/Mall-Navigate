# Phase 4–6 Overlay Investigation Report — MallAR AR Subsystem

**Status:** Authoritative investigation and complete remediation plan for the defect documented in `Phase_4_6_Legacy_Overlay_Defect_Report.md`.
**Author:** Antigravity (Gemini Coding Assistant)
**Date:** 2026-08-25
**Target Audience:** Human Lead Architect, Reviewers, Engineering Team

---

## 1. Executive Summary

During Phase 5/6 Human Device Validation on a physical Samsung Galaxy S22 Ultra (`SM-S908E`), the human reviewer observed a screen-space directional arrow ("Turn right in 117m" / "Turn left in 117m") floating in the center of the live camera view simultaneously with 3D AR content. The reviewer noted that this flat, screen-locked arrow with a turn-distance label matches the visual appearance of the pre-redesign legacy overlay that was claimed to have been removed in Phase 4.

This report provides the exhaustive, evidence-based investigation required by `Phase_4_6_Legacy_Overlay_Defect_Report.md`. It directly answers each of the five questions posed, provides a line-by-line trace of the codebase, supplies the complete source code of the relevant composition logic, and defines the precise remediation plan.

---

## 2. Direct Answers to the Five Defect Report Questions

### Question 1: Are `CameraOverlayManager`, `CameraOverlayView`, `OverlayProjectionEngine`, or `OverlayNavigationEngine` instantiated, composed, or rendered anywhere in the live navigation flow reachable from `UnifiedNavigationScreen`?
- **Direct Finding:** **NO.** None of `CameraOverlayManager`, `CameraOverlayView`, or `OverlayNavigationEngine` are instantiated, composed, or rendered in `UnifiedNavigationScreen.kt` or any component in the live AR navigation flow.
- **Detailed Repository Audit:**
  - `CameraOverlayManager`: **0 references** in active flow. Only exists in `com.example.mallar.overlay.CameraOverlayManager` and the deprecated `com.example.mallar.ui.navigation.legacy.CameraNavigationScreen`.
  - `CameraOverlayView`: **0 references** in active flow. Only exists in `com.example.mallar.overlay.CameraOverlayView` and the deprecated `com.example.mallar.ui.navigation.legacy.CameraNavigationScreen`.
  - `OverlayNavigationEngine`: **0 references** in active flow. Only exists in `com.example.mallar.overlay.OverlayNavigationEngine` and the deprecated `com.example.mallar.ui.navigation.legacy.CameraNavigationScreen`.
  - `OverlayProjectionEngine`: Instantiated privately within `NavigationSessionManager` (`private val projectionEngine = OverlayProjectionEngine()`) solely to populate `turnInfo` in `NavSessionState`. Its projected 2D screen points are **never attached or drawn to any view or canvas** in `UnifiedNavigationScreen`.

### Question 2: If any of the above is still present, identify precisely why the Phase 4 report's shown removal did not fully take effect.
- As established in Question 1, the legacy Android View overlay classes and CameraX `PreviewView` were **genuinely removed** from `UnifiedNavigationScreen.kt` in Phase 4 as reported. There is no second composition path or hidden view binding instantiating `CameraOverlayView`.

### Question 3: What IS actually producing the screen-space arrow and turn-distance UI shown in the screenshots?
- **Component Identified:** The screen-space arrow and turn-distance banner are rendered by **`ArDirectionOverlay(state = state, alpha = camAlpha)`**, located at **lines 184–185 and 514–607 of `UnifiedNavigationScreen.kt`**.
- **Nature of the Component:** `ArDirectionOverlay` is a pure Jetpack Compose UI component. It consists of:
  1. A turn banner with an icon, `turnText` (*"Turn right" / "Turn left" / "Go straight"*), and `"in ${state.remainingDistanceM} m"`.
  2. A **160dp Compose `Canvas` drawing a large 2D chevron arrow tilted in 2.5D screen space (`rotationX = 28f`)** directly in the dead center of the camera viewport.
- **Why It Was Preserved:** In `Phase_4_Execution_Plan_v3.md`, `ArDirectionOverlay` was explicitly listed in the components table:
  `| ArDirectionOverlay | .../ui/navigation/UnifiedNavigationScreen.kt | Preserve | Keep the 2D UI arrow active alongside the 3D view. |`
- **Why It Appeared as the Legacy Overlay:** While `ArDirectionOverlay` was intended by the Phase 4 planning author as a supplementary 2D HUD element, its large, center-screen, tilted Canvas drawing visually functions and reads as a **screen-space pseudo-AR overlay**, directly obscuring the 3D floor-attached anchors rendered by `ArSceneView` and replicating the exact user experience of the deprecated system.

### Question 4: What should currently be visible in the live app when AR mode is active and tracking is good (Phase 5/6 State)?
- **3D Floor Guidance (`ArAnchorRenderer` / Filament):**
  - Floor-attached cyan 3D cylinders/markers along the corridor path for standard nodes.
  - Distinct, larger amber 3D cylinders/markers at sharp turn nodes ($\ge 120^\circ$).
  - Bounded sliding window of up to 10 anchors ahead and 2 trailing behind (max 15).
  - Smooth multi-frame (8-frame) correction interpolation upon accepted localization re-fixes.
- **Clean 2D HUD Elements (Screen Space):**
  - Top Bar: Back button (top-left) and Map/AR mode switcher toggle pill (top-right).
  - Bottom Destination Card: Destination store name, remaining distance in meters, and estimated walking minutes.
  - Floating Audio Button: Mute/unmute voice assistant (bottom-right).
  - Modal sheets (Floor Transition sheet, "Get Oriented" orientation card) when triggered.
- **What Must NOT Be Visible:**
  - **Zero floating 2D canvas arrows** in the center of the camera viewport.
  - **Zero pseudo-AR screen-space chevron graphics.**

### Question 5: Submission of Findings and Full Source
- Documented in full within this report, with the complete 608-line source code of `UnifiedNavigationScreen.kt` provided in Section 5 below.

---

## 3. Detailed Component Audit Matrix

| Component | Path | Instantiated in Live AR Flow? | Rendered in Live AR Flow? | Status / Assessment |
|---|---|---|---|---|
| `CameraOverlayManager` | `com.example.mallar.overlay` | **NO** | **NO** | Completely removed from live flow in Phase 4. Exists only in legacy package. |
| `CameraOverlayView` | `com.example.mallar.overlay` | **NO** | **NO** | Completely removed from live flow in Phase 4. Exists only in legacy package. |
| `OverlayNavigationEngine` | `com.example.mallar.overlay` | **NO** | **NO** | Completely removed from live flow in Phase 4. Exists only in legacy package. |
| `OverlayProjectionEngine` | `com.example.mallar.overlay` | Yes (in `NavigationSessionManager`) | **NO** | Used only as a math utility for `turnInfo`. Output points are never rendered. |
| `ArSceneViewWrapper` | `com.example.mallar.ar.ui` | **YES** | **YES (Layer 1)** | Active modern Filament/ARCore host. Renders 3D floor anchors. |
| `ArDirectionOverlay` | `com.example.mallar.ui.navigation` | **YES** | **YES (Layer 2)** | **ROOT CAUSE OF DEFECT.** 2D Compose Canvas arrow floating over camera. |
| `MapLayer` | `com.example.mallar.ui.navigation` | **YES** | **YES (Layer 3)** | 2D calibrated floor map layer (alpha-faded when in AR mode). |
| `NavigationHud` | `com.example.mallar.ui.navigation` | **YES** | **YES (Layer 4)** | Top pill + bottom destination card. Intended HUD. |

---

## 4. Architectural Analysis & Visual Conflict Trace

### The Layout Layer Stack in `UnifiedNavigationScreen.kt`:
```
┌─────────────────────────────────────────────────────────────────────────┐
│ Layer 4: NavigationHud (Top Back/Mode Pill, Bottom Destination Card)   │
├─────────────────────────────────────────────────────────────────────────┤
│ Layer 3: MapLayer (2D Floor Map Canvas, visible in Map mode)            │
├─────────────────────────────────────────────────────────────────────────┤
│ Layer 2: ArDirectionOverlay (2D Compose Canvas Arrow + Turn Banner)     │  <-- CONFLICT
├─────────────────────────────────────────────────────────────────────────┤
│ Layer 1: ArSceneViewWrapper (ARCore / Filament 3D Floor Anchors)        │  <-- INTENDED
└─────────────────────────────────────────────────────────────────────────┘
```

When AR mode is active (`isCameraMode == true`, `camAlpha == 1f`):
1. **Layer 1 (`ArSceneViewWrapper`)** renders the physical camera background and projects world-locked 3D cylinders onto the floor plane.
2. **Layer 2 (`ArDirectionOverlay`)** unconditionally draws a floating banner and a 160dp rotated 2.5D chevron right on top of the camera stream in the vertical center of the screen (`offset(y = -40.dp)`).
3. The user sees both the 3D floor markers in the lower half of the perspective and a prominent 2D arrow directly in front of their eyes pointing directions.

### The Origin of the Conflict:
In `Phase_4_Execution_Plan_v3.md`, the author noted:
```markdown
| ArDirectionOverlay | .../ui/navigation/UnifiedNavigationScreen.kt | Preserve | Keep the 2D UI arrow active alongside the 3D view. |
```
This was a planning oversight: the author treated `ArDirectionOverlay` as a benign "2D UI arrow" without realizing that drawing a large directional arrow over the camera feed violates the fundamental design goal of the redesign: **"Feel like Live View, indoors. Guidance must appear world-locked and physically attached to the floor... fully superseding the overlay pipeline."** (Redesign §2, §6.3, §13).

---

## 5. Complete Current Source of `UnifiedNavigationScreen.kt`

Below is the complete, unedited source of `UnifiedNavigationScreen.kt` (608 lines) establishing the exact composition logic prior to remediation:

```kotlin
package com.example.mallar.ui.navigation

import android.graphics.BitmapFactory
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.navigation.*
import com.example.mallar.utils.FloorMapAssets
import com.example.mallar.voice.NavigationSessionVoiceCoordinator
import com.example.mallar.voice.VoiceAssistantManager
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*
import com.example.mallar.ar.ui.ArSceneViewWrapper
import com.example.mallar.ui.localization.NavigationState

// ── Design tokens ─────────────────────────────────────────────────────────────
private val NavBlue     = Color(0xFF1E64FF)
private val NavBlueDark = Color(0xFF0A3DBF)
private val NavGreen    = Color(0xFF00C853)
private val NavAmber    = Color(0xFFFFA726)
private val NavSurface  = Color(0xFF0A0F1E)
private val NavCard     = Color(0xF0121829)
private val PathColor   = Color(0xFF00BCD4)
private val PathShadow  = Color(0x99006064)
private val StartGreen  = Color(0xFF43A047)
private val EndRed      = Color(0xFFE53935)
private val UserBlue    = Color(0xFF2979FF)
private val WalkedColor = Color(0x886E6E6E)

// ── Map source dimensions ─────────────────────────────────────────────────────
private const val MAP_SRC_W = 1200f
private const val MAP_SRC_H = 685f

// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun UnifiedNavigationScreen(
    onBackClick: () -> Unit,
    viewModel: UnifiedNavigationViewModel = viewModel()
) {
    val state           by viewModel.navState.collectAsState()
    val poseEnabled     by viewModel.poseEnabled.collectAsState()
    val orientationState by viewModel.orientationState.collectAsState()
    val context     = LocalContext.current
    val lifecycle   = LocalLifecycleOwner.current

    val isCameraMode  = state.mode == NavMode.CAMERA

    val suppressPoseFlatUntilMs = remember { AtomicLong(0L) }
    LaunchedEffect(isCameraMode) {
        if (isCameraMode) {
            suppressPoseFlatUntilMs.set(System.currentTimeMillis() + 2800L)
        }
    }

    val voiceAssistant = remember { VoiceAssistantManager(context) }
    val sessionVoice = remember { NavigationSessionVoiceCoordinator(voiceAssistant.ttsManager) }

    var voiceMuted by remember { mutableStateOf(false) }
    LaunchedEffect(voiceMuted) {
        voiceAssistant.ttsManager.isEnabled = !voiceMuted
        if (voiceMuted) voiceAssistant.ttsManager.stop()
    }

    DisposableEffect(Unit) {
        voiceAssistant.initialize()
        voiceAssistant.graphProvider = { MallGraphRepository.loadedGraph }
        voiceAssistant.navStateProvider = { viewModel.navState.value }
        voiceAssistant.onNavigateTo = { shopName, isArabic ->
            NavigationState.preferArabicVoice = isArabic
            viewModel.navigateToNewDestination(shopName)
        }
        voiceAssistant.onNavigateWithOrigin = { originShop, destShop, isArabic ->
            NavigationState.preferArabicVoice = isArabic
            viewModel.navigateFromShopToShop(originShop, destShop)
        }
        voiceAssistant.onStopNavigation = onBackClick
        onDispose {
            voiceAssistant.cancelListening()
            voiceAssistant.destroy()
            sessionVoice.reset()
        }
    }

    LaunchedEffect(Unit) {
        sessionVoice.reset()
        viewModel.navState.collect { sessionVoice.onSessionState(it) }
    }

    // ── Sensors ───────────────────────────────────────────────────────────────
    val modeSelection = state.modeSelection
    DisposableEffect(poseEnabled, modeSelection) {
        if (poseEnabled && modeSelection == NavigationModeSelection.AUTO) {
            val pose = PoseDetectionManager(context)
            pose.onPoseChanged = { p ->
                when (p) {
                    DevicePose.UPRIGHT -> viewModel.switchToCamera()
                    DevicePose.FLAT -> {
                        if (System.currentTimeMillis() >= suppressPoseFlatUntilMs.get()) {
                            viewModel.switchToMap()
                        }
                    }
                    else -> {}
                }
            }
            pose.start()
            onDispose { pose.stop() }
        } else {
            onDispose {}
        }
    }
    DisposableEffect(Unit) {
        val f = SensorFusionManager(context)
        f.onHeadingChanged = { az, _ -> viewModel.onHeadingUpdated(az) }
        f.start()
        onDispose { f.stop() }
    }

    // Phase 4: ARCore lifecycle is handled by ArSceneViewWrapper via UnifiedNavigationViewModel
    DisposableEffect(Unit) {
        onDispose {
            viewModel.arCoreSessionManager.destroy()
        }
    }

    // ── Mode alpha ────────────────────────────────────────────────────────────
    val camAlpha by animateFloatAsState(if (isCameraMode) 1f else 0f, tween(350), label = "cam")
    val mapAlpha by animateFloatAsState(if (!isCameraMode) 1f else 0f, tween(350), label = "map")

    Box(
        Modifier
            .fillMaxSize()
            .background(NavSurface)
            .onSizeChanged { sz ->
                if (sz.width > 0 && sz.height > 0) {
                    viewModel.setScreenSize(sz.width.toFloat(), sz.height.toFloat())
                }
            }
    ) {
        // LAYER 1: AR/Camera Layer (Module 2 + 7 host). Keep the native
        // SceneView mounted across Map↔Camera so the manager-owned Session is
        // never rebound to a fresh SceneView after native teardown.
        ArSceneViewWrapper(
            modifier = Modifier.fillMaxSize(),
            sessionManager = viewModel.arCoreSessionManager,
            localizationLayer = viewModel.localizationLayer,
            routePathLayer = viewModel.routePathLayer,
            initialStartNode = viewModel.initialLocalizationStartNode,
            initialHeadingDeg = viewModel.initialLocalizationHeading,
            active = isCameraMode
        )

        // LAYER 2: 2D Guidance Overlay (Preserved per Execution Plan)
        ArDirectionOverlay(state = state, alpha = camAlpha)

        // LAYER 3: Calibrated map
        MapLayer(state = state, alpha = mapAlpha, modifier = Modifier.fillMaxSize())

        // LAYER 4: HUD
        NavigationHud(
            state = state,
            isCameraMode = isCameraMode,
            onBackClick = onBackClick,
            onModeSelected = { viewModel.setModeSelection(it) }
        )

        if (!state.isArrived) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 92.dp)
                    .navigationBarsPadding()
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (voiceMuted) NavAmber.copy(0.88f) else Color.Black.copy(0.55f))
                    .clickable { voiceMuted = !voiceMuted },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (voiceMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (voiceMuted) "Unmute navigation voice" else "Mute navigation voice",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        state.pendingFloorTransition?.let { transition ->
            if (state.isPausedForFloorTransition) {
                FloorTransitionSheet(
                    transition = transition,
                    onContinue = { viewModel.confirmFloorTransition() }
                )
            }
        }

        if (orientationState.active) {
            OrientationOverlay(orientationState = orientationState)
        }
    }
}

@Composable
private fun OrientationOverlay(orientationState: OrientationUiState) {
    Box(
        Modifier
            .fillMaxSize()
            .background(NavSurface.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NavCard),
            elevation = CardDefaults.cardElevation(16.dp),
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val icon = when (orientationState.direction) {
                    TurnDirection.STRAIGHT -> Icons.Default.ArrowUpward
                    TurnDirection.RIGHT    -> Icons.AutoMirrored.Filled.ArrowForward
                    TurnDirection.LEFT     -> Icons.AutoMirrored.Filled.ArrowBack
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NavBlue,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Get Oriented",
                    color = Color.White.copy(0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun FloorTransitionSheet(
    transition: FloorTransitionHelper.PathFloorTransition,
    onContinue: () -> Unit
) {
    val message = if (transition.toFloor > transition.fromFloor) {
        "Take the escalator or elevator to Floor ${transition.toFloor}"
    } else {
        "Go down to Floor ${transition.toFloor}"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.6f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(NavCard)
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Elevator,
                contentDescription = null,
                tint = NavBlue,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Floor Change",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = Color.White.copy(0.85f),
                fontSize = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NavBlue)
            ) {
                Text(
                    "Continue on Floor ${transition.toFloor}",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MapLayer(state: NavSessionState, alpha: Float, modifier: Modifier = Modifier) {
    if (alpha <= 0f) {
        Spacer(modifier)
        return
    }

    val context = LocalContext.current

    val displayFloor = remember(state.currentFloor, state.isPausedForFloorTransition, state.pendingFloorTransition) {
        if (state.isPausedForFloorTransition && state.pendingFloorTransition != null) {
            state.pendingFloorTransition.fromFloor
        } else {
            state.currentFloor
        }
    }

    val mapBitmap = remember(displayFloor) {
        runCatching {
            context.assets.open(FloorMapAssets.mapAssetForFloor(displayFloor)).use {
                android.graphics.BitmapFactory.decodeStream(it).asImageBitmap()
            }
        }.getOrNull()
    }

    val floorPathNodes = remember(state.pathNodes, displayFloor) {
        state.pathNodes.filter { it.floor == displayFloor }
    }

    if (mapBitmap == null) {
        Box(modifier.graphicsLayer { this.alpha = alpha }.background(NavSurface), Alignment.Center) {
            Text("Map unavailable", color = Color.White.copy(0.4f), fontSize = 13.sp)
        }
        return
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var mapScale  by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val pathSignature = remember(state.pathNodes) {
        if (state.pathNodes.isEmpty()) 0
        else state.pathNodes.size * 31 + state.pathNodes.first().id.hashCode() + state.pathNodes.last().id.hashCode()
    }

    val fitScale = remember(canvasSize) {
        if (canvasSize.width == 0 || canvasSize.height == 0) 1f
        else minOf(canvasSize.width.toFloat() / MAP_SRC_W, canvasSize.height.toFloat() / MAP_SRC_H)
    }

    val fitOffset = remember(canvasSize, fitScale) {
        if (canvasSize.width == 0 || canvasSize.height == 0) Offset.Zero
        else Offset(
            x = (canvasSize.width  - MAP_SRC_W * fitScale) / 2f,
            y = (canvasSize.height - MAP_SRC_H * fitScale) / 2f
        )
    }

    Box(modifier.graphicsLayer { this.alpha = alpha }.onSizeChanged { canvasSize = it }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(fitOffset.x + panOffset.x, fitOffset.y + panOffset.y)
                scale(fitScale * mapScale, fitScale * mapScale, Offset.Zero)
            }) {
                drawImage(mapBitmap)
                
                // Draw Path
                val nodes = floorPathNodes
                if (nodes.size >= 2) {
                    val routePath = androidx.compose.ui.graphics.Path()
                    routePath.moveTo(nodes[0].x.toFloat(), nodes[0].y.toFloat())
                    for (i in 1 until nodes.size) {
                        routePath.lineTo(nodes[i].x.toFloat(), nodes[i].y.toFloat())
                    }
                    val lineW = 3.5f / (fitScale * mapScale)
                    drawPath(routePath, PathShadow, style = Stroke(lineW * 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(routePath, PathColor, style = Stroke(lineW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                // Draw User
                if (state.currentFloor == displayFloor) {
                    val up = Offset(state.userMapX, state.userMapY)
                    val dr = 6f / (fitScale * mapScale)
                    drawCircle(UserBlue.copy(0.12f), dr * 3.2f, up)
                    drawCircle(UserBlue, dr, up)
                    
                    val headLen = dr * 2.2f
                    val rad = Math.toRadians(state.headingDeg.toDouble())
                    val tip = Offset(
                        up.x + headLen * sin(rad).toFloat(),
                        up.y - headLen * cos(rad).toFloat()
                    )
                    drawLine(UserBlue, up, tip, strokeWidth = dr * 0.6f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun NavigationHud(
    state: NavSessionState,
    isCameraMode: Boolean,
    onBackClick: () -> Unit,
    onModeSelected: (NavigationModeSelection) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        // TOP HUD
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(0.45f),
                modifier = Modifier.size(40.dp).clickable { onBackClick() }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.padding(8.dp))
            }
            
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(0.45f)
            ) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    HudTab("Map", !isCameraMode) { onModeSelected(NavigationModeSelection.MAP) }
                    HudTab("AR", isCameraMode) { onModeSelected(NavigationModeSelection.AR) }
                }
            }
        }

        // BOTTOM CARD
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = NavCard,
            tonalElevation = 8.dp
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(state.destinationName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${state.remainingDistanceM}m", color = PathColor, fontWeight = FontWeight.Bold)
                    Text(" • ${state.walkMinutes} min walk", color = Color.White.copy(0.6f))
                }
            }
        }
    }
}

@Composable
private fun HudTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (selected) NavBlue else Color.Transparent,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(0.6f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ArDirectionOverlay(state: NavSessionState, alpha: Float) {
    val turnInfo = state.turnInfo
    if (alpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha },
            contentAlignment = Alignment.Center
        ) {
            if (!state.isArrived && turnInfo != null) {
                val targetAngle = turnInfo.angleDeg
                val animatedAngle by animateFloatAsState(
                    targetValue = targetAngle,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 80f),
                    label = "arrowRot"
                )

                val turnText = when {
                    abs(targetAngle) < 25f -> "Go straight"
                    targetAngle > 25f && targetAngle < 150f -> "Turn right"
                    targetAngle < -25f && targetAngle > -150f -> "Turn left"
                    else -> "Turn around"
                }

                val icon = when {
                    abs(targetAngle) < 25f -> Icons.Default.ArrowUpward
                    targetAngle > 0 -> Icons.AutoMirrored.Filled.ArrowForward
                    else -> Icons.AutoMirrored.Filled.ArrowBack
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.offset(y = (-40).dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Black.copy(0.72f), NavBlueDark.copy(0.72f))
                                )
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = turnText, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            Text(text = "in ${state.remainingDistanceM} m", color = Color.White.copy(0.7f), fontWeight = FontWeight.Normal, fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(60.dp))
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .graphicsLayer {
                                rotationZ = animatedAngle
                                rotationX = 28f
                                shadowElevation = 12f
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val arrowPath = androidx.compose.ui.graphics.Path().apply {
                                moveTo(w * 0.5f, h * 0.05f)
                                lineTo(w * 0.9f, h * 0.45f)
                                lineTo(w * 0.68f, h * 0.45f)
                                lineTo(w * 0.68f, h * 0.95f)
                                lineTo(w * 0.32f, h * 0.95f)
                                lineTo(w * 0.32f, h * 0.45f)
                                lineTo(w * 0.1f, h * 0.45f)
                                close()
                            }
                            drawPath(
                                path = arrowPath,
                                color = Color.Black.copy(alpha = 0.35f),
                                style = Stroke(width = 20f, join = StrokeJoin.Round)
                            )
                            drawPath(
                                path = arrowPath,
                                color = Color.White,
                                style = Stroke(width = 10f, join = StrokeJoin.Round)
                            )
                            drawPath(path = arrowPath, color = NavBlue)
                        }
                    }
                }
            }
        }
    }
}
```

---

## 6. Remediation Plan

### Concrete Engineering Steps:

#### Step 1: Remove `ArDirectionOverlay` from Composition Hierarchy
In `UnifiedNavigationScreen.kt`:
- Remove line 184–185: `ArDirectionOverlay(state = state, alpha = camAlpha)`
- Remove the entire `ArDirectionOverlay` Composable definition (lines 513–607).

#### Step 2: Ensure Unobstructed Viewport
- With `ArDirectionOverlay` removed, the camera viewport in AR mode becomes 100% clean and transparent for Filament / SceneView rendering.
- Directional and turn-by-turn guidance is provided strictly through:
  1. Floor-attached 3D cyan cylinders (standard nodes) and amber cylinders ($\ge 120^\circ$ turns) rendered by `ArAnchorRenderer`.
  2. The bottom HUD card displaying the destination name, remaining distance, and walk time.

#### Step 3: Verification & Compilation Gate
- Run `./gradlew.bat :app:testDebugUnitTest` to verify that all 21 unit tests pass.
- Run `./gradlew.bat :app:assembleDebug` to confirm clean APK build.

#### Step 4: Device Validation Test Protocol (Galaxy S22 Ultra)
The human reviewer tests the resulting build against the following explicit checklist:
1. **Visual Cleanliness:** Launch AR navigation. Confirm that **NO** 2D screen-space blue arrow or "Turn in Xm" banner appears in the camera viewport.
2. **Floor Anchors:** Confirm cyan 3D markers appear attached to the physical floor along the route corridor, and amber markers appear at sharp turns.
3. **HUD Stability:** Confirm the top mode pill and bottom destination card remain visible and legible without overlapping any 3D content.
4. **Lifecycle Transitions:** Confirm Camera $\leftrightarrow$ Map toggling remains smooth and crash-free.

---

## 7. Resolution & Status

With the identification of `ArDirectionOverlay` as the root cause, the apparent contradiction between verified code removal and device visual observation is completely resolved. The legacy overlay classes were indeed replaced; removing the Compose 2D overlay removes the visual artifact entirely and restores full compliance with the frozen architecture.
