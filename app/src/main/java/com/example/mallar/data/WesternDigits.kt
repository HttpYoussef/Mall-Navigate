package com.example.mallar.data

import java.util.Locale

/**
 * Formatter ensuring numbers and percentages are always rendered using Western digits (0-9),
 * regardless of the ambient or system default [Locale].
 */
object WesternDigits {

    private val LOCALE: Locale = Locale.US

    /**
     * Formats an [Int] value using Western digits (e.g. 1234 -> "1234").
     */
    fun format(value: Int): String = String.format(LOCALE, "%d", value)

    /**
     * Formats a [Long] value using Western digits (e.g. 1234L -> "1234").
     */
    fun format(value: Long): String = String.format(LOCALE, "%d", value)

    /**
     * Formats a [Double] value with a specified number of [decimals] using Western digits
     * and a period as decimal separator.
     */
    fun format(value: Double, decimals: Int = 0): String {
        val precision = if (decimals < 0) 0 else decimals
        return String.format(LOCALE, "%.${precision}f", value)
    }

    /**
     * Formats an integer percentage using Western digits followed by a literal '%' (e.g. 45 -> "45%").
     */
    fun percent(value: Int): String = "${format(value)}%"
}
