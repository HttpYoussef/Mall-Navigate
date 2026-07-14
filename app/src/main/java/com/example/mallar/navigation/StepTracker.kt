package com.example.mallar.navigation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

private const val TAG = "StepTracker"

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * StepTracker
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * PURPOSE
 * -------
 * Replace manual accelerometer-based step detection (which accumulates large
 * drift because every peak-detection error compounds) with the hardware
 * TYPE_STEP_COUNTER sensor, falling back to software detection only when the
 * hardware sensor is not available.
 *
 * ── HARDWARE PATH: TYPE_STEP_COUNTER ────────────────────────────────────────
 * The step counter is implemented inside the SoC's DSP/coprocessor and runs
 * even when the CPU is asleep. It counts total steps since last reboot and
 * NEVER decrements (monotonically increasing). This makes it far more accurate
 * than software peak detection on the accelerometer, because:
 *   • The DSP algorithm is tuned for human gait by the chip vendor.
 *   • Dead-reckoning drift grows with √N for hardware vs N for software.
 *   • No missed peaks or double-counts from CPU scheduling delays.
 *
 * Integration pattern:
 *   1. Record the counter value at the moment we first receive a reading
 *      (baseline). This resets session step count without clearing the sensor.
 *   2. Session steps = current − baseline.
 *
 * ── SOFTWARE FALLBACK: accelerometer peak detection ──────────────────────────
 * When TYPE_STEP_COUNTER is absent (old phones, emulators), we detect steps
 * from the accelerometer magnitude.
 *
 * ALGORITHM
 * ---------
 *   1. Compute magnitude: m = √(ax² + ay² + az²)
 *   2. Apply a gentle low-pass filter to remove high-frequency vibration:
 *         filtered = filtered + α * (m − filtered)   [α ≈ 0.1]
 *   3. Detect a STEP when:
 *         − filtered > STEP_THRESHOLD (gravity removed, ~10 m/s² + swing)
 *         − AND at least STEP_DEBOUNCE_MS milliseconds since the last step
 *           (prevents double-counting a single footfall).
 *   4. Count and report the step.
 *
 * THRESHOLD
 * ---------
 * Gravity alone ≈ 9.8 m/s². During walking the vertical acceleration swings
 * ±2–4 m/s² around gravity. We use 10.5 as the peak threshold.
 * If you find too many false positives, increase to 11.0.
 * If too few steps detected, decrease to 10.2.
 *
 * STRIDE LENGTH
 * -------------
 * Average adult stride ≈ 0.75 m (one stride = two steps = one full cycle).
 * Walking pace: ~80 m/min = ~1.33 m/s ≈ 107 steps/min → 0.75 m/step.
 * This constant is used by IndoorPositionTracker to convert steps to meters.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
class StepTracker(private val context: Context) : SensorEventListener {

    // ── Android sensor access ─────────────────────────────────────────────────
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val stepDetectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val stepCounterSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val accelerometerSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // ── Hardware counter state ────────────────────────────────────────────────
    private var stepCounterBaseline: Long = -1L   // set on first hardware reading
    private var hardwareStepCount:   Long = 0L   // session steps from TYPE_STEP_COUNTER

    // ── Software fallback state ───────────────────────────────────────────────
    private var filteredMagnitude:  Float = 9.8f  // start near gravity
    private var lastStepTimeMs:     Long  = 0L
    private var softwareStepCount:  Long  = 0L
    private var isAboveThreshold:   Boolean = false

    // ── Public state ──────────────────────────────────────────────────────────
    /** True if any hardware step sensor is available. */
    val usingHardwareCounter: Boolean get() = stepDetectorSensor != null || stepCounterSensor != null

    /** Total steps in this session (hardware or software, whichever is active). */
    val sessionSteps: Long
        get() = if (usingHardwareCounter) hardwareStepCount else softwareStepCount

    /** Estimated distance walked in this session (metres). */
    val sessionDistanceMetres: Float
        get() = sessionSteps * STRIDE_LENGTH_M

    /** Callback fired on every new step (called on sensor thread). */
    var onStep: ((totalSteps: Long, distanceMetres: Float) -> Unit)? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    fun start() {
        if (stepDetectorSensor != null) {
            sensorManager.registerListener(
                this, stepDetectorSensor, SensorManager.SENSOR_DELAY_FASTEST
            )
            Log.d(TAG, "Using hardware TYPE_STEP_DETECTOR")
        } else if (stepCounterSensor != null) {
            sensorManager.registerListener(
                this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.d(TAG, "Using hardware TYPE_STEP_COUNTER")
        } else if (accelerometerSensor != null) {
            // Software fallback
            sensorManager.registerListener(
                this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME
            )
            Log.w(TAG, "TYPE_STEP_COUNTER unavailable — falling back to accelerometer")
        } else {
            Log.e(TAG, "Neither step counter nor accelerometer available!")
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /** Reset session counters (e.g. when navigation restarts). */
    fun reset() {
        stepCounterBaseline = -1L
        hardwareStepCount   = 0L
        softwareStepCount   = 0L
        filteredMagnitude   = 9.8f
        lastStepTimeMs      = 0L
    }

    // ── SensorEventListener ───────────────────────────────────────────────────
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> handleHardwareDetector(event)
            Sensor.TYPE_STEP_COUNTER  -> handleHardwareCounter(event)
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ── Hardware counter handler ──────────────────────────────────────────────
    /**
     * TYPE_STEP_COUNTER reports total steps since last reboot.
     * We record the first reading as a baseline so session steps always
     * start at zero regardless of what the device had accumulated before.
     */
    private fun handleHardwareCounter(event: SensorEvent) {
        val total = event.values[0].toLong()

        if (stepCounterBaseline < 0L) {
            // First reading: record baseline
            stepCounterBaseline = total
            Log.d(TAG, "Hardware step counter baseline = $total")
            return
        }

        hardwareStepCount = total - stepCounterBaseline
        onStep?.invoke(hardwareStepCount, sessionDistanceMetres)
    }

    private fun handleHardwareDetector(event: SensorEvent) {
        if (event.values[0] == 1.0f) {
            hardwareStepCount++
            onStep?.invoke(hardwareStepCount, sessionDistanceMetres)
        }
    }

    // ── Software accelerometer fallback ──────────────────────────────────────
    private fun handleAccelerometer(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        val magnitude = sqrt(ax * ax + ay * ay + az * az)
        filteredMagnitude += ACCEL_ALPHA * (magnitude - filteredMagnitude)

        if (filteredMagnitude > STEP_THRESHOLD) {
            if (!isAboveThreshold) {
                isAboveThreshold = true
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastStepTimeMs >= STEP_DEBOUNCE_MS) {
                    lastStepTimeMs = nowMs
                    softwareStepCount++
                    onStep?.invoke(softwareStepCount, sessionDistanceMetres)
                }
            }
        } else if (filteredMagnitude < 9.5f) {
            // Must dip below 9.5 (below gravity) to reset hysteresis
            isAboveThreshold = false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        /** Average stride length in metres (one step = half a stride cycle). */
        const val STRIDE_LENGTH_M: Float = 0.75f

        /** Peak detection threshold: gravity (9.8) + walking swing headroom.
         *  11.5 avoids false positives from phone tilting/rotating while standing. */
        private const val STEP_THRESHOLD: Float = 11.5f

        /** Minimum milliseconds between two consecutive steps (prevents double-count). */
        private const val STEP_DEBOUNCE_MS: Long = 400L

        /** Low-pass alpha for accelerometer magnitude smoothing. */
        private const val ACCEL_ALPHA: Float = 0.08f
    }
}
