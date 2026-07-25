package com.example.mallar.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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

// ── Shared Design Tokens ─────────────────────────────────────────────────────

internal val DeepNavyBg      = Color(0xFF06131A)
internal val GlassCardBg     = Color(0xFF0D1E26)
internal val CyanGlow        = Color(0xFF19D3E6)
internal val MutedTextSubDark = Color(0xFF8BA3AD)

internal val ParkingPurple      = Color(0xFF8B7CF6)
internal val ParkingPurpleDeep  = Color(0xFF4C3FD9)
internal val FavoriteHeartRed   = Color(0xFFEF476F)

internal val LightBg          = Color(0xFFF7F9FA)
internal val LightCardBg      = Color(0xFFFFFFFF)
internal val LightTealAccent  = Color(0xFF258799)
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
            cardBg   = if (isDarkMode) GlassCardBg.copy(alpha = 0.5f) else LightCardBg,
            textMain = if (isDarkMode) Color.White else Color(0xFF1A1A2E),
            textSub  = if (isDarkMode) MutedTextSubDark else MutedTextSubLight,
            accent   = if (isDarkMode) CyanGlow else LightTealAccent,
            border   = if (isDarkMode) Color.White.copy(0.08f) else Color.Black.copy(0.08f)
        )
    }
}

// ── Shared Data Models ────────────────────────────────────────────────────────

internal data class Category(
    val label: String,
    val icon: Any,
    val categoryKey: String
)

// ── Shared UI Components ─────────────────────────────────────────────────────

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
    val bg = if (selected) {
        currentAccent
    } else {
        if (isDarkMode) GlassCardBg.copy(alpha = 0.6f) else Color.White
    }
    val contentColor = if (selected) {
        if (isDarkMode) DeepNavyBg else Color.White
    } else {
        currentTextSub
    }
    val borderColor = if (selected) Color.Transparent else currentBorder

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "category_chip_press")

    Row(
        modifier = Modifier
            .scale(pressScale)
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(50))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (val ic = category.icon) {
            is ImageVector -> Icon(
                imageVector = ic,
                contentDescription = category.label,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            is Painter -> Icon(
                painter = ic,
                contentDescription = category.label,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = category.label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
    val isVerified = remember(place.brand) {
        place.brand.equals("Zara", ignoreCase = true) ||
        place.brand.equals("Nike", ignoreCase = true) ||
        place.brand.equals("Starbucks", ignoreCase = true) ||
        place.brand.equals("Tissot", ignoreCase = true)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(currentCardBg, RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, currentBorder), RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Color.White, CircleShape)
                .border(
                    BorderStroke(
                        1.dp,
                        if (isDarkMode) Color.White.copy(0.12f) else Color.Black.copy(0.06f)
                    ),
                    CircleShape
                )
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            StoreLogo(
                place = place,
                modifier = Modifier.fillMaxSize(),
                contentPadding = 8.dp,
                fallbackTextSize = 16.sp,
                maxDecodeSizePx = 256
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = place.brand.orEmpty(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = currentTextMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isVerified) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified Store",
                        tint = currentAccent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val categoryLabel = remember(place.category) {
                val cat = place.category.orEmpty()
                when {
                    cat.contains("fashion", ignoreCase = true) -> "Fashion"
                    cat.contains("dining", ignoreCase = true) -> "Food & Beverages"
                    cat.contains("perfumes", ignoreCase = true) -> "Perfumes & Cosmetics"
                    cat.contains("accessories", ignoreCase = true) -> "Accessories"
                    cat.contains("beauty", ignoreCase = true) -> "Beauty & Wellness"
                    else -> cat.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            }
            val floorLabel = remember(place.floor) {
                when (place.floor) {
                    1 -> "Ground Floor"
                    2 -> "First Floor"
                    else -> "Inside Mall"
                }
            }
            Text(
                text = "$categoryLabel · $floorLabel",
                fontSize = 12.sp,
                color = currentTextSub
            )
        }

        IconButton(onClick = onToggleSaved, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Save place",
                tint = if (isSaved) FavoriteHeartRed else currentTextSub,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(40.dp)
                .background(if (isDarkMode) Color(0xFF102A35).copy(alpha = 0.5f) else currentAccent.copy(alpha = 0.08f), CircleShape)
                .border(
                    BorderStroke(
                        1.dp,
                        if (isDarkMode) CyanGlow.copy(alpha = 0.2f) else currentAccent.copy(alpha = 0.15f)
                    ),
                    CircleShape
                )
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = currentAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    onSeeAll: (() -> Unit)?,
    currentTextMain: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = currentTextMain
        )
        if (onSeeAll != null) {
            Row(
                modifier = Modifier.clickable { onSeeAll() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "View all",
                    color = currentTextMain.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = currentTextMain.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
internal fun EmptyState(currentTextSub: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = currentTextSub.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "No stores found",
                color = currentTextSub,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
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
    val categoryLabel = remember(place.category) {
        val cat = place.category.orEmpty()
        when {
            cat.contains("fashion", ignoreCase = true) -> "Fashion"
            cat.contains("dining", ignoreCase = true) -> "Food & Beverages"
            cat.contains("perfumes", ignoreCase = true) -> "Perfumes & Cosmetics"
            cat.contains("accessories", ignoreCase = true) -> "Accessories"
            cat.contains("beauty", ignoreCase = true) -> "Beauty & Wellness"
            else -> cat.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
    val floorLabel = remember(place.floor) {
        when (place.floor) {
            1 -> "Ground Floor"
            2 -> "First Floor"
            else -> "Inside Mall"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isDarkMode) GlassCardBg else Color.White,
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 40.dp, height = 4.dp)
                .background(currentTextSub.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.White, CircleShape)
                    .border(
                        BorderStroke(1.dp, if (isDarkMode) Color.White.copy(0.12f) else Color.Black.copy(0.06f)),
                        CircleShape
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                StoreLogo(
                    place = place,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = 7.dp,
                    fallbackTextSize = 18.sp,
                    maxDecodeSizePx = 200
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = place.brand.orEmpty(),
                    color = currentTextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "$categoryLabel · $floorLabel",
                    color = currentTextSub,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "To get accurate directions, we'll scan a nearby store sign to find your exact spot.",
            color = currentTextSub,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onStartNavigation,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = currentAccent,
                contentColor = if (isDarkMode) DeepNavyBg else Color.White
            )
        ) {
            Text(text = "Start Navigation", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Cancel",
            color = currentTextSub,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { onCancel() }
                .padding(8.dp)
        )
    }
}
