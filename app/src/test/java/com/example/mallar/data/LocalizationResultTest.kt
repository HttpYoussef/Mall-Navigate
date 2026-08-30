package com.example.mallar.data

import org.junit.Test
import org.junit.Assert.*

class LocalizationResultTest {

    /**
     * MAJOR FINDING #4 RESOLUTION:
     * This test ensures that the new 'landmarkCount' field is correctly stored in the data model
     * and DOES NOT interfere with the existing HIGH/MEDIUM/LOW confidence tier mapping.
     * The provisional/confirmed distinction based on landmarkCount is deferred to Phase 5.
     */
    @Test
    fun localizationTier_mappingStability_independentOfLandmarkCount() {
        // High confidence scenario: landmarkCount is irrelevant to the current mapping
        val highConf = LocalizationResult(
            detections = emptyList(),
            estimatedMapX = null,
            estimatedMapY = null,
            estimatedHeadingDeg = null,
            confidence = 0.8f, // > 0.75f
            confidenceReason = "High Confidence Test",
            bestStartNode = null,
            landmarkCount = 1 // Single landmark (would be 'provisional' in Phase 5, but 'HIGH' in Phase 1)
        )
        assertEquals("HIGH tier mapping should depend ONLY on confidence in Phase 1", 
            LocalizationTier.HIGH, highConf.tier)

        // Low confidence scenario with multiple landmarks
        val lowConf = LocalizationResult(
            detections = emptyList(),
            estimatedMapX = null,
            estimatedMapY = null,
            estimatedHeadingDeg = null,
            confidence = 0.3f, // < 0.45f
            confidenceReason = "Low Confidence Test",
            bestStartNode = null,
            landmarkCount = 5 // Multiple landmarks (would be 'confirmed' in Phase 5, but 'LOW' in Phase 1)
        )
        assertEquals("LOW tier mapping should depend ONLY on confidence in Phase 1", 
            LocalizationTier.LOW, lowConf.tier)
    }

    @Test
    fun localizationResult_holdsLandmarkCount() {
        val result = LocalizationResult(
            detections = emptyList(),
            estimatedMapX = 100.0,
            estimatedMapY = 200.0,
            estimatedHeadingDeg = 45f,
            confidence = 0.8f,
            confidenceReason = "Storage Test",
            bestStartNode = null,
            landmarkCount = 3
        )
        assertEquals("landmarkCount must be accessible from the data model", 3, result.landmarkCount)
    }
}
