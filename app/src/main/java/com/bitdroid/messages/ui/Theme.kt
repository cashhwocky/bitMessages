package com.bitdroid.messages.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.bitdroid.messages.R

object Bit {
    /** Current theme colors; a snapshot state, so changing it recolors the whole app live. */
    var palette by mutableStateOf(Themes.BITHOME)

    /** Loads the saved theme (Material You colors come from the wallpaper). */
    fun apply(context: android.content.Context) {
        palette = Themes.palette(context, Themes.current(context))
    }

    val Black: Color get() = palette.bg
    val Accent: Color get() = palette.accent
    val OnAccent: Color get() = palette.onAccent
    val AccentGlow: Color get() = palette.accent.copy(alpha = 0.4f)
    val Surface: Color get() = palette.surface
    val SurfaceHigh: Color get() = palette.surfaceHigh
    val Raised: Color get() = palette.raised
    val Outline: Color get() = palette.outline
    val OutlineSoft: Color get() = palette.outlineSoft
    val Hairline: Color get() = palette.hairline
    val Text = Color(0xFFFFFFFF)
    val TextDim: Color get() = palette.textDim
    val Error = Color(0xFFFFB4AB)

    /** bitHome theme: tiles, pastel shapes and the wallpaper gradient instead of flat black. */
    val expressive: Boolean get() = palette.expressive
    val Tile: Color get() = palette.tile
    val Pink: Color get() = palette.pink
    val PinkText: Color get() = palette.pinkText
    val Light: Color get() = palette.light
    val ErrorContainer = Color(0xFF93000A)

    // Kept so older call sites read naturally.
    val Lilac get() = Accent
    val OnLilac get() = OnAccent
    val Theirs get() = Raised
    val Divider get() = Hairline

    val bouncy = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
    val smooth = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val placement = spring<IntOffset>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int): FontFamily = FontFamily(
    weights.map { w ->
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

/** Big, wide display type for titles and names. */
val Display: FontFamily = variable(R.font.unbounded, 600, 700, 800, 900)

/** Monospace for timestamps, counts, labels. */
val Mono: FontFamily = variable(R.font.jetbrains_mono, 500, 700)

/** Body text. */
val Body: FontFamily = variable(R.font.inter, 300, 400, 500, 600, 700, 800)

private fun scheme() = darkColorScheme(
    primary = Bit.Accent,
    onPrimary = Bit.OnAccent,
    primaryContainer = Bit.Accent,
    onPrimaryContainer = Bit.OnAccent,
    secondary = Bit.Accent,
    onSecondary = Bit.OnAccent,
    background = Bit.Black,
    onBackground = Bit.Text,
    surface = Bit.Black,
    onSurface = Bit.Text,
    surfaceVariant = Bit.SurfaceHigh,
    onSurfaceVariant = Bit.TextDim,
    surfaceContainer = Bit.Surface,
    surfaceContainerHigh = Bit.SurfaceHigh,
    surfaceContainerHighest = Bit.SurfaceHigh,
    surfaceContainerLow = Bit.Surface,
    surfaceContainerLowest = Bit.Black,
    outline = Bit.TextDim,
    outlineVariant = Bit.Hairline,
    error = Bit.Error,
    errorContainer = Bit.ErrorContainer,
)

private val base = Typography()
private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = Display, fontWeight = FontWeight.Black),
    headlineMedium = base.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight.ExtraBold),
    titleLarge = base.titleLarge.copy(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
    titleMedium = base.titleMedium.copy(fontFamily = Body, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Body, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = Body),
    bodySmall = base.bodySmall.copy(fontFamily = Body),
    labelLarge = base.labelLarge.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = Body),
    labelSmall = base.labelSmall.copy(fontFamily = Mono),
)

@Composable
fun BitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme(), typography = typography) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides TextStyle(fontFamily = Body, color = Bit.Text),
            content = content,
        )
    }
}

val BubbleText = TextStyle(fontFamily = Body, fontSize = 16.sp, lineHeight = 21.sp)

/** Monospace tag text: TIMES, COUNTS, LABELS. */
fun mono(size: Float, weight: FontWeight = FontWeight.Bold, color: Color = Bit.TextDim, spacing: Float = 0.8f) =
    TextStyle(fontFamily = Mono, fontSize = size.sp, fontWeight = weight, color = color, letterSpacing = spacing.sp)

data class Palette(
    val bg: Color,
    val accent: Color,
    val onAccent: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val raised: Color,
    val outline: Color,
    val outlineSoft: Color,
    val hairline: Color,
    val textDim: Color,
    val expressive: Boolean = false,
    val tile: Color = surfaceHigh,
    val pink: Color = surfaceHigh,
    val pinkText: Color = accent,
    val light: Color = accent,
)

/** App themes: AMOLED black (default), two accent variants, and Material You gray. */
object Themes {
    data class Choice(val key: String, val label: String)

    val all = listOf(
        Choice("bithome", "BITHOME"),
        Choice("amoled", "AMOLED"),
        Choice("lilac", "LILAC"),
        Choice("mint", "MINT"),
        Choice("you", "MATERIAL YOU"),
    )

    private fun black(accent: Long, onAccent: Long) = Palette(
        bg = Color(0xFF000000), accent = Color(accent), onAccent = Color(onAccent),
        surface = Color(0xFF0C0B10), surfaceHigh = Color(0xFF16151C), raised = Color(0xFF1A1920),
        outline = Color(0xFF2E2C38), outlineSoft = Color(0xFF2A2833), hairline = Color(0xFF1A1920),
        textDim = Color(0xFFA9A6B4),
    )

    val AMOLED = black(0xFFA8C7FA, 0xFF062E6F)
    private val LILAC = black(0xFFEADDFF, 0xFF21005D)
    private val MINT = black(0xFFA6F2C9, 0xFF00391F)

    /** Matches the bitDroid home screen: lavender on purple-gray tiles over a purple-to-teal glow. */
    val BITHOME = Palette(
        bg = Color(0xFF120F1C), accent = Color(0xFFCBC2EB), onAccent = Color(0xFF241C4A),
        surface = Color(0xFF2E2842), surfaceHigh = Color(0xFF3A3352), raised = Color(0xFF463F5E),
        outline = Color(0xFF6A6188), outlineSoft = Color(0xFF574F72), hairline = Color(0xFF2A2540),
        textDim = Color(0xFFB9B0D6),
        expressive = true, tile = Color(0xFF463F5E), pink = Color(0xFF5E3A5A), pinkText = Color(0xFFF6D3EA), light = Color(0xFFECE2FF),
    )

    fun current(context: android.content.Context): String =
        com.bitdroid.messages.data.Prefs.get(context).getString("app_theme", "bithome") ?: "bithome"

    fun set(context: android.content.Context, key: String) {
        com.bitdroid.messages.data.Prefs.get(context).edit().putString("app_theme", key).apply()
        Bit.palette = palette(context, key)
    }

    fun palette(context: android.content.Context, key: String): Palette = when (key) {
        "lilac" -> LILAC
        "mint" -> MINT
        "bithome" -> BITHOME
        "you" -> {
            // Android's own dark gray surfaces with the wallpaper accent, like Pixel settings.
            val d = androidx.compose.material3.dynamicDarkColorScheme(context)
            Palette(
                bg = d.surfaceContainer, accent = d.primary, onAccent = d.onPrimary,
                surface = d.surfaceContainerHigh, surfaceHigh = d.surfaceContainerHighest, raised = d.surfaceContainerHighest,
                outline = d.outlineVariant, outlineSoft = d.outlineVariant, hairline = d.surfaceContainerHighest,
                textDim = d.onSurfaceVariant,
            )
        }
        else -> AMOLED
    }
}
