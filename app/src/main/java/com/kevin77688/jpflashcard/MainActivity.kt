package com.kevin77688.jpflashcard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as JpFlashcardApp).container
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    SkeletonScreen(this, container)
                }
            }
        }
    }
}

/** Temporary screen that proves sign-in, Firestore sync and TTS before the real UI is built. */
@Composable
private fun SkeletonScreen(activity: ComponentActivity, container: com.kevin77688.jpflashcard.data.AppContainer) {
    val scope = rememberCoroutineScope()
    val user by container.auth.userFlow.collectAsState(initial = container.auth.currentUser)
    val ttsState by container.speaker.state.collectAsState()
    var message by remember { mutableStateOf("") }
    val docs = remember { mutableStateListOf<String>() }

    DisposableEffect(user?.uid) {
        val uid = user?.uid
        val reg = uid?.let {
            FirebaseFirestore.getInstance().collection("users").document(it).collection("debug")
                .orderBy("at", Query.Direction.DESCENDING).limit(10)
                .addSnapshotListener { snap, err ->
                    if (err != null) message = "listen error: ${err.message}"
                    docs.clear()
                    snap?.documents?.forEach { d -> docs += "${d.getString("from")} · ${d.getString("text")}" }
                }
        }
        onDispose { reg?.remove() }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("日語字卡 · 骨架測試", style = MaterialTheme.typography.headlineSmall)
        Text("TTS：$ttsState")
        Button(onClick = { container.speaker.speak("こんにちは。わたしはがくせいです。") }) { Text("念：こんにちは") }
        if (user == null) {
            Button(onClick = {
                scope.launch {
                    container.auth.signIn(activity).onFailure { message = "登入失敗：${it.message}" }
                }
            }) { Text("用 Google 登入") }
        } else {
            Text("已登入：${user?.email}")
            Button(onClick = {
                val uid = user?.uid ?: return@Button
                FirebaseFirestore.getInstance().collection("users").document(uid).collection("debug")
                    .add(mapOf("from" to android.os.Build.MODEL, "text" to "テスト", "at" to FieldValue.serverTimestamp()))
            }) { Text("寫入測試資料") }
            docs.forEach { Text(it) }
        }
        if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
    }
}
