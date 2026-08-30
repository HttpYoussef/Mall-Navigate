package com.example.mallar.ar.render

import com.google.ar.core.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs

class RenderPoseSmootherTest {

    @Test
    fun stationaryPoseVarianceReduction_exceedsFiftyPercent() {
        val smoother = RenderPoseSmoother()
        val random = Random(42)
        val frameCount = 120
        val dtMs = 16L // ~60 FPS

        val groundTruthX = 2.0f
        val groundTruthY = 1.5f
        val groundTruthZ = -3.0f
        val noiseSigma = 0.005f // 5 mm noise

        val rawPositions = mutableListOf<FloatArray>()
        val filteredPositions = mutableListOf<FloatArray>()

        var currentTimeMs = 1000L
        for (i in 0 until frameCount) {
            val noiseX = (random.nextGaussian() * noiseSigma).toFloat()
            val noiseY = (random.nextGaussian() * noiseSigma).toFloat()
            val noiseZ = (random.nextGaussian() * noiseSigma).toFloat()

            val rawPose = Pose.makeTranslation(
                groundTruthX + noiseX,
                groundTruthY + noiseY,
                groundTruthZ + noiseZ
            )
            rawPositions.add(floatArrayOf(rawPose.tx(), rawPose.ty(), rawPose.tz()))

            val filteredPose = smoother.filter(rawPose, currentTimeMs)
            filteredPositions.add(floatArrayOf(filteredPose.tx(), filteredPose.ty(), filteredPose.tz()))
            currentTimeMs += dtMs
        }

        // Compute variance of X, Y, Z for both (ignoring first 10 warmup frames)
        val warmup = 10
        val rawVarX = computeVariance(rawPositions.subList(warmup, frameCount).map { it[0] })
        val rawVarY = computeVariance(rawPositions.subList(warmup, frameCount).map { it[1] })
        val rawVarZ = computeVariance(rawPositions.subList(warmup, frameCount).map { it[2] })
        val totalRawVar = rawVarX + rawVarY + rawVarZ

        val filteredVarX = computeVariance(filteredPositions.subList(warmup, frameCount).map { it[0] })
        val filteredVarY = computeVariance(filteredPositions.subList(warmup, frameCount).map { it[1] })
        val filteredVarZ = computeVariance(filteredPositions.subList(warmup, frameCount).map { it[2] })
        val totalFilteredVar = filteredVarX + filteredVarY + filteredVarZ

        val varianceRatio = totalFilteredVar / totalRawVar
        println("RenderPoseSmoother Stationary Variance Ratio: $varianceRatio (Raw: $totalRawVar, Filtered: $totalFilteredVar)")

        // Must achieve at least 50% variance reduction (ratio <= 0.50)
        assertTrue(
            "Expected variance ratio <= 0.50, but got $varianceRatio",
            varianceRatio <= 0.50
        )
    }

    @Test
    fun dynamicMotionTracking_tracksWithinLatencyAndErrorBounds() {
        val smoother = RenderPoseSmoother()
        val frameCount = 60
        val dtMs = 16L // 16 ms per frame
        val velocity = 1.0f // 1.0 m/s walking speed

        var currentTimeMs = 1000L
        var maxSteadyStateError = 0.0f

        for (i in 0 until frameCount) {
            val tSec = (i * dtMs) / 1000.0f
            val trueX = velocity * tSec
            val rawPose = Pose.makeTranslation(trueX, 0f, 0f)

            val filteredPose = smoother.filter(rawPose, currentTimeMs)

            if (i > 15) { // Steady state
                val error = abs(filteredPose.tx() - trueX)
                if (error > maxSteadyStateError) {
                    maxSteadyStateError = error
                }
            }
            currentTimeMs += dtMs
        }

        println("RenderPoseSmoother Max Steady State Dynamic Tracking Error: $maxSteadyStateError m")
        // Tracking error must remain <= 2 cm (0.02 m) during 1 m/s motion
        assertTrue(
            "Dynamic tracking error must be <= 0.02m, but was $maxSteadyStateError m",
            maxSteadyStateError <= 0.02f
        )
    }

    @Test
    fun resetClearsFilterState() {
        val smoother = RenderPoseSmoother()
        val pose1 = Pose.makeTranslation(1f, 2f, 3f)
        smoother.filter(pose1, 1000L)

        smoother.reset()
        val pose2 = Pose.makeTranslation(10f, 20f, 30f)
        val result = smoother.filter(pose2, 500L) // earlier timestamp allowed after reset

        assertEquals(10f, result.tx(), 0.001f)
        assertEquals(20f, result.ty(), 0.001f)
        assertEquals(30f, result.tz(), 0.001f)
    }

    private fun computeVariance(values: List<Float>): Double {
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }
}
