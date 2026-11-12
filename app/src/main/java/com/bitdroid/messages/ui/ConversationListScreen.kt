package com.bitdroid.messages.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitdroid.messages.data.Conversation
import com.bitdroid.messages.data.SearchHit

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(vm: AppViewModel) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val hits by vm.hits.collectAsStateWithLifecycle()
    val selected = remember { mutableStateListOf<Long>() }
    val listState = rememberLazyListState()
    val view = LocalView.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    val titleMatches = remember(query, conversations) {
        if (query.isBlank()) emptyList()
        else conversations.filter { c ->
            c.title.contains(query, true) || c.recipients.any { it.address.filter(Char::isDigit).contains(query.filter(Char::isDigit).ifEmpty { "\u0000" }) }
        }
    }

    Box(Modifier.fillMaxSize().background(Bit.Black)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Header / selection bar
            Box(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp)) {
                AnimatedVisibility(
                    visible = selected.isEmpty(),
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Messages",
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                        )
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Menu", tint = Color.White) }
                            DropdownMenu(menu, { menu = false }, containerColor = Bit.SurfaceHigh) {
                                DropdownMenuItem(text = { Text("Mark all as read") }, onClick = {
                                    menu = false
                                    vm.markRead(conversations.filter { it.unread }.map { it.threadId })
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
                }
                AnimatedVisibility(
                    visible = selected.isNotEmpty(),
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Row(
                        Modifier.fillMaxWidth().clip(CircleShape).background(Bit.SurfaceHigh).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { selected.clear() }) { Icon(Icons.Rounded.Close, "Cancel", tint = Color.White) }
                        Text("${selected.size} selected", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.markRead(selected.toList()); selected.clear() }) {
                            Icon(Icons.Rounded.DoneAll, "Mark as read", tint = Color.White)
                        }
                        IconButton(onClick = {
                            vm.block(conversations.filter { it.threadId in selected }.flatMap { c -> c.recipients.map { it.address } })
                            selected.clear()
                        }) { Icon(Icons.Rounded.Block, "Block", tint = Color.White) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete", tint = Color.White) }
                    }
                }
            }

            SearchPill(query, vm::setQuery, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            Spacer(Modifier.height(6.dp))

            if (query.isNotBlank()) {
                SearchResults(titleMatches, hits, query) { vm.setQuery(""); vm.openThread(it) }
            } else if (loaded && conversations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = Bit.Lilac, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No conversations yet", color = Bit.TextDim, fontSize = 16.sp)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 120.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(conversations, key = { it.threadId }) { c ->
                        ConversationRow(
                            c = c,
                            selected = c.threadId in selected,
                            modifier = Modifier.animateItem(placementSpec = Bit.placement),
                            onClick = {
                                if (selected.isNotEmpty()) {
                                    if (!selected.remove(c.threadId)) selected.add(c.threadId)
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                } else {
                                    vm.openThread(c.threadId)
                                }
                            },
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                if (!selected.remove(c.threadId)) selected.add(c.threadId)
                            },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selected.isEmpty() && query.isBlank(),
            enter = scaleIn(Bit.bouncy) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    vm.push(Screen.NewChat)
                },
                expanded = fabExpanded,
                icon = { Icon(Icons.AutoMirrored.Rounded.Chat, null) },
                text = { Text("Start chat", fontWeight = FontWeight.SemiBold) },
                containerColor = Bit.Lilac,
                contentColor = Bit.OnLilac,
                shape = RoundedCornerShape(20.dp),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Bit.SurfaceHigh,
            title = { Text(if (selected.size == 1) "Delete conversation?" else "Delete ${selected.size} conversations?") },
            text = { Text("This removes the messages from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteThreads(selected.toList())
                    selected.clear()
                    confirmDelete = false
                }) { Text("Delete", color = Bit.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    c: Conversation,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val scale by animateFloatAsState(if (selected) 0.97f else 1f, Bit.bouncy, label = "rowScale")
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) Bit.SurfaceHigh else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(c.recipients, 54.dp)
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = scaleIn(Bit.bouncy),
                exit = scaleOut(),
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                Icon(
                    Icons.Rounded.CheckCircle, null, tint = Bit.Lilac,
                    modifier = Modifier.size(22.dp).clip(CircleShape).background(Bit.Black),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = if (c.unread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    shortTime(context, c.date),
                    color = if (c.unread) Bit.Lilac else Bit.TextDim,
                    fontSize = 13.sp,
                    fontWeight = if (c.unread) FontWeight.Bold else FontWeight.Normal,
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.hasError) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = Bit.Error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    if (c.hasError) "Not sent" else c.snippet,
                    color = if (c.hasError) Bit.Error else if (c.unread) Color.White else Bit.TextDim,
                    fontSize = 15.sp,
                    fontWeight = if (c.unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (c.unread) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Bit.Lilac))
                }
            }
        }
    }
}

@Composable
private fun SearchResults(conversations: List<Conversation>, hits: List<SearchHit>, query: String, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        if (conversations.isNotEmpty()) {
            item { SectionLabel("Conversations") }
            items(conversations, key = { "c${it.threadId}" }) { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).combinedClickableSimple { onOpen(c.threadId) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(c.recipients, 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(c.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        if (hits.isNotEmpty()) {
            item { SectionLabel("Messages") }
            items(hits.size) { i ->
                val h = hits[i]
                Row(
                    Modifier.fillMaxWidth().combinedClickableSimple { onOpen(h.threadId) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(listOfNotNull(h.contact), 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(h.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(shortTime(context, h.date), color = Bit.TextDim, fontSize = 13.sp)
                        }
                        Text(highlight(h.body, query), color = Bit.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (conversations.isEmpty() && hits.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    Text("No results", color = Bit.TextDim)
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        color = Bit.Lilac,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp),
    )
}

private fun highlight(body: String, query: String): androidx.compose.ui.text.AnnotatedString {
    val b = androidx.compose.ui.text.AnnotatedString.Builder()
    val idx = body.indexOf(query, ignoreCase = true)
    if (idx < 0) {
        b.append(body)
    } else {
        val start = maxOf(0, idx - 30)
        val prefix = if (start > 0) "…" else ""
        b.append(prefix + body.substring(start, idx))
        b.pushStyle(androidx.compose.ui.text.SpanStyle(color = Bit.Lilac, fontWeight = FontWeight.Bold))
        b.append(body.substring(idx, idx + query.length))
        b.pop()
        b.append(body.substring(idx + query.length))
    }
    return b.toAnnotatedString()
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableSimple(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick)

