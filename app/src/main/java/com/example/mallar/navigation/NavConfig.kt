package com.example.mallar.navigation

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * NavConfig
 * ─────────────────────────────────────────────────────────────────────────────
 * Central source of truth for all physical and geometric constants used across
 * the movement tracking, ML, and rendering subsystems.
 * ─────────────────────────────────────────────────────────────────────────────
 */
object NavConfig {

    // ── Physical Scale ───────────────────────────────────────────────────────
    const val PIXELS_PER_METER = 4.48f
    const val DEFAULT_STRIDE_LENGTH_M = 0.75f

    // ── Snapping & Rerouting Thresholds ──────────────────────────────────────
    const val SNAP_THRESHOLD_PX = 30.0
    const val REROUTE_THRESHOLD_PX = 45.0
    const val GLOBAL_MATCH_THRESHOLD_PX = 60.0
    const val ARRIVAL_THRESHOLD_PX = 9.0
    
    /** Position jump above which we bypass smoothing (instant accept). ~4m. */
    const val SMOOTHING_JUMP_THRESHOLD_PX = 80.0
    
    /** Transition arrival threshold. */
    const val TRANSITION_ARRIVAL_THRESHOLD_PX = 18.0

    // ── Sensor Tuning ────────────────────────────────────────────────────────
    const val STEP_DEBOUNCE_MS = 400L
    const val SOFT_STEP_THRESHOLD = 11.5f
    
    /** Below this threshold the user is considered "facing straight". */
    const val TURN_THRESHOLD_DEG = 20f
    
    /** Heading change beyond which we accept instantly (genuine turn). */
    const val HEADING_JUMP_THRESHOLD_DEG = 60f

    // ── Drift Monitoring ─────────────────────────────────────────────────────
    const val DRIFT_OFF_PATH_WARNING_STEPS = 5
    const val DRIFT_OFF_PATH_CRITICAL_STEPS = 12
    const val DRIFT_MAX_STEPS_BEFORE_RELOC = 80
    const val DRIFT_MAX_STEP_DIST_PX = 40.0
    const val HEADING_STABILITY_CRITICAL_DEG = 40f
    const val DRIFT_RELOC_COOLDOWN_MS = 20_000L

    // ── Floor Transition (Barometer) ─────────────────────────────────────────
    /** Minimum altitude change (meters) to auto-confirm a floor transition. */
    const val AUTO_FLOOR_CONFIRM_THRESHOLD_M = 3.5f
    /** Typical hPa change per meter of elevation (approximate). */
    const val HPA_PER_METER = 0.12f
}
