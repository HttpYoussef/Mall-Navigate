package com.example.mallar.voice

/**
 * Lightweight in-memory context for offline voice + navigation.
 * No cloud, no JSON — only what the assistant needs for follow-ups
 * ("كام باقي؟" while a destination is already known).
 */
object ConversationContext {

    @Volatile
    var currentDestinationName: String? = null
        private set

    @Volatile
    var lastNavigationInstruction: String? = null
        private set

    @Volatile
    var isNavigationActive: Boolean = false
        private set

    private var variationSalt: Int = 0

    fun setDestination(name: String?) {
        currentDestinationName = name?.takeIf { it.isNotBlank() }
    }

    fun setNavigationActive(active: Boolean) {
        isNavigationActive = active
        if (!active) lastNavigationInstruction = null
    }

    fun recordNavigationInstruction(text: String) {
        if (text.isNotBlank()) lastNavigationInstruction = text
    }

    /** Biased pseudo-random index for phrase lists (stable enough, zero allocation). */
    fun nextVariationIndex(mod: Int): Int {
        if (mod <= 1) return 0
        variationSalt = variationSalt * 1103515245 + 12345
        return (variationSalt and 0x7fffffff) % mod
    }

    fun clearMemory() {
        currentDestinationName = null
        lastNavigationInstruction = null
        isNavigationActive = false
        variationSalt = 0
    }
}
