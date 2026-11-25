package com.kevin77688.jpflashcard.data

import android.content.Context
import android.util.Log
import com.kevin77688.jpflashcard.domain.Romaji
import jp.hiroshiba.voicevoxcore.blocking.Onnxruntime
import jp.hiroshiba.voicevoxcore.blocking.OpenJtalk
import jp.hiroshiba.voicevoxcore.blocking.Synthesizer
import jp.hiroshiba.voicevoxcore.blocking.VoiceModelFile
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * On-device neural Japanese speech with VOICEVOX (voice: 四国めたん ノーマル).
 * Open JTalk's dictionary supplies the pitch accent, which the system TTS often gets wrong for
 * single words. Generated audio is cached per word, so each word is synthesised only once.
 * Credit required by the voice's terms: "VOICEVOX:四国めたん".
 */
class VoicevoxEngine(context: Context) {
    enum class Status { NotBundled, Idle, Preparing, Ready, Failed }

    private val appContext = context.applicationContext
    // The native synthesizer is not meant for concurrent use; one thread keeps it simple.
    private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "voicevox") }.asCoroutineDispatcher()
    private var synthesizer: Synthesizer? = null
    private var model: VoiceModelFile? = null

    val bundled: Boolean = runCatching { appContext.assets.list("voicevox")?.contains(MODEL) == true }.getOrDefault(false)
    private val _status = MutableStateFlow(if (bundled) Status.Idle else Status.NotBundled)
    val status: StateFlow<Status> = _status

    private val cacheDir = File(appContext.cacheDir, "voicevox").apply { mkdirs() }

    /** Unpacks the dictionary and model (first run only, ~170 MB) and loads the voice. */
    suspend fun prepare(): Synthesizer = withContext(thread) {
        synthesizer?.let { return@withContext it }
        check(bundled) { "VOICEVOX files are not bundled in this build" }
        _status.value = Status.Preparing
        try {
            val dir = unpack()
            val started = System.currentTimeMillis()
            val ort = Onnxruntime.loadOnce().filename("libvoicevox_onnxruntime.so").perform()
            val synth = Synthesizer.builder(ort, OpenJtalk(File(dir, "dic").path)).build()
            // Kept open on purpose: closing it makes the library's finalizer free it a second time.
            model = VoiceModelFile(File(dir, MODEL).path).also { synth.loadVoiceModel(it).perform() }
            Log.i(TAG, "VOICEVOX ready in ${System.currentTimeMillis() - started} ms")
            synthesizer = synth
            _status.value = Status.Ready
            synth
        } catch (t: Throwable) {
            Log.e(TAG, "VOICEVOX init failed", t)
            _status.value = Status.Failed
            throw t
        }
    }

    private fun unpack(): File {
        val dir = File(appContext.noBackupFilesDir, "voicevox")
        val marker = File(dir, "version")
        val stamp = appContext.packageManager.getPackageInfo(appContext.packageName, 0).lastUpdateTime.toString()
        if (marker.takeIf { it.exists() }?.readText() == stamp) return dir
        val started = System.currentTimeMillis()
        dir.deleteRecursively()
        File(dir, "dic").mkdirs()
        val files = appContext.assets.list("voicevox/dic").orEmpty().map { "dic/$it" } + MODEL
        for (name in files) {
            val target = File(dir, name)
            val tmp = File(target.path + ".tmp")
            appContext.assets.open("voicevox/$name").use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
            tmp.renameTo(target)
        }
        marker.writeText(stamp)
        // New engine files may pronounce differently; drop audio made by the old ones.
        cacheDir.listFiles()?.forEach { it.delete() }
        Log.i(TAG, "Unpacked VOICEVOX files in ${System.currentTimeMillis() - started} ms")
        return dir
    }

    /**
     * Returns a WAV file for [kana]. [spellings] (e.g. 買います, or the dictionary's kanji for a
     * kana-only word) are tried first because Open JTalk knows their accent; one is used only if its
     * reading matches [kana], so a kanji is never read the wrong way.
     */
    suspend fun wav(kana: String, spellings: List<String>, speed: Float): File = withContext(thread) {
        val key = sha1("$STYLE|$speed|$kana|${spellings.joinToString("|")}")
        val file = File(cacheDir, "$key.wav")
        if (file.exists()) return@withContext file
        val synth = prepare()
        var source = kana
        val query = spellings.firstNotNullOfOrNull { text ->
            runCatching { synth.createAudioQuery(text, STYLE) }.getOrNull()
                ?.takeIf { q -> q.kana?.let { sameReading(it, kana) } == true }
                ?.also { source = text }
        } ?: synth.createAudioQuery(kana, STYLE)
        Log.i(TAG, "$kana ← $source, accent ${query.kana}")
        query.speedScale = speed.toDouble()
        val audio = synth.synthesis(query, STYLE).perform()
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(audio)
        tmp.renameTo(file)
        file
    }

    companion object {
        private const val TAG = "Voicevox"
        private const val MODEL = "0.vvm"
        /** 四国めたん ノーマル: a clear, neutral voice suited to learners. */
        const val STYLE = 2
        const val CREDIT = "VOICEVOX:四国めたん"

        private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

        /**
         * Compares Open JTalk's AquesTalk-style kana (e.g. "カ'イマス", "キョ'ー") with a card's reading,
         * ignoring accent marks and the long-vowel spelling difference (きょう vs キョー).
         */
        fun sameReading(aquesKana: String, reading: String): Boolean {
            val plain = aquesKana.filterNot { it in "'/、_？?　 " }
            return normalize(plain) == normalize(reading)
        }

        private fun normalize(kana: String): String =
            Romaji.fromKana(Romaji.toHiragana(kana))
                .replace("ou", "oo").replace("ei", "ee")
                .replace(Regex("([aeiou])\\1+"), "$1")
    }
}
