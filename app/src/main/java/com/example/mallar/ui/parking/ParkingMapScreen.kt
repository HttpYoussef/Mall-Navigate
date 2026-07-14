package com.example.mallar.ui.parking

import android.graphics.Paint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.data.ParkingLocation
import com.example.mallar.data.ParkingManager
import com.example.mallar.ui.theme.*

private val HomePrimary      = Color(0xFF258799)
private val HomePrimaryLight = Color(0xFF2fa3b8)
private val HomeSurface      = Color(0xFFF7F9FA)
private val HomeCard         = Color(0xFFFFFFFF)
private val HomeTextMain     = Color(0xFF1A1A2E)
private val HomeTextSub      = Color(0xFF888EA8)

data class ParkingSlot(
    val name: String,
    val zone: String,
    val number: String,
    val x: Float, // Center x of the spot
    val y: Float  // Center y of the spot
)

@Composable
fun ParkingMapScreen(
    onBackClick: () -> Unit
) {
    val isDarkMode by com.example.mallar.data.AppPreferences.isDarkMode.collectAsState()
    val parkingLocation by ParkingManager.parkingLocation.collectAsState()

    val currentSurface  = if (isDarkMode) DarkBackground else HomeSurface
    val currentCard     = if (isDarkMode) DarkCard       else HomeCard
    val currentTextMain = if (isDarkMode) DarkTextPrimary   else HomeTextMain
    val currentTextSub  = if (isDarkMode) DarkTextSecondary else HomeTextSub

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var reCenterTrigger by remember { mutableStateOf(0) }

    val floorLabel = parkingLocation?.floor ?: "P1"
    val zone = parkingLocation?.zone ?: "B"
    val slotNum = parkingLocation?.slot ?: "12"
    val savedSlotName = "$zone-$slotNum"

    // Floor Selector State
    var selectedFloor by remember { mutableStateOf(floorLabel) }

    // Navigation Active state
    var isNavigating by remember { mutableStateOf(false) }

    // Generate slots
    val slots = remember {
        val list = mutableListOf<ParkingSlot>()
        val zones = listOf("A", "B", "C")
        val numbers = listOf("10", "11", "12", "13", "14")
        
        // Define centers of columns
        val colXs = listOf(100f, 250f, 400f)
        val rowYs = listOf(150f, 230f, 310f, 390f, 470f)

        zones.forEachIndexed { zIdx, z ->
            val colX = colXs[zIdx]
            numbers.forEachIndexed { nIdx, num ->
                val rowY = rowYs[nIdx]
                list.add(ParkingSlot(
                    name = "$z-$num",
                    zone = z,
                    number = num,
                    x = colX,
                    y = rowY
                ))
            }
        }
        list
    }

    val savedSlot = slots.firstOrNull { it.name.equals(savedSlotName, ignoreCase = true) }

    // Auto-center on saved slot or entire grid
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(currentSurface)) {
        val canvasWidth = with(density) { maxWidth.toPx() }
        val canvasHeight = with(density) { maxHeight.toPx() }

        LaunchedEffect(savedSlot, canvasWidth, canvasHeight, reCenterTrigger) {
            if (canvasWidth <= 0f || canvasHeight <= 0f) return@LaunchedEffect
            
            if (savedSlot != null) {
                // Center the saved slot on the screen
                scale = 1.3f
                offset = Offset(
                    canvasWidth / 2f - savedSlot.x * scale,
                    canvasHeight / 2.5f - savedSlot.y * scale
                )
            } else {
                // No saved slot. Center the entire 600x700 grid area.
                val mapW = 600f
                val mapH = 700f
                val padFactor = 0.9f
                val fitScaleX = (canvasWidth * padFactor) / mapW
                val fitScaleY = (canvasHeight * padFactor) / mapH
                val fitScale = minOf(fitScaleX, fitScaleY).coerceIn(0.5f, 3.5f)
 
                // Center the grid area
                val offX = (canvasWidth - mapW * fitScale) / 2f
                val offY = (canvasHeight - mapH * fitScale) / 2f
 
                scale = fitScale
                offset = Offset(offX, offY)
            }
        }

        // --- Interactive Map Canvas ---
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 3.5f)
                        offset += pan
                    }
                }
        ) {
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                // Draw grid lines for visual depth
                val gridPaint = Paint().apply {
                    color = if (isDarkMode) android.graphics.Color.parseColor("#1A1A2E") else android.graphics.Color.parseColor("#E1E5E4")
                    strokeWidth = 1f
                }
                var g = 0f
                while (g < 600f) {
                    drawLine(Color(gridPaint.color), Offset(g, 0f), Offset(g, 700f), 1f)
                    drawLine(Color(gridPaint.color), Offset(0f, g), Offset(600f, g), 1f)
                    g += 50f
                }

                // Draw driving corridors/lanes
                val laneColor = if (isDarkMode) Color(0xFF1E1E2A) else Color(0xFFE8F6F8)
                // Draw horizontal road lane
                drawRect(
                    color = laneColor,
                    topLeft = Offset(20f, 550f),
                    size = Size(500f, 80f),
                    style = Fill
                )
                // Draw vertical road lanes
                drawRect(
                    color = laneColor,
                    topLeft = Offset(150f, 50f),
                    size = Size(50f, 510f),
                    style = Fill
                )
                drawRect(
                    color = laneColor,
                    topLeft = Offset(300f, 50f),
                    size = Size(50f, 510f),
                    style = Fill
                )

                // Write road markings ("DRIVE", arrows)
                drawContext.canvas.nativeCanvas.apply {
                    val roadPaint = Paint().apply {
                        color = if (isDarkMode) android.graphics.Color.parseColor("#363648") else android.graphics.Color.parseColor("#9898A8")
                        textSize = 14f
                        isAntiAlias = true
                        isFakeBoldText = true
                    }
                    drawText("SLOW", 155f, 300f, roadPaint)
                    drawText("EXIT ➔", 220f, 595f, roadPaint)
                }

                // Draw slots
                val slotWidth = 70f
                val slotHeight = 50f

                slots.forEach { slot ->
                    val isSaved = slot.name.equals(savedSlotName, ignoreCase = true) && selectedFloor == floorLabel
                    val left = slot.x - slotWidth / 2f
                    val top = slot.y - slotHeight / 2f

                    // Spot background
                    if (isSaved) {
                        // Teal highlight
                        drawRoundRect(
                            color = HomePrimary.copy(alpha = 0.25f),
                            topLeft = Offset(left, top),
                            size = Size(slotWidth, slotHeight),
                            cornerRadius = CornerRadius(6f),
                            style = Fill
                        )
                        // Glowing borders
                        drawRoundRect(
                            color = HomePrimary,
                            topLeft = Offset(left, top),
                            size = Size(slotWidth, slotHeight),
                            cornerRadius = CornerRadius(6f),
                            style = Stroke(width = 3f)
                        )
                    } else {
                        // Standard slot border
                        drawRoundRect(
                            color = if (isDarkMode) Color(0xFF363648) else Color(0xFFB0B0B0),
                            topLeft = Offset(left, top),
                            size = Size(slotWidth, slotHeight),
                            cornerRadius = CornerRadius(6f),
                            style = Stroke(width = 1f)
                        )
                    }

                    // Spot text label
                    drawContext.canvas.nativeCanvas.apply {
                        val textPaint = Paint().apply {
                            color = if (isSaved) {
                                android.graphics.Color.parseColor("#258799")
                            } else {
                                if (isDarkMode) android.graphics.Color.parseColor("#9898A8")
                                else android.graphics.Color.parseColor("#666666")
                            }
                            textSize = 10f
                            isAntiAlias = true
                            isFakeBoldText = isSaved
                        }
                        // Center text
                        val textWidth = textPaint.measureText(slot.name)
                        drawText(
                            slot.name,
                            slot.x - textWidth / 2f,
                            slot.y + 4f,
                            textPaint
                        )
                    }
                }

                // --- Draw Navigation Route Path (if active) ---
                if (isNavigating && savedSlot != null && selectedFloor == floorLabel) {
                    val pathColor = Color(0xFF00E5CC) // Glowing teal path line
                    val pathOutlineColor = Color(0xFF0F5F5F)
                    
                    // Route coordinates
                    val startPt = Offset(250f, 590f) // "You are here" bottom middle
                    val turnPt = Offset(250f, savedSlot.y)
                    val endPt = Offset(savedSlot.x, savedSlot.y)

                    // Draw route path line
                    drawLine(
                        color = pathOutlineColor,
                        start = startPt,
                        end = turnPt,
                        strokeWidth = 8f / scale,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = pathOutlineColor,
                        start = turnPt,
                        end = endPt,
                        strokeWidth = 8f / scale,
                        cap = StrokeCap.Round
                    )

                    drawLine(
                        color = pathColor,
                        start = startPt,
                        end = turnPt,
                        strokeWidth = 4f / scale,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = pathColor,
                        start = turnPt,
                        end = endPt,
                        strokeWidth = 4f / scale,
                        cap = StrokeCap.Round
                    )

                    // Start Point (Blue Pulse Dot)
                    drawCircle(
                        color = Color.White,
                        radius = 8f / scale,
                        center = startPt
                    )
                    drawCircle(
                        color = Color(0xFF00E5CC),
                        radius = 5f / scale,
                        center = startPt
                    )

                    // "You are here" text
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 11f / scale
                            isAntiAlias = true
                            isFakeBoldText = true
                            setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK)
                        }
                        drawText("You are here", startPt.x + 12f / scale, startPt.y + 4f / scale, paint)
                    }
                }

                // --- Saved Pin marker ---
                if (savedSlot != null && selectedFloor == floorLabel) {
                    // Draw red pin at center of saved spot
                    val px = savedSlot.x
                    val py = savedSlot.y - 12f

                    // Glow circle at base
                    drawCircle(
                        color = HomePrimary.copy(alpha = 0.4f),
                        radius = 8f / scale,
                        center = Offset(px, savedSlot.y)
                    )

                    // Pin outline
                    val pinPath = Path().apply {
                        moveTo(px, py)
                        cubicTo(px - 8f, py - 8f, px - 8f, py - 16f, px, py - 24f)
                        cubicTo(px + 8f, py - 16f, px + 8f, py - 8f, px, py)
                        close()
                    }
                    drawPath(
                        path = pinPath,
                        color = Color(0xFFE53935)
                    )

                    // Inner white dot in pin
                    drawCircle(
                        color = Color.White,
                        radius = 3f / scale,
                        center = Offset(px, py - 16f)
                    )
                }
            }
        }

        // --- Back Button and Floor Title ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBackClick,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = White)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    "Parking Map",
                    color = White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            
            // Re-center button
            Surface(
                onClick = { reCenterTrigger++ },
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CenterFocusStrong, "Re-center", tint = White)
                }
            }
        }

        // --- Floor Level Selector Chips (P1, P2, P3) ---
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .width(50.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            listOf("P1", "P2", "P3").forEach { f ->
                val active = selectedFloor == f
                Surface(
                    onClick = { selectedFloor = f },
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) HomePrimary else Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = f,
                            color = White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- Zoom Controls (+ / -) ---
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .width(44.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = { scale = (scale + 0.3f).coerceAtMost(3.5f) },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("+", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Surface(
                onClick = { scale = (scale - 0.3f).coerceAtLeast(0.5f) },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("-", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- Bottom Details Card ---
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(HomePrimary.copy(0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = HomePrimary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Your Car Location",
                            color = White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (parkingLocation != null) {
                                "$savedSlotName | Floor $selectedFloor"
                            } else {
                                "No Location Saved"
                            },
                            color = White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Start Navigation Button
                Button(
                    onClick = { isNavigating = !isNavigating },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HomePrimary)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, tint = White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isNavigating) "Stop Navigation" else "Start Navigation",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
            }
        }
    }
}
