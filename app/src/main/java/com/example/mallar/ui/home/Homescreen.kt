package com.example.mallar.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mallar.R
import com.example.mallar.data.FavoritesManager
import com.example.mallar.data.Place
import com.example.mallar.data.PlaceRepository
import com.example.mallar.data.MallGraph
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.ui.components.StoreLogo
import com.example.mallar.ui.chatbot.ChatBottomSheet
import com.example.mallar.ui.localization.NavigationState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

// ── Design Tokens ────────────────────────────────────────────────────────────
// Dark Futuristic Mode Tokens
internal val DeepNavyBg      = Color(0xFF06131A)
internal val GlassCardBg     = Color(0xFF0D1E26)
internal val CyanGlow        = Color(0xFF19D3E6)
internal val MutedTextSubDark = Color(0xFF8BA3AD)

// Parking "glass" card accent — purple, distinct from the cyan navigation accent
// so Parking reads as its own feature without competing with Search/Navigation.
internal val ParkingPurple      = Color(0xFF8B7CF6)
internal val ParkingPurpleDeep  = Color(0xFF4C3FD9)
internal val FavoriteHeartRed   = Color(0xFFEF476F)

// Light Mode Tokens
internal val LightBg          = Color(0xFFF7F9FA)
internal val LightCardBg      = Color(0xFFFFFFFF)
internal val LightTealAccent  = Color(0xFF258799)
internal val MutedTextSubLight = Color(0xFF888EA8)

// ── Offers & Vouchers — real brand logos, placeholder discount copy ────────
// Brand logos below point at real files already in app/src/main/assets/logos/
// (the same folder StoreLogo.kt uses for store logos) — no generated artwork.
// The discount/subtitle/floor text is still placeholder: this project has no
// offers/coupons backend yet. Replace `sampleOffers` with a real data source
// (e.g. an OffersRepository) before shipping.
private data class OfferItem(
    val id: String,
    val logoAssetPath: String,
    val brandName: String,
    val tint: Color,
    val discount: String,
    val subtitle: String,
    val floor: String
)

private val sampleOffers = listOf(
    OfferItem("v_starbucks_upsize", "logos/Starbucks.png","Starbucks", Color(0xFF1E6E4A), "Free Upsize", "On any beverage",   "2nd Floor"),
    OfferItem("v_zara_15off",       "logos/ZARA.png",      "Zara",       Color(0xFF6E1E2E), "15% OFF",     "On selected items", "Ground Floor"),
    OfferItem("v_mango_20off",      "logos/Mango.png",     "Mango",      Color(0xFF8B4513), "20% OFF",     "On all items",      "Ground Floor")
)

// ── Category data ─────────────────────────────────────────────────────────────
internal data class Category(
    val label: String,
    val icon: Any,
    val categoryKey: String
)

// ── Bottom nav items ──────────────────────────────────────────────────────────
private data class NavItem(val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("Home",    Icons.Default.Home),
    NavItem("Map",     Icons.Default.Map),
    NavItem("Ask AI",  Icons.Default.SmartToy),
    NavItem("Profile", Icons.Default.Person),
)

// ─────────────────────────────────────────────────────────────────────────────
// HomeScreen Redesign
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    onDestinationSelected: (Place) -> Unit,
    onSettingsClick: () -> Unit,
    onScanClick: () -> Unit,
    onMapClick: () -> Unit = {},
    onSavedClick: () -> Unit = {},
    onParkingClick: () -> Unit = {},
    onNavigateToNavigation: () -> Unit = {},
    onCategoryClick: (categoryKey: String, categoryLabel: String) -> Unit = { _, _ -> },
    onOffersClick: () -> Unit = {},
    onVoucherClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val isDarkMode by com.example.mallar.data.AppPreferences.isDarkMode.collectAsState()
    val favoriteIds by FavoritesManager.favorites.collectAsState()

    // ── Dynamic Color Scheme Mapping ─────────────────────────────────────────
    val currentBg       = if (isDarkMode) DeepNavyBg else LightBg
    val currentCardBg   = if (isDarkMode) GlassCardBg.copy(alpha = 0.5f) else LightCardBg
    val currentTextMain = if (isDarkMode) Color.White else Color(0xFF1A1A2E)
    val currentTextSub  = if (isDarkMode) MutedTextSubDark else MutedTextSubLight
    val currentAccent   = if (isDarkMode) CyanGlow else LightTealAccent
    val currentBorder   = if (isDarkMode) Color.White.copy(0.08f) else Color.Black.copy(0.06f)

    // Resources loading
    val clothesPainter = painterResource(R.drawable.clothes)
    val foodPainter    = painterResource(R.drawable.food)
    val perfumePainter = painterResource(R.drawable.pefume)
    val accesPainter   = painterResource(R.drawable.acces)
    val beauPainter    = painterResource(R.drawable.beauu)

    val categories = remember(clothesPainter) {
        listOf(
            Category("All",         Icons.Default.GridView,   categoryKey = ""),
            Category("Clothes",     clothesPainter,           categoryKey = "fashion"),
            Category("Food",        foodPainter,              categoryKey = "dining"),
            Category("Perfumes",    perfumePainter,           categoryKey = "perfumes& Cosmetics"),
            Category("Beauty",      beauPainter,              categoryKey = "beauty"),
        )
    }

    // ── state ────────────────────────────────────────────────────────────────
    var allPlaces      by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searchQuery    by remember { mutableStateOf("") }
    var searchFocused  by remember { mutableStateOf(false) }
    var selectedCatIdx by remember { mutableStateOf(0) }
    var showChatBot    by remember { mutableStateOf(false) }
    var mallGraph      by remember { mutableStateOf<MallGraph?>(null) }

    // Redesign additions:
    // - pendingPlace: drives the destination confirm sheet (tap a store → confirm → start)
    // - contentVisible: drives the brief staggered entrance animation on first composition
    // - selectedCatIdx: retained for the underlying displayedPlaces filter (category chip
    //   UI was removed from Home, but the filter itself is unchanged business logic and
    //   simply stays at its default "All" value now that there's no UI to change it)
    var pendingPlace       by remember { mutableStateOf<Place?>(null) }
    var contentVisible      by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { contentVisible = true }

    val userName = remember {
        FirebaseAuth.getInstance().currentUser?.displayName
            ?.split(" ")?.firstOrNull()
            ?: FirebaseAuth.getInstance().currentUser?.phoneNumber?.takeLast(4)
            ?: "there"
    }

    // ── load data ────────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        val places = withContext(Dispatchers.IO) { PlaceRepository.load(context) }
        val graph  = withContext(Dispatchers.IO) { MallGraphRepository.load(context) }
        allPlaces = places
        mallGraph = graph
    }

    val savedPlaces by remember {
        derivedStateOf { allPlaces.filter { favoriteIds.contains(it.id) } }
    }

    val displayedPlaces by remember {
        derivedStateOf {
            val trimmedQuery = searchQuery.trim()
            val afterSearch = if (trimmedQuery.isBlank()) {
                allPlaces
            } else {
                allPlaces.filter { place ->
                    place.brand.orEmpty().contains(trimmedQuery, ignoreCase = true)
                }
            }
            val key = categories.getOrNull(selectedCatIdx)?.categoryKey.orEmpty()
            if (key.isBlank()) afterSearch
            else afterSearch.filter { PlaceRepository.matchesCategory(it, key) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentBg)
    ) {
        // --- Top Glow Gradient Overlay ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            currentAccent.copy(alpha = 0.12f),
                            currentAccent.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // ═══════════════════════════════════════ LAZY BODY ════════════════
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // ── Header Section ────────────────────────────────────────────
                // Redesign: one-line purpose statement replaces the generic subtitle,
                // so a first-time user understands what the app does at a glance.
                item(key = "header_item") {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 6 }
                    ) {
                        Column {
                            Spacer(Modifier.height(18.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Hello, $userName 👋",
                                        color = currentTextMain,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                        letterSpacing = (-0.5).sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Find any store and get walking directions",
                                        color = currentTextMain.copy(alpha = 0.7f),
                                        fontSize = 14.sp
                                    )
                                }

                                // Notification Icon with glow circle
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(if (isDarkMode) GlassCardBg.copy(alpha = 0.6f) else Color.White, CircleShape)
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                if (isDarkMode) Color.White.copy(0.12f) else Color.Black.copy(0.08f)
                                            ),
                                            CircleShape
                                        )
                                        .clip(CircleShape)
                                        .clickable { /* Notification click */ },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notifications",
                                        tint = currentTextMain,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    // Accent active dot
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(currentAccent, CircleShape)
                                            .align(Alignment.TopEnd)
                                            .offset(x = (-4).dp, y = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Primary Search — the one dominant action on Home ───────────
                // Scan Logo is merged in as a trailing icon rather than a separate
                // button, so there is exactly one obvious way to start (type or scan).
                item(key = "search_row_item") {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(280, delayMillis = 60)) + slideInVertically(tween(280, delayMillis = 60)) { it / 6 }
                    ) {
                        Column {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .height(56.dp)
                                    .shadow(6.dp, RoundedCornerShape(28.dp), clip = false)
                                    .background(
                                        if (isDarkMode) GlassCardBg else Color.White,
                                        RoundedCornerShape(28.dp)
                                    )
                                    .border(
                                        BorderStroke(
                                            if (searchFocused) 1.5.dp else 1.dp,
                                            if (searchFocused) currentAccent else if (isDarkMode) Color.White.copy(0.08f) else Color.Black.copy(0.08f)
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
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it; selectedCatIdx = 0 },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 15.sp,
                                        color = currentTextMain,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(searchFocusRequester)
                                        .onFocusChanged { focusState -> searchFocused = focusState.isFocused },
                                    decorationBox = { inner ->
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Where do you want to go?",
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
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = currentTextSub,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(currentAccent.copy(alpha = if (isDarkMode) 0.14f else 0.1f))
                                        .border(1.dp, currentAccent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                        .clickable { onScanClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Scan Logo",
                                        tint = currentAccent,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // (AI Assistant now lives as a floating bubble — see bottom of composable —
                // instead of a card in this list, so it no longer competes with Search/Parking.)

                // ── Start Navigation — premium hero CTA ──────────────────────────
                // Focuses the existing search field (the real destination-picking
                // entry point) — visual only, no new navigation action.
                item(key = "start_nav_item") {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(280, delayMillis = 90)) + slideInVertically(tween(280, delayMillis = 90)) { it / 6 }
                    ) {
                        val navInteractionSource = remember { MutableInteractionSource() }
                        val navPressed by navInteractionSource.collectIsPressedAsState()
                        val navPressScale by animateFloatAsState(
                            targetValue = if (navPressed) 0.97f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "start_nav_press_scale"
                        )
                        val navGlow by animateFloatAsState(
                            targetValue = if (navPressed) 0.5f else 0.3f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "start_nav_glow"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                                .scale(navPressScale)
                                // Stronger, layered outer glow for a premium feel
                                .shadow(
                                    elevation = 22.dp,
                                    shape = RoundedCornerShape(28.dp),
                                    ambientColor = currentAccent.copy(alpha = navGlow),
                                    spotColor = currentAccent.copy(alpha = navGlow)
                                )
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    // Layered gradient: diagonal accent wash + radial glow pooled
                                    // behind the icon, over the base glass surface.
                                    Brush.linearGradient(
                                        colors = if (isDarkMode) {
                                            listOf(currentAccent.copy(alpha = 0.28f), GlassCardBg, GlassCardBg)
                                        } else {
                                            listOf(currentAccent.copy(alpha = 0.16f), currentCardBg, currentCardBg)
                                        }
                                    )
                                )
                                .border(BorderStroke(1.dp, currentAccent.copy(alpha = 0.4f)), RoundedCornerShape(28.dp))
                                .clickable(interactionSource = navInteractionSource, indication = null) {
                                    searchFocusRequester.requestFocus()
                                }
                        ) {
                            // Inner top highlight — soft glass reflection
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(70.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                                        ),
                                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                                    )
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(22.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Larger icon, double-layered glow ring
                                Box(
                                    modifier = Modifier.size(76.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.radialGradient(listOf(currentAccent.copy(alpha = 0.4f), Color.Transparent)),
                                                CircleShape
                                            )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(
                                                Brush.radialGradient(listOf(currentAccent.copy(alpha = 0.22f), Color.Transparent)),
                                                CircleShape
                                            )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .border(1.5.dp, currentAccent.copy(alpha = 0.6f), CircleShape)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Navigation,
                                        contentDescription = null,
                                        tint = currentAccent,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(Modifier.width(18.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Start navigation",
                                        color = currentTextMain,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.2).sp
                                    )
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        text = "Get walking directions to any store or place",
                                        color = currentTextSub,
                                        fontSize = 13.sp,
                                        lineHeight = 17.sp
                                    )
                                }

                                Spacer(Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .shadow(6.dp, CircleShape, ambientColor = currentAccent, spotColor = currentAccent)
                                        .background(currentAccent, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Start navigation",
                                        tint = if (isDarkMode) DeepNavyBg else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Parking — hero card ──────────────────────────────────────────
                // A true hero card (large, illustrated, own gradient identity) so
                // it reads as a primary feature next to Start Navigation, not a
                // list item. Same real data (ParkingManager) and onParkingClick.
                if (searchQuery.isBlank()) {
                    item(key = "parking_spacer") { Spacer(Modifier.height(6.dp)) }
                    item(key = "parking_hero_card") {
                        AnimatedVisibility(
                            visible = contentVisible,
                            enter = fadeIn(tween(280, delayMillis = 150)) +
                                slideInVertically(tween(280, delayMillis = 150)) { it / 6 }
                        ) {
                            ParkingHeroCard(
                                isDarkMode = isDarkMode,
                                currentCardBg = currentCardBg,
                                currentTextMain = currentTextMain,
                                currentTextSub = currentTextSub,
                                onParkingClick = onParkingClick
                            )
                        }
                    }
                }

                // ── Categories — compact chip/tab bar ─────────────────────────
                // Small, single row, no headers or expand/collapse — just a
                // quick filter, visually integrated rather than a separate
                // "section". Same selectedCatIdx / displayedPlaces filter as before.
                if (searchQuery.isBlank()) {
                    item(key = "cat_spacer") { Spacer(Modifier.height(20.dp)) }
                    item(key = "cat_row") {
                        AnimatedVisibility(
                            visible = contentVisible,
                            enter = fadeIn(tween(280, delayMillis = 190)) + slideInVertically(tween(280, delayMillis = 190)) { it / 6 }
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(categories, key = { _, cat -> cat.label }) { idx, cat ->
                                    CategoryChip(
                                        category = cat,
                                        selected = selectedCatIdx == idx,
                                        isDarkMode = isDarkMode,
                                        currentAccent = currentAccent,
                                        currentTextSub = currentTextSub,
                                        currentBorder = currentBorder,
                                        onClick = {
                                            selectedCatIdx = idx
                                            val screenTitle = if (cat.categoryKey.isBlank()) "All Stores" else cat.label
                                            val routeKey = cat.categoryKey.ifBlank { "all" }
                                            onCategoryClick(routeKey, screenTitle)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Offers & Vouchers ─────────────────────────────────────────────
                // NOTE: sampleOffers below is placeholder content — this project has
                // no offers/coupons backend yet. Wire `offers` to a real data source
                // (e.g. an OffersRepository) before shipping; nothing here is faked
                // as real data to the user beyond standard mockup placeholders.
                if (searchQuery.isBlank()) {
                    item(key = "offers_spacer") { Spacer(Modifier.height(30.dp)) }
                    item(key = "offers_header") {
                        AnimatedVisibility(
                            visible = contentVisible,
                            enter = fadeIn(tween(280, delayMillis = 220)) + slideInVertically(tween(280, delayMillis = 220)) { it / 6 }
                        ) {
                            SectionHeader(title = "Offers & vouchers", onSeeAll = onOffersClick, currentTextMain = currentTextMain)
                        }
                    }
                    item(key = "offers_spacer2") { Spacer(Modifier.height(14.dp)) }
                    item(key = "offers_row") {
                        AnimatedVisibility(
                            visible = contentVisible,
                            enter = fadeIn(tween(280, delayMillis = 250)) + slideInVertically(tween(280, delayMillis = 250)) { it / 6 }
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(sampleOffers, key = { it.id }) { offer ->
                                    OfferCard(
                                        offer = offer,
                                        isDarkMode = isDarkMode,
                                        currentTextMain = currentTextMain,
                                        currentTextSub = currentTextSub,
                                        onClick = { onVoucherClick(offer.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Search mode — clean vertical results list ──────────────────
                // While typing, Home "transforms": every other section above
                // (Start Navigation / Parking / Categories / Offers) is already
                // hidden by their own `if (searchQuery.isBlank())` guards, so
                // this list appears directly under the search bar. Same
                // `displayedPlaces` filter, same pendingPlace/onToggleSaved
                // wiring as everywhere else — only the layout is new.
                if (searchQuery.isNotBlank()) {
                    item(key = "search_spacer") { Spacer(Modifier.height(18.dp)) }
                    item(key = "search_count") {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 8 }
                        ) {
                            Text(
                                text = if (displayedPlaces.isEmpty()) "No matches" else "${displayedPlaces.size} ${if (displayedPlaces.size == 1) "result" else "results"}",
                                color = currentTextSub,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                    item(key = "search_spacer2") { Spacer(Modifier.height(16.dp)) }
                    if (displayedPlaces.isEmpty()) {
                        item(key = "search_empty") {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 8 }
                            ) {
                                EmptyState(currentTextSub)
                            }
                        }
                    } else {
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
                                        onClick = { pendingPlace = place },
                                        onToggleSaved = { FavoritesManager.toggleFavorite(place.id) },
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                    // Generous spacing between rows — easy to scan while walking
                                    Spacer(Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                } else {
                    // ── Your favorites — real data (FavoritesManager) ────────────
                    // Replaces both the old Categories section and the All Stores
                    // list: Home now surfaces Search (any store), Favorites (saved
                    // stores), and Offers — matching the approved design exactly.
                    item(key = "favorites_spacer") { Spacer(Modifier.height(30.dp)) }
                    item(key = "favorites_header") {
                        SectionHeader(
                            title = "Your favorites",
                            onSeeAll = onSavedClick,
                            currentTextMain = currentTextMain
                        )
                    }
                    item(key = "favorites_spacer2") { Spacer(Modifier.height(14.dp)) }
                    if (savedPlaces.isEmpty()) {
                        item(key = "favorites_empty") {
                            Text(
                                text = "Tap the heart on any store to save it here.",
                                color = currentTextSub,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    } else {
                        items(savedPlaces, key = { it.id }) { place ->
                            StoreRow(
                                place = place,
                                isSaved = true,
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
                item(key = "spacer_bottom") { Spacer(Modifier.height(16.dp)) }
            }
        }

        // ═══════════════════════════════════════ BOTTOM NAV ═══════════════════
        // Parking removed from the bottom nav — it now lives only as the Home
        // hero card (onParkingClick is still used there, just no longer wired
        // here). Index 2 ("Ask AI") opens the same ChatBottomSheet dialog that
        // used to be triggered by the floating bubble.
        BottomNav(
            items = navItems,
            activeIndex = 0,
            isDarkMode = isDarkMode,
            currentAccent = currentAccent,
            currentTextSub = currentTextSub,
            onSelect = { idx ->
                when (idx) {
                    0 -> { /* Home - do nothing */ }
                    1 -> onMapClick()
                    2 -> showChatBot = true
                    3 -> onSettingsClick()
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // (No floating action button on Home — AI Assistant is reached via the
        // "Ask AI" bottom nav tab above instead of a floating bubble.)

        // ── ChatBot dialog ────────────────────────────────────────────────────
        if (showChatBot) {
            Dialog(
                onDismissRequest = { showChatBot = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().clickable { showChatBot = false })
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        ChatBottomSheet(
                            graph = mallGraph,
                            onDismiss = { showChatBot = false },
                            onPathFound = { path ->
                                NavigationState.aStarPath = path
                            },
                            onStartNavigation = { useAr ->
                                showChatBot = false
                                NavigationState.startWithAr = useAr
                                onNavigateToNavigation()
                            }
                        )
                    }
                }
            }
        }

        // ── Destination confirm sheet ───────────────────────────────────────
        // The moment a store is tapped (from search, saved, or the full list),
        // we confirm the choice and explain — once, right before it happens —
        // why the camera opens next. This replaces jumping straight into the
        // camera with no context.
        val confirmPlace = pendingPlace
        if (confirmPlace != null) {
            Dialog(
                onDismissRequest = { pendingPlace = null },
                properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
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
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMedium
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

// ─────────────────────────────────────────────────────────────────────────────
// Category Chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryChip(
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

// ─────────────────────────────────────────────────────────────────────────────
// Place card (horizontal scroll of saved places)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlaceCard(
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "place_card_press")

    Card(
        modifier = Modifier
            .width(150.dp)
            .scale(pressScale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .border(BorderStroke(1.dp, currentBorder), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = currentCardBg)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(currentAccent.copy(alpha = 0.12f), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Perfect Circular Logo container
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White, CircleShape)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    StoreLogo(
                        place = place,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = 6.dp,
                        fallbackTextSize = 22.sp,
                        maxDecodeSizePx = 200,
                    )
                }

                IconButton(
                    onClick = onToggleSaved,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .background(if (isDarkMode) Color(0xFF0D1E26).copy(0.6f) else Color.White.copy(0.85f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isSaved) FavoriteHeartRed else currentTextSub,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp, 10.dp, 10.dp, 12.dp)) {
                Text(
                    text = place.brand.orEmpty(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = currentTextMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val categoryLabel = remember(place.category) {
                    val cat = place.category.orEmpty()
                    when {
                        cat.contains("fashion", ignoreCase = true) -> "Fashion"
                        cat.contains("dining", ignoreCase = true) -> "Dining"
                        cat.contains("perfumes", ignoreCase = true) -> "Perfumes"
                        else -> cat.take(12)
                    }
                }
                Text(
                    text = categoryLabel,
                    fontSize = 11.sp,
                    color = currentTextSub
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = currentAccent,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    val floorLabel = remember(place.floor) {
                        when (place.floor) {
                            1 -> "Ground Floor"
                            2 -> "First Floor"
                            else -> "Inside Mall"
                        }
                    }
                    Text(
                        text = floorLabel,
                        fontSize = 11.sp,
                        color = currentAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Store Row (vertical list of all stores)
// ─────────────────────────────────────────────────────────────────────────────

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
        // Perfect Circular white Logo container (ContentScale.Fit, centered)
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

        // Favorite button
        IconButton(onClick = onToggleSaved, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Save place",
                tint = if (isSaved) FavoriteHeartRed else currentTextSub,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(6.dp))

        // Circular chevron navigation button
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

// ─────────────────────────────────────────────────────────────────────────────
// Section Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
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

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Nav (Floating Pill Bar)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BottomNav(
    items: List<NavItem>,
    activeIndex: Int,
    isDarkMode: Boolean,
    currentAccent: Color,
    currentTextSub: Color,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val navBg = if (isDarkMode) GlassCardBg.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.94f)
    val navBorder = if (isDarkMode) Color.White.copy(alpha = 0.10f) else currentAccent.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Soft outer glow behind the whole bar for a premium glass feel
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = currentAccent.copy(alpha = 0.22f),
                    spotColor = currentAccent.copy(alpha = 0.22f)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(navBg)
                .border(BorderStroke(1.dp, navBorder), RoundedCornerShape(28.dp))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { idx, item ->
                BottomNavTab(
                    item = item,
                    active = activeIndex == idx,
                    currentAccent = currentAccent,
                    currentTextSub = currentTextSub,
                    onClick = { onSelect(idx) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomNavTab(
    item: NavItem,
    active: Boolean,
    currentAccent: Color,
    currentTextSub: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // Smooth color transition between active/inactive rather than a snap-cut
    val tint by animateColorAsState(
        targetValue = if (active) currentAccent else currentTextSub,
        animationSpec = tween(220),
        label = "nav_tab_tint"
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (active) 0.14f else 0f,
        animationSpec = tween(220),
        label = "nav_tab_pill_alpha"
    )
    // Icon scales up slightly when active, and dips on press — combined for a
    // single fluid feel rather than two competing animations.
    val iconScale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else if (active) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "nav_tab_icon_scale"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .background(currentAccent.copy(alpha = pillAlpha), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = tint,
                modifier = Modifier
                    .size(22.dp)
                    .scale(iconScale)
            )
        }
        Text(
            text = item.label,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = tint
        )
        // Small active indicator dot — animates in/out rather than appearing instantly
        AnimatedVisibility(visible = active, enter = fadeIn(tween(180)) + scaleIn(tween(180)), exit = fadeOut(tween(120))) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(currentAccent, CircleShape)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty State
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// Parking Hero Card — full feature-card treatment (not a list item), matching
// the approved design. Same real data source and callback as before:
// ParkingManager.parkingLocation drives the subtitle, onParkingClick is
// unchanged. Only the visual presentation is new.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ParkingHeroCard(
    isDarkMode: Boolean,
    currentCardBg: Color,
    currentTextMain: Color,
    currentTextSub: Color,
    onParkingClick: () -> Unit
) {
    val parkingLocation by com.example.mallar.data.ParkingManager.parkingLocation.collectAsState()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "parking_hero_press_scale"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.55f else 0.3f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "parking_hero_glow"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(280.dp)
            .scale(pressScale)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = ParkingPurple.copy(alpha = glowAlpha),
                spotColor = ParkingPurple.copy(alpha = glowAlpha)
            )
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    colors = if (isDarkMode) {
                        listOf(ParkingPurpleDeep.copy(alpha = 0.45f), currentCardBg)
                    } else {
                        listOf(ParkingPurple.copy(alpha = 0.14f), currentCardBg)
                    }
                )
            )
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = if (isDarkMode) 0.10f else 0.5f)),
                RoundedCornerShape(28.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onParkingClick() }
    ) {
        // Inner top highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent)),
                    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                text = "PARK SMART",
                color = ParkingPurple,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Find parking\nin seconds.",
                color = currentTextMain,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 30.sp
            )
            Spacer(Modifier.height(10.dp))

            val subtitleText = if (parkingLocation != null) {
                "Spot saved: ${parkingLocation!!.zone}-${parkingLocation!!.slot} · Floor ${parkingLocation!!.floor}. Tap to get directions back to your car."
            } else {
                "Real-time availability and directions to your spot."
            }
            Text(
                text = subtitleText,
                color = currentTextSub,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = if (isDarkMode) 0.10f else 0.7f))
                    .border(1.dp, Color.White.copy(alpha = if (isDarkMode) 0.16f else 0.9f), RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (parkingLocation != null) "Find my car" else "Find parking",
                    color = ParkingPurple,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = ParkingPurple,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // Illustration anchored to the right/bottom — reuses the existing custom
        // car + glowing pin Canvas illustration, scaled up for the hero size.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth(0.62f)
                .fillMaxHeight(0.78f)
        ) {
            ParkingCarIllustration(isDarkMode = isDarkMode, currentAccent = ParkingPurple)
        }

        // "Spaces available" badge, top-right
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 24.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isDarkMode) Color(0xFF121B2E).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.9f))
                .border(1.dp, Color.White.copy(alpha = if (isDarkMode) 0.08f else 0.6f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("SPACES", color = currentTextSub, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
            Text("128", color = ParkingPurple, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Available", color = currentTextSub, fontSize = 9.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Offer Card — real brand logo (see NOTE at the sampleOffers declaration above
// regarding the discount copy still being placeholder pending a real backend).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OfferCard(
    offer: OfferItem,
    isDarkMode: Boolean,
    currentTextMain: Color,
    currentTextSub: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "offer_card_press")

    Box(
        modifier = Modifier
            .width(152.dp)
            .height(196.dp)
            .scale(pressScale)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(offer.tint.copy(alpha = if (isDarkMode) 0.55f else 0.28f), GlassCardBg.takeIf { isDarkMode } ?: Color.White)
                )
            )
            .border(1.dp, Color.White.copy(alpha = if (isDarkMode) 0.06f else 0.5f), RoundedCornerShape(22.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val request = remember(offer.logoAssetPath) {
                    ImageRequest.Builder(context)
                        .data("file:///android_asset/${offer.logoAssetPath}")
                        .crossfade(false)
                        .allowHardware(false)
                        .size(CoilSize(128, 128))
                        .memoryCacheKey(offer.logoAssetPath)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = offer.brandName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(7.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            Text(offer.discount, color = currentTextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(offer.subtitle, color = currentTextSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CyanGlow.copy(alpha = 0.12f))
                    .border(1.dp, CyanGlow.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(offer.floor, color = CyanGlow, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Parking Home Card Component (superseded on Home by ParkingHeroCard above;
// left in place unused rather than deleted, in case it's needed elsewhere).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ParkingHomeCard(
    isDarkMode: Boolean,
    currentCardBg: Color,
    currentTextMain: Color,
    currentTextSub: Color,
    currentAccent: Color,
    onParkingClick: () -> Unit
) {
    val parkingLocation by com.example.mallar.data.ParkingManager.parkingLocation.collectAsState()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // Press micro-interaction: scale + elevation + glow move together, matching
    // the reference "Park Smart" card. Purely visual — onParkingClick fires the
    // same as before.
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "parking_card_press_scale"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.5f else 0.25f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "parking_card_glow"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .scale(pressScale)
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = ParkingPurple.copy(alpha = glowAlpha),
                spotColor = ParkingPurple.copy(alpha = glowAlpha)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (isDarkMode) {
                    Brush.verticalGradient(
                        listOf(ParkingPurpleDeep.copy(alpha = 0.30f), currentCardBg)
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(ParkingPurple.copy(alpha = 0.10f), currentCardBg)
                    )
                }
            )
            .border(
                BorderStroke(1.dp, if (isDarkMode) Color.White.copy(alpha = 0.10f) else ParkingPurple.copy(alpha = 0.18f)),
                RoundedCornerShape(24.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onParkingClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stylized forward-facing car illustration + glowing location pin
            Box(
                modifier = Modifier.size(width = 90.dp, height = 70.dp),
                contentAlignment = Alignment.Center
            ) {
                ParkingCarIllustration(isDarkMode = isDarkMode, currentAccent = ParkingPurple)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PARK SMART",
                    color = ParkingPurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Parking Assistant",
                        color = currentTextMain,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = ParkingPurple,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "New",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val subtitleText = if (parkingLocation != null) {
                    "Spot Saved: ${parkingLocation!!.zone}-${parkingLocation!!.slot} • Floor ${parkingLocation!!.floor}"
                } else {
                    "Save your parking location and find your car easily"
                }
                Text(
                    text = subtitleText,
                    color = currentTextSub,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = if (isDarkMode) 0.08f else 0.06f))
                    .border(1.dp, ParkingPurple.copy(alpha = 0.3f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open Parking Assistant",
                    tint = ParkingPurple,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dynamic Car Graphic Illustration Component (Canvas Drawing)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ParkingCarIllustration(isDarkMode: Boolean, currentAccent: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Perspective Road Lines
        for (i in 1..3) {
            val yLine = h * (0.6f + i * 0.1f)
            val wLine = w * (0.4f + i * 0.15f)
            drawLine(
                color = currentAccent.copy(alpha = 0.08f / i),
                start = Offset((w - wLine) / 2f, yLine),
                end = Offset((w + wLine) / 2f, yLine),
                strokeWidth = 2f
            )
        }

        // 2. Glowing Pin "P" (top right background)
        val pinX = w * 0.74f
        val pinY = h * 0.25f

        drawCircle(
            color = currentAccent.copy(alpha = 0.2f),
            radius = 16f,
            center = Offset(pinX, pinY)
        )
        drawCircle(
            color = currentAccent,
            radius = 11f,
            center = Offset(pinX, pinY)
        )
        drawCircle(
            color = Color.White,
            radius = 7f,
            center = Offset(pinX, pinY)
        )

        // Draw letter "P" outline inside the pin
        val pPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(pinX - 2.5f, pinY + 3.5f)
            lineTo(pinX - 2.5f, pinY - 3.5f)
            lineTo(pinX + 0.5f, pinY - 3.5f)
            quadraticTo(pinX + 2.5f, pinY - 1.75f, pinX + 0.5f, pinY)
            lineTo(pinX - 2.5f, pinY)
        }
        drawPath(
            path = pPath,
            color = if (isDarkMode) Color(0xFF0D1E26) else currentAccent,
            style = Stroke(width = 1.5f)
        )

        // 3. Futuristic Car (facing forward)
        val carW = w * 0.64f
        val carH = h * 0.38f
        val carX = (w - carW) / 2f
        val carY = h * 0.44f

        // Roof trapezoid
        val roofPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(carX + carW * 0.26f, carY)
            lineTo(carX + carW * 0.74f, carY)
            lineTo(carX + carW * 0.84f, carY + carH * 0.4f)
            lineTo(carX + carW * 0.16f, carY + carH * 0.4f)
            close()
        }
        drawPath(roofPath, Color(0xFF0F2633).copy(alpha = 0.9f))
        drawPath(roofPath, currentAccent.copy(alpha = 0.2f), style = Stroke(width = 1f))

        // Windshield
        val windPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(carX + carW * 0.29f, carY + 2f)
            lineTo(carX + carW * 0.71f, carY + 2f)
            lineTo(carX + carW * 0.80f, carY + carH * 0.36f)
            lineTo(carX + carW * 0.20f, carY + carH * 0.36f)
            close()
        }
        drawPath(windPath, currentAccent.copy(alpha = 0.25f))

        // Hood and Main body
        val bodyPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(carX + carW * 0.08f, carY + carH * 0.4f)
            lineTo(carX + carW * 0.92f, carY + carH * 0.4f)
            quadraticTo(carX + carW * 0.98f, carY + carH * 0.5f, carX + carW * 0.98f, carY + carH * 0.8f)
            lineTo(carX + carW * 0.02f, carY + carH * 0.8f)
            quadraticTo(carX + carW * 0.02f, carY + carH * 0.5f, carX + carW * 0.08f, carY + carH * 0.4f)
            close()
        }
        drawPath(bodyPath, Color(0xFF0A1720))
        drawPath(bodyPath, currentAccent.copy(alpha = 0.4f), style = Stroke(width = 1f))

        // Bumper grill
        drawRoundRect(
            color = Color(0xFF152D3D),
            topLeft = Offset(carX + carW * 0.32f, carY + carH * 0.56f),
            size = Size(carW * 0.36f, carH * 0.2f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        for (i in 0..2) {
            val yOffset = carY + carH * 0.58f + i * 4f
            drawLine(
                color = currentAccent.copy(alpha = 0.3f),
                start = Offset(carX + carW * 0.35f, yOffset),
                end = Offset(carX + carW * 0.65f, yOffset),
                strokeWidth = 1f
            )
        }

        // Headlights
        val leftLightPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(carX + carW * 0.12f, carY + carH * 0.48f)
            lineTo(carX + carW * 0.24f, carY + carH * 0.48f)
            lineTo(carX + carW * 0.22f, carY + carH * 0.56f)
            lineTo(carX + carW * 0.14f, carY + carH * 0.56f)
            close()
        }
        val rightLightPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(carX + carW * 0.88f, carY + carH * 0.48f)
            lineTo(carX + carW * 0.76f, carY + carH * 0.48f)
            lineTo(carX + carW * 0.78f, carY + carH * 0.56f)
            lineTo(carX + carW * 0.86f, carY + carH * 0.56f)
            close()
        }

        drawPath(leftLightPath, Color(0xFF0D1E26))
        drawPath(rightLightPath, Color(0xFF0D1E26))
        drawPath(leftLightPath, currentAccent.copy(alpha = 0.8f))
        drawPath(rightLightPath, currentAccent.copy(alpha = 0.8f))

        // Projected beams
        val leftBeam = androidx.compose.ui.graphics.Path().apply {
            moveTo(carX + carW * 0.12f, carY + carH * 0.54f)
            lineTo(carX + carW * 0.24f, carY + carH * 0.54f)
            lineTo(carX - carW * 0.05f, h)
            lineTo(carX + carW * 0.18f, h)
            close()
        }
        val rightBeam = androidx.compose.ui.graphics.Path().apply {
            moveTo(carX + carW * 0.88f, carY + carH * 0.54f)
            lineTo(carX + carW * 0.76f, carY + carH * 0.54f)
            lineTo(carX + carW * 1.05f, h)
            lineTo(carX + carW * 0.82f, h)
            close()
        }

        drawPath(leftBeam, Brush.verticalGradient(listOf(currentAccent.copy(alpha = 0.3f), Color.Transparent)))
        drawPath(rightBeam, Brush.verticalGradient(listOf(currentAccent.copy(alpha = 0.3f), Color.Transparent)))

        // Tires
        drawRoundRect(
            color = Color(0xFF02090D),
            topLeft = Offset(carX + carW * 0.08f, carY + carH * 0.76f),
            size = Size(carW * 0.12f, carH * 0.15f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = Color(0xFF02090D),
            topLeft = Offset(carX + carW * 0.80f, carY + carH * 0.76f),
            size = Size(carW * 0.12f, carH * 0.15f),
            cornerRadius = CornerRadius(2f, 2f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Destination Confirm Sheet
// Shown the moment a store is tapped. Confirms the choice and explains — once,
// right before it happens — why the camera opens next, then hands off to the
// existing onDestinationSelected flow (permissions → logo scan → navigation).
// ─────────────────────────────────────────────────────────────────────────────

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
        // Drag handle
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
