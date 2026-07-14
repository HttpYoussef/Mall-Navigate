package com.example.mallar.data

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * LocalizationResult
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Immutable result produced by [com.example.mallar.ml.LocalizationEngine]
 * after multi-landmark detection + algebraic PnP pose estimation.
 */
data class LocalizationResult(

    /**
     * Detected landmarks in descending confidence order.
     * Each entry bundles the ML similarity score, the matched [GraphNode],
     * and its corresponding [Place] for logo display.
     */
    val detections: List<LandmarkDetection>,

    /**
     * Estimated user position in map-pixel coordinates.
     * Derived via reverse-projection from the detected landmarks.
     * Null when fewer than 1 confident detection is available.
     */
    val estimatedMapX: Double?,
    val estimatedMapY: Double?,

    /**
     * Estimated camera heading in degrees, clockwise from map North.
     * Computed from the image-space vs map-space bearing of the detected
     * landmark cluster. Null when only 1 detection is available.
     */
    val estimatedHeadingDeg: Float?,

    /**
     * Composite confidence in [0, 1].
     *
     * Score composition:
     *   • base = average ML similarity of top detections
     *   • penalty applied when only 1 landmark detected (×0.6)
     *   • penalty applied when position std-dev is large (normalised)
     *   • bonus when heading can be cross-validated (≥2 landmarks)
     */
    val confidence: Float,

    /**
     * Human-readable reason for the current confidence tier.
     * Shown in the confirmation dialog subtitle.
     */
    val confidenceReason: String,

    /**
     * The [GraphNode] that best represents the user's estimated position —
     * the nearest walkable node to [estimatedMapX]/[estimatedMapY].
     * This is what gets written to [com.example.mallar.ui.localization.NavigationState].
     */
    val bestStartNode: GraphNode?
)

/**
 * One landmark detection — combines ML output with graph and place data.
 */
data class LandmarkDetection(
    /** ML cosine similarity score (0–1). */
    val similarity: Float,
    /** Detected brand name as returned by [com.example.mallar.ml.LogoDetector]. */
    val brand: String,
    /** Corresponding graph node (nearest shop node for this brand). */
    val graphNode: GraphNode,
    /** Full [Place] record for logo + display name. May be null if only in graph. */
    val place: Place?
)

/** Confidence tiers used for UX branching. */
enum class LocalizationTier {
    /** ≥ 0.75 — auto-accept, show brief toast. */
    HIGH,
    /** 0.45 – 0.74 — show human confirmation dialog. */
    MEDIUM,
    /** < 0.45 — show re-scan prompt. */
    LOW
}

val LocalizationResult.tier: LocalizationTier
    get() = when {
        confidence >= 0.75f -> LocalizationTier.HIGH
        confidence >= 0.45f -> LocalizationTier.MEDIUM
        else                -> LocalizationTier.LOW
    }
