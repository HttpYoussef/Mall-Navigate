package com.example.mallar.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.mallar.R
import com.example.mallar.data.FavoritesManager
import com.example.mallar.data.Place
import com.example.mallar.data.PlaceRepository
import com.example.mallar.data.categoryDisplayRes
import com.example.mallar.ui.components.StoreLogoContainer
import com.example.mallar.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SavedPlacesScreen(
    onBackClick: () -> Unit,
    onPlaceClick: (Place) -> Unit,
) {
    val context = LocalContext.current
    val isDarkMode by com.example.mallar.data.AppPreferences.isDarkMode.collectAsState()
    val favoriteIds by FavoritesManager.favorites.collectAsState()

    val currentSurface  = MallTheme.colors.screenBackground
    val currentCard     = MallTheme.colors.surface
    val currentTextMain = MallTheme.colors.textPrimary
    val currentTextSub  = MallTheme.colors.textSecondary

    var allPlaces by remember { mutableStateOf<List<Place>>(emptyList()) }
    LaunchedEffect(Unit) {
        allPlaces = withContext(Dispatchers.IO) { PlaceRepository.load(context) }
    }

    val savedPlaces = remember(favoriteIds, allPlaces) {
        allPlaces.filter { favoriteIds.contains(it.id) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(currentSurface)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBackClick,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = currentCard
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = currentTextMain,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.saved_places),
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = currentTextMain
            )
        }

        if (savedPlaces.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = MallTheme.colors.accentText.copy(alpha = 0.5f), // theme-lint:allow decorative empty-state icon tint
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.no_favorites),
                        color = currentTextSub,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.bookmark_hint),
                        color = currentTextSub.copy(alpha = 0.7f), // theme-lint:allow decorative empty-state de-emphasis
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(savedPlaces, key = { it.id }) { place ->
                    SavedPlaceRow(
                        place           = place,
                        isDarkMode      = isDarkMode,
                        currentCard     = currentCard,
                        currentTextMain = currentTextMain,
                        currentTextSub  = currentTextSub,
                        onClick         = { onPlaceClick(place) },
                        onRemove        = { FavoritesManager.removeFavorite(place.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedPlaceRow(
    place: Place,
    isDarkMode: Boolean,
    currentCard: Color,
    currentTextMain: Color,
    currentTextSub: Color,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDarkMode) 0.dp else 1.dp, RoundedCornerShape(16.dp))
            .background(currentCard, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StoreLogoContainer(
            place    = place,
            modifier = Modifier.size(52.dp),
            contentPadding = 6.dp,
            fallbackTextSize = 16.sp,
            cornerRadius = 13.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = place.brand,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
                color      = currentTextMain,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            val categoryRes = categoryDisplayRes(place.category)
            val categoryLabel = if (categoryRes != null) {
                stringResource(categoryRes)
            } else {
                place.category.orEmpty()
                    .replaceFirstChar { c -> c.uppercaseChar() }
                    .ifBlank { stringResource(R.string.store_fallback) }
            }
            Text(
                text = stringResource(R.string.saved_category_inside_mall, categoryLabel),
                fontSize = 12.sp,
                color = currentTextSub
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Bookmark,
                contentDescription = stringResource(R.string.remove_saved_desc),
                tint = MallTheme.colors.accentText,
                modifier = Modifier.size(20.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MallTheme.colors.accent.copy(alpha = 0.1f), CircleShape), // theme-lint:allow decorative icon-badge tint
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.navigate_desc),
                tint = MallTheme.colors.accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
