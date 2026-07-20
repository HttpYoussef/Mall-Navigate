package com.example.mallar.navigation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import kotlin.math.sqrt

private const val TAG = "StepTracker"

/**
 * Provides footstep detection and dynamic stride estimation.
 */
class StepTracker(private val context: Context) : SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val stepDetectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val stepCounterSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val accelerometerSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // ── State ────────────────────────────────────────────────────────────────
    private var stepCounterBaseline: Long = -1L
    private var hardwareStepCount:   Long = 0L
    private var softwareStepCount:  Long  = 0L
    
    private var filteredMagnitude:  Float = 9.8f
    private var lastStepTimeMs:     Long  = 0L
    private var isAboveThreshold:   Boolean = false

    // ── Cadence & Stride estimation ──────────────────────────────────────────
    private val stepTimestamps = mutableListOf<Long>()
    private val WINDOW_SIZE = 8
    private val STRIDE_EMA_ALPHA = 0.2f
    
    // Track the time of the last sensor event to distribute batched hardware steps
    private var lastEventTimeMs: Long = SystemClock.elapsedRealtime()
    
    var currentStrideLengthM: Float = NavConfig.DEFAULT_STRIDE_LENGTH_M
        private set

    val sessionSteps: Long
        get() = if (usingHardwareCounter) hardwareStepCount else softwareStepCount

    var sessionDistanceMetres: Float = 0f
        private set

    val usingHardwareCounter: Boolean get() = stepDetectorSensor != null || stepCounterSensor != null

    var onStep: ((Long, Float, Float) -> Unit)? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    fun start() {
        reset()
        val delay = SensorManager.SENSOR_DELAY_GAME
        lastEventTimeMs = SystemClock.elapsedRealtime()
        when {
            stepDetectorSensor != null -> sensorManager.registerListener(this, stepDetectorSensor, delay)
            stepCounterSensor != null -> sensorManager.registerListener(this, stepCounterSensor, delay)
            accelerometerSensor != null -> sensorManager.registerListener(this, accelerometerSensor, delay)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun reset() {
        stepCounterBaseline = -1L
        hardwareStepCount   = 0L
        softwareStepCount   = 0L
        sessionDistanceMetres = 0f
        filteredMagnitude   = 9.8f
        lastStepTimeMs      = 0L
        stepTimestamps.clear()
        currentStrideLengthM = NavConfig.DEFAULT_STRIDE_LENGTH_M
    }

    // ── Processing ────────────────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> if (event.values[0] == 1.0f) registerStep(SystemClock.elapsedRealtime())
            Sensor.TYPE_STEP_COUNTER  -> handleHardwareCounter(event.values[0].toLong())
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event.values)
        }
    }

    private fun handleHardwareCounter(total: Long) {
        if (stepCounterBaseline < 0L) {
            stepCounterBaseline = total
            lastEventTimeMs = SystemClock.elapsedRealtime()
            return
        }
        val currentTotalSteps = total - stepCounterBaseline
        val newStepsInBatch = (currentTotalSteps - hardwareStepCount).toInt()
        
        if (newStepsInBatch > 0) {
            val now = SystemClock.elapsedRealtime()
            val elapsedSinceLast = now - lastEventTimeMs
            
            // Distribute the timestamps for batched steps to ensure calculateCadence() 
            // receives a meaningful duration.
            val interval = elapsedSinceLast / newStepsInBatch
            
            for (i in 1..newStepsInBatch) {
                val simulatedTimestamp = lastEventTimeMs + (i * interval)
                registerStep(simulatedTimestamp)
            }
            lastEventTimeMs = now
        }
    }

    private fun handleAccelerometer(values: FloatArray) {
        val magnitude = sqrt(values[0]*values[0] + values[1]*values[1] + values[2]*values[2])
        filteredMagnitude += 0.08f * (magnitude - filteredMagnitude)

        if (filteredMagnitude > NavConfig.SOFT_STEP_THRESHOLD) {
            if (!isAboveThreshold) {
                isAboveThreshold = true
                val now = SystemClock.elapsedRealtime()
                if (now - lastStepTimeMs >= NavConfig.STEP_DEBOUNCE_MS) {
                    lastStepTimeMs = now
                    registerStep(now)
                }
            }
        } else if (filteredMagnitude < 9.5f) {
            isAboveThreshold = false
        }
    }

    private fun registerStep(timestampMs: Long) {
        // 1. Calculate Cadence (Steps Per Minute)
        stepTimestamps.add(timestampMs)
        if (stepTimestamps.size > WINDOW_SIZE) stepTimestamps.removeAt(0)
        
        val cadence = calculateCadence()
        
        // 2. Estimate Stride Length (Linear model + EMA smoothing)
        val rawStride = (0.35f + 0.004f * cadence).coerceIn(0.5f, 1.0f)
        currentStrideLengthM = currentStrideLengthM + STRIDE_EMA_ALPHA * (rawStride - currentStrideLengthM)
        
        // 3. Update State
        if (usingHardwareCounter) hardwareStepCount++ else softwareStepCount++
        sessionDistanceMetres += currentStrideLengthM
        
        onStep?.invoke(sessionSteps, currentStrideLengthM, sessionDistanceMetres)
    }

    private fun calculateCadence(): Float {
        if (stepTimestamps.size < 2) return 100f 
        
        val durationMs = stepTimestamps.last() - stepTimestamps.first()
        val steps = stepTimestamps.size - 1
        if (durationMs <= 0L) return 100f
        
        return (steps.toFloat() / durationMs) * 60000f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
