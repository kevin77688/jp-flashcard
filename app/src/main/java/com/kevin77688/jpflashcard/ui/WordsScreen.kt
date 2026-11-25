package com.kevin77688.jpflashcard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kevin77688.jpflashcard.domain.Romaji

@Composable
fun WordsScreen(onAdd: () -> Unit, onOpen: (String) -> Unit, onReview: (String) -> Unit) {
    val container = LocalContainer.current
    val words by container.words.collectAsState()
    val settings by container.settings.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val wide = LocalIsWide.current

    val filtered = remember(words, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) words else {
            val asKana = Romaji.toKana(q)
            words.filter { w ->
                w.kana.contains(asKana) || Romaji.toHiragana(w.kana).contains(asKana) ||
                    w.kanji.orEmpty().contains(q) || w.meaning.contains(q) || w.romaji.contains(q) ||
                    w.meaningEn.lowercase().contains(q)
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 920.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = if (wide) 40.dp else 16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("單字本", style = MaterialTheme.typography.headlineMedium)
                    Text("${words.size} 個單字", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (words.isNotEmpty()) {
                    TextButton(onClick = { onReview("words-all") }) {
                        Icon(Icons.Rounded.Shuffle, null)
                        Spacer(Modifier.width(6.dp))
                        Text("全部練習")
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜尋：羅馬拼音、假名、漢字或中文") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
            Spacer(Modifier.padding(6.dp))
            AiFillBanner()
            if (filtered.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (words.isEmpty()) "單字本還是空的" else "找不到符合的單字", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (words.isEmpty()) "按右下角的「新增單字」開始吧" else "換個關鍵字試試",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    LazyColumn(contentPadding = PaddingValues(vertical = 6.dp, horizontal = 0.dp)) {
                        items(filtered, key = { it.id }) { w ->
                            WordRow(w, settings.kanaOnly, onClick = { onOpen(w.id) })
                        }
                    }
                }
                // Room below the card so the FAB never covers the last word.
                Spacer(Modifier.height(88.dp))
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("新增單字") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(20.dp),
        )
    }
}

/** Offers to fill in missing Chinese meanings with the configured LLM, and shows its progress. */
@Composable
private fun AiFillBanner() {
    val container = LocalContainer.current
    val words by container.words.collectAsState()
    val settings by container.settings.collectAsState()
    val fill by container.aiFill.collectAsState()
    val missing = words.count { it.meaning.isBlank() }
    val f = fill
    if (f == null && (missing == 0 || !settings.llmConfigured)) return
    if (f != null && !f.running && missing == 0 && f.failed == 0) {
        // Finished cleanly: show a short confirmation until the next change.
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text("已用 AI 補齊 ${f.total} 個單字的中文意思", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
        return
    }
    if (f?.running != true && (missing == 0 || !settings.llmConfigured)) return
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (f?.running == true) "AI 正在填中文意思… ${f.done} / ${f.total}" else "$missing 個單字還沒有中文意思",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (f != null && !f.running && f.failed > 0) {
                        Text("${f.failed} 個沒成功${f.error?.let { "：$it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (f?.running != true) {
                    FilledTonalButton(onClick = { container.fillMissingMeanings() }, shape = RoundedCornerShape(12.dp)) { Text("AI 補齊") }
                }
            }
            if (f?.running == true) {
                LinearProgressIndicator(progress = { f.done.toFloat() / f.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
            }
        }
    }
}
