package com.example.mallar.ui.destination

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    onDestinationSelected: (Place) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkMode by AppPreferences.isDarkMode.collectAsState()
    val favoriteIds by FavoritesManager.favorites.collectAsState()

    val colorScheme = rememberHomeColorScheme(isDarkMode)
    val currentBg       = colorScheme.bg
    val currentCardBg   = colorScheme.cardBg
    val currentTextMain = colorScheme.textMain
    val currentTextSub  = colorScheme.textSub
    val currentAccent   = colorScheme.accent
    val currentBorder   = colorScheme.border

    var searchFocused by remember { mutableStateOf(false) }
    var pendingPlace by remember { mutableStateOf<Place?>(null) }
    var contentVisible by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) { 
        contentVisible = true
        searchFocusRequester.requestFocus()
    }

    val clothesPainter = painterResource(R.drawable.clothes)
    val foodPainter    = painterResource(R.drawable.food)
    val perfumePainter = painterResource(R.drawable.pefume)
    val beauPainter    = painterResource(R.drawable.beauu)

    val categories = remember(clothesPainter, foodPainter, perfumePainter, beauPainter) {
        listOf(
            Category("All",         Icons.Default.GridView,   categoryKey = ""),
            Category("Clothes",     clothesPainter,           categoryKey = "fashion"),
            Category("Food",        foodPainter,              categoryKey = "dining"),
            Category("Perfumes",    perfumePainter,           categoryKey = "perfumes& Cosmetics"),
            Category("Beauty",      beauPainter,              categoryKey = "beauty"),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = currentTextMain)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Select Destination",
                    color = currentTextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            // ── Search Bar ────────────────────────────────────────────────────
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(8.dp, RoundedCornerShape(28.dp), clip = false)
                        .background(
                            if (isDarkMode) GlassCardBg else Color.White,
                            RoundedCornerShape(28.dp)
                        )
                        .border(
                            BorderStroke(
                                if (searchFocused) 2.dp else 1.dp,
                                if (searchFocused) currentAccent else currentBorder
                            ),
                            RoundedCornerShape(28.dp)
                        )
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (searchFocused) currentAccent else currentTextSub,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 16.sp,
                            color = currentTextMain,
                            fontWeight = FontWeight.Normal
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester)
                            .onFocusChanged { searchFocused = it.isFocused },
                        decorationBox = { inner ->
                            if (uiState.searchQuery.isEmpty()) {
                                Text(
                                    text = "Search stores or landmarks",
                                    color = currentTextSub,
                                    fontSize = 15.sp
                                )
                            }
                            inner()
                        }
                    )
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = currentTextSub)
                        }
                    }
                }
            }

            // ── Categories ────────────────────────────────────────────────────
            Spacer(Modifier.height(16.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(categories, key = { _, cat -> cat.label }) { _, cat ->
                    CategoryChip(
                        category = cat,
                        selected = uiState.selectedCategoryKey == cat.categoryKey,
                        isDarkMode = isDarkMode,
                        currentAccent = currentAccent,
                        currentTextSub = currentTextSub,
                        currentBorder = currentBorder,
                        onClick = { viewModel.onCategorySelected(cat.categoryKey) }
                    )
                }
            }

            // ── Store List ────────────────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                if (uiState.displayedPlaces.isEmpty() && !uiState.isLoading) {
                    item { EmptyState(currentTextSub) }
                } else {
                    val results = uiState.displayedPlaces
                    itemsIndexed(results, key = { _, place -> place.id }) { index, place ->
                        AnimatedVisibility(
                            visible = contentVisible,
                            enter = fadeIn(tween(200, delayMillis = (index * 20).coerceAtMost(200))) +
                                    slideInVertically(tween(200, delayMillis = (index * 20).coerceAtMost(200))) { it / 10 }
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
                                    onClick = { pendingPlace = place },
                                    onToggleSaved = { FavoritesManager.toggleFavorite(place.id) },
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── Confirmation Dialog ───────────────────────────────────────────────
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
                            .background(Color.Black.copy(alpha = 0.45f))
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
