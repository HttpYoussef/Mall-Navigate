package com.example.mallar.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "VoiceManager"

/**
 * Supported navigation languages.
 * Extend freely — each entry maps to a [Locale] used with Android TTS.
 */
enum class NavigationLanguage(val locale: Locale, val displayName: String) {
    ARABIC(Locale("ar", "EG"), "العربية"),
    ENGLISH(Locale("en", "GB"), "English")  // Force British English
}

/**
 * Thin wrapper around Android's built-in [TextToSpeech] engine.
 *
 * Responsibilities:
 *  - Initialise & shut down TTS safely
 *  - Switch language at runtime
 *  - Speak with an optional global cooldown (avoids TTS spam)
 *  - Expose [isReady] / [isEnabled] so callers can gate logic
 *
 * Lifecycle:
 *  - Call [init] once (e.g. in LaunchedEffect / ViewModel init)
 *  - Call [shutdown] in onDispose / ViewModel onCleared
 */
class VoiceManager(private val context: Context) {

    // ── State ─────────────────────────────────────────────────────────────────

    /** TTS is initialised and the requested language is available. */
    var isReady: Boolean = false
        private set

    /** User-facing toggle — voice instructions are played only when true. */
    var isEnabled: Boolean = true

    /** Currently active language. */
    var language: NavigationLanguage = NavigationLanguage.ARABIC
        private set

    private var tts: TextToSpeech? = null
    private val isSpeaking = AtomicBoolean(false)

    /** Timestamp of last spoken utterance (ms). */
    private var lastSpeakTimeMs = 0L

    // ── Initialisation ────────────────────────────────────────────────────────

    /**
     * Initialise TTS.  Safe to call multiple times — subsequent calls are no-ops.
     *
     * @param language    Language to start with.
     * @param onReady     Invoked on the main thread once TTS is ready.
     * @param onError     Invoked if TTS init fails.
     */
    fun init(
        language: NavigationLanguage = NavigationLanguage.ARABIC,
        onReady: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (tts != null) return          // already initialised

        this.language = language

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                applyLanguage(language)
                configureProgressListener()
                isReady = true
                Log.d(TAG, "TTS ready — lang=${language.locale.displayName}")
                onReady()
            } else {
                Log.e(TAG, "TTS init failed with status=$status")
                isReady = false
                onError("TTS initialisation failed (status=$status)")
            }
        }
    }

    /** Switch the TTS language at runtime. */
    fun setLanguage(lang: NavigationLanguage) {
        language = lang
        tts?.let { applyLanguage(lang) }
    }

    // ── Speak API ─────────────────────────────────────────────────────────────

    /**
     * Speak [text] immediately, interrupting any ongoing utterance.
     *
     * Silently no-ops when:
     *  - [isEnabled] is false
     *  - [isReady]   is false
     *  - [minCooldownMs] has not elapsed since the last utterance
     *
     * @param text          Text to synthesise.
     * @param minCooldownMs Minimum milliseconds between two utterances (default 0 = no limit).
     * @param force         Skip cooldown check — use for critical announcements (e.g. "Arrived").
     */
    fun speak(text: String, minCooldownMs: Long = 0L, force: Boolean = false) {
        if (!isEnabled || !isReady) return
        if (text.isBlank()) return

        val now = System.currentTimeMillis()
        if (!force && minCooldownMs > 0 && (now - lastSpeakTimeMs) < minCooldownMs) {
            Log.v(TAG, "speak() throttled — cooldown not elapsed")
            return
        }

        lastSpeakTimeMs = now
        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        Log.d(TAG, "Speaking [$utteranceId]: \"$text\"")
    }

    /** Stop any current utterance without shutting down. */
    fun stop() {
        tts?.stop()
    }

    /** Release all TTS resources. Call in onDispose / onCleared. */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error during TTS shutdown: ${e.message}")
        } finally {
            tts    = null
            isReady = false
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    // FIX — language-appropriate rates in applyLanguage()
    private fun applyLanguage(lang: NavigationLanguage) {
        val engine = tts ?: return
        val result = engine.setLanguage(lang.locale)

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.getDefault())
            Log.w(TAG, "Locale ${lang.locale} not supported — falling back")
        }

        when (lang) {
            NavigationLanguage.ENGLISH -> {
                engine.setSpeechRate(0.88f)   // Measured, authoritative — like a calm navigator
                engine.setPitch(0.92f)        // Slightly lower = warmer, more masculine, less robotic
            }
            NavigationLanguage.ARABIC -> {
                engine.setSpeechRate(0.90f)   // Arabic TTS tends to rush — pull it back
                engine.setPitch(1.0f)         // Arabic voices are already calibrated well at 1.0
            }
        }
    }
    // ADD this method to VoiceManager, call it inside init() after applyLanguage()
    private fun selectBestVoice(lang: NavigationLanguage) {
        val engine = tts ?: return
        val available = engine.voices ?: return

        val targetLocale = lang.locale
        val langTag = targetLocale.toLanguageTag() // e.g. "en-GB"

        // Priority order: neural > standard, prefer male for British English
        val preferred = available
            .filter { voice ->
                !voice.isNetworkConnectionRequired || isNetworkAvailable()
            }
            .filter { voice ->
                voice.locale.toLanguageTag().startsWith(langTag.take(5)) // "en-GB" or "ar-EG"
            }
            .sortedWith(compareBy(
                { if (it.name.contains("neural", ignoreCase = true) ||
                    it.name.contains("wavenet", ignoreCase = true) ||
                    it.name.contains("network", ignoreCase = true)) 0 else 1 },
                { if (lang == NavigationLanguage.ENGLISH &&
                    it.name.contains("male", ignoreCase = true)) 0 else 1 },
                { it.latency }
            ))
            .firstOrNull()

        if (preferred != null) {
            val result = engine.setVoice(preferred)
            Log.d(TAG, "Voice selected: ${preferred.name} (result=$result)")
        } else {
            Log.w(TAG, "No preferred voice found for $langTag — using engine default")
        }
    }

    private fun isNetworkAvailable(): Boolean {
        // Inject ConnectivityManager or pass from context — simple check
        return true // Default permissive; override in production
    }

    // ADD a helper method to VoiceManager
    fun speakSsml(ssml: String, minCooldownMs: Long = 0L, force: Boolean = false) {
        if (!isEnabled || !isReady) return
        val now = System.currentTimeMillis()
        if (!force && minCooldownMs > 0 && (now - lastSpeakTimeMs) < minCooldownMs) return

        lastSpeakTimeMs = now
        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        // Note: Android TTS SSML support is partial but handles <break>, <emphasis>, <say-as>
        tts?.speak(ssml, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    // Helper to wrap plain text with navigation-appropriate SSML
    fun speakNavigationInstruction(instruction: String, force: Boolean = false) {
        // Convert plain text to SSML with natural pauses and emphasis
        val ssml = buildNavigationSsml(instruction)
        speakSsml(ssml, force = force)
    }

    private fun buildNavigationSsml(text: String): String {
        // Android TTS supports a reduced SSML set
        var result = text
            .replace("Turn right", "<emphasis level=\"strong\">Turn right</emphasis>")
            .replace("Turn left", "<emphasis level=\"strong\">Turn left</emphasis>")
            .replace("now", "<emphasis level=\"moderate\">now</emphasis>")
            .replace(Regex("(\\d+) metres")) { mr ->
                "<say-as interpret-as=\"cardinal\">${mr.groupValues[1]}</say-as>" +
                        "<break time=\"100ms\"/>metres"
            }
        // Wrap in speak tag — required for SSML
        return "<speak>$result</speak>"
    }

    private fun configureProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?)  { isSpeaking.set(true)  }
            override fun onDone(utteranceId: String?)   { isSpeaking.set(false) }
            @Deprecated("Deprecated in API 21", ReplaceWith(""))
            override fun onError(utteranceId: String?)  { isSpeaking.set(false) }
        })
    }
}
