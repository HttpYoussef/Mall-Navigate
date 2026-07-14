package com.example.mallar.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "SpeechRecognitionMgr"

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * SpeechRecognitionManager
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Uses Android's built-in SpeechRecognizer (backed by Google's STT service)
 * which provides:
 *  • Arabic (ar-EG Egyptian dialect) support
 *  • English (en-US) support
 *  • Real-time partial results (streaming feel)
 *  • No API key required — free, on-device routing to Google STT
 *  • Low latency (~500ms round trip on Wi-Fi)
 *
 * WHY Android SpeechRecognizer over alternatives:
 *  ┌─────────────────┬──────────────┬───────────┬──────────────┬──────────┐
 *  │ Option          │ Arabic       │ Latency   │ Cost         │ Offline? │
 *  ├─────────────────┼──────────────┼───────────┼──────────────┼──────────┤
 *  │ Android STT     │ ✅ Egyptian  │ ~0.5s     │ Free         │ Partial  │
 *  │ Google Cloud    │ ✅ Egyptian  │ ~0.3s     │ $0.006/min   │ No       │
 *  │ Whisper (local) │ ✅ Good      │ 2-5s      │ Free         │ Yes      │
 *  │ Azure Speech    │ ✅ Egyptian  │ ~0.4s     │ $1/hr        │ No       │
 *  │ OpenAI Realtime │ ✅ Excellent │ ~0.2s     │ $0.06/min    │ No       │
 *  └─────────────────┴──────────────┴───────────┴──────────────┴──────────┘
 *
 * For a production upgrade: use Google Cloud Speech-to-Text with streaming
 * recognition for the fastest Arabic STT. This implementation uses Android's
 * built-in which is free and requires no keys.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class SpeechRecognitionManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    // ── State flows ───────────────────────────────────────────────────────────
    private val _partialResult = MutableStateFlow("")
    val partialResult: StateFlow<String> = _partialResult.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    // ── Callbacks ─────────────────────────────────────────────────────────────
    var onResult: ((transcript: String, isArabic: Boolean, alternatives: List<String>) -> Unit)? = null
    var onError: ((errorCode: Int, message: String) -> Unit)? = null
    var onReadyForSpeech: (() -> Unit)? = null

    // ── Init ──────────────────────────────────────────────────────────────────
    fun initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "SpeechRecognizer not available on this device")
            onError?.invoke(-1, "Speech recognition not available")
            return
        }
        destroyRecognizer()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(buildListener())
        }
        Log.d(TAG, "SpeechRecognizer initialized")
    }

    // ── Start listening ───────────────────────────────────────────────────────
    /**
     * Start listening for speech. Language is auto-detected from the spoken
     * content — we request both Arabic and English in priority order so the
     * recognizer picks whichever the user is speaking.
     *
     * Implementation detail: Android STT accepts a list of preferred languages
     * via EXTRA_LANGUAGE_MODEL. We use the device locale as primary and add
     * Arabic + English as extras so the user can freely switch languages.
     */
    fun startListening(preferArabic: Boolean = true) {
        if (_isListening.value) {
            stopListening()
        }

        val lang = if (preferArabic) "ar-EG" else "en-US"
        val altLang = if (preferArabic) "en-US" else "ar-EG"

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            // Alternative languages — enables bilingual detection
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(altLang))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // FIX
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L) // ADD: ignore sub-300ms noise
        }

        _partialResult.value = ""
        _isListening.value = true

        try {
            recognizer?.startListening(intent)
            Log.d(TAG, "Listening started (lang=$lang)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            _isListening.value = false
            onError?.invoke(-1, e.message ?: "Unknown error")
        }
    }

    fun stopListening() {
        recognizer?.stopListening()
        _isListening.value = false
        _partialResult.value = ""
    }

    fun cancelListening() {
        recognizer?.cancel()
        _isListening.value = false
        _partialResult.value = ""
    }

    fun destroy() {
        destroyRecognizer()
    }

    // ── Private helpers ───────────────────────────────────────────────────────
    private fun destroyRecognizer() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying recognizer: ${e.message}")
        }
        recognizer = null
    }

    private fun buildListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
            _isListening.value = true
            onReadyForSpeech?.invoke()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return
            _partialResult.value = partial
            Log.v(TAG, "Partial: $partial")
        }

        // FIX — replace onResults() in buildListener()
        override fun onResults(results: Bundle?) {
            _isListening.value = false
            val candidates = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?: run { onError?.invoke(-1, "No results"); return }

            val confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

            // Pick best candidate: highest confidence that contains recognisable text
            val best = if (confidences != null && candidates.size == confidences.size) {
                candidates.zip(confidences.toList())
                    .maxByOrNull { (_, conf) -> conf }
                    ?.first ?: candidates.first()
            } else {
                candidates.first()
            }

            val arabic = isArabicText(best)
            Log.d(TAG, "Final result: \"$best\" (arabic=$arabic, candidates=${candidates.size})")
            _partialResult.value = ""

            // Pass ALL candidates so VoiceAssistantManager can try fallbacks
            onResult?.invoke(best, arabic, candidates)
        }

        override fun onError(error: Int) {
            _isListening.value = false
            _partialResult.value = ""
            val msg = sttErrorMessage(error)
            Log.w(TAG, "STT error $error: $msg")
            onError?.invoke(error, msg)
        }

        override fun onBeginningOfSpeech() { Log.v(TAG, "onBeginningOfSpeech") }
        override fun onEndOfSpeech()       { Log.v(TAG, "onEndOfSpeech") }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun isArabicText(text: String): Boolean =
        text.any { it.code in 0x0600..0x06FF }

    private fun sttErrorMessage(code: Int) = when (code) {
        SpeechRecognizer.ERROR_AUDIO                -> "Audio error — check microphone"
        SpeechRecognizer.ERROR_CLIENT               -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
        SpeechRecognizer.ERROR_NETWORK              -> "Network error — check connection"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT      -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH             -> "No speech detected"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY      -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER               -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT       -> "No speech detected"
        else -> "Unknown STT error ($code)"
    }
}
