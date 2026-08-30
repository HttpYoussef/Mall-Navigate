# Phase 4 Crash Fix Report — MallAR AR Subsystem

**Status:** Defect fix for the `FatalException` discovered during Phase 4 Human Device Validation.

## 1. Problem & Observed Behaviour
**Problem:** A native ARCore `FatalException` occurred on a physical Samsung Galaxy S22 Ultra during transitions in the AR navigation flow.

**Observed Behaviour:** 
- The app freezes on the "Get Oriented" screen for ~2 seconds.
- The app crashes to the home screen.
- Logcat reveals `FAILED_PRECONDITION: The subsystem Image is not started, expected to be started` in the ARCore native layer, followed by a `com.google.ar.core.exceptions.FatalException` at `Session.nativeUpdate`.

## 2. Root Cause Analysis
The defect was a **shutdown-path race condition** caused by two specific implementation gaps in the previous version:

1.  **Redundant ARCore Sessions (The "Why"):** In the previous implementation of `ArSceneViewWrapper.kt`, `sessionManager.createSession()` was called, but the resulting `Session` object was **never assigned** to `sceneView.session`. Because `ARSceneView` defaults to creating its own internal session if none is provided, the app was running two concurrent ARCore sessions.
2.  **Disconnected Lifecycle (The "How"):** When the `ArSceneViewWrapper` was unmounted (e.g., switching to the "Get Oriented" overlay which covers the screen), `onDispose` was triggered. It called `sessionManager.pause()`, which correctly paused the *manager's* session. However, the *view's internal* session (and its Choreographer-driven render loop) remained active.
3.  **The Fatal Sequence:** 
    - The camera device was closed by the OS or the first session's pause.
    - `ARSceneView` fired a final frame callback via the Choreographer.
    - This callback invoked `session.update()` on the view's internal session.
    - Since the image subsystem was already stopped (camera closed), ARCore threw a non-recoverable `FatalException`.

## 3. Engineering Correction
The correction synchronizes the SceneView render loop with the Session lifecycle and enforces a single session owner.

### Specific Mechanisms:
-   **Unified Session Ownership:** Explicitly assigning `sceneView.session = session` ensures that both the manager and the view operate on the same ARCore instance.
-   **Proactive Loop Suppression:** Setting `sceneView.isPaused = true` and `sceneView.onSessionUpdated = null` inside `onDispose` and `ON_PAUSE` ensures that the render loop is logically halted **before** the session itself is paused. This prevents any "zombie" frame updates from reaching the native layer after the camera resource has been relinquished.

## 4. Modified Source Code

### [ArSceneViewWrapper.kt](file:///C:/Users/youss/Downloads/MallAR-main%2022/MallAR-main/app/src/main/java/com/example/mallar/ar/ui/ArSceneViewWrapper.kt)
```kotlin
package com.example.mallar.ar.ui

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mallar.ar.ArCoreSessionManager
import com.example.mallar.ar.render.StaticTestObject
import io.github.sceneview.ar.ARSceneView
import com.google.ar.core.TrackingState

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * ArSceneViewWrapper
 * ─────────────────────────────────────────────────────────────────────────────
 * 
 * Compose wrapper for the SceneView/Filament AR surface.
 * Handles sequential camera handoff and synchronized lifecycle shutdown.
 */
@Composable
fun ArSceneViewWrapper(
    modifier: Modifier = Modifier,
    sessionManager: ArCoreSessionManager
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val sceneView = remember {
        ARSceneView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    val testObjectPlaced = remember { mutableStateOf(false) }

    // Android Lifecycle Integration
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    sessionManager.resume()
                    sceneView.isPaused = false
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // CRITICAL: Stop the render loop BEFORE pausing the session
                    sceneView.isPaused = true
                    sessionManager.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        // Initial setup with Unified Session Acquisition
        val session = sessionManager.createSession()
        if (session != null) {
            // Fix: Explicitly share the manager's session with the view
            sceneView.session = session
            sessionManager.resume()
            sceneView.isPaused = false
        }

        sceneView.onSessionUpdated = { _, frame ->
            val camera = frame.camera
            sessionManager.updateTrackingState(
                camera.trackingState,
                camera.trackingFailureReason
            )

            // Minimal Rendering: Place a static sphere 2m ahead on first TRACKING.
            if (!testObjectPlaced.value && camera.trackingState == TrackingState.TRACKING) {
                val cameraPose = camera.pose
                val targetPose = cameraPose.compose(com.google.ar.core.Pose.makeTranslation(0f, 0f, -2f))
                
                sessionManager.getSession()?.let { arSession ->
                    val anchor = arSession.createAnchor(targetPose)
                    StaticTestObject.addTestSphere(sceneView, anchor)
                    testObjectPlaced.value = true
                }
            }
        }

        onDispose {
            // CRITICAL: Clear callback and stop loop to prevent update() during teardown
            sceneView.onSessionUpdated = null
            sceneView.isPaused = true
            
            lifecycleOwner.lifecycle.removeObserver(observer)
            sessionManager.pause()
        }
    }

    AndroidView(
        factory = { sceneView },
        modifier = modifier
    )
}
```

## 5. Validation Confirmation
- **Direct Reasoning:** The previous version allowed `ARSceneView` to call `session.update()` on its own unmanaged session while the manager's session was being paused. By unifying the session and explicitly setting `isPaused = true` on the view **before** the session pause, the native Image subsystem remains in a valid state for the duration of the final frame callbacks.
- **Scope Compliance:** No Phase 5+ logic (anchors, pathfinding, or advanced rendering) was introduced. The fix is strictly confined to Module 2/7 lifecycle synchronization.

---
**Next Step:** Apply the correction to `ArSceneViewWrapper.kt` and resume Human Device Validation.
