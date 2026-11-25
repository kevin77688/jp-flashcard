package com.kevin77688.jpflashcard.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sync state at a glance; tapping it forces a fresh fetch from the server. Changes normally arrive
 * on their own within seconds through Firestore's live listeners, so this is the fallback.
 */
@Composable
fun SyncButton() {
    val container = LocalContainer.current
    val status by container.syncStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val s = status ?: return

    LaunchedEffect(note) { if (note != null) { delay(2500); note = null } }

    val (icon, label, color) = when {
        s.pendingWrites -> Triple(Icons.Rounded.CloudUpload, "上傳中", MaterialTheme.colorScheme.secondary)
        s.fromCache -> Triple(Icons.Rounded.CloudOff, "離線", MaterialTheme.colorScheme.error)
        else -> Triple(Icons.Rounded.CloudDone, "已同步", MaterialTheme.colorScheme.tertiary)
    }
    TextButton(
        onClick = {
            if (busy) return@TextButton
            busy = true
            scope.launch {
                note = runCatching { container.cards.refresh() }.fold({ "剛剛同步" }, { "連不上伺服器" })
                busy = false
            }
        },
        enabled = !busy,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(androidx.compose.ui.Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(icon, null, tint = color, modifier = androidx.compose.ui.Modifier.size(20.dp))
            Spacer(androidx.compose.ui.Modifier.width(6.dp))
            Text(if (busy) "同步中" else note ?: label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
