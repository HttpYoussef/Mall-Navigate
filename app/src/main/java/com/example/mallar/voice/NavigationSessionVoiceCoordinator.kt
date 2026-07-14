package com.example.mallar.voice

import com.example.mallar.navigation.NavSessionState
import com.example.mallar.overlay.OverlayTurnDirection
import com.example.mallar.ui.localization.NavigationState

/**
 * Live turn guidance driven by [NavSessionState] (map + AR). Offline, no APIs.
 */
class NavigationSessionVoiceCoordinator(
    private val voice: VoiceManager,
    private val preferArabic: () -> Boolean = { NavigationState.preferArabicVoice }
) {

    private var tripStartAnnounced = false
    private var rerouteInProgress = false
    private var reroutingPhraseSpoken = false
    private var arrivedAnnounced = false
    private val approachKeys = mutableSetOf<String>()
    private val nowKeys = mutableSetOf<String>()
    private var lastSpeakMs = 0L

    fun reset() {
        tripStartAnnounced = false
        rerouteInProgress = false
        reroutingPhraseSpoken = false
        arrivedAnnounced = false
        approachKeys.clear()
        nowKeys.clear()
        lastSpeakMs = 0L
    }

    fun onSessionState(state: NavSessionState) {
        if (!voice.isReady || !voice.isEnabled) return
        if (state.pathNodes.size < 2) return

        val ar = preferArabic()
        voice.setLanguage(if (ar) NavigationLanguage.ARABIC else NavigationLanguage.ENGLISH)

        if (state.isArrived) {
            if (!arrivedAnnounced) {
                arrivedAnnounced = true
                speak(SmartResponseEngine.arrived(state.destinationName, ar), force = true)
            }
            return
        }

        if (state.isRerouting) {
            if (!reroutingPhraseSpoken) {
                reroutingPhraseSpoken = true
                speak(SmartResponseEngine.rerouting(ar), force = true)
            }
            rerouteInProgress = true
            return
        }

        if (rerouteInProgress) {
            rerouteInProgress = false
            reroutingPhraseSpoken = false
            approachKeys.clear()
            nowKeys.clear()
            speak(SmartResponseEngine.rerouteComplete(ar), force = true)
        }

        if (!tripStartAnnounced) {
            tripStartAnnounced = true
            val dist = state.remainingDistanceM.coerceAtLeast(1)
            val msg = SmartResponseEngine.tripStartedShort(
                destination = state.destinationName.takeIf { it.isNotBlank() },
                distM = dist,
                isArabic = ar
            )
            speak(msg, force = true)
            ConversationContext.setNavigationActive(true)
            ConversationContext.setDestination(state.destinationName.takeIf { it.isNotBlank() })
        }

        val ti = state.turnInfo ?: return
        val dir = ti.direction
        val dist = state.remainingDistanceM
        val seg = state.segmentIdx

        val approachKey = "${seg}_${dir}_A"
        if (dir != OverlayTurnDirection.U_TURN && dist in 5..14) {
            if (approachKey !in approachKeys && canSpeak(4200L)) {
                approachKeys.add(approachKey)
                speak(SmartResponseEngine.turnApproach(dir, dist, ar), force = false)
            }
        }

        val nowKey = "${seg}_${dir}_N"
        if (dir != OverlayTurnDirection.U_TURN && dist <= 4) {
            if (nowKey !in nowKeys && canSpeak(3200L)) {
                nowKeys.add(nowKey)
                speak(SmartResponseEngine.turnNow(dir, ar), force = false)
            }
        }
    }

    private fun canSpeak(minGapMs: Long): Boolean =
        System.currentTimeMillis() - lastSpeakMs >= minGapMs

    private fun speak(text: String, force: Boolean) {
        if (text.isBlank()) return
        lastSpeakMs = System.currentTimeMillis()
        ConversationContext.recordNavigationInstruction(text)
        voice.speak(text, minCooldownMs = 0L, force = force)
    }
}
