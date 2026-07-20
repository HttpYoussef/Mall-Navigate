package com.example.mallar.voice

import android.content.Context
import android.util.Log
import com.example.mallar.data.AStarPath
import com.example.mallar.data.MallGraph
import com.example.mallar.navigation.NavSessionState
import com.example.mallar.ui.localization.NavigationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "VoiceAssistantMgr"

/**
 * Coordinates offline STT → [LocalIntentParser] → [SmartResponseEngine] → [VoiceManager],
 * plus [NavigationVoiceController] for AR-style distance cues when wired from AR screens.
 */
class VoiceAssistantManager(private val context: Context) {

    private val sttManager = SpeechRecognitionManager(context)
    val ttsManager = VoiceManager(context)
    private val voiceController = NavigationVoiceController(ttsManager)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var errorResetJob: Job? = null

    private val _uiState = MutableStateFlow(VoiceAssistantUiState())
    val uiState: StateFlow<VoiceAssistantUiState> = _uiState.asStateFlow()

    var onNavigateTo: ((shopName: String, isArabic: Boolean) -> AStarPath?)? = null

    /**
     * When user specifies both start and end (e.g. "from Zara to Bershka").
     * Resolved canonical shop names from the graph.
     */
    var onNavigateWithOrigin: ((originShop: String, destShop: String, isArabic: Boolean) -> AStarPath?)? = null

    var onStopNavigation: (() -> Unit)? = null

    /** Provider for the current navigation state (replaces Singleton access). */
    var navStateProvider: (() -> NavSessionState?)? = null

    var graphProvider: (() -> MallGraph?)? = null

    private var preferArabic: Boolean = NavigationState.preferArabicVoice

    fun initialize() {
        ttsManager.init(
            language = NavigationLanguage.ARABIC,
            onReady = { Log.d(TAG, "TTS ready") },
            onError = { err -> Log.e(TAG, "TTS error: $err") }
        )
        sttManager.initialize()
        wireSTTCallbacks()
        Log.d(TAG, "VoiceAssistantManager initialized")
    }

    fun destroy() {
        sttManager.cancelListening()
        sttManager.destroy()
        ttsManager.shutdown()
        scope.coroutineContext[Job]?.cancel()
        ConversationContext.clearMemory()
        Log.d(TAG, "VoiceAssistantManager destroyed")
    }

    fun startListening() {
        if (_uiState.value.status == VoiceAssistantStatus.LISTENING) return
        ttsManager.stop()
        setStatus(VoiceAssistantStatus.LISTENING, transcript = "")
        sttManager.startListening(preferArabic = preferArabic)
        Log.d(TAG, "Started listening")
    }

    fun cancelListening() {
        sttManager.cancelListening()
        setStatus(VoiceAssistantStatus.IDLE)
    }

    fun speak(text: String, isArabic: Boolean = preferArabic) {
        val lang = if (isArabic) NavigationLanguage.ARABIC else NavigationLanguage.ENGLISH
        ttsManager.setLanguage(lang)
        ttsManager.speak(text)
    }

    fun getVoiceController(): NavigationVoiceController = voiceController

    private fun wireSTTCallbacks() {
        scope.launch {
            sttManager.partialResult.collect { partial ->
                if (partial.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(transcript = partial)
                }
            }
        }

        sttManager.onReadyForSpeech = {
            _uiState.value = _uiState.value.copy(
                status = VoiceAssistantStatus.LISTENING,
                transcript = ""
            )
        }

        sttManager.onResult = { transcript, isArabic, alternatives ->
            scope.launch { processTranscript(transcript, isArabic, alternatives) }
        }

        sttManager.onError = { code, message ->
            val isSilent = code == 7 || code == 6
            if (isSilent) {
                setStatus(VoiceAssistantStatus.IDLE)
            } else {
                setStatus(VoiceAssistantStatus.ERROR, errorMessage = message)
                autoResetAfterError()
            }
        }
    }

    private suspend fun processTranscript(
        transcript: String,
        isArabic: Boolean,
        alternatives: List<String> = emptyList()
    ) {
        val graph = graphProvider?.invoke()
        var intent = withContext(Dispatchers.Default) {
            LocalIntentParser.parse(transcript, graph)
        }

        // If top result was UNKNOWN, try alternatives before giving up
        if (intent.intent == VoiceIntent.UNKNOWN && alternatives.size > 1) {
            for (alt in alternatives.drop(1)) {
                val altIntent = withContext(Dispatchers.Default) {
                    LocalIntentParser.parse(alt, graph)
                }
                if (altIntent.intent != VoiceIntent.UNKNOWN) {
                    intent = altIntent
                    Log.d(TAG, "Alternative transcript used: \"$alt\"")
                    break
                }
            }
        }

        handleIntent(intent, graph)
    }

    private suspend fun handleIntent(intent: ParsedIntent, graph: MallGraph?) {
        val navState = navStateProvider?.invoke()

        when (intent.intent) {

            VoiceIntent.NAVIGATE_TO, VoiceIntent.WHERE_IS -> {
                val destName = intent.destination
                if (destName == null) {
                    speakReply(SmartResponseEngine.replyForIntent(intent, graph, navState), intent.isArabic)
                    return
                }

                val destNode = graph?.let { LocalIntentParser.findNodeByName(destName, it) }
                    ?: graph?.let { g -> fuzzyFindInGraph(destName, g) }

                if (destNode == null) {
                    speakReply(SmartResponseEngine.storeNotFound(destName, intent.isArabic), intent.isArabic)
                    return
                }

                val canonical = destNode.shopName ?: destName
                val originRaw = intent.origin?.trim()?.takeIf { it.isNotBlank() }

                var originLookupFailed = false
                val foundPath = withContext(Dispatchers.IO) {
                    if (originRaw != null && graph != null) {
                        val originNode = LocalIntentParser.findNodeByName(originRaw, graph)
                            ?: fuzzyFindInGraph(originRaw, graph)
                        if (originNode == null) {
                            originLookupFailed = true
                            null
                        } else {
                            val originCanon = originNode.shopName ?: originRaw
                            onNavigateWithOrigin?.invoke(originCanon, canonical, intent.isArabic)
                        }
                    } else {
                        onNavigateTo?.invoke(canonical, intent.isArabic)
                    }
                }

                if (originLookupFailed) {
                    speakReply(
                        if (intent.isArabic) "مش لاقي \"$originRaw\" كبداية للمسار. جرب اسم تاني."
                        else "I couldn't find \"$originRaw\" as the starting place. Try another name.",
                        intent.isArabic
                    )
                    return
                }

                if (foundPath == null) {
                    speakReply(
                        if (intent.isArabic) "مقدرش أحسب المسار. جرب تاني."
                        else "I couldn't compute a route. Please try again.",
                        intent.isArabic
                    )
                    return
                }

                ConversationContext.setDestination(canonical)
                ConversationContext.setNavigationActive(true)

                val reply = SmartResponseEngine.navigateStarted(
                    destination = canonical,
                    path = foundPath,
                    isArabic = intent.isArabic
                )
                speakReply(reply, intent.isArabic)
            }

            VoiceIntent.STOP_NAVIGATION -> {
                onStopNavigation?.invoke()
                ConversationContext.setNavigationActive(false)
                speakReply(SmartResponseEngine.stopNavigation(intent.isArabic), intent.isArabic)
            }

            VoiceIntent.REPEAT_DIRECTIONS -> {
                val lastNav = ConversationContext.lastNavigationInstruction
                val reply = if (ConversationContext.isNavigationActive && !lastNav.isNullOrBlank()) {
                    lastNav
                } else {
                    SmartResponseEngine.repeatDirections(navState, intent.isArabic)
                }
                speakReply(reply, intent.isArabic)
            }

            else -> {
                speakReply(SmartResponseEngine.replyForIntent(intent, graph, navState), intent.isArabic)
            }
        }
    }

    fun onNavigationArrived(destinationName: String, isArabic: Boolean) {
        ConversationContext.setNavigationActive(false)
        speak(SmartResponseEngine.arrived(destinationName, isArabic), isArabic)
    }

    fun onRerouteStarted(isArabic: Boolean) {
        speak(SmartResponseEngine.rerouting(isArabic), isArabic)
    }

    private fun speakReply(text: String, isArabic: Boolean) {
        setStatus(VoiceAssistantStatus.SPEAKING, assistantReply = text)
        speak(text, isArabic)

        val estimatedDurationMs = (text.length * 55L).coerceIn(1500L, 8000L)
        scope.launch {
            delay(estimatedDurationMs)
            if (_uiState.value.status == VoiceAssistantStatus.SPEAKING) {
                setStatus(VoiceAssistantStatus.IDLE)
            }
        }
    }

    private fun setStatus(
        status: VoiceAssistantStatus,
        transcript: String = _uiState.value.transcript,
        assistantReply: String = _uiState.value.assistantReply,
        errorMessage: String? = null
    ) {
        _uiState.value = VoiceAssistantUiState(
            status = status,
            transcript = transcript,
            assistantReply = assistantReply,
            isArabic = preferArabic,
            errorMessage = errorMessage
        )
    }

    private fun autoResetAfterError() {
        errorResetJob?.cancel()
        errorResetJob = scope.launch {
            delay(2500)
            setStatus(VoiceAssistantStatus.IDLE)
        }
    }

    private fun fuzzyFindInGraph(name: String, graph: MallGraph) =
        graph.nodes.filter { !it.shopName.isNullOrBlank() }
            .minByOrNull { node ->
                val n = node.shopName!!.lowercase()
                val q = name.lowercase()
                if (n.contains(q) || q.contains(n)) 0
                else levenshtein(n, q)
            }?.takeIf { node ->
                val n = node.shopName!!.lowercase()
                val q = name.lowercase()
                n.contains(q) || q.contains(n) || levenshtein(n, q) <= 3
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
