package com.kevin77688.jpflashcard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kevin77688.jpflashcard.ui.EditorScreen
import com.kevin77688.jpflashcard.ui.LocalContainer
import com.kevin77688.jpflashcard.ui.LocalIsWide
import com.kevin77688.jpflashcard.ui.theme.JpFlashcardTheme

/**
 * The floating "記單字" window: a translucent activity that opens on top of whatever app is in front
 * (e.g. Duolingo) from the Quick Settings tile or the launcher shortcut, and closes back to it after saving.
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as JpFlashcardApp).container
        setContent {
            JpFlashcardTheme {
                val user by container.auth.userFlow.collectAsState(initial = container.auth.currentUser)
                CompositionLocalProvider(LocalContainer provides container, LocalIsWide provides false) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable(remember { MutableInteractionSource() }, indication = null) { finish() }
                            .safeDrawingPadding()
                            .imePadding(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.background,
                            shadowElevation = 12.dp,
                            modifier = Modifier
                                .widthIn(max = 560.dp)
                                .fillMaxWidth()
                                .fillMaxHeight(0.88f)
                                .padding(horizontal = 12.dp)
                                // Swallow taps so only the scrim closes the window.
                                .clickable(remember { MutableInteractionSource() }, indication = null) {},
                        ) {
                            if (user == null) {
                                Column(Modifier.padding(28.dp)) {
                                    Text("請先打開日語字卡登入 Google 帳號", style = MaterialTheme.typography.titleMedium)
                                    Button(onClick = { finish() }, modifier = Modifier.padding(top = 16.dp)) { Text("好") }
                                }
                            } else {
                                EditorScreen(wordId = null, onClose = { finish() })
                            }
                        }
                    }
                }
            }
        }
    }
}
