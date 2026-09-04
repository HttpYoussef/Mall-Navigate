package com.example.mallar.data

/**
 * Helper for bidirectional (bidi) text isolation.
 *
 * Wraps dynamic/interpolated values (such as Latin brand names, numbers, codes)
 * in Unicode First Strong Isolate (FSI, U+2068) and Pop Directional Isolate (PDI, U+2069)
 * marks so that they render in their own directional run without scrambling or spilling
 * over into surrounding RTL text.
 */
object BidiFormatting {

    /** First Strong Isolate mark (U+2068) */
    const val FSI: Char = '\u2068'

    /** Pop Directional Isolate mark (U+2069) */
    const val PDI: Char = '\u2069'

    /**
     * Isolates [value] within bidirectional text using Unicode isolate characters (FSI ... PDI).
     */
    fun isolate(value: String): String = "$FSI$value$PDI"
}

/**
 * Extension function wrapping this string with Unicode bidi isolate marks.
 */
fun String.bidiIsolated(): String = BidiFormatting.isolate(this)
