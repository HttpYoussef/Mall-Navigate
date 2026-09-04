package com.example.mallar.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.mallar.R
import com.example.mallar.data.StartupState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// ── Design Tokens ────────────────────────────────────────────────────────────
private val DesignNavy = Color(0xFF06131A)
private val DesignPurple = Color(0xFF9D50FF)
private val DesignCyan = Color(0xFF19D3E6)

/**
 * High-fidelity Splash Screen optimized for performance and stability.
 * Features: Recomposition-free animations, cached path rendering, and stable layout measurements.
 */
@Composable
fun SplashScreen(
    startupState: StartupState,
    onTimeout: () -> Unit,
    onRetry: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    
    // Animation Controllers
    var entrancePlayed by rememberSaveable { mutableStateOf(false) }
    val mainAlpha = remember { Animatable(0f) }
    val pathProgress = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.95f) }
    val glowPulse = remember { Animatable(0.4f) }
    
    // Background Particles (constant, no state needed)
    val particles = remember { List(30) { Particle() } }

    // Sync status bar
    SideEffect {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    // Sequence Logic
    LaunchedEffect(Unit) {
        if (!entrancePlayed) {
            launch { mainAlpha.animateTo(1f, tween(500)) }
            launch {
                delay(200)
                pathProgress.animateTo(1f, tween(1000, easing = EaseOutQuart))
            }
            launch {
                logoScale.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
            }
            launch {
                glowPulse.animateTo(0.8f, infiniteRepeatable(tween(2000), RepeatMode.Reverse))
            }
            delay(1200) // Minimum brand visibility
            entrancePlayed = true
        } else {
            mainAlpha.snapTo(1f)
            pathProgress.snapTo(1f)
            logoScale.snapTo(1f)
        }
    }

    // Hand-off
    LaunchedEffect(startupState, entrancePlayed) {
        if (startupState == StartupState.Success && entrancePlayed) {
            delay(200) // Smooth beat before transition
            onTimeout()
        }
    }

    // Use graphicsLayer for the whole screen to avoid recomposing the tree when mainAlpha changes
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignNavy)
            .graphicsLayer { alpha = mainAlpha.value }
    ) {
        // ── 1. Atmospheric Background Layer ──────────────────────────────────
        AtmosphericLayer(particles)

        // ── 2. Perspective Floor & Path ──────────────────────────────────────
        PerspectiveFloor(progressProvider = { pathProgress.value })

        // ── 3. Top-Left Branding ──────────────────────────────────────────────
        TopLeftMark()

        // ── 4. Center Identity ───────────────────────────────────────────────
        CenterIdentity(
            scaleProvider = { logoScale.value },
            glowProvider = { glowPulse.value }
        )

        // ── 5. Lifecycle Overlay ─────────────────────────────────────────────
        if (startupState == StartupState.Error) {
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp)) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = DesignPurple.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, DesignPurple.copy(alpha = 0.5f))
                ) {
                    Text(stringResource(R.string.splash_retry_connection), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AtmosphericLayer(particles: List<Particle>) {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing)),
        label = "drift"
    )

    // Using drift in draw loop ensures no recompositions at 60fps
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        
        // Base Vignette (Radial gradients can be cached but small ones are cheap)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF0D1E26), DesignNavy),
                center = Offset(w / 2, h / 2),
                radius = w * 1.5f
            )
        )

        // Floating Particles
        particles.forEach { p ->
            val x = (p.x * w + drift * p.speed) % w
            val y = (p.y * h + drift * 0.5f * p.speed) % h
            drawCircle(
                color = Color.White.copy(alpha = p.alpha),
                radius = p.size,
                center = Offset(x, y)
            )
        }

        // Faint Building Wireframes
        val buildingColor = Color.White.copy(alpha = 0.05f)
        drawRect(buildingColor, Offset(w * 0.05f, h * 0.3f), Size(w * 0.15f, h * 0.4f), style = Stroke(1f))
        drawRect(buildingColor, Offset(w * 0.8f, h * 0.25f), Size(w * 0.15f, h * 0.5f), style = Stroke(1f))
    }
}

@Composable
private fun PerspectiveFloor(progressProvider: () -> Float) {
    // Cache the Path and PathMeasure to avoid heavy object creation in the draw loop
    val path = remember { Path() }
    val androidPath = remember { android.graphics.Path() }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val horizonY = h * 0.45f
        val progress = progressProvider()
        
        // Grid Lines
        val gridAlpha = 0.08f
        for (i in 0..12) {
            drawLine(
                color = DesignCyan.copy(alpha = gridAlpha),
                start = Offset(w / 2, horizonY),
                end = Offset((w / 12) * i, h),
                strokeWidth = 1f
            )
        }
        for (i in 1..8) {
            val y = horizonY + (h - horizonY) * (i * i / 64f)
            drawLine(color = DesignPurple.copy(alpha = gridAlpha), start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1f)
        }

        // Glowing Navigation Path (re-calculate only when size changes)
        if (path.isEmpty) {
            path.moveTo(w * 0.8f, h * 0.95f)
            path.cubicTo(w * 0.7f, h * 0.85f, w * 0.9f, h * 0.75f, w * 0.65f, h * 0.7f)
            path.cubicTo(w * 0.4f, h * 0.65f, w * 0.6f, h * 0.55f, w * 0.5f, h * 0.5f)
            androidPath.set(path.asAndroidPath())
        }

        if (progress > 0.01f) {
            val pathMeasure = android.graphics.PathMeasure(androidPath, false)
            val pathLength = pathMeasure.length
            
            // PathEffect must be created in the draw loop if it depends on animated values,
            // but we use progress to drive a DashPathEffect which is standard.
            val drawEffect = PathEffect.dashPathEffect(floatArrayOf(pathLength * progress, pathLength), 0f)
            
            drawPath(path, DesignPurple.copy(alpha = 0.15f), style = Stroke(24f, cap = StrokeCap.Round, pathEffect = drawEffect))
            drawPath(path, DesignPurple.copy(alpha = 0.4f), style = Stroke(10f, cap = StrokeCap.Round, pathEffect = drawEffect))
            drawPath(path, Color.White.copy(alpha = 0.9f), style = Stroke(3f, cap = StrokeCap.Round, pathEffect = drawEffect))
        }
        
        if (progress > 0.8f) {
            drawCircle(DesignPurple, 8f, Offset(w * 0.72f, h * 0.68f))
            drawCircle(DesignPurple.copy(alpha = 0.3f), 20f, Offset(w * 0.72f, h * 0.68f))
        }
    }
}

@Composable
private fun TopLeftMark() {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 40.dp, start = 30.dp)
    ) {
        Box {
            Canvas(
                modifier = Modifier
                    .size(60.dp)
                    .offset(x = if (isRtl) 10.dp else (-10).dp, y = (-10).dp)
            ) {
                val s = size.width
                val p = Path().apply {
                    if (isRtl) {
                        moveTo(0f, 0f)
                        lineTo(s, 0f)
                        lineTo(s, s)
                    } else {
                        moveTo(s, 0f)
                        lineTo(0f, 0f)
                        lineTo(0f, s)
                    }
                }
                drawPath(p, DesignCyan, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                if (isRtl) {
                    drawCircle(DesignCyan, 4f, Offset(0f, 0f))
                    drawCircle(DesignCyan, 4f, Offset(s, s))
                } else {
                    drawCircle(DesignCyan, 4f, Offset(s, 0f))
                    drawCircle(DesignCyan, 4f, Offset(0f, s))
                }
            }

            Column {
                Text(
                    text = stringResource(R.string.splash_navigate),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        shadow = Shadow(DesignCyan.copy(alpha = 0.5f), blurRadius = 20f)
                    )
                )
                Text(
                    text = stringResource(R.string.splash_through_anywhere),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        shadow = Shadow(DesignPurple.copy(alpha = 0.5f), blurRadius = 20f)
                    )
                )
            }
        }
    }
}

@Composable
private fun CenterIdentity(
    scaleProvider: () -> Float,
    glowProvider: () -> Float
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Use a fixed size container with graphicsLayer scale to fix squashing/compression
        Box(
            modifier = Modifier
                .size(240.dp)
                .graphicsLayer {
                    val s = scaleProvider()
                    scaleX = s
                    scaleY = s
                },
            contentAlignment = Alignment.Center
        ) {
            // Background Brand Glow
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = glowProvider() * 0.35f }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(DesignPurple, Color.Transparent)
                        ),
                        CircleShape
                    )
            )
            
            // Logo Image - ratio ensures it never squashes
            Image(
                painter = painterResource(id = R.drawable.logo_main),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.6f),
                contentScale = ContentScale.Fit
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.app_wordmark),
            style = TextStyle(
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
        )
        
        Text(
            text = stringResource(R.string.splash_indoor_navigation),
            style = TextStyle(
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 6.sp
            )
        )
    }
}

private class Particle {
    val x = Random.nextFloat()
    val y = Random.nextFloat()
    val size = Random.nextFloat() * 3f + 1f
    val alpha = Random.nextFloat() * 0.4f + 0.1f
    val speed = Random.nextFloat() * 0.5f + 0.2f
}
