package com.example.mallar.ui.destination

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mallar.R
import com.example.mallar.data.AppPreferences
import com.example.mallar.data.FavoritesManager
import com.example.mallar.data.Place
import com.example.mallar.ui.home.*

@Composable
fun DestinationSelectionScreen(
    viewModel: DestinationViewModel = viewModel(),
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCategoryClick: (key: String, label: String) -> Unit,
    onDestinationSelected: (Place) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkMode by AppPreferences.isDarkMode.collectAsState()

    val colorScheme = rememberHomeColorScheme(isDarkMode = isDarkMode)
    val currentBg       = colorScheme.bg
    val currentCardBg   = colorScheme.cardBg
    val currentTextMain = colorScheme.textMain
    val currentTextSub  = colorScheme.textSub
    val currentAccent   = colorScheme.accent

    var pendingPlace by remember { mutableStateOf<Place?>(null) }
    var contentVisible by remember { mutableStateOf(value = false) }
    
    LaunchedEffect(Unit) { 
        contentVisible = true
    }

    val clothesPainter = painterResource(R.drawable.clothes)
    val foodPainter    = painterResource(R.drawable.food)
    val perfumePainter = painterResource(R.drawable.pefume)
    val beauPainter    = painterResource(R.drawable.beauu)

    val categories = remember(clothesPainter, foodPainter, perfumePainter, beauPainter) {
        listOf(
            Category("Food & Dining", Icons.Default.Restaurant, categoryKey = "dining"),
            Category("Fashion",       clothesPainter,           categoryKey = "fashion"),
            Category("Cafés",         foodPainter,              categoryKey = "cafes"),
            Category("Entertainment", Icons.Default.VideogameAsset, categoryKey = "entertainment"),
            Category("Services",      Icons.Default.MoreHoriz,   categoryKey = "services"),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentBg)
    ) {
        // --- Premium Atmospheric Glow ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            currentAccent.copy(alpha = 0.15f),
                            currentAccent.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // ── Unified Vertical Scroll per UX requirement ──────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                // ── Editorial Header ──────────────────────────────────────────
                item(key = "header") {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { -it / 3 }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 28.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Where would\nyou like to go?",
                                    color = currentTextMain,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 34.sp,
                                    lineHeight = 40.sp,
                                    letterSpacing = (-1.2).sp
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Find stores, restaurants and more\ninside the mall.",
                                    color = currentTextSub,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 22.sp
                                )
                            }
                            IconButton(
                                onClick = { /* Notifications */ },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(if (isDarkMode) Color.White.copy(0.05f) else Color.Black.copy(0.05f), androidx.compose.foundation.shape.CircleShape)
                            ) {
                                Icon(Icons.Default.Notifications, null, tint = currentTextMain)
                            }
                        }
                    }
                }

                // ── Glowing Search Bar Entry ──────────────────────────────────
                item(key = "search_entry") {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(600, delayMillis = 150)) + slideInVertically(tween(600, delayMillis = 150)) { it / 4 }
                    ) {
                        GlowingSearchBar(
                            query = "",
                            onQueryChange = {},
                            isFocused = false,
                            onFocusChange = {},
                            focusRequester = remember { FocusRequester() },
                            currentAccent = currentAccent,
                            isDarkMode = isDarkMode,
                            currentTextMain = currentTextMain,
                            currentTextSub = currentTextSub,
                            onClick = onSearchClick,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }
                }

                // ── Browse Categories ─────────────────────────────────────────
                item(key = "categories_section") {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(600, delayMillis = 300)) + slideInVertically(tween(600, delayMillis = 300)) { it / 6 }
                    ) {
                        Column(modifier = Modifier.padding(top = 42.dp)) {
                            SectionHeader(
                                title = "Browse Categories",
                                onSeeAll = { /* View all categories */ },
                                currentTextMain = currentTextMain,
                                currentAccent = currentAccent
                            )
                            Spacer(Modifier.height(20.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                itemsIndexed(categories, key = { _, cat -> cat.label }) { _, cat ->
                                    DestinationCategoryCard(
                                        category = cat,
                                        isDarkMode = isDarkMode,
                                        currentAccent = currentAccent,
                                        currentTextMain = currentTextMain,
                                        onClick = { onCategoryClick(cat.categoryKey, cat.label) }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Popular Destinations ──────────────────────────────────────
                item(key = "popular_section") {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(600, delayMillis = 450)) + slideInVertically(tween(600, delayMillis = 450)) { it / 8 }
                    ) {
                        Column(modifier = Modifier.padding(top = 48.dp)) {
                            SectionHeader(
                                title = "Popular Destinations",
                                onSeeAll = { /* View all popular */ },
                                currentTextMain = currentTextMain,
                                currentAccent = currentAccent
                            )
                            Spacer(Modifier.height(20.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                itemsIndexed(uiState.allPlaces.take(8), key = { _, place -> "pop_${place.id}" }) { _, place ->
                                    PopularStoreCard(
                                        place = place,
                                        isDarkMode = isDarkMode,
                                        currentAccent = currentAccent,
                                        currentTextMain = currentTextMain,
                                        currentTextSub = currentTextSub,
                                        onClick = { pendingPlace = place }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Recently Visited ──────────────────────────────────────────
                item(key = "recent_header") {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(600, delayMillis = 600))
                    ) {
                        Column(modifier = Modifier.padding(top = 48.dp)) {
                            SectionHeader(
                                title = "Recently Visited",
                                onSeeAll = { /* View all recent */ },
                                currentTextMain = currentTextMain,
                                currentAccent = currentAccent
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                val recentPlaces = uiState.allPlaces.filter { it.brand in listOf("Nike", "Sephora", "ZARA") }
                itemsIndexed(recentPlaces, key = { _, place -> "recent_${place.id}" }) { index, place ->
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(500, delayMillis = 650 + (index * 50))) +
                                slideInVertically(tween(500, delayMillis = 650 + (index * 50))) { it / 10 }
                    ) {
                        RefinedStoreRow(
                            place = place,
                            currentTextMain = currentTextMain,
                            currentTextSub = currentTextSub,
                            currentAccent = currentAccent,
                            onClick = { pendingPlace = place },
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        }

        // ── Destination confirmation sheet logic remains intact ───────────────
        val confirmPlace = pendingPlace
        if (confirmPlace != null) {
            Dialog(
                onDismissRequest = { pendingPlace = null },
                properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { pendingPlace = null }
                    )
                    Box(Modifier.align(Alignment.BottomCenter)) {
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
