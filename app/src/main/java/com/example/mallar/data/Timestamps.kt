package com.example.mallar.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared helper for locale-neutral, Western-digit wall-clock timestamps.
 */
object Timestamps {
    /** Locale-neutral, Western-digit wall-clock stamp: "yyyy-MM-dd HH:mm". */
    fun format(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            .format(Date(epochMillis))
}
