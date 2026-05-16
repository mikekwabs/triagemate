package com.triagemate.chps.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around Android's on-device [TextToSpeech] engine. Used to
 * read the final urgency banner / recommended action aloud for CHOs who
 * may be doing other things with their hands.
 *
 * Prefers Ghanaian English where available, then falls back to UK and US
 * English so the engine still works on devices without en-GH voice data.
 */
@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext context: Context
) {

    companion object {
        private const val TAG = "TextToSpeechManager"
    }

    private var tts: TextToSpeech? = null

    @Volatile var isReady: Boolean = false
        private set

    @Volatile private var isSpeaking: Boolean = false

    private var onCompleteListener: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locales = listOf(
                    Locale("en", "GH"),
                    Locale.UK,
                    Locale.US,
                    Locale.ENGLISH
                )
                val chosen = locales.firstOrNull { locale ->
                    val result = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_MISSING_DATA
                    result == TextToSpeech.LANG_AVAILABLE ||
                        result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                        result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
                }
                if (chosen != null) {
                    tts?.language = chosen
                    isReady = true
                    Log.d(TAG, "init: TTS ready with locale=$chosen")
                } else {
                    Log.w(TAG, "init: no supported English locale on device")
                }

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        onCompleteListener?.invoke()
                    }

                    @Deprecated("Required override")
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        onCompleteListener?.invoke()
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        isSpeaking = false
                        onCompleteListener?.invoke()
                    }
                })
            } else {
                Log.e(TAG, "init: TTS init failed status=$status")
            }
        }
    }

    fun isCurrentlySpeaking(): Boolean = isSpeaking

    /** Speak [text]. If TTS is currently active, replaces the current utterance. */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isReady || text.isBlank()) {
            onComplete?.invoke()
            return
        }
        onCompleteListener = onComplete
        val id = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        isSpeaking = false
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        isReady = false
        isSpeaking = false
    }
}
