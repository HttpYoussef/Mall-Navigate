package com.example.mallar.ui.parking

import android.text.format.DateFormat
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
import com.example.mallar.ui.theme.*
import java.util.*

// Brand colors matching Homescreen.kt
private val HomePrimary      = Color(0xFF258799)
private val HomePrimaryLight = Color(0xFF2fa3b8)
private val HomePrimaryDark  = Color(0xFF1a6b78)
private val HomeSurface      = Color(0xFFF7F9FA)
private val HomeCard         = Color(0xFFFFFFFF)
private val HomeTextMain     = Color(0xFF1A1A2E)
private val HomeTextSub      = Color(0xFF888EA8)

@Composable
fun ParkingHomeScreen(
    onBackClick: () -> Unit,
    onSaveLocationClick: () -> Unit,
    onNavigateToCarClick: () -> Unit,
    onEditLocationClick: () -> Unit
) {
    val isDarkMode by com.example.mallar.data.AppPreferences.isDarkMode.collectAsState()
    val parkingLocation by ParkingManager.parkingLocation.collectAsState()

    val currentSurface  = if (isDarkMode) DarkBackground else HomeSurface
    val currentCard     = if (isDarkMode) DarkCard       else HomeCard
    val currentTextMain = if (isDarkMode) DarkTextPrimary   else HomeTextMain
    val currentTextSub  = if (isDarkMode) DarkTextSecondary else HomeTextSub

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
                    color = if (isDarkMode) DarkSurface else Color.Black.copy(0.05f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDarkMode) White else TextPrimary
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "My Parking",
                    color = currentTextMain,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = if (isDarkMode) DarkSurface else Color.Black.copy(0.05f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = "Car",
                            tint = if (isDarkMode) White else TextPrimary,
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
                        currentTextMain = currentTextMain,
                        currentTextSub = currentTextSub,
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
                        listOf(Color(0xFF0F5F5F), Color(0xFF0A3D42), DarkBackground)
                    else
                        listOf(HomePrimary, HomePrimaryLight, Color(0xFF9dd8e2), Color(0xFFe8f6f8))
                )
            )
            .border(1.dp, if (isDarkMode) Color.White.copy(0.1f) else Color.Transparent, RoundedCornerShape(24.dp))
            .padding(24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(
                text = "Park & Find Your Car",
                color = if (isDarkMode) White else Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Take a photo of the parking column. AI will automatically read the zone, slot and floor so you can find your way back.",
                color = if (isDarkMode) White.copy(0.8f) else Color.White.copy(0.85f),
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
        colors = ButtonDefaults.buttonColors(containerColor = HomePrimary)
    ) {
        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = White)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Save Parking Location",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = White
        )
    }

    Spacer(Modifier.height(32.dp))

    // Features / How to use guide
    Text(
        text = "How It Works",
        color = currentTextMain,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start
    )
    Spacer(Modifier.height(16.dp))

    ParkingStepItem(
        icon = Icons.Default.DirectionsCar,
        title = "1. Park Your Car",
        desc = "Find a spot and prepare to scan the nearest column marker.",
        currentTextMain = currentTextMain,
        currentTextSub = currentTextSub
    )
    Spacer(Modifier.height(16.dp))

    ParkingStepItem(
        icon = Icons.Default.QrCodeScanner,
        title = "2. Scan Column",
        desc = "Point your camera at the column sign (e.g. B-12). ML Kit reads it instantly.",
        currentTextMain = currentTextMain,
        currentTextSub = currentTextSub
    )
    Spacer(Modifier.height(16.dp))

    ParkingStepItem(
        icon = Icons.Default.PinDrop,
        title = "3. Navigation Saved",
        desc = "Your location is stored. Navigate back to it from anywhere in the mall.",
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
                .background(HomePrimary.copy(0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = HomePrimary, modifier = Modifier.size(20.dp))
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
    currentTextMain: Color,
    currentTextSub: Color,
    onNavigateClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    // Single high-fidelity details card matching mockup Screen 3
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151B26)),
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
                    text = "Saved Location",
                    color = Color(0xFF00BCD4),
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
                        contentDescription = "Delete Location",
                        tint = RedAccent.copy(0.7f),
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
                    text = "${location.zone}-${location.slot}",
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Location",
                    tint = Color(0xFF00BCD4),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Floor ${location.floor}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Color.White.copy(0.08f))
            Spacer(Modifier.height(20.dp))

            // Detail rows inside the card
            DetailRow(label = "Zone", value = location.zone, Color.White, Color.White.copy(0.6f))
            HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 10.dp))
            
            DetailRow(label = "Slot", value = location.slot, Color.White, Color.White.copy(0.6f))
            HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 10.dp))
            
            DetailRow(label = "Floor", value = location.floor, Color.White, Color.White.copy(0.6f))
            HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 10.dp))
            
            val dateString = DateFormat.format("MMM dd, yyyy - hh:mm a", Date(location.savedAt)).toString()
            DetailRow(label = "Saved Time", value = dateString, Color.White, Color.White.copy(0.6f))
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
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4))
    ) {
        Icon(Icons.Default.NearMe, contentDescription = null, tint = White)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "NAVIGATE TO CAR",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = White
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
        border = BorderStroke(2.dp, Color(0xFF00BCD4)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00BCD4))
    ) {
        Icon(Icons.Default.Map, contentDescription = null, tint = Color(0xFF00BCD4))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "VIEW ON MAP",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00BCD4)
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
