package com.example.mallar.navigation

import com.example.mallar.data.AStarDirection

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Navigation Turn Directions & Info
 * ─────────────────────────────────────────────────────────────────────────────
 * 
 * High-level turn directions and instruction metadata consumed by voice
 * coordinators, text guidance engines, and navigation UI cues.
 * Completely decoupled from the deprecated pseudo-AR overlay pipeline.
 */
enum class NavigationTurnDirection {
    STRAIGHT,
    LEFT,
    RIGHT,
    U_TURN,
    ELEVATOR,
    STAIRS,
    ARRIVED;

    companion object {
        fun fromAStarDirection(dir: AStarDirection): NavigationTurnDirection = when (dir) {
            AStarDirection.STRAIGHT -> STRAIGHT
            AStarDirection.LEFT -> LEFT
            AStarDirection.RIGHT -> RIGHT
            AStarDirection.ARRIVED -> ARRIVED
        }
    }
}

/**
 * Encapsulates the next imminent turn instruction and remaining distance.
 */
data class NavigationTurnInfo(
    val direction: NavigationTurnDirection,
    val distanceM: Float,
    val instructionText: String = ""
)
