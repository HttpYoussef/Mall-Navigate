package com.example.mallar.ui.parking

import android.graphics.Bitmap
import android.text.format.DateFormat
import java.util.Date
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.data.ParkingLocation
import com.example.mallar.data.ParkingManager
import com.example.mallar.ml.ParkingOcrEngine
import com.example.mallar.ui.theme.*

private val HomePrimary      = Color(0xFF258799)
private val HomeSurface      = Color(0xFFF7F9FA)
private val HomeCard         = Color(0xFFFFFFFF)
private val HomeTextMain     = Color(0xFF1A1A2E)
private val HomeTextSub      = Color(0xFF888EA8)

@Composable
fun ParkingScanResultScreen(
    onBackClick: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val isDarkMode by com.example.mallar.data.AppPreferences.isDarkMode.collectAsState()
    val capturedBitmap = remember { ParkingCameraState.capturedBitmap }

    val currentSurface  = if (isDarkMode) DarkBackground else HomeSurface
    val currentCard     = if (isDarkMode) DarkCard       else HomeCard
    val currentTextMain = if (isDarkMode) DarkTextPrimary   else HomeTextMain
    val currentTextSub  = if (isDarkMode) DarkTextSecondary else HomeTextSub

    var isProcessing by remember { mutableStateOf(true) }
    var zoneVal by remember { mutableStateOf("") }
    var slotVal by remember { mutableStateOf("") }
    var floorVal by remember { mutableStateOf("") }
    var rawOcrText by remember { mutableStateOf("") }
    var isLowConfidence by remember { mutableStateOf(false) }

    var showEditDialog by remember { mutableStateOf(false) }
    var editFieldType by remember { mutableStateOf("") } // "zone", "slot", or "floor"
    var editValue by remember { mutableStateOf("") }

    // Run OCR asynchronously
    LaunchedEffect(capturedBitmap) {
        val bitmap = capturedBitmap
        if (bitmap != null) {
            val parsed = ParkingOcrEngine.recognizeText(bitmap)
            zoneVal = parsed.zone
            slotVal = parsed.slot
            floorVal = parsed.floor
            rawOcrText = parsed.rawOcrText
            isLowConfidence = parsed.isZoneFallback || parsed.isSlotFallback || parsed.isFloorFallback
            isProcessing = false
        } else {
            isProcessing = false
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit ${editFieldType.replaceFirstChar { it.uppercase() }}", color = currentTextMain) },
            text = {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = currentTextMain,
                        unfocusedTextColor = currentTextMain,
                        focusedBorderColor = HomePrimary,
                        unfocusedBorderColor = currentTextSub.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (editFieldType) {
                            "zone" -> zoneVal = editValue.uppercase()
                            "slot" -> slotVal = editValue.filter { it.isDigit() }
                            "floor" -> floorVal = editValue.uppercase()
                        }
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HomePrimary)
                ) {
                    Text("OK", color = White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = HomePrimary)
                }
            }
        )
    }

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
                    text = "Scan Result",
                    color = currentTextMain,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // --- Scrollable Body ---
            if (isProcessing) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = HomePrimary, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Reading column text...",
                            color = currentTextMain,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Snapped Photo Display with targeting brackets overlay
                    capturedBitmap?.let {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(20.dp)),
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Captured Spot",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Cyan targeting brackets overlay
                                Canvas(modifier = Modifier.size(100.dp)) {
                                    val s = size.minDimension
                                    val c = center
                                    val r = s * 0.48f
                                    val arm = s * 0.20f
                                    val gap = s * 0.08f
                                    val col = Color(0xFF00BCD4) // Teal cyan
                                    listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
                                        drawLine(col, Offset(c.x + sx * gap, c.y + sy * r),
                                            Offset(c.x + sx * (gap + arm), c.y + sy * r), strokeWidth = 3f, cap = StrokeCap.Round)
                                        drawLine(col, Offset(c.x + sx * r, c.y + sy * gap),
                                            Offset(c.x + sx * r, c.y + sy * (gap + arm)), strokeWidth = 3f, cap = StrokeCap.Round)
                                    }
                                }
                            }
                        }
                    }

                    if (rawOcrText.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(if (isDarkMode) 1.dp else 0.dp, Color.White.copy(0.05f), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = currentCard),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkMode) 0.dp else 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Raw Detected Text",
                                        color = currentTextMain,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isLowConfidence) {
                                        Surface(
                                            color = Color(0xFFFF9800).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "Low Confidence / Partial Match",
                                                color = Color(0xFFFF9800),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = rawOcrText,
                                    color = currentTextMain,
                                    fontSize = 14.sp,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Detected Location Card with clean read-only table details
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151B26)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "Detected Location",
                                color = Color(0xFF00BCD4),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(16.dp))

                            // Zone Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editFieldType = "zone"
                                        editValue = zoneVal
                                        showEditDialog = true
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Zone", color = Color.White.copy(0.6f), fontSize = 14.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = zoneVal.ifBlank { "(not detected)" },
                                        color = if (zoneVal.isBlank()) Color.White.copy(0.3f) else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF00BCD4), modifier = Modifier.size(16.dp))
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(0.08f), modifier = Modifier.padding(vertical = 4.dp))

                            // Slot Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editFieldType = "slot"
                                        editValue = slotVal
                                        showEditDialog = true
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Slot", color = Color.White.copy(0.6f), fontSize = 14.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = slotVal.ifBlank { "(not detected)" },
                                        color = if (slotVal.isBlank()) Color.White.copy(0.3f) else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF00BCD4), modifier = Modifier.size(16.dp))
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(0.08f), modifier = Modifier.padding(vertical = 4.dp))

                            // Floor Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editFieldType = "floor"
                                        editValue = floorVal
                                        showEditDialog = true
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Floor", color = Color.White.copy(0.6f), fontSize = 14.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = floorVal.ifBlank { "(not detected)" },
                                        color = if (floorVal.isBlank()) Color.White.copy(0.3f) else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF00BCD4), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Green checkmark Status Box
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF1B5E20).copy(0.3f), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F261B))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Parking location saved",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val dateString = remember {
                                    DateFormat.format("MMMM dd, yyyy - hh:mm a", Date()).toString()
                                }
                                Text(
                                    text = dateString,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(30.dp))

                    // Save Location Button
                    Button(
                        onClick = {
                            val loc = ParkingLocation(
                                zone = zoneVal.ifBlank { "B" },
                                slot = slotVal.ifBlank { "12" },
                                floor = floorVal.ifBlank { "P1" },
                                savedAt = System.currentTimeMillis()
                            )
                            ParkingManager.saveLocation(loc)
                            onSaveSuccess()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4)) // cyan
                    ) {
                        Text(
                            text = "SAVE LOCATION",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}
