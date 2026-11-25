package com.kevin77688.jpflashcard.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Hand-rolled dependency container; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val auth = AuthRepository()
    val cards = CardRepository(auth)
    val settingsRepo = SettingsRepository(context)
    val dictionary = Dictionary(context)
    val llm = LlmClient()
    val speaker = Speaker(context, scope) { kana -> dictionarySpellings(kana) }

    val words = cards.words.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val kanaProgress = cards.kanaProgress.stateIn(scope, SharingStarted.Eagerly, emptyMap())
    val settings = settingsRepo.settings.stateIn(scope, SharingStarted.Eagerly, Settings())
    val syncStatus = cards.syncStatus.stateIn(scope, SharingStarted.Eagerly, null)

    /** Progress of "AI 補齊中文意思"; lives here so it keeps going when the user leaves the screen. */
    data class AiFill(val done: Int, val total: Int, val failed: Int, val running: Boolean, val error: String? = null)

    private val _aiFill = MutableStateFlow<AiFill?>(null)
    val aiFill: StateFlow<AiFill?> = _aiFill

    fun fillMissingMeanings() {
        if (_aiFill.value?.running == true) return
        val s = settings.value
        val todo = words.value.filter { it.meaning.isBlank() }
        if (!s.llmConfigured || todo.isEmpty()) return
        scope.launch {
            var state = AiFill(0, todo.size, 0, running = true)
            _aiFill.value = state
            for (w in todo) {
                llm.suggest(s, w.written, w.kana, w.meaningEn)
                    .onSuccess { r ->
                        if (r.meaning.isNotBlank()) cards.fillMeaning(w.id, r.meaning, r.note.takeIf { w.note.isBlank() && it.isNotBlank() })
                        state = state.copy(done = state.done + 1, failed = state.failed + if (r.meaning.isBlank()) 1 else 0)
                    }
                    .onFailure {
                        android.util.Log.w("AppContainer", "AI fill failed for ${w.kana}", it)
                        state = state.copy(done = state.done + 1, failed = state.failed + 1, error = it.message?.take(160))
                    }
                _aiFill.value = state
            }
            _aiFill.value = state.copy(running = false)
        }
    }

    /** Kanji spellings of an exact dictionary match, most common first (for VOICEVOX's pitch accent). */
    private suspend fun dictionarySpellings(kana: String): List<String> = runCatching {
        val hira = com.kevin77688.jpflashcard.domain.Romaji.toHiragana(kana)
        dictionary.search(kana, limit = 10)
            .filter { com.kevin77688.jpflashcard.domain.Romaji.toHiragana(it.reading) == hira }
            .mapNotNull { it.kanji }
            .distinct()
            .take(2)
    }.getOrDefault(emptyList())

    init {
        scope.launch {
            settings.collect { speaker.useVoicevox = it.useVoicevox }
        }
        scope.launch {
            runCatching { dictionary.prepare() }.onFailure { android.util.Log.e("AppContainer", "Dictionary unpack failed", it) }
        }
    }
}
