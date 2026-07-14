package com.example.mallar.navigation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.*

private const val TAG = "PoseDetectionManager"

// ─────────────────────────────────────────────────────────────────────────────
// Device pose
// ─────────────────────────────────────────────────────────────────────────────
enum class DevicePose { FLAT, UPRIGHT, UNKNOWN }

// ─────────────────────────────────────────────────────────────────────────────
/**
 * PoseDetectionManager
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Detects whether the phone is held upright (AR / camera mode) or
 * flat / face-down (map mode) using the TYPE_GRAVITY sensor.
 *
 * MATH — TILT ANGLE
 * -----------------
 * tiltDeg = acos(gz / |g|) × 180/π
 *
 *   Phone flat face-up  : gz ≈ +9.8  → tilt ≈  0°
 *   Phone upright       : gz ≈  0    → tilt ≈ 90°
 *   Phone flat face-down: gz ≈ −9.8  → tilt ≈ 180°
 *
 * Thresholds (widened to reduce accidental flicker while walking):
 *   tilt > 60°  → UPRIGHT (phone raised to look ahead → AR mode)
 *   tilt < 30°  → FLAT    (phone held face-up flat → map mode)
 *   tilt > 150° → FLAT    (phone face-down — also map mode)
 *   30°–60°     → hysteresis zone (keep current pose)
 *
 * HYSTERESIS + DEBOUNCE
 * ---------------------
 * Dual-threshold hysteresis + 800 ms debounce prevents rapid mode flicker
 * while walking or transitioning between poses.
 */
class PoseDetectionManager(context: Context) : SensorEventListener {

    // ── Android sensor ────────────────────────────────────────────────────────
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gravitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // ── State ─────────────────────────────────────────────────────────────────
    private var currentPose: DevicePose = DevicePose.UNKNOWN
    private var pendingPose: DevicePose = DevicePose.UNKNOWN
    private var pendingSince: Long      = 0L

    // ── Low-pass filter for gravity (α = 0.12) ────────────────────────────────
    private var lpGx = 0f; private var lpGy = 0f; private var lpGz = 9.8f
    private val LP_ALPHA = 0.12f

    // ── Callback ──────────────────────────────────────────────────────────────
    /**
     * Called on the sensor thread whenever the stable pose changes.
     * Switch your NavMode here.
     */
    var onPoseChanged: ((DevicePose) -> Unit)? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun start() {
        val sensor = gravitySensor
        if (sensor == null) {
            Log.e(TAG, "No gravity/accelerometer sensor available")
            return
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        Log.d(TAG, "Started — sensor=${sensor.name}")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Stopped")
    }

    // ── SensorEventListener ───────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        // ── Low-pass filter ────────────────────────────────────────────────────
        lpGx = lpGx + LP_ALPHA * (event.values[0] - lpGx)
        lpGy = lpGy + LP_ALPHA * (event.values[1] - lpGy)
        lpGz = lpGz + LP_ALPHA * (event.values[2] - lpGz)

        // ── Tilt angle calculation ─────────────────────────────────────────────
        // tiltDeg: 0° = flat face-up, 90° = fully upright, 180° = face-down
        val gMag = sqrt(lpGx * lpGx + lpGy * lpGy + lpGz * lpGz)
        if (gMag < 1f) return // degenerate reading

        val cosAngle = (lpGz / gMag).coerceIn(-1f, 1f)
        val tiltDeg  = Math.toDegrees(acos(cosAngle.toDouble())).toFloat()

        // ── Classify pose with hysteresis ──────────────────────────────────────
        val newPose: DevicePose = when {
            // Upright (AR mode): phone raised, tilt near 90°
            tiltDeg > UPRIGHT_ENTER_DEG -> DevicePose.UPRIGHT
            // Flat face-down: gz strongly negative → map mode too
            tiltDeg > FACE_DOWN_DEG     -> DevicePose.FLAT
            // Flat face-up: map mode
            tiltDeg < FLAT_ENTER_DEG    -> DevicePose.FLAT
            // Hysteresis: stay in current pose until threshold crossed
            currentPose == DevicePose.UPRIGHT && tiltDeg > UPRIGHT_EXIT_DEG -> DevicePose.UPRIGHT
            currentPose == DevicePose.FLAT    && tiltDeg < FLAT_EXIT_DEG    -> DevicePose.FLAT
            // Default: use midpoint
            else -> if (tiltDeg > 45f) DevicePose.UPRIGHT else DevicePose.FLAT
        }

        // ── Debounce ───────────────────────────────────────────────────────────
        val now = System.currentTimeMillis()
        if (newPose != pendingPose) {
            pendingPose  = newPose
            pendingSince = now
        } else if (newPose != currentPose && (now - pendingSince) >= DEBOUNCE_MS) {
            currentPose = newPose
            Log.d(TAG, "Pose → $newPose  tilt=${tiltDeg.toInt()}°  gz=${lpGz.toInt()}")
            onPoseChanged?.invoke(newPose)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* ignore */ }

    companion object {
        /** Tilt above which phone is classified as UPRIGHT (AR/camera mode). */
        private const val UPRIGHT_ENTER_DEG = 60f
        /** Tilt below which UPRIGHT reverts to hysteresis zone. */
        private const val UPRIGHT_EXIT_DEG  = 45f
        /** Tilt below which phone is classified as FLAT (map mode, face-up). */
        private const val FLAT_ENTER_DEG    = 30f
        /** Tilt above which FLAT reverts to hysteresis zone. */
        private const val FLAT_EXIT_DEG     = 45f
        /** Tilt above which phone is face-down (also map mode). */
        private const val FACE_DOWN_DEG     = 150f
        /** Milliseconds the new pose must be stable before firing the callback. */
        private const val DEBOUNCE_MS       = 800L
    }
}
