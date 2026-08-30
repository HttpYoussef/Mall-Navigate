package com.example.mallar.ar.render

import com.google.ar.core.Pose
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Module 7 — Render-Level Pose-Noise Smoothing Filter
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Implements an adaptive One-Euro filter for 6-DOF tracking poses.
 * Attenuates high-frequency micro-jitter and hand tremors when stationary,
 * while dynamically increasing cutoff frequency during fast motion to eliminate latency.
 *
 * Operating strictly on render-level poses; does NOT alter Module 6 anchor coordinates.
 */
class RenderPoseSmoother(
    private val minCutoffHz: Double = 1.0,
    private val beta: Double = 10.0,
    private val dCutoffHz: Double = 1.0
) {
    private var lastTimestampMs: Long = Long.MIN_VALUE
    private var xFilter: LowPassFilter? = null
    private var yFilter: LowPassFilter? = null
    private var zFilter: LowPassFilter? = null
    private var dxFilter: LowPassFilter? = null
    private var dyFilter: LowPassFilter? = null
    private var dzFilter: LowPassFilter? = null

    // For rotation smoothing (quaternion)
    private var qxFilter: LowPassFilter? = null
    private var qyFilter: LowPassFilter? = null
    private var qzFilter: LowPassFilter? = null
    private var qwFilter: LowPassFilter? = null

    fun filter(pose: Pose, timestampMs: Long): Pose {
        if (lastTimestampMs == Long.MIN_VALUE || timestampMs <= lastTimestampMs) {
            lastTimestampMs = timestampMs
            initFilters(pose)
            return pose
        }

        val dt = (timestampMs - lastTimestampMs) / 1000.0
        lastTimestampMs = timestampMs

        // Prevent division by zero or invalid dt
        if (dt <= 0.0 || dt > 1.0) {
            initFilters(pose)
            return pose
        }

        // Position filtering with adaptive velocity cutoff
        val rawX = pose.tx().toDouble()
        val rawY = pose.ty().toDouble()
        val rawZ = pose.tz().toDouble()

        val prevX = xFilter?.lastValue ?: rawX
        val prevY = yFilter?.lastValue ?: rawY
        val prevZ = zFilter?.lastValue ?: rawZ

        val dX = (rawX - prevX) / dt
        val dY = (rawY - prevY) / dt
        val dZ = (rawZ - prevZ) / dt

        val edX = dxFilter!!.filter(dX, alpha(dCutoffHz, dt))
        val edY = dyFilter!!.filter(dY, alpha(dCutoffHz, dt))
        val edZ = dzFilter!!.filter(dZ, alpha(dCutoffHz, dt))

        val speed = sqrt(edX * edX + edY * edY + edZ * edZ)
        val cutoff = minCutoffHz + beta * speed
        val a = alpha(cutoff, dt)

        val filteredX = xFilter!!.filter(rawX, a).toFloat()
        val filteredY = yFilter!!.filter(rawY, a).toFloat()
        val filteredZ = zFilter!!.filter(rawZ, a).toFloat()

        // Rotation filtering
        val rotAlpha = alpha(minCutoffHz + beta * speed, dt)
        val qx = pose.qx().toDouble()
        val qy = pose.qy().toDouble()
        val qz = pose.qz().toDouble()
        val qw = pose.qw().toDouble()

        var fQx = qxFilter!!.filter(qx, rotAlpha)
        var fQy = qyFilter!!.filter(qy, rotAlpha)
        var fQz = qzFilter!!.filter(qz, rotAlpha)
        var fQw = qwFilter!!.filter(qw, rotAlpha)

        // Normalize quaternion
        val qMag = sqrt(fQx * fQx + fQy * fQy + fQz * fQz + fQw * fQw)
        if (qMag > 1e-6) {
            fQx /= qMag
            fQy /= qMag
            fQz /= qMag
            fQw /= qMag
        } else {
            fQx = qx; fQy = qy; fQz = qz; fQw = qw
        }

        return Pose(
            floatArrayOf(filteredX, filteredY, filteredZ),
            floatArrayOf(fQx.toFloat(), fQy.toFloat(), fQz.toFloat(), fQw.toFloat())
        )
    }

    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }

    private fun initFilters(pose: Pose) {
        xFilter = LowPassFilter(pose.tx().toDouble())
        yFilter = LowPassFilter(pose.ty().toDouble())
        zFilter = LowPassFilter(pose.tz().toDouble())
        dxFilter = LowPassFilter(0.0)
        dyFilter = LowPassFilter(0.0)
        dzFilter = LowPassFilter(0.0)

        qxFilter = LowPassFilter(pose.qx().toDouble())
        qyFilter = LowPassFilter(pose.qy().toDouble())
        qzFilter = LowPassFilter(pose.qz().toDouble())
        qwFilter = LowPassFilter(pose.qw().toDouble())
    }

    fun reset() {
        lastTimestampMs = Long.MIN_VALUE
        xFilter = null
        yFilter = null
        zFilter = null
        dxFilter = null
        dyFilter = null
        dzFilter = null
        qxFilter = null
        qyFilter = null
        qzFilter = null
        qwFilter = null
    }

    private class LowPassFilter(var lastValue: Double) {
        fun filter(value: Double, alpha: Double): Double {
            val result = alpha * value + (1.0 - alpha) * lastValue
            lastValue = result
            return result
        }
    }
}
