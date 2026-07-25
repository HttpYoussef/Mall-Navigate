package com.example.mallar.ui.destination

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mallar.data.AppPreferences
import com.example.mallar.data.Place
import com.example.mallar.ui.home.*

@Composable
fun DestinationCategoryScreen(
    categoryKey: String,
    categoryLabel: String,
    viewModel: DestinationViewModel = viewModel(),
    onBackClick: () -> Unit,
    onDestinationSelected: (Place) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkMode by AppPreferences.isDarkMode.collectAsState()

    val colorScheme = rememberHomeColorScheme(isDarkMode)
    val currentBg       = colorScheme.bg
    val currentTextMain = colorScheme.textMain
    val currentTextSub  = colorScheme.textSub
    val currentAccent   = colorScheme.accent
    val currentBorder   = colorScheme.border

    var searchFocused by remember { mutableStateOf(value = false) }
    val searchFocusRequester = remember { FocusRequester() }
    
    LaunchedEffect(categoryKey) {
        viewModel.initCategory(categoryKey)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Unified Scrollable Page ──────────────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                // ── Editorial Category Header ────────────────────────────────
                item(key = "header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 28.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDarkMode) GlassCardBg.copy(alpha = 0.6f) else Color.White)
                                    .border(BorderStroke(1.2.dp, currentBorder), RoundedCornerShape(16.dp))
                                    .clickable { onBackClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = currentTextMain, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(20.dp))
                            Column {
                                Text(
                                    text = categoryLabel,
                                    color = currentTextMain,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 32.sp,
                                    letterSpacing = (-1).sp
                                )
                                Text(
                                    text = "${uiState.displayedPlaces.size} Stores",
                                    color = currentAccent,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                // ── Internal Category Search Bar ────────────────────────────
                item(key = "search") {
                    GlowingSearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = "Search in $categoryLabel",
                        isFocused = searchFocused,
                        onFocusChange = { searchFocused = it },
                        focusRequester = searchFocusRequester,
                        currentAccent = currentAccent,
                        isDarkMode = isDarkMode,
                        currentTextMain = currentTextMain,
                        currentTextSub = currentTextSub,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }

                // ── Collection Section Title ─────────────────────────────────
                item(key = "list_title") {
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = if (uiState.searchQuery.isNotBlank()) "RESULTS IN $categoryLabel" else "ALL IN $categoryLabel",
                        color = currentTextMain,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(20.dp))
                }

                // ── Category Results List ────────────────────────────────────
                if (uiState.displayedPlaces.isEmpty() && !uiState.isLoading) {
                    item { EmptyState(currentTextSub) }
                } else {
                    itemsIndexed(uiState.displayedPlaces, key = { _, place -> "cat_${place.id}" }) { _, place ->
                        RefinedStoreRow(
                            place = place,
                            currentTextMain = currentTextMain,
                            currentTextSub = currentTextSub,
                            currentAccent = currentAccent,
                            onClick = { onDestinationSelected(place) },
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        }
    }
}
