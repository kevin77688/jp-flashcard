package com.kevin77688.jpflashcard.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kevin77688.jpflashcard.ui.theme.JaText
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(activity: ComponentActivity) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // A little stack of cards as the hero illustration.
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                HeroCard("ア", -12f, Modifier.offset(x = (-46).dp, y = 10.dp), MaterialTheme.colorScheme.surfaceContainerHigh)
                HeroCard("食", 10f, Modifier.offset(x = 46.dp, y = 6.dp), MaterialTheme.colorScheme.surfaceContainerHigh)
                HeroCard("あ", 0f, Modifier, MaterialTheme.colorScheme.surfaceContainerLowest, accent = true)
            }
            Spacer(Modifier.height(16.dp))
            Text("日語字卡", style = MaterialTheme.typography.displaySmall)
            Text(
                "五十音、自己的單字本、翻卡複習\n平板和手機自動同步",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        container.auth.signIn(activity).onFailure { error = "登入沒有完成：${it.message ?: it.javaClass.simpleName}" }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.surface)
                else Text("使用 Google 帳號登入", style = MaterialTheme.typography.titleMedium)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun HeroCard(text: String, angle: Float, modifier: Modifier, color: androidx.compose.ui.graphics.Color, accent: Boolean = false) {
    Surface(
        modifier = modifier
            .size(width = 120.dp, height = 160.dp)
            .rotate(angle)
            .shadow(if (accent) 18.dp else 6.dp, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = color,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                style = JaText.large.copy(fontSize = if (accent) JaText.display.fontSize else JaText.large.fontSize),
                color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
