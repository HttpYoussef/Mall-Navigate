package com.example.mallar.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mallar.R
import com.example.mallar.data.Place
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.data.WesternDigits
import com.example.mallar.data.bidiIsolated
import com.example.mallar.ui.theme.*
import com.example.mallar.ui.localization.NavigationState

// NavigationState is defined in LogoScanScreen.kt

private val StoreGlowDeep1 = Color(0xFF1A1A2E) // theme-lint:allow decorative ambient glow, fixed by design
private val StoreGlowDeep2 = Color(0xFF16213E) // theme-lint:allow decorative ambient glow, fixed by design
private val LocationPinAccent = Color(0xFFE53935) // theme-lint:allow location-pin accent, fixed by design, not a status color
private val MapModeButtonBg = Color(0xFF444444) // theme-lint:allow secondary action button, fixed neutral gray by design

@Composable
fun StoreDetailScreen(
    place: Place,
    onBackClick: () -> Unit,
    onStartNavigation: (Boolean) -> Unit
) {
    val isDarkMode by com.example.mallar.data.AppPreferences.isDarkMode.collectAsState()
    val context = LocalContext.current
    // Compute distance synchronously so it's ready on first frame
    val distM = remember(place) {
        val dx = place.x - 319f
        val dy = place.y - 227f
        (kotlin.math.sqrt(dx * dx + dy * dy) * 0.9f).toInt().coerceIn(80, 480)
    }
    val mins = remember(distM) { (distM / 60).coerceIn(2, 10) }

    // Store into shared nav state
    LaunchedEffect(place) {
        NavigationState.selectedPlace      = place
        NavigationState.estimatedDistance  = distM
        NavigationState.estimatedMinutes   = mins
    }

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MallTheme.colors.screenBackground)
    ) {
        // ── Background gradient ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        if (isDarkMode) listOf(StoreGlowDeep1, StoreGlowDeep2, MallTheme.colors.screenBackground)
                        else listOf(
                            MallTheme.colors.accent.copy(alpha = 0.1f), // theme-lint:allow decorative ambient glow
                            MallTheme.colors.accent.copy(alpha = 0.05f), // theme-lint:allow decorative ambient glow
                            Color.Transparent
                        )
                    )
                )
        )

        // ── Top Bar ──────────────────────────────────────────────────────────
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
                color = MallTheme.colors.textPrimary.copy(alpha = 0.08f) // theme-lint:allow adaptive low-alpha tint for translucent icon-button chrome
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = MallTheme.colors.textPrimary)
                }
            }

            Surface(
                onClick = onBackClick,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MallTheme.colors.textPrimary.copy(alpha = 0.08f) // theme-lint:allow adaptive low-alpha tint for translucent icon-button chrome
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, stringResource(R.string.close), tint = MallTheme.colors.textPrimary)
                }
            }
        }

        // ── Central Card ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Store logo card
            Surface(
                modifier = Modifier
                    .size(130.dp)
                    .shadow(if (isDarkMode) 0.dp else 24.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                color = MallTheme.colors.surface
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("file:///android_asset/${place.logo}")
                        .crossfade(true)
                        .build(),
                    contentDescription = place.brand,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                        .padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Store name
            Text(
                text = place.brand,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                color = MallTheme.colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Distance & time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.LocationOn, null, tint = LocationPinAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(R.string.distance_meters, WesternDigits.format(distM)),
                    color = MallTheme.colors.textSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(16.dp))
                Surface(
                    shape = CircleShape,
                    color = LocationPinAccent,
                    modifier = Modifier.size(6.dp)
                ) {}
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    pluralStringResource(R.plurals.duration_minutes, mins, WesternDigits.format(mins)),
                    color = MallTheme.colors.textSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Navigation Buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { 
                        // We need to run A* if we have a start place
                        val start = NavigationState.startPlace
                        if (start != null) {
                            val mallGraph = MallGraphRepository.load(context)
                            val path = MallGraphRepository.aStar(mallGraph, start.id, place.id)
                            NavigationState.aStarPath = path
                            if (path != null) {
                                NavigationState.estimatedDistance = (path.totalDistancePx * 0.25).toInt()
                                NavigationState.estimatedMinutes = (NavigationState.estimatedDistance / 72).coerceIn(1, 20)
                            }
                        }
                        onStartNavigation(true) 
                    },
                    modifier = Modifier.weight(1f).height(60.dp).shadow(16.dp, RoundedCornerShape(30.dp)),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MallTheme.colors.accent)
                ) {
                    Icon(Icons.Filled.ViewInAr, null, tint = MallTheme.colors.onAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ar_mode), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MallTheme.colors.onAccent)
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Button(
                    onClick = { 
                        // We need to run A* if we have a start place
                        val start = NavigationState.startPlace
                        if (start != null) {
                            val mallGraph = MallGraphRepository.load(context)
                            val path = MallGraphRepository.aStar(mallGraph, start.id, place.id)
                            NavigationState.aStarPath = path
                            if (path != null) {
                                NavigationState.estimatedDistance = (path.totalDistancePx * 0.25).toInt()
                                NavigationState.estimatedMinutes = (NavigationState.estimatedDistance / 72).coerceIn(1, 20)
                            }
                        }
                        onStartNavigation(false) 
                    },
                    modifier = Modifier.weight(1f).height(60.dp).shadow(16.dp, RoundedCornerShape(30.dp)),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MapModeButtonBg)
                ) {
                    Icon(Icons.Filled.Map, null, tint = MallTheme.colors.onScrim, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.map_mode), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MallTheme.colors.onScrim)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.choose_nav_mode_to, place.brand.bidiIsolated()),
                color = MallTheme.colors.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
