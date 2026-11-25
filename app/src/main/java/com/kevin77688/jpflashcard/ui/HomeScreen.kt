package com.kevin77688.jpflashcard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevin77688.jpflashcard.domain.Kana
import com.kevin77688.jpflashcard.domain.KanaScript
import com.kevin77688.jpflashcard.domain.Srs
import com.kevin77688.jpflashcard.domain.WordCard
import com.kevin77688.jpflashcard.ui.components.MasteryBar
import com.kevin77688.jpflashcard.ui.components.SectionTitle
import com.kevin77688.jpflashcard.ui.components.WordText
import com.kevin77688.jpflashcard.ui.theme.JaText
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    onReview: (String) -> Unit,
    onAddWord: () -> Unit,
    onOpenWord: (String) -> Unit,
    onOpenKana: () -> Unit,
) {
    val container = LocalContainer.current
    val words by container.words.collectAsState()
    val kana by container.kanaProgress.collectAsState()
    val settings by container.settings.collectAsState()
    val wide = LocalIsWide.current
    val now = remember(words, kana) { System.currentTimeMillis() }
    val dueWords = words.count { it.srs.due <= now }
    val mastered = words.count { it.srs.intervalDays >= 21 }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 920.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = if (wide) 40.dp else 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Header()
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ReviewHero(dueWords, words.size, mastered, Modifier.weight(1.3f), onReview, onAddWord)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        KanaScript.entries.forEach { KanaProgressCard(it, kana, now, onReview, Modifier.fillMaxWidth()) }
                    }
                }
            } else {
                ReviewHero(dueWords, words.size, mastered, Modifier.fillMaxWidth(), onReview, onAddWord)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KanaScript.entries.forEach { KanaProgressCard(it, kana, now, onReview, Modifier.weight(1f)) }
                }
            }
            RecentWords(words.take(8), settings.kanaOnly, onOpenWord, onAddWord)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Header() {
    val today = remember { LocalDate.now() }
    val weekday = today.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.TRADITIONAL_CHINESE)
    Column {
        Text(
            "${today.monthValue}月${today.dayOfMonth}日 · $weekday",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("今日の勉強", style = JaText.title.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun ReviewHero(
    due: Int,
    total: Int,
    mastered: Int,
    modifier: Modifier,
    onReview: (String) -> Unit,
    onAddWord: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.primary.copy(alpha = 0.82f))))
            .padding(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("待複習單字", style = MaterialTheme.typography.titleMedium, color = scheme.onPrimary.copy(alpha = 0.85f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$due", style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), color = scheme.onPrimary)
                Text(" 張", style = MaterialTheme.typography.titleLarge, color = scheme.onPrimary.copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 12.dp))
            }
            Text(
                if (total == 0) "還沒有單字，先加幾個多鄰國遇到的字吧" else "單字本共 $total 個 · 已熟練 $mastered 個",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onPrimary.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (due > 0) {
                    Button(
                        onClick = { onReview("words") },
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.onPrimary, contentColor = scheme.primary),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("開始複習")
                    }
                } else if (total > 0) {
                    Button(
                        onClick = { onReview("words-all") },
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.onPrimary, contentColor = scheme.primary),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("全部再練一次") }
                }
                OutlinedButton(
                    onClick = onAddWord,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onPrimary),
                    border = ButtonDefaults.outlinedButtonBorder(true).copy(brush = Brush.linearGradient(listOf(scheme.onPrimary.copy(alpha = 0.6f), scheme.onPrimary.copy(alpha = 0.6f)))),
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("新增單字")
                }
            }
        }
    }
}

@Composable
private fun KanaProgressCard(
    script: KanaScript,
    progress: Map<String, com.kevin77688.jpflashcard.domain.SrsState>,
    now: Long,
    onReview: (String) -> Unit,
    modifier: Modifier,
) {
    val all = Kana.all
    val states = all.map { progress[it.cardId(script)] }
    val learned = states.count { it != null && !it.isNew }
    val due = states.count { it != null && !it.isNew && it.due <= now }
    val mastery = states.map { Srs.mastery(it) }.average().toFloat()
    Card(
        onClick = { onReview("kana:${script.prefix}:ALL") },
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (script == KanaScript.HIRAGANA) "あ" else "ア", style = JaText.large, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(script.label, style = MaterialTheme.typography.titleMedium)
                    Text("已學 $learned / ${all.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            MasteryBar(mastery, Modifier.fillMaxWidth())
            Text(
                if (due > 0) "$due 個待複習 · 點一下練習" else "點一下練習",
                style = MaterialTheme.typography.labelMedium,
                color = if (due > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentWords(words: List<WordCard>, kanaOnly: Boolean, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("最近加入的單字")
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            if (words.isEmpty()) {
                Column(Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📝", style = MaterialTheme.typography.headlineMedium)
                    Text("在多鄰國看到不認得的字？", style = MaterialTheme.typography.titleSmall)
                    Text("點這裡用羅馬拼音加進單字本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                words.forEachIndexed { i, w ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 20.dp))
                    WordRow(w, kanaOnly, onClick = { onOpen(w.id) })
                }
            }
        }
    }
}

@Composable
fun WordRow(w: WordCard, kanaOnly: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.widthIn(min = 96.dp)) {
            WordText(w.kanji, w.kana, w.effectiveDisplay(kanaOnly), JaText.body.copy(fontSize = 22.sp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(w.meaning.ifBlank { w.meaningEn.lineSequence().firstOrNull().orEmpty() }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(w.romaji, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        MasteryBar(Srs.mastery(w.srs), Modifier.width(44.dp))
    }
}
