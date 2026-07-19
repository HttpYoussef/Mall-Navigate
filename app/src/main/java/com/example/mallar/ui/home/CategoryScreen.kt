package com.example.mallar.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import com.example.mallar.data.AppPreferences
import com.example.mallar.data.FavoritesManager
import com.example.mallar.data.Place
import com.example.mallar.data.PlaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// Category Screen
//
// Reuses, unchanged:
//   • PlaceRepository.load() / PlaceRepository.matchesCategory() — same data
//     source and filter rule Home already uses for its category chips.
//   • StoreRow — the same store-row component Home's Search/Favorites use.
//   • DestinationConfirmSheet — the same confirm-before-navigate sheet Home
//     uses, so tapping a store here behaves identically to tapping one on Home.
//   • Color tokens (DeepNavyBg/GlassCardBg/CyanGlow/etc.) — the same design
//     tokens Home is built from, widened from `private` to `internal` in
//     Homescreen.kt so they can be shared here without duplication.
//
// New in this file: only the icon/subtitle lookup for the header (pure
// presentation, not business logic) and the screen layout itself.
// ─────────────────────────────────────────────────────────────────────────────

private data class CategoryMeta(val icon: ImageVector, val subtitle: String)

/** Presentational only — mirrors the category set already defined in HomeScreen. */
private fun categoryMetaFor(categoryKey: String): CategoryMeta = when (categoryKey.lowercase()) {
    "fashion"               -> CategoryMeta(Icons.Default.Checkroom, "Trendy outfits and everyday wear")
    "dining"                -> CategoryMeta(Icons.Default.Restaurant, "Restaurants, cafés, and quick bites")
    "perfumes& cosmetics"   -> CategoryMeta(Icons.Default.Spa, "Fragrances and beauty essentials")
    "beauty"                -> CategoryMeta(Icons.Default.Face, "Skincare, haircare, and wellness")
    else                    -> CategoryMeta(Icons.Default.GridView, "Every store in the mall, all in one place")
}

@Composable
fun CategoryScreen(
    categoryKey: String,
    categoryLabel: String,
    onBackClick: () -> Unit,
    onDestinationSelected: (Place) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isDarkMode by AppPreferences.isDarkMode.collectAsState()
    val favoriteIds by FavoritesManager.favorites.collectAsState()

    // "all" is a routing-safe stand-in for the "All Stores" chip's blank
    // category key (an empty path segment is unreliable in Navigation Compose).
    // Normalized back to blank here, before it ever reaches PlaceRepository.
    val effectiveCategoryKey = remember(categoryKey) { if (categoryKey.equals("all", ignoreCase = true)) "" else categoryKey }

    // Same dynamic color mapping HomeScreen uses — same tokens, same rules.
    val currentBg       = if (isDarkMode) DeepNavyBg else LightBg
    val currentCardBg   = if (isDarkMode) GlassCardBg.copy(alpha = 0.5f) else LightCardBg
    val currentTextMain = if (isDarkMode) Color.White else Color(0xFF1A1A2E)
    val currentTextSub  = if (isDarkMode) MutedTextSubDark else MutedTextSubLight
    val currentAccent   = if (isDarkMode) CyanGlow else LightTealAccent
    val currentBorder   = if (isDarkMode) Color.White.copy(0.08f) else Color.Black.copy(0.06f)

    var allPlaces     by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searchQuery   by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var pendingPlace  by remember { mutableStateOf<Place?>(null) }
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }

    // Same repository call HomeScreen makes — no duplicated data logic.
    LaunchedEffect(Unit) {
        allPlaces = withContext(Dispatchers.IO) { PlaceRepository.load(context) }
    }

    val categoryPlaces by remember(allPlaces, effectiveCategoryKey) {
        derivedStateOf { allPlaces.filter { PlaceRepository.matchesCategory(it, effectiveCategoryKey) } }
    }
    val displayedPlaces by remember {
        derivedStateOf {
            val q = searchQuery.trim()
            if (q.isBlank()) categoryPlaces
            else categoryPlaces.filter { it.brand.orEmpty().contains(q, ignoreCase = true) }
        }
    }

    val meta = remember(effectiveCategoryKey) { categoryMetaFor(effectiveCategoryKey) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header: back button + large icon + title + subtitle ──────────
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

                    Spacer(Modifier.height(22.dp))

                    // Large glowing icon container — same visual language as the
                    // Start Navigation icon on Home (layered radial glow + ring).
                    Box(
                        modifier = Modifier.size(68.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(listOf(currentAccent.copy(alpha = 0.35f), Color.Transparent)),
                                    CircleShape
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .border(1.5.dp, currentAccent.copy(alpha = 0.55f), CircleShape)
                        )
                        Icon(
                            imageVector = meta.icon,
                            contentDescription = null,
                            tint = currentAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = categoryLabel,
                        color = currentTextMain,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = meta.subtitle,
                        color = currentTextSub,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            // ── Pinned search bar — stays fixed while the list below scrolls ──
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
                        .background(
                            if (isDarkMode) GlassCardBg else Color.White,
                            RoundedCornerShape(26.dp)
                        )
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
                                    text = "Search in $categoryLabel",
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

            Spacer(Modifier.height(18.dp))

            // ── Scrollable results list — this category only ─────────────────
            if (displayedPlaces.isEmpty()) {
                CategoryEmptyState(
                    hasQuery = searchQuery.isNotBlank(),
                    currentTextMain = currentTextMain,
                    currentTextSub = currentTextSub,
                    isDarkMode = isDarkMode
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(displayedPlaces, key = { _, place -> place.id }) { index, place ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(220, delayMillis = (index * 30).coerceAtMost(240))) +
                                slideInVertically(tween(220, delayMillis = (index * 30).coerceAtMost(240))) { it / 8 }
                        ) {
                            Column {
                                StoreRow(
                                    place = place,
                                    isSaved = favoriteIds.contains(place.id),
                                    isDarkMode = isDarkMode,
                                    currentCardBg = currentCardBg,
                                    currentTextMain = currentTextMain,
                                    currentTextSub = currentTextSub,
                                    currentAccent = currentAccent,
                                    currentBorder = currentBorder,
                                    onClick = {
                                        focusManager.clearFocus()
                                        pendingPlace = place
                                    },
                                    onToggleSaved = { FavoritesManager.toggleFavorite(place.id) },
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                                Spacer(Modifier.height(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── Destination confirm sheet — identical flow to Home ───────────────
        val confirmPlace = pendingPlace
        if (confirmPlace != null) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { pendingPlace = null },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnClickOutside = true
                )
            ) {
                Box(Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(200)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                                .clickable { pendingPlace = null }
                        )
                    }
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(220)) + slideInVertically(
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                )
                            ) { it / 2 }
                        ) {
                            DestinationConfirmSheet(
                                place = confirmPlace,
                                isDarkMode = isDarkMode,
                                currentCardBg = currentCardBg,
                                currentTextMain = currentTextMain,
                                currentTextSub = currentTextSub,
                                currentAccent = currentAccent,
                                onStartNavigation = {
                                    val place = confirmPlace
                                    pendingPlace = null
                                    onDestinationSelected(place)
                                },
                                onCancel = { pendingPlace = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryEmptyState(
    hasQuery: Boolean,
    currentTextMain: Color,
    currentTextSub: Color,
    isDarkMode: Boolean
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (isDarkMode) GlassCardBg.copy(alpha = 0.6f) else Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasQuery) Icons.Default.SearchOff else Icons.Default.Storefront,
                    contentDescription = null,
                    tint = currentTextSub,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (hasQuery) "No matches" else "No stores yet",
                color = currentTextMain,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (hasQuery) "Try a different search term" else "Check back soon for stores in this category",
                color = currentTextSub,
                fontSize = 13.sp
            )
        }
    }
}
