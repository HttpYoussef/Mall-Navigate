package com.example.mallar.ui.localization

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mallar.data.LandmarkDetection
import com.example.mallar.data.LocalizationResult
import com.example.mallar.data.LocalizationTier
import com.example.mallar.data.Place
import com.example.mallar.data.tier
import com.example.mallar.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// LocalizationConfirmScreen
// ─────────────────────────────────────────────────────────────────────────────
//
// Human Confirmation Layer for the multi-landmark localization pipeline.
//
// Shows after LocalizationEngine.estimatePose() returns a result with
// confidence in the MEDIUM tier (0.45 – 0.74). HIGH confidence (≥ 0.75)
// auto-confirms after a brief auto-accept banner. LOW confidence redirects
// straight back to re-scan.
//
// UX flow:
//   HIGH   → Auto-accept banner (2 s) → onConfirmed()
//   MEDIUM → Show this screen → user confirms the best-match store → onConfirmed()
//   LOW    → Show this screen with warning → user can re-scan → onRescan()
//
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LocalizationConfirmScreen(
    result: LocalizationResult,
    onConfirmed: (LandmarkDetection) -> Unit,
    onRescan: () -> Unit,
    onDismiss: () -> Unit
) {
    val tier = result.tier

    // Auto-accept for HIGH confidence
    if (tier == LocalizationTier.HIGH && result.detections.isNotEmpty()) {
        AutoAcceptBanner(
            detection  = result.detections.first(),
            confidence = result.confidence,
            onAccepted = { onConfirmed(result.detections.first()) },
            onRescan   = onRescan
        )
        return
    }

    // detections are assumed sorted by confidence descending
    val bestDetection = result.detections.firstOrNull()

    // Full confirmation dialog for MEDIUM / LOW
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss)  // tap outside to dismiss
    ) {
        // Bottom sheet style card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clickable { /* consume click so it doesn't dismiss */ }
                .shadow(24.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = White
        ) {
            Column(modifier = Modifier.navigationBarsPadding()) {

                // ── Drag handle ──────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(TextSecondary.copy(alpha = 0.3f))
                    )
                }

                // ── Header ───────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick     = onDismiss,
                        modifier    = Modifier.size(36.dp),
                        shape       = CircleShape,
                        color       = SurfaceLight
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint     = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text       = "Where are you standing?",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 18.sp,
                            color      = TextPrimary
                        )
                        Text(
                            text     = result.confidenceReason,
                            fontSize = 12.sp,
                            color    = TextSecondary
                        )
                    }
                }

                // ── Confidence badge ─────────────────────────────────────────
                ConfidenceBadge(tier = tier, confidence = result.confidence)

                Spacer(Modifier.height(8.dp))

                // ── Question label ───────────────────────────────────────────
                Text(
                    text       = if (bestDetection == null)
                        "No store detected — please try again"
                    else
                        "We detected this store. Is this where you are?",
                    fontSize   = 14.sp,
                    color      = TextSecondary,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    textAlign  = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                // ── Best-match landmark card ─────────────────────────────────
                if (bestDetection == null) {
                    EmptyDetectionState()
                } else {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        LandmarkCard(
                            detection = bestDetection,
                            rank      = 1,
                            isTop     = true,
                            onClick   = { onConfirmed(bestDetection) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── "None of these" / Re-scan button ─────────────────────────
                OutlinedButton(
                    onClick   = onRescan,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 16.dp),
                    shape     = RoundedCornerShape(26.dp),
                    border    = androidx.compose.foundation.BorderStroke(1.5.dp, Teal)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Teal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "None of these — scan again",
                        color      = Teal,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 14.sp
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Auto-accept banner (HIGH confidence)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AutoAcceptBanner(
    detection:  LandmarkDetection,
    confidence: Float,
    onAccepted: () -> Unit,
    onRescan:   () -> Unit
) {
    var countdown by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        repeat(3) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
        onAccepted()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(16.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = White
        ) {
            Column(
                modifier            = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // High confidence icon
                Box(
                    modifier          = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(listOf(
                                Color(0xFF00C853).copy(alpha = 0.15f),
                                Color(0xFF00C853).copy(alpha = 0.05f)
                            ))
                        ),
                    contentAlignment  = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint     = Color(0xFF00C853),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    "Location Confirmed!",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 20.sp,
                    color      = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "High confidence: ${"%.0f".format(confidence * 100)}%",
                    fontSize = 13.sp,
                    color    = Color(0xFF00C853),
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(14.dp))

                // Store logo + name
                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier             = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceLight)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    val logoPath = detection.place?.logo
                    if (logoPath != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("file:///android_asset/$logoPath").crossfade(true).build(),
                            contentDescription = detection.brand,
                            contentScale       = ContentScale.Fit,
                            modifier           = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column {
                        Text(
                            text       = "You are near",
                            fontSize   = 12.sp,
                            color      = TextSecondary
                        )
                        Text(
                            text       = detection.brand,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 18.sp,
                            color      = TextPrimary
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    "Starting navigation in $countdown…",
                    fontSize = 13.sp,
                    color    = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick  = onRescan,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape    = RoundedCornerShape(23.dp),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, TextSecondary.copy(alpha = 0.4f))
                ) {
                    Text(
                        "Wrong location — scan again",
                        fontSize = 13.sp,
                        color    = TextSecondary
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Landmark card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LandmarkCard(
    detection: LandmarkDetection,
    rank:      Int,
    isTop:     Boolean,
    onClick:   () -> Unit
) {
    val borderColor = if (isTop) Teal.copy(alpha = 0.6f) else Color.Transparent
    val bgColor     = if (isTop) Teal.copy(alpha = 0.04f) else White

    Surface(
        modifier    = Modifier
            .fillMaxWidth()
            .shadow(if (isTop) 8.dp else 3.dp, RoundedCornerShape(18.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable { onClick() },
        shape       = RoundedCornerShape(18.dp),
        color       = bgColor
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            Box(
                modifier         = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isTop) Teal else TextSecondary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = "#$rank",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = if (isTop) White else TextSecondary
                )
            }

            Spacer(Modifier.width(12.dp))

            // Logo
            val logoPath = detection.place?.logo
            Surface(
                modifier = Modifier.size(52.dp),
                shape    = RoundedCornerShape(12.dp),
                color    = SurfaceLight
            ) {
                if (logoPath != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("file:///android_asset/$logoPath").crossfade(true).build(),
                        contentDescription = detection.brand,
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .padding(6.dp)
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Filled.Store, null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Brand name + similarity
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = detection.brand,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp,
                    color      = TextPrimary
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Similarity bar
                    SimilarityBar(similarity = detection.similarity)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text     = "${"%.0f".format(detection.similarity * 100)}% match",
                        fontSize = 11.sp,
                        color    = TextSecondary
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Tap here indicator
            Surface(
                modifier = Modifier.size(36.dp),
                shape    = CircleShape,
                color    = Teal.copy(alpha = if (isTop) 0.12f else 0.07f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Select",
                        tint     = Teal,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Confidence badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConfidenceBadge(tier: LocalizationTier, confidence: Float) {
    val (color, label, icon) = when (tier) {
        LocalizationTier.HIGH   -> Triple(Color(0xFF00C853), "High Confidence", Icons.Filled.CheckCircle)
        LocalizationTier.MEDIUM -> Triple(Color(0xFFFFA000), "Medium Confidence", Icons.Filled.Info)
        LocalizationTier.LOW    -> Triple(Color(0xFFE53935), "Low Confidence — Re-scan Recommended", Icons.Filled.Warning)
    }

    Row(
        modifier             = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "$label (${"%.0f".format(confidence * 100)}%)",
            fontSize   = 12.sp,
            color      = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Similarity bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SimilarityBar(similarity: Float) {
    val barColor = when {
        similarity >= 0.7f -> Color(0xFF00C853)
        similarity >= 0.5f -> Color(0xFFFFA000)
        else               -> Color(0xFFE53935)
    }
    Box(
        modifier = Modifier
            .width(60.dp)
            .height(5.dp)
            .clip(CircleShape)
            .background(TextSecondary.copy(alpha = 0.12f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(similarity.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(barColor)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyDetectionState() {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.SearchOff,
            contentDescription = null,
            tint     = TextSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "No stores were recognised in the camera frame.\nPlease point the camera at a store sign and try again.",
            fontSize  = 13.sp,
            color     = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}