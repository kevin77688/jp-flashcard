package com.kevin77688.jpflashcard.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.kevin77688.jpflashcard.domain.Conjugation
import com.kevin77688.jpflashcard.domain.Romaji
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class DictEntry(
    val kanji: String?,
    val reading: String,
    val gloss: String,
    val pos: List<String>,
    val common: Boolean,
    /** JMdict "usually written using kana alone" — default the card to kana display. */
    val usuallyKana: Boolean,
    /** Set when this is a conjugated form (e.g. 出かけます): the dictionary form it came from. */
    val baseForm: String? = null,
)

/**
 * Offline JMdict lookup. The SQLite asset is copied into internal storage on first use.
 * JMdict © EDRDG, CC BY-SA 4.0.
 */
class Dictionary(private val context: Context) {
    private val mutex = Mutex()
    private var db: SQLiteDatabase? = null

    private suspend fun open(): SQLiteDatabase = mutex.withLock {
        db?.let { return it }
        withContext(Dispatchers.IO) {
            // Re-extract whenever the app is updated, in case the dictionary changed.
            val stamp = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
            val target = File(context.noBackupFilesDir, "jmdict.db")
            val marker = File(context.noBackupFilesDir, "jmdict.version")
            if (!target.exists() || marker.takeIf { it.exists() }?.readText() != stamp) {
                val tmp = File(target.path + ".tmp")
                context.assets.open(ASSET).use { input ->
                    tmp.outputStream().use { input.copyTo(it, 1 shl 16) }
                }
                tmp.renameTo(target)
                marker.writeText(stamp)
            }
            SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY).also { db = it }
        }
    }

    /** Warm up (unpack) in the background so the first search is instant. */
    suspend fun prepare() {
        open()
    }

    /**
     * Searches by reading (romaji, hiragana or katakana are all accepted) or by kanji prefix.
     * Exact matches first, then common words, then shorter readings.
     */
    suspend fun search(query: String, limit: Int = 40): List<DictEntry> {
        val raw = query.trim()
        if (raw.isEmpty()) return emptyList()
        val q = if (raw.any { it in 'a'..'z' || it in 'A'..'Z' }) Romaji.toKana(raw) else Romaji.toHiragana(raw)
        val database = open()
        val polite = politeForms(database, q)
        return polite + withContext(Dispatchers.IO) {
            val hasKanji = q.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
            val column = if (hasKanji) "kanji" else "reading"
            val sql = """
                SELECT kanji, reading, kata, reading_orig, gloss, pos, common, uk, ($column = ?1) AS exact
                FROM entries
                WHERE $column >= ?1 AND $column < ?1 || '${'￿'}'
                ORDER BY exact DESC, common DESC, length(reading), id
                LIMIT $limit
            """.trimIndent()
            database.rawQuery(sql, arrayOf(q)).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val hira = c.getString(1)
                        val reading = c.getString(3) ?: if (c.getInt(2) == 1) Romaji.toKatakana(hira) else hira
                        add(
                            DictEntry(
                                kanji = c.getString(0),
                                reading = reading,
                                gloss = c.getString(4),
                                pos = c.getString(5).split(',').filter { it.isNotBlank() },
                                common = c.getInt(6) == 1,
                                usuallyKana = c.getInt(7) == 1,
                            ),
                        )
                    }
                }.distinctBy { it.kanji to it.reading }
            }
        }
    }

    /**
     * でかけます is not in the dictionary, but 出かける is. Guess dictionary forms from the ます stem,
     * keep only verbs that really conjugate back to the same stem, and return them in the polite form.
     */
    private suspend fun politeForms(database: SQLiteDatabase, query: String): List<DictEntry> {
        if (query.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) return emptyList()
        val (stem, suffix) = Conjugation.splitPolite(query) ?: return emptyList()
        val hiraStem = Romaji.toHiragana(stem)
        return withContext(Dispatchers.IO) {
            Conjugation.dictionaryCandidates(hiraStem).flatMap { candidate ->
                database.rawQuery(
                    "SELECT kanji, reading, gloss, pos, common, uk FROM entries WHERE reading = ? ORDER BY common DESC, id",
                    arrayOf(candidate),
                ).use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            val pos = c.getString(3).split(',').filter { it.isNotBlank() }
                            if (Conjugation.masuStem(c.getString(1), pos) != hiraStem) continue
                            val kanji = c.getString(0)
                            add(
                                DictEntry(
                                    kanji = kanji?.let { k -> Conjugation.masuStem(k, pos)?.plus(suffix) },
                                    reading = query,
                                    gloss = c.getString(2),
                                    pos = pos,
                                    common = c.getInt(4) == 1,
                                    usuallyKana = c.getInt(5) == 1,
                                    baseForm = kanji ?: c.getString(1),
                                ),
                            )
                        }
                    }
                }
            }.sortedByDescending { it.common }.distinctBy { it.kanji to it.reading }
        }
    }

    private companion object {
        // The repo stores assets/jmdict.db.gz; the Android asset merger gunzips it into jmdict.db,
        // which the APK then stores deflated.
        const val ASSET = "jmdict.db"
    }
}

/** Short Traditional Chinese labels for the most common JMdict part-of-speech tags. */
fun posLabel(tag: String): String? = when {
    tag == "n" -> "名詞"
    tag == "pn" -> "代名詞"
    tag == "adj-i" -> "い形容詞"
    tag == "adj-na" -> "な形容詞"
    tag == "adj-no" -> "の形容詞"
    tag == "adv" -> "副詞"
    tag == "v1" -> "一段動詞"
    tag.startsWith("v5") -> "五段動詞"
    tag == "vs" || tag == "vs-i" || tag == "vs-s" -> "サ變動詞"
    tag == "vk" -> "カ變動詞"
    tag == "vt" -> "他動詞"
    tag == "vi" -> "自動詞"
    tag == "exp" -> "慣用語"
    tag == "int" -> "感嘆詞"
    tag == "conj" -> "連接詞"
    tag == "prt" -> "助詞"
    tag == "ctr" -> "量詞"
    tag == "suf" -> "接尾詞"
    tag == "pref" -> "接頭詞"
    else -> null
}
