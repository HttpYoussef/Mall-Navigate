package com.example.mallar.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CaptureRequest
import android.util.Log
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.data.PlaceRepository
import com.example.mallar.ml.LocalizationEngine
import com.example.mallar.ml.LogoDetector
import com.example.mallar.navigation.DriftMonitor
import com.example.mallar.navigation.NavSessionState
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "CameraManager"

/**
 * CameraOverlayManager
 * ─────────────────────────────────────────────────────────────────────────────
 * Manages the CameraX lifecycle and the visual relocalization pipeline.
 */
class CameraOverlayManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor = Executors.newSingleThreadExecutor()
    private var boundImageAnalysis: ImageAnalysis? = null

    private val logoDetector: LogoDetector by lazy { LogoDetector.getInstance(context) }

    /** Prevents two frames from running the relocalization pipeline concurrently. */
    private val isProcessingFrame = AtomicBoolean(false)

    /** Minimum gap between relocalization attempts (ms). */
    private var lastRelocMs = 0L
    private val RELOC_INTERVAL_MS = 2_000L

    // ── DECOUPLING FIX ───────────────────────────────────────────────────────
    /** Provider for current nav state, avoiding singleton dependency. */
    var navStateProvider: (() -> NavSessionState?)? = null
    
    /** Callback for when a logo is detected. */
    var onLogoDetected: ((com.example.mallar.data.GraphNode) -> Unit)? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        relocalizationEnabled: Boolean = false
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            bindPreview(lifecycleOwner, surfaceProvider, relocalizationEnabled)
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopCamera() {
        try {
            boundImageAnalysis?.clearAnalyzer()
            boundImageAnalysis = null
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera: ${e.message}")
        }
    }

    fun release() {
        stopCamera()
        analysisExecutor.shutdown()
    }

    private fun bindPreview(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        relocalizationEnabled: Boolean
    ) {
        val provider = cameraProvider ?: return

        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(surfaceProvider)
        }

        val selector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            boundImageAnalysis?.clearAnalyzer()
            boundImageAnalysis = null
            provider.unbindAll()

            if (relocalizationEnabled) {
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(analysisExecutor) { proxy ->
                    processFrameForRelocalization(proxy)
                }
                boundImageAnalysis = imageAnalysis

                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
            } else {
                provider.bindToLifecycle(lifecycleOwner, selector, preview)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Camera bind failed: ${e.message}")
        }
    }

    private fun processFrameForRelocalization(imageProxy: ImageProxy) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        try {
            val now = System.currentTimeMillis()
            if (now - lastRelocMs < RELOC_INTERVAL_MS || !shouldRelocalize()) {
                imageProxy.close()
                return
            }

            val bitmap = imageProxy.toBitmap()
            imageProxy.close()

            if (bitmap != null) {
                lastRelocMs = now
                runRelocalizationPipeline(bitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error: ${e.message}")
            imageProxy.close()
        } finally {
            isProcessingFrame.set(false)
        }
    }

    private fun shouldRelocalize(): Boolean {
        val state = navStateProvider?.invoke() ?: return false
        return state.driftLevel != DriftMonitor.DriftLevel.OK || state.relocReason != null
    }

    private fun runRelocalizationPipeline(bitmap: Bitmap) {
        try {
            val graph  = MallGraphRepository.loadedGraph ?: return
            val places = PlaceRepository.load(context)
            if (places.isEmpty()) return

            val detections = logoDetector.detectTopNWithLocation(bitmap)
            if (detections.isEmpty()) return

            val result = LocalizationEngine.estimatePose(
                frame    = bitmap,
                detector = logoDetector,
                graph    = graph,
                places   = places
            )

            result.bestStartNode?.let { node ->
                onLogoDetected?.invoke(node)
                Log.d(TAG, "Reloc: corrected to node ${node.id} (${node.shopName})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Relocalization pipeline error: ${e.message}")
        }
    }

    private fun ImageProxy.toBitmap(): Bitmap? {
        return try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 85, out)
            val bytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "YUV→Bitmap failed: ${e.message}")
            null
        }
    }
}
