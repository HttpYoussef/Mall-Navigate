package com.example.mallar.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.shadow
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
private val DeepNavyBg      = Color(0xFF06131A)
private val GlassCardBg     = Color(0xFF0D1E26)
private val CyanGlow        = Color(0xFF19D3E6)
private val MutedTextSubDark = Color(0xFF8BA3AD)

// Light Mode Tokens
private val LightBg          = Color(0xFFF7F9FA)
private val LightCardBg      = Color(0xFFFFFFFF)
private val LightTealAccent  = Color(0xFF258799)
private val MutedTextSubLight = Color(0xFF888EA8)

// Hero panel gradient (Home screen top zone)
private val HeroGradientLight = listOf(Color(0xFF2AA7B8), Color(0xFF1B6E7D))
private val HeroGradientDark  = listOf(Color(0xFF115C68), Color(0xFF08313A))

// ── Category data ─────────────────────────────────────────────────────────────
private data class Category(
    val label: String,
    val icon: Any,
    val categoryKey: String
)

// ── Bottom nav items ──────────────────────────────────────────────────────────
private data class NavItem(val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("Home",    Icons.Default.Home),
    NavItem("Map",     Icons.Default.Map),
    NavItem("Parking", Icons.Default.DirectionsCar),
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
        Column(modifier = Modifier.fillMaxSize()) {
            // ═══════════════════════════════════════ LAZY BODY ════════════════
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // ── Hero Panel: Greeting + Primary Search ───────────────────────
                // Redesign: one bold, high-contrast zone owns the top of the screen
                // so the search action is unmistakably the first thing a user sees.
                item(key = "hero_item") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(if (isDarkMode) HeroGradientDark else HeroGradientLight),
                                RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Hi, $userName 👋",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 26.sp,
                                    letterSpacing = (-0.5).sp
                                )

                                // Notification icon — translucent on-color treatment
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                                        .clip(CircleShape)
                                        .clickable { /* Notification click */ },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notifications",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color.White, CircleShape)
                                            .align(Alignment.TopEnd)
                                            .offset(x = (-4).dp, y = 4.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Find your way around the mall",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 15.sp
                            )

                            Spacer(Modifier.height(20.dp))

                            // Primary search capsule — merges Scan Logo as a trailing
                            // action so there is exactly one dominant CTA on this screen.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .shadow(10.dp, RoundedCornerShape(28.dp), clip = false)
                                    .background(Color.White, RoundedCornerShape(28.dp))
                                    .padding(horizontal = 18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = if (isDarkMode) DeepNavyBg else LightTealAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it; selectedCatIdx = 0 },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 16.sp,
                                        color = Color(0xFF1A1A2E),
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .onFocusChanged { focusState -> searchFocused = focusState.isFocused },
                                    decorationBox = { inner ->
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Where would you like to go?",
                                                color = Color(0xFF888EA8),
                                                fontSize = 15.sp,
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
                                            tint = Color(0xFF888EA8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            (if (isDarkMode) CyanGlow else LightTealAccent).copy(alpha = 0.12f),
                                            CircleShape
                                        )
                                        .clip(CircleShape)
                                        .clickable { onScanClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Scan Logo",
                                        tint = if (isDarkMode) CyanGlow else LightTealAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── AI Assistant Card (Ask Me Anything) ───────────────────────
                item(key = "ai_assistant_item") {
                    Spacer(Modifier.height(18.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .border(BorderStroke(1.dp, currentBorder), RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = currentCardBg)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showChatBot = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(currentAccent.copy(alpha = if (isDarkMode) 0.15f else 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = "AI Assistant",
                                    tint = currentAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ask Me Anything",
                                    color = currentTextMain,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Your AI assistant for the mall",
                                    color = currentTextSub,
                                    fontSize = 12.sp
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Open AI Assistant",
                                tint = currentAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // ── Categories Section ────────────────────────────────────────
                item(key = "cat_spacer") { Spacer(Modifier.height(24.dp)) }
                item(key = "cat_header") {
                    SectionHeader(title = "Categories", onSeeAll = {}, currentTextMain = currentTextMain)
                }
                item(key = "spacer_cat") { Spacer(Modifier.height(12.dp)) }
                item(key = "cat_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(categories, key = { _, cat -> cat.label }) { idx, cat ->
                            CategoryChip(
                                category = cat,
                                selected = selectedCatIdx == idx,
                                isDarkMode = isDarkMode,
                                currentAccent = currentAccent,
                                currentTextSub = currentTextSub,
                                currentBorder = currentBorder,
                                onClick  = { selectedCatIdx = idx }
                            )
                        }
                    }
                }

                // ── Search query results ──────────────────────────────────────
                if (searchQuery.isNotBlank()) {
                    item(key = "search_spacer") { Spacer(Modifier.height(24.dp)) }
                    item(key = "search_header") {
                        SectionHeader(title = "Results (${displayedPlaces.size})", onSeeAll = null, currentTextMain = currentTextMain)
                    }
                    item(key = "search_spacer2") { Spacer(Modifier.height(12.dp)) }
                    if (displayedPlaces.isEmpty()) {
                        item(key = "search_empty") { EmptyState(currentTextSub) }
                    } else {
                        item(key = "search_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(displayedPlaces, key = { it.id }) { place ->
                                    PlaceCard(
                                        place = place,
                                        isSaved = favoriteIds.contains(place.id),
                                        isDarkMode = isDarkMode,
                                        currentCardBg = currentCardBg,
                                        currentTextMain = currentTextMain,
                                        currentTextSub = currentTextSub,
                                        currentAccent = currentAccent,
                                        currentBorder = currentBorder,
                                        onClick = { onDestinationSelected(place) },
                                        onToggleSaved = { FavoritesManager.toggleFavorite(place.id) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ── Saved Places horizontal row ───────────────────────────
                    if (selectedCatIdx == 0 && savedPlaces.isNotEmpty()) {
                        item(key = "saved_spacer") { Spacer(Modifier.height(24.dp)) }
                        item(key = "saved_header") {
                            SectionHeader(
                                title = context.getString(R.string.saved_places),
                                onSeeAll = onSavedClick,
                                currentTextMain = currentTextMain
                            )
                        }
                        item(key = "saved_spacer2") { Spacer(Modifier.height(12.dp)) }
                        item(key = "saved_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(savedPlaces, key = { it.id }) { place ->
                                    PlaceCard(
                                        place = place,
                                        isSaved = true,
                                        isDarkMode = isDarkMode,
                                        currentCardBg = currentCardBg,
                                        currentTextMain = currentTextMain,
                                        currentTextSub = currentTextSub,
                                        currentAccent = currentAccent,
                                        currentBorder = currentBorder,
                                        onClick = { onDestinationSelected(place) },
                                        onToggleSaved = { FavoritesManager.toggleFavorite(place.id) }
                                    )
                                }
                            }
                        }
                    }

                    // ── Parking Assistant Card ────────────────────────────────
                    if (selectedCatIdx == 0) {
                        item(key = "parking_spacer") { Spacer(Modifier.height(24.dp)) }
                        item(key = "parking_header") {
                            SectionHeader(
                                title = "Parking Assistant",
                                onSeeAll = onParkingClick,
                                currentTextMain = currentTextMain
                            )
                        }
                        item(key = "parking_spacer2") { Spacer(Modifier.height(12.dp)) }
                        item(key = "parking_card") {
                            ParkingHomeCard(
                                isDarkMode = isDarkMode,
                                currentCardBg = currentCardBg,
                                currentTextMain = currentTextMain,
                                currentTextSub = currentTextSub,
                                currentAccent = currentAccent,
                                onParkingClick = onParkingClick
                            )
                        }
                    }

                    // ── Stores list ───────────────────────────────────────────
                    item(key = "stores_spacer") { Spacer(Modifier.height(28.dp)) }
                    item(key = "stores_header") {
                        val headerTitle = categories.getOrNull(selectedCatIdx)
                            ?.label
                            ?.let { if (it == "All") "All Stores" else "$it Stores" }
                            ?: "All Stores"
                        SectionHeader(title = headerTitle, onSeeAll = {}, currentTextMain = currentTextMain)
                    }
                    item(key = "stores_spacer2") { Spacer(Modifier.height(12.dp)) }

                    if (displayedPlaces.isEmpty()) {
                        item(key = "stores_empty") { EmptyState(currentTextSub) }
                    } else {
                        items(displayedPlaces, key = { it.id }) { place ->
                            StoreRow(
                                place = place,
                                isSaved = favoriteIds.contains(place.id),
                                isDarkMode = isDarkMode,
                                currentCardBg = currentCardBg,
                                currentTextMain = currentTextMain,
                                currentTextSub = currentTextSub,
                                currentAccent = currentAccent,
                                currentBorder = currentBorder,
                                onClick = { onDestinationSelected(place) },
                                onToggleSaved = { FavoritesManager.toggleFavorite(place.id) },
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
                item(key = "spacer_bottom") { Spacer(Modifier.height(12.dp)) }
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
                    2 -> onParkingClick()
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
    val cardBg = if (selected) {
        if (isDarkMode) Color(0xFF102733) else currentAccent
    } else {
        if (isDarkMode) GlassCardBg.copy(alpha = 0.5f) else Color.White
    }

    val iconColor = if (selected) {
        if (isDarkMode) CyanGlow else Color.White
    } else {
        currentTextSub
    }

    val textColor = if (selected) {
        if (isDarkMode) CyanGlow else Color.White
    } else {
        currentTextSub
    }

    val borderColor = if (selected) currentAccent else currentBorder

    Box(
        modifier = Modifier
            .size(80.dp)
            .background(cardBg, RoundedCornerShape(18.dp))
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor), RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val ic = category.icon) {
                is ImageVector -> Icon(
                    imageVector = ic,
                    contentDescription = category.label,
                    tint = iconColor,
                    modifier = Modifier.size(26.dp)
                )
                is Painter -> Icon(
                    painter = ic,
                    contentDescription = category.label,
                    tint = iconColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = category.label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    Card(
        modifier = Modifier
            .width(150.dp)
            .clickable { onClick() }
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
                        imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = if (isSaved) currentAccent else currentTextSub,
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
private fun StoreRow(
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

        // Bookmark button
        IconButton(onClick = onToggleSaved, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = "Save place",
                tint = if (isSaved) currentAccent else currentTextSub,
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
    val navBg = if (isDarkMode) GlassCardBg.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.92f)
    val navBorder = if (isDarkMode) CyanGlow.copy(alpha = 0.15f) else currentAccent.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(navBg, RoundedCornerShape(28.dp))
                .border(BorderStroke(1.dp, navBorder), RoundedCornerShape(28.dp))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { idx, item ->
                val active = activeIndex == idx
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSelect(idx) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier.then(
                            if (active)
                                Modifier
                                    .background(currentAccent.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                            else Modifier
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (active) currentAccent else currentTextSub,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) currentAccent else currentTextSub
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty State
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(currentTextSub: Color) {
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
// Parking Home Card Component
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
    val borderCol = if (isDarkMode) CyanGlow.copy(alpha = 0.15f) else currentAccent.copy(alpha = 0.12f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(BorderStroke(1.dp, borderCol), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = currentCardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onParkingClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stylized forward-facing car illustration + location pin
            Box(
                modifier = Modifier.size(width = 90.dp, height = 70.dp),
                contentAlignment = Alignment.Center
            ) {
                ParkingCarIllustration(isDarkMode = isDarkMode, currentAccent = currentAccent)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Parking Assistant",
                        color = currentTextMain,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = currentAccent,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "New",
                            color = if (isDarkMode) DeepNavyBg else Color.White,
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

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open Parking Assistant",
                tint = currentAccent,
                modifier = Modifier.size(20.dp)
            )
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