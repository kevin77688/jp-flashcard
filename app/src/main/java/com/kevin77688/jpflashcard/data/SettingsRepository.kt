package com.kevin77688.jpflashcard.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val autoPlay: Boolean = true,
    val speechRate: Float = 0.9f,
    val kanaOnly: Boolean = false,
    val llmBaseUrl: String = "",
    val llmApiKey: String = "",
    val llmModel: String = "",
) {
    val llmConfigured: Boolean get() = llmBaseUrl.isNotBlank() && llmApiKey.isNotBlank() && llmModel.isNotBlank()
}

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Per-device settings. The LLM key stays on the device and is never synced. */
class SettingsRepository(private val context: Context) {
    private object Keys {
        val autoPlay = booleanPreferencesKey("auto_play")
        val speechRate = floatPreferencesKey("speech_rate")
        val kanaOnly = booleanPreferencesKey("kana_only")
        val llmBaseUrl = stringPreferencesKey("llm_base_url")
        val llmApiKey = stringPreferencesKey("llm_api_key")
        val llmModel = stringPreferencesKey("llm_model")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            autoPlay = p[Keys.autoPlay] ?: true,
            speechRate = p[Keys.speechRate] ?: 0.9f,
            kanaOnly = p[Keys.kanaOnly] ?: false,
            llmBaseUrl = p[Keys.llmBaseUrl].orEmpty(),
            llmApiKey = p[Keys.llmApiKey].orEmpty(),
            llmModel = p[Keys.llmModel].orEmpty(),
        )
    }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val current = Settings(
                autoPlay = p[Keys.autoPlay] ?: true,
                speechRate = p[Keys.speechRate] ?: 0.9f,
                kanaOnly = p[Keys.kanaOnly] ?: false,
                llmBaseUrl = p[Keys.llmBaseUrl].orEmpty(),
                llmApiKey = p[Keys.llmApiKey].orEmpty(),
                llmModel = p[Keys.llmModel].orEmpty(),
            )
            val next = transform(current)
            p.write(Keys.autoPlay, next.autoPlay)
            p.write(Keys.speechRate, next.speechRate)
            p.write(Keys.kanaOnly, next.kanaOnly)
            p.write(Keys.llmBaseUrl, next.llmBaseUrl.trim())
            p.write(Keys.llmApiKey, next.llmApiKey.trim())
            p.write(Keys.llmModel, next.llmModel.trim())
        }
    }

    private fun <T> androidx.datastore.preferences.core.MutablePreferences.write(key: Preferences.Key<T>, value: T) {
        this[key] = value
    }
}
