package com.example.mallar.navigation

import com.example.mallar.data.GraphNode
import kotlin.math.sqrt

/**
 * Detects floor-change points along an A* path and supports pausing at
 * escalator/elevator nodes until the user confirms they changed floors.
 */
object FloorTransitionHelper {

    data class PathFloorTransition(
        val departNodeIndex: Int,
        val arriveNodeIndex: Int,
        val fromFloor: Int,
        val toFloor: Int,
        val transitionType: String,
        val departNodeId: Int,
        val arriveNodeId: Int,
    )

    fun scanPathTransitions(path: List<GraphNode>): List<PathFloorTransition> {
        if (path.size < 2) return emptyList()
        val out = mutableListOf<PathFloorTransition>()
        for (i in 0 until path.lastIndex) {
            val a = path[i]
            val b = path[i + 1]
            if (a.floor != b.floor) {
                out += PathFloorTransition(
                    departNodeIndex = i,
                    arriveNodeIndex = i + 1,
                    fromFloor       = a.floor,
                    toFloor         = b.floor,
                    transitionType  = a.transitionType ?: b.transitionType ?: "escalator",
                    departNodeId    = a.id,
                    arriveNodeId    = b.id,
                )
            }
        }
        return out
    }

    fun distanceToNodePx(userX: Double, userY: Double, node: GraphNode): Double {
        val dx = node.x - userX
        val dy = node.y - userY
        return sqrt(dx * dx + dy * dy)
    }

    fun pendingTransition(
        path: List<GraphNode>,
        transitions: List<PathFloorTransition>,
        completedCount: Int,
        segmentIdx: Int,
        userX: Double,
        userY: Double,
    ): PathFloorTransition? {
        val next = transitions.getOrNull(completedCount) ?: return null
        if (segmentIdx < next.departNodeIndex) return null
        val depart = path.getOrNull(next.departNodeIndex) ?: return null
        if (distanceToNodePx(userX, userY, depart) > NavConfig.TRANSITION_ARRIVAL_THRESHOLD_PX) return null
        return next
    }
}
