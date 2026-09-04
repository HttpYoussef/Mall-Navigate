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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mallar.R
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.data.WesternDigits
import com.example.mallar.data.floorDisplayLabel
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
            active = isCameraMode,
            supervisor = viewModel.driftRecoverySupervisor,
            driftState = viewModel.sessionManager.driftState
        )

        // LAYER 2: Calibrated map
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
                    contentDescription = if (voiceMuted) stringResource(R.string.nav_voice_unmute) else stringResource(R.string.nav_voice_mute),
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
                    text = stringResource(R.string.nav_get_oriented),
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
    val toFloorLabel = floorDisplayLabel(transition.toFloor)
    val message = if (transition.toFloor > transition.fromFloor) {
        stringResource(R.string.nav_floor_up, toFloorLabel)
    } else {
        stringResource(R.string.nav_floor_down, toFloorLabel)
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
                text = stringResource(R.string.nav_floor_change),
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
                    stringResource(R.string.nav_continue_on_floor, toFloorLabel),
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
            Text(stringResource(R.string.map_unavailable), color = Color.White.copy(0.4f), fontSize = 13.sp)
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
                    HudTab(stringResource(R.string.map_mode), !isCameraMode) { onModeSelected(NavigationModeSelection.MAP) }
                    HudTab(stringResource(R.string.ar_mode), isCameraMode) { onModeSelected(NavigationModeSelection.AR) }
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
                    Text(
                        stringResource(R.string.distance_meters, WesternDigits.format(state.remainingDistanceM)),
                        color = PathColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        pluralStringResource(
                            R.plurals.nav_walk_minutes,
                            state.walkMinutes,
                            WesternDigits.format(state.walkMinutes)
                        ),
                        color = Color.White.copy(0.6f)
                    )
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

