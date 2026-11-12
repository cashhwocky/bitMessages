package com.bitdroid.messages.ui

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bitdroid.messages.data.AppIcons
import com.bitdroid.messages.data.Rcs

@Composable
fun BetaChip() {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Bit.Accent).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text("BETA", style = mono(10f, color = Bit.OnAccent, spacing = 1.2f))
    }
}

/** Re-reads state whenever the app comes back to the foreground (after the user installs or switches apps). */
@Composable
private fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

// ============================================================ settings

/** The "RCS · BETA" block in Settings. [onTurnOff] asks Android to make bitMessages the default again. */
@Composable
fun RcsSettingsSection(onTurnOff: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val tick = rememberResumeTick()
    var on by remember { mutableStateOf(Rcs.enabled(context)) }
    var confirm by remember { mutableStateOf(false) }
    var missingGm by remember { mutableStateOf(false) }
    val gmInstalled = remember(tick) { Rcs.installed(context, Rcs.GOOGLE_MESSAGES) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RCS chats", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    BetaChip()
                }
                Text("Typing dots, read receipts and HD photos with RCS users", color = Bit.TextDim, fontSize = 14.sp)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = on,
                onCheckedChange = { want ->
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (want) {
                        if (!gmInstalled) missingGm = true else confirm = true
                    } else {
                        on = false
                        Rcs.setEnabled(context, false)
                        onTurnOff()
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Bit.OnAccent, checkedTrackColor = Bit.Accent,
                    uncheckedThumbColor = Bit.TextDim, uncheckedTrackColor = Bit.SurfaceHigh, uncheckedBorderColor = Bit.TextDim,
                ),
            )
        }
        Spacer(Modifier.height(12.dp))
        WhyBeta()
        if (on) {
            Spacer(Modifier.height(16.dp))
            RcsChecklist(tick)
        }
    }

    if (missingGm) {
        AlertDialog(
            onDismissRequest = { missingGm = false },
            containerColor = Bit.SurfaceHigh,
            title = { Text("Google Messages needed", fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) },
            text = { Text("RCS on Android only works through Google Messages. Install it, then come back and flip RCS on again.", color = Bit.TextDim) },
            confirmButton = { TextButton(onClick = { missingGm = false; Rcs.openStore(context, Rcs.GOOGLE_MESSAGES) }) { Text("Get Google Messages", color = Bit.Accent, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { missingGm = false }) { Text("Cancel", color = Color.White) } },
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Bit.SurfaceHigh,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Turn on RCS?", fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    BetaChip()
                }
            },
            text = {
                Text(
                    "Texting moves to Google Messages, the only app Android lets use RCS. " +
                        "Your conversations come with you, and turning RCS off brings everything back to bitMessages.\n\n" +
                        "Next, Google Messages will ask to be your default texting app. Tap Set as default.",
                    color = Bit.TextDim,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    on = true
                    Rcs.setEnabled(context, true)
                    if (!Rcs.openGoogleMessages(context)) Toast.makeText(context, "Couldn't open Google Messages", Toast.LENGTH_SHORT).show()
                }) { Text("Turn on", color = Bit.Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", color = Color.White) } },
        )
    }
}

@Composable
private fun WhyBeta() {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.5.dp, Bit.OutlineSoft, RoundedCornerShape(20.dp))
            .clickable { open = !open }.padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("WHY THIS PROBABLY WON'T WORK", style = mono(11f, color = Bit.Accent, spacing = 1.2f), modifier = Modifier.weight(1f))
            Text(if (open) "−" else "+", style = mono(14f, color = Bit.Accent))
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            Text(
                "• Android only lets Google Messages use RCS. bitMessages can't sign in to RCS itself, so this mode hands texting to Google Messages.\n\n" +
                    "• Google checks the phone before turning RCS on. On custom ROMs like bitDroid that check often fails, and RCS stays stuck on \"Setting up\" or \"Verifying\". If that happens, there's no app-side fix.\n\n" +
                    "• It can work when the checks below all pass, especially Carrier Services and Play Protect certification.\n\n" +
                    "• While RCS is on, bitMessages isn't your texting app. Before turning it off, turn off RCS chats in Google Messages so RCS texts don't get stuck there.",
                fontSize = 14.sp, lineHeight = 20.sp, color = Color.White,
            )
        }
    }
}

// ============================================================ checklist

private enum class Check { OK, FIX, MANUAL }

@Composable
fun RcsChecklist(tick: Int) {
    val context = LocalContext.current
    val gm = remember(tick) { Rcs.installed(context, Rcs.GOOGLE_MESSAGES) }
    val cs = remember(tick) { Rcs.installed(context, Rcs.CARRIER_SERVICES) }
    val gmDefault = remember(tick) { Rcs.googleMessagesIsDefault(context) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("RCS CHECKLIST", style = mono(11.5f, spacing = 1.6f))
        CheckRow(
            if (gm) Check.OK else Check.FIX, "Google Messages installed",
            if (gm) "Ready" else "Needed for RCS", if (gm) null else "GET",
        ) { Rcs.openStore(context, Rcs.GOOGLE_MESSAGES) }
        CheckRow(
            if (cs) Check.OK else Check.FIX, "Carrier Services installed",
            if (cs) "Ready" else "Most common reason RCS fails on custom ROMs", if (cs) null else "GET",
        ) { Rcs.openStore(context, Rcs.CARRIER_SERVICES) }
        CheckRow(
            if (gmDefault) Check.OK else Check.FIX, "Google Messages is your texting app",
            if (gmDefault) "Ready" else "Open it and tap Set as default", if (gmDefault) null else "OPEN",
        ) { Rcs.openGoogleMessages(context) }
        CheckRow(
            Check.MANUAL, "Play Protect certified",
            "Play Store → Settings → About. \"Not certified\" means Google will likely block RCS.", "CHECK",
        ) { Rcs.openPlayStore(context) }
        CheckRow(
            Check.MANUAL, "VoLTE / HD Voice working",
            "AT&T needs it for RCS. Make sure 4G/5G calling is on.", "OPEN",
        ) { Rcs.openMobileNetwork(context) }
        CheckRow(
            Check.MANUAL, "RCS chats turned on",
            "Google Messages → Settings → RCS chats. Wait for \"Connected\" (can take a while).", "OPEN",
        ) { Rcs.openGoogleMessages(context) }
        CheckRow(
            Check.MANUAL, "Stuck on \"Setting up\"?",
            "Clear storage for Carrier Services, then Google Messages, reopen Messages and wait.", "FIX",
        ) { Rcs.openAppInfo(context, Rcs.CARRIER_SERVICES) }
    }
}

@Composable
private fun CheckRow(state: Check, title: String, sub: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (state) {
                Check.OK -> Icons.Rounded.CheckCircle
                Check.FIX -> Icons.Rounded.ErrorOutline
                Check.MANUAL -> Icons.Rounded.HelpOutline
            },
            null,
            tint = when (state) {
                Check.OK -> Bit.Accent
                Check.FIX -> Bit.Error
                Check.MANUAL -> Bit.TextDim
            },
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Bit.TextDim, fontSize = 13.sp, lineHeight = 17.sp)
        }
        if (action != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.height(36.dp).clip(CircleShape).border(2.dp, Bit.OutlineSoft, CircleShape).clickable(onClick = onAction).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(action, style = mono(11f, color = Color.White, spacing = 1f)) }
        }
    }
}

// ============================================================ RCS mode

/** Shown in place of the app while Google Messages is handling texting. */
@Composable
fun RcsModeScreen(onTurnOff: () -> Unit) {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToggleMark()
            Spacer(Modifier.width(10.dp))
            Text("BITMESSAGES", style = mono(12f, color = Bit.Accent, spacing = 1.6f))
        }
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RCS mode", fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 38.sp, letterSpacing = (-1.2).sp)
            Spacer(Modifier.width(10.dp))
            BetaChip()
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Google Messages is handling your texts so RCS can work. Your chats are all there.",
            color = Bit.TextDim, fontSize = 15.sp, lineHeight = 21.sp,
        )
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.fillMaxWidth().height(58.dp).clip(CircleShape).background(Bit.Accent).clickable { Rcs.openGoogleMessages(context) },
            contentAlignment = Alignment.Center,
        ) { Text("Open Google Messages", color = Bit.OnAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().height(54.dp).clip(CircleShape).border(2.5.dp, Color.White, CircleShape).clickable {
                Rcs.setEnabled(context, false)
                onTurnOff()
            },
            contentAlignment = Alignment.Center,
        ) { Text("Turn off RCS · back to bitMessages", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tip: turn off RCS chats in Google Messages first, so RCS texts don't get stuck there.",
            color = Bit.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        WhyBeta()
        Spacer(Modifier.height(16.dp))
        RcsChecklist(tick)
    }
}

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
