package com.example.mallar.utils

import com.example.mallar.data.AStarPath
import com.example.mallar.data.GraphNode
import com.example.mallar.data.MallGraph
import com.example.mallar.data.Place

object FloorMapAssets {

    const val FLOOR_2 = 2
    const val FLOOR_3 = 3

    fun mapAssetForFloor(floor: Int): String = when (floor) {
        FLOOR_3 -> "floor3_map.jpg"
        else -> "floor2_map.jpg"
    }

    fun floorForPlace(place: Place?): Int = place?.floor ?: FLOOR_2

    /** Floor to display on the static map: destination, else path majority, else 2. */
    fun floorForStaticMap(
        path: AStarPath?,
        nodeMap: Map<Int, GraphNode>,
        destination: Place?,
    ): Int {
        destination?.floor?.let { return it }
        path?.nodeIds?.let { ids ->
            if (ids.isEmpty()) return FLOOR_2
            val counts = ids.mapNotNull { nodeMap[it]?.floor }.groupingBy { it }.eachCount()
            return counts.maxByOrNull { it.value }?.key ?: FLOOR_2
        }
        return FLOOR_2
    }

    /** Floor for live navigation map layer from current path segment. */
    fun floorForPathIndex(pathNodeIds: List<Int>, nodeMap: Map<Int, GraphNode>, segmentIdx: Int): Int {
        val nodeId = pathNodeIds.getOrNull(segmentIdx.coerceIn(0, (pathNodeIds.size - 1).coerceAtLeast(0)))
        return nodeMap[nodeId]?.floor ?: FLOOR_2
    }

    fun nodesOnFloor(graph: MallGraph, floor: Int): List<GraphNode> =
        graph.nodes.filter { it.floor == floor }

    /** Path node IDs that lie on [floor] (for drawing on a single-floor map image). */
    fun pathNodeIdsOnFloor(
        path: AStarPath?,
        nodeMap: Map<Int, GraphNode>,
        floor: Int,
    ): List<Int> = path?.nodeIds?.filter { nodeMap[it]?.floor == floor } ?: emptyList()
}
