package com.example.mallar.ui.profile

import android.app.Activity
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.mallar.ui.components.StoreLogoContainer
import com.example.mallar.data.AppPreferences
import com.example.mallar.data.FavoritesManager
import com.example.mallar.data.Place
import com.example.mallar.data.PlaceRepository
import com.example.mallar.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val context = LocalContext.current
    val isDarkMode by AppPreferences.isDarkMode.collectAsState()
    val currentLang by AppPreferences.language.collectAsState()
    val favoriteIds by FavoritesManager.favorites.collectAsState()
    val colorScheme = MaterialTheme.colorScheme

    // Load all places to match favorites
    var allPlaces by remember { mutableStateOf<List<Place>>(emptyList()) }
    LaunchedEffect(Unit) {
        allPlaces = withContext(Dispatchers.IO) { PlaceRepository.load(context) }
    }

    val favoritePlaces = remember(favoriteIds, allPlaces) {
        allPlaces.filter { favoriteIds.contains(it.id) }
    }

    // User info
    val user = FirebaseAuth.getInstance().currentUser
    val displayName = user?.displayName ?: ""
    val phoneNumber = user?.phoneNumber ?: ""

    // Avatar photo URI (persisted in SharedPreferences)
    val avatarPrefs = remember {
        context.getSharedPreferences("mallar_avatar", android.content.Context.MODE_PRIVATE)
    }
    var avatarUri by remember {
        mutableStateOf(avatarPrefs.getString("avatar_uri", null)?.let { Uri.parse(it) })
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist the URI for next launch
            // Take persistent read permission so it survives reboots
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* best effort */ }
            avatarUri = uri
            avatarPrefs.edit().putString("avatar_uri", uri.toString()).apply()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
    ) {
        // ── Top bar ─────────────────────────────────────────────────────────
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
                color = colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.profile),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Avatar + Name Card ──────────────────────────────────────────────
        ProfileAvatarCard(
            displayName = displayName,
            phone = phoneNumber,
            avatarUri = avatarUri,
            isLoggedIn = user != null,
            colorScheme = colorScheme,
            onEditAvatar = { photoPickerLauncher.launch("image/*") }
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ── Favorite Stores ─────────────────────────────────────────────────
        SectionLabel(
            text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.favorite_stores),
            icon = Icons.Default.Favorite,
            color = colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (favoritePlaces.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.no_favorites),
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.bookmark_hint),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surfaceVariant
            ) {
                Column {
                    favoritePlaces.forEachIndexed { index, place ->
                        FavoriteStoreRow(
                            place = place,
                            colorScheme = colorScheme,
                            onRemove = { FavoritesManager.removeFavorite(place.id) }
                        )
                        if (index < favoritePlaces.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Preferences ─────────────────────────────────────────────────────
        SectionLabel(
            text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.preferences),
            icon = Icons.Default.Settings,
            color = colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.surfaceVariant
        ) {
            Column {
                // Language Toggle
                LanguageToggleRow(
                    currentLang = currentLang,
                    colorScheme = colorScheme,
                    onLanguageChange = { lang ->
                        AppPreferences.setLanguage(lang)
                        val locale = java.util.Locale(lang)
                        java.util.Locale.setDefault(locale)
                        val config = android.content.res.Configuration(context.resources.configuration).apply {
                            setLocale(locale)
                            setLayoutDirection(locale)
                        }
                        @Suppress("DEPRECATION")
                        context.resources.updateConfiguration(config, context.resources.displayMetrics)
                        val intent = android.content.Intent(context, com.example.mallar.MainActivity::class.java).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                )

                // Dark Mode Toggle
                DarkModeToggleRow(
                    isDarkMode = isDarkMode,
                    colorScheme = colorScheme,
                    onToggle = { AppPreferences.setDarkMode(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Sign Out / Sign In ──────────────────────────────────────────────
        Button(
            onClick = onLogoutClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (user != null) RedAccent.copy(alpha = 0.1f) else Teal,
                contentColor = if (user != null) RedAccent else White
            )
        ) {
            Icon(
                imageVector = if (user != null) Icons.AutoMirrored.Filled.Logout else Icons.Default.Login,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (user != null) androidx.compose.ui.res.stringResource(com.example.mallar.R.string.sign_out) else androidx.compose.ui.res.stringResource(com.example.mallar.R.string.sign_in),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(40.dp).navigationBarsPadding())
    }
}

// ── Avatar + Name Card ───────────────────────────────────────────────────────
@Composable
private fun ProfileAvatarCard(
    displayName: String,
    phone: String,
    avatarUri: Uri?,
    isLoggedIn: Boolean,
    colorScheme: ColorScheme,
    onEditAvatar: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar with edit overlay
        Box(contentAlignment = Alignment.BottomEnd) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(Teal, TealLight)),
                        CircleShape
                    )
                    .clickable { onEditAvatar() },
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            ImageRequest.Builder(LocalContext.current)
                                .data(avatarUri)
                                .crossfade(true)
                                .build()
                        ),
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                    )
                } else {
                    // Initials or placeholder
                    Text(
                        text = displayName.take(1).uppercase().ifEmpty { "?" },
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
            }

            // Edit badge
            Surface(
                onClick = onEditAvatar,
                modifier = Modifier
                    .size(32.dp)
                    .shadow(4.dp, CircleShape),
                shape = CircleShape,
                color = colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Change photo",
                        tint = Teal,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Name (from signup)
        if (isLoggedIn) {
            Text(
                text = displayName.ifEmpty { "User" },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground
            )
            if (phone.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = phone,
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.guest),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.sign_in_features),
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Section Label ────────────────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String, icon: ImageVector, color: Color) {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Teal,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color.copy(alpha = 0.6f),
            letterSpacing = 0.5.sp
        )
    }
}

// ── Favorite Store Row ───────────────────────────────────────────────────────
@Composable
private fun FavoriteStoreRow(
    place: Place,
    colorScheme: ColorScheme,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StoreLogoContainer(
            place    = place,
            modifier = Modifier.size(42.dp),
            contentPadding = 5.dp,
            fallbackTextSize = 14.sp,
            cornerRadius = 12.dp,
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.brand,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.store_inside_mall),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Language Toggle Row ──────────────────────────────────────────────────────
@Composable
private fun LanguageToggleRow(
    currentLang: String,
    colorScheme: ColorScheme,
    onLanguageChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Language,
            contentDescription = null,
            tint = Teal,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.language),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        // Segmented toggle
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colorScheme.background,
            modifier = Modifier.height(36.dp)
        ) {
            Row {
                LanguageChip(
                    label = "EN",
                    isSelected = currentLang == "en",
                    onClick = { onLanguageChange("en") }
                )
                LanguageChip(
                    label = "عربي",
                    isSelected = currentLang == "ar",
                    onClick = { onLanguageChange("ar") }
                )
            }
        }
    }
}

@Composable
private fun LanguageChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        if (isSelected) Teal else Color.Transparent,
        tween(200),
        label = "langBg"
    )
    val textColor by animateColorAsState(
        if (isSelected) White else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(200),
        label = "langText"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

// ── Dark Mode Toggle Row ─────────────────────────────────────────────────────
@Composable
private fun DarkModeToggleRow(
    isDarkMode: Boolean,
    colorScheme: ColorScheme,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
            contentDescription = null,
            tint = Teal,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.dark_mode),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = isDarkMode,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = White,
                checkedTrackColor = Teal,
                uncheckedThumbColor = colorScheme.onSurfaceVariant,
                uncheckedTrackColor = colorScheme.surfaceVariant
            )
        )
    }
}
