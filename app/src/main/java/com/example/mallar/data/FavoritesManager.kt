package com.example.mallar.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists favorite store IDs locally and exposes a reactive [StateFlow] for Compose.
 */
object FavoritesManager {

    private const val PREFS_NAME = "mallar_favorites"
    private const val KEY_FAVORITE_IDS = "favorite_ids"
    private const val LEGACY_KEY_BRANDS = "favorite_brands"

    private lateinit var prefs: SharedPreferences

    private val _favorites = MutableStateFlow<Set<Int>>(emptySet())
    val favorites: StateFlow<Set<Int>> = _favorites.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_FAVORITE_IDS, null)
        if (stored != null) {
            _favorites.value = stored.mapNotNull { it.toIntOrNull() }.toSet()
        } else {
            migrateLegacyBrands(context)
        }
    }

    private fun migrateLegacyBrands(context: Context) {
        val legacyBrands = prefs.getStringSet(LEGACY_KEY_BRANDS, null) ?: emptySet()
        if (legacyBrands.isEmpty()) {
            _favorites.value = emptySet()
            return
        }
        val places = PlaceRepository.load(context)
        val ids = legacyBrands.mapNotNull { brand ->
            places.firstOrNull { it.brand == brand }?.id
        }.toSet()
        _favorites.value = ids
        persist()
        prefs.edit().remove(LEGACY_KEY_BRANDS).apply()
    }

    fun isFavorite(placeId: Int): Boolean = _favorites.value.contains(placeId)

    fun toggleFavorite(placeId: Int): Boolean {
        val current = _favorites.value.toMutableSet()
        val added = if (current.contains(placeId)) {
            current.remove(placeId)
            false
        } else {
            current.add(placeId)
            true
        }
        _favorites.value = current
        persist()
        return added
    }

    fun addFavorite(placeId: Int) {
        val current = _favorites.value.toMutableSet()
        if (current.add(placeId)) {
            _favorites.value = current
            persist()
        }
    }

    fun removeFavorite(placeId: Int) {
        val current = _favorites.value.toMutableSet()
        if (current.remove(placeId)) {
            _favorites.value = current
            persist()
        }
    }

    private fun persist() {
        val encoded = _favorites.value.map { it.toString() }.toSet()
        prefs.edit().putStringSet(KEY_FAVORITE_IDS, encoded).apply()
    }
}
