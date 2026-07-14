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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mallar.data.Place
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.ui.theme.*
import com.example.mallar.ui.localization.NavigationState

// NavigationState is defined in LogoScanScreen.kt

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
            .background(if (isDarkMode) com.example.mallar.ui.theme.DarkBackground else Color(0xFFF5F7FA))
    ) {
        // ── Background gradient ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        if (isDarkMode) listOf(Color(0xFF1A1A2E), Color(0xFF16213E), com.example.mallar.ui.theme.DarkBackground)
                        else listOf(com.example.mallar.ui.theme.Teal.copy(0.1f), com.example.mallar.ui.theme.TealLight.copy(0.05f), Color.Transparent)
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
                color = if (isDarkMode) White.copy(alpha = 0.15f) else Color.Black.copy(0.05f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (isDarkMode) White else Color.Black)
                }
            }

            Surface(
                onClick = onBackClick,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = if (isDarkMode) White.copy(alpha = 0.15f) else Color.Black.copy(0.05f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Close", tint = if (isDarkMode) White else Color.Black)
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
                color = if (isDarkMode) com.example.mallar.ui.theme.DarkCard else White
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
                color = if (isDarkMode) com.example.mallar.ui.theme.DarkTextPrimary else com.example.mallar.ui.theme.TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Distance & time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.LocationOn, null, tint = RedAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "${distM}m",
                    color = if (isDarkMode) White.copy(alpha = 0.8f) else com.example.mallar.ui.theme.TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(16.dp))
                Surface(
                    shape = CircleShape,
                    color = RedAccent,
                    modifier = Modifier.size(6.dp)
                ) {}
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "${mins}min",
                    color = if (isDarkMode) White.copy(alpha = 0.8f) else com.example.mallar.ui.theme.TextSecondary,
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
                    colors = ButtonDefaults.buttonColors(containerColor = Teal)
                ) {
                    Icon(Icons.Filled.ViewInAr, null, tint = White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AR", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = White)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Icon(Icons.Filled.Map, null, tint = White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Map", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Choose your navigation mode to ${place.brand}",
                color = if (isDarkMode) White.copy(alpha = 0.5f) else com.example.mallar.ui.theme.TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
