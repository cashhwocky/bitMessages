package com.bitdroid.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Select mode for chats (keys = thread ids) or messages (keys = message keys). */
class Selection<T> {
    var active by mutableStateOf(false)
        private set
    val items = mutableStateListOf<T>()

    fun start(first: T? = null) {
        active = true
        if (first != null && first !in items) items.add(first)
    }

    fun toggle(item: T) {
        if (!items.remove(item)) items.add(item)
    }

    fun setAll(all: List<T>) {
        items.clear()
        items.addAll(all)
    }

    fun exit() {
        active = false
        items.clear()
    }

    operator fun contains(item: T) = item in items
}

data class SelectAction(val label: String, val icon: ImageVector, val color: Color = Color.White, val onClick: () -> Unit)

/** Replaces a screen's top bar while selecting: ✕, "N SELECTED", ALL / NONE, then actions. */
@Composable
fun SelectBar(count: Int, allSelected: Boolean, onClose: () -> Unit, onAll: () -> Unit, actions: List<SelectAction>) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp)
            .clip(CircleShape).background(Bit.SurfaceHigh).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, "Cancel", tint = Color.White)
        }
        Spacer(Modifier.width(6.dp))
        Text("$count SELECTED", style = mono(12f, color = Color.White, spacing = 1.2f), modifier = Modifier.weight(1f))
        Box(
            Modifier.height(36.dp).clip(CircleShape).border(2.dp, Bit.OutlineSoft, CircleShape).clickable(onClick = onAll).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (allSelected) "NONE" else "ALL", style = mono(11f, color = Bit.Accent, spacing = 1f)) }
        actions.forEach { a ->
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(enabled = count > 0, onClick = a.onClick),
                contentAlignment = Alignment.Center,
            ) { Icon(a.icon, a.label, tint = if (count > 0) a.color else Bit.TextDim) }
        }
    }
}
