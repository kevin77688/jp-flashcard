package com.kevin77688.jpflashcard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Visual language: washi paper, sumi ink, a vermilion (朱) accent like a hanko seal,
 * indigo (藍) for secondary actions and matcha green for "mastered".
 */
private val Shu = Color(0xFFC8402A)
private val ShuLight = Color(0xFFF0745A)
private val Ai = Color(0xFF2F4B7C)
private val AiLight = Color(0xFF9DB4E0)
private val Matcha = Color(0xFF5F8545)
private val MatchaLight = Color(0xFFA9CB8E)
private val Washi = Color(0xFFF7F3EC)
private val Kami = Color(0xFFFFFDF8)
private val Sumi = Color(0xFF1E1B18)

private val Light = lightColorScheme(
    primary = Shu,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDD3),
    onPrimaryContainer = Color(0xFF3F0A02),
    secondary = Ai,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE4F5),
    onSecondaryContainer = Color(0xFF0E1E3B),
    tertiary = Matcha,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDEBCF),
    onTertiaryContainer = Color(0xFF14260A),
    background = Washi,
    onBackground = Sumi,
    surface = Washi,
    onSurface = Sumi,
    surfaceVariant = Color(0xFFEDE5D8),
    onSurfaceVariant = Color(0xFF675E53),
    surfaceContainerLowest = Kami,
    surfaceContainerLow = Color(0xFFFCF8F1),
    surfaceContainer = Color(0xFFF3EDE3),
    surfaceContainerHigh = Color(0xFFEDE6DA),
    surfaceContainerHighest = Color(0xFFE6DED0),
    outline = Color(0xFFB9AD9C),
    outlineVariant = Color(0xFFDDD3C3),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = ShuLight,
    onPrimary = Color(0xFF4A1206),
    primaryContainer = Color(0xFF6E2414),
    onPrimaryContainer = Color(0xFFFFDDD3),
    secondary = AiLight,
    onSecondary = Color(0xFF102347),
    secondaryContainer = Color(0xFF2A3C5E),
    onSecondaryContainer = Color(0xFFDCE4F5),
    tertiary = MatchaLight,
    onTertiary = Color(0xFF1D3410),
    tertiaryContainer = Color(0xFF34502A),
    onTertiaryContainer = Color(0xFFDDEBCF),
    background = Color(0xFF15130F),
    onBackground = Color(0xFFEEE7DB),
    surface = Color(0xFF15130F),
    onSurface = Color(0xFFEEE7DB),
    surfaceVariant = Color(0xFF2B2620),
    onSurfaceVariant = Color(0xFFC9BEAE),
    // Cards use surfaceContainerLowest, so in the dark theme it sits *above* the background like paper.
    surfaceContainerLowest = Color(0xFF231F1A),
    surfaceContainerLow = Color(0xFF1B1814),
    surfaceContainer = Color(0xFF1D1A16),
    surfaceContainerHigh = Color(0xFF2C2721),
    surfaceContainerHighest = Color(0xFF37312A),
    outline = Color(0xFF7D7366),
    outlineVariant = Color(0xFF3F3931),
)

/** Colours for the five familiarity ratings, from 忘了 (1) to 秒答 (5). */
@Immutable
data class RatingColors(val colors: List<Color>)

val LocalRatingColors = staticCompositionLocalOf {
    RatingColors(listOf(Color(0xFFC8402A), Color(0xFFD9822B), Color(0xFFB8952A), Color(0xFF5F8545), Color(0xFF2F7C77)))
}

private val Japanese = LocaleList("ja-JP")
private val TraditionalChinese = LocaleList("zh-TW")

/**
 * Japanese text must carry a ja locale, otherwise on a zh-TW phone shared Han characters
 * (直, 骨, 写…) render with Chinese glyph shapes.
 */
object JaText {
    val display = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Medium, localeList = Japanese, letterSpacing = 0.02.em)
    val large = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Medium, localeList = Japanese)
    val title = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium, localeList = Japanese)
    val body = TextStyle(fontSize = 18.sp, localeList = Japanese)
    val ruby = TextStyle(fontSize = 12.sp, localeList = Japanese)
}

private val base = Typography()
private fun TextStyle.zh() = copy(localeList = TraditionalChinese)

private val AppTypography = Typography(
    displayLarge = base.displayLarge.zh(),
    displayMedium = base.displayMedium.zh(),
    displaySmall = base.displaySmall.zh().copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.zh().copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.zh().copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.zh().copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.zh().copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.zh().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.zh(),
    bodyLarge = base.bodyLarge.zh(),
    bodyMedium = base.bodyMedium.zh(),
    bodySmall = base.bodySmall.zh(),
    labelLarge = base.labelLarge.zh().copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.zh(),
    labelSmall = base.labelSmall.zh(),
)

@Composable
fun JpFlashcardTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (dark) Dark else Light
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

val MonoFamily = FontFamily.Monospace
