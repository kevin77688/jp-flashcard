package com.kevin77688.jpflashcard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevin77688.jpflashcard.data.DictEntry
import com.kevin77688.jpflashcard.data.posLabel
import com.kevin77688.jpflashcard.domain.DisplayMode
import com.kevin77688.jpflashcard.domain.Romaji
import com.kevin77688.jpflashcard.domain.WordDraft
import com.kevin77688.jpflashcard.ui.components.Pill
import com.kevin77688.jpflashcard.ui.components.SectionTitle
import com.kevin77688.jpflashcard.ui.components.SpeakButton
import com.kevin77688.jpflashcard.ui.components.WordText
import com.kevin77688.jpflashcard.ui.theme.JaText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(wordId: String?, onClose: () -> Unit) {
    val container = LocalContainer.current
    val words by container.words.collectAsState()
    val settings by container.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val wide = LocalIsWide.current

    var input by rememberSaveable { mutableStateOf("") }
    var katakana by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf(WordDraft()) }
    var loaded by remember { mutableStateOf(wordId == null) }
    var candidates by remember { mutableStateOf<List<DictEntry>>(emptyList()) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var baseFormHint by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val typingRomaji = input.any { it in 'a'..'z' || it in 'A'..'Z' }
    val livePreview = if (typingRomaji) Romaji.toKana(input, katakana, final = false) else input
    val converted = if (typingRomaji) Romaji.toKana(input, katakana, final = true) else input.trim()

    LaunchedEffect(wordId, words) {
        if (!loaded) {
            words.firstOrNull { it.id == wordId }?.let { w ->
                draft = WordDraft(w.kanji.orEmpty(), w.kana, w.meaning, w.meaningEn, w.pos, w.display, w.note)
                loaded = true
            }
        }
    }

    LaunchedEffect(converted) {
        if (converted.isBlank()) { candidates = emptyList(); return@LaunchedEffect }
        delay(180)
        candidates = runCatching { container.dictionary.search(converted, limit = 30) }
            .onFailure { android.util.Log.e("Editor", "Dictionary search failed", it) }
            .getOrDefault(emptyList())
    }

    fun askAi(d: WordDraft) {
        if (!settings.llmConfigured || d.kana.isBlank()) return
        aiBusy = true; aiError = null
        scope.launch {
            container.llm.suggest(settings, d.kanji.ifBlank { d.kana }, d.kana, d.meaningEn)
                .onSuccess { s ->
                    draft = draft.copy(
                        meaning = s.meaning.ifBlank { draft.meaning },
                        note = if (draft.note.isBlank()) s.note else draft.note,
                    )
                    val form = s.dictionaryForm
                    if (form != null && form != d.kanji && form != d.kana) baseFormHint = form to (s.dictionaryReading ?: "")
                }
                .onFailure { aiError = "AI 沒有回應：${it.message?.take(120)}" }
            aiBusy = false
        }
    }

    fun choose(entry: DictEntry?) {
        val next = if (entry == null) {
            draft.copy(kanji = "", kana = converted, display = DisplayMode.KANA, meaningEn = "", pos = emptyList())
        } else {
            draft.copy(
                kanji = entry.kanji.orEmpty(),
                kana = entry.reading,
                meaningEn = entry.gloss,
                pos = entry.pos,
                display = if (entry.kanji == null || entry.usuallyKana) DisplayMode.KANA else DisplayMode.FURIGANA,
            )
        }
        draft = next
        baseFormHint = null
        container.speaker.speak(next.kana, settings.speechRate)
        if (next.meaning.isBlank()) askAi(next)
    }

    val canSave = draft.kana.isNotBlank() && loaded

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (wordId == null) "新增單字" else "編輯單字") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "關閉") } },
                actions = {
                    if (wordId != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "刪除") }
                    Button(
                        onClick = {
                            if (wordId == null) container.cards.addWord(draft) else container.cards.updateWord(wordId, draft)
                            onClose()
                        },
                        enabled = canSave,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 12.dp),
                    ) { Text("儲存") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val inputSection: @Composable () -> Unit = {
            InputSection(
                input = input,
                onInput = { input = it },
                katakana = katakana,
                onKatakana = { katakana = it },
                preview = livePreview,
                onSpeak = { container.speaker.speak(converted, settings.speechRate) },
            )
        }
        val candidateSection: @Composable () -> Unit = {
            if (converted.isNotBlank()) CandidateSection(converted, candidates, draft, ::choose)
        }
        val cardSection: @Composable () -> Unit = {
            CardSection(
                draft = draft,
                onDraft = { draft = it },
                kanaOnly = settings.kanaOnly,
                llmConfigured = settings.llmConfigured,
                aiBusy = aiBusy,
                aiError = aiError,
                onAskAi = { askAi(draft) },
                baseFormHint = baseFormHint,
                onApplyBaseForm = {
                    baseFormHint?.let { (form, reading) ->
                        val isKana = Romaji.isKana(form)
                        draft = draft.copy(
                            kanji = if (isKana) "" else form,
                            kana = reading.ifBlank { if (isKana) form else draft.kana },
                            display = if (isKana) DisplayMode.KANA else draft.display,
                        )
                    }
                    baseFormHint = null
                },
                onSpeak = { container.speaker.speak(draft.kana, settings.speechRate) },
            )
        }

        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            if (wide) {
                Row(
                    Modifier.widthIn(max = 1100.dp).fillMaxSize().padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) { inputSection(); candidateSection() }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) { cardSection() }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    inputSection(); candidateSection(); cardSection()
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (confirmDelete && wordId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除這個單字？") },
            text = { Text("「${draft.kanji.ifBlank { draft.kana }}」和它的複習紀錄會從所有裝置移除。") },
            confirmButton = {
                TextButton(onClick = { container.cards.deleteWord(wordId); confirmDelete = false; onClose() }) {
                    Text("刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun InputSection(
    input: String,
    onInput: (String) -> Unit,
    katakana: Boolean,
    onKatakana: (Boolean) -> Unit,
    preview: String,
    onSpeak: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("① 用羅馬拼音輸入讀音")
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("例如 taberu、watashi、ko-hi-") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done,
            ),
            trailingIcon = {
                SingleChoiceSegmentedButtonRow(Modifier.padding(end = 6.dp).width(112.dp)) {
                    SegmentedButton(selected = !katakana, onClick = { onKatakana(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2), icon = {}) {
                        Text("あ", style = JaText.body)
                    }
                    SegmentedButton(selected = katakana, onClick = { onKatakana(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2), icon = {}) {
                        Text("ア", style = JaText.body)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preview.ifEmpty { "かな" },
                    style = JaText.large,
                    color = if (preview.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (preview.isNotEmpty()) SpeakButton(onSpeak)
            }
        }
        Text(
            "長音打 -，促音重複子音（gakkou），ん 打 nn 或 n'，小字前加 x（xa → ぁ）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateSection(converted: String, candidates: List<DictEntry>, draft: WordDraft, onChoose: (DictEntry?) -> Unit) {
    var showAll by remember(converted) { mutableStateOf(false) }
    val shown = if (showAll) candidates else candidates.take(6)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("② 選擇寫法")
        val kanaOnlySelected = draft.kanji.isBlank() && draft.kana == converted
        CandidateCard(selected = kanaOnlySelected, onClick = { onChoose(null) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(converted, style = JaText.title)
                Spacer(Modifier.width(12.dp))
                Pill("只用假名")
            }
        }
        shown.forEach { e ->
            val selected = draft.kana == e.reading && draft.kanji == e.kanji.orEmpty() && !(e.kanji == null && kanaOnlySelected)
            CandidateCard(selected = selected, onClick = { onChoose(e) }) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (e.kanji != null) {
                            Text(e.kanji, style = JaText.title)
                            Spacer(Modifier.width(10.dp))
                            Text(e.reading, style = JaText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(e.reading, style = JaText.title)
                        }
                        Spacer(Modifier.weight(1f))
                        if (e.common) Pill("常用", container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        e.pos.mapNotNull(::posLabel).distinct().take(3).forEach { Pill(it) }
                        if (e.usuallyKana && e.kanji != null) Pill("通常寫假名")
                    }
                    Text(
                        e.gloss.lineSequence().take(2).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (!showAll && candidates.size > shown.size) {
            TextButton(onClick = { showAll = true }) { Text("顯示更多（${candidates.size - shown.size}）") }
        }
        if (candidates.isEmpty()) {
            Text("詞典裡找不到，可以直接用假名或自己填漢字", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CandidateCard(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) { content() }
    }
}

@Composable
private fun CardSection(
    draft: WordDraft,
    onDraft: (WordDraft) -> Unit,
    kanaOnly: Boolean,
    llmConfigured: Boolean,
    aiBusy: Boolean,
    aiError: String?,
    onAskAi: () -> Unit,
    baseFormHint: Pair<String, String>?,
    onApplyBaseForm: () -> Unit,
    onSpeak: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("③ 字卡內容")
        // Live preview of the front of the card.
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = 150.dp).padding(20.dp), contentAlignment = Alignment.Center) {
                if (draft.kana.isBlank()) {
                    Text("先在上面輸入讀音", color = MaterialTheme.colorScheme.outline)
                } else {
                    WordText(
                        draft.kanji.ifBlank { null },
                        draft.kana,
                        if (kanaOnly) DisplayMode.KANA else draft.display,
                        JaText.large.copy(fontSize = 44.sp),
                    )
                    SpeakButton(onSpeak, Modifier.align(Alignment.BottomEnd), size = 40.dp)
                }
            }
        }

        Text("卡片正面顯示", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            DisplayMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = draft.display == mode,
                    onClick = { onDraft(draft.copy(display = mode)) },
                    enabled = mode == DisplayMode.KANA || draft.kanji.isNotBlank(),
                    shape = SegmentedButtonDefaults.itemShape(i, DisplayMode.entries.size),
                ) { Text(mode.label, maxLines = 1) }
            }
        }
        if (kanaOnly) {
            Text("目前在設定裡開啟了「全部只顯示假名」", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("漢字（可留空）", draft.kanji, { onDraft(draft.copy(kanji = it)) }, Modifier.weight(1f), japanese = true)
            Field("讀音（假名）", draft.kana, { onDraft(draft.copy(kana = it)) }, Modifier.weight(1f), japanese = true)
        }

        AnimatedVisibility(baseFormHint != null) {
            baseFormHint?.let { (form, reading) ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "這看起來是「$form${if (reading.isNotBlank() && reading != form) "（$reading）" else ""}」的變化形",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onApplyBaseForm) { Text("改存原形") }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("中文意思", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (llmConfigured) {
                    FilledTonalButton(onClick = onAskAi, enabled = !aiBusy && draft.kana.isNotBlank(), shape = RoundedCornerShape(12.dp)) {
                        if (aiBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (aiBusy) "AI 查詢中…" else "AI 填入")
                    }
                }
            }
            Field("例如：吃；食用", draft.meaning, { onDraft(draft.copy(meaning = it)) }, Modifier.fillMaxWidth(), label = false, big = true)
            aiError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (!llmConfigured) {
                Text("在「設定」填入 AI 的 Base URL / API Key，就能自動填中文意思", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (draft.meaningEn.isNotBlank()) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("詞典（英文）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(draft.meaningEn.replace("\n", "\n· ").let { "· $it" }, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Field("備註（例句、用法…）", draft.note, { onDraft(draft.copy(note = it)) }, Modifier.fillMaxWidth(), singleLine = false)
    }
}

@Composable
private fun Field(
    hint: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier,
    japanese: Boolean = false,
    label: Boolean = true,
    singleLine: Boolean = true,
    big: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = if (label) ({ Text(hint) }) else null,
        placeholder = if (!label) ({ Text(hint) }) else null,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        textStyle = when {
            japanese -> JaText.body
            big -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal)
            else -> MaterialTheme.typography.bodyLarge
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}
