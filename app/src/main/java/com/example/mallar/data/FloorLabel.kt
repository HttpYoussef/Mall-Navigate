package com.example.mallar.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.mallar.R

/**
 * Floor label container holding the localized string resource ID and the
 * Western-digit-formatted floor number argument.
 */
data class FloorLabel(
    @StringRes val resId: Int,
    val arg: String
) {
    /** Alias for [arg] for explicit readability. */
    val formattedFloor: String get() = arg
}

/**
 * Returns a [FloorLabel] with [R.string.floor_label] and the floor number
 * pre-formatted with Western digits via [WesternDigits.format].
 */
fun floorLabel(floor: Int): FloorLabel {
    return FloorLabel(
        resId = R.string.floor_label,
        arg = WesternDigits.format(floor)
    )
}

/**
 * Resolves the localized floor label in Compose.
 */
@Composable
fun FloorLabel.asString(): String = stringResource(resId, arg)

/**
 * Resolves the localized floor label using an Android [Context].
 */
fun FloorLabel.format(context: Context): String = context.getString(resId, arg)

/**
 * Resolves the localized floor label directly in Compose.
 */
@Composable
fun floorDisplayLabel(floor: Int): String = floorLabel(floor).asString()

/**
 * Resolves the localized floor label directly using an Android [Context].
 */
fun floorDisplayLabel(floor: Int, context: Context): String = floorLabel(floor).format(context)
