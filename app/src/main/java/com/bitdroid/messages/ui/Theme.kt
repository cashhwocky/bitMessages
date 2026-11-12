package com.bitdroid.messages.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp

object Bit {
    val Black = Color(0xFF0B0A10)
    val Lilac = Color(0xFFEADDFF)
    val OnLilac = Color(0xFF21005D)
    val Surface = Color(0xFF16151C)
    val SurfaceHigh = Color(0xFF201E27)
    val Theirs = Color(0xFF2A2931)
    val Text = Color(0xFFFFFFFF)
    val TextDim = Color(0xFFA9A6B4)
    val Divider = Color(0xFF26242D)
    val Error = Color(0xFFFFB4AB)
    val ErrorContainer = Color(0xFF93000A)

    /** Shared spring specs so everything moves with the same feel. */
    val bouncy = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
    val smooth = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val placement = spring<IntOffset>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
}

private val scheme = darkColorScheme(
    primary = Bit.Lilac,
    onPrimary = Bit.OnLilac,
    primaryContainer = Bit.Lilac,
    onPrimaryContainer = Bit.OnLilac,
    secondary = Bit.Lilac,
    onSecondary = Bit.OnLilac,
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
    outlineVariant = Bit.Divider,
    error = Bit.Error,
    errorContainer = Bit.ErrorContainer,
)

private val base = Typography()
private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = base.bodyMedium,
    labelSmall = base.labelSmall,
)

@Composable
fun BitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

val BubbleText = TextStyle(fontSize = 16.sp, lineHeight = 21.sp)
