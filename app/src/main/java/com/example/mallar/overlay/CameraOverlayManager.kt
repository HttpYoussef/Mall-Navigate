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
import com.example.mallar.navigation.NavigationSessionManager
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "CameraManager"

// ─────────────────────────────────────────────────────────────────────────────
/**
 * CameraOverlayManager
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * FIX 1 — RELOCALIZATION PIPELINE
 * ─────────────────────────────────────────────────────────────────────────────
 * In addition to the preview surface, this manager now binds a CameraX
 * ImageAnalysis use-case when startCamera(relocalizationEnabled = true).
 *
 * Relocalization is intentionally throttled — NOT every frame.
 * It fires only when drift is elevated or NavSessionState carries a reloc hint.
 *
 * Pipeline per triggered frame:
 *   ImageProxy → Bitmap
 *             → LogoDetector.detectTopNWithLocation()
 *             → LocalizationEngine.estimatePose()
 *             → best GraphNode
 *             → NavigationSessionManager.onLogoDetected()  ← drift correction
 */
class CameraOverlayManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor   = Executors.newSingleThreadExecutor()
    private var analysisExecutor = Executors.newSingleThreadExecutor()
    /** Cleared in [stopCamera] so analyzers never outlive their use case. */
    private var boundImageAnalysis: ImageAnalysis? = null

    // Lazy ML components — allocated only when relocalization is first needed
    private val logoDetector: LogoDetector by lazy { LogoDetector(context) }


    /** Prevents two frames from running the relocalization pipeline concurrently. */
    private val isProcessingFrame = AtomicBoolean(false)

    /** Minimum gap between relocalization attempts (ms). */
    private var lastRelocMs = 0L
    private val RELOC_INTERVAL_MS = 2_000L

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Start the camera preview.
     *
     * @param relocalizationEnabled  Pass true during active AR navigation so that
     *                               the ImageAnalysis use-case runs and drift is
     *                               corrected via the relocalization pipeline.
     */
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
        cameraExecutor.shutdown()
        analysisExecutor.shutdown()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private — camera binding
    // ─────────────────────────────────────────────────────────────────────────

    private fun bindPreview(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        relocalizationEnabled: Boolean
    ) {
        val provider = cameraProvider ?: return

        val previewBuilder = Preview.Builder()
        Camera2Interop.Extender(previewBuilder)
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
            )
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )

        val preview = previewBuilder.build().apply {
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
                Log.d(TAG, "Camera bound: preview + ImageAnalysis (relocalization ENABLED)")
            } else {
                provider.bindToLifecycle(lifecycleOwner, selector, preview)
                Log.d(TAG, "Camera bound: preview only")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Camera bind failed: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FIX 1 — Relocalization pipeline
    // ─────────────────────────────────────────────────────────────────────────

    private fun processFrameForRelocalization(imageProxy: ImageProxy) {
        var proxyClosed = false
        fun closeProxy() {
            if (proxyClosed) return
            proxyClosed = true
            try {
                imageProxy.close()
            } catch (_: Exception) {
            }
        }
        if (!isProcessingFrame.compareAndSet(false, true)) {
            closeProxy()
            return
        }
        try {
            val now = System.currentTimeMillis()
            if (now - lastRelocMs < RELOC_INTERVAL_MS) {
                closeProxy()
                return
            }
            if (!shouldRelocalize()) {
                closeProxy()
                return
            }

            val bitmap = imageProxy.toBitmap()
            closeProxy()

            if (bitmap == null) {
                return
            }

            lastRelocMs = now
            runRelocalizationPipeline(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error: ${e.message}")
            closeProxy()
        } finally {
            isProcessingFrame.set(false)
        }
    }

    /**
     * Returns true when visual relocalization is worth the ML cost.
     * Do NOT run analysis on every camera frame just because AR mode is on —
     * that causes thermal throttling, frame drops, and janky Compose.
     *
     * Triggers: elevated drift or an explicit reloc hint from [DriftMonitor].
     */
    private fun shouldRelocalize(): Boolean {
        val state = NavigationSessionManager.instance.sessionState.value
        val driftBad  = state.driftLevel != DriftMonitor.DriftLevel.OK
        val hasReason = state.relocReason != null
        return driftBad || hasReason
    }

    /**
     * Bitmap → LogoDetector → LocalizationEngine → NavigationSessionManager
     */
    private fun runRelocalizationPipeline(bitmap: Bitmap) {
        try {
            val graph  = MallGraphRepository.loadedGraph ?: return
            val places = PlaceRepository.load(context)
            if (places.isEmpty()) return

            // 1. Detect top-N landmarks with image-space location
            val detections = logoDetector.detectTopNWithLocation(bitmap)
            if (detections.isEmpty()) {
                Log.d(TAG, "Reloc: no landmarks detected in frame")
                return
            }

            // 2. Estimate pose (PnP + heading)
            val result = LocalizationEngine.estimatePose(
                frame    = bitmap,
                detector = logoDetector,
                graph    = graph,
                places   = places
            )

            // 3. Feed best node into session manager for position correction
            val bestNode = result.bestStartNode

            if (bestNode != null) {
                NavigationSessionManager.instance.onLogoDetected(bestNode)
                Log.d(TAG, "Reloc: corrected to node ${bestNode.id} (${bestNode.shopName})")
            } else {
                Log.d(TAG, "Reloc: pose estimated but no matching graph node found")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Relocalization pipeline error: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: ImageProxy (YUV_420_888) → Bitmap
    // ─────────────────────────────────────────────────────────────────────────

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
