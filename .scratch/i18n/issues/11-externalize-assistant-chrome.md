# 11: Externalize — assistant chrome (chat sheet + voice overlay)

**What to build:** The chat bottom sheet and the voice-assistant overlay have their *chrome*
translated and RTL-correct in Arabic — the parts that are UI, not conversation. The assistant's
language detection, intent parsing, and generated replies are deliberately left exactly as they
are.

**Blocked by:** 04, 05.

**Status:** ready-for-agent

- [ ] Chrome literals externalized with Egyptian-Arabic translations: panel titles/subtitles,
      the send / show-map / close buttons, the mic / listening / thinking / speaking status
      labels, the text-input hint, "Tap to speak" and similar, and all `contentDescription`s on
      these two surfaces.
- [ ] **Not touched:** `ui/chatbot/ChatSystem.kt` (`detectCategory`, keyword dictionaries,
      `DirectionsGenerator`), `voice/SmartResponseEngine.kt`, `voice/NavigationVoiceController.kt`,
      `voice/LocalIntentParser.kt`, STT/TTS locale handling, and any generated reply / greeting
      text. Existing bilingual reply keys (`chat_*_ar` / `chat_*_en`) are left as-is.
- [ ] The overlay and sheet layout is RTL-correct (`ar_XB` + real `ar-EG`, light and dark):
      mirrored controls, correct text alignment, input field direction.
- [ ] Pseudolocale check (`en_XA`) on the chrome strings.
- [ ] `./gradlew compileDebugKotlin`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin`,
      `lintDebug` green.
