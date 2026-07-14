package com.example.mallar.ui.auth

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.R
import com.example.mallar.ui.theme.Teal
import com.example.mallar.ui.theme.White
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun WelcomeScreen(
    onSignInClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onSkipClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    var backPressedTime by remember { mutableLongStateOf(0L) }

    val isDarkMode by com.example.mallar.data.AppPreferences.isDarkMode.collectAsState()

    // Ensure dark icons on white background
    SideEffect {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkMode
        }
    }

    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < 2000) {
            (context as? android.app.Activity)?.finish()
        } else {
            backPressedTime = currentTime
            Toast.makeText(context, context.getString(R.string.press_back_exit), Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkMode) com.example.mallar.ui.theme.DarkBackground else Color.White)
    ) {
        // Decorative background element
        Image(
            painter = painterResource(id = R.drawable.dekor),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .alpha(0.3f),
            contentScale = ContentScale.Fit
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // Heading
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.welcome_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDarkMode) White else Teal,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // Main Brands Illustration
            Image(
                painter = painterResource(id = R.drawable.brands_image),
                contentDescription = "Major Brands",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.weight(1f))

            // ── Option B: Two separate buttons ───────────────────────────────

            // Sign In Button (primary)
            Button(
                onClick = onSignInClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .shadow(12.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Teal,
                    contentColor = White
                )
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.sign_in),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sign Up Button (secondary / outlined)
            OutlinedButton(
                onClick = onSignUpClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Teal
                )
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.sign_up),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Skip Button
            TextButton(
                onClick = onSkipClick,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.skip_for_now),
                    color = if (isDarkMode) White.copy(0.7f) else Teal.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
