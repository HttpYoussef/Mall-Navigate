package com.example.mallar.data

import com.google.gson.annotations.SerializedName

/**
 * Data classes matching the mall_navigation_map_v1.0.json schema.
 * Used only for JSON deserialization — the app internally works with
 * GraphNode / MallGraph (converted in MallGraphRepository.load).
 */

data class NavigationMap(
    @SerializedName("floors")             val floors: List<NavFloor>,
    @SerializedName("stair_connections")   val stairConnections: List<StairConnection> = emptyList(),
    @SerializedName("stores")             val stores: List<StoreInfo> = emptyList()
)

data class NavFloor(
    @SerializedName("id")    val id: Int,
    @SerializedName("nodes") val nodes: List<MapNode>,
    @SerializedName("edges") val edges: List<List<String>> = emptyList(),
    @SerializedName("image") val image: String? = null
)

data class MapNode(
    @SerializedName("id")       val id: String,
    @SerializedName("x")        val x: Double,
    @SerializedName("y")        val y: Double,
    @SerializedName("store_id") val storeId: String? = null
)

data class StairConnection(
    @SerializedName("id")        val id: String,
    @SerializedName("type")      val type: String,
    @SerializedName("from_node") val fromNode: String,
    @SerializedName("to_node")   val toNode: String,
    @SerializedName("label")     val label: String? = null
)

data class StoreInfo(
    @SerializedName("id")       val id: String,
    @SerializedName("name")     val name: String,
    @SerializedName("category") val category: String? = null,
    @SerializedName("floor")    val floor: Int = 2,
    @SerializedName("logo")     val logo: String? = null
)
