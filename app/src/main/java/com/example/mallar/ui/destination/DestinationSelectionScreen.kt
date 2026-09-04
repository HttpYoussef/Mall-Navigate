package com.example.mallar.ui.destination

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.mallar.R
import com.example.mallar.data.StoreCategory
import com.example.mallar.data.categoryDisplayRes
import com.example.mallar.data.floorDisplayLabel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mallar.data.AppPreferences
import com.example.mallar.data.Place
import com.example.mallar.ui.components.StoreLogo
import com.example.mallar.ui.home.DestinationConfirmSheet

// ── Design tokens — screen-local "Refined Indigo-Violet" system ──────────────

private val DselBgDark           = Color(0xFF0A0E1A)
private val DselBgLight          = Color(0xFFF6F7FB)
private val DselSurfaceDark      = Color(0xFF131A2C)
private val DselSurfaceLight     = Color(0xFFFFFFFF)
private val DselSurfaceAltDark   = Color(0xFF1B2438)
private val DselSurfaceAltLight  = Color(0xFFEEF0F7)
private val DselAccentDark       = Color(0xFF6D5DF6)
private val DselAccentLight      = Color(0xFF5847E8)
private val DselAquaDark         = Color(0xFF38CFE8)
private val DselAquaLight        = Color(0xFF0FA8C4)
private val DselTextMainDark     = Color(0xFFF2F4FB)
private val DselTextMainLight    = Color(0xFF111527)
private val DselTextSubDark      = Color(0xFF9AA3BC)
private val DselTextSubLight     = Color(0xFF5C6478)

// ── Spacing system (8pt grid) ────────────────────────────────────────────────
private val DselGutter = 20.dp
private val DselSectionGap = 36.dp

private data class DselTokens(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val accent: Color,
    val aqua: Color,
    val textMain: Color,
    val textSub: Color,
    val border: Color,
)

@Composable
private fun rememberDselTokens(isDarkMode: Boolean): DselTokens {
    return remember(isDarkMode) {
        if (isDarkMode) {
            DselTokens(
                bg         = DselBgDark,
                surface    = DselSurfaceDark,
                surfaceAlt = DselSurfaceAltDark,
                accent     = DselAccentDark,
                aqua       = DselAquaDark,
                textMain   = DselTextMainDark,
                textSub    = DselTextSubDark,
                border     = Color.White.copy(alpha = 0.09f),
            )
        } else {
            DselTokens(
                bg         = DselBgLight,
                surface    = DselSurfaceLight,
                surfaceAlt = DselSurfaceAltLight,
                accent     = DselAccentLight,
                aqua       = DselAquaLight,
                textMain   = DselTextMainLight,
                textSub    = DselTextSubLight,
                border     = Color.Black.copy(alpha = 0.07f),
            )
        }
    }
}

private data class DselCategory(
    val categoryKey: String,
    val icon: ImageVector,
)

private val dselCategories = listOf(
    DselCategory(StoreCategory.DINING, Icons.Rounded.Restaurant),
    DselCategory(StoreCategory.FASHION, Icons.Rounded.Checkroom),
    DselCategory(StoreCategory.JEWELLERY, Icons.Rounded.Diamond),
    DselCategory(StoreCategory.PERFUMES_COSMETICS, Icons.Rounded.Spa),
    DselCategory(StoreCategory.PHARMACY, Icons.Rounded.LocalPharmacy),
)

// ─────────────────────────────────────────────────────────────────────────────
// Start Navigation screen (destination selection)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DestinationSelectionScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: DestinationViewModel = viewModel(),
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCategoryClick: (key: String) -> Unit,
    onDestinationSelected: (Place) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkMode by AppPreferences.isDarkMode.collectAsState()
    val tokens = rememberDselTokens(isDarkMode)

    var pendingPlace by remember { mutableStateOf<Place?>(null) }
    var contentVisible by remember { mutableStateOf(value = false) }
    var showAllPopular by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    val popularPlaces = uiState.allPlaces.take(8)
    val recentPlaces = uiState.allPlaces.filter { it.brand in listOf("Nike", "Sephora", "ZARA") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg)
    ) {
        // Subtle atmospheric wash (lighter than the previous 450dp gradient)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            tokens.accent.copy(alpha = if (isDarkMode) 0.12f else 0.09f),
                            Color.Transparent
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            // ── Compact header: Back + Search pill ───────────────────────────
            item(key = "header") {
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(240, delayMillis = 140)) +
                        slideInVertically(tween(240, delayMillis = 140)) { -it / 8 }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = DselGutter, end = DselGutter, top = 16.dp, bottom = 4.dp)
                    ) {
                        DselIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tokens = tokens,
                            onClick = onBackClick
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.where_would_you_go),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = tokens.textMain,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            lineHeight = 34.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.dsel_search_subtitle),
                            color = tokens.textSub,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── Browse Categories ────────────────────────────────────────────
            item(key = "categories_section") {
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(240, delayMillis = 200)) +
                        slideInVertically(tween(240, delayMillis = 200)) { it / 10 }
                ) {
                    Column(modifier = Modifier.padding(top = 20.dp)) {
                        SectionOverline(
                            title = stringResource(R.string.dsel_browse_categories),
                            tokens = tokens
                        )
                        Spacer(Modifier.height(16.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = DselGutter),
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            itemsIndexed(dselCategories, key = { _, cat -> cat.categoryKey }) { _, cat ->
                                DselCategoryTile(
                                    category = cat,
                                    tokens = tokens,
                                    onClick = { onCategoryClick(cat.categoryKey) }
                                )
                            }
                        }
                    }
                }
            }

            // ── Hero search — framed centerpiece between Categories and Popular ─
            item(key = "search_hero") {
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(240, delayMillis = 260)) +
                        slideInVertically(tween(240, delayMillis = 260)) { it / 12 }
                ) {
                    Column(
                        modifier = Modifier
                            .then(
                                with(sharedTransitionScope) {
                                    Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "nav_cta_card"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                }
                            )
                            .padding(
                                horizontal = DselGutter,
                                vertical = DselSectionGap
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Cheap breathing glow: animates only the halo's alpha,
                        // never the card's RenderNode shadow.
                        val glowTransition = rememberInfiniteTransition(label = "dsel_search_glow")
                        val haloPulse by glowTransition.animateFloat(
                            initialValue = 0.45f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dsel_halo_pulse"
                        )
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            // Soft radial bloom behind the card
                            Box(
                                modifier = Modifier
                                    .width(380.dp)
                                    .height(200.dp)
                                    .alpha(haloPulse)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                tokens.accent.copy(alpha = 0.30f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            DselSearchPill(
                                tokens = tokens,
                                onClick = onSearchClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ── Loading indicator while places load ──────────────────────────
            if (uiState.isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = tokens.accent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            } else {
                // ── Popular Destinations ─────────────────────────────────────
                item(key = "popular_header") {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(240, delayMillis = 320))
                    ) {
                        Column {
                            SectionOverline(
                                title = stringResource(R.string.dsel_popular_destinations),
                                tokens = tokens,
                                onSeeAll = { showAllPopular = !showAllPopular },
                                seeAllLabel = if (showAllPopular) stringResource(R.string.dsel_show_less) else stringResource(R.string.see_all),
                                chevronRotated = showAllPopular
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }

                if (!showAllPopular) {
                    item(key = "popular_row") {
                        AnimatedVisibility(
                            visible = contentVisible,
                            enter = fadeIn(tween(240, delayMillis = 320)) +
                                slideInVertically(tween(240, delayMillis = 320)) { it / 10 }
                        ) {
                            if (popularPlaces.isEmpty()) {
                                DselEmptyHint(text = stringResource(R.string.dsel_no_destinations), tokens = tokens)
                            } else {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = DselGutter),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    itemsIndexed(popularPlaces, key = { _, place -> "pop_${place.id}" }) { _, place ->
                                        DselPopularCard(
                                            place = place,
                                            tokens = tokens,
                                            onClick = { pendingPlace = place }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (uiState.allPlaces.isEmpty()) {
                    item(key = "popular_all_empty") {
                        DselEmptyHint(text = stringResource(R.string.dsel_no_destinations), tokens = tokens)
                    }
                } else {
                    items(
                        uiState.allPlaces,
                        key = { "popx_${it.id}" }
                    ) { place ->
                        DselStoreRow(
                            place = place,
                            tokens = tokens,
                            onClick = { pendingPlace = place },
                            modifier = Modifier.padding(horizontal = DselGutter, vertical = 5.dp)
                        )
                    }
                }

                // ── Recently Visited ─────────────────────────────────────────
                item(key = "recent_header") {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(240, delayMillis = 380))
                    ) {
                        Column(modifier = Modifier.padding(top = DselSectionGap)) {
                            SectionOverline(
                                title = stringResource(R.string.dsel_recently_visited),
                                tokens = tokens
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }

                if (recentPlaces.isEmpty()) {
                    item(key = "recent_empty") {
                        DselEmptyHint(text = stringResource(R.string.dsel_places_appear_here), tokens = tokens)
                    }
                } else {
                    itemsIndexed(recentPlaces, key = { _, place -> "recent_${place.id}" }) { _, place ->
                        AnimatedVisibility(
                            visible = contentVisible,
                            enter = fadeIn(tween(220, delayMillis = 400)) +
                                slideInVertically(tween(220, delayMillis = 400)) { it / 12 }
                        ) {
                            DselStoreRow(
                                place = place,
                                tokens = tokens,
                                onClick = { pendingPlace = place },
                                modifier = Modifier.padding(horizontal = DselGutter, vertical = 5.dp)
                            )
                        }
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
                            currentCardBg = tokens.surface,
                            currentTextMain = tokens.textMain,
                            currentTextSub = tokens.textSub,
                            currentAccent = tokens.accent,
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

// ── Header primitives ────────────────────────────────────────────────────────

@Composable
private fun DselIconButton(
    icon: ImageVector,
    contentDescription: String,
    tokens: DselTokens,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tokens.surface)
            .border(1.dp, tokens.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tokens.textMain,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun DselSearchPill(
    tokens: DselTokens,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "dsel_search_press"
    )

    // Static shadow (cached by RenderNode — never recolored per frame).
    // The breathing effect lives in the radial halo behind the card instead.
    Box(
        modifier = modifier
            .height(110.dp)
            .scale(pressScale)
            .shadow(
                elevation = if (pressed) 10.dp else 30.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = tokens.accent.copy(alpha = 0.50f),
                spotColor = tokens.accent.copy(alpha = 0.50f)
            )
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        tokens.accent.copy(alpha = 0.85f),
                        tokens.aqua.copy(alpha = 0.55f),
                        tokens.accent.copy(alpha = 0.65f)
                    )
                )
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(1.6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(27.dp))
                .background(tokens.surface)
                .padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(tokens.accent, tokens.accent.copy(alpha = 0.72f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = stringResource(R.string.dsel_search_action),
                    color = tokens.aqua,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.where_would_you_go),
                    color = tokens.textMain,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(tokens.accent.copy(alpha = 0.10f), CircleShape)
                    .border(1.dp, tokens.accent.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = tokens.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ── Section header ───────────────────────────────────────────────────────────

@Composable
private fun SectionOverline(
    title: String,
    tokens: DselTokens,
    onSeeAll: (() -> Unit)? = null,
    seeAllLabel: String = stringResource(R.string.see_all),
    chevronRotated: Boolean = false
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (chevronRotated) 180f else 0f,
        animationSpec = tween(200),
        label = "dsel_seeall_chevron"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DselGutter),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            color = tokens.textSub,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        if (onSeeAll != null) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onSeeAll),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = seeAllLabel,
                        color = tokens.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = tokens.accent,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(chevronRotation)
                    )
                }
            }
        }
    }
}

// ── Category tile ────────────────────────────────────────────────────────────

@Composable
private fun DselCategoryTile(
    category: DselCategory,
    tokens: DselTokens,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "dsel_tile_press"
    )

    val displayLabel = categoryDisplayRes(category.categoryKey)?.let { stringResource(it) } ?: category.categoryKey

    Column(
        modifier = Modifier
            .width(88.dp)
            .scale(pressScale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(tokens.surface)
                .border(1.dp, tokens.border, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(tokens.accent.copy(alpha = 0.12f), CircleShape)
            )
            Icon(
                imageVector = category.icon,
                contentDescription = displayLabel,
                tint = tokens.accent,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.height(34.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                text = displayLabel,
                color = tokens.textMain,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Popular destination card ─────────────────────────────────────────────────

@Composable
private fun DselPopularCard(
    place: Place,
    tokens: DselTokens,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "dsel_pop_press"
    )

    Column(
        modifier = Modifier
            .width(156.dp)
            .scale(pressScale)
            .shadow(
                elevation = if (pressed) 2.dp else 6.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = tokens.accent.copy(alpha = 0.10f),
                spotColor = tokens.accent.copy(alpha = 0.10f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(tokens.surface)
            .border(1.dp, tokens.border, RoundedCornerShape(20.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color.White, CircleShape)
                .border(1.dp, tokens.border, CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            StoreLogo(place = place, modifier = Modifier.fillMaxSize(), contentPadding = 10.dp)
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = place.brand,
            color = tokens.textMain,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        val categoryRes = categoryDisplayRes(place.category)
        val categoryLabel = if (categoryRes != null) stringResource(categoryRes) else (place.category ?: stringResource(R.string.store_fallback))
        val floor = floorDisplayLabel(place.floor)
        Text(
            text = stringResource(R.string.store_category_floor, categoryLabel, floor),
            color = tokens.textSub,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Store row (recently visited) ─────────────────────────────────────────────

@Composable
private fun DselStoreRow(
    place: Place,
    tokens: DselTokens,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "dsel_row_press"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = tokens.accent.copy(alpha = 0.08f),
                spotColor = tokens.accent.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(tokens.surface)
            .border(1.dp, tokens.border, RoundedCornerShape(18.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Color.White, CircleShape)
                .border(1.dp, tokens.border, CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            StoreLogo(place = place, modifier = Modifier.fillMaxSize(), contentPadding = 9.dp)
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.brand,
                color = tokens.textMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            val categoryRes = categoryDisplayRes(place.category)
            val categoryLabel = if (categoryRes != null) stringResource(categoryRes) else (place.category ?: stringResource(R.string.store_fallback))
            val floor = floorDisplayLabel(place.floor)
            Text(
                text = stringResource(R.string.store_category_floor, categoryLabel, floor),
                color = tokens.textSub,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .background(tokens.accent.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = tokens.accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Empty hint ───────────────────────────────────────────────────────────────

@Composable
private fun DselEmptyHint(
    text: String,
    tokens: DselTokens,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Storefront,
            contentDescription = null,
            tint = tokens.textSub.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            color = tokens.textSub,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
