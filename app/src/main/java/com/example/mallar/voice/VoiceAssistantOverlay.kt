package com.example.mallar.voice

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

// ── Design tokens ─────────────────────────────────────────────────────────────
private val AiBlue        = Color(0xFF00BCD4)
private val AiBlueDeep    = Color(0xFF006064)
private val AiPurple      = Color(0xFF7C4DFF)
private val AiCard        = Color(0xFF0D1B2A)
private val AiSurface     = Color(0xFF112240)
private val AiSuccess     = Color(0xFF00E676)
private val AiError       = Color(0xFFFF5252)
private val AiText        = Color(0xFFE8F4F8)
private val AiTextMuted   = Color(0xFF78909C)

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * VoiceAssistantOverlay
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Full-screen overlay that appears when the user activates the voice assistant.
 * Animates between states: IDLE → LISTENING (waveform) → THINKING → SPEAKING.
 *
 * Design inspired by: Gemini Live / Google Assistant / ChatGPT Voice.
 *
 * @param uiState       Current assistant state (from VoiceAssistantManager)
 * @param onMicTap      Called when the user taps the mic button
 * @param onDismiss     Called when user dismisses the overlay
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Composable
fun VoiceAssistantOverlay(
    uiState: VoiceAssistantUiState,
    onMicTap: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(onClick = {}),  // absorb touches
        contentAlignment = Alignment.BottomCenter
    ) {
        // Dismiss tap area (top half)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.TopCenter)
                .clickable(onClick = onDismiss)
        )

        // Main assistant card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .shadow(24.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(listOf(AiSurface, AiCard))
                )
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status indicator pill
            StatusPill(status = uiState.status)

            Spacer(Modifier.height(24.dp))

            // Central animated visual (mic ring / waveform / thinking dots)
            CentralVisual(
                status = uiState.status,
                onMicTap = onMicTap
            )

            Spacer(Modifier.height(20.dp))

            // Transcript / Reply text area
            TextDisplay(uiState = uiState)

            Spacer(Modifier.height(24.dp))

            // Bottom action row
            BottomActionRow(
                status = uiState.status,
                isArabic = uiState.isArabic,
                onMicTap = onMicTap,
                onDismiss = onDismiss
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Floating Mic Button (shown on LogoScanScreen + NavigationScreen)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Compact floating voice button — pulse animation when active.
 */
@Composable
fun FloatingVoiceButton(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp
) {
    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = if (isActive) 1.18f else 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "micPulse"
    )
    val glowAlpha by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.35f, targetValue = if (isActive) 0.7f else 0.35f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "micGlow"
    )

    Box(
        modifier = modifier.size(size * 1.6f),
        contentAlignment = Alignment.Center
    ) {
        // Glow ring
        Box(
            modifier = Modifier
                .size(size * 1.5f)
                .clip(CircleShape)
                .background(AiBlue.copy(alpha = glowAlpha * pulseScale))
        )
        // Button
        Box(
            modifier = Modifier
                .size(size)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            if (isActive) AiBlue else Color(0xFF1A3550),
                            if (isActive) AiBlueDeep else Color(0xFF0D1B2A)
                        )
                    )
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Mic else Icons.Default.Mic,
                contentDescription = "Voice Assistant",
                tint = if (isActive) Color.White else AiBlue,
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }
}

/** Same as [FloatingVoiceButton] — navigation HUD naming. */
@Composable
fun NavigationVoiceFab(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp
) {
    FloatingVoiceButton(isActive, onClick, modifier, size)
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusPill(status: VoiceAssistantStatus) {
    val (text, color) = when (status) {
        VoiceAssistantStatus.IDLE      -> "Voice Assistant" to AiTextMuted
        VoiceAssistantStatus.LISTENING -> "Listening…" to AiBlue
        VoiceAssistantStatus.THINKING  -> "Thinking…" to AiPurple
        VoiceAssistantStatus.SPEAKING  -> "Speaking" to AiSuccess
        VoiceAssistantStatus.ERROR     -> "Try again" to AiError
    }

    val dotAlpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "dot"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        if (status != VoiceAssistantStatus.IDLE) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = dotAlpha))
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CentralVisual(
    status: VoiceAssistantStatus,
    onMicTap: () -> Unit
) {
    Box(
        modifier = Modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            VoiceAssistantStatus.LISTENING -> WaveformRing()
            VoiceAssistantStatus.THINKING  -> ThinkingOrbs()
            VoiceAssistantStatus.SPEAKING  -> SpeakingWave()
            else -> Unit
        }

        // Central mic button
        val isActive = status == VoiceAssistantStatus.LISTENING
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            if (isActive) AiBlue else Color(0xFF1A3550),
                            if (isActive) AiBlueDeep else Color(0xFF0D1B2A)
                        )
                    )
                )
                .clickable(onClick = onMicTap),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (status == VoiceAssistantStatus.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
private fun WaveformRing() {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "wavePhase"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "waveScale"
    )

    Canvas(modifier = Modifier.size(140.dp)) {
        val bars = 32
        val radius = size.minDimension * 0.42f
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        for (i in 0 until bars) {
            val angle = (i.toFloat() / bars) * 2 * PI.toFloat()
            val amplitude = 0.06f + 0.10f * sin(phase + angle * 3).coerceIn(-1f, 1f).let { (it + 1f) / 2f }
            val outerR = radius * (1f + amplitude) * scale
            val innerR = radius * 0.88f

            val sx = centerX + innerR * cos(angle.toDouble()).toFloat()
            val sy = centerY + innerR * sin(angle.toDouble()).toFloat()
            val ex = centerX + outerR * cos(angle.toDouble()).toFloat()
            val ey = centerY + outerR * sin(angle.toDouble()).toFloat()

            drawLine(
                color = AiBlue.copy(alpha = 0.6f + 0.4f * amplitude),
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ThinkingOrbs() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "orbRotation"
    )

    Canvas(modifier = Modifier.size(140.dp)) {
        val radius = size.minDimension * 0.40f
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val orbCount = 4

        for (i in 0 until orbCount) {
            val angle = Math.toRadians((rotation + i * (360f / orbCount)).toDouble())
            val orbX = centerX + radius * cos(angle).toFloat()
            val orbY = centerY + radius * sin(angle).toFloat()
            val orbScale = 0.5f + 0.5f * ((i.toFloat() / orbCount))

            drawCircle(
                color = AiPurple.copy(alpha = 0.4f + orbScale * 0.6f),
                radius = 10f * orbScale,
                center = Offset(orbX, orbY)
            )
        }
    }
}

@Composable
private fun SpeakingWave() {
    val infiniteTransition = rememberInfiniteTransition(label = "speak")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
        label = "speakPhase"
    )

    Canvas(modifier = Modifier.size(140.dp)) {
        val bars = 5
        val barWidth = 10f
        val maxHeight = size.height * 0.35f
        val spacing = size.width / (bars + 1)
        val centerY = size.height / 2f

        for (i in 0 until bars) {
            val x = spacing * (i + 1)
            val heightFactor = 0.3f + 0.7f * ((sin(phase + i * 0.8f) + 1f) / 2f)
            val barH = maxHeight * heightFactor

            drawLine(
                color = AiSuccess.copy(alpha = 0.7f + 0.3f * heightFactor),
                start = Offset(x, centerY - barH / 2f),
                end   = Offset(x, centerY + barH / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun TextDisplay(uiState: VoiceAssistantUiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Transcript (what user said)
        AnimatedVisibility(uiState.transcript.isNotBlank()) {
            Text(
                text = uiState.transcript,
                color = AiTextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (uiState.transcript.isNotBlank() && uiState.assistantReply.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
        }

        // Assistant reply
        AnimatedVisibility(uiState.assistantReply.isNotBlank()) {
            Text(
                text = uiState.assistantReply,
                color = AiText,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Error message
        AnimatedVisibility(uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage ?: "",
                color = AiError,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Placeholder when nothing to show
        AnimatedVisibility(
            uiState.transcript.isBlank() && uiState.assistantReply.isBlank()
                    && uiState.errorMessage == null
        ) {
            Text(
                text = when (uiState.status) {
                    VoiceAssistantStatus.IDLE      -> "اضغط للتحدث • Tap to speak"
                    VoiceAssistantStatus.LISTENING -> "…"
                    VoiceAssistantStatus.THINKING  -> "…"
                    else -> ""
                },
                color = AiTextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BottomActionRow(
    status: VoiceAssistantStatus,
    isArabic: Boolean,
    onMicTap: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Close button
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, null, tint = AiTextMuted, modifier = Modifier.size(20.dp))
        }

        // Hint text
        Text(
            text = when (status) {
                VoiceAssistantStatus.LISTENING -> "Tap mic to stop"
                VoiceAssistantStatus.SPEAKING  -> "Speaking…"
                VoiceAssistantStatus.THINKING  -> "Processing…"
                else -> "عربي • English"
            },
            color = AiTextMuted,
            fontSize = 12.sp
        )

        // Lang indicator
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(AiBlue.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isArabic) "عربي" else "EN",
                color = AiBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}
