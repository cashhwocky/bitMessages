package com.bitdroid.messages.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Fruit glass: a frosted fill, a bright rim, a soft highlight across the top, and a squishy press.

/** Frosted glass surface for [shape]. */
fun Modifier.glass(shape: Shape, tint: Color = Color.White, strength: Float = 1f): Modifier =
    this.clip(shape)
        .background(
            Brush.verticalGradient(
                0f to tint.copy(alpha = 0.16f * strength),
                0.45f to tint.copy(alpha = 0.05f * strength),
                1f to tint.copy(alpha = 0.09f * strength),
            ),
        )
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.07f))), shape)
        .glassShine()

/** The soft highlight band across the top of glass (also nice on gradient buttons). */
fun Modifier.glassShine(alpha: Float = 0.26f): Modifier = drawWithContent {
    drawContent()
    val h = size.height * 0.4f
    val inset = size.width * 0.12f
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = alpha), Color.Transparent), startY = 2f, endY = 2f + h),
        topLeft = Offset(inset, 2f), size = Size(size.width - inset * 2, h), cornerRadius = CornerRadius(h, h),
    )
}

/** A slow light sweep across glass, like it's catching the light. */
fun Modifier.glassSweep(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "sweep")
    val x by t.animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "x")
    drawWithContent {
        drawContent()
        val cx = size.width * x
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.09f), Color.Transparent),
                start = Offset(cx - size.width * 0.25f, 0f), end = Offset(cx + size.width * 0.25f, size.height),
            ),
        )
    }
}

/** Clickable that squishes and wobbles back like a drop of liquid. */
fun Modifier.squish(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = if (pressed) Spring.DampingRatioNoBouncy else 0.35f, stiffness = 420f),
        label = "squish",
    )
    this.graphicsLayer { scaleX = s * (if (pressed) 1.04f else 1f); scaleY = s }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** A round glass button. */
@Composable
fun GlassCircle(size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.size(size).squish(onClick = onClick).glass(CircleShape), contentAlignment = Alignment.Center) { content() }
}
