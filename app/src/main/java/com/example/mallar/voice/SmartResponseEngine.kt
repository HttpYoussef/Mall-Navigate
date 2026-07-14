package com.example.mallar.voice

import com.example.mallar.data.AStarPath
import com.example.mallar.data.MallGraph
import com.example.mallar.navigation.NavSessionState
import com.example.mallar.overlay.OverlayTurnDirection
import com.example.mallar.ui.localization.NavigationState
import kotlin.math.roundToInt

/**
 * Offline response generation: template pools + light context.
 * Replaces fixed single-line replies with varied, conversational phrasing.
 */
object SmartResponseEngine {

    private const val PX_TO_M = 1f / 4.48f

    private fun pick(list: List<String>): String =
        list[ConversationContext.nextVariationIndex(list.size)]

    // ── Greeting / help ───────────────────────────────────────────────────────

    fun greeting(isArabic: Boolean): String = if (isArabic) {
        pick(
            listOf(
                "أهلاً! فين ناوي تروح النهاردة؟",
                "مرحباً! قولي عايز توصل فين.",
                "هلا! تحب أوصلك لمكان معين؟",
                "أهلاً بيك! فين وجهتك؟"
            )
        )
    } else {
        pick(
            listOf(
                "Hi! Where would you like to go today?",
                "Hey there! Tell me where you're headed.",
                "Hello! What destination can I help you find?",
                "Hi! Ready when you are — where to?"
            )
        )
    }

    fun help(isArabic: Boolean): String = if (isArabic) {
        pick(
            listOf(
                "تقدر تقولي مثلاً: «وديني زارا»، «وقف التنقل»، «كام باقي؟»، «كرر»، أو «فين أنا؟».",
                "جرب: «خدني المحل الفلاني»، «كم دقيقة»، أو «وقف» لو عايز توقف التوجيه.",
                "أنا هنا للتنقل جوه المول: قول وجهتك، أو اسأل عن المسافة والوقت."
            )
        )
    } else {
        pick(
            listOf(
                "Try things like: \"Take me to Zara\", \"Stop navigation\", \"How far?\", \"Repeat\", or \"Where am I?\".",
                "I handle indoor directions — say a store name, ask how far, or ask for your ETA.",
                "Say a destination to start, or ask \"repeat\" if you missed the last instruction."
            )
        )
    }

    // ── Trip lifecycle ─────────────────────────────────────────────────────────

    fun navigateStarted(destination: String, path: AStarPath?, isArabic: Boolean): String {
        ConversationContext.setDestination(destination)
        val distM = path?.let { (it.totalDistancePx * PX_TO_M).roundToInt() }
        val mins = distM?.let { (it / 80f).roundToInt().coerceAtLeast(1) }
        return if (isArabic) {
            when {
                distM != null && mins != null -> pick(
                    listOf(
                        "تمام، بدأت التنقل إلى $destination. تقريباً $distM متر — حوالي $mins دقيقة.",
                        "يلا بينا على $destination! المسافة حوالي $distM متر.",
                        "تمام! أنا معاك لحد $destination — تقريباً $mins دقيقة مشي.",
                        "تمام، شغّالتلك المسار لـ $destination. اتبع التوجيهات."
                    )
                )
                else -> pick(
                    listOf(
                        "تمام، بدأت التنقل إلى $destination.",
                        "تمام، يلا على $destination.",
                        "حاضر، هنوصلك لـ $destination."
                    )
                )
            }
        } else {
            when {
                distM != null && mins != null -> pick(
                    listOf(
                        "Okay, navigating to $destination. About $distM metres — roughly $mins minute${if (mins == 1) "" else "s"}.",
                        "Got it — heading to $destination. Around $distM metres to go.",
                        "On it! Follow me to $destination — about $mins minute${if (mins == 1) "" else "s"} on foot.",
                        "Starting navigation to $destination now."
                    )
                )
                else -> pick(
                    listOf(
                        "Okay, let's go to $destination.",
                        "Starting navigation to $destination.",
                        "Got it — follow the route to $destination."
                    )
                )
            }
        }
    }

    fun stopNavigation(isArabic: Boolean): String = if (isArabic) {
        pick(
            listOf(
                "تمام، وقفنا التنقل.",
                "خلاص، إلغينا التوجيه.",
                "تمام! لو محتاج حاجة تاني قولي."
            )
        )
    } else {
        pick(
            listOf(
                "Okay, navigation stopped.",
                "Done — I've ended the route.",
                "Stopped. Just say if you need anything else."
            )
        )
    }

    fun rerouting(isArabic: Boolean): String = if (isArabic) {
        pick(listOf("جاري إعادة حساب المسار…", "ثانية، بظبطلك المسار من تاني…", "تمام، بحدّث المسار…"))
    } else {
        pick(listOf("Recalculating your route…", "Updating your path…", "Rerouting…"))
    }

    fun rerouteComplete(isArabic: Boolean): String = if (isArabic) {
        pick(listOf("تمام، المسار اتحدث. كمل على التوجيهات.", "خلاص، المسار الجديد جاهز.", "تمام، اتبع المسار الجديد."))
    } else {
        pick(listOf("Route updated — follow the new directions.", "New path ready. Keep going.", "Updated! Follow the refreshed route."))
    }

    fun arrived(destination: String, isArabic: Boolean): String = if (isArabic) {
        pick(
            listOf(
                "وصلت! أنت عند $destination.",
                "خلاص وصلنا $destination. بالهنا!",
                "وصلت لـ $destination — استمتع!"
            )
        )
    } else {
        pick(
            listOf(
                "You've arrived at $destination!",
                "Here we are — welcome to $destination.",
                "You made it! Enjoy $destination."
            )
        )
    }

    fun tripStartedShort(destination: String?, distM: Int, isArabic: Boolean): String =
        if (isArabic) {
            pick(
                listOf(
                    "بدأنا التوجيه${destSuffixAr(destination)}. المسافة الإجمالية حوالي $distM متر.",
                    "يلا${destSuffixAr(destination)} — تقريباً $distM متر.",
                    "تمام${destSuffixAr(destination)}. اتبع الصوت والخريطة."
                )
            )
        } else {
            pick(
                listOf(
                    "Navigation started${destSuffixEn(destination)}. About $distM metres in total.",
                    "Let's go${destSuffixEn(destination)} — roughly $distM metres.",
                    "You're on your way${destSuffixEn(destination)}. Follow the prompts."
                )
            )
        }

    private fun destSuffixAr(d: String?) = if (d.isNullOrBlank()) "" else " لـ $d"
    private fun destSuffixEn(d: String?) = if (d.isNullOrBlank()) "" else " to $d"

    // ── Queries while navigating ────────────────────────────────────────────────

    fun repeatDirections(navState: NavSessionState?, isArabic: Boolean): String {
        val dest = navState?.destinationName?.takeIf { it.isNotBlank() }
            ?: ConversationContext.currentDestinationName
            ?: NavigationState.selectedPlace?.brand
        val distM = navState?.remainingDistanceM ?: 0
        val mins = navState?.walkMinutes ?: 0
        return if (dest != null && (navState?.pathNodes?.size ?: 0) >= 2) {
            if (isArabic) {
                pick(
                    listOf(
                        "أنت في طريقك إلى $dest. باقي حوالي $distM متر — تقريباً $mins دقيقة.",
                        "لسه رايح $dest — $distM متر تقريباً.",
                        "المسار لـ $dest: باقي $distM متر."
                    )
                )
            } else {
                pick(
                    listOf(
                        "You're heading to $dest. About $distM metres left — roughly $mins minute${if (mins == 1) "" else "s"}.",
                        "Still on route to $dest — $distM metres to go.",
                        "Your route to $dest: $distM metres remaining."
                    )
                )
            }
        } else {
            if (isArabic) "مفيش توجيه نشط. قولي هتروح فين؟"
            else "No active navigation. Where would you like to go?"
        }
    }

    fun remainingDistance(navState: NavSessionState?, isArabic: Boolean): String {
        val distM = navState?.remainingDistanceM ?: 0
        val dest = navState?.destinationName?.takeIf { it.isNotBlank() }
            ?: ConversationContext.currentDestinationName
        return if (navState != null && distM > 0 && navState.pathNodes.size >= 2) {
            if (isArabic) {
                pick(
                    listOf(
                        "باقي حوالي $distM متر${if (dest != null) " لـ $dest" else ""}.",
                        "لسه $distM متر${if (dest != null) " لحد $dest" else ""}.",
                        "المتبقي تقريباً $distM متر."
                    )
                )
            } else {
                pick(
                    listOf(
                        "About $distM metres left${if (dest != null) " to $dest" else ""}.",
                        "Roughly $distM metres to go.",
                        "You have around $distM metres remaining."
                    )
                )
            }
        } else {
            if (isArabic) "مفيش رحلة نشطة دلوقتي."
            else "No active trip right now."
        }
    }

    fun eta(navState: NavSessionState?, isArabic: Boolean): String {
        val mins = navState?.walkMinutes?.coerceAtLeast(1) ?: 0
        val dest = navState?.destinationName?.takeIf { it.isNotBlank() }
            ?: ConversationContext.currentDestinationName
        return if (navState != null && mins > 0 && navState.pathNodes.size >= 2) {
            if (isArabic) {
                pick(
                    listOf(
                        "هتوصل${if (dest != null) " لـ $dest" else ""} في حوالي $mins دقيقة.",
                        "تقريباً $mins دقيقة${if (dest != null) " لحد $dest" else ""}.",
                        "متوقع $mins دقيقة مشي."
                    )
                )
            } else {
                pick(
                    listOf(
                        "You'll arrive${if (dest != null) " at $dest" else ""} in about $mins minute${if (mins == 1) "" else "s"}.",
                        "Roughly $mins minute${if (mins == 1) "" else "s"} to go.",
                        "ETA is about $mins minute${if (mins == 1) "" else "s"}."
                    )
                )
            }
        } else {
            if (isArabic) "مفيش رحلة نشطة دلوقتي."
            else "No active trip right now."
        }
    }

    fun currentLocation(navState: NavSessionState?, isArabic: Boolean): String {
        val start = NavigationState.startPlace?.brand
        val dest = navState?.destinationName?.takeIf { it.isNotBlank() }
            ?: ConversationContext.currentDestinationName
        val distM = navState?.remainingDistanceM ?: 0
        return when {
            start != null && dest != null -> {
                if (isArabic) pick(
                    listOf(
                        "أنت قريب من $start في طريقك إلى $dest. باقي حوالي $distM متر.",
                        "مشيت من عند $start رايح $dest — لسه $distM متر تقريباً."
                    )
                ) else pick(
                    listOf(
                        "You're near $start, heading to $dest. About $distM metres left.",
                        "From $start toward $dest — roughly $distM metres remaining."
                    )
                )
            }
            start != null -> {
                if (isArabic) pick(
                    listOf("أنت قريب من $start في المول.", "مكانيش بالظبط، بس قريب من $start.")
                ) else pick(
                    listOf("You're near $start in the mall.", "You're around $start.")
                )
            }
            else -> {
                if (isArabic) "مش عارف مكانك بالظبط. امسح لوجو محل قريب منك لو سمحت."
                else "I can't pinpoint you yet. Scan a nearby store logo to lock your position."
            }
        }
    }

    fun unknown(isArabic: Boolean): String = if (isArabic) {
        pick(
            listOf(
                "مش فاهم قصدك. جرب تقول اسم المحل.",
                "ممكن تعيدها؟ أو قولي «وديني» وبعدها اسم المحل.",
                "قولي فين عايز تروح بالاسم."
            )
        )
    } else {
        pick(
            listOf(
                "I'm not sure I caught that. Try a store name, or \"Take me to …\".",
                "Could you rephrase? For example: \"Navigate to Adidas\".",
                "Tell me where you'd like to go."
            )
        )
    }

    fun missingDestination(isArabic: Boolean): String =
        if (isArabic) pick(listOf("عايز تروح فين بالضبط؟", "قولي اسم المحل.", "فين وجهتك؟"))
        else pick(listOf("Where would you like to go?", "Which store?", "Tell me the destination name."))

    fun storeNotFound(name: String, isArabic: Boolean): String =
        if (isArabic) "مش لاقي \"$name\" في المول. جرب اسم تاني."
        else "I couldn't find \"$name\" in the mall. Try another name."

    // ── Live turn phrases (session voice coordinator) ───────────────────────────

    fun turnApproach(direction: OverlayTurnDirection, distM: Int, isArabic: Boolean): String {
        if (direction == OverlayTurnDirection.STRAIGHT || direction == OverlayTurnDirection.U_TURN) {
            return straightApproach(distM, isArabic)
        }
        val isLeft = direction == OverlayTurnDirection.LEFT
        return if (isArabic) {
            if (isLeft) pick(listOf("لف يسار بعد حوالي $distM متر", "خد شمال بعد $distM متر", "اتجه يساراً بعد $distM متر"))
            else pick(listOf("لف يمين بعد حوالي $distM متر", "خد يمين بعد $distM متر", "اتجه يميناً بعد $distM متر"))
        } else {
            if (isLeft) pick(listOf("Turn left in about $distM metres", "Left turn coming in $distM metres", "Bear left in $distM metres"))
            else pick(listOf("Turn right in about $distM metres", "Right turn coming in $distM metres", "Bear right in $distM metres"))
        }
    }

    fun turnNow(direction: OverlayTurnDirection, isArabic: Boolean): String {
        if (direction == OverlayTurnDirection.STRAIGHT || direction == OverlayTurnDirection.U_TURN) {
            return straightNow(isArabic)
        }
        val isLeft = direction == OverlayTurnDirection.LEFT
        return if (isArabic) {
            if (isLeft) pick(listOf("لف يسار دلوقتي", "يسار", "اتجه يساراً الآن"))
            else pick(listOf("لف يمين دلوقتي", "يمين", "اتجه يميناً الآن", "وجهتك على اليمين"))
        } else {
            if (isLeft) pick(listOf("Turn left now", "Left here", "Take the left"))
            else pick(listOf("Turn right now", "Right here", "Take the right"))
        }
    }

    private fun straightApproach(distM: Int, isArabic: Boolean): String =
        if (isArabic) pick(listOf("كمل على طول حوالي $distM متر", "امشي عدل $distM متر", "استمر للأمام $distM متر"))
        else pick(listOf("Continue straight for about $distM metres", "Keep going straight — $distM metres", "Stay on this corridor"))

    private fun straightNow(isArabic: Boolean): String =
        if (isArabic) pick(listOf("كمل على طول", "امشي عدل", "استمر للأمام"))
        else pick(listOf("Continue straight", "Keep going", "Head straight"))

    /**
     * Main dispatcher from parsed intent → spoken text.
     */
    fun replyForIntent(
        intent: ParsedIntent,
        graph: MallGraph?,
        navState: NavSessionState?,
        foundPath: AStarPath? = null
    ): String = when (intent.intent) {
        VoiceIntent.GREETING -> greeting(intent.isArabic)
        VoiceIntent.HELP -> help(intent.isArabic)
        VoiceIntent.NAVIGATE_TO, VoiceIntent.WHERE_IS -> {
            val dest = intent.destination ?: ConversationContext.currentDestinationName
            if (dest == null) missingDestination(intent.isArabic)
            else navigateStarted(dest, foundPath, intent.isArabic)
        }
        VoiceIntent.REPEAT_DIRECTIONS -> repeatDirections(navState, intent.isArabic)
        VoiceIntent.REMAINING_DISTANCE -> remainingDistance(navState, intent.isArabic)
        VoiceIntent.ETA -> eta(navState, intent.isArabic)
        VoiceIntent.CURRENT_LOCATION -> currentLocation(navState, intent.isArabic)
        VoiceIntent.STOP_NAVIGATION -> stopNavigation(intent.isArabic)
        VoiceIntent.CANCEL -> if (intent.isArabic) "تمام." else "Okay."
        VoiceIntent.UNKNOWN -> unknown(intent.isArabic)
    }
}
