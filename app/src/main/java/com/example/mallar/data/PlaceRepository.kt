package com.example.mallar.data

import android.content.Context

object PlaceRepository {

    private var places: List<Place> = emptyList()

    fun load(context: Context): List<Place> {
        if (places.isNotEmpty()) return places
        return try {
            val graph = MallGraphRepository.load(context)
            places = buildPlaces(graph)
            places
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildPlaces(graph: MallGraph): List<Place> {
        return graph.nodes
            .filter { it.shopId != null && it.shopName != null }
            .groupBy { it.shopId!! }
            .map { (_, nodes) ->
                nodes.firstOrNull { !it.category.isNullOrBlank() }
                    ?: nodes.firstOrNull { it.floor == 2 }
                    ?: nodes.first()
            }
            .map { node ->
                Place(
                    id       = node.shopId!!,
                    brand    = node.shopName!!,
                    x        = node.x.toInt(),
                    y        = node.y.toInt(),
                    logo     = node.logo ?: "",
                    category = node.category,
                    floor    = node.floor,
                )
            }
            .sortedBy { it.brand.lowercase() }
    }

    fun logoAssetPath(place: Place): String = place.logo

    fun matchesCategory(place: Place, filterKey: String): Boolean {
        if (filterKey.isBlank()) return true
        return place.category.orEmpty().equals(filterKey.trim(), ignoreCase = true)
    }
}
