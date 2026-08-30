package com.example.mallar.ar.ui

import android.content.Context
import android.util.Log
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.ARSession
import com.google.ar.core.Session

/**
 * SceneView 2.2.1 creates its AR session internally and exposes it read-only.
 * This adapter is the narrow integration seam that lets the application keep
 * ownership of the one ARSession while retaining SceneView's renderer.
 */
internal class ManagedARSceneView(context: Context) : ARSceneView(context) {
    var isPaused: Boolean = true
    var onRenderSurfaceActiveChanged: ((Boolean) -> Unit)? = null

    companion object {
        private const val TAG = "ManagedARSceneView"
    }

    fun setManagedSession(session: Session) {
        require(session is ARSession) {
            "ManagedARSceneView requires SceneView's ARSession subtype"
        }

        try {
            val arCoreMethod = javaClass.superclass?.declaredMethods?.firstOrNull { 
                it.name == "getArCore" || it.name.startsWith("getArCore$")
            } ?: javaClass.superclass?.getMethod("getArCore")
            
            val arCore = arCoreMethod?.let {
                it.isAccessible = true
                it.invoke(this)
            }
            
            if (arCore != null) {
                val sessionField = arCore.javaClass.getDeclaredField("session")
                sessionField.isAccessible = true
                sessionField.set(arCore, session)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bridge session injection fallback: ${e.message}")
        }

        try {
            onSessionCreated(session)
        } catch (e: Exception) {
            Log.w(TAG, "onSessionCreated notice: ${e.message}")
        }
    }

    fun clearManagedSession() {
        try {
            val arCoreMethod = javaClass.superclass?.declaredMethods?.firstOrNull { 
                it.name == "getArCore" || it.name.startsWith("getArCore$")
            } ?: javaClass.superclass?.getMethod("getArCore")
            
            val arCore = arCoreMethod?.let {
                it.isAccessible = true
                it.invoke(this)
            }
            if (arCore != null) {
                val sessionField = arCore.javaClass.getDeclaredField("session")
                sessionField.isAccessible = true
                sessionField.set(arCore, null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "clearManagedSession notice: ${e.message}")
        }
    }

    override fun onFrame(frameTimeNanos: Long) {
        if (!isPaused) {
            try {
                super.onFrame(frameTimeNanos)
            } catch (e: Exception) {
                Log.w(TAG, "onFrame transient error: ${e.message}")
            }
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility != VISIBLE) {
            // Surface teardown can precede an Android lifecycle callback. Gate
            // the Choreographer first so ARSceneView cannot call Session.update
            // while ARCore's camera image subsystem is stopping.
            isPaused = true
            onRenderSurfaceActiveChanged?.invoke(false)
        }
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            onRenderSurfaceActiveChanged?.invoke(true)
        }
    }

    override fun onDetachedFromWindow() {
        isPaused = true
        onRenderSurfaceActiveChanged?.invoke(false)
        super.onDetachedFromWindow()
    }
}
