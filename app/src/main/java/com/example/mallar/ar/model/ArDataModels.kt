package com.example.mallar.ar.model

import com.example.mallar.data.AStarDirection
import com.example.mallar.data.NavInstruction

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * AR Subsystem Data Contracts
 * ─────────────────────────────────────────────────────────────────────────────
 * 
 * Immutable data models as defined in AR Engineering Specification §5.
 */

/**
 * Module 9 Output / Module 5 Input.
 * An immutable, session-scoped snapshot of the global navigation state.
 */
data class NavigationSessionSnapshot @JvmOverloads constructor(
    val destinationName: String,
    val startNodeId: Int,
    val pathNodeIds: List<Int>,
    val instructions: List<NavInstruction>,
    val initialHeadingDeg: Float? = null,
    val startWithAr: Boolean = false
)

/**
 * Metadata for a single node on the active route.
 * Bridges facility-coordinate data with subsystem-specific attributes.
 *
 * @JvmOverloads generates both the 7-parameter and 6-parameter JVM constructors,
 * guaranteeing backward-compatibility across callers and DEX slices.
 */
data class RouteNodeMetadata @JvmOverloads constructor(
    val nodeId: Int,
    val x: Double,
    val y: Double,
    val floor: Int,
    val direction: AStarDirection,
    val isDestination: Boolean,
    val isFloorTransition: Boolean = false
)

/**
 * Module 3 Output.
 * Represents a relative 2D displacement vector in facility coordinates.
 */
data class PdrDisplacement(
    val dx: Double,
    val dy: Double,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Module 3 Output.
 * Health signal for a specific sensor stream.
 */
data class SensorStalenessStatus(
    val isStale: Boolean,
    val sensorName: String
)
