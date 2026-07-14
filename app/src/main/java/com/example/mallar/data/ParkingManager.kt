package com.example.mallar.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ParkingManager {
    private const val PREFS_NAME = "mallar_parking"
    private const val KEY_PARKING_LOCATION = "parking_location"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    private val _parkingLocation = MutableStateFlow<ParkingLocation?>(null)
    val parkingLocation: StateFlow<ParkingLocation?> = _parkingLocation.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PARKING_LOCATION, null)
        if (json != null) {
            try {
                _parkingLocation.value = gson.fromJson(json, ParkingLocation::class.java)
            } catch (e: Exception) {
                _parkingLocation.value = null
            }
        }
    }

    fun saveLocation(location: ParkingLocation) {
        _parkingLocation.value = location
        persist()
    }

    fun deleteLocation() {
        _parkingLocation.value = null
        prefs.edit().remove(KEY_PARKING_LOCATION).apply()
    }

    private fun persist() {
        val json = gson.toJson(_parkingLocation.value)
        prefs.edit().putString(KEY_PARKING_LOCATION, json).apply()
    }
}
