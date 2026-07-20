/**
 * LEGACY IMPLEMENTATION
 *
 * This screen represents the original camera navigation
 * implementation used before UnifiedNavigationScreen
 * became the primary navigation experience.
 *
 * It is kept only for historical reference and possible
 * future comparison.
 *
 * Current production implementation:
 * UnifiedNavigationScreen
 */




package com.example.mallar.ui.navigation.legacy

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.navigation.SensorFusionManager
import com.example.mallar.navigation.StepTracker
import com.example.mallar.overlay.*

// ── Design tokens ─────────────────────────────────────────────────────────────
private val OverlayBlue       = Color(0xFF1E64FF)
private val OverlayBlueDark   = Color(0xFF0A3DBF)
private val OverlayGreen      = Color(0xFF1ECC64)
private val OverlayAmber      = Color(0xFFFFA726)
private val OverlayRed        = Color(0xFFEF5350)
private val OverlaySurface    = Color(0xFF0A0F1E)
private val OverlayCardBg     = Color(0xFF121829)

private const val DISPLAY_SCALE = 0.05f   // px → metres
private const val M_PER_MIN     = 80f

// ─────────────────────────────────────────────────────────────────────────────
/**
 * CameraNavigationScreen
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The new hybrid pseudo-AR navigation screen that replaces ARSceneView with:
 *   1. CameraX back-camera preview (stable, no ARCore overhead)
 *   2. CameraOverlayView (Canvas rendering of projected navigation dots)
 *   3. OverlayNavigationEngine (position tracking + screen-space projection)
 *   4. Full sensor fusion (SensorFusionManager + StepTracker)
 *
 * LAYOUT STRUCTURE
 * ────────────────
 *   ┌──────────────────────────────────────────┐
 *   │  CameraX PreviewView (full-screen)        │
 *   │  ┌────────────────────────────────────┐   │
 *   │  │  CameraOverlayView (transparent)   │   │
 *   │  │   • projected path dots            │   │
 *   │  │   • destination beacon             │   │
 *   │  │   • turn direction card (top)      │   │
 *   │  │   • user position dot (bottom)     │   │
 *   │  └────────────────────────────────────┘   │
 *   │  [Compose HUD overlays — Compose layer]   │
 *   │   • Back / Map mode toggle (top)          │
 *   │   • Destination card (top-right)          │
 *   │   • Distance / ETA card (bottom)          │
 *   │   • Rerouting banner                      │
 *   └──────────────────────────────────────────┘
 */
@Composable
fun CameraNavigationScreen(
    onBackClick: () -> Unit,
    onSwitchToMapMode: () -> Unit,
    viewModel: CameraNavigationViewModel = viewModel()
) {
    val state          by viewModel.uiState.collectAsState()
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── Hold references to the overlay engine and camera manager ─────────────
    val overlayEngineRef = remember { mutableStateOf<OverlayNavigationEngine?>(null) }
    val cameraManager: CameraOverlayManager = remember { CameraOverlayManager(context) }
    val overlayViewRef   = remember { mutableStateOf<CameraOverlayView?>(null) }

    // ── Sensor fusion — provides stable heading ───────────────────────────────
    DisposableEffect(Unit) {
        val fusion = SensorFusionManager(context)
        fusion.onHeadingChanged = { azimuthDeg, _ ->
            overlayEngineRef.value?.onHeadingUpdated(azimuthDeg)
            viewModel.updateHeading(azimuthDeg)
        }
        fusion.start()
        onDispose { fusion.stop() }
    }

    // ── Step tracker — drives dead-reckoning position ─────────────────────────
    DisposableEffect(Unit) {
        val step = StepTracker(context)
        step.onStep = { totalSteps, strideLength, _ ->
            overlayEngineRef.value?.onStep(totalSteps, strideLength)
            viewModel.onStep(totalSteps)
        }
        step.start()
        onDispose { step.stop() }
    }

    // ── Root layout ──────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(OverlaySurface)) {

        // ── 1. CameraX preview ────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    cameraManager.startCamera(lifecycleOwner, surfaceProvider)
                }
            },
            onRelease = { cameraManager.release() },
            modifier  = Modifier.fillMaxSize()
        )

        // ── 2. Navigation overlay Canvas ──────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                CameraOverlayView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    overlayViewRef.value = this

                    // Initialize engine once we know screen size
                    post {
                        val graph = MallGraphRepository.loadedGraph ?: return@post
                        val pathNodes = state.pathNodes
                        if (pathNodes.size < 2) return@post

                        val engine = OverlayNavigationEngine(
                            mallGraph    = graph,
                            initialPath  = pathNodes,
                            pxPerMetre   = 20f
                        )
                        engine.setScreenSize(width.toFloat(), height.toFloat(), fovDeg = 68f)
                        engine.onStateChanged = { navState ->
                            // Update overlay view (must run on UI thread)
                            post {
                                updateNavigation(
                                    points    = navState.projectedPoints,
                                    turn      = navState.turnInfo,
                                    distM     = navState.remainingDistancePx * DISPLAY_SCALE,
                                    destName  = state.destinationName,
                                    navigating = true
                                )
                            }
                            viewModel.onNavStateUpdated(navState)
                        }
                        engine.onRerouteNeeded = {
                            viewModel.triggerReroute { newPath ->
                                engine.updatePath(newPath)
                            }
                        }
                        engine.onArrived = {
                            viewModel.onArrived()
                        }
                        engine.onWaypointReached = { idx, node ->
                            viewModel.onWaypointReached(idx, node)
                        }
                        engine.initialize()
                        overlayEngineRef.value = engine
                    }
                }
            },
            update = { view ->
                // Overlay view is self-animating; explicit invalidation handled by engine
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── 3. Compose HUD overlay ────────────────────────────────────────────

        // Top bar
        CameraNavTopBar(
            destinationName = state.destinationName,
            distanceM       = state.remainingDistanceM,
            walkMinutes     = state.walkMinutes,
            isRerouting     = state.isRerouting,
            isOnPath        = state.isOnPath,
            onBackClick     = onBackClick,
            onSwitchToMap   = onSwitchToMapMode,
            modifier        = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
        )

        // Rerouting banner
        AnimatedVisibility(
            visible  = state.isRerouting,
            modifier = Modifier.align(Alignment.Center),
            enter    = fadeIn() + scaleIn(),
            exit     = fadeOut() + scaleOut()
        ) {
            ReroutingBanner()
        }

        // Arrival card
        AnimatedVisibility(
            visible  = state.isArrived,
            modifier = Modifier.align(Alignment.Center),
            enter    = fadeIn() + scaleIn(spring(Spring.DampingRatioMediumBouncy)),
            exit     = fadeOut() + scaleOut()
        ) {
            ArrivalCard(
                destinationName = state.destinationName,
                onDone          = onBackClick
            )
        }

        // Bottom distance / ETA bar
        AnimatedVisibility(
            visible  = !state.isArrived,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut()
        ) {
            CameraNavBottomBar(
                distanceM   = state.remainingDistanceM,
                walkMinutes = state.walkMinutes,
                modifier    = Modifier.navigationBarsPadding()
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.release()
            overlayEngineRef.value = null
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CameraNavTopBar(
    destinationName: String,
    distanceM: Int,
    walkMinutes: Int,
    isRerouting: Boolean,
    isOnPath: Boolean,
    onBackClick: () -> Unit,
    onSwitchToMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier             = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment    = Alignment.CenterVertically
        ) {
            // Back button
            NavIconButton(
                icon      = Icons.AutoMirrored.Filled.ArrowBack,
                tint      = Color.White,
                bgColor   = Color.Black.copy(alpha = 0.55f),
                onClick   = onBackClick
            )

            // Destination chip (centre)
            if (destinationName.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                listOf(OverlayBlue.copy(alpha = 0.9f), OverlayBlueDark.copy(alpha = 0.9f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            destinationName,
                            color      = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 15.sp,
                            maxLines   = 1
                        )
                    }
                }
            }

            // Map mode toggle
            NavIconButton(
                icon    = Icons.Default.Map,
                tint    = Color.White,
                bgColor = OverlayBlue.copy(alpha = 0.75f),
                onClick = onSwitchToMap
            )
        }

        // Off-path warning
        AnimatedVisibility(visible = !isOnPath && !isRerouting) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OverlayAmber.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Off path — recalculating…", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun NavIconButton(
    icon: ImageVector,
    tint: Color,
    bgColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ReroutingBanner() {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "reroute_scale"
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(OverlayBlue.copy(alpha = 0.95f))
            .padding(horizontal = 28.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                color    = Color.White,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Recalculating route…",
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp
            )
        }
    }
}

@Composable
private fun ArrivalCard(destinationName: String, onDone: () -> Unit) {
    Card(
        shape     = RoundedCornerShape(24.dp),
        colors    = CardDefaults.cardColors(containerColor = OverlayCardBg),
        elevation = CardDefaults.cardElevation(12.dp),
        modifier  = Modifier.padding(horizontal = 32.dp)
    ) {
        Column(
            modifier            = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎉", fontSize = 52.sp)
            Spacer(Modifier.height(12.dp))
            Text("You Arrived!", color = OverlayGreen, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Spacer(Modifier.height(6.dp))
            if (destinationName.isNotBlank()) {
                Text(destinationName, color = Color.White, fontSize = 16.sp)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onDone,
                shape   = RoundedCornerShape(16.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = OverlayBlue),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun CameraNavBottomBar(
    distanceM: Int,
    walkMinutes: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(20.dp))
                .background(OverlayCardBg.copy(alpha = 0.9f))
                .padding(horizontal = 28.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            BottomStatItem(
                icon   = Icons.Default.Straighten,
                label  = if (distanceM < 1000) "${distanceM}m" else "${"%.1f".format(distanceM / 1000f)}km",
                color  = OverlayBlue
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )
            BottomStatItem(
                icon   = Icons.Default.AccessTime,
                label  = "${walkMinutes.coerceAtLeast(1)} min",
                color  = OverlayGreen
            )
        }
    }
}

@Composable
private fun BottomStatItem(
    icon: ImageVector,
    label: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}
