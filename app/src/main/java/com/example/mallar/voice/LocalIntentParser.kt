package com.example.mallar.voice

import com.example.mallar.data.GraphNode
import com.example.mallar.data.MallGraph

/**
 * Offline intent detection: bilingual keyword + fuzzy shop matching on the mall graph.
 */
object LocalIntentParser {

    private val GREETING_EN = setOf("hi", "hello", "hey", "yo", "hiya", "howdy", "morning", "evening")
    private val GREETING_AR = listOf("مرحبا", "مرحباً", "اهلا", "أهلا", "هلا", "هاي", "السلام", "صباح", "مساء")

    private val NAV_TRIGGERS_EN = listOf(
        "take me to", "go to", "navigate to", "get to", "directions to",
        "find", "show me", "walk to", "i want to go to", "i need to go to",
        "where is", "how do i get to", "bring me to", "head to"
    )
    private val NAV_TRIGGERS_AR = listOf(
        "عايز أروح", "عايز اروح", "روحني", "وديني", "روح", "اروح",
        "خدني", "اتجه", "ابي اروح", "أريد الذهاب", "أريد أذهب",
        "احتاج أروح", "فين", "أين", "كيف أروح", "ازاي أروح"
    )

    private val REPEAT_TRIGGERS_EN = listOf("repeat", "again", "say that again", "what did you say", "come again")
    private val REPEAT_TRIGGERS_AR = listOf("كرر", "تاني", "مرة تانية", "قولي تاني", "إيه اللي قلته")

    private val DISTANCE_TRIGGERS_EN = listOf("how far", "distance", "how many meters", "remaining distance", "how much further")
    private val DISTANCE_TRIGGERS_AR = listOf("كام باقي", "كم باقي", "المسافة", "كام متر", "قد ايه باقي", "باقي كام")

    private val ETA_TRIGGERS_EN = listOf("how long", "how many minutes", "when do i arrive", "eta", "time remaining")
    private val ETA_TRIGGERS_AR = listOf("كام دقيقة", "كم دقيقة", "هوصل امتى", "امتى أوصل", "الوقت")

    private val LOCATION_TRIGGERS_EN = listOf("where am i", "my location", "current location", "where are we")
    private val LOCATION_TRIGGERS_AR = listOf("فين أنا", "فين انا", "أنا فين", "انا فين", "مكاني", "احنا فين")

    private val STOP_TRIGGERS_EN = listOf("stop navigation", "stop", "cancel navigation", "end navigation", "quit")
    private val STOP_TRIGGERS_AR = listOf("وقف التنقل", "وقف", "إلغاء", "الغاء", "خلاص وقف", "إلغي التنقل")

    private val HELP_TRIGGERS_EN = listOf("help", "what can you do", "commands", "assist me")
    private val HELP_TRIGGERS_AR = listOf("مساعدة", "ساعدني", "ايه اللي تعرفه", "ازاي تساعدني")

    fun parse(transcript: String, graph: MallGraph?): ParsedIntent {
        val text = transcript.trim()
        if (text.isEmpty()) return ParsedIntent(VoiceIntent.UNKNOWN, rawText = text, isArabic = false)

        val arabic = text.any { it.code in 0x0600..0x06FF }
        val lower = text.lowercase()

        if (isGreeting(lower, arabic)) {
            return ParsedIntent(VoiceIntent.GREETING, rawText = text, isArabic = arabic)
        }

        if (matchesAny(lower, arabic, STOP_TRIGGERS_EN, STOP_TRIGGERS_AR)) {
            return ParsedIntent(VoiceIntent.STOP_NAVIGATION, rawText = text, isArabic = arabic)
        }

        if (matchesAny(lower, arabic, REPEAT_TRIGGERS_EN, REPEAT_TRIGGERS_AR)) {
            return ParsedIntent(VoiceIntent.REPEAT_DIRECTIONS, rawText = text, isArabic = arabic)
        }

        if (matchesAny(lower, arabic, DISTANCE_TRIGGERS_EN, DISTANCE_TRIGGERS_AR)) {
            return ParsedIntent(VoiceIntent.REMAINING_DISTANCE, rawText = text, isArabic = arabic)
        }

        if (matchesAny(lower, arabic, ETA_TRIGGERS_EN, ETA_TRIGGERS_AR)) {
            return ParsedIntent(VoiceIntent.ETA, rawText = text, isArabic = arabic)
        }

        if (matchesAny(lower, arabic, LOCATION_TRIGGERS_EN, LOCATION_TRIGGERS_AR)) {
            return ParsedIntent(VoiceIntent.CURRENT_LOCATION, rawText = text, isArabic = arabic)
        }

        if (matchesAny(lower, arabic, HELP_TRIGGERS_EN, HELP_TRIGGERS_AR)) {
            return ParsedIntent(VoiceIntent.HELP, rawText = text, isArabic = arabic)
        }

        extractOriginDestination(lower, graph)?.let { (origin, dest) ->
            return ParsedIntent(
                intent = VoiceIntent.NAVIGATE_TO,
                destination = dest,
                origin = origin,
                rawText = text,
                isArabic = arabic,
                confidence = 0.92f
            )
        }

        val destination = extractDestination(text, arabic, graph)
        if (destination != null) {
            return ParsedIntent(
                intent = VoiceIntent.NAVIGATE_TO,
                destination = destination,
                rawText = text,
                isArabic = arabic,
                confidence = 0.9f
            )
        }

        if (graph != null) {
            val directMatch = fuzzyMatchShop(text, graph)
            if (directMatch != null) {
                return ParsedIntent(
                    intent = VoiceIntent.NAVIGATE_TO,
                    destination = directMatch,
                    rawText = text,
                    isArabic = arabic,
                    confidence = 0.75f
                )
            }
        }

        return ParsedIntent(VoiceIntent.UNKNOWN, rawText = text, isArabic = arabic, confidence = 0f)
    }

    /**
     * "from Zara to Bershka", "to Bershka from Zara", "من زارا ل برشكا", etc.
     */
    private fun extractOriginDestination(lower: String, graph: MallGraph?): Pair<String, String>? {
        if (graph == null) return null

        fun cleanSegment(s: String): String =
            s.trim().removeSuffix(".").removeSuffix(",").removeSuffix("؟").removeSuffix("?").trim()

        fun resolve(frag: String): String? {
            val c = cleanSegment(frag)
            if (c.length < 2) return null
            return fuzzyMatchShop(c, graph) ?: c
        }

        Regex("""from\s+(.+?)\s+to\s+(.+)$""", RegexOption.IGNORE_CASE).find(lower)?.let { m ->
            val o = resolve(m.groupValues[1]) ?: return null
            val d = resolve(m.groupValues[2]) ?: return null
            if (o.equals(d, ignoreCase = true)) return null
            return o to d
        }

        Regex("""(?:go|get)\s+from\s+(.+?)\s+to\s+(.+)$""", RegexOption.IGNORE_CASE).find(lower)?.let { m ->
            val o = resolve(m.groupValues[1]) ?: return null
            val d = resolve(m.groupValues[2]) ?: return null
            if (o.equals(d, ignoreCase = true)) return null
            return o to d
        }

        Regex(""".*\bto\s+(.+?)\s+from\s+(.+)$""", RegexOption.IGNORE_CASE).find(lower)?.let { m ->
            val d = resolve(m.groupValues[1]) ?: return null
            val o = resolve(m.groupValues[2]) ?: return null
            if (o.equals(d, ignoreCase = true)) return null
            return o to d
        }

        Regex("""من\s+(.+?)\s+(?:إلى|الى|الي)\s*(.+)$""").find(lower)?.let { m ->
            val o = resolve(m.groupValues[1]) ?: return null
            val d = resolve(m.groupValues[2]) ?: return null
            if (o.equals(d, ignoreCase = true)) return null
            return o to d
        }

        Regex("""من\s+(.+?)\s+ل\s+(.+)$""").find(lower)?.let { m ->
            val o = resolve(m.groupValues[1]) ?: return null
            val d = resolve(m.groupValues[2]) ?: return null
            if (o.equals(d, ignoreCase = true)) return null
            return o to d
        }

        return null
    }

    private fun isGreeting(lower: String, arabic: Boolean): Boolean {
        if (arabic) {
            if (GREETING_AR.any { lower.contains(it) }) return true
            return false
        }
        val tokens = lower.split(Regex("[\\s,.!؟?]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        if (tokens.size > 4) return false
        if (tokens.any { it in GREETING_EN }) return true
        if (tokens.size >= 2 && tokens[0] == "good" && (tokens[1] == "morning" || tokens[1] == "evening" || tokens[1] == "day")) return true
        return false
    }

    private fun extractDestination(text: String, arabic: Boolean, graph: MallGraph?): String? {
        val lower = text.lowercase()
        val triggers = if (arabic) NAV_TRIGGERS_AR else NAV_TRIGGERS_EN + NAV_TRIGGERS_AR

        for (trigger in triggers) {
            if (lower.contains(trigger)) {
                val afterTrigger = lower.substringAfter(trigger).trim()
                    .removePrefix("to").removePrefix("لـ").removePrefix("ل").trim()
                if (afterTrigger.isNotBlank()) {
                    val shopName = if (graph != null) fuzzyMatchShop(afterTrigger, graph) else afterTrigger
                    if (shopName != null) return shopName
                    if (afterTrigger.length >= 2) return afterTrigger
                }
            }
        }
        return null
    }

    fun fuzzyMatchShop(query: String, graph: MallGraph): String? {
        val q = query.trim().lowercase()
        val namedNodes = graph.nodes.filter { !it.shopName.isNullOrBlank() }

        namedNodes.firstOrNull { it.shopName!!.lowercase() == q }?.shopName?.let { return it }

        namedNodes.firstOrNull { n ->
            val name = n.shopName!!.lowercase()
            name.contains(q) || q.contains(name)
        }?.shopName?.let { return it }

        val qNoSpace = q.replace(" ", "")
        namedNodes.firstOrNull { n ->
            val name = n.shopName!!.lowercase().replace(" ", "")
            name.contains(qNoSpace) || qNoSpace.contains(name)
        }?.shopName?.let { return it }

        val best = namedNodes.minByOrNull { levenshtein(it.shopName!!.lowercase(), q) }
        if (best != null && levenshtein(best.shopName!!.lowercase(), q) <= 3) {
            return best.shopName
        }

        val queryTokens = q.split(" ", "،", ",").filter { it.length >= 2 }
        for (token in queryTokens) {
            namedNodes.firstOrNull { n ->
                val name = n.shopName!!.lowercase()
                name.contains(token) || token.contains(name)
            }?.shopName?.let { return it }
        }

        return null
    }

    fun findNodeByName(shopName: String, graph: MallGraph): GraphNode? {
        val lower = shopName.lowercase()
        return graph.nodes.firstOrNull { it.shopName?.lowercase() == lower }
            ?: graph.nodes.firstOrNull { it.shopName?.lowercase()?.contains(lower) == true }
    }

    private fun matchesAny(
        lowerText: String,
        arabic: Boolean,
        enTriggers: List<String>,
        arTriggers: List<String>
    ): Boolean {
        val triggers = if (arabic) arTriggers else enTriggers + arTriggers
        return triggers.any { lowerText.contains(it) }
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) { 0 } }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
            else minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]) + 1
        }
        return dp[a.length][b.length]
    }
}
