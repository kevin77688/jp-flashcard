package com.kevin77688.jpflashcard.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.kevin77688.jpflashcard.domain.WordCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kevin77688.jpflashcard.data.Settings
import com.kevin77688.jpflashcard.data.Speaker
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val container = LocalContainer.current
    val settings by container.settings.collectAsState()
    val user by container.auth.userFlow.collectAsState(initial = container.auth.currentUser)
    val tts by container.speaker.state.collectAsState()
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as ComponentActivity
    val wide = LocalIsWide.current

    fun update(f: (Settings) -> Settings) = scope.launch { container.settingsRepo.update(f) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = if (wide) 40.dp else 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("設定", style = MaterialTheme.typography.headlineMedium)

            Group("帳號與同步") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(user?.email.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text("單字和複習進度會在登入同一帳號的裝置間自動同步", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { scope.launch { container.auth.signOut(activity) } }, shape = RoundedCornerShape(12.dp)) { Text("登出") }
                }
            }

            Group("學習") {
                ToggleRow("自動播放發音", "翻到新卡片時自動念一次", settings.autoPlay) { v -> update { it.copy(autoPlay = v) } }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleRow("全部只顯示假名", "暫時不看漢字（每個單字的個別設定會保留）", settings.kanaOnly) { v -> update { it.copy(kanaOnly = v) } }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("語速", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("%.1fx".format(settings.speechRate), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = { container.speaker.speak("おはようございます", settings.speechRate) }, shape = RoundedCornerShape(12.dp)) { Text("試聽") }
                    }
                    Slider(
                        value = settings.speechRate,
                        onValueChange = { v -> update { it.copy(speechRate = (v * 10).toInt() / 10f) } },
                        valueRange = 0.5f..1.3f,
                        steps = 7,
                    )
                    Text(
                        when (tts) {
                            Speaker.State.Ready -> "語音引擎：已就緒（日文）"
                            Speaker.State.MissingVoice -> "語音引擎缺少日文語音，請到系統設定安裝 Google 文字轉語音的日文語音包"
                            Speaker.State.Unavailable -> "找不到可用的語音引擎"
                            Speaker.State.Initializing -> "語音引擎啟動中…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (tts == Speaker.State.Ready) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
            }

            LlmGroup(settings) { f -> update(f) }

            QuickAddGroup()

            DataGroup()

            Group("關於") {
                Text("日語字卡 0.1.0", style = MaterialTheme.typography.titleSmall)
                Text(
                    "詞典資料來自 JMdict（© Electronic Dictionary Research and Development Group，CC BY-SA 4.0），經 jmdict-simplified 轉換。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LlmGroup(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var baseUrl by remember { mutableStateOf(settings.llmBaseUrl) }
    var apiKey by remember { mutableStateOf(settings.llmApiKey) }
    var model by remember { mutableStateOf(settings.llmModel) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // Settings arrive asynchronously from DataStore; fill the fields once they do.
    LaunchedEffect(settings.llmBaseUrl, settings.llmApiKey, settings.llmModel) {
        if (baseUrl.isEmpty()) baseUrl = settings.llmBaseUrl
        if (apiKey.isEmpty()) apiKey = settings.llmApiKey
        if (model.isEmpty()) model = settings.llmModel
    }

    Group("AI 輔助（OpenAI 相容 API）") {
        Text(
            "用來自動填入繁體中文意思、判斷變化形的原形。API Key 只存在這台裝置。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(),
            label = { Text("Base URL") }, placeholder = { Text("https://api.deepseek.com") }, singleLine = true,
            shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        )
        OutlinedTextField(
            apiKey, { apiKey = it }, Modifier.fillMaxWidth(),
            label = { Text("API Key") }, singleLine = true, shape = RoundedCornerShape(14.dp),
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (showKey) "隱藏" else "顯示")
                }
            },
        )
        OutlinedTextField(
            model, { model = it }, Modifier.fillMaxWidth(),
            label = { Text("Model ID") }, placeholder = { Text("deepseek-chat") }, singleLine = true,
            shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(
                onClick = { update { it.copy(llmBaseUrl = baseUrl, llmApiKey = apiKey, llmModel = model) }; result = true to "已儲存" },
                shape = RoundedCornerShape(12.dp),
            ) { Text("儲存") }
            OutlinedButton(
                onClick = {
                    val s = settings.copy(llmBaseUrl = baseUrl, llmApiKey = apiKey, llmModel = model)
                    update { s }
                    testing = true; result = null
                    scope.launch {
                        result = container.llm.ping(s).fold({ true to "連線成功：$it" }, { false to "失敗：${it.message?.take(160)}" })
                        testing = false
                    }
                },
                enabled = !testing && baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (testing) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text("儲存並測試")
            }
        }
        result?.let { (ok, msg) ->
            Text(msg, style = MaterialTheme.typography.bodySmall, color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** CSV export/import through the system file picker (Drive, Downloads, …). */
@Composable
private fun DataGroup() {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val words by container.words.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val snapshot = words
        scope.launch {
            message = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)!!.use { it.write(WordCsv.export(snapshot).toByteArray()) }
                }
                "已匯出 ${snapshot.size} 個單字"
            }.getOrElse { "匯出失敗：${it.message}" }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                }
                val result = WordCsv.import(text)
                // Skip words that are already in the list (same kanji and kana).
                val existing = container.words.value.map { it.kanji.orEmpty() to it.kana }.toSet()
                val fresh = result.drafts.filter { (it.kanji to it.kana) !in existing }.distinctBy { it.kanji to it.kana }
                fresh.forEach { container.cards.addWord(it) }
                buildString {
                    append("已匯入 ${fresh.size} 個單字")
                    val dup = result.drafts.size - fresh.size
                    if (dup > 0) append("，$dup 個已經有了")
                    if (result.skipped > 0) append("，${result.skipped} 列沒有讀音所以略過")
                }
            }.getOrElse { "匯入失敗：${it.message}" }
        }
    }

    Group("資料") {
        Text(
            "CSV 欄位：kanji, kana, meaning, meaning_en, display, note, pos。自己做的表格只要有 kana（可以打羅馬拼音）就能匯入，複習進度不會匯出。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = { exporter.launch("jp-flashcard-words.csv") }, enabled = words.isNotEmpty(), shape = RoundedCornerShape(12.dp)) { Text("匯出 CSV") }
            OutlinedButton(onClick = { importer.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }, shape = RoundedCornerShape(12.dp)) { Text("匯入 CSV") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
    }
}

/** Explains the two ways to open the floating quick-add window, and offers to add the QS tile. */
@Composable
private fun QuickAddGroup() {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    Group("懸浮記單字") {
        Text(
            "在 Duolingo 遇到生字時，不用切換 App：從螢幕頂端往下滑，點快速設定裡的「記單字」，或長按日語字卡圖示選「記單字」，就會在目前畫面上開一個小視窗，存好自動回到原本的 App。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                FilledTonalButton(
                    onClick = {
                        val sbm = context.getSystemService(android.app.StatusBarManager::class.java)
                        sbm.requestAddTileService(
                            android.content.ComponentName(context, com.kevin77688.jpflashcard.QuickAddTileService::class.java),
                            "記單字",
                            android.graphics.drawable.Icon.createWithResource(context, com.kevin77688.jpflashcard.R.drawable.ic_quick_add),
                            context.mainExecutor,
                        ) { result ->
                            message = when (result) {
                                android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "已加入快速設定"
                                android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "快速設定裡已經有了"
                                else -> null
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                ) { Text("加到快速設定") }
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(android.content.Intent(context, com.kevin77688.jpflashcard.QuickAddActivity::class.java))
                },
                shape = RoundedCornerShape(12.dp),
            ) { Text("試開小視窗") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
    }
}
