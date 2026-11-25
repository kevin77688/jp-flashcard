package com.kevin77688.jpflashcard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** What the LLM returns for a word the user is adding. */
data class WordSuggestion(
    val meaning: String,
    val dictionaryForm: String?,
    val dictionaryReading: String?,
    val note: String,
)

/** Minimal client for any OpenAI-compatible chat completions API (DeepSeek, OpenAI, local servers…). */
class LlmClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun endpoint(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    private suspend fun chat(settings: Settings, system: String, user: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", settings.llmModel)
            .put("temperature", 0.2)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )
        val request = Request.Builder()
            .url(endpoint(settings.llmBaseUrl))
            .header("Authorization", "Bearer ${settings.llmApiKey}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) error("HTTP ${response.code}: ${text.take(300)}")
            JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }
    }

    /** Quick connectivity check for the settings screen. */
    suspend fun ping(settings: Settings): Result<String> = runCatching {
        chat(settings, "Reply with exactly: OK", "ping").trim()
    }

    suspend fun suggest(settings: Settings, written: String, reading: String, englishHint: String): Result<WordSuggestion> =
        runCatching {
            val system = """
                你是日語老師，協助台灣學生製作單字卡。只輸出 JSON，不要任何其他文字。
                JSON 格式：{"meaning": "...", "dictionary_form": "...", "dictionary_reading": "...", "note": "..."}
                - meaning：精簡的繁體中文意思（台灣用語），多個意思用「；」分隔，最多三個。
                - dictionary_form / dictionary_reading：如果單字是變化形（例如 食べました、行きたい），填原形與其假名讀音；否則填 null。
                - note：一句繁體中文補充，例如變化形說明或常見用法；沒有就填空字串。
            """.trimIndent()
            val user = buildString {
                append("單字：").append(written)
                if (reading.isNotBlank() && reading != written) append("（讀音：").append(reading).append("）")
                if (englishHint.isNotBlank()) append("\n英文詞典參考：").append(englishHint.replace('\n', ' '))
            }
            val raw = chat(settings, system, user)
            val json = JSONObject(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1))
            fun opt(key: String) = json.optString(key).takeIf { it.isNotBlank() && it != "null" }
            WordSuggestion(
                meaning = opt("meaning").orEmpty(),
                dictionaryForm = opt("dictionary_form"),
                dictionaryReading = opt("dictionary_reading"),
                note = opt("note").orEmpty(),
            )
        }
}
