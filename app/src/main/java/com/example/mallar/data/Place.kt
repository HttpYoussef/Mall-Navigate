package com.example.mallar.data

import com.google.gson.annotations.SerializedName

data class Place(
    @SerializedName("ID")     val id: Int,
    @SerializedName("Brand")  val brand: String,
    @SerializedName("X")      val x: Int,
    @SerializedName("Y")      val y: Int,
    @SerializedName("logos")  val logo: String,
    @SerializedName("category") val category: String? = null,  // e.g. "Fashion", "Dining", "Perfumes& Cosmetics"
    @SerializedName("floor")  val floor: Int = 2,
)