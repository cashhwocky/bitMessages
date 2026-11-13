package com.bitdroid.messages.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Settings → Look → Theme. Each swatch shows that theme's background with its accent dot. */
@Composable
fun ThemePicker() {
    val context = LocalContext.current
    val view = LocalView.current
    var current by remember { mutableStateOf(Themes.current(context)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text("Theme", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Themes.all.forEach { t ->
                val on = t.key == current
                val p = remember(t.key) { Themes.palette(context, t.key) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape)
                            .border(if (on) 3.dp else 1.5.dp, if (on) Bit.Accent else Bit.OutlineSoft, CircleShape)
                            .padding(5.dp).clip(CircleShape).background(p.bg)
                            .border(1.dp, p.outlineSoft, CircleShape)
                            .clickable {
                                if (!on) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    Themes.set(context, t.key)
                                    current = t.key
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(22.dp).clip(CircleShape).background(p.accent)) }
                    Spacer(Modifier.height(6.dp))
                    Text(t.label, style = mono(9.5f, color = if (on) Bit.Accent else Bit.TextDim, spacing = 0.6f))
                }
            }
        }
    }
}
