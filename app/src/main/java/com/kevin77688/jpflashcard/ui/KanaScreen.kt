package com.kevin77688.jpflashcard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevin77688.jpflashcard.domain.Kana
import com.kevin77688.jpflashcard.domain.KanaGroup
import com.kevin77688.jpflashcard.domain.KanaScript
import com.kevin77688.jpflashcard.domain.KanaSound
import com.kevin77688.jpflashcard.domain.Srs
import com.kevin77688.jpflashcard.domain.SrsState
import com.kevin77688.jpflashcard.ui.components.MasteryBar
import com.kevin77688.jpflashcard.ui.theme.JaText

@Composable
fun KanaScreen(onPractice: (String) -> Unit) {
    val container = LocalContainer.current
    val progress by container.kanaProgress.collectAsState()
    val settings by container.settings.collectAsState()
    var script by rememberSaveable { mutableStateOf(KanaScript.HIRAGANA) }
    var group by rememberSaveable { mutableStateOf(KanaGroup.BASIC) }
    var lastTapped by rememberSaveable { mutableStateOf<String?>(null) }
    val wide = LocalIsWide.current

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = if (wide) 40.dp else 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("五十音", style = MaterialTheme.typography.headlineMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                KanaScript.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = script == s,
                        onClick = { script = s },
                        shape = SegmentedButtonDefaults.itemShape(i, KanaScript.entries.size),
                    ) { Text(s.label) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KanaGroup.entries.forEach { g ->
                    FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g.label) })
                }
            }
            Text(
                "點一下聽發音；顏色條代表熟練度",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kana.grids.getValue(group).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { sound ->
                            if (sound == null) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                KanaCell(
                                    sound = sound,
                                    script = script,
                                    state = progress[sound.cardId(script)],
                                    highlighted = lastTapped == sound.cardId(script),
                                    youon = group == KanaGroup.YOUON,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        lastTapped = sound.cardId(script)
                                        container.speaker.speak(sound.hiragana, settings.speechRate)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Button(
                onClick = { onPractice("kana:${script.prefix}:${group.name}") },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.School, null)
                Spacer(Modifier.width(8.dp))
                Text("練習「${script.label}・${group.label}」")
            }
        }
    }
}

@Composable
private fun KanaCell(
    sound: KanaSound,
    script: KanaScript,
    state: SrsState?,
    highlighted: Boolean,
    youon: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        label = "cell",
    )
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(if (youon) 1.35f else 0.9f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(2.dp))
            Text(sound.text(script), style = JaText.title.copy(fontSize = if (youon) 26.sp else 30.sp))
            Text(sound.romaji, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MasteryBar(Srs.mastery(state), Modifier.fillMaxWidth(0.6f))
        }
    }
}
