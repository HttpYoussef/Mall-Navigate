package com.example.mallar.ar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.arcore.ARSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference
import java.io.ByteArrayOutputStream

data class CameraImageSnapshot(
    val y: ByteArray,
    val u: ByteArray,
    val v: ByteArray,
    val width: Int,
    val height: Int
) {
    fun toBitmap(): Bitmap? = try {
        val nv21 = ByteArray(y.size + u.size + v.size)
        y.copyInto(nv21, 0)
        v.copyInto(nv21, y.size)
        u.copyInto(nv21, y.size + v.size)
        val output = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, width, height, null)
            .compressToJpeg(Rect(0, 0, width, height), 85, output)
        BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size())
    } catch (_: Exception) {
        null
    }
}

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Module 2 — ARCore Session Layer
 * ─────────────────────────────────────────────────────────────────────────────
 * 
 * Owns continuous six-degree-of-freedom pose tracking and provides the physical-world 
 * reference. Managed by UnifiedNavigationViewModel.
 */
class ArCoreSessionManager(private val context: Context) {
    companion object {
        private const val TAG = "ArCoreSessionManager"
    }

    enum class LifecycleState { CREATED, RESUMED, PAUSED, DESTROYED }

    private val _lifecycleState = MutableStateFlow(LifecycleState.PAUSED)
    val lifecycleState: StateFlow<LifecycleState> = _lifecycleState.asStateFlow()

    private val _trackingState = MutableStateFlow(TrackingState.STOPPED)
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

    private val _failureReason = MutableStateFlow(TrackingFailureReason.NONE)
    val failureReason: StateFlow<TrackingFailureReason> = _failureReason.asStateFlow()

    private val sessionRef = AtomicReference<Session?>(null)
    private val lifecycleLock = Any()

    /**
     * Initializes the ARCore Session.
     * Precondition: CameraX has been fully released by LogoScanScreen.
     */
    fun createSession(): Session? = synchronized(lifecycleLock) {
        sessionRef.get()?.let { return@synchronized it }
        if (_lifecycleState.value == LifecycleState.DESTROYED) return@synchronized null
        Log.d(TAG, "Module 2: Attempting to create ARCore Session...")
        try {
            // Use SceneView's ARSession subtype so the manager-owned session
            // can be consumed by ManagedARSceneView without a second session.
            val session = ARSession(
                context = context,
                onResumed = {},
                onPaused = {},
                onConfigChanged = { _, _ -> }
            )
            val config = Config(session)
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            config.focusMode = Config.FocusMode.FIXED
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            session.configure(config)
            sessionRef.set(session)
            _lifecycleState.value = LifecycleState.CREATED
            Log.d(TAG, "Module 2: ARCore Session created successfully.")
            return session
        } catch (e: Exception) {
            Log.e(TAG, "Module 2: Failed to create ARCore session", e)
            return null
        }
    }

    fun resume() = synchronized(lifecycleLock) {
        if (_lifecycleState.value == LifecycleState.RESUMED ||
            _lifecycleState.value == LifecycleState.DESTROYED
        ) return@synchronized
        sessionRef.get()?.let { session ->
            try {
                session.resume()
                _lifecycleState.value = LifecycleState.RESUMED
                Log.d(TAG, "Module 2: ARCore Session resumed.")
            } catch (e: Exception) {
                Log.e(TAG, "Module 2: Failed to resume session", e)
            }
        }
    }

    fun pause() = synchronized(lifecycleLock) {
        // Surface teardown, ON_PAUSE, and Compose disposal can all request a
        // pause for the same session. ARCore.pause() is not safe to enter
        // repeatedly during native teardown, so make this transition strict.
        if (_lifecycleState.value != LifecycleState.RESUMED) {
            return@synchronized
        }
        sessionRef.get()?.let { session ->
            try {
                session.pause()
                _lifecycleState.value = LifecycleState.PAUSED
                Log.d(TAG, "Module 2: ARCore Session paused.")
            } catch (e: Exception) {
                Log.e(TAG, "Module 2: Failed to pause session", e)
            }
        }
    }

    fun destroy() = synchronized(lifecycleLock) {
        sessionRef.getAndSet(null)?.let { session ->
            session.close()
            _lifecycleState.value = LifecycleState.DESTROYED
            Log.d(TAG, "Module 2: ARCore Session destroyed.")
        } ?: run {
            _lifecycleState.value = LifecycleState.DESTROYED
        }
    }

    /**
     * Updates internal tracking state flows from the current frame.
     * To be called by the render loop (ArSceneView).
     */
    fun updateTrackingState(state: TrackingState, reason: TrackingFailureReason) {
        if (_trackingState.value != state || _failureReason.value != reason) {
            _trackingState.value = state
            _failureReason.value = reason
            Log.d(TAG, "Module 2: Tracking State Changed: $state (Reason: $reason)")
        }
    }

    /**
     * Copies the current ARCore camera image and closes it before returning.
     * Recognition may safely consume the returned immutable bytes off the
     * render thread; no second camera session is created.
     */
    fun copyCameraImage(frame: Frame): CameraImageSnapshot? {
        return try {
            val image = frame.acquireCameraImage()
            try {
                val y = image.planes[0].buffer.let { buffer -> ByteArray(buffer.remaining()).also(buffer::get) }
                val u = image.planes[1].buffer.let { buffer -> ByteArray(buffer.remaining()).also(buffer::get) }
                val v = image.planes[2].buffer.let { buffer -> ByteArray(buffer.remaining()).also(buffer::get) }
                CameraImageSnapshot(y, u, v, image.width, image.height)
            } finally {
                image.close()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Camera image unavailable for periodic fix: ${e.message}")
            null
        }
    }

    fun getSession(): Session? = sessionRef.get()
}
