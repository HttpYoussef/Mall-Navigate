package com.example.mallar.voice

import android.util.Log
import com.example.mallar.data.AStarDirection
import com.example.mallar.data.AStarPath
import com.example.mallar.data.GraphNode
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "NavVoiceController"

// ── Tuning constants ──────────────────────────────────────────────────────────

/** Pixel → metre scale (must match AR_SCALE in ArCoordinateTransformer). */
private const val PX_TO_M = 0.05f

/** Minimum time (ms) between any two voice announcements. */
private const val VOICE_COOLDOWN_MS = 4_000L

/**
 * Distance in metres at which we give the "approaching" pre-announcement
 * (e.g. "Turn right in 5 metres").
 */
private const val APPROACH_ANNOUNCE_M = 6f

/**
 * Distance in metres at which we give the "now" announcement
 * (e.g. "Turn right").
 */
private const val NOW_ANNOUNCE_M = 2.5f

/** Arrival threshold in metres (mirrors ArrowSceneManager.ARRIVAL_THRESHOLD_M). */
private const val ARRIVAL_M = 1.5f

// ─────────────────────────────────────────────────────────────────────────────

/**
 * Drives Google-Maps–style voice navigation.
 *
 * **Responsibilities:**
 *  1. Determine the current manoeuvre from the A* [AStarPath].
 *  2. Measure real distance between the user and the next waypoint.
 *  3. Fire exactly the right announcement at the right time using [VoiceManager].
 *
 * **Usage:**
 * ```kotlin
 * val controller = NavigationVoiceController(voiceManager)
 * controller.onPathLoaded(pathNodes, aStarPath)
 *
 * // Called every AR frame (or whenever user position updates):
 * controller.update(camArX, camArZ, currentTargetIndex)
 *
 * // Called when the AR system advances to the next waypoint:
 * controller.onTargetIndexChanged(newIndex, pathNodes, aStarPath)
 *
 * // Clean up:
 * controller.reset()
 * ```
 *
 * @param voice  The shared [VoiceManager] instance.
 */
class NavigationVoiceController(private val voice: VoiceManager) {

    // ── Internal state ────────────────────────────────────────────────────────

    private var pathNodes: List<GraphNode> = emptyList()
    private var aStarPath: AStarPath?      = null

    /**
     * Tracks which announcement phase we are in for the *current* target node.
     *  NONE     — nothing announced yet (or index just changed)
     *  APPROACH — "Turn right in N metres" was already said
     *  NOW      — "Turn right" (at the turn) was already said
     *  ARRIVED  — destination reached
     */
    private enum class Phase { NONE, APPROACH, NOW, ARRIVED }

    private var phase = Phase.NONE

    /** Index of the node the user is currently heading toward. */
    private var lastTargetIndex = -1

    /** Timestamp of the last call to [voice].speak(). */
    private var lastSpeakMs = 0L

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called once when the path is known (before navigation starts).
     * Optionally speaks a welcome/start announcement.
     */
    fun onPathLoaded(
        nodes: List<GraphNode>,
        path: AStarPath?,
        speakGreeting: Boolean = true
    ) {
        pathNodes      = nodes
        aStarPath      = path
        phase          = Phase.NONE
        lastTargetIndex = -1

        if (speakGreeting && nodes.size >= 2) {
            val dest = nodes.last().shopName
            val distM = ((path?.totalDistancePx ?: 0.0) * PX_TO_M).roundToInt()
            val greeting = buildGreetingText(dest, distM)
            speakNow(greeting, force = true)
        }
    }

    /**
     * Main update loop — call this every AR frame (or whenever the camera
     * position changes).  Handles distance-threshold announcements automatically.
     *
     * @param camArX          Camera X position in AR local space (metres, relative to anchor).
     * @param camArZ          Camera Z position in AR local space (metres, relative to anchor).
     * @param currentTargetIdx  Index of the node the user is currently heading toward.
     * @param anchorMapX      Start-node map X, used to convert graph coords to AR coords.
     * @param anchorMapY      Start-node map Y, used to convert graph coords to AR coords.
     */
    fun update(
        camArX: Float,
        camArZ: Float,
        currentTargetIdx: Int,
        anchorMapX: Double,
        anchorMapY: Double
    ) {
        if (!voice.isReady || pathNodes.isEmpty()) return
        if (phase == Phase.ARRIVED) return

        // ── Target changed externally? ────────────────────────────────────────
        if (currentTargetIdx != lastTargetIndex) {
            onTargetIndexChangedInternal(currentTargetIdx)
        }

        val nextNode = pathNodes.getOrNull(currentTargetIdx) ?: return

        // ── Compute real distance camera → next node (AR metres) ──────────────
        val nodeArX = (nextNode.x.toFloat() - anchorMapX.toFloat()) * PX_TO_M
        val nodeArZ = (nextNode.y.toFloat() - anchorMapY.toFloat()) * PX_TO_M
        val dist    = horizontalDist(camArX, camArZ, nodeArX, nodeArZ)

        Log.v(TAG, "dist to node[$currentTargetIdx] = ${"%.2f".format(dist)} m  phase=$phase")

        // ── Arrival check ─────────────────────────────────────────────────────
        if (dist <= ARRIVAL_M && currentTargetIdx >= pathNodes.size - 1) {
            if (phase != Phase.ARRIVED) {
                phase = Phase.ARRIVED
                speakNow(arrivedText(), force = true)
            }
            return
        }

        // ── Determine the NEXT manoeuvre (direction at next node) ─────────────
        val direction = directionAtNode(currentTargetIdx)

        // ── Approach announcement ("Turn right in N metres") ──────────────────
        if (phase == Phase.NONE && dist <= APPROACH_ANNOUNCE_M && dist > NOW_ANNOUNCE_M) {
            val distRounded = dist.roundToInt().coerceAtLeast(1)
            val text = buildApproachText(direction, distRounded)
            if (speakWithCooldown(text)) {
                phase = Phase.APPROACH
            }
        }

        // ── Now announcement ("Turn right") ───────────────────────────────────
        if ((phase == Phase.NONE || phase == Phase.APPROACH) && dist <= NOW_ANNOUNCE_M) {
            val text = buildNowText(direction)
            if (speakWithCooldown(text)) {
                phase = Phase.NOW
            }
        }
    }

    /**
     * Notify the controller that [ArrowSceneManager] has advanced to a new
     * target index.  Resets the announcement phase so fresh instructions
     * are given for the new segment.
     */
    fun onTargetIndexChanged(
        newIndex: Int,
        nodes: List<GraphNode> = pathNodes,
        path: AStarPath?       = aStarPath
    ) {
        pathNodes = nodes
        aStarPath = path
        onTargetIndexChangedInternal(newIndex)
    }

    /** Immediately speak a custom text (e.g. "Reached Zara"). */
    fun announceWaypoint(waypointName: String) {
        val text = buildWaypointText(waypointName)
        speakNow(text, force = false)
    }

    /** Reset all state (e.g. when navigation ends). */
    fun reset() {
        phase           = Phase.NONE
        lastTargetIndex = -1
        pathNodes       = emptyList()
        aStarPath       = null
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun onTargetIndexChangedInternal(newIndex: Int) {
        Log.d(TAG, "Target index changed: $lastTargetIndex → $newIndex")
        lastTargetIndex = newIndex
        // Reset phase so we get fresh announcements for the new segment
        phase = Phase.NONE
    }

    /**
     * Determine the [AStarDirection] that the user should execute *at*
     * [nodeIndex].  Uses the pre-computed A* instructions.
     *
     * Falls back to computing it geometrically from pathNodes if the
     * instruction list is unavailable.
     */
    private fun directionAtNode(nodeIndex: Int): AStarDirection {
        // ── Try A* instruction list first ─────────────────────────────────────
        val steps = aStarPath?.steps
        if (!steps.isNullOrEmpty()) {
            // Find the instruction whose nodeIndex most closely precedes or equals ours
            val instr = steps
                .filter { it.nodeIndex <= nodeIndex && it.direction != AStarDirection.ARRIVED }
                .maxByOrNull { it.nodeIndex }
            if (instr != null) return instr.direction
        }

        // ── Geometric fallback ────────────────────────────────────────────────
        if (nodeIndex < 1 || nodeIndex >= pathNodes.size - 1) return AStarDirection.STRAIGHT
        val prev    = pathNodes[nodeIndex - 1]
        val current = pathNodes[nodeIndex]
        val next    = pathNodes[nodeIndex + 1]
        return geometricDirection(prev, current, next)
    }

    /**
     * Compute turn direction using cross product / dot product on map coords.
     * Threshold = 30°, matching the A* builder in [MallGraphRepository].
     */
    private fun geometricDirection(
        prev: GraphNode,
        cur: GraphNode,
        next: GraphNode
    ): AStarDirection {
        val v1x = cur.x  - prev.x;  val v1y = cur.y  - prev.y
        val v2x = next.x - cur.x;   val v2y = next.y - cur.y
        val cross = v1x * v2y - v1y * v2x
        val dot   = v1x * v2x + v1y * v2y
        val angle = Math.toDegrees(Math.atan2(cross, dot))
        return when {
            angle >  30.0 -> AStarDirection.RIGHT
            angle < -30.0 -> AStarDirection.LEFT
            else          -> AStarDirection.STRAIGHT
        }
    }

    private fun horizontalDist(x1: Float, z1: Float, x2: Float, z2: Float): Float {
        val dx = x1 - x2; val dz = z1 - z2
        return sqrt(dx * dx + dz * dz)
    }

    /** Speak with global cooldown. Returns true if speech was triggered. */
    private fun speakWithCooldown(text: String): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastSpeakMs < VOICE_COOLDOWN_MS) return false
        lastSpeakMs = now
        ConversationContext.recordNavigationInstruction(text)
        voice.speak(text)
        return true
    }

    /** Speak immediately, bypassing cooldown. */
    private fun speakNow(text: String, force: Boolean = false) {
        lastSpeakMs = System.currentTimeMillis()
        ConversationContext.recordNavigationInstruction(text)
        voice.speak(text, force = force)
    }

    // ── Text builders (bilingual) ─────────────────────────────────────────────

    private fun buildGreetingText(destination: String?, distM: Int): String {
        val idx = ConversationContext.nextVariationIndex(4)
        return when (voice.language) {
            NavigationLanguage.ARABIC -> {
                val opts = listOf(
                    "بدأنا${if (destination != null) " التوجيه إلى $destination" else " التوجيه"}. المسافة الإجمالية $distM متر.",
                    "يلا بينا${if (destination != null) " على $destination" else ""} — حوالي $distM متر.",
                    "تمام${if (destination != null) "، هنوصل $destination" else ""}. المسافة $distM متر.",
                    "شغّالين${if (destination != null) " لحد $destination" else ""}. تقريباً $distM متر."
                )
                opts[idx]
            }
            NavigationLanguage.ENGLISH -> {
                val opts = listOf(
                    "Navigation started${if (destination != null) " to $destination" else ""}. Total distance: $distM metres.",
                    "Let's go${if (destination != null) " to $destination" else ""} — about $distM metres.",
                    "You're on your way${if (destination != null) " to $destination" else ""}. Roughly $distM metres.",
                    "Route active${if (destination != null) " toward $destination" else ""}. $distM metres total."
                )
                opts[idx]
            }
        }
    }

    private fun buildApproachText(direction: AStarDirection, distM: Int): String {
        val idx = ConversationContext.nextVariationIndex(3)
        return when (voice.language) {
            NavigationLanguage.ARABIC -> when (direction) {
                AStarDirection.RIGHT -> listOf(
                    "اتجه يميناً بعد $distM متر",
                    "لف يمين بعد حوالي $distM متر",
                    "خد يمين بعد $distM متر"
                )[idx]
                AStarDirection.LEFT -> listOf(
                    "اتجه يساراً بعد $distM متر",
                    "لف يسار بعد حوالي $distM متر",
                    "خد شمال بعد $distM متر"
                )[idx]
                AStarDirection.STRAIGHT -> listOf(
                    "استمر للأمام لمسافة $distM متر",
                    "كمل على طول حوالي $distM متر",
                    "امشي عدل $distM متر"
                )[idx]
                AStarDirection.ARRIVED -> arrivedText()
            }
            NavigationLanguage.ENGLISH -> when (direction) {
                AStarDirection.RIGHT -> listOf(
                    "Turn right in $distM metres",
                    "Bear right in about $distM metres",
                    "Right turn in $distM metres"
                )[idx]
                AStarDirection.LEFT -> listOf(
                    "Turn left in $distM metres",
                    "Bear left in about $distM metres",
                    "Left turn in $distM metres"
                )[idx]
                AStarDirection.STRAIGHT -> listOf(
                    "Continue straight for $distM metres",
                    "Go straight for about $distM metres",
                    "Keep straight for $distM metres"
                )[idx]
                AStarDirection.ARRIVED -> arrivedText()
            }
        }
    }

    private fun buildNowText(direction: AStarDirection): String {
        val idx = ConversationContext.nextVariationIndex(3)
        return when (voice.language) {
            NavigationLanguage.ARABIC -> when (direction) {
                AStarDirection.RIGHT -> listOf("اتجه يميناً الآن", "يمين", "لف يمين دلوقتي")[idx]
                AStarDirection.LEFT -> listOf("اتجه يساراً الآن", "يسار", "لف يسار دلوقتي")[idx]
                AStarDirection.STRAIGHT -> listOf("استمر للأمام", "كمل على طول", "امشي عدل")[idx]
                AStarDirection.ARRIVED -> arrivedText()
            }
            NavigationLanguage.ENGLISH -> when (direction) {
                AStarDirection.RIGHT -> listOf("Turn right now", "Right here", "Take the right")[idx]
                AStarDirection.LEFT -> listOf("Turn left now", "Left here", "Take the left")[idx]
                AStarDirection.STRAIGHT -> listOf("Go straight", "Continue ahead", "Keep going straight")[idx]
                AStarDirection.ARRIVED -> arrivedText()
            }
        }
    }

    private fun buildWaypointText(name: String): String =
        when (voice.language) {
            NavigationLanguage.ARABIC  -> "وصلت إلى $name"
            NavigationLanguage.ENGLISH -> "Reached $name"
        }

    private fun arrivedText(): String =
        when (voice.language) {
            NavigationLanguage.ARABIC  -> "لقد وصلت إلى وجهتك"
            NavigationLanguage.ENGLISH -> "You have arrived at your destination"
        }
}
