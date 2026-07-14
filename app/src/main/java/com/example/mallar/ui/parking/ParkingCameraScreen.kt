package com.example.mallar.ui.parking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mallar.ui.theme.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object ParkingCameraState {
    var capturedBitmap: Bitmap? = null
}

@Composable
fun ParkingCameraScreen(
    onBackClick: () -> Unit,
    onPhotoCaptured: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDarkMode by com.example.mallar.data.AppPreferences.isDarkMode.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDarkMode) DarkBackground else White),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Camera Permission Required",
                    color = if (isDarkMode) DarkTextPrimary else TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "This feature needs camera access to take a photo of the parking column marker.",
                    color = if (isDarkMode) DarkTextSecondary else TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF258799)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Grant Permission", color = White)
                }
            }
        }
        return
    }

    var flashEnabled by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var cameraInfo by remember { mutableStateOf<CameraInfo?>(null) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(flashEnabled, cameraControl) {
        cameraControl?.enableTorch(flashEnabled)
    }

    DisposableEffect(lifecycleOwner) {
        val provider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        try {
            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
            cameraControl = camera.cameraControl
            cameraInfo = camera.cameraInfo
        } catch (e: Exception) {
            Log.e("ParkingCamera", "Binding failed", e)
        }

        onDispose {
            try {
                provider.unbindAll()
            } catch (_: Exception) {}
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val rawBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (rawBitmap != null) {
                    ParkingCameraState.capturedBitmap = rawBitmap
                    onPhotoCaptured()
                }
            } catch (e: Exception) {
                Log.e("ParkingCamera", "Failed to load gallery image", e)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera view
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Top Controls Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBackClick,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = White)
                }
            }
            Text(
                text = "Parking Assistant",
                color = White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Car",
                        tint = White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Center Overlay Target frame
        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(260.dp)) {
                val s = size.minDimension
                val c = center
                val r = s * 0.48f
                val arm = s * 0.18f
                val gap = s * 0.08f
                val col = Color(0xFF00BCD4) // Teal cyan target color
                listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
                    drawLine(col, Offset(c.x + sx * gap, c.y + sy * r),
                        Offset(c.x + sx * (gap + arm), c.y + sy * r), strokeWidth = 4f, cap = StrokeCap.Round)
                    drawLine(col, Offset(c.x + sx * r, c.y + sy * gap),
                        Offset(c.x + sx * r, c.y + sy * (gap + arm)), strokeWidth = 4f, cap = StrokeCap.Round)
                }
            }
        }

        // Bottom control panel matching mockup Screen 1
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.Black.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Position the code inside the frame\nMake sure it's clear and well lit",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Gallery button
                        Surface(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.size(52.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Photo,
                                    contentDescription = "Gallery",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Center: Glowing capture shutter button
                        if (isCapturing) {
                            CircularProgressIndicator(color = Color(0xFF00BCD4), modifier = Modifier.size(64.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .border(3.dp, Color(0xFF00BCD4), CircleShape) // glowing cyan outer ring
                                    .padding(5.dp)
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                                    .clickable {
                                        isCapturing = true
                                        imageCapture.takePicture(
                                            Executors.newSingleThreadExecutor(),
                                            object : ImageCapture.OnImageCapturedCallback() {
                                                override fun onCaptureSuccess(image: ImageProxy) {
                                                    try {
                                                        val buffer = image.planes[0].buffer
                                                        val bytes = ByteArray(buffer.remaining())
                                                        buffer.get(bytes)
                                                        val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
                                                        
                                                        // Rotate bitmap based on image info rotation degrees
                                                        val matrix = Matrix().apply {
                                                            postRotate(image.imageInfo.rotationDegrees.toFloat())
                                                        }
                                                        val rotatedBitmap = Bitmap.createBitmap(
                                                            rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                                                        )

                                                        ParkingCameraState.capturedBitmap = rotatedBitmap

                                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                            isCapturing = false
                                                            // Navigate to result
                                                            onPhotoCaptured()
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("ParkingCamera", "Photo capture processing failed", e)
                                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                            isCapturing = false
                                                        }
                                                    } finally {
                                                        image.close()
                                                    }
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    Log.e("ParkingCamera", "Photo capture failed", exception)
                                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                        isCapturing = false
                                                    }
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(Color.White, CircleShape)
                                )
                            }
                        }

                        // Right: Flash toggle button
                        Surface(
                            onClick = { flashEnabled = !flashEnabled },
                            modifier = Modifier.size(52.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Flash",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
