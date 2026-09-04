package com.example.mallar.data

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.mallar.R

/**
 * Presentation-only mapping for canonical store categories.
 *
 * The runtime mall graph stores raw keys (`Fashion`, `Jewellery`, `Perfumes& Cosmetics`,
 * `Dining`, `Pharmacy`). Raw keys stay untouched everywhere they are used for filtering
 * and matching; only display goes through this mapping.
 */
object StoreCategory {
    const val FASHION = "Fashion"
    const val JEWELLERY = "Jewellery"
    const val PERFUMES_COSMETICS = "Perfumes& Cosmetics"
    const val DINING = "Dining"
    const val PHARMACY = "Pharmacy"

    val CANONICAL_KEYS: List<String> = listOf(
        FASHION,
        JEWELLERY,
        PERFUMES_COSMETICS,
        DINING,
        PHARMACY,
    )

    @StringRes
    fun displayRes(rawKey: String?): Int? = categoryDisplayRes(rawKey)
}

/**
 * Returns the [StringRes] display resource for a raw category key.
 *
 * Handles the 5 canonical keys including the exact literal `"Perfumes& Cosmetics"`,
 * normalizes whitespace variations, and aliases `"Food"` to Dining.
 * Returns `null` for unknown keys or null/blank input.
 */
@StringRes
fun categoryDisplayRes(rawKey: String?): Int? {
    if (rawKey.isNullOrBlank()) return null
    return when (rawKey.trim().lowercase()) {
        "fashion" -> R.string.category_fashion
        "jewellery" -> R.string.category_jewellery
        "perfumes& cosmetics", "perfumes & cosmetics" -> R.string.category_perfumes_cosmetics
        "dining", "food" -> R.string.category_dining
        "pharmacy" -> R.string.category_pharmacy
        else -> null
    }
}

/**
 * Returns the localized category display label if recognized, or `null`.
 */
@Composable
fun categoryDisplayLabel(rawKey: String?): String? {
    val resId = categoryDisplayRes(rawKey) ?: return null
    return stringResource(resId)
}
