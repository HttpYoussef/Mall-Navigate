package com.example.mallar.navigation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.getAltitude
import android.util.Log
import kotlin.math.abs

private const val TAG = "BarometerManager"

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * BarometerManager
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Monitors relative altitude changes using the device's pressure sensor.
 * Used to automatically confirm floor transitions (escalators/elevators).
 *
 * HYSTERESIS:
 * We don't use absolute altitude (which drifts with weather). We track the
 * delta from the moment navigation enters a "Pending Transition" state.
 */
class BarometerManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private var baselineAltitude: Float? = null
    private var currentAltitude: Float? = null
    
    /** True if the device has a barometer. */
    val isAvailable: Boolean = pressureSensor != null

    /** 
     * Difference in meters between current altitude and the baseline set 
     * when [resetBaseline] was called.
     */
    val relativeAltitudeDelta: Float
        get() {
            val curr = currentAltitude ?: return 0f
            val base = baselineAltitude ?: return 0f
            return curr - base
        }

    fun start() {
        if (!isAvailable) return
        sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        baselineAltitude = null
    }

    /**
     * Set the current altitude as the 0m reference. 
     * Call this when the user arrives at a transition node.
     */
    fun resetBaseline() {
        baselineAltitude = currentAltitude
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_PRESSURE) {
            val hPa = event.values[0]
            // Standard pressure at sea level is 1013.25 hPa.
            // We use relative changes, so the exact baseline doesn't matter much.
            val alt = getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hPa)
            currentAltitude = alt
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
