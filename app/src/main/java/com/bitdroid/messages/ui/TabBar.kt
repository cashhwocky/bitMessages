package com.bitdroid.messages.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class TabItem(val icon: ImageVector, val label: String)

/**
 * Floating glass tab bar with a sliding lens. Tap a tab, or drag along the bar and the lens follows
 * your finger, then snaps to the nearest tab when you let go. A separate round search button sits
 * on the right; while [searchContent] is set, it replaces the tabs and the button closes it.
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
    searchContent: @Composable () -> Unit = {},
) {
    val view = LocalView.current
    Row(
        modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(searching, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tabs") { s ->
            if (s) {
                Box(Modifier.fillMaxWidth().height(64.dp).frosted(haze, CircleShape).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                    searchContent()
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp).frosted(haze, CircleShape).padding(5.dp)) {
                    val density = LocalDensity.current
                    val tabW = with(density) { maxWidth.toPx() } / tabs.size
                    val x = remember { Animatable(selected * tabW) }
                    var dragging by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    LaunchedEffect(selected, tabW) {
                        if (!dragging) x.animateTo(selected * tabW, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
                    }
                    val stretch by animateFloatAsState(if (dragging) 1.08f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "stretch")
                    val hover = (x.value / tabW).roundToInt().coerceIn(0, tabs.lastIndex)
                    // The lens.
                    Box(
                        Modifier.offset { IntOffset(x.value.roundToInt(), 0) }
                            .width(with(density) { tabW.toDp() }).fillMaxHeight()
                            .graphicsLayer { scaleX = stretch; scaleY = 1f + (stretch - 1f) * 0.5f }
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.13f))
                            .border(0.75.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.05f))), CircleShape),
                    )
                    Row(
                        Modifier.fillMaxSize().pointerInput(tabs.size, tabW) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = {
                                    val target = (x.value / tabW).roundToInt().coerceIn(0, tabs.lastIndex)
                                    dragging = false
                                    scope.launch { x.animateTo(target * tabW, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) }
                                    if (target != selected) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        onSelect(target)
                                    }
                                },
                                onDragCancel = {
                                    dragging = false
                                    scope.launch { x.animateTo(selected * tabW) }
                                },
                            ) { change, dx ->
                                change.consume()
                                scope.launch { x.snapTo((x.value + dx).coerceIn(0f, tabW * tabs.lastIndex)) }
                            }
                        },
                    ) {
                        tabs.forEachIndexed { i, t ->
                            val on = if (dragging) i == hover else i == selected
                            Column(
                                Modifier.weight(1f).fillMaxHeight().clip(CircleShape).squish {
                                    if (i != selected) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onSelect(i)
                                },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                            ) {
                                Icon(t.icon, t.label, tint = if (on) Bit.Info else Color.White, modifier = Modifier.size(22.dp))
                                Text(t.label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (on) Bit.Info else Color.White)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(64.dp).squish(onClick = onSearch).frosted(haze, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, if (searching) "Close search" else "Search", tint = Color.White, modifier = Modifier.size(24.dp)) }
    }
}
