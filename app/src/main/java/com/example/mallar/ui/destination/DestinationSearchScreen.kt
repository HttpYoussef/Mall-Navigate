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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mallar.R
import com.example.mallar.data.AppPreferences
import com.example.mallar.data.Place
import com.example.mallar.ui.home.*

@Composable
fun DestinationSearchScreen(
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
    
    LaunchedEffect(Unit) { 
        searchFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Premium Search Header ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDarkMode) GlassCardBg.copy(alpha = 0.5f) else Color.White)
                            .border(BorderStroke(1.dp, currentBorder), RoundedCornerShape(14.dp))
                            .clickable { onBackClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = currentTextMain, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    
                    GlowingSearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                        isFocused = searchFocused,
                        onFocusChange = { searchFocused = it },
                        focusRequester = searchFocusRequester,
                        currentAccent = currentAccent,
                        isDarkMode = isDarkMode,
                        currentTextMain = currentTextMain,
                        currentTextSub = currentTextSub,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Search Results ──────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                if (uiState.searchQuery.isNotBlank()) {
                    item(key = "search_title") {
                        Text(
                            text = stringResource(R.string.dsearch_title),
                            color = currentTextMain,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            letterSpacing = 1.2.sp
                        )
                    }
                    
                    if (uiState.displayedPlaces.isEmpty() && !uiState.isLoading) {
                        item { EmptyState(currentTextSub) }
                    } else {
                        itemsIndexed(uiState.displayedPlaces, key = { _, place -> "search_${place.id}" }) { _, place ->
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
                } else {
                    item {
                        SearchEmptyState(
                            title = stringResource(R.string.dsearch_ready_to_discover),
                            subtitle = stringResource(R.string.dsearch_start_typing),
                            currentTextSub = currentTextSub
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyState(title: String, subtitle: String, currentTextSub: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 100.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 40.dp)) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = currentTextSub.copy(alpha = 0.15f),
                modifier = Modifier.size(100.dp)
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = title,
                color = currentTextSub,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = subtitle,
                color = currentTextSub.copy(alpha = 0.6f),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
