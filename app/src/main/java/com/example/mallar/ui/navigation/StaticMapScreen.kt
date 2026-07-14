package com.example.mallar.ui.navigation

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.ui.theme.Teal
import com.example.mallar.utils.CoordinateTransformer
import com.example.mallar.utils.FloorMapAssets
import com.example.mallar.ui.theme.White
import com.example.mallar.ui.theme.RedAccent
import com.example.mallar.ui.localization.NavigationState

private val PathTeal = Color(0xFF00897B)
private val StartGreen = Color(0xFF43A047)
private val EndRed = Color(0xFFE53935)

@Composable
fun StaticMapScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current

    val pathData = NavigationState.aStarPath
    val hasActiveRoute = pathData != null && pathData.nodeIds.size >= 2

    val mallGraph = remember {
        MallGraphRepository.loadedGraph ?: MallGraphRepository.load(context)
    }
    val nodeMap = remember(mallGraph) { mallGraph.nodes.associateBy { it.id } }

    val displayFloor = remember(pathData, NavigationState.selectedPlace, NavigationState.currentFloor) {
        if (pathData != null && pathData.nodeIds.size >= 2) {
            NavigationState.currentFloor
        } else {
            FloorMapAssets.floorForStaticMap(pathData, nodeMap, NavigationState.selectedPlace)
        }
    }
    val floorPathIds = remember(pathData, displayFloor) {
        FloorMapAssets.pathNodeIdsOnFloor(pathData, nodeMap, displayFloor)
    }
    val mapBitmap = remember(displayFloor) {
        runCatching {
            context.assets.open(FloorMapAssets.mapAssetForFloor(displayFloor)).use {
                BitmapFactory.decodeStream(it).asImageBitmap()
            }
        }.getOrNull()
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var reCenterTrigger by remember { mutableStateOf(0) }
    var isDebugMode by remember { mutableStateOf(false) }
 
    // Auto-zoom to fit the path or entire map on first load/re-center
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        val canvasWidth = with(density) { maxWidth.toPx() }
        val canvasHeight = with(density) { maxHeight.toPx() }
 
        LaunchedEffect(floorPathIds, canvasWidth, canvasHeight, mapBitmap, reCenterTrigger) {
            if (canvasWidth <= 0f || canvasHeight <= 0f) return@LaunchedEffect
            
            if (floorPathIds.size >= 2) {
                // Scenario A: Active route. Fit the bounding box of the path.
                val pathCoords = floorPathIds.mapNotNull { nodeMap[it] }
                if (pathCoords.isEmpty()) return@LaunchedEffect
 
                val minX = pathCoords.minOf { CoordinateTransformer.transformX(it.x) }
                val maxX = pathCoords.maxOf { CoordinateTransformer.transformX(it.x) }
                val minY = pathCoords.minOf { CoordinateTransformer.transformY(it.y) }
                val maxY = pathCoords.maxOf { CoordinateTransformer.transformY(it.y) }
 
                val pathW = (maxX - minX).coerceAtLeast(50f)
                val pathH = (maxY - minY).coerceAtLeast(50f)
 
                // Add padding (20% on each side)
                val padFactor = 1.4f
                val fitScaleX = canvasWidth / (pathW * padFactor)
                val fitScaleY = canvasHeight / (pathH * padFactor)
                val fitScale = minOf(fitScaleX, fitScaleY).coerceIn(0.5f, 5f)
 
                // Center the path
                val centerX = (minX + maxX) / 2f
                val centerY = (minY + maxY) / 2f
                val offX = canvasWidth / 2f - centerX * fitScale
                val offY = canvasHeight / 2f - centerY * fitScale
 
                scale = fitScale
                offset = Offset(offX, offY)
            } else if (mapBitmap != null) {
                // Scenario B: No active route. Fit and center the entire map bitmap.
                val mapW = mapBitmap.width.toFloat()
                val mapH = mapBitmap.height.toFloat()
 
                // Fit to screen with 5% padding on edges (0.9 fit factor)
                val padFactor = 0.9f
                val fitScaleX = (canvasWidth * padFactor) / mapW
                val fitScaleY = (canvasHeight * padFactor) / mapH
                val fitScale = minOf(fitScaleX, fitScaleY).coerceIn(0.2f, 5f)
 
                // Center the map
                val offX = (canvasWidth - mapW * fitScale) / 2f
                val offY = (canvasHeight - mapH * fitScale) / 2f
 
                scale = fitScale
                offset = Offset(offX, offY)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offset += pan
                    }
                }
        ) {
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                mapBitmap?.let { drawImage(image = it) }
                
                // --- DEBUG MODE ---
                if (isDebugMode) {
                    mallGraph.edges.forEach { edge ->
                        val n1 = nodeMap[edge.from]
                        val n2 = nodeMap[edge.to]
                        if (n1 != null && n2 != null) {
                            val x1 = CoordinateTransformer.transformX(n1.x)
                            val y1 = CoordinateTransformer.transformY(n1.y)
                            val x2 = CoordinateTransformer.transformX(n2.x)
                            val y2 = CoordinateTransformer.transformY(n2.y)
                            drawLine(
                                color = Color.Yellow.copy(alpha = 0.5f),
                                start = Offset(x1, y1),
                                end = Offset(x2, y2),
                                strokeWidth = 2f / scale
                            )
                        }
                    }
                    mallGraph.nodes.forEach { node ->
                        val nx = CoordinateTransformer.transformX(node.x)
                        val ny = CoordinateTransformer.transformY(node.y)
                        drawCircle(
                            color = if (node.shopId != null) Color.Cyan else Color.Magenta,
                            radius = 4f / scale,
                            center = Offset(nx, ny)
                        )
                    }
                }

                // Draw A* path (segment on the active floor map only)
                if (floorPathIds.size >= 2) {
                        val outlinePath = Path()
                        val mainPath = Path()
                        val firstNode = nodeMap[floorPathIds.first()]
                        if (firstNode != null) {
                            val startX = CoordinateTransformer.transformX(firstNode.x)
                            val startY = CoordinateTransformer.transformY(firstNode.y)
                            
                            outlinePath.moveTo(startX, startY)
                            mainPath.moveTo(startX, startY)
                            for (i in 1 until floorPathIds.size) {
                                val node = nodeMap[floorPathIds[i]]
                                if (node != null) {
                                    val nx = CoordinateTransformer.transformX(node.x)
                                    val ny = CoordinateTransformer.transformY(node.y)
                                    outlinePath.lineTo(nx, ny)
                                    mainPath.lineTo(nx, ny)
                                }
                            }
                            // Draw outline
                            drawPath(
                                path = outlinePath,
                                color = Color(0xFF004D40),
                                style = Stroke(
                                    width = 12f / scale,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                            // Draw main path
                            drawPath(
                                path = mainPath,
                                color = PathTeal,
                                style = Stroke(
                                    width = 7f / scale,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )

                            // ── Start marker (green) with white outline ────────────
                            drawCircle(
                                color = Color.White,
                                radius = 16f / scale,
                                center = Offset(startX, startY)
                            )
                            drawCircle(
                                color = StartGreen,
                                radius = 12f / scale,
                                center = Offset(startX, startY)
                            )
                            // Inner white dot
                            drawCircle(
                                color = Color.White,
                                radius = 4f / scale,
                                center = Offset(startX, startY)
                            )

                            // ── End marker (red) with white outline ────────────────
                            val lastNode = nodeMap[floorPathIds.last()]
                            var endX = 0f
                            var endY = 0f
                            if (lastNode != null) {
                                endX = CoordinateTransformer.transformX(lastNode.x)
                                endY = CoordinateTransformer.transformY(lastNode.y)
                                drawCircle(
                                    color = Color.White,
                                    radius = 16f / scale,
                                    center = Offset(endX, endY)
                                )
                                drawCircle(
                                    color = EndRed,
                                    radius = 12f / scale,
                                    center = Offset(endX, endY)
                                )
                                // Inner white dot
                                drawCircle(
                                    color = Color.White,
                                    radius = 4f / scale,
                                    center = Offset(endX, endY)
                                )
                            }

                            // ── Draw labels using native canvas ────────────────────
                            drawContext.canvas.nativeCanvas.apply {
                                val textPaint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    textSize = 13f / scale
                                    isAntiAlias = true
                                    isFakeBoldText = true
                                    setShadowLayer(3f / scale, 1f / scale, 1f / scale, android.graphics.Color.BLACK)
                                }
                                // Start label
                                val startLabel = NavigationState.startPlace?.brand ?: "Start"
                                drawText(startLabel, startX + 18f / scale, startY - 8f / scale, textPaint)

                                // End label
                                if (lastNode != null) {
                                    val endLabel = NavigationState.selectedPlace?.brand ?: "Destination"
                                    drawText(endLabel, endX + 18f / scale, endY - 8f / scale, textPaint)
                                }
                            }

                            // ── Direction arrows along path ────────────────────────
                            for (i in 0 until floorPathIds.size - 1) {
                                val nA = nodeMap[floorPathIds[i]] ?: continue
                                val nB = nodeMap[floorPathIds[i + 1]] ?: continue
                                
                                val ax = CoordinateTransformer.transformX(nA.x)
                                val ay = CoordinateTransformer.transformY(nA.y)
                                val bx = CoordinateTransformer.transformX(nB.x)
                                val by = CoordinateTransformer.transformY(nB.y)
                                
                                val dx = bx - ax
                                val dy = by - ay
                                val segLen = kotlin.math.sqrt(dx * dx + dy * dy)
                                if (segLen < 20f) continue
                                // Place a small direction indicator at midpoint
                                val midX = (ax + bx) / 2f
                                val midY = (ay + by) / 2f
                                val arrowSize = 6f / scale
                                val ndx = dx / segLen * arrowSize
                                val ndy = dy / segLen * arrowSize
                                val perpX = -ndy * 0.5f
                                val perpY = ndx * 0.5f
                                val arrowPath = Path().apply {
                                    moveTo(midX + ndx, midY + ndy)        // tip
                                    lineTo(midX - ndx + perpX, midY - ndy + perpY) // left base
                                    lineTo(midX - ndx - perpX, midY - ndy - perpY) // right base
                                    close()
                                }
                                drawPath(arrowPath, color = Color.White)
                            }
                        }
                }
            }
        }

        if (mapBitmap == null) {
            Text(
                "Map unavailable",
                color = White.copy(0.6f),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Top UI Overlay
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBackClick,
                    modifier = Modifier.size(48.dp),
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
                        "Route Map · Floor $displayFloor",
                        color = White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // Debug button
                Surface(
                    onClick = { isDebugMode = !isDebugMode },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = if (isDebugMode) Teal.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.6f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("BUG", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    onClick = { reCenterTrigger++ },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CenterFocusStrong, "Re-center", tint = White)
                    }
                }
            }
        }

        if (hasActiveRoute) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    NavigationState.startPlace?.brand?.let { startBrand ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(StartGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "From: $startBrand",
                                color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    NavigationState.selectedPlace?.brand?.let { destBrand ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(EndRed, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "To: $destBrand",
                                color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Distance: ${NavigationState.estimatedDistance}m",
                            color = White.copy(alpha = 0.7f), fontSize = 13.sp
                        )
                        Text(
                            "Est. Time: ${NavigationState.estimatedMinutes} min",
                            color = White.copy(alpha = 0.7f), fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Text(
            "Pinch to zoom • Drag to pan",
            color = White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (hasActiveRoute) 150.dp else 32.dp)
        )
    }
}
