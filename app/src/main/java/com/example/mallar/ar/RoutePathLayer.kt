package com.example.mallar.ar

import android.util.Log
import com.example.mallar.ar.model.NavigationSessionSnapshot
import com.example.mallar.ar.model.RouteNodeMetadata
import com.example.mallar.data.AStarDirection
import com.example.mallar.data.MallGraph
import com.example.mallar.data.MallGraphRepository

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Module 5 — Route/Path Layer
 * ─────────────────────────────────────────────────────────────────────────────
 * 
 * Owns the facility-coordinate representation of the route. Decoupled from
 * ARCore concepts. Manages path updates and metadata resolution.
 */
class RoutePathLayer(
    initialSnapshot: NavigationSessionSnapshot,
    private val mallGraph: MallGraph
) {
    companion object {
        private const val TAG = "RoutePathLayer"
    }

    private var currentPathNodeIds = initialSnapshot.pathNodeIds
    private var currentInstructions = initialSnapshot.instructions
    private var cachedMetadata: List<RouteNodeMetadata> = emptyList()

    init {
        refreshMetadata()
    }

    /**
     * Triggers a route recalculation using the existing pathfinding engine.
     * Updates internal state while leaving global NavigationState untouched.
     */
    fun recalculate(currentPositionNodeId: Int) {
        val destinationNodeId = currentPathNodeIds.lastOrNull() ?: return
        
        Log.d(TAG, "Module 5: Requesting recalculation from node $currentPositionNodeId to $destinationNodeId.")
        val newPath = MallGraphRepository.aStarByNodeId(mallGraph, currentPositionNodeId, destinationNodeId)
        
        if (newPath != null) {
            currentPathNodeIds = newPath.nodeIds
            currentInstructions = newPath.steps
            refreshMetadata()
            Log.d(TAG, "Module 5: Route successfully updated. New path has ${currentPathNodeIds.size} nodes.")
        } else {
            Log.e(TAG, "Module 5: Recalculation failed. No path found.")
        }
    }

    /**
     * Recalculates route from a facility position (e.g. following deviation or route reversal).
     * Finds the nearest navigable node on the specified floor and plans to the current destination.
     */
    fun recalculateFromFacilityPosition(facilityX: Double, facilityY: Double, floor: Int? = null): Boolean {
        val destinationNodeId = currentPathNodeIds.lastOrNull() ?: return false
        val candidateNodes = if (floor != null) {
            mallGraph.nodes.filter { it.floor == floor }
        } else {
            mallGraph.nodes
        }
        val nearest = candidateNodes.minByOrNull { node ->
            val dx = node.x - facilityX
            val dy = node.y - facilityY
            dx * dx + dy * dy
        } ?: return false

        val newPath = MallGraphRepository.aStarByNodeId(mallGraph, nearest.id, destinationNodeId)
        return if (newPath != null) {
            currentPathNodeIds = newPath.nodeIds
            currentInstructions = newPath.steps
            refreshMetadata()
            Log.d(TAG, "Module 5: Route successfully rebuilt from nearest node ${nearest.id}. Nodes: ${currentPathNodeIds.size}")
            true
        } else {
            Log.e(TAG, "Module 5: Route rebuild failed from node ${nearest.id}.")
            false
        }
    }

    /**
     * Resolves node IDs into coordinates and instruction metadata.
     */
    private fun refreshMetadata() {
        val nodeMap = mallGraph.nodes.associateBy { it.id }
        val instructionMap = currentInstructions.associateBy { it.nodeIndex }

        cachedMetadata = currentPathNodeIds.mapIndexed { index, id ->
            val node = nodeMap[id] ?: throw IllegalStateException("Critical: Node ID $id missing from Graph during metadata refresh.")
            val instruction = instructionMap[index]
            
            RouteNodeMetadata(
                nodeId = id,
                x = node.x,
                y = node.y,
                floor = node.floor,
                direction = instruction?.direction ?: AStarDirection.STRAIGHT,
                isDestination = index == currentPathNodeIds.lastIndex,
                isFloorTransition = node.isFloorTransition
            )
        }
    }

    /** Returns the current list of node IDs forming the route. */
    fun getActiveRouteIds(): List<Int> = currentPathNodeIds

    /** Returns the full metadata set for the current route. */
    fun getRouteMetadata(): List<RouteNodeMetadata> = cachedMetadata
}
