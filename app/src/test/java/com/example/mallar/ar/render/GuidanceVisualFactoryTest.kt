package com.example.mallar.ar.render

import org.junit.Assert.assertEquals
import org.junit.Test

class GuidanceVisualFactoryTest {

    @Test
    fun computeWorldHeadingDeg_computesExactWorldSpaceTangents() {
        // Heading towards +Z (South): deltaX = 0, deltaZ = 5 -> 0 degrees
        val headingSouth = GuidanceVisualFactory.computeWorldHeadingDeg(0f, 0f, 0f, 5f)
        assertEquals(0f, headingSouth, 0.001f)

        // Heading towards +X (East): deltaX = 5, deltaZ = 0 -> 90 degrees
        val headingEast = GuidanceVisualFactory.computeWorldHeadingDeg(0f, 0f, 5f, 0f)
        assertEquals(90f, headingEast, 0.001f)

        // Heading towards -Z (North): deltaX = 0, deltaZ = -5 -> 180 degrees
        val headingNorth = GuidanceVisualFactory.computeWorldHeadingDeg(0f, 0f, 0f, -5f)
        assertEquals(180f, Math.abs(headingNorth), 0.001f)

        // Heading towards -X (West): deltaX = -5, deltaZ = 0 -> -90 degrees
        val headingWest = GuidanceVisualFactory.computeWorldHeadingDeg(0f, 0f, -5f, 0f)
        assertEquals(-90f, headingWest, 0.001f)

        // Diagonal Southeast: deltaX = 5, deltaZ = 5 -> 45 degrees
        val headingSE = GuidanceVisualFactory.computeWorldHeadingDeg(0f, 0f, 5f, 5f)
        assertEquals(45f, headingSE, 0.001f)
    }

    @Test
    fun computeWorldHeadingDeg_zeroDeltaReturnsZero() {
        val heading = GuidanceVisualFactory.computeWorldHeadingDeg(2f, 3f, 2f, 3f)
        assertEquals(0f, heading, 0.001f)
    }
}
