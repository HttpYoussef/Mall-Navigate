package com.example.mallar.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.data.Place
import com.example.mallar.ui.components.StoreLogo
import java.util.Locale

// ── Design Specification Tokens ──────────────────────────────────────────────

internal val DeepNavyBg      = Color(0xFF06131A)
internal val GlassCardBg     = Color(0xFF0D1E26)
internal val DesignPurple    = Color(0xFF9D5CFF) // Specification accent
internal val MutedTextSubDark = Color(0xFF8BA3AD)

internal val ParkingPurple      = Color(0xFF8B7CF6)
internal val ParkingPurpleDeep  = Color(0xFF4C3FD9)
internal val CyanGlow           = Color(0xFF19D3E6)
internal val FavoriteHeartRed   = Color(0xFFEF476F)

internal val LightBg          = Color(0xFFF7F9FA)
internal val LightCardBg      = Color(0xFFFFFFFF)
internal val MutedTextSubLight = Color(0xFF888EA8)

internal data class HomeColorScheme(
    val bg: Color,
    val cardBg: Color,
    val textMain: Color,
    val textSub: Color,
    val accent: Color,
    val border: Color
)

@Composable
internal fun rememberHomeColorScheme(isDarkMode: Boolean): HomeColorScheme {
    return remember(isDarkMode) {
        HomeColorScheme(
            bg       = if (isDarkMode) DeepNavyBg else LightBg,
            cardBg   = if (isDarkMode) GlassCardBg.copy(alpha = 0.45f) else LightCardBg,
            textMain = if (isDarkMode) Color.White else Color(0xFF1A1A2E),
            textSub  = if (isDarkMode) MutedTextSubDark else MutedTextSubLight,
            accent   = DesignPurple,
            border   = if (isDarkMode) Color.White.copy(0.08f) else Color.Black.copy(0.05f)
        )
    }
}

// ── Helper Utilities ─────────────────────────────────────────────────────────

@Composable
internal fun rememberPlaceMetadata(place: Place): Pair<String, String> {
    return remember(place.id) {
        val level = if (place.floor == 1) "Level 1" else "Level 2"
        // Mock distance derived from coordinate hash
        val dist = (place.brand.hashCode().coerceAtLeast(0) % 20 * 10 + 60)
        Pair(level, "${dist}m")
    }
}

// ── Design Spec Components ───────────────────────────────────────────────────

@Composable
internal fun GlowingSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search for stores, places...",
    isFocused: Boolean = false,
    onFocusChange: (Boolean) -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    currentAccent: Color = DesignPurple,
    isDarkMode: Boolean = true,
    currentTextMain: Color = Color.White,
    currentTextSub: Color = MutedTextSubDark,
    onClick: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "search_glow_infinite")
    val idleGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_glow"
    )

    val searchElevation by animateDpAsState(if (isFocused) 16.dp else 8.dp, label = "search_elevation")
    val searchGlow by animateFloatAsState(if (isFocused) 0.55f else idleGlowAlpha, label = "search_glow")
    val searchBorderWidth by animateDpAsState(if (isFocused) 2.2.dp else 1.2.dp, label = "search_border")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .shadow(
                elevation = searchElevation,
                shape = RoundedCornerShape(24.dp),
                ambientColor = currentAccent.copy(alpha = searchGlow),
                spotColor = currentAccent.copy(alpha = searchGlow)
            )
            .background(
                if (isDarkMode) GlassCardBg.copy(alpha = 0.9f) else Color.White,
                RoundedCornerShape(24.dp)
            )
            .border(
                BorderStroke(
                    searchBorderWidth,
                    if (isFocused) currentAccent else currentAccent.copy(alpha = idleGlowAlpha + 0.1f)
                ),
                RoundedCornerShape(24.dp)
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = if (isFocused) currentAccent else currentTextSub,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(16.dp))
            if (onClick != null) {
                Text(
                    text = query.ifBlank { placeholder },
                    color = currentTextSub,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 18.sp,
                        color = currentTextMain,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { onFocusChange(it.isFocused) },
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = currentTextSub,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        inner()
                    }
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = currentTextSub, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun DestinationCategoryCard(
    category: Category,
    isDarkMode: Boolean,
    currentAccent: Color,
    currentTextMain: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "cat_card_press")

    Column(
        modifier = Modifier
            .width(86.dp)
            .scale(pressScale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = currentAccent.copy(alpha = 0.15f),
                    spotColor = currentAccent.copy(alpha = 0.15f)
                )
                .background(
                    if (isDarkMode) GlassCardBg.copy(alpha = 0.7f) else Color.White,
                    RoundedCornerShape(22.dp)
                )
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(currentAccent.copy(alpha = 0.12f), CircleShape)
            )
            
            when (val ic = category.icon) {
                is ImageVector -> Icon(ic, null, tint = currentAccent, modifier = Modifier.size(28.dp))
                is Painter -> Icon(ic, null, tint = currentAccent, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = category.label,
            color = currentTextMain,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun PopularStoreCard(
    place: Place,
    isDarkMode: Boolean,
    currentAccent: Color,
    currentTextMain: Color,
    currentTextSub: Color,
    onClick: () -> Unit
) {
    val (level, dist) = rememberPlaceMetadata(place)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "pop_card_press")

    Column(
        modifier = Modifier
            .width(136.dp)
            .scale(pressScale)
            .shadow(6.dp, RoundedCornerShape(24.dp))
            .background(
                if (isDarkMode) GlassCardBg.copy(alpha = 0.8f) else Color.White,
                RoundedCornerShape(24.dp)
            )
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(24.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Color.White, CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            StoreLogo(place = place, modifier = Modifier.fillMaxSize(), contentPadding = 10.dp)
        }
        
        Spacer(Modifier.height(14.dp))
        
        Text(
            text = place.brand,
            color = currentTextMain,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(Modifier.height(4.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, null, tint = currentAccent, modifier = Modifier.size(10.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = "$level  •  $dist",
                color = currentTextSub,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
internal fun RefinedStoreRow(
    place: Place,
    currentTextMain: Color,
    currentTextSub: Color,
    currentAccent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (level, dist) = rememberPlaceMetadata(place)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Color.White, CircleShape)
                .border(BorderStroke(1.dp, Color.Black.copy(0.04f)), CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            StoreLogo(place = place, modifier = Modifier.fillMaxSize(), contentPadding = 9.dp)
        }
        
        Spacer(Modifier.width(18.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.brand,
                color = currentTextMain,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$level  •  $dist",
                color = currentTextSub,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = currentTextSub.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    onSeeAll: (() -> Unit)?,
    currentTextMain: Color,
    currentAccent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            color = currentTextMain,
            letterSpacing = 0.5.sp
        )
        if (onSeeAll != null) {
            Text(
                text = "See all",
                color = currentAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onSeeAll() }
            )
        }
    }
}

// ── Shared Data Models ────────────────────────────────────────────────────────

internal data class Category(
    val label: String,
    val icon: Any,
    val categoryKey: String
)

// ── Keeping legacy components for compatibility until fully refactored ────────

@Composable
internal fun CategoryChip(
    category: Category,
    selected: Boolean,
    isDarkMode: Boolean,
    currentAccent: Color,
    currentTextSub: Color,
    currentBorder: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    
    val bg by animateColorAsState(
        if (selected) currentAccent else (if (isDarkMode) GlassCardBg.copy(alpha = 0.5f) else Color.White),
        animationSpec = tween(300), label = "chip_bg"
    )
    val contentColor by animateColorAsState(
        if (selected) (if (isDarkMode) DeepNavyBg else Color.White) else currentTextSub,
        animationSpec = tween(300), label = "chip_content"
    )
    val borderColor by animateColorAsState(
        if (selected) currentAccent.copy(alpha = 0.6f) else currentBorder,
        animationSpec = tween(300), label = "chip_border"
    )
    val haloAlpha by animateFloatAsState(if (selected) 0.15f else 0f, animationSpec = tween(400), label = "chip_halo")
    val pressScale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "category_chip_press")

    Box(
        modifier = Modifier
            .scale(pressScale)
            .shadow(
                elevation = if (selected) 10.dp else 0.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = currentAccent.copy(alpha = haloAlpha),
                spotColor = currentAccent.copy(alpha = haloAlpha),
                clip = false
            )
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(BorderStroke(1.5.dp, borderColor), RoundedCornerShape(18.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (val ic = category.icon) {
                is ImageVector -> Icon(ic, null, tint = contentColor, modifier = Modifier.size(18.dp))
                is Painter -> Icon(ic, null, tint = contentColor, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = category.label,
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun StoreRow(
    place: Place,
    isSaved: Boolean,
    isDarkMode: Boolean,
    currentCardBg: Color,
    currentTextMain: Color,
    currentTextSub: Color,
    currentAccent: Color,
    currentBorder: Color,
    onClick: () -> Unit,
    onToggleSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "store_row_press")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale)
            .shadow(if (pressed) 12.dp else 4.dp, RoundedCornerShape(26.dp), clip = false)
            .background(currentCardBg, RoundedCornerShape(26.dp))
            .border(BorderStroke(1.2.dp, currentBorder), RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color.White, CircleShape)
                .border(BorderStroke(1.dp, Color.Black.copy(0.06f)), CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            StoreLogo(place = place, modifier = Modifier.fillMaxSize(), contentPadding = 10.dp)
        }

        Spacer(Modifier.width(22.dp))

        Column(modifier = Modifier.weight(weight = 1f)) {
            Text(
                text = place.brand,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = currentTextMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = (-0.4).sp
            )
            Spacer(Modifier.height(4.dp))
            val categoryLabel = place.category?.take(20) ?: "Store"
            val floorLabel = if (place.floor == 1) "Ground Floor" else "First Floor"
            Text(
                text = "$categoryLabel · $floorLabel",
                fontSize = 14.sp,
                color = currentTextSub,
                fontWeight = FontWeight.Medium
            )
        }

        IconButton(onClick = onToggleSaved, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Save place",
                tint = if (isSaved) FavoriteHeartRed else currentTextSub,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(48.dp)
                .background(if (isDarkMode) Color(0xFF102A35).copy(alpha = 0.5f) else currentAccent.copy(alpha = 0.08f), CircleShape)
                .border(BorderStroke(1.2.dp, currentAccent.copy(alpha = 0.2f)), CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ChevronRight, null, tint = currentAccent, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
internal fun EmptyState(currentTextSub: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = currentTextSub.copy(alpha = 0.25f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "No results found",
                color = currentTextSub,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Try a different keyword",
                color = currentTextSub.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
internal fun DestinationConfirmSheet(
    place: Place,
    isDarkMode: Boolean,
    currentCardBg: Color,
    currentTextMain: Color,
    currentTextSub: Color,
    currentAccent: Color,
    onStartNavigation: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isDarkMode) GlassCardBg else Color.White,
                RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            )
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 44.dp, height = 5.dp)
                .background(currentTextSub.copy(alpha = 0.25f), RoundedCornerShape(2.5.dp))
        )
        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.White, CircleShape)
                    .border(BorderStroke(1.5.dp, Color.Black.copy(0.06f)), CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                StoreLogo(place = place, modifier = Modifier.fillMaxSize(), contentPadding = 10.dp)
            }
            Spacer(Modifier.width(22.dp))
            Column {
                Text(
                    text = place.brand,
                    color = currentTextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    letterSpacing = (-0.6).sp
                )
                val floorLabel = if (place.floor == 1) "Ground Floor" else "First Floor"
                Text(
                    text = "${place.category ?: "Store"} · $floorLabel",
                    color = currentTextSub,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = "To get accurate directions, we'll scan a nearby store sign to find your exact spot.",
            color = currentTextSub,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onStartNavigation,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = currentAccent,
                contentColor = Color.White
            )
        ) {
            Text(text = "Start Navigation", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Cancel",
            color = currentTextSub,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { onCancel() }
                .padding(12.dp)
        )
    }
}
