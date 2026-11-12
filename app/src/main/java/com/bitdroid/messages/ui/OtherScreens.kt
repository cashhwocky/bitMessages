package com.bitdroid.messages.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitdroid.messages.data.Contact
import com.bitdroid.messages.data.Contacts
import com.bitdroid.messages.data.Phone
import com.bitdroid.messages.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun TopBar(title: String, onBack: () -> Unit, action: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(68.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f).padding(start = 4.dp))
        action()
    }
}

// ============================================================== new chat

private fun looksLikeAddress(s: String): Boolean {
    val t = s.trim()
    if (t.contains('@') && t.contains('.')) return true
    val digits = t.count { it.isDigit() }
    return digits >= 3 && t.all { it.isDigit() || it in "+-() ." }
}

@Composable
fun NewChatScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    var input by remember { mutableStateOf("") }
    var dialpad by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<Contact>() }
    var results by remember { mutableStateOf<List<Contacts.PhoneEntry>>(emptyList()) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(input) {
        results = withContext(Dispatchers.IO) { Contacts.search(context, input.trim()) }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun add(name: String?, number: String) {
        if (picked.none { Phone.same(it.address, number) }) {
            picked.add(Contact(Phone.clean(number), name, null, null))
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        input = ""
    }

    fun go() {
        val all = picked.map { it.address }.toMutableList()
        if (looksLikeAddress(input)) all += Phone.clean(input)
        if (all.isEmpty()) return
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        vm.openAddresses(all, null)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("New message", onBack = { vm.back() }) {
            val enabled = picked.isNotEmpty() || looksLikeAddress(input)
            TextButton(
                onClick = ::go,
                enabled = enabled,
                modifier = Modifier.padding(end = 8.dp).clip(CircleShape).background(if (enabled) Bit.Lilac else Bit.SurfaceHigh),
            ) {
                Text(if (picked.size + (if (looksLikeAddress(input)) 1 else 0) > 1) "Group chat" else "Chat", color = if (enabled) Bit.OnLilac else Bit.TextDim, fontWeight = FontWeight.Bold)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Bit.Surface)
                .border(2.5.dp, Color.White, RoundedCornerShape(26.dp))
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("To", color = Bit.Lilac, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (picked.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        items(picked, key = { it.address }) { c ->
                            val pop = remember { Animatable(0.6f) }
                            LaunchedEffect(Unit) { pop.animateTo(1f, Bit.bouncy) }
                            Row(
                                Modifier
                                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                                    .clip(CircleShape)
                                    .background(Bit.Lilac)
                                    .clickable { picked.remove(c) }
                                    .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(c.name ?: Phone.format(context, c.address), color = Bit.OnLilac, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Rounded.Close, "Remove", tint = Bit.OnLilac, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    if (input.isEmpty()) Text("Name, number or email", color = Bit.TextDim, fontSize = 16.sp)
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(Bit.Lilac),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (dialpad) KeyboardType.Phone else KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            if (looksLikeAddress(input)) add(null, input) else go()
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
            IconButton(onClick = { dialpad = !dialpad }) {
                Icon(if (dialpad) Icons.Rounded.Keyboard else Icons.Rounded.Dialpad, "Switch keyboard", tint = Color.White)
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            if (looksLikeAddress(input)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { add(null, input) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Bit.Lilac), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PersonAdd, null, tint = Bit.OnLilac)
                        }
                        Spacer(Modifier.width(14.dp))
                        Text("Send to ${Phone.format(context, input)}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (results.isNotEmpty()) item { SectionLabel(if (input.isBlank()) "Contacts" else "Matches") }
            items(results, key = { it.name + it.number }) { r ->
                Row(
                    Modifier.fillMaxWidth().clickable { add(r.name, r.number) }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(listOf(Contact(r.number, r.name, r.photo, null)), 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("${r.label} · ${Phone.format(context, r.number)}", color = Bit.TextDim, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ============================================================== blocked

@Composable
fun BlockedScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadBlocked() }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Blocked numbers", onBack = { vm.back() })
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(54.dp)
                .clip(CircleShape).background(Bit.Surface).border(2.5.dp, Color.White, CircleShape)
                .padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (input.isEmpty()) Text("Add a number", color = Bit.TextDim, fontSize = 16.sp)
                BasicTextField(
                    value = input, onValueChange = { input = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Bit.Lilac),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextButton(onClick = {
                if (looksLikeAddress(input)) {
                    vm.block(listOf(input))
                    input = ""
                }
            }) { Text("Block", color = Bit.Lilac, fontWeight = FontWeight.Bold) }
        }
        if (blocked.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No blocked numbers", color = Bit.TextDim)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(blocked, key = { it }) { n ->
                    val c = remember(n) { Contacts.lookup(context, n) }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).animateItem(placementSpec = Bit.placement),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(listOf(c), 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name ?: Phone.format(context, n), color = Color.White, fontSize = 16.sp)
                            if (c.name != null) Text(Phone.format(context, n), color = Bit.TextDim, fontSize = 13.sp)
                        }
                        TextButton(onClick = { vm.unblock(n) }) { Text("Unblock", color = Bit.Lilac) }
                    }
                }
            }
        }
    }
}

// ============================================================== settings

@Composable
fun SettingsScreen(vm: AppViewModel, onSetDefault: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var delivery by remember { mutableStateOf(Prefs.deliveryReports(context)) }
    var groupMms by remember { mutableStateOf(Prefs.groupMms(context)) }
    val sims by vm.sims.collectAsStateWithLifecycle()
    val monet by vm.monet.collectAsStateWithLifecycle()
    val listStyle by vm.listStyle.collectAsStateWithLifecycle()
    val swipeLeft by vm.swipeLeft.collectAsStateWithLifecycle()
    val swipeRight by vm.swipeRight.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", onBack = { vm.back() })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item { SectionLabel("Messages") }
            item {
                ToggleRow("Delivery reports", "Show when your texts are delivered", delivery) {
                    delivery = it
                    Prefs.setDeliveryReports(context, it)
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            item {
                ToggleRow("Group texts as MMS", "Everyone sees replies. Off sends separate texts to each person.", groupMms) {
                    groupMms = it
                    Prefs.setGroupMms(context, it)
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            item {
                SettingRow("Blocked numbers", "Texts from these numbers are dropped") {
                    vm.loadBlocked()
                    vm.push(Screen.Blocked)
                }
            }
            item { SectionLabel("RCS · Beta") }
            item { RcsSettingsSection(onTurnOff = onSetDefault) }
            item { SectionLabel("Look") }
            item { IconPicker() }
            item {
                ToggleRow("Monet background", "A faint glow in your wallpaper colors behind the black", monet) {
                    vm.setMonet(it)
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text("Chat list style", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Prefs.ListStyle.PANEL to "FROSTED PANEL", Prefs.ListStyle.CARDS to "GLASS CARDS").forEach { (opt, label) ->
                            val on = opt == listStyle
                            Box(
                                Modifier.height(40.dp).clip(CircleShape)
                                    .then(if (on) Modifier.background(Bit.Accent) else Modifier.border(2.dp, Bit.OutlineSoft, CircleShape))
                                    .clickable {
                                        vm.setListStyle(opt)
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(label, style = mono(11.5f, color = if (on) Bit.OnAccent else Color.White, spacing = 0.8f)) }
                        }
                    }
                }
            }
            item { SectionLabel("Swipe actions") }
            item {
                Text(
                    "Off by default. Choose what swiping a chat does in your list.",
                    color = Bit.TextDim, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            item {
                SwipeChoice("Swipe right", swipeRight) {
                    vm.setSwipeRight(it)
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            item {
                SwipeChoice("Swipe left", swipeLeft) {
                    vm.setSwipeLeft(it)
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            if (sims.isNotEmpty()) {
                item { SectionLabel("SIMs") }
                items(sims, key = { it.subId }) { s ->
                    SettingRow("SIM ${s.slotIndex + 1} · ${s.label}", s.number ?: "Number unknown") {}
                }
            }
            item { SectionLabel("About") }
            item { SettingRow("bitMessages", "SMS, MMS and RCS (beta) for bitDroid · v0.3.0") {} }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Bit.TextDim, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Bit.OnLilac,
                checkedTrackColor = Bit.Lilac,
                uncheckedThumbColor = Bit.TextDim,
                uncheckedTrackColor = Bit.SurfaceHigh,
                uncheckedBorderColor = Bit.TextDim,
            ),
        )
    }
}

@Composable
private fun SwipeChoice(title: String, value: Prefs.Swipe, onChange: (Prefs.Swipe) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Prefs.Swipe.NONE to "OFF", Prefs.Swipe.READ to "MARK READ", Prefs.Swipe.DELETE to "DELETE").forEach { (opt, label) ->
                val on = opt == value
                Box(
                    Modifier.height(40.dp).clip(CircleShape)
                        .then(if (on) Modifier.background(Bit.Accent) else Modifier.border(2.dp, Bit.OutlineSoft, CircleShape))
                        .clickable { onChange(opt) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = mono(11.5f, color = if (on) Bit.OnAccent else Color.White, spacing = 0.8f)) }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp)) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(subtitle, color = Bit.TextDim, fontSize = 14.sp)
    }
}

// ============================================================== setup

@Composable
fun SetupScreen(onSetDefault: () -> Unit, blocked: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val float = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { float.animateTo(1f, Bit.bouncy) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(112.dp).graphicsLayer { scaleX = float.value; scaleY = float.value }
                .clip(RoundedCornerShape(36.dp)).background(Bit.Lilac),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = Bit.OnLilac, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text("bitMessages", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(Modifier.height(12.dp))
        Text(
            "Set bitMessages as your default SMS app to read your texts and send and receive SMS and MMS.",
            color = Bit.TextDim, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 22.sp,
        )
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.fillMaxWidth().height(58.dp).clip(CircleShape).background(Bit.Lilac)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onSetDefault()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("Set as default", color = Bit.OnLilac, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        if (blocked) {
            Spacer(Modifier.height(22.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(2.dp, Bit.Outline, RoundedCornerShape(24.dp)).padding(20.dp),
            ) {
                Text("DIDN'T WORK?", style = mono(11.5f, color = Bit.Accent, spacing = 1.4f))
                Spacer(Modifier.height(8.dp))
                Text(
                    "If Android said the app was denied access, apps installed from an APK file need one extra step:\n\n1. Open App info below\n2. Tap ⋮ in the top corner → Allow restricted settings\n3. Come back and tap Set as default",
                    color = Color.White, fontSize = 14.5.sp, lineHeight = 21.sp,
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().height(50.dp).clip(CircleShape).border(2.5.dp, Color.White, CircleShape)
                        .clickable {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null),
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) { Text("Open App info", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}
