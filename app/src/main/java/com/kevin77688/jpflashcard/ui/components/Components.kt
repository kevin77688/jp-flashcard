package com.kevin77688.jpflashcard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.kevin77688.jpflashcard.domain.DisplayMode
import com.kevin77688.jpflashcard.domain.Furigana
import com.kevin77688.jpflashcard.ui.theme.JaText

/**
 * Renders a word as kanji, kanji with furigana above, or kana only.
 * [style] sets the base text size; ruby text is scaled from it.
 */
@Composable
fun WordText(
    kanji: String?,
    kana: String,
    mode: DisplayMode,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val effective = if (kanji.isNullOrBlank()) DisplayMode.KANA else mode
    when (effective) {
        DisplayMode.KANA -> Text(kana, style = style, color = color, modifier = modifier, textAlign = TextAlign.Center)
        DisplayMode.KANJI -> Text(kanji!!, style = style, color = color, modifier = modifier, textAlign = TextAlign.Center)
        DisplayMode.FURIGANA -> {
            val rubySize: TextUnit = style.fontSize * 0.42f
            Row(modifier, verticalAlignment = Alignment.Bottom) {
                for (seg in Furigana.split(kanji!!, kana)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            seg.ruby ?: "",
                            style = JaText.ruby.copy(fontSize = rubySize),
                            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color.copy(alpha = 0.7f),
                            maxLines = 1,
                            softWrap = false,
                        )
                        Text(seg.base, style = style, color = color, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
fun SpeakButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "播放發音")
    }
}

/** Thin progress bar coloured from ink to matcha as a card is mastered. */
@Composable
fun MasteryBar(mastery: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val fill = when {
        mastery <= 0f -> Color.Transparent
        mastery < 0.35f -> MaterialTheme.colorScheme.primary
        mastery < 0.75f -> Color(0xFFB8952A)
        else -> MaterialTheme.colorScheme.tertiary
    }
    Box(
        modifier
            .height(4.dp)
            .clip(CircleShape)
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(mastery.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(CircleShape)
                .background(fill),
        )
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceVariant, content: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        trailing?.invoke()
    }
}

/** The small red seal (判子) used as the app's mark. */
@Composable
fun Hanko(text: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = JaText.title.copy(fontSize = (size.value * 0.5f).let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) }), color = MaterialTheme.colorScheme.onPrimary)
    }
}
