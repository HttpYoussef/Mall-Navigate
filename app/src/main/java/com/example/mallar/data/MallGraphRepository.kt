package com.example.mallar.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ── Graph data structures ────────────────────────────────────────────────────

data class GraphNode(
    @SerializedName("id")       val id: Int,
    @SerializedName("x")        val x: Double,
    @SerializedName("y")        val y: Double,
    @SerializedName("floor")    val floor: Int = 2,
    @SerializedName("shopId")   val shopId: Int?,
    @SerializedName("shopName") val shopName: String?,
    @SerializedName("logo")     val logo: String?,
    @SerializedName("category") val category: String? = null,
    @SerializedName("transitionType") val transitionType: String? = null,
    @SerializedName("connectedFloor") val connectedFloor: Int? = null,
    @SerializedName("transitionNodeId") val transitionNodeId: Int? = null,
    @SerializedName("escalatorElevatorId") val escalatorElevatorId: Int? = null,
) {
    val isFloorTransition: Boolean
        get() = transitionNodeId != null && connectedFloor != null
}

data class GraphEdge(
    @SerializedName("from") val from: Int,
    @SerializedName("to")   val to: Int
)

data class MallGraph(
    @SerializedName("nodes") val nodes: List<GraphNode>,
    @SerializedName("edges") val edges: List<GraphEdge>
)

// ── A* path result ───────────────────────────────────────────────────────────

data class AStarPath(
    val nodeIds: List<Int>,          // ordered node IDs on the path
    val totalDistancePx: Double,     // total path length in map pixels
    val steps: List<NavInstruction>  // consolidated turn-by-turn instructions
)

data class NavInstruction(
    val direction: AStarDirection,
    val distancePx: Double,
    val nodeIndex: Int = 0           // which node in nodeIds this step starts at
)

enum class AStarDirection { STRAIGHT, LEFT, RIGHT, ARRIVED }

// ── Repository ───────────────────────────────────────────────────────────────

object MallGraphRepository {

    /** Extra cost (px) when an edge connects two different floors (stairs/escalator). */
    private const val INTER_FLOOR_PENALTY_PX = 80.0

    private var graph: MallGraph? = null
    var loadedGraph: MallGraph? = null  // public read-only access without context

    // ── FIX: HashMap caches built once on first load — O(1) node lookups ─────
    private var nodeMapCache:     Map<Int, GraphNode>? = null
    private var shopIdMapCache:   Map<Int, GraphNode>? = null

    fun load(context: Context): MallGraph {
        graph?.let { return it }

        val json = context.assets.open("mall_navigation_map_v1.0.json")
            .bufferedReader().use { it.readText() }
        val navMap = Gson().fromJson(json, NavigationMap::class.java)
        val loaded = convertToMallGraph(navMap)

        graph       = loaded
        loadedGraph = loaded
        // Build lookup caches immediately — O(n) once, then O(1) forever
        nodeMapCache   = loaded.nodes.associateBy { it.id }
        shopIdMapCache = loaded.nodes.filter { it.shopId != null }.associateBy { it.shopId!! }
        // Validate on load
        validateGraph(loaded)
        return loaded
    }

    // ── Conversion from NavigationMap → MallGraph ────────────────────────────

    private fun convertToMallGraph(navMap: NavigationMap): MallGraph {
        // 1. Build store lookup: storeId string → StoreInfo
        val storeLookup = navMap.stores.associateBy { it.id }

        // 2. Assign sequential integer IDs to every node, track string→int mapping
        val stringToIntId = mutableMapOf<String, Int>()
        val nodeFloorMap  = mutableMapOf<String, Int>()  // nodeStringId → floor
        val graphNodes    = mutableListOf<GraphNode>()
        var nextId = 1

        for (floor in navMap.floors) {
            for (mapNode in floor.nodes) {
                val intId = nextId++
                stringToIntId[mapNode.id] = intId
                nodeFloorMap[mapNode.id]  = floor.id

                // Look up store metadata if this node belongs to a store
                val store = mapNode.storeId?.let { storeLookup[it] }
                val shopIdInt = mapNode.storeId?.removePrefix("store_")?.toIntOrNull()

                graphNodes.add(
                    GraphNode(
                        id        = intId,
                        x         = mapNode.x,
                        y         = mapNode.y,
                        floor     = floor.id,
                        shopId    = shopIdInt,
                        shopName  = store?.name,
                        logo      = store?.logo,
                        category  = store?.category,
                        // Floor transitions are handled via stair_connections below
                        transitionType      = null,
                        connectedFloor      = null,
                        transitionNodeId    = null,
                        escalatorElevatorId = null
                    )
                )
            }
        }

        // 3. Convert per-floor edges (string pairs → GraphEdge)
        val graphEdges = mutableListOf<GraphEdge>()
        for (floor in navMap.floors) {
            for (pair in floor.edges) {
                if (pair.size < 2) continue
                val fromInt = stringToIntId[pair[0]] ?: continue
                val toInt   = stringToIntId[pair[1]] ?: continue
                graphEdges.add(GraphEdge(from = fromInt, to = toInt))
            }
        }

        // 4. Convert stair_connections into cross-floor edges
        //    Also mark the departure/arrival nodes with transition metadata
        val nodeMap = graphNodes.associateBy { it.id }.toMutableMap()
        for (stair in navMap.stairConnections) {
            val fromInt = stringToIntId[stair.fromNode] ?: continue
            val toInt   = stringToIntId[stair.toNode]   ?: continue
            graphEdges.add(GraphEdge(from = fromInt, to = toInt))

            // Annotate transition nodes so FloorTransitionHelper can detect them
            val fromFloor = nodeFloorMap[stair.fromNode] ?: 2
            val toFloor   = nodeFloorMap[stair.toNode]   ?: 3
            nodeMap[fromInt]?.let { old ->
                nodeMap[fromInt] = old.copy(
                    transitionType   = stair.type,
                    connectedFloor   = toFloor,
                    transitionNodeId = toInt
                )
            }
            nodeMap[toInt]?.let { old ->
                nodeMap[toInt] = old.copy(
                    transitionType   = stair.type,
                    connectedFloor   = fromFloor,
                    transitionNodeId = fromInt
                )
            }
        }

        return MallGraph(nodes = nodeMap.values.toList(), edges = graphEdges)
    }

    // ── FIX: All lookup functions use the HashMap cache — no linear scan ──────

    fun nodeForShop(graph: MallGraph, shopId: Int): GraphNode? {
        // Use cache if built, otherwise fall back to linear (cache is always built after load())
        return shopIdMapCache?.get(shopId)
            ?: graph.nodes.firstOrNull { it.shopId == shopId }
    }

    fun nodeById(graph: MallGraph, nodeId: Int): GraphNode? {
        return nodeMapCache?.get(nodeId)
            ?: graph.nodes.firstOrNull { it.id == nodeId }
    }

    /** Look up a GraphNode from the path's nodeIds list by sequential index */
    fun nodeAtPathIndex(graph: MallGraph, path: AStarPath, index: Int): GraphNode? {
        val nodeId = path.nodeIds.getOrNull(index) ?: return null
        return nodeById(graph, nodeId)
    }

    /**
     * Find the nearest graph node to a given position (in map pixel coords).
     * Used to snap ML-detected locations to the nearest walkable node.
     */
    fun findNearestNode(graph: MallGraph, x: Double, y: Double): GraphNode? {
        return graph.nodes.minByOrNull { node ->
            val dx = node.x - x; val dy = node.y - y
            sqrt(dx * dx + dy * dy)
        }
    }

    /**
     * Find the nearest node that has a shopName (a named store/landmark).
     */
    fun findNearestShopNode(graph: MallGraph, x: Double, y: Double): GraphNode? {
        return graph.nodes
            .filter { it.shopName != null }
            .minByOrNull { node ->
                val dx = node.x - x; val dy = node.y - y
                sqrt(dx * dx + dy * dy)
            }
    }

    // ── Graph validation ─────────────────────────────────────────────────────

    private fun validateGraph(graph: MallGraph) {
        val nodeIds      = graph.nodes.map { it.id }.toSet()
        val connectedIds = mutableSetOf<Int>()
        var invalidEdges = 0

        for (e in graph.edges) {
            if (e.from !in nodeIds || e.to !in nodeIds) {
                invalidEdges++
                android.util.Log.w("MallGraph", "Invalid edge: ${e.from} → ${e.to}")
                continue
            }
            connectedIds.add(e.from)
            connectedIds.add(e.to)
        }

        val disconnected = nodeIds - connectedIds
        if (disconnected.isNotEmpty()) {
            android.util.Log.w("MallGraph", "Disconnected nodes: $disconnected")
        }
        if (invalidEdges > 0) {
            android.util.Log.w("MallGraph", "$invalidEdges invalid edges found")
        }
        android.util.Log.d("MallGraph", "Graph validated: ${graph.nodes.size} nodes, ${graph.edges.size} edges, $invalidEdges invalid")
    }

    // ── Public A* entry points ───────────────────────────────────────────────

    fun aStar(graph: MallGraph, startShopId: Int, endShopId: Int): AStarPath? {
        val startNode = nodeForShop(graph, startShopId) ?: return null
        val endNode   = nodeForShop(graph, endShopId)   ?: return null
        return runAStar(graph, startNode.id, endNode.id)
    }

    /** A* using raw node IDs instead of shop IDs */
    fun aStarByNodeId(graph: MallGraph, startNodeId: Int, endNodeId: Int): AStarPath? {
        return runAStar(graph, startNodeId, endNodeId)
    }

    // ── Core A* with Euclidean edge weights ───────────────────────────────────

    private fun runAStar(graph: MallGraph, startId: Int, goalId: Int): AStarPath? {
        // FIX: use the pre-built HashMap cache instead of building a new map every call
        val nodeMap = nodeMapCache ?: graph.nodes.associateBy { it.id }
        val goal    = nodeMap[goalId] ?: return null

        // Build undirected adjacency with Euclidean pixel distances as weights
        val adj = mutableMapOf<Int, MutableList<Pair<Int, Double>>>()
        for (e in graph.edges) {
            val a = nodeMap[e.from] ?: continue
            val b = nodeMap[e.to]   ?: continue
            val w = edgeWeight(a, b)
            adj.getOrPut(e.from) { mutableListOf() }.add(Pair(e.to,   w))
            adj.getOrPut(e.to)   { mutableListOf() }.add(Pair(e.from, w))
        }

        val gCost    = HashMap<Int, Double>()
        val cameFrom = HashMap<Int, Int>()
        gCost[startId] = 0.0

        // Priority queue sorted by fCost = g + h
        val open = java.util.PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        open.add(Pair(heuristic(nodeMap[startId]!!, goal), startId))

        val closed = HashSet<Int>()

        while (open.isNotEmpty()) {
            val (_, current) = open.poll()!!
            if (current == goalId) return reconstructPath(cameFrom, nodeMap, startId, goalId)
            if (!closed.add(current)) continue

            for ((neighbor, w) in adj[current] ?: emptyList()) {
                if (neighbor in closed) continue
                val tentativeG = (gCost[current] ?: Double.MAX_VALUE) + w
                if (tentativeG < (gCost[neighbor] ?: Double.MAX_VALUE)) {
                    cameFrom[neighbor] = current
                    gCost[neighbor] = tentativeG
                    open.add(Pair(tentativeG + heuristic(nodeMap[neighbor]!!, goal), neighbor))
                }
            }
        }
        return null
    }

    private fun heuristic(a: GraphNode, b: GraphNode) = euclidean(a, b)

    private fun euclidean(a: GraphNode, b: GraphNode): Double {
        val dx = a.x - b.x; val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun edgeWeight(a: GraphNode, b: GraphNode): Double {
        val base = euclidean(a, b)
        return if (a.floor != b.floor) base + INTER_FLOOR_PENALTY_PX else base
    }

    fun floorForShop(graph: MallGraph, shopId: Int): Int =
        nodeForShop(graph, shopId)?.floor ?: 2

    // ── Path reconstruction ──────────────────────────────────────────────────

    private fun reconstructPath(
        cameFrom: Map<Int, Int>,
        nodeMap: Map<Int, GraphNode>,
        startId: Int,
        goalId: Int
    ): AStarPath {
        val path = mutableListOf<Int>()
        var current = goalId
        while (current != startId) {
            path.add(current)
            current = cameFrom[current] ?: break
        }
        path.add(startId)
        path.reverse()

        var total = 0.0
        for (i in 0 until path.size - 1) {
            total += euclidean(nodeMap[path[i]]!!, nodeMap[path[i + 1]]!!)
        }

        return AStarPath(path, total, buildConsolidatedInstructions(path, nodeMap))
    }

    // ── Consolidated instruction builder ─────────────────────────────────────
    // Merges consecutive straight segments so the user gets meaningful turn cards
    // instead of one card per node.

    private fun buildConsolidatedInstructions(
        path: List<Int>,
        nodeMap: Map<Int, GraphNode>
    ): List<NavInstruction> {
        if (path.size < 2) return listOf(NavInstruction(AStarDirection.ARRIVED, 0.0, 0))

        val TURN_THRESHOLD = 30.0  // degrees — 30° is correct for tight indoor corridors

        val instructions    = mutableListOf<NavInstruction>()
        var currentDir      = AStarDirection.STRAIGHT
        var accumulatedDist = 0.0
        var segNodeIndex    = 0

        for (i in 0 until path.size - 1) {
            val a = nodeMap[path[i]]!!
            val b = nodeMap[path[i + 1]]!!
            val segDist = euclidean(a, b)

            val dir: AStarDirection = if (i == 0) {
                AStarDirection.STRAIGHT
            } else {
                val prev  = nodeMap[path[i - 1]]!!
                val angle = angleChange(prev, a, b)
                when {
                    angle > TURN_THRESHOLD  -> AStarDirection.RIGHT
                    angle < -TURN_THRESHOLD -> AStarDirection.LEFT
                    else -> AStarDirection.STRAIGHT
                }
            }

            if (i == 0) {
                currentDir      = dir
                segNodeIndex    = 0
                accumulatedDist = segDist
            } else if (dir == AStarDirection.STRAIGHT && currentDir == AStarDirection.STRAIGHT) {
                // Continue accumulating straight distance
                accumulatedDist += segDist
            } else {
                // Emit the previous segment
                instructions.add(NavInstruction(currentDir, accumulatedDist, segNodeIndex))
                // Start new segment
                currentDir      = dir
                segNodeIndex    = i
                accumulatedDist = segDist
            }
        }

        // Emit the last segment
        if (accumulatedDist > 0) {
            instructions.add(NavInstruction(currentDir, accumulatedDist, segNodeIndex))
        }

        // Final arrived
        instructions.add(NavInstruction(AStarDirection.ARRIVED, 0.0, path.size - 1))

        return instructions
    }

    private fun angleChange(prev: GraphNode, cur: GraphNode, next: GraphNode): Double {
        val v1x = cur.x - prev.x; val v1y = cur.y - prev.y
        val v2x = next.x - cur.x; val v2y = next.y - cur.y
        val cross = v1x * v2y - v1y * v2x
        val dot   = v1x * v2x + v1y * v2y
        return Math.toDegrees(atan2(cross, dot))
    }
}