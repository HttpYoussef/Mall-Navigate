package com.example.mallar.ui.localization

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.compose.material.icons.filled.SmartToy
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search

import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mallar.ui.chatbot.ChatBottomSheet
import com.example.mallar.data.AStarPath
import com.example.mallar.data.LandmarkDetection
import com.example.mallar.data.LocalizationResult
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.data.Place
import com.example.mallar.data.PlaceRepository
import com.example.mallar.data.tier
import com.example.mallar.data.LocalizationTier
import com.example.mallar.ml.LocalizationEngine
import com.example.mallar.ml.LogoDetector
import com.example.mallar.ui.theme.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

// ── Global navigation state (start + end + A* path) ──────────────────────────────
object NavigationState {
    var startPlace: Place?           = null
    var selectedPlace: Place?        = null
    var estimatedDistance: Int       = 0
    var estimatedMinutes: Int        = 0
    var aStarPath: AStarPath?        = null
    var startWithAr: Boolean         = false
    var estimatedHeadingDeg: Float?  = null
    fun reset() {
        startPlace            = null
        selectedPlace         = null
        estimatedDistance     = 0
        estimatedMinutes      = 0
        aStarPath             = null
        startWithAr           = false
        estimatedHeadingDeg   = null
        currentFloor          = 2 }

    /** Voice + live guidance pick Arabic vs English TTS (set from STT when user speaks). */
    var preferArabicVoice: Boolean =
        java.util.Locale.getDefault().language.startsWith("ar")

    /** Active floor during navigation (2 = map2.png, 3 = map3.png). */
    var currentFloor: Int = 2
}

enum class ScanState { IDLE, SCANNING, LOCALIZING, FOUND, NOT_FOUND }

enum class ScreenFlow {
    CAMERA_IDLE,
    LOCALIZATION_CONFIRM,
    SCAN_CONFIRM,
    PICK_DESTINATION,
    DESTINATION_DETAIL,
    COMPUTING
}

@Composable
fun LogoScanScreen(
    preselectedDestination: Boolean = false,
    onBackFromLogo: (() -> Unit)? = null,
    onStoreSelected: (Boolean) -> Unit
) {
    val context        = LocalContext.current
    var backPressedTime by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDarkMode by com.example.mallar.data.AppPreferences.isDarkMode.collectAsState()

    var allPlaces         by remember { mutableStateOf<List<Place>>(emptyList()) }
    var mallGraph         by remember { mutableStateOf<com.example.mallar.data.MallGraph?>(null) }
    var logoDetector      by remember { mutableStateOf<LogoDetector?>(null) }
    var isMallDataLoading by remember { mutableStateOf(true) }

    val scanRequested   = remember { AtomicBoolean(false) }
    val analysisAllowed = remember { AtomicBoolean(false) }

    // Mall graph + places first (fast), then TFLite logo model on a worker thread so UI can appear sooner.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val loadedPlaces = PlaceRepository.load(context)
            val loadedGraph  = MallGraphRepository.load(context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                allPlaces = loadedPlaces
                mallGraph = loadedGraph
                isMallDataLoading = false
            }
            val loadedDetector = LogoDetector(context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                logoDetector = loadedDetector
            }
        }
    }

    var flow               by remember { mutableStateOf(ScreenFlow.CAMERA_IDLE) }
    var scanState          by remember { mutableStateOf(ScanState.IDLE) }
    var detectedBrand      by remember { mutableStateOf<String?>(null) }
    var detectedScore      by remember { mutableStateOf(0f) }
    var startPlace         by remember { mutableStateOf<Place?>(null) }
    var destination        by remember { mutableStateOf<Place?>(null) }
    var searchQuery        by remember { mutableStateOf("") }
    var showChatBot        by remember { mutableStateOf(false) }
    var chatPath           by remember { mutableStateOf<AStarPath?>(null) }
    var localizationResult by remember { mutableStateOf<LocalizationResult?>(null) }
    val latestBitmap       = remember { AtomicReference<Bitmap?>(null) }

    // Keep local destination in sync when Home sends a new selection (NavigationState is not Compose state).
    SideEffect {
        if (preselectedDestination) {
            val p = NavigationState.selectedPlace
            if (p != null && destination?.id != p.id) destination = p
        }
    }

    BackHandler {
        when (flow) {
            ScreenFlow.DESTINATION_DETAIL -> {
                if (preselectedDestination) {
                    flow = ScreenFlow.CAMERA_IDLE
                    destination = NavigationState.selectedPlace
                } else {
                    flow = ScreenFlow.PICK_DESTINATION
                    destination = null
                }
            }
            ScreenFlow.PICK_DESTINATION   -> {
                searchQuery = ""
                flow = if (startPlace != null) ScreenFlow.SCAN_CONFIRM else ScreenFlow.CAMERA_IDLE
            }
            ScreenFlow.LOCALIZATION_CONFIRM -> {
                flow = ScreenFlow.CAMERA_IDLE; scanState = ScanState.IDLE; localizationResult = null
            }
            ScreenFlow.SCAN_CONFIRM -> {
                flow = ScreenFlow.CAMERA_IDLE; scanState = ScanState.IDLE; detectedBrand = null
            }
            ScreenFlow.CAMERA_IDLE -> {
                val handler = onBackFromLogo
                if (handler != null) {
                    handler()
                } else {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - backPressedTime < 2000) {
                        (context as? android.app.Activity)?.finish()
                    } else {
                        backPressedTime = currentTime
                        Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else -> { flow = ScreenFlow.CAMERA_IDLE }
        }
    }

    val detectedPlace = remember(detectedBrand) {
        val b = detectedBrand ?: return@remember null
        allPlaces.firstOrNull { it.brand.equals(b, ignoreCase = true) }
            ?: allPlaces.firstOrNull { it.brand.contains(b, ignoreCase = true) }
            ?: allPlaces.firstOrNull { b.replace(" ", "").contains(it.brand.replace(" ", ""), ignoreCase = true) }
    }

    val filteredPlaces = remember(searchQuery, allPlaces, startPlace) {
        val base = if (searchQuery.isBlank()) allPlaces
        else allPlaces.filter { it.brand.contains(searchQuery, ignoreCase = true) }
        base.filter { it.id != startPlace?.id }
    }

    val destDistM = remember(destination, startPlace, mallGraph) {
        val d = destination ?: return@remember 0
        val s = startPlace
        val g = mallGraph
        if (s != null && g != null) {
            val path = MallGraphRepository.aStar(g, s.id, d.id)
            if (path != null) return@remember (path.totalDistancePx * 0.05).toInt().coerceAtLeast(1)
        }
        val dx = (d.x - (s?.x ?: d.x)).toFloat()
        val dy = (d.y - (s?.y ?: d.y)).toFloat()
        (kotlin.math.sqrt(dx * dx + dy * dy) * 0.05f).toInt().coerceAtLeast(1)
    }
    val destMins = remember(destDistM) { (destDistM / 80f).coerceIn(1f, 20f).toInt() }

    SideEffect {
        analysisAllowed.set(flow == ScreenFlow.CAMERA_IDLE && scanState != ScanState.LOCALIZING)
    }

    val previewView = remember(context) {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, logoDetector, mallGraph, allPlaces, previewView, context) {
        val det   = logoDetector
        val graph = mallGraph
        if (det == null || graph == null) {
            onDispose { }
        } else {
            val cancelled      = AtomicBoolean(false)
            val executor       = Executors.newSingleThreadExecutor()
            var imageAnalysis  : ImageAnalysis? = null
            var boundProvider  : ProcessCameraProvider? = null
            val placesSnapshot = allPlaces
            val providerFuture = ProcessCameraProvider.getInstance(context)

            providerFuture.addListener({
                if (cancelled.get()) return@addListener
                try {
                    val cp = providerFuture.get()
                    if (cancelled.get()) return@addListener
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val ia = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    imageAnalysis = ia
                    ia.setAnalyzer(executor) { proxy ->
                        try {
                            val bmp = proxy.toBitmapSafe()
                            if (bmp != null) {
                                latestBitmap.set(bmp)
                                if (analysisAllowed.get() && scanRequested.getAndSet(false)) {
                                    val result = LocalizationEngine.estimatePose(
                                        frame   = bmp,
                                        detector = det,
                                        graph   = graph,
                                        places  = placesSnapshot
                                    )
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        if (result.detections.isNotEmpty()) {
                                            Log.d("LogoScan", "Localization: ${result.detections.size} landmarks, conf=${result.confidence}")
                                            localizationResult = result
                                            scanState = ScanState.FOUND
                                        } else {
                                            Log.d("LogoScan", "No landmarks detected")
                                            localizationResult = null
                                            scanState = ScanState.NOT_FOUND
                                        }
                                    }
                                }
                            }
                        } finally {
                            try { proxy.close() } catch (_: Exception) { }
                        }
                    }
                    cp.unbindAll()
                    if (cancelled.get()) {
                        try { ia.clearAnalyzer() } catch (_: Exception) { }
                        return@addListener
                    }
                    cp.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, ia)
                    boundProvider = cp
                } catch (e: Exception) {
                    Log.e("LogoScan", "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                cancelled.set(true)
                try { imageAnalysis?.clearAnalyzer() } catch (_: Exception) { }
                try { boundProvider?.unbindAll() } catch (_: Exception) { }
                executor.shutdown()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F7FA))) {

        // CAMERA PREVIEW
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        LaunchedEffect(scanState) {
            if (scanState == ScanState.FOUND) flow = ScreenFlow.LOCALIZATION_CONFIRM
        }

        // ── Loading ───────────────────────────────────────────────────────────
        AnimatedVisibility(visible = isMallDataLoading, modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(), exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Teal, modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(0.7f)) {
                    Text("Initialising mall map…", color = White, fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
            }
        }

        val scannerReady = !isMallDataLoading && logoDetector != null
        AnimatedVisibility(
            visible = !isMallDataLoading && logoDetector == null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Teal, modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(0.65f)) {
                    Text("Loading logo scanner…", color = White, fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp))
                }
            }
        }

        // ── NOT_FOUND ─────────────────────────────────────────────────────────
        AnimatedVisibility(visible = scanState == ScanState.NOT_FOUND,
            modifier = Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            LaunchedEffect(scanState) {
                if (scanState == ScanState.NOT_FOUND) { kotlinx.coroutines.delay(2500); scanState = ScanState.IDLE }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFB71C1C).copy(0.9f)) {
                Text("No logo recognised — point camera at a store sign and try again",
                    color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp))
            }
        }

        // ── SCANNING spinner ──────────────────────────────────────────────────
        AnimatedVisibility(visible = scanState == ScanState.SCANNING,
            modifier = Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Teal, modifier = Modifier.size(56.dp), strokeWidth = 4.dp)
                Spacer(Modifier.height(14.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(0.6f)) {
                    Text("Scanning logo…", color = White, fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
            }
        }

        // ── LOCALIZING spinner ────────────────────────────────────────────────
        AnimatedVisibility(visible = scanState == ScanState.LOCALIZING,
            modifier = Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Teal, modifier = Modifier.size(64.dp), strokeWidth = 5.dp)
                Spacer(Modifier.height(14.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(0.75f)) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔎 Detecting landmarks…", color = White,
                            fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Estimating your position", color = White.copy(0.75f), fontSize = 12.sp)
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // LOCALIZATION_CONFIRM
        // ══════════════════════════════════════════════════════════════════════
        val locResult = localizationResult
        if (flow == ScreenFlow.LOCALIZATION_CONFIRM && locResult != null) {
            LocalizationConfirmScreen(
                result      = locResult,
                onConfirmed = { chosenDetection ->
                    val chosenPlace = chosenDetection.place
                        ?: allPlaces.firstOrNull { p ->
                            p.brand.equals(chosenDetection.brand, ignoreCase = true) ||
                                    p.brand.contains(chosenDetection.brand, ignoreCase = true)
                        }
                    startPlace = chosenPlace
                    NavigationState.startPlace = chosenPlace
                    NavigationState.estimatedHeadingDeg = locResult.estimatedHeadingDeg
                    val dest = NavigationState.selectedPlace
                    if (preselectedDestination && dest != null) {
                        destination = dest
                        flow = ScreenFlow.DESTINATION_DETAIL
                    } else {
                        flow = ScreenFlow.PICK_DESTINATION
                    }
                    scanState = ScanState.IDLE
                    localizationResult = null
                },
                onRescan  = { flow = ScreenFlow.CAMERA_IDLE; scanState = ScanState.IDLE; localizationResult = null },
                onDismiss = { flow = ScreenFlow.CAMERA_IDLE; scanState = ScanState.IDLE; localizationResult = null }
            )
        }

        // ══════════════════════════════════════════════════════════════════════
        // SCAN_CONFIRM
        // ══════════════════════════════════════════════════════════════════════
        AnimatedVisibility(visible = flow == ScreenFlow.SCAN_CONFIRM,
            enter = slideInVertically(tween(300)) { it / 2 } + fadeIn(tween(250)),
            exit  = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(150))) {
            Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), Alignment.Center) {
                Surface(Modifier.fillMaxWidth().shadow(28.dp, RoundedCornerShape(28.dp)),
                    RoundedCornerShape(28.dp), color = White) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.MyLocation, null, tint = Teal, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("You are currently at:", color = TextSecondary, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        if (detectedPlace != null) {
                            Surface(Modifier.size(80.dp), RoundedCornerShape(18.dp), color = SurfaceLight) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data("file:///android_asset/${detectedPlace.logo}").crossfade(true).build(),
                                    contentDescription = detectedPlace.brand, contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).padding(8.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        Text(detectedBrand ?: "Unknown store", fontWeight = FontWeight.ExtraBold,
                            fontSize = 26.sp, color = TextPrimary, textAlign = TextAlign.Center)
                        if (detectedPlace == null) {
                            Spacer(Modifier.height(4.dp))
                            Text("(not in mall map — please scan again)", color = RedAccent,
                                fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Match confidence: ${"%.0f".format(detectedScore * 100)}%",
                            color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                val start = detectedPlace
                                if (start != null) {
                                    startPlace = start; NavigationState.startPlace = start
                                    val dest = NavigationState.selectedPlace
                                    if (preselectedDestination && dest != null) {
                                        destination = dest
                                        flow = ScreenFlow.DESTINATION_DETAIL
                                    } else {
                                        flow = ScreenFlow.PICK_DESTINATION
                                    }
                                    scanState = ScanState.IDLE; detectedBrand = null
                                } else {
                                    flow = ScreenFlow.CAMERA_IDLE; scanState = ScanState.IDLE; detectedBrand = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (detectedPlace != null) Teal else Color.Gray)
                        ) {
                            Text(if (detectedPlace != null) "Yes, I'm here" else "Not found — Scan Again",
                                fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = White)
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { flow = ScreenFlow.CAMERA_IDLE; scanState = ScanState.IDLE; detectedBrand = null },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            border = androidx.compose.foundation.BorderStroke(2.dp, Teal),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal)
                        ) {
                            Icon(Icons.Filled.Search, null, tint = Teal, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Scan Again", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Teal)
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // PICK_DESTINATION
        // ══════════════════════════════════════════════════════════════════════
        AnimatedVisibility(visible = flow == ScreenFlow.PICK_DESTINATION && !preselectedDestination,
            enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { it / 10 },
            exit  = fadeOut(tween(150)) + slideOutVertically(tween(200)) { it / 10 }) {
            Column(Modifier.fillMaxSize().background(if (isDarkMode) com.example.mallar.ui.theme.DarkBackground else White)) {
                Column(Modifier.fillMaxWidth().background(if (isDarkMode) com.example.mallar.ui.theme.DarkSurface else Teal).statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(onClick = { flow = ScreenFlow.SCAN_CONFIRM; searchQuery = "" },
                            modifier = Modifier.size(40.dp), shape = CircleShape,
                            color = White.copy(0.25f)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = White)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("From: ${startPlace?.brand ?: "My Location"}",
                                color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Where do you want to go?", color = White.copy(0.8f), fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Surface(Modifier.fillMaxWidth().height(46.dp).shadow(if (isDarkMode) 0.dp else 6.dp, RoundedCornerShape(23.dp)),
                        RoundedCornerShape(23.dp), color = if (isDarkMode) com.example.mallar.ui.theme.DarkCard else White) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Search, null, tint = TextSecondary.copy(0.6f), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(value = searchQuery, onValueChange = { searchQuery = it },
                                textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = if (isDarkMode) com.example.mallar.ui.theme.DarkTextPrimary else TextPrimary),
                                modifier = Modifier.weight(1f), singleLine = true,
                                decorationBox = { inner ->
                                    if (searchQuery.isEmpty()) Text("Search destination…",
                                        color = TextSecondary.copy(0.5f), fontSize = 15.sp)
                                    inner()
                                })
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                LazyColumn(contentPadding = PaddingValues(top = 8.dp, start = 14.dp, end = 14.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(filteredPlaces) { _, place ->
                        DestinationRow(place = place, startPlace = startPlace, mallGraph = mallGraph,
                            onClick = { destination = place; flow = ScreenFlow.DESTINATION_DETAIL })
                        HorizontalDivider(color = DividerColor.copy(0.4f), thickness = 0.7.dp,
                            modifier = Modifier.padding(start = 82.dp))
                    }
                    if (filteredPlaces.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 60.dp), Alignment.Center) {
                                Text("No stores found", color = TextSecondary, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // DESTINATION_DETAIL
        // ══════════════════════════════════════════════════════════════════════
        AnimatedVisibility(visible = flow == ScreenFlow.DESTINATION_DETAIL && destination != null,
            enter = fadeIn(tween(200)) + slideInVertically(tween(280)) { it / 10 },
            exit  = fadeOut(tween(150))) {
            val dest = destination
            if (dest != null) {
                Box(Modifier.fillMaxSize().background(if (isDarkMode) com.example.mallar.ui.theme.DarkBackground else Color(0xFFF5F7FA))) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Surface(onClick = {
                            if (preselectedDestination) {
                                flow = ScreenFlow.CAMERA_IDLE
                                destination = NavigationState.selectedPlace
                            } else {
                                flow = ScreenFlow.PICK_DESTINATION
                                destination = null
                            }
                        },
                            modifier = Modifier.size(42.dp), shape = CircleShape, color = if (isDarkMode) com.example.mallar.ui.theme.DarkCard else White.copy(0.9f)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (isDarkMode) White else TextPrimary)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Surface(Modifier.weight(1f).height(44.dp).shadow(if (isDarkMode) 0.dp else 6.dp, RoundedCornerShape(22.dp)),
                            RoundedCornerShape(22.dp), color = if (isDarkMode) com.example.mallar.ui.theme.DarkCard else White) {
                            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), Alignment.CenterStart) {
                                Text(dest.brand, color = if (isDarkMode) White else TextPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            }
                        }
                    }
                    Surface(Modifier.fillMaxWidth().align(Alignment.Center)
                        .padding(horizontal = 16.dp).shadow(if (isDarkMode) 0.dp else 24.dp, RoundedCornerShape(28.dp)),
                        RoundedCornerShape(28.dp), color = if (isDarkMode) com.example.mallar.ui.theme.DarkCard else White) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(70.dp), RoundedCornerShape(16.dp), color = SurfaceLight) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data("file:///android_asset/${dest.logo}").crossfade(true).build(),
                                        contentDescription = dest.brand, contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).padding(8.dp))
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(dest.brand, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = if (isDarkMode) White else TextPrimary)
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.LocationOn, null, tint = RedAccent, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("${destDistM}m", color = if (isDarkMode) White.copy(0.7f) else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(12.dp))
                                        Icon(Icons.Filled.AccessTime, null, tint = RedAccent, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("${destMins}min", color = if (isDarkMode) White.copy(0.7f) else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Surface(onClick = {
                                    if (preselectedDestination) {
                                        flow = ScreenFlow.CAMERA_IDLE
                                        destination = NavigationState.selectedPlace
                                    } else {
                                        flow = ScreenFlow.PICK_DESTINATION
                                        destination = null
                                    }
                                },
                                    modifier = Modifier.size(32.dp), shape = CircleShape, color = SurfaceLight) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Close, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                            if (startPlace != null) {
                                Spacer(Modifier.height(10.dp))
                                Surface(shape = RoundedCornerShape(12.dp), color = Teal.copy(0.08f)) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.MyLocation, null, tint = Teal, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("From: ${startPlace!!.brand}", color = Teal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            Row(Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { startNavigation(mallGraph, startPlace, destination, destDistM, onStoreSelected, useAr = true) },
                                    modifier = Modifier.weight(1f).height(54.dp),
                                    shape = RoundedCornerShape(27.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Teal)
                                ) {
                                    Icon(Icons.Filled.ViewInAr, null, tint = White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("AR", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = White)
                                }
                                Spacer(Modifier.width(12.dp))
                                Button(
                                    onClick = { startNavigation(mallGraph, startPlace, destination, destDistM, onStoreSelected, useAr = false) },
                                    modifier = Modifier.weight(1f).height(54.dp),
                                    shape = RoundedCornerShape(27.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64))
                                ) {
                                    Icon(Icons.Filled.Map, null, tint = White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Map", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = White)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Choose your navigation mode", color = TextSecondary, fontSize = 13.sp,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // CAMERA_IDLE — NEW UI
        // ══════════════════════════════════════════════════════════════════════
        val showIdle = flow == ScreenFlow.CAMERA_IDLE && scanState == ScanState.IDLE

        // Top bar: Settings button


        // Centre crosshair + hint text
        AnimatedVisibility(visible = showIdle, modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(400)), exit = fadeOut(tween(200))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 220.dp)) {
                Canvas(modifier = Modifier.size(72.dp)) {
                    val s   = size.minDimension
                    val c   = center
                    val r   = s * 0.40f
                    val arm = s * 0.20f
                    val gap = s * 0.12f
                    val col = Color(0xFF00BCD4)
                    listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
                        drawLine(col, Offset(c.x + sx * gap, c.y + sy * r),
                            Offset(c.x + sx * (gap + arm), c.y + sy * r), strokeWidth = 3.5f, cap = StrokeCap.Round)
                        drawLine(col, Offset(c.x + sx * r, c.y + sy * gap),
                            Offset(c.x + sx * r, c.y + sy * (gap + arm)), strokeWidth = 3.5f, cap = StrokeCap.Round)
                    }
                    drawCircle(col, radius = s * 0.06f, center = c)
                }
                Spacer(Modifier.height(20.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isDarkMode) com.example.mallar.ui.theme.DarkCard.copy(alpha = 0.88f) else White.copy(alpha = 0.88f),
                    shadowElevation = if (isDarkMode) 0.dp else 2.dp
                ) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = if (isDarkMode) White else TextPrimary, fontSize = 18.sp)) { append("Point your camera at a ") }
                            withStyle(SpanStyle(color = Teal, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)) { append("store logo") }
                            if (preselectedDestination) {
                                withStyle(SpanStyle(color = TextSecondary, fontSize = 16.sp)) { append("\nto confirm where you are") }
                            } else {
                                withStyle(SpanStyle(color = TextSecondary, fontSize = 16.sp)) { append("\nto start navigation") }
                            }
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // Bottom: search bar + action buttons
        AnimatedVisibility(visible = showIdle, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(280)) { it } + fadeIn(),
            exit  = slideOutVertically(tween(200)) { it } + fadeOut()) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Destination summary (Home already chose) — no second destination search here.
                if (preselectedDestination && NavigationState.selectedPlace != null) {
                    val destPlace = NavigationState.selectedPlace!!
                    Surface(
                        Modifier.fillMaxWidth().height(54.dp).shadow(if (isDarkMode) 0.dp else 8.dp, RoundedCornerShape(27.dp)),
                        RoundedCornerShape(27.dp),
                        color = if (isDarkMode) com.example.mallar.ui.theme.DarkCard else White
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.LocationOn, null, tint = Teal, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Going to", color = if (isDarkMode) White.copy(0.7f) else TextSecondary, fontSize = 11.sp)
                                Text(destPlace.brand, color = if (isDarkMode) White else TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    Surface(
                        Modifier.fillMaxWidth().height(54.dp).shadow(if (isDarkMode) 0.dp else 8.dp, RoundedCornerShape(27.dp))
                            .clickable { startPlace = NavigationState.startPlace; searchQuery = ""; flow = ScreenFlow.PICK_DESTINATION },
                        RoundedCornerShape(27.dp),
                        color = if (isDarkMode) com.example.mallar.ui.theme.DarkCard else White
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Search, null, tint = TextSecondary.copy(0.5f), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Where to go...", color = TextSecondary.copy(0.5f), fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Box(Modifier.size(34.dp).clip(CircleShape).background(Teal),
                                Alignment.Center) {

                            }
                        }
                    }
                }

                // Centered Scan Logo button panel (Ask Me moved to HomeScreen)
                Surface(
                    Modifier.fillMaxWidth().shadow(if (isDarkMode) 0.dp else 6.dp, RoundedCornerShape(24.dp)),
                    RoundedCornerShape(24.dp),
                    color = if (isDarkMode) com.example.mallar.ui.theme.DarkCard else White
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Scan Logo — centered button
                        Box(
                            Modifier.size(80.dp).clip(CircleShape)
                                .background(Brush.radialGradient(listOf(Teal, DarkTeal)))
                                .clickable {
                                    if (scanState != ScanState.SCANNING && scannerReady) {
                                        scanState = ScanState.SCANNING
                                        detectedBrand = null
                                        scanRequested.set(true)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(Modifier.size(40.dp)) {
                                val arm = size.minDimension * 0.32f
                                val gap = size.minDimension * 0.12f
                                val s   = size.minDimension * 0.48f
                                val c   = center
                                listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
                                    drawLine(Color.White, Offset(c.x + sx * gap, c.y + sy * s),
                                        Offset(c.x + sx * (gap + arm), c.y + sy * s), strokeWidth = 4f, cap = StrokeCap.Round)
                                    drawLine(Color.White, Offset(c.x + sx * s, c.y + sy * gap),
                                        Offset(c.x + sx * s, c.y + sy * (gap + arm)), strokeWidth = 4f, cap = StrokeCap.Round)
                                }
                            }
                        }
                        Text(
                            when {
                                isMallDataLoading    -> "Loading map…"
                                logoDetector == null -> "Loading scanner…"
                                else                 -> "Scan Logo"
                            },
                            color = if (isDarkMode) White else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (showChatBot) {
            Dialog(
                onDismissRequest = { showChatBot = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().clickable { showChatBot = false })
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        ChatBottomSheet(
                            graph = mallGraph,
                            onDismiss = { showChatBot = false },
                            onPathFound = { path: AStarPath ->
                                chatPath = path
                                NavigationState.aStarPath = path
                            },
                            onStartNavigation = { useAr ->
                                showChatBot = false
                                onStoreSelected(useAr)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun startNavigation(
    mallGraph: com.example.mallar.data.MallGraph?,
    startPlace: Place?,
    destination: Place?,
    destDistM: Int,
    onStoreSelected: (Boolean) -> Unit,
    useAr: Boolean
) {
    val start = startPlace
    val end   = destination
    if (start != null && end != null) {
        val path = mallGraph?.let { MallGraphRepository.aStar(it, start.id, end.id) }
        val realDistM = if (path != null) (path.totalDistancePx * 0.05).toInt().coerceAtLeast(1) else destDistM
        val realMins  = (realDistM / 80f).coerceIn(1f, 20f).toInt()
        NavigationState.selectedPlace     = end
        NavigationState.startPlace        = start
        NavigationState.estimatedDistance = realDistM
        NavigationState.estimatedMinutes  = realMins
        NavigationState.aStarPath         = path
        NavigationState.startWithAr       = useAr
        onStoreSelected(useAr)
    } else if (end != null) {
        NavigationState.selectedPlace     = end
        NavigationState.estimatedDistance = destDistM
        NavigationState.estimatedMinutes  = (destDistM / 80).coerceIn(1, 20)
        NavigationState.aStarPath         = null
        NavigationState.startWithAr       = useAr
        onStoreSelected(useAr)
    }
}

@Composable
private fun DestinationRow(
    place: Place,
    startPlace: Place?,
    mallGraph: com.example.mallar.data.MallGraph?,
    onClick: () -> Unit
) {
    val distM = remember(place.id, startPlace?.id, mallGraph) {
        if (startPlace != null && mallGraph != null) {
            val path = MallGraphRepository.aStar(mallGraph, startPlace.id, place.id)
            if (path != null) return@remember (path.totalDistancePx * 0.05).toInt().coerceAtLeast(1)
        }
        val dx = place.x.toFloat(); val dy = place.y.toFloat()
        (kotlin.math.sqrt(dx * dx + dy * dy) * 0.05f).toInt().coerceAtLeast(1)
    }
    val mins = remember(distM) { (distM / 80f).coerceIn(1f, 10f).toInt() }

    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(60.dp), RoundedCornerShape(13.dp), color = SurfaceLight, shadowElevation = 1.dp) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("file:///android_asset/${place.logo}").crossfade(true).build(),
                contentDescription = place.brand, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp)).padding(7.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(place.brand, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, null, tint = RedAccent, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text("${distM}m", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.AccessTime, null, tint = RedAccent, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text("${mins}min", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun ImageProxy.toBitmapSafe(): Bitmap? {
    val image = this.image ?: return null
    if (image.format != ImageFormat.YUV_420_888) {
        return try { this.toBitmap() } catch (e: Exception) { null }
    }
    val yBuf = image.planes[0].buffer
    val uBuf = image.planes[1].buffer
    val vBuf = image.planes[2].buffer
    val nv21 = ByteArray(yBuf.remaining() + uBuf.remaining() + vBuf.remaining())
    yBuf.get(nv21, 0, yBuf.remaining())
    vBuf.get(nv21, yBuf.capacity(), vBuf.remaining())
    uBuf.get(nv21, yBuf.capacity() + vBuf.capacity(), uBuf.remaining())
    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuv.compressToJpeg(Rect(0, 0, width, height), 95, out)
    val bytes = out.toByteArray()
    val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val mat   = android.graphics.Matrix().also { it.postRotate(imageInfo.rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mat, true)
}