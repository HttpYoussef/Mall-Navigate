package com.example.mallar.navigation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs

private const val TAG = "SensorFusionManager"

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * SensorFusionManager
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * PURPOSE
 * -------
 * Replace the unstable accelerometer + magnetometer compass with Android's
 * built-in sensor fusion: TYPE_ROTATION_VECTOR.
 *
 * TYPE_ROTATION_VECTOR is computed on-device by the OS from accelerometer,
 * magnetometer, AND gyroscope data using a Kalman filter that is aware of
 * both short-term (gyro) and long-term (mag/accel) drift. This gives a
 * heading that is dramatically more stable indoors than reading from the
 * magnetometer alone.
 *
 * MATH
 * ----
 * 1. The sensor provides a rotation vector [x, y, z, w] (quaternion-like).
 * 2. SensorManager.getRotationMatrixFromVector() converts it to a 3×3
 *    rotation matrix R that maps device space → world space.
 * 3. SensorManager.getOrientation(R, angles) extracts:
 *       angles[0] = azimuth  (rotation around world Z-down, i.e. compass heading)
 *       angles[1] = pitch
 *       angles[2] = roll
 *    in radians, with azimuth in (−π, +π].
 * 4. We convert azimuth to degrees and normalise to [0, 360).
 * 5. A two-stage pipeline then smooths the reading:
 *    a) Low-pass filter (exponential moving average, α = 0.15) → removes
 *       single-sample spikes caused by magnetic interference indoors.
 *    b) Scalar Kalman filter → further smooths slow drift while still
 *       allowing genuine heading changes to converge.
 *
 * LOW-PASS FILTER
 * ---------------
 *   filtered = filtered + α * shortestDelta(filtered, raw)
 *
 * α = 0.15 means 15 % of the error is corrected each sample.
 * At 50 Hz this gives ≈ 10-sample (200 ms) time constant — fast enough for
 * walking, slow enough to kill magnetic noise spikes.
 *
 * KALMAN FILTER (scalar, 1-D)
 * ---------------------------
 *   Predict:  P'  = P + Q        (Q = process noise, how fast heading truly changes)
 *   Update:   K   = P' / (P' + R) (Kalman gain)
 *             x'  = x + K * (measurement − x)   [with angle-wrap correction]
 *             P   = (1 − K) * P'
 *
 * Q = 0.005 (heading changes slowly while walking)
 * R = 5.0   (compass indoors has ~5° measurement noise)
 *
 * SENSOR RATE
 * -----------
 * SENSOR_DELAY_GAME (~50 Hz) balances responsiveness and battery.
 * Using SENSOR_DELAY_FASTEST would give 200 Hz but wastes CPU.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
class SensorFusionManager(private val context: Context) : SensorEventListener {

    // ── Android sensor plumbing ───────────────────────────────────────────────
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /** Primary: TYPE_ROTATION_VECTOR (fused gyro + accel + mag). */
    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    /** Fallback: TYPE_GAME_ROTATION_VECTOR (no magnetometer — no absolute north). */
    private val gameRotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    // Pre-allocated scratch arrays (no GC pressure in hot path)
    private val rotationMatrix    = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // ── Filters ───────────────────────────────────────────────────────────────
    /** Low-pass filter kills single-sample magnetic spikes. */
    private val lowPassFilter  = HeadingLowPassFilter(alpha = 0.15f)
    /** Kalman filter smooths slow drift and guarantees convergence. */
    private val kalmanFilter   = HeadingKalmanFilter(processNoise = 0.005f, measurementNoise = 5f)

    // ── Public outputs ────────────────────────────────────────────────────────
    /** Latest stable azimuth in degrees, [0, 360). Thread-safe read via @Volatile. */
    @Volatile var azimuthDeg: Float = 0f
        private set

    /** Sensor accuracy from onAccuracyChanged (0 = unreliable … 3 = high). */
    @Volatile var accuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE
        private set

    /** True if TYPE_ROTATION_VECTOR is being used (preferred). */
    val usingFullFusion: Boolean get() = rotationVectorSensor != null

    // ── Callback ──────────────────────────────────────────────────────────────
    /** Notified on every filtered heading update (called on sensor thread). */
    var onHeadingChanged: ((azimuthDeg: Float, accuracy: Int) -> Unit)? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    fun start() {
        val sensor = rotationVectorSensor ?: gameRotationSensor
        if (sensor == null) {
            Log.e(TAG, "No rotation vector sensor available on this device!")
            return
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        Log.d(TAG, "Started — sensor=${sensor.name} fullFusion=$usingFullFusion")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        lowPassFilter.reset()
        kalmanFilter.reset()
        Log.d(TAG, "Stopped")
    }

    // ── SensorEventListener ───────────────────────────────────────────────────
    override fun onSensorChanged(event: SensorEvent) {
        val sensorType = event.sensor.type
        if (sensorType != Sensor.TYPE_ROTATION_VECTOR &&
            sensorType != Sensor.TYPE_GAME_ROTATION_VECTOR) return

        // Step 1: quaternion → rotation matrix → orientation angles
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // Step 2: azimuth (orientation[0]) in radians → degrees, normalised to [0,360)
        val rawDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        val normalised = (rawDeg + 360f) % 360f

        // Step 3: low-pass → kill spikes
        val lpFiltered = lowPassFilter.filter(normalised)

        // Step 4: Kalman → smooth drift
        val stable = kalmanFilter.filter(lpFiltered)

        azimuthDeg = stable
        onHeadingChanged?.invoke(stable, accuracy)
    }

    override fun onAccuracyChanged(sensor: Sensor?, newAccuracy: Int) {
        accuracy = newAccuracy
        if (newAccuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
            Log.w(TAG, "Sensor accuracy low ($newAccuracy). Consider calibration.")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner filter classes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Exponential moving average for angles with wraparound handling.
     *
     * Formula:  filtered = filtered + α * shortestDelta(filtered, raw)
     *
     * [alpha] = fraction of error corrected per sample.
     * Smaller α → heavier smoothing (slower response).
     * Larger  α → lighter smoothing (faster response).
     */
    class HeadingLowPassFilter(private val alpha: Float) {
        private var filtered: Float? = null

        fun filter(newDeg: Float): Float {
            val prev = filtered ?: run {
                filtered = newDeg
                return newDeg
            }
            // Shortest angular delta in (−180, +180]
            var delta = newDeg - prev
            while (delta > 180f)  delta -= 360f
            while (delta < -180f) delta += 360f

            val updated = ((prev + alpha * delta) + 360f) % 360f
            filtered = updated
            return updated
        }

        fun reset() { filtered = null }
    }

    /**
     * Scalar (1-D) Kalman filter for heading.
     *
     * State:   x  = heading estimate (degrees)
     * Process: Q  = process noise — how fast true heading can change per sample
     * Measure: R  = measurement noise — compass accuracy indoors (~5°²)
     *
     * Predict step:
     *   P' = P + Q
     *
     * Update step:
     *   K  = P' / (P' + R)            (Kalman gain, 0 = ignore measurement, 1 = trust it)
     *   x' = x + K * Δmeasurement    (Δ uses shortest angular path)
     *   P  = (1 − K) * P'
     */
    class HeadingKalmanFilter(
        private val processNoise: Float,     // Q
        private val measurementNoise: Float  // R
    ) {
        private var estimate: Float? = null
        private var errorCovariance: Float = 1f   // P

        fun filter(measured: Float): Float {
            val est = estimate ?: run {
                estimate = measured
                return measured
            }

            // Predict
            val predictedError = errorCovariance + processNoise

            // Kalman gain
            val gain = predictedError / (predictedError + measurementNoise)

            // Innovation (shortest angular path)
            var delta = measured - est
            while (delta > 180f)  delta -= 360f
            while (delta < -180f) delta += 360f

            // Update
            val newEst = ((est + gain * delta) + 360f) % 360f
            errorCovariance = (1f - gain) * predictedError

            estimate = newEst
            return newEst
        }

        fun reset() {
            estimate = null
            errorCovariance = 1f
        }
    }
}
