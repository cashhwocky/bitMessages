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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
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

    val byId = remember(conversations) { conversations.associateBy { it.threadId } }
    val pinned = remember(pinOrder, conversations) { pinOrder.mapNotNull { byId[it] } }
    val rest = remember(pinOrder, conversations) { conversations.filter { it.threadId !in pinOrder } }
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
            item(key = "header") { Header(vm, unreadTotal, conversations.size) }

            if (query.isNotBlank()) {
                searchResults(titleMatches, hits, query) { vm.setQuery(""); vm.openThread(it) }
            } else {
                if (pinned.isNotEmpty()) {
                    item(key = "pins") {
                        PinGrid(pinned, onOpen = { vm.openThread(it.threadId) }, onLong = openMenu)
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
                    item(key = "label") { SectionRule(if (pinned.isEmpty()) "CHATS" else "ALL CHATS") }
                    itemsIndexed(rest, key = { _, it -> it.threadId }) { index, c ->
                        val surface = rowSurface(listStyle, index == 0, index == rest.lastIndex)
                        SwipeRow(
                            left = swipeLeft,
                            right = swipeRight,
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
                                ConversationRow(c, onClick = { vm.openThread(c.threadId) }, onLong = { openMenu(c) })
                            }
                        }
                    }
                }
            }
        }

        // Fade + floating dock: search pill and new chat.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Bit.Black.copy(alpha = 0.7f), Bit.Black.copy(alpha = 0.85f)))),
        )
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().navigationBarsPadding()
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.weight(1f).height(62.dp).clip(CircleShape).background(Bit.Black.copy(alpha = 0.55f))
                    .border(2.5.dp, Color.White, CircleShape).padding(start = 20.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search", color = Bit.TextDim, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
                Modifier.size(62.dp)
                    .clip(CircleShape).background(Bit.Accent)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        vm.push(Screen.NewChat)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, "Start chat", tint = Bit.OnAccent, modifier = Modifier.size(30.dp))
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
                )
            }
        }
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

@Composable
private fun Header(vm: AppViewModel, unread: Int, chats: Int) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleMark()
            Spacer(Modifier.width(10.dp))
            Text("BITMESSAGES", style = mono(12f, color = Bit.Accent, spacing = 1.6f), modifier = Modifier.weight(1f))
            Box {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).border(2.dp, Bit.OutlineSoft, CircleShape).clickable { menu = true },
                    contentAlignment = Alignment.Center,
                ) { MenuGlyph() }
                DropdownMenu(menu, { menu = false }, containerColor = Bit.SurfaceHigh) {
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
private fun PinGrid(pins: List<Conversation>, onOpen: (Conversation) -> Unit, onLong: (Conversation) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 30.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        pins.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { c ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { Pin(c, onOpen, onLong) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Pin(c: Conversation, onOpen: (Conversation) -> Unit, onLong: (Conversation) -> Unit) {
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
private fun ConversationRow(c: Conversation, onClick: () -> Unit, onLong: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(c.recipients, 54.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.title, fontSize = 17.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    shortTime(context, c.date).uppercase(),
                    style = mono(11.5f, if (c.unread) FontWeight.Bold else FontWeight.Medium, if (c.unread) Bit.Accent else Bit.TextDim, 0.4f),
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
                    color = if (c.hasError) Bit.Error else if (c.unread) Color.White else Bit.TextDim,
                    fontSize = 14.5.sp, fontWeight = if (c.unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (c.unread) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp).clip(CircleShape).background(Bit.Accent).padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val n = maxOf(1, c.unreadCount)
                        Text(if (n > 99) "99+" else "$n", style = mono(11.5f, color = Bit.OnAccent, spacing = 0f))
                    }
                }
            }
        }
    }
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
