package com.kevin77688.jpflashcard.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Hand-rolled dependency container; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val auth = AuthRepository()
    val speaker = Speaker(context)
    val cards = CardRepository(auth)
    val settingsRepo = SettingsRepository(context)
    val dictionary = Dictionary(context)
    val llm = LlmClient()

    val words = cards.words.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val kanaProgress = cards.kanaProgress.stateIn(scope, SharingStarted.Eagerly, emptyMap())
    val settings = settingsRepo.settings.stateIn(scope, SharingStarted.Eagerly, Settings())

    init {
        scope.launch {
            runCatching { dictionary.prepare() }.onFailure { android.util.Log.e("AppContainer", "Dictionary unpack failed", it) }
        }
    }
}
