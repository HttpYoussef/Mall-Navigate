package com.example.mallar.ui.navigation.prototype

/**
 * PROTOTYPE IMPLEMENTATION
 *
 * Experimental AR navigation screen.
 *
 * Development was paused before completion.
 *
 * This file is intentionally preserved because the
 * future AR navigation system will likely reuse
 * concepts from this implementation.
 *
 * Not used in production.
 */




//package com.example.mallar.ui.navigation
//
//import android.os.Handler
//import android.os.Looper
//import android.view.ViewGroup
//import androidx.compose.animation.*
//import androidx.compose.animation.core.tween
//import androidx.compose.foundation.background
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.rememberScrollState
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.foundation.verticalScroll
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.automirrored.filled.ArrowBack
//import androidx.compose.material.icons.automirrored.filled.ArrowForward
//import androidx.compose.material.icons.filled.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.compose.ui.viewinterop.AndroidView
//import androidx.lifecycle.viewmodel.compose.viewModel
//import coil.compose.AsyncImage
//import com.example.mallar.ar.*
//import com.example.mallar.ui.ar.*
//import com.example.mallar.data.GraphNode
//import com.example.mallar.navigation.SensorFusionManager
//import com.example.mallar.navigation.StepTracker
//import com.google.ar.core.*
//import io.github.sceneview.ar.ARSceneView
//import kotlin.math.*
//
//// ── Design tokens ─────────────────────────────────────────────────────────────
//private val NavTeal     = Color(0xFF009688)
//private val NavGreen    = Color(0xFF43A047)
//private val NavAmber    = Color(0xFFFFA000)
//
//// ─────────────────────────────────────────────────────────────────────────────
//@Composable
//fun ArNavigationScreen(
//    onBackClick: () -> Unit,
//    viewModel: ArNavigationViewModel = viewModel()
//) {
//    val state by viewModel.uiState.collectAsState()
//    val context = LocalContext.current
//
//    val pathNodes                      = state.pathNodes
//    val destinationNode                = state.destinationNode
//    val distanceM                      = state.distanceM
//    val walkMinutes                    = state.walkMinutes
//    val currentInstructionLabel        = state.currentInstructionLabel
//    val routeSteps                     = state.routeSteps
//    val statusText                     = state.statusText
//    val segmentIndex                   = state.segmentIndex
//    val showRouteSheet                 = state.showRouteSheet
//    val showStoreCard                  = state.showStoreCard
//    val reachedNotification            = state.reachedNotification
//    val navigationPhase                = state.navigationPhase
//    val distToStartPinM                = state.distToStartPinM
//    val showDebugOverlay               = state.showDebugOverlay
//
//    val managerRef = remember { mutableStateOf<ArrowSceneManager?>(null) }
//
//    // ── Sensor fusion: TYPE_ROTATION_VECTOR → LP filter → Kalman filter ────────
//    // Replaces raw accelerometer+magnetometer compass to fix indoor drift/jitter.
//    // SensorFusionManager applies:
//    //   1. Android OS Kalman fusion (TYPE_ROTATION_VECTOR = gyro+accel+mag).
//    //   2. Exponential low-pass filter (α=0.15) to kill magnetic spike noise.
//    //   3. Scalar Kalman filter (Q=0.005, R=5) to smooth residual drift.
//    //
//    // arrowRotation = targetBearing − userHeading (normalised to −180…+180)
//    val compassHeading  = remember { mutableFloatStateOf(0f) }
//    val compassAccuracy = remember { mutableIntStateOf(0) }
//
//    // SensorFusionManager lifecycle
//    DisposableEffect(Unit) {
//        val fusionManager = SensorFusionManager(context)
//        fusionManager.onHeadingChanged = { azimuthDeg, accuracy ->
//            compassHeading.floatValue  = azimuthDeg
//            compassAccuracy.intValue   = accuracy
//
//            val manager = managerRef.value
//            if (manager != null) {
//                // Feed smoothed heading into ArrowSceneManager (replaces raw feed)
//                manager.feedCompassReading(azimuthDeg, accuracy)
//
//                // Push debug values to ViewModel — throttled by the sensor rate (~50 Hz)
//                Handler(Looper.getMainLooper()).post {
//                    viewModel.updateDebugValues(
//                        rawHeading = manager.debugRawHeadingDeg,
//                        bearing    = manager.debugCalculatedBearing,
//                        offset     = manager.debugAppliedOffset
//                    )
//                }
//            }
//        }
//        fusionManager.start()
//        onDispose { fusionManager.stop() }
//    }
//
//    // ── Step counter: TYPE_STEP_COUNTER (hardware) or accelerometer fallback ────
//    // Hardware step counter runs on the DSP — no CPU wakeups, no drift errors.
//    // Dead-reckoning advances position by STRIDE_LENGTH_M (0.75 m) per step.
//    DisposableEffect(Unit) {
//        val stepTracker = StepTracker(context)
//        stepTracker.onStep = { totalSteps, distMetres ->
//            viewModel.onStepDetected(totalSteps, distMetres)
//        }
//        stepTracker.start()
//        onDispose { stepTracker.stop() }
//    }
//
//    // ── Root layout ───────────────────────────────────────────────────────────
//    Box(modifier = Modifier.fillMaxSize()) {
//
//        // ── AR view ───────────────────────────────────────────────────────────
//        AndroidView(
//            factory = { ctx ->
//                ARSceneView(ctx).apply {
//                    layoutParams = ViewGroup.LayoutParams(
//                        ViewGroup.LayoutParams.MATCH_PARENT,
//                        ViewGroup.LayoutParams.MATCH_PARENT
//                    )
//                    planeRenderer.isEnabled = true
//                    planeRenderer.isVisible = true
//
//                    configureSession { session, config ->
//                        configureArSessionForNavigation(session, config)
//                    }
//
//                    fun handleFrame(session: Session, frame: Frame) {
//                        val trackingState = frame.camera.trackingState
//                        if (trackingState == TrackingState.TRACKING) {
//                                val manager = managerRef.value
//
//                                // Create manager on first TRACKING frame
//                                if (manager == null) {
//                                    if (pathNodes.size < 2) {
//                                        Handler(Looper.getMainLooper()).post {
//                                            viewModel.updateStatusText("❌ No navigation path loaded")
//                                        }
//                                        return
//                                    }
//                                    val transformer = ArCoordinateTransformer(
//                                        startNode = pathNodes.first()
//                                    )
//                                    managerRef.value = ArrowSceneManager(
//                                        sceneView   = this,
//                                        transformer = transformer,
//                                        pathNodes   = pathNodes
//                                    )
//                                    return
//                                }
//
//                                // Always update camera-to-start-pin distance for UI
//                                val distToStart = sqrt(
//                                    manager.cameraArX * manager.cameraArX +
//                                            manager.cameraArZ * manager.cameraArZ
//                                )
//                                Handler(Looper.getMainLooper()).post {
//                                    viewModel.updateDistToStartPin(distToStart)
//                                }
//
//                                when (manager.phase) {
//                                    NavigationPhase.SCANNING -> {
//                                        val floorPlane = session
//                                            .getAllTrackables(Plane::class.java)
//                                            .firstOrNull {
//                                                it.trackingState == TrackingState.TRACKING &&
//                                                        it.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
//                                                        it.extentX > 0.3f && it.extentZ > 0.3f
//                                            }
//                                        if (floorPlane == null) {
//                                            Handler(Looper.getMainLooper()).post {
//                                                viewModel.updateStatusText("👀 Scanning floor… move phone slowly")
//                                            }
//                                            return
//                                        }
//
//                                        val hit = frame
//                                            .hitTest(width / 2f, height / 2f)
//                                            .firstOrNull { h ->
//                                                val t = h.trackable
//                                                t is Plane &&
//                                                        t.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
//                                                        t.isPoseInPolygon(h.hitPose)
//                                            }
//                                        if (hit == null) {
//                                            Handler(Looper.getMainLooper()).post {
//                                                viewModel.updateStatusText("⚠️ Point camera at the floor")
//                                            }
//                                            return
//                                        }
//
//                                        if (!manager.isCollectingSamples) {
//                                            manager.startSampleCollection()
//                                            Handler(Looper.getMainLooper()).post {
//                                                viewModel.updateStatusText("📍 Hold steady — calibrating…")
//                                            }
//                                        }
//
//                                        val ready = manager.feedHitSample(hit)
//                                        val pct   = (manager.sampleProgress * 100).toInt()
//                                        Handler(Looper.getMainLooper()).post {
//                                            viewModel.updateStatusText("📍 Calibrating… $pct%")
//                                        }
//
//                                        if (ready) {
//                                            val n1 = pathNodes[0]; val n2 = pathNodes[1]
//                                            val mapDX = (n2.x - n1.x).toFloat()
//                                            val mapDY = (n2.y - n1.y).toFloat()
//                                            val firstSegMapBearing = Math
//                                                .toDegrees(atan2(mapDX.toDouble(), -mapDY.toDouble()))
//                                                .toFloat()
//
//                                            manager.commitAnchorAndAwaitUser(
//                                                session                   = session,
//                                                frame                     = frame,
//                                                firstSegmentMapBearingDeg = firstSegMapBearing
//                                            )
//                                            planeRenderer.isVisible = false
//                                            Handler(Looper.getMainLooper()).post {
//                                                viewModel.onStartPinPlaced()
//                                            }
//                                        }
//                                    }
//
//                                    NavigationPhase.AWAITING_USER -> {
//                                        // Waiting for user to walk to start pin — nothing per-frame
//                                    }
//
//                                    NavigationPhase.MANUAL_CALIBRATION -> {
//                                        // Compass sensor feeds headings continuously via the
//                                        // DisposableEffect above; nothing extra needed per-frame.
//                                    }
//
//                                    NavigationPhase.NAVIGATING -> {
//                                        manager.onFrame(
//                                            frame = frame,
//                                            currentSegmentIdx = segmentIndex,
//                                            onNodeReached = { reachedIdx ->
//                                                Handler(Looper.getMainLooper()).post {
//                                                    viewModel.onNodeReached(reachedIdx)
//                                                }
//                                            },
//                                            onDeviation = { _, x, z ->
//                                                Handler(Looper.getMainLooper()).post {
//                                                    viewModel.onRerouteTriggered(x, z) { newPath ->
//                                                        manager.rebuildPath(newPath)
//                                                    }
//                                                }
//                                            }
//                                        )
//
//                                        // Feed orientation guidance to the UI
//                                        manager.lastOrientationGuidance?.let { guidance ->
//                                            Handler(Looper.getMainLooper()).post {
//                                                viewModel.updateOrientationGuidance(guidance.turnHint)
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//
//                            else if (trackingState == TrackingState.PAUSED) {
//                                Handler(Looper.getMainLooper()).post {
//                                    viewModel.updateStatusText("⚠️ Move slower — tracking lost")
//                                }
//                            }
//
//                            else if (trackingState == TrackingState.STOPPED) {
//                                Handler(Looper.getMainLooper()).post {
//                                    viewModel.updateStatusText("❌ AR stopped. Please restart.")
//                                }
//                                managerRef.value?.destroy()
//                                managerRef.value = null
//                            }
//                    }
//
//                    onSessionUpdated = { session, frame -> handleFrame(session, frame) }
//                }
//            },
//            update = { /* AR session drives itself */ },
//            onRelease = { view -> view.destroy() },
//            modifier = Modifier.fillMaxSize()
//        )
//
//        // ── TOP: back button + debug toggle + store card ───────────────────────
//        Column(
//            modifier = Modifier
//                .align(Alignment.TopStart)
//                .statusBarsPadding()
//                .padding(horizontal = 16.dp, vertical = 12.dp)
//        ) {
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.SpaceBetween,
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                // Back button
//                Box(
//                    modifier = Modifier
//                        .size(42.dp)
//                        .clip(CircleShape)
//                        .background(Color.White)
//                        .clickable { onBackClick() },
//                    contentAlignment = Alignment.Center
//                ) {
//                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.Black, modifier = Modifier.size(20.dp))
//                }
//
//                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
//                    // Debug overlay toggle
//                    Box(
//                        modifier = Modifier
//                            .size(42.dp)
//                            .clip(CircleShape)
//                            .background(if (showDebugOverlay) NavAmber else Color.White.copy(alpha = 0.8f))
//                            .clickable { viewModel.toggleDebugOverlay() },
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Icon(
//                            Icons.Default.BugReport,
//                            "Debug",
//                            tint = if (showDebugOverlay) Color.White else Color.DarkGray,
//                            modifier = Modifier.size(20.dp)
//                        )
//                    }
//
//                    Box(
//                        modifier = Modifier
//                            .size(42.dp)
//                            .clip(CircleShape)
//                            .background(NavTeal),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Icon(Icons.Default.MyLocation, "Location", tint = Color.White, modifier = Modifier.size(20.dp))
//                    }
//                }
//            }
//
//            Spacer(Modifier.height(12.dp))
//
//            // Debug overlay
//            AnimatedVisibility(visible = showDebugOverlay) {
//                DebugOverlay(
//                    rawHeading = state.debugRawHeadingDeg,
//                    bearing    = state.debugCalculatedBearing,
//                    offset     = state.debugAppliedOffset,
//                    phase      = navigationPhase
//                )
//            }
//
//            Spacer(Modifier.height(8.dp))
//
//            // Store info card
//            AnimatedVisibility(
//                visible = showStoreCard && destinationNode != null,
//                enter = fadeIn(tween(300)) + expandVertically(tween(300)),
//                exit  = fadeOut(tween(300)) + shrinkVertically(tween(300))
//            ) {
//                if (destinationNode != null) {
//                    StoreInfoCard(
//                        node        = destinationNode,
//                        distanceM   = distanceM,
//                        walkMinutes = walkMinutes,
//                        statusText  = statusText,
//                        onClose     = { viewModel.closeStoreCard() }
//                    )
//                }
//            }
//        }
//
//        // ── CENTER overlays ───────────────────────────────────────────────────
//
//        // AWAITING_USER — walk to start pin
//        AnimatedVisibility(
//            visible  = navigationPhase == NavigationPhase.AWAITING_USER,
//            modifier = Modifier
//                .align(Alignment.Center)
//                .padding(horizontal = 32.dp),
//            enter = fadeIn() + scaleIn(),
//            exit  = fadeOut() + scaleOut()
//        ) {
//            StartPinConfirmCard(
//                distToStartPinM = distToStartPinM,
//                onConfirm = {
//                    val manager = managerRef.value ?: return@StartPinConfirmCard
//                    val result  = manager.requestStartNavigation(compassAccuracy.intValue)
//                    viewModel.onUserConfirmedStart(result)
//                }
//            )
//        }
//
//        // MANUAL_CALIBRATION — point phone toward destination
//        AnimatedVisibility(
//            visible  = navigationPhase == NavigationPhase.MANUAL_CALIBRATION,
//            modifier = Modifier
//                .align(Alignment.Center)
//                .padding(horizontal = 32.dp),
//            enter = fadeIn() + scaleIn(),
//            exit  = fadeOut() + scaleOut()
//        ) {
//            ManualDirectionCalibrationCard(
//                onConfirmDirection = {
//                    val manager = managerRef.value ?: return@ManualDirectionCalibrationCard
//                    val success = manager.confirmManualDirection(compassHeading.floatValue)
//                    if (success) {
//                        viewModel.onManualCalibrationConfirmed()
//                    } else {
//                        viewModel.onManualCalibrationFailed()
//                    }
//                }
//            )
//        }
//
//        // Waypoint reached notification
//        AnimatedVisibility(
//            visible  = reachedNotification != null,
//            modifier = Modifier.align(Alignment.Center),
//            enter    = fadeIn() + scaleIn(),
//            exit     = fadeOut() + scaleOut()
//        ) {
//            Box(
//                modifier = Modifier
//                    .background(NavGreen.copy(alpha = 0.93f), RoundedCornerShape(18.dp))
//                    .padding(horizontal = 32.dp, vertical = 16.dp)
//            ) {
//                Text(
//                    text       = reachedNotification ?: "",
//                    color      = Color.White,
//                    fontSize   = 20.sp,
//                    fontWeight = FontWeight.Bold
//                )
//            }
//        }
//
//        // ── BOTTOM: navigation instruction + show road ────────────────────────
//        AnimatedVisibility(
//            visible  = navigationPhase == NavigationPhase.NAVIGATING,
//            modifier = Modifier.align(Alignment.BottomCenter),
//            enter    = fadeIn() + slideInVertically { it / 2 },
//            exit     = fadeOut() + slideOutVertically { it / 2 }
//        ) {
//            Column(
//                modifier = Modifier
//                    .navigationBarsPadding()
//                    .padding(bottom = 28.dp),
//                horizontalAlignment = Alignment.CenterHorizontally
//            ) {
//                if (pathNodes.size >= 2) {
//                    val dynamicInstruction = when (state.orientationTurnHint) {
//                        TurnHint.STRAIGHT -> currentInstructionLabel
//                        TurnHint.LEFT     -> "↰  Turn Left"
//                        TurnHint.RIGHT    -> "↱  Turn Right"
//                        else              -> currentInstructionLabel
//                    }
//
//                    Button(
//                        onClick  = { /* informational */ },
//                        shape    = RoundedCornerShape(24.dp),
//                        colors   = ButtonDefaults.buttonColors(containerColor = NavTeal),
//                        elevation = ButtonDefaults.buttonElevation(4.dp),
//                        modifier = Modifier.widthIn(min = 200.dp).height(50.dp)
//                    ) {
//                        Icon(Icons.Default.Navigation, null, tint = Color.White)
//                        Spacer(Modifier.width(8.dp))
//                        Text(
//                            dynamicInstruction,
//                            color      = Color.White,
//                            fontWeight = FontWeight.SemiBold,
//                            fontSize   = 15.sp
//                        )
//                    }
//                    Spacer(Modifier.height(14.dp))
//                }
//
//                Row(
//                    modifier = Modifier
//                        .clip(RoundedCornerShape(20.dp))
//                        .clickable { viewModel.toggleRouteSheet() }
//                        .background(Color.Black.copy(alpha = 0.45f))
//                        .padding(horizontal = 20.dp, vertical = 10.dp),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Text("Show Road", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
//                    Spacer(Modifier.width(6.dp))
//                    Icon(
//                        imageVector = if (showRouteSheet) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.ArrowForward,
//                        contentDescription = null,
//                        tint = Color.White,
//                        modifier = Modifier.size(18.dp)
//                    )
//                }
//            }
//        }
//
//        // ── BOTTOM SHEET: route detail ────────────────────────────────────────
//        AnimatedVisibility(
//            visible  = showRouteSheet,
//            modifier = Modifier.align(Alignment.BottomCenter),
//            enter    = slideInVertically(tween(350)) { it },
//            exit     = slideOutVertically(tween(300)) { it }
//        ) {
//            RouteSheet(
//                destination     = destinationNode,
//                steps           = routeSteps,
//                segmentIndex    = segmentIndex,
//                distanceM       = distanceM,
//                walkMinutes     = walkMinutes,
//                onEndNavigation = onBackClick
//            )
//        }
//    }
//
//    DisposableEffect(Unit) {
//        onDispose { managerRef.value = null }
//    }
//}
//
//// ─────────────────────────────────────────────────────────────────────────────
//// Start pin confirmation card — user walks to the green pin
//// ─────────────────────────────────────────────────────────────────────────────
//@Composable
//private fun StartPinConfirmCard(
//    distToStartPinM: Float,
//    onConfirm: () -> Unit
//) {
//    val distText = if (distToStartPinM < Float.MAX_VALUE / 2)
//        "%.1fm away".format(distToStartPinM) else "Locating…"
//    val closeEnough = distToStartPinM <= 2.5f
//
//    Card(
//        shape     = RoundedCornerShape(20.dp),
//        colors    = CardDefaults.cardColors(containerColor = Color.White),
//        elevation = CardDefaults.cardElevation(8.dp)
//    ) {
//        Column(
//            modifier            = Modifier.padding(24.dp),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            Icon(
//                imageVector = Icons.Default.LocationOn,
//                contentDescription = null,
//                tint     = Color(0xFF43A047),
//                modifier = Modifier.size(40.dp)
//            )
//            Spacer(Modifier.height(12.dp))
//            Text(
//                "Walk to the Green Pin",
//                fontWeight = FontWeight.Bold,
//                fontSize   = 18.sp,
//                color      = Color.Black
//            )
//            Spacer(Modifier.height(6.dp))
//            Text(
//                distText,
//                fontSize = 14.sp,
//                color    = if (closeEnough) Color(0xFF43A047) else Color.Gray
//            )
//            Spacer(Modifier.height(16.dp))
//            Button(
//                onClick  = onConfirm,
//                enabled  = closeEnough,
//                shape    = RoundedCornerShape(14.dp),
//                colors   = ButtonDefaults.buttonColors(
//                    containerColor         = Color(0xFF009688),
//                    disabledContainerColor = Color(0xFFB2DFDB)
//                ),
//                modifier = Modifier.fillMaxWidth().height(48.dp)
//            ) {
//                Text(
//                    if (closeEnough) "I'm Here" else "Keep Walking…",
//                    color      = Color.White,
//                    fontWeight = FontWeight.Bold
//                )
//            }
//        }
//    }
//}
//
//// ─────────────────────────────────────────────────────────────────────────────
//// Manual Direction Calibration card
//// ─────────────────────────────────────────────────────────────────────────────
//@Composable
//private fun ManualDirectionCalibrationCard(
//    onConfirmDirection: () -> Unit
//) {
//    Card(
//        shape     = RoundedCornerShape(20.dp),
//        colors    = CardDefaults.cardColors(containerColor = Color.White),
//        elevation = CardDefaults.cardElevation(8.dp)
//    ) {
//        Column(
//            modifier            = Modifier.padding(24.dp),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            // Animated compass icon hint
//            Icon(
//                imageVector = Icons.Default.Explore,
//                contentDescription = null,
//                tint     = Color(0xFF1565C0),
//                modifier = Modifier.size(44.dp)
//            )
//            Spacer(Modifier.height(12.dp))
//            Text(
//                text       = "Align Direction",
//                fontWeight = FontWeight.Bold,
//                fontSize   = 20.sp,
//                color      = Color.Black
//            )
//            Spacer(Modifier.height(10.dp))
//
//            // Step 1
//            CalibrationStep(
//                number = "1",
//                text   = "Point your phone toward the real-world destination direction"
//            )
//            Spacer(Modifier.height(8.dp))
//
//            // Step 2
//            CalibrationStep(
//                number = "2",
//                text   = "Hold still, then tap \"Confirm Direction\" below"
//            )
//
//            Spacer(Modifier.height(20.dp))
//
//            // Direction indicator hint
//            Box(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .clip(RoundedCornerShape(10.dp))
//                    .background(Color(0xFFF0F4FF))
//                    .padding(12.dp),
//                contentAlignment = Alignment.Center
//            ) {
//                Row(verticalAlignment = Alignment.CenterVertically) {
//                    Icon(
//                        Icons.Default.Navigation,
//                        contentDescription = null,
//                        tint = Color(0xFF1565C0),
//                        modifier = Modifier.size(20.dp)
//                    )
//                    Spacer(Modifier.width(8.dp))
//                    Text(
//                        "Phone forward = destination direction",
//                        fontSize   = 13.sp,
//                        color      = Color(0xFF1565C0),
//                        fontWeight = FontWeight.Medium,
//                        textAlign  = TextAlign.Center
//                    )
//                }
//            }
//
//            Spacer(Modifier.height(20.dp))
//
//            Button(
//                onClick  = onConfirmDirection,
//                shape    = RoundedCornerShape(14.dp),
//                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
//                modifier = Modifier.fillMaxWidth().height(52.dp)
//            ) {
//                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
//                Spacer(Modifier.width(8.dp))
//                Text(
//                    "Confirm Direction",
//                    color      = Color.White,
//                    fontWeight = FontWeight.Bold,
//                    fontSize   = 16.sp
//                )
//            }
//        }
//    }
//}
//
//@Composable
//private fun CalibrationStep(number: String, text: String) {
//    Row(
//        modifier = Modifier.fillMaxWidth(),
//        verticalAlignment = Alignment.Top
//    ) {
//        Box(
//            modifier = Modifier
//                .size(24.dp)
//                .clip(CircleShape)
//                .background(Color(0xFF1565C0)),
//            contentAlignment = Alignment.Center
//        ) {
//            Text(number, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
//        }
//        Spacer(Modifier.width(10.dp))
//        Text(
//            text     = text,
//            fontSize = 14.sp,
//            color    = Color(0xFF333333),
//            modifier = Modifier.weight(1f)
//        )
//    }
//}
//
//// ─────────────────────────────────────────────────────────────────────────────
//// Debug overlay
//// ─────────────────────────────────────────────────────────────────────────────
//@Composable
//private fun DebugOverlay(
//    rawHeading: Float,
//    bearing:    Float,
//    offset:     Float,
//    phase:      NavigationPhase
//) {
//    Card(
//        shape     = RoundedCornerShape(10.dp),
//        colors    = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
//        elevation = CardDefaults.cardElevation(0.dp),
//        modifier  = Modifier.fillMaxWidth()
//    ) {
//        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
//            Text(
//                "🐛 Debug — Phase: ${phase.name}",
//                color      = Color(0xFFFFA000),
//                fontSize   = 11.sp,
//                fontWeight = FontWeight.Bold
//            )
//            Spacer(Modifier.height(4.dp))
//            DebugRow("Raw compass heading", "%.1f°".format(rawHeading))
//            DebugRow("Calculated bearing",  "%.1f°".format(bearing))
//            DebugRow("Applied offset",      "%.1f°".format(offset))
//        }
//    }
//}
//
//@Composable
//private fun DebugRow(label: String, value: String) {
//    Row(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(vertical = 1.dp),
//        horizontalArrangement = Arrangement.SpaceBetween
//    ) {
//        Text(label, color = Color(0xFFBBBBBB), fontSize = 11.sp)
//        Text(value, color = Color.White,       fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
//    }
//}
//
//// ─────────────────────────────────────────────────────────────────────────────
//// Store info card (unchanged)
//// ─────────────────────────────────────────────────────────────────────────────
//@Composable
//private fun StoreInfoCard(
//    node: GraphNode,
//    distanceM: Int,
//    walkMinutes: Int,
//    statusText: String,
//    onClose: () -> Unit
//) {
//    Card(
//        shape     = RoundedCornerShape(16.dp),
//        colors    = CardDefaults.cardColors(containerColor = Color.White),
//        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
//        modifier  = Modifier.fillMaxWidth()
//    ) {
//        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
//            if (node.logo != null) {
//                AsyncImage(
//                    model              = "file:///android_asset/${node.logo}",
//                    contentDescription = node.shopName,
//                    modifier           = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
//                    contentScale       = ContentScale.Fit
//                )
//            } else {
//                Box(
//                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF009688)),
//                    contentAlignment = Alignment.Center
//                ) {
//                    Icon(Icons.Default.Store, null, tint = Color.White)
//                }
//            }
//            Spacer(Modifier.width(12.dp))
//            Column(Modifier.weight(1f)) {
//                Text(node.shopName ?: "Destination", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.Black)
//                Spacer(Modifier.height(4.dp))
//                Row(verticalAlignment = Alignment.CenterVertically) {
//                    Icon(Icons.Default.LocationOn, null, tint = Color(0xFFE53935), modifier = Modifier.size(13.dp))
//                    Text(" ${distanceM}m", fontSize = 13.sp, color = Color.Gray)
//                    Spacer(Modifier.width(10.dp))
//                    Icon(Icons.Default.AccessTime, null, tint = Color(0xFF009688), modifier = Modifier.size(13.dp))
//                    Text(" ${walkMinutes}min", fontSize = 13.sp, color = Color.Gray)
//                }
//                Text(statusText, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
//            }
//            IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
//                Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
//            }
//        }
//    }
//}
//
//// ─────────────────────────────────────────────────────────────────────────────
//// Route sheet (unchanged)
//// ─────────────────────────────────────────────────────────────────────────────
//@Composable
//private fun RouteSheet(
//    destination: GraphNode?,
//    steps: List<StepItem>,
//    segmentIndex: Int,
//    distanceM: Int,
//    walkMinutes: Int,
//    onEndNavigation: () -> Unit
//) {
//    Card(
//        shape     = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
//        colors    = CardDefaults.cardColors(containerColor = Color.White),
//        elevation = CardDefaults.cardElevation(12.dp),
//        modifier  = Modifier.fillMaxWidth()
//    ) {
//        Column(
//            modifier = Modifier
//                .fillMaxWidth()
//                .heightIn(max = 520.dp)
//                .verticalScroll(rememberScrollState())
//                .padding(horizontal = 24.dp, vertical = 20.dp)
//        ) {
//            Box(Modifier.width(40.dp).height(4.dp).background(Color(0xFFDDDDDD), CircleShape).align(Alignment.CenterHorizontally))
//            Spacer(Modifier.height(16.dp))
//            Text(destination?.shopName ?: "Destination", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black)
//            Spacer(Modifier.height(4.dp))
//            Row(verticalAlignment = Alignment.CenterVertically) {
//                Text("1st Floor", fontSize = 14.sp, color = Color.Gray)
//                Text("  |  $walkMinutes min walk", fontSize = 14.sp, color = Color.Gray)
//                Spacer(Modifier.weight(1f))
//                Text("${distanceM}m", fontSize = 13.sp, color = Color(0xFF009688), fontWeight = FontWeight.SemiBold)
//            }
//            Spacer(Modifier.height(20.dp))
//            HorizontalDivider(color = Color(0xFFEEEEEE))
//            Spacer(Modifier.height(16.dp))
//            steps.forEachIndexed { i, step ->
//                RouteStepRow(
//                    step      = step,
//                    isFirst   = i == 0,
//                    isLast    = i == steps.size - 1,
//                    isPassed  = step.nodeIndex != null && step.nodeIndex < segmentIndex,
//                    isCurrent = step.nodeIndex != null && step.nodeIndex == segmentIndex,
//                    showLine  = i < steps.size - 1
//                )
//            }
//            Spacer(Modifier.height(24.dp))
//            Button(
//                onClick  = onEndNavigation,
//                modifier = Modifier.fillMaxWidth().height(52.dp),
//                shape    = RoundedCornerShape(14.dp),
//                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF009688))
//            ) {
//                Text("End Navigation", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
//            }
//        }
//    }
//}
//
//// ─────────────────────────────────────────────────────────────────────────────
//@Composable
//private fun RouteStepRow(
//    step: StepItem,
//    isFirst: Boolean,
//    isLast: Boolean,
//    isPassed: Boolean,
//    isCurrent: Boolean,
//    showLine: Boolean
//) {
//    Row(modifier = Modifier.fillMaxWidth()) {
//        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
//            if (step.isWaypoint) {
//                Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(
//                    when {
//                        isFirst   -> Color(0xFF43A047)
//                        isPassed  -> Color(0xFF009688)
//                        isCurrent -> Color(0xFF009688)
//                        else      -> Color(0xFFBDBDBD)
//                    }
//                ))
//            } else {
//                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFCCCCCC)))
//            }
//            if (showLine) {
//                Box(modifier = Modifier.width(2.dp).height(28.dp).background(Color(0xFFDDDDDD)))
//            }
//        }
//        Spacer(Modifier.width(14.dp))
//        Column(modifier = Modifier.padding(bottom = 4.dp)) {
//            if (step.isWaypoint && isFirst) {
//                Text("Your Location", fontSize = 10.sp, color = Color(0xFF43A047), fontWeight = FontWeight.SemiBold)
//            }
//            Text(
//                text       = step.label,
//                fontSize   = if (step.isWaypoint) 15.sp else 13.sp,
//                fontWeight = if (step.isWaypoint) FontWeight.SemiBold else FontWeight.Normal,
//                color      = when {
//                    isPassed  -> Color(0xFFAAAAAA)
//                    isCurrent -> Color(0xFF009688)
//                    isLast    -> Color(0xFF555555)
//                    else      -> Color(0xFF333333)
//                }
//            )
//        }
//    }
//}
