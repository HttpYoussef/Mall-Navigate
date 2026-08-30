package com.example.mallar.ar

import android.util.Log
import com.example.mallar.ar.model.PdrDisplacement
import com.example.mallar.ar.model.SensorStalenessStatus
import com.example.mallar.navigation.NavConfig
import com.example.mallar.navigation.NavSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.cos
import kotlin.math.sin

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Module 3 — Sensor Fusion / PDR Layer
 * ─────────────────────────────────────────────────────────────────────────────
 * 
 * Provides a corroborating motion estimate and staleness flag from existing
 * application data (PDR), without registering new hardware listeners.
 */
class SensorFusionLayer(
    scope: CoroutineScope,
    private val sessionState: StateFlow<NavSessionState>,
    private val currentTimeProvider: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val TAG = "SensorFusionLayer"
        private const val STALENESS_THRESHOLD_MS = NavConfig.STEP_DEBOUNCE_MS * 5
    }

    private var accumulatedDx = 0.0
    private var accumulatedDy = 0.0
    private var totalDxSinceStart = 0.0
    private var totalDySinceStart = 0.0
    private val accumulatorLock = Any()

    private val lastStepCount = AtomicLong(0)
    private val lastHeading = AtomicReference<Float?>(null)
    private val lastHeadingChangeTime = AtomicLong(currentTimeProvider())
    
    private val stalenessStatus = AtomicReference(SensorStalenessStatus(false, "Heading"))

    init {
        scope.launch {
            sessionState.collect { state ->
                processUpdate(state)
            }
        }
    }

    private fun processUpdate(state: NavSessionState) {
        val currentSteps = state.totalSteps
        val currentHeading = state.headingDeg
        val prevSteps = lastStepCount.getAndSet(currentSteps)
        val prevHeading = lastHeading.getAndSet(currentHeading)

        // 1. PDR Accumulation (on step increment)
        if (currentSteps > prevSteps) {
            val stepDelta = currentSteps - prevSteps
            val headingRad = Math.toRadians(currentHeading.toDouble())
            
            val dx = stepDelta * NavConfig.DEFAULT_STRIDE_LENGTH_M * sin(headingRad)
            val dy = -stepDelta * NavConfig.DEFAULT_STRIDE_LENGTH_M * cos(headingRad)

            synchronized(accumulatorLock) {
                accumulatedDx += dx
                accumulatedDy += dy
                totalDxSinceStart += dx
                totalDySinceStart += dy
            }
            Log.d(TAG, "Step detected: +$stepDelta steps. Delta(dx=${"%.2f".format(dx)}, dy=${"%.2f".format(dy)})")
        }

        // 2. Staleness Heuristic
        val now = currentTimeProvider()
        if (currentHeading != prevHeading) {
            lastHeadingChangeTime.set(now)
            if (stalenessStatus.get().isStale) {
                stalenessStatus.set(SensorStalenessStatus(false, "Heading"))
                Log.d(TAG, "Sensor recovered: Heading is varying again.")
            }
        } else if (currentSteps > prevSteps) {
            // Steps are occurring but heading is bitwise identical
            val timeSinceChange = now - lastHeadingChangeTime.get()
            if (timeSinceChange > STALENESS_THRESHOLD_MS && !stalenessStatus.get().isStale) {
                stalenessStatus.set(SensorStalenessStatus(true, "Heading"))
                Log.w(TAG, "Sensor Staleness Detected: Heading unchanged for ${timeSinceChange}ms during motion.")
            }
        }
    }

    /**
     * Returns the accumulated PDR displacement since the last call and resets it.
     */
    fun getCorroborationSignal(): PdrDisplacement {
        return synchronized(accumulatorLock) {
            val res = PdrDisplacement(accumulatedDx, accumulatedDy)
            accumulatedDx = 0.0
            accumulatedDy = 0.0
            res
        }
    }

    /**
     * Returns the total PDR displacement since the session started.
     */
    fun getFallbackDisplacement(): PdrDisplacement {
        return synchronized(accumulatorLock) {
            PdrDisplacement(totalDxSinceStart, totalDySinceStart)
        }
    }

    /**
     * Returns the current health status of the sensors.
     */
    fun getStalenessStatus(): SensorStalenessStatus = stalenessStatus.get()
}
