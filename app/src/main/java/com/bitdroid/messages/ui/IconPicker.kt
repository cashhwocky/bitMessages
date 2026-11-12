package com.bitdroid.messages.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitdroid.messages.data.AppIcons

// ============================================================ app icons

@Composable
fun IconPicker() {
    val context = LocalContext.current
    val view = LocalView.current
    var current by remember { mutableStateOf(AppIcons.current(context)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text("App icon", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("Your home screen may take a moment to update", color = Bit.TextDim, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AppIcons.all.forEach { c ->
                val on = c.key == current
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape)
                            .border(if (on) 3.dp else 1.5.dp, if (on) Bit.Accent else Bit.OutlineSoft, CircleShape)
                            .padding(5.dp).clip(CircleShape)
                            .clickable {
                                if (!on) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    AppIcons.set(context, c.key)
                                    current = c.key
                                }
                            },
                    ) { IconPreview(Color(c.bg), c.fgDark) }
                    Spacer(Modifier.height(6.dp))
                    Text(c.label, style = mono(10f, color = if (on) Bit.Accent else Bit.TextDim, spacing = 0.8f))
                }
            }
        }
    }
}

/** Draws the bubble-with-toggle mark, matching the launcher icon geometry (108-unit viewport). */
@Composable
private fun IconPreview(bg: Color, darkMark: Boolean) {
    val bubble = if (darkMark) Color(0xFF21005D) else Color(0xFFEADDFF)
    val inner = if (darkMark) Color(0xFFEADDFF) else Color(0xFF21005D)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(bg)
        val s = size.width / 66f // icon content spans ~66 of 108 units; zoom to fill the preview
        fun x(v: Float) = (v - 21f) * s
        fun y(v: Float) = (v - 21f) * s
        drawRoundRect(bubble, Offset(x(33f), y(37f)), Size(42f * s, 30f * s), CornerRadius(9f * s))
        val tail = Path().apply {
            moveTo(x(44f), y(66f)); lineTo(x(44f), y(75f)); lineTo(x(53f), y(66f)); close()
        }
        drawPath(tail, bubble)
        drawRoundRect(inner, Offset(x(43.5f), y(47.5f)), Size(21f * s, 9f * s), CornerRadius(4.5f * s))
        drawCircle(bubble, 3f * s, Offset(x(60f), y(52f)))
    }
}
