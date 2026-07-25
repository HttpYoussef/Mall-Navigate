package com.example.mallar.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mallar.data.FavoritesManager
import com.example.mallar.data.Place
import com.example.mallar.data.PlaceRepository
import com.example.mallar.data.MallGraph
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.ui.chatbot.ChatBottomSheet
import com.example.mallar.ui.localization.NavigationState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Offers & Vouchers — real brand logos, placeholder discount copy ────────
private data class OfferItem(
    val id: String,
    val logoAssetPath: String,
    val brandName: String,
    val tint: Color,
    val discount: String,
    val subtitle: String,
    val floor: String,
)

private val sampleOffers = listOf(
    OfferItem("v_starbucks_upsize", "logos/Starbucks.png","Starbucks", Color(0xFF1E6E4A), "Free Upsize", "On any beverage",   "2nd Floor"),
    OfferItem("v_zara_15off",       "logos/ZARA.png",      "Zara",       Color(0xFF6E1E2E), "15% OFF",     "On selected items", "Ground Floor"),
    OfferItem("v_mango_20off",      "logos/Mango.png",     "Mango",      Color(0xFF8B4513), "20% OFF",     "On all items",      "Ground Floor"),
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
    onMapClick: () -> Unit = {},
    onSavedClick: () -> Unit = {},
    onParkingClick: () -> Unit = {},
    onNavigateToNavigation: () -> Unit = {},
    onOffersClick: () -> Unit = {},
    onVoucherClick: (String) -> Unit = {},
    onNavigateToDestinationSelection: () -> Unit = {},
) {
    val context = LocalContext.current
    val isDarkMode by com.example.mallar.data.AppPreferences.isDarkMode.collectAsState()
    val favoriteIds by FavoritesManager.favorites.collectAsState()

    val colorScheme = rememberHomeColorScheme(isDarkMode)
    val currentBg       = colorScheme.bg
    val currentCardBg   = colorScheme.cardBg
    val currentTextMain = colorScheme.textMain
    val currentTextSub  = colorScheme.textSub
    val currentAccent   = colorScheme.accent
    val currentBorder   = colorScheme.border

    // ── state ────────────────────────────────────────────────────────────────
    var allPlaces      by remember { mutableStateOf<List<Place>>(emptyList()) }
    var showChatBot    by remember { mutableStateOf(value = false) }
    var mallGraph      by remember { mutableStateOf<MallGraph?>(null) }
    var pendingPlace   by remember { mutableStateOf<Place?>(null) }
    var contentVisible by remember { mutableStateOf(value = false) }
    
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentBg),
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
                                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
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
                                        text = "Discover your favorite brands",
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

                // ── Start Navigation — premium hero CTA ──────────────────────────
                item(key = "start_nav_item") {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(280, delayMillis = 90)) + slideInVertically(tween(280, delayMillis = 90)) { it / 6 }
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "nav_glow_infinite")
                        val idleGlowAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.25f,
                            targetValue = 0.45f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "nav_idle_glow"
                        )
                        val navInteractionSource = remember { MutableInteractionSource() }
                        val navPressed by navInteractionSource.collectIsPressedAsState()
                        val navPressScale by animateFloatAsState(
                            targetValue = if (navPressed) 0.96f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                            label = "start_nav_press_scale"
                        )
                        val navGlow by animateFloatAsState(
                            targetValue = if (navPressed) 0.65f else idleGlowAlpha,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "start_nav_glow"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 20.dp)
                                .heightIn(min = 140.dp)
                                .scale(navPressScale)
                                .shadow(
                                    elevation = 30.dp,
                                    shape = RoundedCornerShape(32.dp),
                                    ambientColor = currentAccent.copy(alpha = navGlow),
                                    spotColor = currentAccent.copy(alpha = navGlow)
                                )
                                .clip(RoundedCornerShape(32.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = if (isDarkMode) {
                                            listOf(currentAccent.copy(alpha = 0.28f), GlassCardBg, GlassCardBg)
                                        } else {
                                            listOf(currentAccent.copy(alpha = 0.16f), currentCardBg, currentCardBg)
                                        }
                                    )
                                )
                                .border(BorderStroke(1.2.dp, currentAccent.copy(alpha = 0.5f)), RoundedCornerShape(32.dp))
                                .clickable(interactionSource = navInteractionSource, indication = null) {
                                    onNavigateToDestinationSelection()
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                                        ),
                                        RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                                    )
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 26.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(86.dp),
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
                                            .size(64.dp)
                                            .background(
                                                Brush.radialGradient(listOf(currentAccent.copy(alpha = 0.22f), Color.Transparent)),
                                                CircleShape
                                            )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(58.dp)
                                            .border(1.5.dp, currentAccent.copy(alpha = 0.6f), CircleShape)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Navigation,
                                        contentDescription = null,
                                        tint = currentAccent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(Modifier.width(20.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Start navigation",
                                        color = currentTextMain,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.4).sp
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Get walking directions to any store or place",
                                        color = currentTextSub,
                                        fontSize = 14.sp,
                                        lineHeight = 18.sp
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
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
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

                // ── Offers & Vouchers ─────────────────────────────────────────────
                item(key = "offers_spacer") { Spacer(Modifier.height(30.dp)) }
                item(key = "offers_header") {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(280, delayMillis = 220)) + slideInVertically(tween(280, delayMillis = 220)) { it / 6 }
                    ) {
                        SectionHeader(title = "Offers & vouchers", onSeeAll = onOffersClick, currentTextMain = currentTextMain, currentAccent = currentAccent)
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

                // ── Your favorites — real data (FavoritesManager) ────────────
                item(key = "favorites_spacer") { Spacer(Modifier.height(30.dp)) }
                item(key = "favorites_header") {
                    SectionHeader(
                        title = "Your favorites",
                        onSeeAll = onSavedClick,
                        currentTextMain = currentTextMain,
                        currentAccent = currentAccent
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
                item(key = "spacer_bottom") { Spacer(Modifier.height(16.dp)) }
            }
        }

        // ═══════════════════════════════════════ BOTTOM NAV ═══════════════════
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
                                    pendingPlace = null
                                    onDestinationSelected(confirmPlace)
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
// Parking Hero Card
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
            .height(200.dp)
            .scale(pressScale)
            .shadow(
                elevation = 16.dp,
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent)),
                    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text(
                text = "PARK SMART",
                color = ParkingPurple,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Find parking\nin seconds.",
                color = currentTextMain,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 26.sp
            )
            Spacer(Modifier.height(8.dp))

            val subtitleText = if (parkingLocation != null) {
                "Spot saved: ${parkingLocation!!.zone}-${parkingLocation!!.slot}"
            } else {
                "Real-time availability and directions."
            }
            Text(
                text = subtitleText,
                color = currentTextSub,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = if (isDarkMode) 0.10f else 0.7f))
                    .border(1.dp, Color.White.copy(alpha = if (isDarkMode) 0.16f else 0.9f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (parkingLocation != null) "Find my car" else "Find parking",
                    color = ParkingPurple,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = ParkingPurple,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth(0.55f)
                .fillMaxHeight(0.7f)
        ) {
            ParkingCarIllustration(isDarkMode = isDarkMode, currentAccent = ParkingPurple)
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 18.dp)
                .scale(0.85f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isDarkMode) Color(0xFF121B2E).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.9f))
                .border(1.dp, Color.White.copy(alpha = if (isDarkMode) 0.08f else 0.6f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("SPACES", color = currentTextSub, fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
            Text("128", color = ParkingPurple, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Available", color = currentTextSub, fontSize = 8.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Offer Card
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
                        .crossfade(enable = false)
                        .allowHardware(enable = false)
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
// Dynamic Car Graphic Illustration Component (Canvas Drawing)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ParkingCarIllustration(isDarkMode: Boolean, currentAccent: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        for (i in 1..3) {
                val yLine = h * (0.6f + (i * 0.1f))
                val wLine = w * (0.4f + (i * 0.15f))
            drawLine(
                color = currentAccent.copy(alpha = 0.08f / i),
                start = Offset((w - wLine) / 2f, yLine),
                end = Offset((w + wLine) / 2f, yLine),
                strokeWidth = 2f
            )
        }

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

        val carW = w * 0.64f
        val carH = h * 0.38f
        val carX = (w - carW) / 2f
        val carY = h * 0.44f

        val roofPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(carX + (carW * 0.26f), carY)
            lineTo(carX + carW * 0.74f, carY)
            lineTo(carX + carW * 0.84f, carY + carH * 0.4f)
            lineTo(carX + carW * 0.16f, carY + carH * 0.4f)
            close()
        }
        drawPath(roofPath, Color(0xFF0F2633).copy(alpha = 0.9f))
        drawPath(roofPath, currentAccent.copy(alpha = 0.2f), style = Stroke(width = 1f))

        val windPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(carX + carW * 0.29f, carY + 2f)
            lineTo(carX + carW * 0.71f, carY + 2f)
            lineTo(carX + carW * 0.80f, carY + carH * 0.36f)
            lineTo(carX + carW * 0.20f, carY + carH * 0.36f)
            close()
        }
        drawPath(windPath, currentAccent.copy(alpha = 0.25f))

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
