package com.bitdroid.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitdroid.messages.update.Updater
import kotlinx.coroutines.launch

/** Settings card: installed version, check for updates, download + install with progress. */
@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsStateWithLifecycle()
    val installed = "v${Updater.installedName(context)} · build ${Updater.installedCode(context)}"
    val status = when (val s = state) {
        Updater.State.Idle -> "Updates come from this app's GitHub releases."
        Updater.State.Checking -> "Checking…"
        Updater.State.UpToDate -> "You're up to date."
        is Updater.State.Available -> "Update ready: v${s.info.versionName} · build ${s.info.versionCode}"
        is Updater.State.Downloading -> "Downloading… ${(s.progress * 100).toInt()}%"
        Updater.State.Installing -> "Installing…"
        is Updater.State.Error -> s.message
    }
    val button: Pair<String, (() -> Unit)?> = when (val s = state) {
        is Updater.State.Available -> "Install update" to { scope.launch { Updater.install(context, s.info) }; Unit }
        is Updater.State.Error -> if (s.info != null) "Install update" to { scope.launch { Updater.install(context, s.info) }; Unit }
        else "Check for updates" to { scope.launch { Updater.check(context) }; Unit }
        is Updater.State.Downloading, Updater.State.Installing, Updater.State.Checking -> "Please wait…" to null
        else -> "Check for updates" to { scope.launch { Updater.check(context) }; Unit }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text("App updates", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(installed, color = Bit.TextDim, fontSize = 13.5.sp)
        Spacer(Modifier.height(6.dp))
        Text(status, color = if (state is Updater.State.Error) Bit.Error else Bit.TextDim, fontSize = 14.sp)
        val s = state
        if (s is Updater.State.Downloading) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Bit.Raised)) {
                Box(Modifier.fillMaxWidth(s.progress.coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(Bit.Accent))
            }
        }
        Spacer(Modifier.height(12.dp))
        val (label, action) = button
        Box(
            Modifier.clip(CircleShape).background(if (action != null) Bit.Accent else Bit.Raised)
                .clickable(enabled = action != null) { action?.invoke() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(label, color = if (action != null) Bit.OnAccent else Bit.TextDim, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
    }
}
