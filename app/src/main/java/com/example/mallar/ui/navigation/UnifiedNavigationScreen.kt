package com.example.mallar.ui.navigation

import android.graphics.BitmapFactory
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.asImageBitmap
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
import com.example.mallar.overlay.*
import com.example.mallar.voice.NavigationSessionVoiceCoordinator
import com.example.mallar.voice.NavigationVoiceFab
import com.example.mallar.voice.VoiceAssistantManager
import com.example.mallar.voice.VoiceAssistantOverlay
import com.example.mallar.voice.VoiceAssistantStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*
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

    val cameraManager = remember { CameraOverlayManager(context) }
    val previewRef    = remember { mutableStateOf<PreviewView?>(null) }
    val isCameraMode  = state.mode == NavMode.CAMERA

    val suppressPoseFlatUntilMs = remember { AtomicLong(0L) }
    LaunchedEffect(isCameraMode) {
        if (isCameraMode) {
            suppressPoseFlatUntilMs.set(System.currentTimeMillis() + 2800L)
        }
    }

    val voiceAssistant = remember { VoiceAssistantManager(context) }
    val voiceUiState by voiceAssistant.uiState.collectAsState()
    val sessionVoice = remember { NavigationSessionVoiceCoordinator(voiceAssistant.ttsManager) }

    var voiceMuted by remember { mutableStateOf(false) }
    LaunchedEffect(voiceMuted) {
        voiceAssistant.ttsManager.isEnabled = !voiceMuted
        if (voiceMuted) voiceAssistant.ttsManager.stop()
    }

    DisposableEffect(Unit) {
        voiceAssistant.initialize()
        voiceAssistant.graphProvider = { MallGraphRepository.loadedGraph }
        voiceAssistant.navStateProvider = {
            runCatching { NavigationSessionManager.instance.sessionState.value }.getOrNull()
        }
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
    DisposableEffect(Unit) {
        val s = StepTracker(context)
        s.onStep = { total, _ -> viewModel.onStep(total) }
        s.start()
        onDispose { s.stop() }
    }

    // ── Camera lifecycle ──────────────────────────────────────────────────────
    LaunchedEffect(isCameraMode, previewRef.value) {
        if (!isCameraMode) {
            cameraManager.stopCamera()
            return@LaunchedEffect
        }
        val pv = previewRef.value ?: return@LaunchedEffect
        yield()
        delay(48)
        cameraManager.startCamera(
            lifecycleOwner        = lifecycle,
            surfaceProvider       = pv.surfaceProvider,
            relocalizationEnabled = true
        )
    }
    DisposableEffect(Unit) { onDispose { cameraManager.release() } }

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
        // LAYER 1: Camera preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewRef.value = this
                }
            },
            update  = { pv -> pv.alpha = if (isCameraMode) 1f else 0.02f },
            modifier = Modifier.fillMaxSize()
        )

        // LAYER 2: AR overlay
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

        // LAYER 5: Orientation phase — shown once, before normal navigation,
        // so the user faces the correct direction before it begins.
        if (orientationState.active) {
            OrientationOverlay(orientationState = orientationState)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Orientation Phase Overlay
// ─────────────────────────────────────────────────────────────────────────────
// Text-only guidance today. It renders purely from OrientationUiState
// (targetBearingDeg / headingDeg / direction), so a future AR arrow can be
// swapped in here — reading the same state — without touching the navigation
// flow that drives it (UnifiedNavigationViewModel / OrientationManager).
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
                Spacer(Modifier.height(6.dp))
                Text(
                    text = orientationState.instruction.ifBlank { "Hold still..." },
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Navigation starts once you're facing the right way",
                    color = Color.White.copy(0.6f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloorTransitionSheet(
    transition: com.example.mallar.navigation.FloorTransitionHelper.PathFloorTransition,
    onContinue: () -> Unit,
) {
    val typeLabel = when (transition.transitionType.lowercase()) {
        "elevator" -> androidx.compose.ui.res.stringResource(com.example.mallar.R.string.transition_elevator)
        else -> androidx.compose.ui.res.stringResource(com.example.mallar.R.string.transition_escalator)
    }
    val message = androidx.compose.ui.res.stringResource(
        com.example.mallar.R.string.floor_transition_message,
        typeLabel,
        transition.toFloor
    )

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { false },
    )
    ModalBottomSheet(
        onDismissRequest = { },
        sheetState = sheetState,
        containerColor = NavCard,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.floor_transition_title),
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
                    androidx.compose.ui.res.stringResource(com.example.mallar.R.string.floor_transition_continue),
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LAYER 3: Calibrated map layer
// ─────────────────────────────────────────────────────────────────────────────

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
                BitmapFactory.decodeStream(it).asImageBitmap()
            }
        }.getOrNull()
    }

    val floorPathNodes = remember(state.pathNodes, displayFloor) {
        state.pathNodes.filter { it.floor == displayFloor }
    }

    if (mapBitmap == null) {
        Box(modifier.graphicsLayer(alpha = alpha).background(NavSurface), Alignment.Center) {
            Text("Map unavailable", color = Color.White.copy(0.4f), fontSize = 13.sp)
        }
        return
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    var mapScale  by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var userPanning by remember { mutableStateOf(false) }

    val pathSignature = remember(state.pathNodes) {
        if (state.pathNodes.isEmpty()) 0
        else state.pathNodes.size * 31 + state.pathNodes.first().id.hashCode() + state.pathNodes.last().id.hashCode()
    }

    // FIX: replaced return@remember (invalid label on non-inline lambda) with
    // plain if/else expressions.
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

    // Route-focused initial zoom + pan
    LaunchedEffect(fitScale, fitOffset, canvasSize, pathSignature) {
        if (userPanning || canvasSize.width == 0 || canvasSize.height == 0) return@LaunchedEffect
        val nodes = state.pathNodes
        if (nodes.size < 2) {
            mapScale = fitScale
            panOffset = fitOffset
            return@LaunchedEffect
        }
        val pad = 120f
        val minX = nodes.minOf { it.x }.toFloat()
        val maxX = nodes.maxOf { it.x }.toFloat()
        val minY = nodes.minOf { it.y }.toFloat()
        val maxY = nodes.maxOf { it.y }.toFloat()
        val bw = (maxX - minX).coerceAtLeast(200f)
        val bh = (maxY - minY).coerceAtLeast(200f)
        val routeFit = minOf(
            canvasSize.width.toFloat() / (bw + pad * 2f),
            canvasSize.height.toFloat() / (bh + pad * 2f)
        )
        val smartScale = routeFit.coerceIn(fitScale * 1.38f, fitScale * 4.25f)
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        mapScale = smartScale
        panOffset = Offset(
            x = canvasSize.width / 2f - cx * smartScale,
            y = canvasSize.height * 0.58f - cy * smartScale
        )
    }

    fun toCv(x: Float, y: Float) = Offset(x * mapScale + panOffset.x, y * mapScale + panOffset.y)
    fun toCv(x: Double, y: Double) = toCv(x.toFloat(), y.toFloat())

    val targetPanX = canvasSize.width  / 2f - state.userMapX * mapScale
    val targetPanY = canvasSize.height * 0.60f - state.userMapY * mapScale

    val animPanX by animateFloatAsState(
        targetValue = if (!userPanning) targetPanX else panOffset.x,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "px"
    )
    val animPanY by animateFloatAsState(
        targetValue = if (!userPanning) targetPanY else panOffset.y,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "py"
    )

    LaunchedEffect(animPanX, animPanY) {
        if (!userPanning) panOffset = Offset(animPanX, animPanY)
    }

    LaunchedEffect(userPanning) {
        if (userPanning) { delay(3000); userPanning = false }
    }

    Canvas(
        modifier = modifier
            .graphicsLayer(alpha = alpha)
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    userPanning = true
                    mapScale  = (mapScale * zoom).coerceIn(fitScale * 0.8f, fitScale * 6f)
                    panOffset = Offset(panOffset.x + pan.x, panOffset.y + pan.y)
                }
            }
    ) {
        // 1. Map image
        withTransform({
            translate(panOffset.x, panOffset.y)
            scale(mapScale, mapScale, Offset.Zero)
        }) {
            mapBitmap?.let { drawImage(it) }
        }

        // 2. A* route path (current floor segment only)
        val nodes = floorPathNodes
        if (nodes.size >= 2) {
            val fullNodes = state.pathNodes
            val seg = state.segmentIdx
            val segNode = fullNodes.getOrNull(seg)
            val floorSegIdx = if (segNode != null && segNode.floor == displayFloor) {
                nodes.indexOf(segNode).coerceAtLeast(0)
            } else {
                nodes.indexOfLast { fullNodes.indexOf(it) <= seg }.coerceAtLeast(0)
            }

            val routePath = Path().apply {
                val s = toCv(nodes[0].x, nodes[0].y)
                moveTo(s.x, s.y)
                for (i in 1 until nodes.size) {
                    val p = toCv(nodes[i].x, nodes[i].y)
                    lineTo(p.x, p.y)
                }
            }
            val lineW = (5f * mapScale).coerceIn(3f, 14f)

            drawPath(routePath, PathShadow, style = Stroke(lineW * 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(routePath, PathColor, style = Stroke(lineW, cap = StrokeCap.Round, join = StrokeJoin.Round))

            if (floorSegIdx > 0) {
                val walked = Path().apply {
                    val s = toCv(nodes[0].x, nodes[0].y)
                    moveTo(s.x, s.y)
                    for (i in 1..floorSegIdx.coerceAtMost(nodes.size - 1)) {
                        val p = toCv(nodes[i].x, nodes[i].y)
                        lineTo(p.x, p.y)
                    }
                }
                drawPath(walked, WalkedColor, style = Stroke(lineW, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            val r = (9f * mapScale).coerceIn(5f, 20f)

            val sp = toCv(nodes.first().x, nodes.first().y)
            drawCircle(Color.White,  r * 1.6f, sp)
            drawCircle(StartGreen,   r, sp)
            drawCircle(Color.White,  r * 0.38f, sp)

            val ep = toCv(nodes.last().x, nodes.last().y)
            drawCircle(Color.White,  r * 1.8f, ep)
            drawCircle(EndRed,       r * 1.2f, ep)
            drawCircle(Color.White,  r * 0.42f, ep)

            nodes.getOrNull(floorSegIdx + 1)?.let { nxt ->
                val np = toCv(nxt.x, nxt.y)
                drawCircle(PathColor.copy(0.22f), r * 1.8f, np)
                drawCircle(PathColor, r * 0.7f, np)
            }
        }

        // 3. User dot
        val up = toCv(state.userMapX.toDouble(), state.userMapY.toDouble())
        val dr = (9f * mapScale).coerceIn(5f, 18f)

        drawCircle(UserBlue.copy(0.12f), dr * 3.2f, up)
        drawCircle(Color.White,          dr * 1.55f, up)
        drawCircle(UserBlue,             dr, up)

        // Heading arrow
        val hr = Math.toRadians(state.headingDeg.toDouble())
        val al = dr * 2.8f
        drawLine(
            color       = UserBlue,
            start       = up,
            end         = Offset(up.x + (sin(hr) * al).toFloat(), up.y - (cos(hr) * al).toFloat()),
            strokeWidth = (2.5f * mapScale).coerceIn(1.5f, 5f),
            cap         = StrokeCap.Round
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LAYER 4: HUD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NavigationHud(
    state: NavSessionState,
    isCameraMode: Boolean,
    onBackClick: () -> Unit,
    onModeSelected: (NavigationModeSelection) -> Unit
) {
    // Top bar
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBackClick)
            Spacer(Modifier.width(10.dp))

            if (state.destinationName.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .weight(1f).height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.horizontalGradient(listOf(NavBlue.copy(0.88f), NavBlueDark.copy(0.88f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(state.destinationName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                    }
                }
            } else Spacer(Modifier.weight(1f))
        }

        // Mode badge (only visible in Auto selection mode to indicate active orientation mode)
        if (state.modeSelection == NavigationModeSelection.AUTO) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(0.5f))
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (isCameraMode) NavBlue else NavGreen))
                Spacer(Modifier.width(5.dp))
                Text(if (isCameraMode) "AR Mode" else "Map Mode", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    // Centre banners
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        AnimatedVisibility(state.isRerouting, enter = fadeIn() + scaleIn(spring(Spring.DampingRatioMediumBouncy)), exit = fadeOut() + scaleOut()) {
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).background(NavBlue.copy(0.95f)).padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Recalculating…", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        val showDrift = state.relocReason != null || state.driftLevel == DriftMonitor.DriftLevel.CRITICAL
        AnimatedVisibility(showDrift, enter = fadeIn() + slideInVertically { -40 }, exit = fadeOut() + slideOutVertically { -40 }) {
            val sev = state.relocReason != null || state.driftLevel == DriftMonitor.DriftLevel.CRITICAL
            Row(
                Modifier.padding(top = 120.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (sev) EndRed.copy(0.95f) else NavAmber.copy(0.95f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (sev) Icons.Default.Warning else Icons.Default.Info, null, tint = Color.White, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(state.relocReason ?: if (sev) "Tracking lost — scan a store logo" else "Accuracy low", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }

        AnimatedVisibility(state.isArrived, enter = fadeIn() + scaleIn(spring(Spring.DampingRatioMediumBouncy)), exit = fadeOut()) {
            ArrivalCard(state.destinationName)
        }

        AnimatedVisibility(state.waypointMessage != null, enter = fadeIn() + slideInVertically { -30 }, exit = fadeOut() + slideOutVertically { -30 }) {
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(NavGreen.copy(0.93f)).padding(horizontal = 18.dp, vertical = 9.dp)) {
                Text(state.waypointMessage ?: "", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }

    // Bottom bar
    Box(Modifier.fillMaxSize(), Alignment.BottomCenter) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 14.dp).navigationBarsPadding()
        ) {
            // Segmented mode selector
            AnimatedVisibility(
                visible = !state.isArrived,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                NavigationModeSelector(
                    selectedMode = state.modeSelection,
                    onModeSelected = onModeSelected,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            AnimatedVisibility(
                visible = !state.isArrived,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)).background(NavCard)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Distance", color = Color.White.copy(0.55f), fontSize = 11.sp)
                        Text(
                            if (state.remainingDistanceM < 1000) "${state.remainingDistanceM} m"
                            else "${"%.1f".format(state.remainingDistanceM / 1000f)} km",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp
                        )
                    }
                    Box(Modifier.width(1.dp).height(34.dp).background(Color.White.copy(0.12f)))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ETA", color = Color.White.copy(0.55f), fontSize = 11.sp)
                        Text("${state.walkMinutes.coerceAtLeast(1)} min", color = NavGreen, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isCameraMode) Icons.Default.Videocam else Icons.Default.Map,
                            contentDescription = null,
                            tint = Color(0xFF00BCD4).copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = when (state.modeSelection) {
                                NavigationModeSelection.AUTO -> "Auto"
                                NavigationModeSelection.AR -> "AR"
                                NavigationModeSelection.MAP -> "Map"
                            },
                            color = Color.White.copy(0.35f),
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationModeSelector(
    selectedMode: NavigationModeSelection,
    onModeSelected: (NavigationModeSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = NavigationModeSelection.values()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(0.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { mode ->
            val isSelected = selectedMode == mode
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF00BCD4).copy(alpha = 0.25f) else Color.Transparent,
                animationSpec = tween(250),
                label = "bg_color"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF00BCD4) else Color.White.copy(alpha = 0.6f),
                animationSpec = tween(250),
                label = "content_color"
            )
            val borderModifier = if (isSelected) {
                Modifier.border(1.dp, Color(0xFF00BCD4), RoundedCornerShape(20.dp))
            } else {
                Modifier
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(borderModifier)
                    .background(backgroundColor)
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (mode) {
                        NavigationModeSelection.AUTO -> "Auto"
                        NavigationModeSelection.AR -> "AR"
                        NavigationModeSelection.MAP -> "Map"
                    },
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ArrivalCard(destName: String) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = NavCard),
        elevation = CardDefaults.cardElevation(16.dp),
        modifier = Modifier.padding(horizontal = 28.dp)
    ) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CheckCircle, "Arrived", tint = NavGreen, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text("You Arrived!", color = NavGreen, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            if (destName.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(destName, color = Color.White, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun HudButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bgColor: Color = Color.Black.copy(0.52f),
    onClick: () -> Unit
) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(bgColor).clickable(onClick = onClick),
        Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LAYER 2: AR Directional Arrow Overlay
// ─────────────────────────────────────────────────────────────────────────────

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

                val smoothTarget = remember { mutableFloatStateOf(targetAngle) }

                LaunchedEffect(targetAngle) {
                    val prev = smoothTarget.floatValue
                    val rawDiff = targetAngle - prev
                    val wrappedDiff = ((rawDiff + 180f) % 360f + 360f) % 360f - 180f
                    smoothTarget.floatValue = prev + wrappedDiff
                }

                val animatedAngle by animateFloatAsState(
                    targetValue = smoothTarget.floatValue,
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
                    // Guidance card
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

                    // Large animated arrow
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

                            // Shadow glow
                            drawPath(
                                path = arrowPath,
                                color = Color.Black.copy(alpha = 0.35f),
                                style = Stroke(width = 20f, join = StrokeJoin.Round)
                            )
                            // White outline
                            drawPath(
                                path = arrowPath,
                                color = Color.White,
                                style = Stroke(width = 10f, join = StrokeJoin.Round)
                            )
                            // Main fill
                            drawPath(path = arrowPath, color = NavBlue)
                        }
                    }
                }
            }
        }
    }
}