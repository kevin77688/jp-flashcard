package com.kevin77688.jpflashcard.data

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.kevin77688.jpflashcard.domain.DisplayMode
import com.kevin77688.jpflashcard.domain.SrsState
import com.kevin77688.jpflashcard.domain.WordCard
import com.kevin77688.jpflashcard.domain.WordDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Cards live in Firestore under /users/{uid}/words and /users/{uid}/kana.
 * Firestore's offline cache is the local store: reads and writes work without a network and sync later.
 * Content edits and review results update different fields, so a Mac edit and a phone review never clobber each other.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CardRepository(private val auth: AuthRepository) {
    private val db = FirebaseFirestore.getInstance()

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun uidOrNull() = auth.currentUser?.uid

    val words: Flow<List<WordCard>> = auth.userFlow.flatMapLatest { user ->
        if (user == null) flowOf(emptyList()) else callbackFlow {
            val reg = userDoc(user.uid).collection("words").addSnapshotListener { snap, err ->
                if (err != null) Log.w(TAG, "words listener", err)
                if (snap != null) trySend(snap.documents.mapNotNull { it.toWordCard() }.sortedByDescending { it.createdAt })
            }
            awaitClose { reg.remove() }
        }
    }

    /** Kana progress keyed by card id (e.g. "h-か"). Missing ids are new cards. */
    val kanaProgress: Flow<Map<String, SrsState>> = auth.userFlow.flatMapLatest { user ->
        if (user == null) flowOf(emptyMap()) else callbackFlow {
            val reg = userDoc(user.uid).collection("kana").addSnapshotListener { snap, err ->
                if (err != null) Log.w(TAG, "kana listener", err)
                if (snap != null) trySend(snap.documents.associate { it.id to it.srs() })
            }
            awaitClose { reg.remove() }
        }
    }

    fun addWord(draft: WordDraft): String? {
        val uid = uidOrNull() ?: return null
        val ref = userDoc(uid).collection("words").document()
        val now = System.currentTimeMillis()
        ref.set(draft.toMap() + mapOf("createdAt" to now, "updatedAt" to now, "srs" to SrsState(due = now).toMap()))
        return ref.id
    }

    fun updateWord(id: String, draft: WordDraft) {
        val uid = uidOrNull() ?: return
        userDoc(uid).collection("words").document(id)
            .update(draft.toMap() + mapOf("updatedAt" to System.currentTimeMillis()))
    }

    fun deleteWord(id: String) {
        val uid = uidOrNull() ?: return
        userDoc(uid).collection("words").document(id).delete()
    }

    fun saveWordReview(id: String, srs: SrsState) {
        val uid = uidOrNull() ?: return
        userDoc(uid).collection("words").document(id).update("srs", srs.toMap())
    }

    fun saveKanaReview(cardId: String, srs: SrsState) {
        val uid = uidOrNull() ?: return
        userDoc(uid).collection("kana").document(cardId).set(mapOf("srs" to srs.toMap()), SetOptions.merge())
    }

    private fun WordDraft.toMap(): Map<String, Any?> = mapOf(
        "kanji" to kanji.trim().ifEmpty { null },
        "kana" to kana.trim(),
        "meaning" to meaning.trim(),
        "meaningEn" to meaningEn.trim(),
        "pos" to pos,
        "display" to display.name,
        "note" to note.trim(),
    )

    private fun SrsState.toMap(): Map<String, Any> = mapOf(
        "due" to due, "intervalDays" to intervalDays, "ease" to ease, "reps" to reps,
        "lapses" to lapses, "lastRating" to lastRating, "lastReviewed" to lastReviewed,
    )

    private fun DocumentSnapshot.srs(): SrsState {
        @Suppress("UNCHECKED_CAST")
        val m = get("srs") as? Map<String, Any?> ?: return SrsState()
        fun num(key: String) = (m[key] as? Number)
        return SrsState(
            due = num("due")?.toLong() ?: 0L,
            intervalDays = num("intervalDays")?.toDouble() ?: 0.0,
            ease = num("ease")?.toDouble() ?: com.kevin77688.jpflashcard.domain.Srs.DEFAULT_EASE,
            reps = num("reps")?.toInt() ?: 0,
            lapses = num("lapses")?.toInt() ?: 0,
            lastRating = num("lastRating")?.toInt() ?: 0,
            lastReviewed = num("lastReviewed")?.toLong() ?: 0L,
        )
    }

    private fun DocumentSnapshot.toWordCard(): WordCard? {
        val kana = getString("kana") ?: return null
        @Suppress("UNCHECKED_CAST")
        return WordCard(
            id = id,
            kanji = getString("kanji"),
            kana = kana,
            meaning = getString("meaning").orEmpty(),
            meaningEn = getString("meaningEn").orEmpty(),
            pos = (get("pos") as? List<String>).orEmpty(),
            display = runCatching { DisplayMode.valueOf(getString("display") ?: "") }.getOrDefault(DisplayMode.FURIGANA),
            note = getString("note").orEmpty(),
            createdAt = getLong("createdAt") ?: 0L,
            updatedAt = getLong("updatedAt") ?: 0L,
            srs = srs(),
        )
    }

    private companion object {
        const val TAG = "CardRepository"
    }
}
