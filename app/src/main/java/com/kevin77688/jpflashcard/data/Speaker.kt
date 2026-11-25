package com.kevin77688.jpflashcard.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Japanese text-to-speech. Binds to Google's engine explicitly because device defaults vary
 * (the phone defaults to Samsung TTS, the tablet has no default set); falls back to the default.
 */
class Speaker(context: Context) {
    enum class State { Initializing, Ready, MissingVoice, Unavailable }

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(State.Initializing)
    val state: StateFlow<State> = _state

    private var tts: TextToSpeech? = null
    private var triedFallback = false

    init {
        create(GOOGLE_ENGINE)
    }

    private fun create(engine: String?) {
        tts = TextToSpeech(appContext, { status -> onInit(status, engine) }, engine)
    }

    private fun onInit(status: Int, engine: String?) {
        val engineInstance = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS init failed for engine=$engine status=$status")
            if (!triedFallback) {
                triedFallback = true
                engineInstance.shutdown()
                create(null)
            } else {
                _state.value = State.Unavailable
            }
            return
        }
        val result = engineInstance.setLanguage(Locale.JAPAN)
        Log.i(TAG, "TTS ready engine=${engineInstance.defaultEngine} requested=$engine setLanguage=$result")
        _state.value = if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            State.MissingVoice
        } else {
            State.Ready
        }
        engineInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                Log.i(TAG, "Utterance done: $utteranceId")
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.w(TAG, "Utterance error: $utteranceId")
            }
        })
    }

    /** Speaks [text] (pass the kana reading so kanji are never mis-read). */
    fun speak(text: String, rate: Float = 0.9f) {
        val engineInstance = tts ?: return
        if (text.isBlank()) return
        engineInstance.setSpeechRate(rate)
        engineInstance.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt-${System.nanoTime()}")
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }

    private companion object {
        const val TAG = "Speaker"
        const val GOOGLE_ENGINE = "com.google.android.tts"
    }
}
