package com.example.mallar.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.data.AppPreferences
import com.example.mallar.data.Place
import com.example.mallar.data.PlaceRepository
import com.example.mallar.data.VoucherRepository
import com.example.mallar.ui.theme.SuccessGreen
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

// ── UX State ────────────────────────────────────────────────────────────────
private enum class RedemptionState {
    IDLE,       // Initial state
    REDEEMING,  // Loading/Spinning
    SUCCESS,    // "✓ Offer Redeemed"
    READY       // "Start Navigation"
}

@Composable
fun VoucherDetailsScreen(
    voucherId: String,
    onBackClick: () -> Unit,
    onDestinationSelected: (Place) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkMode by AppPreferences.isDarkMode.collectAsState()

    val currentBg       = if (isDarkMode) DeepNavyBg else LightBg
    val currentCardBg   = if (isDarkMode) GlassCardBg.copy(alpha = 0.5f) else LightCardBg
    val currentTextMain = if (isDarkMode) Color.White else Color(0xFF1A1A2E)
    val currentTextSub  = if (isDarkMode) MutedTextSubDark else MutedTextSubLight
    val currentAccent   = if (isDarkMode) CyanGlow else LightTealAccent
    val currentBorder   = if (isDarkMode) Color.White.copy(0.08f) else Color.Black.copy(0.06f)

    val voucher = remember(voucherId) {
        VoucherRepository.loadPlaceholderVouchers().firstOrNull { it.id == voucherId }
    }

    var matchedPlace by remember { mutableStateOf<Place?>(null) }
    var contentVisible by remember { mutableStateOf(false) }
    var redemptionState by remember { mutableStateOf(RedemptionState.IDLE) }

    LaunchedEffect(Unit) { contentVisible = true }

    LaunchedEffect(voucher?.storeBrand) {
        val brand = voucher?.storeBrand ?: return@LaunchedEffect
        val places = withContext(Dispatchers.IO) { PlaceRepository.load(context) }
        matchedPlace = places.firstOrNull { it.brand.equals(brand, ignoreCase = true) }
    }

    if (voucher == null) {
        Box(modifier = Modifier.fillMaxSize().background(currentBg), contentAlignment = Alignment.Center) {
            Text("Voucher not found", color = currentTextSub, fontSize = 14.sp)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(currentBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {

                // ── Header ───────────────────────────────────────────────────
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(240)) + slideInVertically(tween(240)) { -it / 8 }
                ) {
                    Box(modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isDarkMode) GlassCardBg.copy(alpha = 0.6f) else Color.White)
                                .border(BorderStroke(1.dp, currentBorder), CircleShape)
                                .clickable { onBackClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = currentTextMain,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // ── Store & Offer Info ──────────────────────────────────────
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(260, delayMillis = 60)) + slideInVertically(tween(260, delayMillis = 60)) { it / 10 }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .shadow(14.dp, CircleShape, ambientColor = currentAccent.copy(alpha = 0.3f), spotColor = currentAccent.copy(alpha = 0.3f))
                                .background(Color.White, CircleShape)
                                .border(BorderStroke(1.dp, Color.Black.copy(alpha = 0.06f)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val request = remember(voucher.logoAssetPath) {
                                ImageRequest.Builder(context)
                                    .data("file:///android_asset/${voucher.logoAssetPath}")
                                    .allowHardware(false)
                                    .size(CoilSize(220, 220))
                                    .memoryCacheKey(voucher.logoAssetPath)
                                    .build()
                            }
                            AsyncImage(
                                model = request,
                                contentDescription = voucher.storeBrand,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(14.dp)
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        Text(voucher.storeBrand, color = currentTextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(voucher.floorLabel, color = currentTextSub, fontSize = 13.sp)

                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(currentAccent.copy(alpha = if (isDarkMode) 0.18f else 0.12f))
                                .border(1.dp, currentAccent.copy(alpha = 0.4f), RoundedCornerShape(50))
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Text(voucher.discountTitle, color = currentAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = voucher.description,
                            color = currentTextSub,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = currentTextSub, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(voucher.expirationDate, color = currentTextSub, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // ── QR Code Panel (Revealed on Redemption) ───────────────────
                AnimatedVisibility(
                    visible = redemptionState == RedemptionState.SUCCESS || redemptionState == RedemptionState.READY,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val qrGlowColor by animateColorAsState(
                            targetValue = if (redemptionState == RedemptionState.SUCCESS) SuccessGreen else currentAccent,
                            animationSpec = tween(360),
                            label = "qr_glow_color"
                        )
                        Box(
                            modifier = Modifier
                                .shadow(16.dp, RoundedCornerShape(28.dp), ambientColor = qrGlowColor.copy(alpha = 0.25f), spotColor = qrGlowColor.copy(alpha = 0.25f))
                                .clip(RoundedCornerShape(28.dp))
                                .background(Brush.verticalGradient(listOf(qrGlowColor.copy(alpha = if (isDarkMode) 0.12f else 0.06f), currentCardBg)))
                                .border(BorderStroke(1.dp, qrGlowColor.copy(alpha = 0.4f)), RoundedCornerShape(28.dp))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier.size(200.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp)
                            ) {
                                QrPlaceholder(seed = voucher.id, modifier = Modifier.fillMaxSize())
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Your voucher has been redeemed successfully. Show this QR code at checkout.",
                                color = SuccessGreen,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // ── Terms & Conditions ────────────────────────────────────────
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(280, delayMillis = 160)) + slideInVertically(tween(280, delayMillis = 160)) { it / 10 }
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        Text("Terms & Conditions", color = currentTextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(text = voucher.terms, color = currentTextSub, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // ── Morphing Primary Action Button ───────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                val buttonColor by animateColorAsState(
                    targetValue = when (redemptionState) {
                        RedemptionState.SUCCESS -> SuccessGreen
                        RedemptionState.READY -> if (isDarkMode) GlassCardBg else currentAccent
                        else -> currentAccent
                    },
                    animationSpec = tween(400),
                    label = "btn_color"
                )

                Button(
                    onClick = {
                        when (redemptionState) {
                            RedemptionState.IDLE -> {
                                scope.launch {
                                    redemptionState = RedemptionState.REDEEMING
                                    delay(500) // Loading phase
                                    redemptionState = RedemptionState.SUCCESS
                                    delay(800) // Success message display phase
                                    redemptionState = RedemptionState.READY
                                }
                            }
                            RedemptionState.READY -> {
                                matchedPlace?.let(onDestinationSelected)
                            }
                            else -> {}
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(20.dp),
                    enabled = redemptionState != RedemptionState.REDEEMING &&
                            (redemptionState != RedemptionState.READY || matchedPlace != null),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = if (redemptionState == RedemptionState.READY && isDarkMode) currentAccent else Color.White,
                        disabledContainerColor = buttonColor.copy(alpha = 0.7f),
                        disabledContentColor = Color.White
                    ),
                    border = if (redemptionState == RedemptionState.READY) BorderStroke(1.5.dp, currentAccent.copy(alpha = 0.5f)) else null
                ) {
                    AnimatedContent(
                        targetState = redemptionState,
                        transitionSpec = { (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut()) },
                        label = "btn_content_anim"
                    ) { state ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (state) {
                                RedemptionState.IDLE -> {
                                    Icon(Icons.Default.Redeem, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text("Redeem Offer Now", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                RedemptionState.REDEEMING -> {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.5.dp)
                                }
                                RedemptionState.SUCCESS -> {
                                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text("Offer Redeemed", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                RedemptionState.READY -> {
                                    Icon(Icons.Default.Navigation, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text("Start Navigation", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QrPlaceholder(seed: String, modifier: Modifier = Modifier) {
    val gridSize = 21
    val random = remember(seed) { Random(seed.hashCode()) }
    val cells = remember(seed) {
        Array(gridSize) { r ->
            BooleanArray(gridSize) { c ->
                val inFinderZone = (r < 7 && c < 7) || (r < 7 && c >= gridSize - 7) || (r >= gridSize - 7 && c < 7)
                if (inFinderZone) false else random.nextBoolean()
            }
        }
    }

    Canvas(modifier = modifier) {
        val cell = size.minDimension / gridSize
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                if (cells[r][c]) {
                    drawRect(color = Color.Black, topLeft = Offset(c * cell, r * cell), size = Size(cell, cell))
                }
            }
        }
        val finderPositions = listOf(Offset(0f, 0f), Offset((gridSize - 7) * cell, 0f), Offset(0f, (gridSize - 7) * cell))
        finderPositions.forEach { origin ->
            drawRect(color = Color.Black, topLeft = origin, size = Size(cell * 7, cell * 7))
            drawRect(color = Color.White, topLeft = origin + Offset(cell, cell), size = Size(cell * 5, cell * 5))
            drawRect(color = Color.Black, topLeft = origin + Offset(cell * 2, cell * 2), size = Size(cell * 3, cell * 3))
        }
    }
}