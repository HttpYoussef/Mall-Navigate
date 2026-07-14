package com.example.mallar.data

import com.google.gson.annotations.SerializedName

data class ParkingLocation(
    @SerializedName("zone") val zone: String,
    @SerializedName("slot") val slot: String,
    @SerializedName("floor") val floor: String,
    @SerializedName("savedAt") val savedAt: Long,
    @SerializedName("photoUri") val photoUri: String? = null
)
