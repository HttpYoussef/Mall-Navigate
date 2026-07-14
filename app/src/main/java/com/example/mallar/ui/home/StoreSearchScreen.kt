package com.example.mallar.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.data.Place
import com.example.mallar.data.PlaceRepository
import com.example.mallar.ui.components.StoreLogoContainer
import com.example.mallar.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun StoreSearchScreen(
    onStoreClick: (Place) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var allPlaces by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val isDarkMode by com.example.mallar.data.AppPreferences.isDarkMode.collectAsState(initial = false)
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        allPlaces = withContext(Dispatchers.IO) { PlaceRepository.load(context) }
    }

    val filteredPlaces = remember(searchQuery, allPlaces) {
        if (searchQuery.isBlank()) allPlaces
        else allPlaces.filter { it.brand.contains(searchQuery, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        // ── Camera/Mall overlay header ───────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(CameraPlaceholder)
        ) {
            // Header controls row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBackClick,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = if (isDarkMode) colorScheme.surfaceVariant else White.copy(alpha = 0.9f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colorScheme.onSurface)
                    }
                }
            }
        }

        // ── Bottom sheet with search + list ─────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 220.dp)
                .background(colorScheme.surface, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
        ) {
            // Search bar
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(androidx.compose.ui.res.stringResource(com.example.mallar.R.string.search_hint), color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    },
                    modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(18.dp)),
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = Teal)
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) ({
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear",
                                tint = TextSecondary
                            )
                        }
                    }) else null,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = colorScheme.surfaceVariant,
                        unfocusedContainerColor = colorScheme.surfaceVariant,
                        focusedTextColor = colorScheme.onSurface,
                        unfocusedTextColor = colorScheme.onSurface
                    ),
                    singleLine = true
                )
            }

            // Store list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (filteredPlaces.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(androidx.compose.ui.res.stringResource(com.example.mallar.R.string.no_stores_found), color = colorScheme.onSurfaceVariant, fontSize = 16.sp)
                        }
                    }
                } else {
                    items(filteredPlaces, key = { it.id }) { place ->
                        StoreCard(
                            place = place,
                            onClick = { onStoreClick(place) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreCard(
    place: Place,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StoreLogoContainer(
                place    = place,
                modifier = Modifier.size(68.dp),
                contentPadding = 2.dp,
                cornerRadius = 14.dp,
            )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = place.brand,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    // Deterministic distance estimate from X,Y coordinates
                    val distM = remember(place.id) {
                        val dx = place.x - 319f
                        val dy = place.y - 227f
                        (kotlin.math.sqrt(dx * dx + dy * dy) * 0.9f).toInt().coerceIn(80, 480)
                    }
                    val mins = remember(distM) { (distM / 60).coerceIn(2, 10) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.LocationOn,
                            null,
                            tint = RedAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${distM}m",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "• ${mins}min",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colorScheme.onSurfaceVariant)
        }
    }
}
