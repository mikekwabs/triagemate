package com.triagemate.chps.domain.model

/**
 * Outcome of a single voice-input round: Twi speech is captured by the CHO,
 * passed to Gemma 4 E2B's audio encoder for translation, then the model is
 * asked to map the translation onto the canonical symptom checklist for the
 * current pathway.
 */
sealed class VoiceInputResult {
    /**
     * @param rawTranslation The English translation of what the CHO said.
     * @param extractedSymptoms Canonical symptoms (from the active pathway's
     *  checklist) that match the CHO's description. May be empty if Gemma
     *  could not confidently map the speech to any checklist item.
     */
    data class Success(
        val rawTranslation: String,
        val extractedSymptoms: List<String>
    ) : VoiceInputResult()

    data class Error(val message: String) : VoiceInputResult()
}
