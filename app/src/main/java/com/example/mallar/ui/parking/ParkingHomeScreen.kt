package com.example.mallar.ui.parking

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.R
import com.example.mallar.data.ParkingLocation
import com.example.mallar.data.ParkingManager
import com.example.mallar.data.Timestamps
import com.example.mallar.data.bidiIsolated
import com.example.mallar.ui.theme.*

// Bespoke promo banner illustration fills (Ticket 01 precedent)
private val ParkingBannerDeepTeal   = Color(0xFF0F5F5F) // theme-lint:allow bespoke illustration fill (locked splash teal, Ticket 01)
private val ParkingBannerDarkMid    = Color(0xFF0A3D42) // theme-lint:allow bespoke illustration fill
private val ParkingBannerLightPale1 = Color(0xFF9dd8e2) // theme-lint:allow bespoke illustration fill
private val ParkingBannerLightPale2 = Color(0xFFe8f6f8) // theme-lint:allow bespoke illustration fill

@Composable
fun ParkingHomeScreen(
    onBackClick: () -> Unit,
    onSaveLocationClick: () -> Unit,
    onNavigateToCarClick: () -> Unit,
    onEditLocationClick: () -> Unit
) {
    val isDarkMode by com.example.mallar.data.AppPreferences.isDarkMode.collectAsState()
    val parkingLocation by ParkingManager.parkingLocation.collectAsState()

    val currentSurface  = MallTheme.colors.screenBackground
    val currentCard     = MallTheme.colors.surface
    val currentTextMain = MallTheme.colors.textPrimary
    val currentTextSub  = MallTheme.colors.textSecondary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // --- Top bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBackClick,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MallTheme.colors.surface
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MallTheme.colors.textPrimary
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.park_home_title),
                    color = currentTextMain,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MallTheme.colors.surface
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = stringResource(R.string.park_car_cd),
                            tint = MallTheme.colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // --- Content ---
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val loc = parkingLocation
                if (loc == null) {
                    EmptyParkingState(onSaveLocationClick, isDarkMode, currentTextMain, currentTextSub)
                } else {
                    SavedParkingState(
                        location = loc,
                        isDarkMode = isDarkMode,
                        currentCard = currentCard,
                        onNavigateClick = onNavigateToCarClick,
                        onEditClick = onEditLocationClick,
                        onDeleteClick = { ParkingManager.deleteLocation() }
                    )
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun EmptyParkingState(
    onSaveClick: () -> Unit,
    isDarkMode: Boolean,
    currentTextMain: Color,
    currentTextSub: Color
) {
    // Shimmer Banner matching Homescreen.kt
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = if (isDarkMode)
                        listOf(ParkingBannerDeepTeal, ParkingBannerDarkMid, DarkBackground)
                    else
                        listOf(MallTheme.colors.accent, MallTheme.colors.brandTeal, ParkingBannerLightPale1, ParkingBannerLightPale2)
                )
            )
            .border(1.dp, if (isDarkMode) MallTheme.colors.hairlineOverlay else Color.Transparent, RoundedCornerShape(24.dp))
            .padding(24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(
                text = stringResource(R.string.park_home_banner_title),
                color = MallTheme.colors.onAccent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.park_home_banner_desc),
                color = MallTheme.colors.onAccent.copy(alpha = 0.85f), // theme-lint:allow decorative banner subtitle
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }

    Spacer(Modifier.height(30.dp))

    // Save Parking Location Button
    Button(
        onClick = onSaveClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(if (isDarkMode) 0.dp else 6.dp, RoundedCornerShape(27.dp)),
        shape = RoundedCornerShape(27.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MallTheme.colors.accent)
    ) {
        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MallTheme.colors.onAccent)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.park_home_btn_save),
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MallTheme.colors.onAccent
        )
    }

    Spacer(Modifier.height(32.dp))

    // Features / How to use guide
    Text(
        text = stringResource(R.string.park_home_how_it_works),
        color = currentTextMain,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start
    )
    Spacer(Modifier.height(16.dp))

    ParkingStepItem(
        icon = Icons.Default.DirectionsCar,
        title = stringResource(R.string.park_home_step1_title),
        desc = stringResource(R.string.park_home_step1_desc),
        currentTextMain = currentTextMain,
        currentTextSub = currentTextSub
    )
    Spacer(Modifier.height(16.dp))

    ParkingStepItem(
        icon = Icons.Default.QrCodeScanner,
        title = stringResource(R.string.park_home_step2_title),
        desc = stringResource(R.string.park_home_step2_desc),
        currentTextMain = currentTextMain,
        currentTextSub = currentTextSub
    )
    Spacer(Modifier.height(16.dp))

    ParkingStepItem(
        icon = Icons.Default.PinDrop,
        title = stringResource(R.string.park_home_step3_title),
        desc = stringResource(R.string.park_home_step3_desc),
        currentTextMain = currentTextMain,
        currentTextSub = currentTextSub
    )
}

@Composable
private fun ParkingStepItem(
    icon: ImageVector,
    title: String,
    desc: String,
    currentTextMain: Color,
    currentTextSub: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MallTheme.colors.accent.copy(alpha = 0.1f), CircleShape), // theme-lint:allow decorative icon-badge tint
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MallTheme.colors.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = currentTextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(text = desc, color = currentTextSub, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun SavedParkingState(
    location: ParkingLocation,
    isDarkMode: Boolean,
    currentCard: Color,
    onNavigateClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    // Explicit pure black/white for this hero card's body text, per user request -
    // overrides the AA-tuned textPrimary/textSecondary tokens for this card only.
    val heroTextColor = if (isDarkMode) Color.White else Color.Black // theme-lint:allow explicit hero-card contrast per user request
    val heroTextSubColor = heroTextColor.copy(alpha = 0.6f) // theme-lint:allow explicit hero-card contrast per user request

    // Single high-fidelity details card matching mockup Screen 3
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MallTheme.colors.border, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = currentCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.park_home_saved_location),
                    color = MallTheme.colors.accentText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                // Small subtle delete icon in the top corner of the card
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.park_home_delete_location_cd),
                        tint = MallTheme.colors.errorText.copy(alpha = 0.7f), // theme-lint:allow decorative delete-icon de-emphasis
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Large Spot Name (clickable to edit)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditClick() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${location.zone}-${location.slot}".bidiIsolated(),
                    color = heroTextColor,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.park_home_edit_location_cd),
                    tint = MallTheme.colors.accentText,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.floor_label, location.floor.bidiIsolated()),
                color = heroTextSubColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MallTheme.colors.divider)
            Spacer(Modifier.height(20.dp))

            // Detail rows inside the card
            DetailRow(label = stringResource(R.string.park_field_zone), value = location.zone.bidiIsolated(), heroTextColor, heroTextSubColor)
            HorizontalDivider(color = MallTheme.colors.divider, modifier = Modifier.padding(vertical = 10.dp))

            DetailRow(label = stringResource(R.string.park_field_slot), value = location.slot.bidiIsolated(), heroTextColor, heroTextSubColor)
            HorizontalDivider(color = MallTheme.colors.divider, modifier = Modifier.padding(vertical = 10.dp))

            DetailRow(label = stringResource(R.string.park_field_floor), value = location.floor.bidiIsolated(), heroTextColor, heroTextSubColor)
            HorizontalDivider(color = MallTheme.colors.divider, modifier = Modifier.padding(vertical = 10.dp))

            val dateString = Timestamps.format(location.savedAt).bidiIsolated()
            DetailRow(label = stringResource(R.string.park_home_saved_time), value = dateString, heroTextColor, heroTextSubColor)
        }
    }

    Spacer(Modifier.height(30.dp))

    // Mockup buttons
    // Button 1: NAVIGATE TO CAR (cyan/teal fill)
    Button(
        onClick = onNavigateClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(27.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MallTheme.colors.accent)
    ) {
        Icon(Icons.Default.NearMe, contentDescription = null, tint = MallTheme.colors.onAccent)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.park_home_btn_navigate),
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MallTheme.colors.onAccent
        )
    }

    Spacer(Modifier.height(12.dp))

    // Button 2: VIEW ON MAP (outlined style)
    OutlinedButton(
        onClick = onNavigateClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(2.dp, MallTheme.colors.accentText),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MallTheme.colors.accentText)
    ) {
        Icon(Icons.Default.Map, contentDescription = null, tint = MallTheme.colors.accentText)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.park_home_btn_view_on_map),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MallTheme.colors.accentText
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, currentTextMain: Color, currentTextSub: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = currentTextSub, fontSize = 13.sp)
        Text(text = value, color = currentTextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
