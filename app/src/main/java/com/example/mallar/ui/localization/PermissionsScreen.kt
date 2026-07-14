package com.example.mallar.ui.localization

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.mallar.R
import com.example.mallar.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun PermissionsScreen(onContinueClick: () -> Unit) {
    val context = LocalContext.current
    val isDarkMode by com.example.mallar.data.AppPreferences.isDarkMode.collectAsState(initial = false)
    val colorScheme = MaterialTheme.colorScheme

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var motionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        cameraGranted = isGranted
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        locationGranted = isGranted
    }

    val motionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        motionGranted = isGranted
    }

    val allGranted = cameraGranted && locationGranted && motionGranted

    // Automatic navigation when all permissions are granted
    LaunchedEffect(allGranted) {
        if (allGranted) {
            delay(600) // Small delay to show the final checkmark animation
            onContinueClick()
        }
    }

    // Global Entrance control
    val entranceAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entranceAlpha.animateTo(1f, animationSpec = tween(800))
    }

    Box(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Hero Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer {
                        alpha = entranceAlpha.value
                        translationY = (-20).dp.toPx() * (1f - entranceAlpha.value)
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_main),
                    contentDescription = "logos",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(0.55f)
                )
            }

            // Fixed Layout Sheet
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = colorScheme.surface,
                shadowElevation = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Teal, fontWeight = FontWeight.ExtraBold)) {
                                append("MallAR ")
                            }
                            withStyle(SpanStyle(color = colorScheme.onSurface, fontWeight = FontWeight.Bold)) {
                                append(androidx.compose.ui.res.stringResource(R.string.permissions_title).removePrefix("MallAR "))
                            }
                        },
                        fontSize = 28.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha(entranceAlpha.value)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.permissions_subtitle),
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 16.dp).alpha(entranceAlpha.value)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Permissions list
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PermissionItemFixed(
                            delay = 200,
                            icon = Icons.Outlined.CameraAlt,
                            title = androidx.compose.ui.res.stringResource(R.string.enable_camera),
                            subtitle = "Place path markers",
                            granted = cameraGranted,
                            color = Color(0xFF167D92),
                            onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }
                        )

                        PermissionItemFixed(
                            delay = 350,
                            icon = Icons.Outlined.LocationOn,
                            title = androidx.compose.ui.res.stringResource(R.string.enable_location),
                            subtitle = "Find which floor you are on",
                            granted = locationGranted,
                            color = Color(0xFF2099B9),
                            onClick = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                        )

                        PermissionItemFixed(
                            delay = 500,
                            icon = Icons.Outlined.DirectionsRun,
                            title = androidx.compose.ui.res.stringResource(R.string.enable_motion),
                            subtitle = "Estimate movement speed",
                            granted = motionGranted,
                            color = Color(0xFFC39D51),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    motionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onContinueClick,
                        enabled = allGranted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .shadow(if (allGranted) 12.dp else 0.dp, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Teal,
                            contentColor = White,
                            disabledContainerColor = DividerColor
                        )
                    ) {
                        Text(
                            text = if (allGranted) "..." else androidx.compose.ui.res.stringResource(R.string.continue_btn),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
        }
    }
}

@Composable
private fun PermissionItemFixed(
    delay: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val alphaAnim = remember { Animatable(0f) }
    val translationXAnim = remember { Animatable(30f) }

    LaunchedEffect(Unit) {
        delay(delay.toLong())
        alphaAnim.animateTo(1f, animationSpec = tween(600))
        translationXAnim.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessLow))
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = alphaAnim.value
                translationX = translationXAnim.value.dp.toPx()
            },
        shape = RoundedCornerShape(16.dp),
        color = if (granted) SuccessGreen.copy(alpha = 0.12f) else color.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (granted) SuccessGreen else color.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(if (granted) SuccessGreen else color, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (granted) Icons.Filled.Check else icon,
                    contentDescription = null,
                    tint = White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (granted) SuccessGreen else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
