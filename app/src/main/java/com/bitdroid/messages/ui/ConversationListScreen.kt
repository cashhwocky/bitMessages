package com.bitdroid.messages.ui

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitdroid.messages.data.Conversation
import com.bitdroid.messages.data.Prefs
import com.bitdroid.messages.data.SearchHit

@Composable
fun ConversationListScreen(vm: AppViewModel) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val hits by vm.hits.collectAsStateWithLifecycle()
    val pinOrder by vm.pins.collectAsStateWithLifecycle()
    val swipeLeft by vm.swipeLeft.collectAsStateWithLifecycle()
    val swipeRight by vm.swipeRight.collectAsStateWithLifecycle()
    val listStyle by vm.listStyle.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    var menuFor by remember { mutableStateOf<Conversation?>(null) }
    var confirmDelete by remember { mutableStateOf<Conversation?>(null) }
    val sel = remember { Selection<Long>() }
    var confirmBulk by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = sel.active) { sel.exit() }

    val byId = remember(conversations) { conversations.associateBy { it.threadId } }
    val pinned = remember(pinOrder, conversations) { pinOrder.mapNotNull { byId[it] } }
    val expressive = Bit.expressive
    // bitHome: unread chats float to the top as their own pink pills.
    val rest = remember(pinOrder, conversations, expressive) {
        val r = conversations.filter { it.threadId !in pinOrder }
        if (expressive) r.filter { it.unread } + r.filterNot { it.unread } else r
    }
    val firstRead = rest.indexOfFirst { !it.unread }
    val unreadTotal = conversations.count { it.unread }

    val titleMatches = remember(query, conversations) {
        if (query.isBlank()) emptyList() else conversations.filter { c ->
            c.title.contains(query, true) ||
                (query.any(Char::isDigit) && c.recipients.any { r -> r.address.filter(Char::isDigit).contains(query.filter(Char::isDigit)) })
        }
    }

    val openMenu: (Conversation) -> Unit = { c ->
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        menuFor = c
    }

    val blur by animateDpAsState(if (menuFor != null) 14.dp else 0.dp, label = "blur")

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().blur(blur),
            contentPadding = PaddingValues(bottom = 130.dp),
        ) {
            item(key = "header") {
                if (sel.active) {
                    SelectBar(
                        count = sel.items.size,
                        allSelected = conversations.isNotEmpty() && sel.items.size == conversations.size,
                        onClose = { sel.exit() },
                        onAll = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            if (sel.items.size == conversations.size) sel.setAll(emptyList()) else sel.setAll(conversations.map { it.threadId })
                        },
                        actions = listOf(
                            SelectAction("Mark as read", Icons.Rounded.Done) {
                                vm.markRead(sel.items.toList())
                                sel.exit()
                            },
                            SelectAction("Delete", Icons.Rounded.Delete, Bit.Error) { confirmBulk = true },
                        ),
                    )
                } else {
                    if (Bit.expressive) HomeHeader(vm, unreadTotal, conversations.size, onSelect = { sel.start() })
                    else Header(vm, unreadTotal, conversations.size, onSelect = { sel.start() })
                }
            }

            if (query.isNotBlank()) {
                searchResults(titleMatches, hits, query) { vm.setQuery(""); vm.openThread(it) }
            } else {
                if (pinned.isNotEmpty()) {
                    item(key = "pins") {
                        PinGrid(
                            pinned, sel,
                            onOpen = { if (sel.active) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); sel.toggle(it.threadId) } else vm.openThread(it.threadId) },
                            onLong = { if (sel.active) sel.toggle(it.threadId) else openMenu(it) },
                        )
                    }
                }
                if (loaded && conversations.isEmpty()) {
                    item(key = "empty") {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 60.dp)) {
                            Text("Nothing\nhere yet", fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 38.sp, lineHeight = 42.sp, color = Bit.Outline)
                            Spacer(Modifier.height(14.dp))
                            Text("Tap + to start a chat.", color = Bit.TextDim, fontSize = 15.sp)
                        }
                    }
                } else if (rest.isNotEmpty()) {
                    if (!expressive) item(key = "label") { SectionRule(if (pinned.isEmpty()) "CHATS" else "ALL CHATS") }
                    else item(key = "gap") { Spacer(Modifier.height(14.dp)) }
                    itemsIndexed(rest, key = { _, it -> it.threadId }) { index, c ->
                        val pink = expressive && c.unread
                        val surface = when {
                            pink -> Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp).clip(CircleShape).background(Bit.Pink)
                            expressive -> homePanel(index == firstRead, index == rest.lastIndex)
                            else -> rowSurface(listStyle, index == 0, index == rest.lastIndex)
                        }
                        SwipeRow(
                            left = if (sel.active) Prefs.Swipe.NONE else swipeLeft,
                            right = if (sel.active) Prefs.Swipe.NONE else swipeRight,
                            modifier = Modifier.animateItem(placementSpec = Bit.placement),
                            onAction = { action ->
                                when (action) {
                                    Prefs.Swipe.READ -> {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        vm.markRead(listOf(c.threadId))
                                    }
                                    Prefs.Swipe.DELETE -> confirmDelete = c
                                    Prefs.Swipe.NONE -> Unit
                                }
                            },
                        ) {
                            Box(surface) {
                                ConversationRow(
                                    c,
                                    pink = pink,
                                    selectMode = sel.active,
                                    selected = c.threadId in sel,
                                    onClick = {
                                        if (sel.active) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            sel.toggle(c.threadId)
                                        } else {
                                            vm.openThread(c.threadId)
                                        }
                                    },
                                    onLong = { if (sel.active) sel.toggle(c.threadId) else openMenu(c) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Fade + floating dock: search pill and new chat.
        val fadeTo = if (expressive) Color(0xFF0F1419) else Bit.Black
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, fadeTo.copy(alpha = 0.7f), fadeTo.copy(alpha = 0.85f)))),
        )
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().navigationBarsPadding()
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                if (expressive) {
                    Modifier.weight(1f).height(62.dp).clip(CircleShape).background(Color(0xFF463E60))
                        .border(2.5.dp, Bit.Accent.copy(alpha = 0.55f), CircleShape).padding(start = 7.dp, end = 6.dp)
                } else {
                    Modifier.weight(1f).height(62.dp).clip(CircleShape).background(Bit.Black.copy(alpha = 0.55f))
                        .border(2.5.dp, Color.White, CircleShape).padding(start = 20.dp, end = 6.dp)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (expressive) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Bit.Accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Search, null, tint = Bit.OnAccent, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                } else {
                    Icon(Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        if (expressive) Text("Search chats", color = Color(0xFFEDE6FF), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        else Text("Search", color = Bit.TextDim, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = vm::setQuery,
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Body, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(Bit.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Close, "Clear", tint = Color.White) }
                }
            }
            Box(
                Modifier.size(if (expressive) 70.dp else 62.dp)
                    .clip(if (expressive) Shapes.Ripple7 else CircleShape).background(if (expressive) Bit.Light else Bit.Accent)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        vm.push(Screen.NewChat)
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (expressive) Icon(Icons.Rounded.Edit, "Start chat", tint = Bit.OnAccent, modifier = Modifier.size(28.dp))
                else Icon(Icons.Rounded.Add, "Start chat", tint = Bit.OnAccent, modifier = Modifier.size(30.dp))
            }
        }

        // Hold menu.
        AnimatedVisibility(menuFor != null, enter = fadeIn(), exit = fadeOut()) {
            val c = menuFor
            Box(
                Modifier.fillMaxSize().background(Bit.Black.copy(alpha = 0.72f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { menuFor = null },
                contentAlignment = Alignment.Center,
            ) {
                if (c != null) HoldMenu(
                    c = c,
                    pinCount = pinOrder.size,
                    onPin = {
                        val ok = vm.togglePin(c.threadId)
                        if (!ok) Toast.makeText(context, "You can pin up to ${Prefs.MAX_PINS} chats", Toast.LENGTH_SHORT).show()
                        else view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        menuFor = null
                    },
                    onRead = { vm.markRead(listOf(c.threadId)); menuFor = null },
                    onBlock = { vm.block(c.recipients.map { it.address }); menuFor = null },
                    onDelete = { confirmDelete = c; menuFor = null },
                    onSelect = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        sel.start(c.threadId)
                        menuFor = null
                    },
                )
            }
        }
    }

    if (confirmBulk) {
        val n = sel.items.size
        AlertDialog(
            onDismissRequest = { confirmBulk = false },
            containerColor = Bit.SurfaceHigh,
            title = { Text(if (n == 1) "Delete 1 chat?" else "Delete $n chats?", fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) },
            text = { Text("This removes the messages from this phone.", color = Bit.TextDim) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteThreads(sel.items.toList())
                    confirmBulk = false
                    sel.exit()
                }) { Text("Delete", color = Bit.Error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmBulk = false }) { Text("Cancel", color = Color.White) } },
        )
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Bit.SurfaceHigh,
            title = { Text("Delete chat with ${c.title}?", fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) },
            text = { Text("This removes the messages from this phone.", color = Bit.TextDim) },
            confirmButton = {
                TextButton(onClick = { vm.deleteThreads(listOf(c.threadId)); confirmDelete = null }) {
                    Text("Delete", color = Bit.Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = Color.White) } },
        )
    }
}

/** bitHome header: a big title tile and a ripple badge with the unread count, like the clock widget. */
@Composable
private fun HomeHeader(vm: AppViewModel, unread: Int, chats: Int, onSelect: () -> Unit) {
    val today = remember { java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(java.util.Date()) }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 12.dp).height(134.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(40.dp, 40.dp, 40.dp, 14.dp)).background(Bit.Tile)
                .padding(start = 22.dp, end = 10.dp, top = 12.dp, bottom = 18.dp),
        ) {
            Text(
                buildAnnotatedString {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Light)); append("Mess"); pop()
                    pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)); append("ages"); pop()
                },
                fontFamily = Body, fontSize = 40.sp, letterSpacing = (-1.5).sp, lineHeight = 44.sp,
                modifier = Modifier.align(Alignment.CenterStart).padding(bottom = 18.dp),
            )
            Text(
                "$today · $chats chats", color = Bit.TextDim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart),
            )
            Box(Modifier.align(Alignment.TopEnd)) { HeaderMenu(vm, onSelect, Color.Transparent) }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(122.dp).clip(Shapes.Sunny).background(Bit.Accent), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (unread > 99) "99+" else "$unread", color = Bit.OnAccent, fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 42.sp)
                Text("NEW", color = Bit.OnAccent, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.5.sp)
            }
        }
    }
}

@Composable
private fun HeaderMenu(vm: AppViewModel, onSelect: () -> Unit, ring: Color = Bit.OutlineSoft) {
    var menu by remember { mutableStateOf(false) }
            Box {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).border(2.dp, ring, CircleShape).clickable { menu = true },
                    contentAlignment = Alignment.Center,
                ) { MenuGlyph() }
                DropdownMenu(menu, { menu = false }, containerColor = Bit.SurfaceHigh) {
                    DropdownMenuItem(text = { Text("Select chats") }, onClick = {
                        menu = false
                        onSelect()
                    })
                    DropdownMenuItem(text = { Text("Mark all as read") }, onClick = {
                        menu = false
                        vm.markRead(vm.conversations.value.filter { it.unread }.map { it.threadId })
                    })
                    DropdownMenuItem(text = { Text("Blocked numbers") }, onClick = {
                        menu = false
                        vm.loadBlocked()
                        vm.push(Screen.Blocked)
                    })
                    DropdownMenuItem(text = { Text("Settings") }, onClick = {
                        menu = false
                        vm.push(Screen.Settings)
                    })
                }
            }
}

@Composable
private fun Header(vm: AppViewModel, unread: Int, chats: Int, onSelect: () -> Unit) {
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleMark()
            Spacer(Modifier.width(10.dp))
            Text("BITMESSAGES", style = mono(12f, color = Bit.Accent, spacing = 1.6f), modifier = Modifier.weight(1f))
            HeaderMenu(vm, onSelect)
        }
        Text(
            "Messages",
            fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 44.sp, letterSpacing = (-1.5).sp, lineHeight = 46.sp,
            modifier = Modifier.padding(start = 20.dp, top = 18.dp),
        )
        val status = buildAnnotatedString {
            if (unread > 0) {
                pushStyle(SpanStyle(color = Bit.Accent))
                append("$unread UNREAD")
                pop()
                append(" · ")
            }
            append("$chats CHATS")
        }
        Text(status, style = mono(12.5f, FontWeight.Medium, spacing = 0.6f), modifier = Modifier.padding(start = 22.dp, top = 8.dp, bottom = 8.dp))
    }
}

/** bitDroid's toggle-switch mark. */
@Composable
fun ToggleMark() {
    Box(Modifier.size(38.dp, 22.dp).clip(CircleShape).background(Bit.Accent)) {
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 3.dp).size(16.dp).clip(CircleShape).background(Bit.Black))
    }
}

@Composable
private fun MenuGlyph() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(16.dp, 2.5.dp).clip(CircleShape).background(Color.White))
        Box(Modifier.size(10.dp, 2.5.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun SectionRule(label: String) {
    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = mono(11.5f, spacing = 1.6f))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Bit.Hairline))
    }
}

// ------------------------------------------------------------------ pins

@Composable
private fun PinGrid(pins: List<Conversation>, sel: Selection<Long>, onOpen: (Conversation) -> Unit, onLong: (Conversation) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = if (Bit.expressive) 20.dp else 30.dp),
        verticalArrangement = Arrangement.spacedBy(if (Bit.expressive) 16.dp else 28.dp),
    ) {
        pins.chunked(3).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { i, c ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (Bit.expressive) HomePin(c, pinShapes[(r * 3 + i) % pinShapes.size], sel.active, c.threadId in sel, onOpen, onLong)
                        else Pin(c, sel.active, c.threadId in sel, onOpen, onLong)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Pin(c: Conversation, selectMode: Boolean, selected: Boolean, onOpen: (Conversation) -> Unit, onLong: (Conversation) -> Unit) {
    val pop = remember(c.threadId) { Animatable(0.6f) }
    LaunchedEffect(c.threadId) { pop.animateTo(1f, Bit.bouncy) }
    val unread = c.unread
    Box(contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .clip(RoundedCornerShape(24.dp))
                .combinedClickable(onClick = { onOpen(c) }, onLongClick = { onLong(c) })
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(106.dp)
                        .clip(CircleShape)
                        .background(if (unread) Bit.Accent else Bit.Raised),
                )
                Avatar(c.recipients, 94.dp, Modifier.border(3.dp, Bit.Black, CircleShape))
                if (selectMode) SelectDot(selected, Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp))
                if (unread) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(x = (-2).dp, y = (-4).dp)
                            .clip(CircleShape).background(Bit.Black).padding(3.dp)
                            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                            .clip(CircleShape).background(Bit.Accent).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val n = maxOf(1, c.unreadCount)
                        Text(if (n > 99) "99+" else "$n", style = mono(11.5f, color = Bit.OnAccent, spacing = 0f))
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(
                c.recipients.singleOrNull()?.name ?: c.title,
                fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 112.dp),
            )
        }
        if (unread && c.snippet.isNotBlank()) {
            // Latest message floats above the avatar.
            Column(
                Modifier.offset(y = (-22).dp).zIndex(3f).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    c.snippet,
                    color = Bit.OnAccent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 112.dp).clip(RoundedCornerShape(13.dp)).background(Bit.Accent).padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Box(Modifier.offset(y = (-6).dp).size(10.dp).rotate(45f).background(Bit.Accent))
            }
        }
    }
}

/** bitHome pins: only ripples, circles and clovers. */
private val pinShapes = listOf(Shapes.Ripple, Shapes.Circle, Shapes.Circle, Shapes.Clover, Shapes.Ripple, Shapes.Circle)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePin(
    c: Conversation,
    shape: androidx.compose.ui.graphics.Shape,
    selectMode: Boolean,
    selected: Boolean,
    onOpen: (Conversation) -> Unit,
    onLong: (Conversation) -> Unit,
) {
    val pop = remember(c.threadId) { Animatable(0.6f) }
    LaunchedEffect(c.threadId) { pop.animateTo(1f, Bit.bouncy) }
    Column(
        Modifier
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(onClick = { onOpen(c) }, onLongClick = { onLong(c) })
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Avatar(c.recipients, 90.dp, shape = shape)
            if (c.unread) Box(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).size(24.dp).clip(Shapes.Sunny).background(Color(0xFFF28FB5)))
            if (selectMode) SelectDot(selected, Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            c.recipients.singleOrNull()?.name ?: c.title,
            fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 108.dp),
        )
    }
}

// ------------------------------------------------------------------ rows

@Composable
private fun SwipeRow(
    left: Prefs.Swipe,
    right: Prefs.Swipe,
    modifier: Modifier,
    onAction: (Prefs.Swipe) -> Unit,
    content: @Composable () -> Unit,
) {
    if (left == Prefs.Swipe.NONE && right == Prefs.Swipe.NONE) {
        Box(modifier) { content() }
        return
    }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> onAction(right)
                SwipeToDismissBoxValue.EndToStart -> onAction(left)
                else -> Unit
            }
            false // always snap back; delete asks first
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = right != Prefs.Swipe.NONE,
        enableDismissFromEndToStart = left != Prefs.Swipe.NONE,
        backgroundContent = {
            val dir = state.dismissDirection
            val action = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> right
                SwipeToDismissBoxValue.EndToStart -> left
                else -> Prefs.Swipe.NONE
            }
            if (action != Prefs.Swipe.NONE) {
                val bg = if (action == Prefs.Swipe.DELETE) Bit.ErrorContainer else Bit.Accent
                val fg = if (action == Prefs.Swipe.DELETE) Color.White else Bit.OnAccent
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 2.dp).clip(RoundedCornerShape(26.dp)).background(bg).padding(horizontal = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (dir == SwipeToDismissBoxValue.StartToEnd) Arrangement.Start else Arrangement.End,
                ) {
                    Icon(if (action == Prefs.Swipe.DELETE) Icons.Rounded.Delete else Icons.Rounded.Done, null, tint = fg)
                    Spacer(Modifier.width(10.dp))
                    Text(if (action == Prefs.Swipe.DELETE) "DELETE" else "MARK READ", style = mono(12f, color = fg, spacing = 1.2f))
                }
            }
        },
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(c: Conversation, pink: Boolean = false, selectMode: Boolean, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val context = LocalContext.current
    val ink = if (pink) Bit.PinkText else Color.White
    Row(
        Modifier.fillMaxWidth().clip(if (pink) CircleShape else RoundedCornerShape(24.dp))
            .background(if (selected) Bit.Accent.copy(alpha = 0.16f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(c.recipients, if (Bit.expressive) 52.dp else 54.dp, shape = if (pink) Shapes.Ripple7 else Shapes.Circle)
            if (selectMode) SelectDot(selected, Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.title, color = ink, fontSize = 17.5.sp, fontWeight = if (Bit.expressive && !c.unread) FontWeight.SemiBold else FontWeight.ExtraBold, letterSpacing = (-0.3).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    shortTime(context, c.date).uppercase(),
                    style = if (Bit.expressive) TextStyle(fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (pink) Bit.PinkText else Bit.TextDim)
                    else mono(11.5f, if (c.unread) FontWeight.Bold else FontWeight.Medium, if (c.unread) Bit.Accent else Bit.TextDim, 0.4f),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.hasError) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = Bit.Error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    if (c.hasError) "Not sent" else c.snippet,
                    color = if (c.hasError) Bit.Error else if (pink) Bit.PinkText.copy(alpha = 0.85f) else if (c.unread) Color.White else Bit.TextDim,
                    fontSize = 14.5.sp, fontWeight = if (c.unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (c.unread) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.defaultMinSize(minWidth = if (pink) 28.dp else 22.dp, minHeight = if (pink) 28.dp else 22.dp).clip(CircleShape)
                            .background(if (pink) Bit.PinkText else Bit.Accent).padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val n = maxOf(1, c.unreadCount)
                        Text(
                            if (n > 99) "99+" else "$n",
                            style = if (pink) TextStyle(fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4A1E3E))
                            else mono(11.5f, color = Bit.OnAccent, spacing = 0f),
                        )
                    }
                }
            }
        }
    }
}

/** bitHome: read chats share one purple-gray tile with one tucked-in corner. */
private fun homePanel(first: Boolean, last: Boolean): Modifier {
    val shape = RoundedCornerShape(
        topStart = if (first) 14.dp else 0.dp, topEnd = if (first) 34.dp else 0.dp,
        bottomStart = if (last) 34.dp else 0.dp, bottomEnd = if (last) 34.dp else 0.dp,
    )
    return Modifier.padding(horizontal = 14.dp).clip(shape).background(Bit.Tile)
        .padding(top = if (first) 4.dp else 0.dp, bottom = if (last) 4.dp else 0.dp)
}

/** Frosted panel: rows share one rounded translucent panel. Glass cards: each row its own card. */
private fun rowSurface(style: Prefs.ListStyle, first: Boolean, last: Boolean): Modifier = when (style) {
    Prefs.ListStyle.CARDS -> Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
        .clip(RoundedCornerShape(24.dp))
        .background(Color.White.copy(alpha = 0.045f))
        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
    Prefs.ListStyle.PANEL -> {
        val r = 28.dp
        val shape = RoundedCornerShape(
            topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
            bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
        )
        Modifier.padding(horizontal = 10.dp).clip(shape).background(Color.White.copy(alpha = 0.04f))
            .padding(top = if (first) 4.dp else 0.dp, bottom = if (last) 4.dp else 0.dp)
    }
}

// ------------------------------------------------------------- hold menu

@Composable
private fun HoldMenu(
    c: Conversation,
    pinCount: Int,
    onPin: () -> Unit,
    onRead: () -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
) {
    val context = LocalContext.current
    val lift = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) { lift.animateTo(1f, Bit.bouncy) }
    Column(
        Modifier.padding(horizontal = 14.dp)
            .graphicsLayer { scaleX = lift.value; scaleY = lift.value; alpha = ((lift.value - 0.85f) / 0.15f).coerceIn(0f, 1f) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Bit.SurfaceHigh).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(c.recipients, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title, fontSize = 16.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.snippet, color = Bit.TextDim, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(shortTime(context, c.date).uppercase(), style = mono(11.5f))
        }
        Column(Modifier.width(260.dp).clip(RoundedCornerShape(24.dp)).background(Bit.SurfaceHigh).padding(8.dp)) {
            val full = !c.pinned && pinCount >= Prefs.MAX_PINS
            MenuRow(
                label = if (c.pinned) "Unpin" else "Pin",
                icon = { Icon(Icons.Rounded.PushPin, null, tint = it) },
                highlight = true,
                trailing = if (c.pinned) null else if (full) "FULL" else "${pinCount + 1} OF ${Prefs.MAX_PINS}",
                dim = full,
                onClick = onPin,
            )
            if (c.unread) MenuRow("Mark as read", { Icon(Icons.Rounded.Done, null, tint = it) }, onClick = onRead)
            MenuRow("Select", { Icon(Icons.Rounded.CheckCircle, null, tint = it) }, onClick = onSelect)
            MenuRow("Block", { Icon(Icons.Rounded.Block, null, tint = it) }, onClick = onBlock)
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).height(1.dp).background(Bit.Outline))
            MenuRow("Delete", { Icon(Icons.Rounded.Delete, null, tint = it) }, color = Bit.Error, onClick = onDelete)
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    icon: @Composable (Color) -> Unit,
    highlight: Boolean = false,
    trailing: String? = null,
    dim: Boolean = false,
    color: Color = Color.White,
    onClick: () -> Unit,
) {
    val bg = if (highlight) Bit.Accent.copy(alpha = if (dim) 0.4f else 1f) else Color.Transparent
    val fg = if (highlight) Bit.OnAccent else color
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(fg)
        Spacer(Modifier.width(14.dp))
        Text(label, color = fg, fontSize = 16.sp, fontWeight = if (highlight) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, style = mono(11f, color = fg.copy(alpha = 0.75f), spacing = 0.6f)) }
    }
}

/** Check mark shown on avatars in select mode: filled when selected, an empty ring when not. */
@Composable
fun SelectDot(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(24.dp).clip(CircleShape).background(Bit.Black).padding(2.dp).clip(CircleShape)
            .then(if (selected) Modifier.background(Bit.Accent) else Modifier.border(2.dp, Color.White, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Done, null, tint = Bit.OnAccent, modifier = Modifier.size(14.dp))
    }
}

// ---------------------------------------------------------------- search

private fun LazyListScope.searchResults(
    conversations: List<Conversation>,
    hits: List<SearchHit>,
    query: String,
    onOpen: (Long) -> Unit,
) {
    if (conversations.isNotEmpty()) {
        item(key = "sc") { SectionRule("CHATS") }
        items(conversations, key = { "c${it.threadId}" }) { c ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(c.threadId) }.padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(c.recipients, 46.dp)
                Spacer(Modifier.width(14.dp))
                Text(c.title, fontSize = 16.5.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
    if (hits.isNotEmpty()) {
        item(key = "sm") { SectionRule("MESSAGES") }
        items(hits.size, key = { "h$it" }) { i ->
            val h = hits[i]
            val context = LocalContext.current
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(h.threadId) }.padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(listOfNotNull(h.contact), 46.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(h.title, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(shortTime(context, h.date).uppercase(), style = mono(11f))
                    }
                    Text(highlight(h.body, query), color = Bit.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (conversations.isEmpty() && hits.isEmpty()) {
        item(key = "none") {
            Text("NO RESULTS", style = mono(12f, spacing = 1.4f), modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 40.dp))
        }
    }
}

private fun highlight(body: String, query: String): AnnotatedString {
    val b = AnnotatedString.Builder()
    val idx = body.indexOf(query, ignoreCase = true)
    if (idx < 0) {
        b.append(body)
    } else {
        val start = maxOf(0, idx - 30)
        b.append((if (start > 0) "…" else "") + body.substring(start, idx))
        b.pushStyle(SpanStyle(color = Bit.Accent, fontWeight = FontWeight.Bold))
        b.append(body.substring(idx, idx + query.length))
        b.pop()
        b.append(body.substring(idx + query.length))
    }
    return b.toAnnotatedString()
}

/** Section header used by the other screens. */
@Composable
fun SectionLabel(text: String) = SectionRule(text.uppercase())
