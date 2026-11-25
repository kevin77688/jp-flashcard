package com.kevin77688.jpflashcard.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Plays Japanese speech. Uses VOICEVOX when it is selected and ready; until then (first run unpacks
 * ~170 MB) and on any error it falls back to the system TTS, so a tap always makes a sound.
 */
class Speaker(
    context: Context,
    private val scope: CoroutineScope,
    /** Kanji spellings the dictionary knows for a kana reading, used to get the right pitch accent. */
    private val spellingsFor: suspend (String) -> List<String>,
) {
    val system = SystemTts(context)
    val voicevox = VoicevoxEngine(context)

    /** Follows the 發音引擎 setting. */
    var useVoicevox: Boolean = true
        set(value) {
            field = value
            if (value && voicevox.bundled) scope.launch { runCatching { voicevox.prepare() } }
        }

    private var job: Job? = null
    private var player: MediaPlayer? = null

    /** Speaks [kana]. Pass [written] (the card's kanji spelling) when there is one. */
    fun speak(kana: String, rate: Float = 0.9f, written: String? = null) {
        if (kana.isBlank()) return
        job?.cancel()
        stopPlayer()
        if (!useVoicevox || voicevox.status.value != VoicevoxEngine.Status.Ready) {
            system.speak(kana, rate)
            return
        }
        job = scope.launch {
            runCatching {
                val spellings = written?.takeIf { it.isNotBlank() && it != kana }?.let(::listOf)
                    ?: spellingsFor(kana)
                play(voicevox.wav(kana, spellings, rate))
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                Log.w(TAG, "VOICEVOX failed for $kana, using system TTS", it)
                system.speak(kana, rate)
            }
        }
    }

    private fun play(file: File) {
        stopPlayer()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            setDataSource(file.path)
            setOnCompletionListener { it.release(); if (player === it) player = null }
            prepare()
            start()
        }
    }

    private fun stopPlayer() {
        player?.runCatching { stop(); release() }
        player = null
    }

    private companion object {
        const val TAG = "Speaker"
    }
}
