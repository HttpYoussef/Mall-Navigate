package com.example.mallar.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.R
import com.example.mallar.ui.theme.*
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun SplashScreen(
    isFirstLaunch: Boolean,
    onStartClick: () -> Unit
) {
    // Animation States
    val logoScale = remember { Animatable(0.7f) }
    val logoAlpha = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    val view = LocalView.current
    val context = LocalContext.current

    // Ensure light icons on colored splash background
    SideEffect {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    LaunchedEffect(Unit) {
        // logos Entrance
        logoAlpha.animateTo(1f, animationSpec = tween(1000))
        logoScale.animateTo(1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))

        // Background elements entrance
        delay(200)
        contentAlpha.animateTo(1f, animationSpec = tween(800))

        // Auto-navigate if returning user
        if (!isFirstLaunch) {
            delay(1000) // Stay a bit longer for brand recognition
            onStartClick()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Hero Background
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1.2f))

            // Animated logos
            Image(
                painter = painterResource(id = R.drawable.logo_main),
                contentDescription = "logos",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Animated Start button area
            Column(
                modifier = Modifier.alpha(if (isFirstLaunch) contentAlpha.value else 0f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isFirstLaunch) {
                    Button(
                        onClick = onStartClick,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(64.dp)
                            .shadow(16.dp, RoundedCornerShape(32.dp)),
                        shape = RoundedCornerShape(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = White,
                            contentColor = Teal
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.get_started),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Modern Page indicators
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 8.dp)
                                .background(White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(White.copy(alpha = 0.4f), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(White.copy(alpha = 0.4f), CircleShape)
                        )
                    }
                }
            }

            // --- Loading Indicator for returning users ---
            if (!isFirstLaunch) {
                Spacer(modifier = Modifier.height(48.dp))
                Column(
                    modifier = Modifier.alpha(contentAlpha.value),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = White,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.loading),
                        color = White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp).navigationBarsPadding())
        }
    }
}
