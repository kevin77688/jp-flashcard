package com.kevin77688.jpflashcard.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevin77688.jpflashcard.data.AppContainer
import com.kevin77688.jpflashcard.data.posLabel
import com.kevin77688.jpflashcard.domain.DisplayMode
import com.kevin77688.jpflashcard.domain.Kana
import com.kevin77688.jpflashcard.domain.KanaGroup
import com.kevin77688.jpflashcard.domain.KanaScript
import com.kevin77688.jpflashcard.domain.KanaSound
import com.kevin77688.jpflashcard.domain.Srs
import com.kevin77688.jpflashcard.domain.SrsState
import com.kevin77688.jpflashcard.domain.WordCard
import com.kevin77688.jpflashcard.ui.components.Pill
import com.kevin77688.jpflashcard.ui.components.SpeakButton
import com.kevin77688.jpflashcard.ui.components.WordText
import com.kevin77688.jpflashcard.ui.theme.JaText
import com.kevin77688.jpflashcard.ui.theme.LocalRatingColors

private sealed interface ReviewItem {
    val key: String
    val srs: SrsState
    val speech: String

    data class Word(val card: WordCard, override val srs: SrsState) : ReviewItem {
        override val key get() = "w-${card.id}"
        override val speech get() = card.kana
    }

    data class KanaCard(val sound: KanaSound, val script: KanaScript, override val srs: SrsState) : ReviewItem {
        override val key get() = sound.cardId(script)
        override val speech get() = sound.hiragana
    }
}

private val ratingLabels = listOf("忘了", "模糊", "想一下", "記得", "秒答")

private fun buildDeck(deck: String, c: AppContainer, now: Long): List<ReviewItem> {
    val words = c.words.value
    return when {
        deck == "words" -> words.filter { it.srs.due <= now }.sortedBy { it.srs.due }.map { ReviewItem.Word(it, it.srs) }
        deck == "words-all" -> words.shuffled().map { ReviewItem.Word(it, it.srs) }
        deck.startsWith("kana:") -> {
            val (_, prefix, groupName) = deck.split(":")
            val script = KanaScript.entries.first { it.prefix == prefix }
            val sounds = if (groupName == "ALL") Kana.all else Kana.sounds(KanaGroup.valueOf(groupName))
            val progress = c.kanaProgress.value
            val items = sounds.map { ReviewItem.KanaCard(it, script, progress[it.cardId(script)] ?: SrsState()) }
            val due = items.filter { !it.srs.isNew && it.srs.due <= now }.sortedBy { it.srs.due }
            val fresh = items.filter { it.srs.isNew }
            val rest = items.filter { !it.srs.isNew && it.srs.due > now }.shuffled()
            val limit = if (groupName == "ALL") 20 else 25
            (due + fresh).take(limit).ifEmpty { rest.take(limit) }
        }
        else -> emptyList()
    }
}

@Composable
fun ReviewScreen(deck: String, onClose: () -> Unit) {
    val container = LocalContainer.current
    val settings by container.settings.collectAsState()
    val queue = remember(deck) { mutableStateListOf<ReviewItem>().apply { addAll(buildDeck(deck, container, System.currentTimeMillis())) } }
    val initialSize = remember(deck) { queue.size }
    var index by remember(deck) { mutableIntStateOf(0) }
    var flipped by remember(deck) { mutableStateOf(false) }
    val tally = remember(deck) { mutableStateListOf(0, 0, 0, 0, 0) }
    val reviewed = remember(deck) { mutableStateOf(0) }
    val wide = LocalIsWide.current
    val current = queue.getOrNull(index)

    LaunchedEffect(current?.key, index) {
        if (current != null && settings.autoPlay) container.speaker.speak(current.speech, settings.speechRate)
    }

    fun rate(rating: Int) {
        val item = current ?: return
        val now = System.currentTimeMillis()
        val next = Srs.review(item.srs, rating, now)
        when (item) {
            is ReviewItem.Word -> container.cards.saveWordReview(item.card.id, next)
            is ReviewItem.KanaCard -> container.cards.saveKanaReview(item.key, next)
        }
        tally[rating - 1] = tally[rating - 1] + 1
        reviewed.value += 1
        if (rating == 1) {
            // Bring a forgotten card back a few cards later in this session.
            val again = when (item) {
                is ReviewItem.Word -> item.copy(srs = next)
                is ReviewItem.KanaCard -> item.copy(srs = next)
            }
            queue.add(minOf(index + 4, queue.size), again)
        }
        flipped = false
        index += 1
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 760.dp).fillMaxSize().padding(horizontal = if (wide) 32.dp else 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "結束") }
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { if (queue.isEmpty()) 1f else index.toFloat() / queue.size },
                    modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.width(12.dp))
                Text("${minOf(index + 1, queue.size)} / ${queue.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            when {
                initialSize == 0 -> EmptyState(onClose)
                current == null -> DoneState(reviewed.value, tally, onClose)
                else -> {
                    FlipCard(
                        item = current,
                        flipped = flipped,
                        kanaOnly = settings.kanaOnly,
                        onFlip = { flipped = !flipped },
                        onSpeak = { container.speaker.speak(current.speech, settings.speechRate) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    if (flipped) {
                        RatingBar(current.srs, ::rate)
                    } else {
                        Button(
                            onClick = { flipped = true },
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) { Text("翻面看答案", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlipCard(
    item: ReviewItem,
    flipped: Boolean,
    kanaOnly: Boolean,
    onFlip: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier,
) {
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(420), label = "flip")
    val showBack = rotation > 90f
    Box(
        modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            }
            .shadow(10.dp, RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFlip),
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { rotationY = if (showBack) 180f else 0f },
        ) {
            // Washi accent strip along the top edge.
            Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = if (showBack) 0.25f else 0.9f)))
            if (showBack) CardBack(item, onSpeak) else CardFront(item, kanaOnly)
            SpeakButton(onSpeak, Modifier.align(Alignment.TopEnd).padding(16.dp))
        }
    }
}

@Composable
private fun CardFront(item: ReviewItem, kanaOnly: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (item) {
            is ReviewItem.Word -> WordText(
                item.card.kanji, item.card.kana, item.card.effectiveDisplay(kanaOnly),
                JaText.display.copy(fontSize = if (item.card.written.length > 5) 48.sp else 64.sp),
            )
            is ReviewItem.KanaCard -> Text(item.sound.text(item.script), style = JaText.display.copy(fontSize = 120.sp))
        }
        Spacer(Modifier.height(24.dp))
        Text(if (item is ReviewItem.KanaCard) "想一想怎麼唸，再點卡片翻面" else "想一想意思，再點卡片翻面", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardBack(item: ReviewItem, onSpeak: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        when (item) {
            is ReviewItem.Word -> {
                val c = item.card
                WordText(c.kanji, c.kana, if (c.kanji.isNullOrBlank()) DisplayMode.KANA else DisplayMode.FURIGANA, JaText.large)
                Text(c.romaji, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.widthIn(max = 240.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    c.meaning.ifBlank { "（還沒有中文意思）" },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                )
                if (c.pos.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                        c.pos.mapNotNull(::posLabel).distinct().take(3).forEach { Pill(it) }
                    }
                }
                if (c.meaningEn.isNotBlank()) {
                    Text(
                        c.meaningEn.lineSequence().take(2).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (c.note.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Text(c.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                    }
                }
            }
            is ReviewItem.KanaCard -> {
                Text(item.sound.text(item.script), style = JaText.display.copy(fontSize = 72.sp))
                Text(item.sound.romaji, style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                val other = if (item.script == KanaScript.HIRAGANA) item.sound.katakana else item.sound.hiragana
                Text("${if (item.script == KanaScript.HIRAGANA) "片假名" else "平假名"}：$other", style = JaText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val text = item.sound.text(item.script)
                Kana.confusables.firstOrNull { it.first == text || it.second == text }?.let { (a, b) ->
                    val twin = if (a == text) b else a
                    Pill("小心別和「$twin」搞混", container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
private fun RatingBar(state: SrsState, onRate: (Int) -> Unit) {
    val colors = LocalRatingColors.current.colors
    val now = remember(state) { System.currentTimeMillis() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("有多熟？", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(84.dp)) {
            (1..5).forEach { r ->
                val color = colors[r - 1]
                Surface(
                    onClick = { onRate(r) },
                    shape = RoundedCornerShape(18.dp),
                    color = color.copy(alpha = 0.14f),
                    contentColor = color,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("$r", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        Text(ratingLabels[r - 1], style = MaterialTheme.typography.labelLarge)
                        Text(Srs.previewLabel(state, r, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("🎉", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text("目前沒有要複習的卡片", style = MaterialTheme.typography.titleLarge)
        Text("晚點再回來，或先新增一些單字", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClose, shape = RoundedCornerShape(14.dp)) { Text("返回") }
    }
}

@Composable
private fun DoneState(count: Int, tally: List<Int>, onClose: () -> Unit) {
    val colors = LocalRatingColors.current.colors
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("おつかれさま！", style = JaText.title.copy(fontSize = 34.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(6.dp))
        Text("這一輪複習了 $count 張卡片", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        val max = (tally.maxOrNull() ?: 0).coerceAtLeast(1)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(160.dp)) {
            tally.forEachIndexed { i, n ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$n", style = MaterialTheme.typography.labelLarge)
                    Box(
                        Modifier
                            .width(36.dp)
                            .height((8 + 100 * n / max).dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors[i]),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(ratingLabels[i], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onClose, shape = RoundedCornerShape(14.dp), modifier = Modifier.height(52.dp)) { Text("完成") }
    }
}
