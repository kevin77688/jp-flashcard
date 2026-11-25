package com.kevin77688.jpflashcard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextOverflow
import com.kevin77688.jpflashcard.domain.ReviewMode
import com.kevin77688.jpflashcard.domain.Romaji
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

/** The meaning shown on a word card: Chinese if there is one, otherwise the first English gloss. */
private fun WordCard.shownMeaning(): String = meaning.ifBlank { meaningEn.lineSequence().firstOrNull().orEmpty() }

private fun isKatakana(text: String) = text.all { it in '゠'..'ヿ' }

private fun buildDeck(deck: String, c: AppContainer, now: Long): List<ReviewItem> {
    val words = c.words.value
    val progress = c.kanaProgress.value
    return when {
        deck == "words" -> words.filter { it.srs.due <= now }.sortedBy { it.srs.due }.map { ReviewItem.Word(it, it.srs) }
        deck == "words-all" -> words.shuffled().map { ReviewItem.Word(it, it.srs) }
        deck == CONFUSABLE_DECK -> Kana.confusables.flatMap { listOf(it.first, it.second) }.distinct().mapNotNull { glyph ->
            val script = if (isKatakana(glyph)) KanaScript.KATAKANA else KanaScript.HIRAGANA
            Kana.all.firstOrNull { it.text(script) == glyph }?.let { ReviewItem.KanaCard(it, script, progress[it.cardId(script)] ?: SrsState()) }
        }.shuffled().take(20)
        deck.startsWith("kana:") -> {
            val (_, prefix, groupName) = deck.split(":")
            val script = KanaScript.entries.first { it.prefix == prefix }
            val sounds = if (groupName == "ALL") Kana.all else Kana.sounds(KanaGroup.valueOf(groupName))
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

/** A multiple-choice question. [japanese] says whether the options are kana/words (styled as Japanese). */
private data class Choice(val options: List<String>, val answer: Int, val japanese: Boolean)

private fun buildChoice(item: ReviewItem, words: List<WordCard>, confusableDeck: Boolean): Choice? {
    val (right, pool, japanese) = when (item) {
        is ReviewItem.Word -> Triple(
            item.card.shownMeaning(),
            words.filter { it.id != item.card.id }.map { it.shownMeaning() },
            false,
        )
        is ReviewItem.KanaCard -> {
            val glyph = item.sound.text(item.script)
            val twins = Kana.confusables.filter { it.first == glyph || it.second == glyph }
                .map { if (it.first == glyph) it.second else it.first }
            val sameGroup = Kana.sounds(item.sound.group).filter { it != item.sound }.shuffled()
            if (confusableDeck) {
                // Hear it, then pick the right glyph out of its look-alikes.
                Triple(glyph, twins + sameGroup.map { it.text(item.script) }, true)
            } else {
                val twinRomaji = twins.mapNotNull { t -> Kana.all.firstOrNull { it.hiragana == Romaji.toHiragana(t) }?.romaji }
                Triple(item.sound.romaji, twinRomaji + sameGroup.map { it.romaji }, false)
            }
        }
    }
    if (right.isBlank()) return null
    // Look-alikes come first in the pool, so they always make it into the four options.
    val distractors = pool.filter { it.isNotBlank() && it != right }.distinct().let { all ->
        val priority = all.take(if (item is ReviewItem.KanaCard) 2 else 0)
        (priority + (all - priority.toSet()).shuffled()).take(3)
    }
    if (distractors.isEmpty()) return null
    val options = (distractors + right).shuffled()
    return Choice(options, options.indexOf(right), japanese)
}

const val CONFUSABLE_DECK = "confusable"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(deck: String, onClose: () -> Unit) {
    val container = LocalContainer.current
    val settings by container.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val queue = remember(deck) { mutableStateListOf<ReviewItem>().apply { addAll(buildDeck(deck, container, System.currentTimeMillis())) } }
    val initialSize = remember(deck) { queue.size }
    var index by remember(deck) { mutableIntStateOf(0) }
    var flipped by remember(deck) { mutableStateOf(false) }
    var picked by remember(deck) { mutableStateOf<Int?>(null) }
    val tally = remember(deck) { mutableStateListOf(0, 0, 0, 0, 0) }
    val reviewed = remember(deck) { mutableStateOf(0) }
    val wide = LocalIsWide.current
    val current = queue.getOrNull(index)
    val confusableDeck = deck == CONFUSABLE_DECK
    val mode = if (confusableDeck) ReviewMode.CHOICE else settings.reviewMode
    val choice = remember(current?.key, index, mode) {
        if (mode == ReviewMode.CHOICE && current != null) buildChoice(current, container.words.value, confusableDeck) else null
    }

    // Speaking on the front must not give the answer away: a kana's sound *is* its answer,
    // and in 意→日 the reading is the answer too.
    val speakOnFront = current != null && when {
        mode == ReviewMode.LISTENING || confusableDeck -> true
        mode == ReviewMode.MEANING_TO_JP -> false
        else -> current is ReviewItem.Word
    }

    LaunchedEffect(current?.key, index, mode) {
        if (current == null) return@LaunchedEffect
        if (mode == ReviewMode.LISTENING || confusableDeck || (speakOnFront && settings.autoPlay)) {
            container.speaker.speak(current.speech, settings.speechRate)
        }
    }
    LaunchedEffect(flipped) {
        if (flipped && current != null && !speakOnFront && settings.autoPlay) container.speaker.speak(current.speech, settings.speechRate)
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
        picked = null
        index += 1
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 760.dp).fillMaxSize().padding(horizontal = if (wide) 32.dp else 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
            if (confusableDeck) {
                Text("易混字：聽發音，選出正確的字", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (initialSize > 0 && current != null) {
                ModeSelector(mode) { m ->
                    flipped = false; picked = null
                    scope.launch { container.settingsRepo.update { it.copy(reviewMode = m) } }
                }
            }

            when {
                initialSize == 0 -> EmptyState(onClose)
                current == null -> DoneState(reviewed.value, tally, onClose)
                else -> {
                    FlipCard(
                        item = current,
                        mode = mode,
                        flipped = flipped,
                        kanaOnly = settings.kanaOnly,
                        showSpeakOnFront = speakOnFront,
                        soundPrompt = confusableDeck,
                        onFlip = { if (choice == null || picked != null) flipped = !flipped },
                        onSpeak = { container.speaker.speak(current.speech, settings.speechRate) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    when {
                        choice != null && !flipped -> ChoiceGrid(choice, picked) { i ->
                            picked = i
                            scope.launch { delay(650); flipped = true }
                        }
                        flipped -> {
                            if (choice != null && picked != null) ChoiceResult(choice, picked!!)
                            RatingBar(current.srs, ::rate)
                        }
                        else -> Button(
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
private fun ModeSelector(mode: ReviewMode, onChange: (ReviewMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ReviewMode.entries.forEachIndexed { i, m ->
            SegmentedButton(
                selected = mode == m,
                onClick = { onChange(m) },
                shape = SegmentedButtonDefaults.itemShape(i, ReviewMode.entries.size),
                icon = {},
            ) { Text(m.label, maxLines = 1) }
        }
    }
}

@Composable
private fun ChoiceGrid(choice: Choice, picked: Int?, onPick: (Int) -> Unit) {
    val good = MaterialTheme.colorScheme.tertiary
    val bad = MaterialTheme.colorScheme.error
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        choice.options.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(if (choice.japanese) 76.dp else 68.dp)) {
                pair.forEachIndexed { col, text ->
                    val i = row * 2 + col
                    val tint = when {
                        picked == null -> null
                        i == choice.answer -> good
                        i == picked -> bad
                        else -> null
                    }
                    OutlinedButton(
                        onClick = { if (picked == null) onPick(i) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(if (tint != null) 2.dp else 1.dp, tint ?: MaterialTheme.colorScheme.outlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = tint?.copy(alpha = 0.14f) ?: MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(
                            text,
                            style = if (choice.japanese) JaText.title.copy(fontSize = 32.sp) else MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceResult(choice: Choice, picked: Int) {
    val right = picked == choice.answer
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = (if (right) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error).copy(alpha = 0.14f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (right) "答對了！依照你的把握程度評分" else "選了「${choice.options[picked]}」，正確答案是「${choice.options[choice.answer]}」",
            style = MaterialTheme.typography.titleSmall,
            color = if (right) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun FlipCard(
    item: ReviewItem,
    mode: ReviewMode,
    flipped: Boolean,
    kanaOnly: Boolean,
    showSpeakOnFront: Boolean,
    soundPrompt: Boolean,
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
            if (showBack) CardBack(item, onSpeak) else CardFront(item, mode, kanaOnly, soundPrompt, onSpeak)
            if (showBack || (showSpeakOnFront && mode != ReviewMode.LISTENING)) {
                SpeakButton(onSpeak, Modifier.align(Alignment.TopEnd).padding(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardFront(item: ReviewItem, mode: ReviewMode, kanaOnly: Boolean, soundPrompt: Boolean, onSpeak: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val hint = when {
            // 易混字: the glyph is the answer, so the prompt is only its sound and romaji.
            soundPrompt && item is ReviewItem.KanaCard -> {
                Text(item.sound.romaji, style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                Text(if (item.script == KanaScript.HIRAGANA) "平假名" else "片假名", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                "哪一個才是這個音？"
            }
            mode == ReviewMode.LISTENING -> {
                // Only the sound: a big replay button instead of any text.
                FilledTonalIconButton(onClick = onSpeak, modifier = Modifier.size(120.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, "再聽一次", Modifier.size(56.dp))
                }
                "聽聽看，想一想是哪個字；點卡片翻面"
            }
            mode == ReviewMode.MEANING_TO_JP -> when (item) {
                is ReviewItem.Word -> {
                    Text(
                        item.card.shownMeaning().ifBlank { "（還沒有意思，先看讀音）" },
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                        textAlign = TextAlign.Center,
                    )
                    if (item.card.pos.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                            item.card.pos.mapNotNull(::posLabel).distinct().take(3).forEach { Pill(it) }
                        }
                    }
                    "日文怎麼說？想好再點卡片翻面"
                }
                is ReviewItem.KanaCard -> {
                    Text(item.sound.romaji, style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                    Text(if (item.script == KanaScript.HIRAGANA) "平假名" else "片假名", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    "寫得出來嗎？想好再點卡片翻面"
                }
            }
            else -> {
                when (item) {
                    is ReviewItem.Word -> WordText(
                        item.card.kanji, item.card.kana, item.card.effectiveDisplay(kanaOnly),
                        JaText.display.copy(fontSize = if (item.card.written.length > 5) 48.sp else 64.sp),
                    )
                    is ReviewItem.KanaCard -> Text(item.sound.text(item.script), style = JaText.display.copy(fontSize = 120.sp))
                }
                when {
                    mode == ReviewMode.CHOICE && item is ReviewItem.KanaCard -> "選出正確的讀音"
                    mode == ReviewMode.CHOICE -> "選出正確的意思"
                    item is ReviewItem.KanaCard -> "想一想怎麼唸，再點卡片翻面"
                    else -> "想一想意思，再點卡片翻面"
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
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
