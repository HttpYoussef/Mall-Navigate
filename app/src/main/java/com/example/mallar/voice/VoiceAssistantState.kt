package com.example.mallar.voice

/**
 * All possible states of the voice assistant's listening/speaking lifecycle.
 */
enum class VoiceAssistantStatus {
    IDLE,        // Mic button visible, not active
    LISTENING,   // STT active — recording user speech
    THINKING,    // Received transcript, processing intent
    SPEAKING,    // TTS is playing response
    ERROR        // Something failed — shows error briefly then returns to IDLE
}

/**
 * Detected intent category from user speech.
 */
enum class VoiceIntent {
    NAVIGATE_TO,         // "Take me to Zara" / "عايز أروح زارا"
    WHERE_IS,            // "Where is the nearest bathroom?"
    REPEAT_DIRECTIONS,   // "Repeat" / "كرر"
    REMAINING_DISTANCE,  // "How far?" / "كم باقي؟"
    ETA,                 // "How long?" / "كم دقيقة؟"
    CURRENT_LOCATION,    // "Where am I?" / "فين أنا؟"
    STOP_NAVIGATION,     // "Stop" / "وقف"
    CANCEL,              // "Cancel" / "إلغاء"
    GREETING,            // "Hi" / "مرحبا"
    HELP,                // "Help" / "مساعدة"
    UNKNOWN              // Fallback
}

/**
 * Structured result of intent parsing — everything needed to act on what the user said.
 */
data class ParsedIntent(
    val intent: VoiceIntent,
    val destination: String? = null,   // Store/place name extracted from speech
    /** When set with [destination], route from this store (e.g. "from Zara to Bershka"). */
    val origin: String? = null,
    val rawText: String = "",          // Original transcript
    val isArabic: Boolean = false,     // Language detected
    val confidence: Float = 1f         // 0.0–1.0
)

/**
 * Full UI state of the voice assistant overlay.
 */
data class VoiceAssistantUiState(
    val status: VoiceAssistantStatus = VoiceAssistantStatus.IDLE,
    val transcript: String = "",          // Live speech-to-text result
    val assistantReply: String = "",      // Last AI/system reply text
    val isArabic: Boolean = false,        // Language for current session
    val errorMessage: String? = null      // Shown briefly on error
)
