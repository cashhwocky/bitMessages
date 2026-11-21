package com.bitdroid.messages.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

class TabItem(val icon: ImageVector, val label: String)

// bitHome tonal colors for the bar.
private val Lav = Color(0xFFCBC2EB)
private val OnLav = Color(0xFF241C4A)
private val BarTint = Color(0xFF2E2842)
private val Ink = Color(0xFFE8E0FF)

/** A soft rounded pill whose edge ripples like the bitHome cookie shapes when [wave] > 0. */
private class WavyPill(private val wave: Float, private val phase: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val steps = 160
        for (i in 0..steps) {
            val t = 2 * PI * i / steps
            val c = cos(t)
            val s = sin(t)
            // superellipse: a rounded rectangle that stays soft at the corners
            val r = (abs(c).pow(4.0) + abs(s).pow(4.0)).pow(-0.25)
            val w = 1.0 + wave * cos(9 * t + phase)
            val x = cx + (size.width / 2f) * (c * r * w).toFloat()
            val y = cy + (size.height / 2f) * (s * r * w).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return Outline.Generic(p)
    }
}

/**
 * Floating bitHome-style tab bar. A lavender pill marks the tab you're on; drag along the bar and it
 * follows your finger, its edge rippling like a bitHome cookie, then settles on the nearest tab.
 * Only the selected tab shows its label. The clover on the right opens search.
 */
@Composable
fun GlassTabBar(
    haze: HazeState,
    tabs: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    searching: Boolean,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    action: TabItem? = null,
    onAction: (() -> Unit)? = null,
    searchContent: @Composable () -> Unit = {},
) {
    val view = LocalView.current
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val barShape = RoundedCornerShape(30.dp)
    val tonal = Modifier.clip(barShape).hazeEffect(haze) {
        blurRadius = 24.dp
        tints = listOf(HazeTint(BarTint.copy(alpha = 0.82f)))
        noiseFactor = 0f
    }
    Row(
        modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(searching, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tabs") { s ->
            if (s) {
                Box(Modifier.fillMaxWidth().height(68.dp).then(tonal).padding(horizontal = 22.dp), contentAlignment = Alignment.CenterStart) {
                    searchContent()
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().height(68.dp).then(tonal).padding(7.dp)) {
                    val density = LocalDensity.current
                    val tabW = with(density) { maxWidth.toPx() } / tabs.size
                    val x = remember { Animatable(selected * tabW) }
                    var dragging by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    LaunchedEffect(selected, tabW) {
                        if (!dragging) x.animateTo(selected * tabW, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow))
                    }
                    val wave by animateFloatAsState(if (dragging) 0.07f else 0f, spring(dampingRatio = 0.5f, stiffness = 300f), label = "wave")
                    val spin = rememberInfiniteTransition(label = "ripple")
                    val phase by spin.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing)), label = "phase")
                    val hover = (x.value / tabW).roundToInt().coerceIn(0, tabs.lastIndex)
                    Box(
                        Modifier.offset { IntOffset(x.value.roundToInt(), 0) }
                            .width(with(density) { tabW.toDp() }).fillMaxHeight()
                            .graphicsLayer { val s2 = 1f + wave * 0.8f; scaleX = s2; scaleY = s2 }
                            .clip(WavyPill(wave, phase))
                            .background(Lav),
                    )
                    Row(
                        Modifier.fillMaxSize().pointerInput(tabs.size, tabW) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = {
                                    val target = (x.value / tabW).roundToInt().coerceIn(0, tabs.lastIndex)
                                    dragging = false
                                    scope.launch { x.animateTo(target * tabW, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow)) }
                                    if (target != currentSelected) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        currentOnSelect(target)
                                    }
                                },
                                onDragCancel = {
                                    dragging = false
                                    scope.launch { x.animateTo(currentSelected * tabW) }
                                },
                            ) { change, dx ->
                                change.consume()
                                scope.launch { x.snapTo((x.value + dx).coerceIn(0f, tabW * tabs.lastIndex)) }
                            }
                        },
                    ) {
                        tabs.forEachIndexed { i, t ->
                            val on = if (dragging) i == hover else i == selected
                            Row(
                                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).squish {
                                    if (i != selected) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onSelect(i)
                                },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(t.icon, t.label, tint = if (on) OnLav else Ink, modifier = Modifier.size(23.dp))
                                AnimatedVisibility(on, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                                    Row {
                                        Spacer(Modifier.width(7.dp))
                                        Text(t.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnLav, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(68.dp).squish(onClick = { if (searching || onAction == null) onSearch() else onAction() }).clip(Shapes.Clover).background(Lav),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (searching) Icons.Rounded.Close else action?.icon ?: Icons.Rounded.Search,
                if (searching) "Close search" else action?.label ?: "Search",
                tint = OnLav, modifier = Modifier.size(26.dp),
            )
        }
    }
}
