package com.example.mallar.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import com.example.mallar.data.AppPreferences
import com.example.mallar.data.Voucher
import com.example.mallar.data.VoucherRepository
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size as CoilSize

// ─────────────────────────────────────────────────────────────────────────────
// Offers & Vouchers Screen
//
// Data: VoucherRepository.loadPlaceholderVouchers() — STATIC placeholder data,
// deliberately kept swap-ready (see Voucher.kt) so this screen doesn't need to
// change when a real backend is connected later.
//
// Design tokens (DeepNavyBg/GlassCardBg/CyanGlow/etc.) are the same `internal`
// tokens CategoryScreen.kt already reuses from Homescreen.kt — no duplicated
// color system.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun OffersScreen(
    onBackClick: () -> Unit,
    onVoucherClick: (voucherId: String) -> Unit
) {
    val isDarkMode by AppPreferences.isDarkMode.collectAsState()

    val currentBg       = if (isDarkMode) DeepNavyBg else LightBg
    val currentCardBg   = if (isDarkMode) GlassCardBg.copy(alpha = 0.5f) else LightCardBg
    val currentTextMain = if (isDarkMode) Color.White else Color(0xFF1A1A2E)
    val currentTextSub  = if (isDarkMode) MutedTextSubDark else MutedTextSubLight
    val currentAccent   = if (isDarkMode) CyanGlow else LightTealAccent
    val currentBorder   = if (isDarkMode) Color.White.copy(0.08f) else Color.Black.copy(0.06f)

    val allVouchers = remember { VoucherRepository.loadPlaceholderVouchers() }
    var searchQuery by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }

    val displayedVouchers by remember {
        derivedStateOf {
            val q = searchQuery.trim()
            allVouchers
                .filter { selectedCategory == "All" || it.category.equals(selectedCategory, ignoreCase = true) }
                .filter {
                    q.isBlank() ||
                        it.storeBrand.contains(q, ignoreCase = true) ||
                        it.discountTitle.contains(q, ignoreCase = true)
                }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { -it / 8 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
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
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = currentTextMain,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = "Offers & Vouchers",
                        color = currentTextMain,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Save more on your favorite stores",
                        color = currentTextSub,
                        fontSize = 14.sp
                    )
                }
            }

            // ── Pinned search bar ────────────────────────────────────────────
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(260, delayMillis = 60)) + slideInVertically(tween(260, delayMillis = 60)) { -it / 8 }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(52.dp)
                        .shadow(6.dp, RoundedCornerShape(26.dp), clip = false)
                        .background(if (isDarkMode) GlassCardBg else Color.White, RoundedCornerShape(26.dp))
                        .border(
                            BorderStroke(
                                if (searchFocused) 1.5.dp else 1.dp,
                                if (searchFocused) currentAccent else currentBorder
                            ),
                            RoundedCornerShape(26.dp)
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (searchFocused) currentAccent else currentTextSub,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 15.sp,
                            color = currentTextMain,
                            fontWeight = FontWeight.Normal
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { searchFocused = it.isFocused },
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search offers or stores",
                                    color = currentTextSub,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(26.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = currentTextSub,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Filter chips ──────────────────────────────────────────────────
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(260, delayMillis = 100)) + slideInVertically(tween(260, delayMillis = 100)) { -it / 8 }
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(VoucherRepository.categories) { cat ->
                        OfferFilterChip(
                            label = cat,
                            selected = selectedCategory == cat,
                            isDarkMode = isDarkMode,
                            currentAccent = currentAccent,
                            currentTextSub = currentTextSub,
                            currentBorder = currentBorder,
                            onClick = { selectedCategory = cat }
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Voucher list ──────────────────────────────────────────────────
            if (displayedVouchers.isEmpty()) {
                OffersEmptyState(currentTextMain = currentTextMain, currentTextSub = currentTextSub, isDarkMode = isDarkMode)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    itemsIndexed(displayedVouchers, key = { _, v -> v.id }) { index, voucher ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(240, delayMillis = (index * 40).coerceAtMost(280))) +
                                slideInVertically(tween(240, delayMillis = (index * 40).coerceAtMost(280))) { it / 8 }
                        ) {
                            Column {
                                VoucherCard(
                                    voucher = voucher,
                                    isDarkMode = isDarkMode,
                                    currentCardBg = currentCardBg,
                                    currentTextMain = currentTextMain,
                                    currentTextSub = currentTextSub,
                                    currentAccent = currentAccent,
                                    onClick = { onVoucherClick(voucher.id) }
                                )
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }
                    item(key = "bottom_spacer") { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun OfferFilterChip(
    label: String,
    selected: Boolean,
    isDarkMode: Boolean,
    currentAccent: Color,
    currentTextSub: Color,
    currentBorder: Color,
    onClick: () -> Unit
) {
    val bg = if (selected) currentAccent else (if (isDarkMode) GlassCardBg.copy(alpha = 0.6f) else Color.White)
    val contentColor = if (selected) (if (isDarkMode) DeepNavyBg else Color.White) else currentTextSub
    val borderColor = if (selected) Color.Transparent else currentBorder

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "offer_filter_chip_press")

    Box(
        modifier = Modifier
            .scale(pressScale)
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(50))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun VoucherCard(
    voucher: Voucher,
    isDarkMode: Boolean,
    currentCardBg: Color,
    currentTextMain: Color,
    currentTextSub: Color,
    currentAccent: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "voucher_card_press"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = currentAccent.copy(alpha = 0.2f), spotColor = currentAccent.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(currentAccent.copy(alpha = if (isDarkMode) 0.10f else 0.06f), currentCardBg)
                )
            )
            .border(BorderStroke(1.dp, Color.White.copy(alpha = if (isDarkMode) 0.08f else 0.5f)), RoundedCornerShape(24.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val request = remember(voucher.logoAssetPath) {
                    ImageRequest.Builder(context)
                        .data("file:///android_asset/${voucher.logoAssetPath}")
                        .allowHardware(false)
                        .size(CoilSize(160, 160))
                        .memoryCacheKey(voucher.logoAssetPath)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = voucher.storeBrand,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(9.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(voucher.storeBrand, color = currentTextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(voucher.discountTitle, color = currentAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = if (isDarkMode) 0.08f else 0.06f))
                    .border(1.dp, currentAccent.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(voucher.floorLabel, color = currentAccent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = voucher.description,
            color = currentTextSub,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = currentTextSub,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(voucher.expirationDate, color = currentTextSub, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(currentAccent)
                .clickable(interactionSource = interactionSource, indication = null) { onClick() }
                .padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Get Voucher",
                color = if (isDarkMode) DeepNavyBg else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = if (isDarkMode) DeepNavyBg else Color.White,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun OffersEmptyState(
    currentTextMain: Color,
    currentTextSub: Color,
    isDarkMode: Boolean
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (isDarkMode) GlassCardBg.copy(alpha = 0.6f) else Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalOffer,
                    contentDescription = null,
                    tint = currentTextSub,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("No offers found", color = currentTextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Try a different search or category", color = currentTextSub, fontSize = 13.sp)
        }
    }
}
