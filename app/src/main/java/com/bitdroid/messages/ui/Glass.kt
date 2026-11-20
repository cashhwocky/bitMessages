package com.bitdroid.messages.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

// Fruit glass. Real background blur (Haze) where something scrolls behind a bar; elsewhere a quiet
// frosted surface: a faint fill and a hairline edge that's brightest along the top.

/** Quiet frosted surface for [shape] (no blur). */
fun Modifier.glass(shape: Shape, tint: Color = Color.White, strength: Float = 1f): Modifier =
    this.clip(shape)
        // bitHome tonal tile: deep purple-gray, a hint of lavender at the edge.
        .background(Color(0xFF2A2440).copy(alpha = 0.9f * strength))
        .border(0.75.dp, Color(0xFFCBC2EB).copy(alpha = 0.16f), shape)

/** Real glass: blurs whatever [state]'s source draws behind it, with a dark tint so text stays readable. */
fun Modifier.frosted(state: HazeState, shape: Shape): Modifier =
    this.clip(shape)
        .hazeEffect(state) {
            blurRadius = 28.dp
            tints = listOf(HazeTint(Color(0xFF2E2842).copy(alpha = 0.8f)))
            noiseFactor = 0.03f
        }
        .border(0.75.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.05f))), shape)

/** Kept for callers; the shine is gone (it looked cheap). */
@Suppress("UNUSED_PARAMETER")
fun Modifier.glassShine(alpha: Float = 0f): Modifier = this

/** Kept for callers; the light sweep is gone. */
fun Modifier.glassSweep(): Modifier = this

/** Clickable with a gentle press-in and settle. */
fun Modifier.squish(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = if (pressed) Spring.DampingRatioNoBouncy else 0.6f, stiffness = 600f),
        label = "squish",
    )
    this.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** A round glass button. */
@Composable
fun GlassCircle(size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.size(size).squish(onClick = onClick).glass(CircleShape), contentAlignment = Alignment.Center) { content() }
}
