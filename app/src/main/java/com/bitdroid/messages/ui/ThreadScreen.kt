package com.bitdroid.messages.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.telephony.SmsMessage
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitdroid.messages.data.Contact
import com.bitdroid.messages.data.Message
import com.bitdroid.messages.data.MessageStatus
import com.bitdroid.messages.data.Phone
import com.bitdroid.messages.data.SimInfo
import kotlinx.coroutines.launch

/** What the chat list shows: a day header, or a run of consecutive messages drawn as one block. */
private sealed interface ChatItem {
    val key: String

    data class Day(override val key: String, val date: Long) : ChatItem
    data class Run(override val key: String, val messages: List<Message>) : ChatItem {
        val outgoing get() = messages.first().outgoing
        val failed get() = messages.any { it.status == MessageStatus.FAILED }
        val last get() = messages.last()
    }
}

private fun sameSender(a: Message, b: Message): Boolean =
    a.outgoing == b.outgoing && (a.outgoing || Phone.same(a.address.ifBlank { "?" }, b.address.ifBlank { "?" }) || (a.address.isBlank() && b.address.isBlank()))

private fun buildItems(messages: List<Message>): List<ChatItem> {
    val out = ArrayList<ChatItem>()
    var run: MutableList<Message>? = null
    var prev: Message? = null
    fun flush() {
        run?.let { out += ChatItem.Run("r" + it.first().key, it.toList()) }
        run = null
    }
    for (m in messages) {
        val newDay = prev == null || m.date - prev.date > 60 * 60 * 1000L
        if (newDay) {
            flush()
            out += ChatItem.Day("d" + m.key, m.date)
        }
        val r = run
        val joins = r != null && sameSender(r.last(), m) && m.date - r.last().date < 10 * 60 * 1000L &&
            r.none { it.status == MessageStatus.FAILED } && m.status != MessageStatus.FAILED
        if (joins) r!!.add(m) else {
            flush()
            run = mutableListOf(m)
        }
        prev = m
    }
    flush()
    return out
}

@Composable
fun ThreadScreen(vm: AppViewModel, threadId: Long) {
    val state by vm.thread.collectAsStateWithLifecycle()
    val sims by vm.sims.collectAsStateWithLifecycle()
    val pins by vm.pins.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var viewer by remember { mutableStateOf<Uri?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val msgSel = remember(threadId) { Selection<String>() }
    var confirmMsgDelete by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = msgSel.active) { msgSel.exit() }

    val draft = vm.drafts[threadId]
    var text by remember(threadId) { mutableStateOf(draft?.text ?: "") }
    val images = remember(threadId) { mutableStateListOf<Uri>().apply { draft?.images?.let(::addAll) } }
    DisposableEffect(threadId) { onDispose { vm.saveDraft(threadId, text, images.toList()) } }

    val items = remember(state.messages) { buildItems(state.messages).asReversed() }
    // Runs present at first load don't animate in; new ones do.
    val initialKeys = remember(threadId, state.loaded) { if (state.loaded) items.map { it.key }.toHashSet() else null }

    LaunchedEffect(state.messages.size) {
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        images.addAll(uris.filter { it !in images })
    }

    val isGroup = state.recipients.size > 1
    val title = when {
        state.recipients.isEmpty() -> ""
        isGroup -> state.recipients.joinToString(", ") { it.name?.substringBefore(' ') ?: Phone.format(context, it.address) }
        else -> state.recipients[0].name ?: Phone.format(context, state.recipients[0].address)
    }
    val sim = sims.firstOrNull { it.subId == state.subId }
    val subtitle = buildList {
        if (sims.size > 1 && sim != null) add("SIM ${sim.slotIndex + 1}")
        when {
            isGroup -> add("${state.recipients.size} PEOPLE")
            state.recipients.size == 1 && state.recipients[0].name != null -> add(Phone.format(context, state.recipients[0].address))
        }
    }.joinToString(" · ")
    val blocked = state.recipients.isNotEmpty() && state.recipients.all { vm.isBlocked(it.address) }
    val pinned = threadId in pins

    Column(Modifier.fillMaxSize().imePadding()) {
        // ----------------------------------------------------------- header
        if (msgSel.active) SelectBar(
            count = msgSel.items.size,
            allSelected = state.messages.isNotEmpty() && msgSel.items.size == state.messages.size,
            onClose = { msgSel.exit() },
            onAll = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                if (msgSel.items.size == state.messages.size) msgSel.setAll(emptyList()) else msgSel.setAll(state.messages.map { it.key })
            },
            actions = listOf(
                SelectAction("Copy", Icons.Rounded.ContentCopy) {
                    val text = state.messages.filter { it.key in msgSel && it.body.isNotBlank() }.joinToString("\n") { it.body }
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("messages", text))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    msgSel.exit()
                },
                SelectAction("Delete", Icons.Rounded.Delete, Bit.Error) { confirmMsgDelete = true },
            ),
        ) else if (Bit.expressive) Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(50.dp).clip(CircleShape).background(Bit.Tile).clickable { vm.back() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth().clip(CircleShape).background(Bit.Tile)
                        .clickable { menu = true; vm.loadBlocked() }.padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(state.recipients, 50.dp, shape = Shapes.Ripple)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle.isNotBlank()) Text(subtitle, color = Bit.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Rounded.MoreVert, "Menu", tint = Bit.TextDim, modifier = Modifier.size(20.dp))
                }
                ThreadMenu(menu, { menu = false }, pinned, blocked, isGroup, state.recipients, threadId, vm, onDelete = { confirmDelete = true })
            }
            if (!isGroup && state.recipients.size == 1 && !Phone.isEmail(state.recipients[0].address)) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(58.dp).clip(Shapes.Clover).background(Bit.Light).clickable {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", state.recipients[0].address, null)))
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Call, "Call", tint = Bit.OnAccent, modifier = Modifier.size(22.dp)) }
            }
        } else Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            Box(Modifier.size(50.dp).clip(CircleShape).background(Bit.Accent), contentAlignment = Alignment.Center) {
                Avatar(state.recipients, 44.dp, Modifier.border(2.dp, Bit.Black, CircleShape))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = (-0.6).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(subtitle.uppercase(), style = mono(11f, FontWeight.Medium, spacing = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!isGroup && state.recipients.size == 1 && !Phone.isEmail(state.recipients[0].address)) {
                OutlinedCircle(onClick = {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", state.recipients[0].address, null)))
                }) { Icon(Icons.Rounded.Call, "Call", tint = Color.White, modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.width(8.dp))
            }
            Box {
                OutlinedCircle(onClick = { menu = true; vm.loadBlocked() }) { Icon(Icons.Rounded.MoreVert, "Menu", tint = Color.White, modifier = Modifier.size(20.dp)) }
                ThreadMenu(menu, { menu = false }, pinned, blocked, isGroup, state.recipients, threadId, vm, onDelete = { confirmDelete = true })
            }
        }
        if (!Bit.expressive) Box(Modifier.fillMaxWidth().height(1.dp).background(Bit.Hairline))

        // --------------------------------------------------------- messages
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.key }) { item ->
                    Box(Modifier.animateItem(placementSpec = Bit.placement)) {
                        when (item) {
                            is ChatItem.Day -> DayRule(dayHeader(context, item.date))
                            is ChatItem.Run -> RunBlock(
                                run = item,
                                isGroup = isGroup,
                                sender = if (isGroup && !item.outgoing) {
                                    state.recipients.firstOrNull { Phone.same(it.address, item.last.address) }
                                        ?: Contact(item.last.address, null, null, null)
                                } else null,
                                animateIn = initialKeys != null && item.key !in initialKeys,
                                onImage = { viewer = it },
                                onRetry = { vm.retry(it) },
                                onDownload = { vm.download(it) },
                                onCopy = { m ->
                                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("message", m.body))
                                },
                                onDelete = { vm.deleteMessage(it) },
                                sel = msgSel,
                            )
                        }
                    }
                }
            }
        }

        // --------------------------------------------------------- composer
        if (blocked) {
            Text(
                "YOU BLOCKED THIS NUMBER",
                style = mono(12f, spacing = 1.2f),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        } else {
            Composer(
                text = text,
                onText = { text = it },
                images = images,
                sims = sims,
                subId = state.subId,
                onSim = { vm.chooseSim(it) },
                onAttach = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onSend = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    vm.send(text.trim(), images.toList())
                    text = ""
                    images.clear()
                    scope.launch { listState.animateScrollToItem(0) }
                },
            )
        }
    }

    viewer?.let { uri ->
        Dialog(onDismissRequest = { viewer = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val img = rememberImage(uri, 2400)
            Box(Modifier.fillMaxSize().background(Bit.Black).clickable { viewer = null }, contentAlignment = Alignment.Center) {
                if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else CircularProgressIndicator(color = Bit.Accent)
            }
        }
    }

    if (confirmMsgDelete) {
        val n = msgSel.items.size
        AlertDialog(
            onDismissRequest = { confirmMsgDelete = false },
            containerColor = Bit.SurfaceHigh,
            title = { Text(if (n == 1) "Delete 1 message?" else "Delete $n messages?", fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) },
            text = { Text("This removes them from this phone.", color = Bit.TextDim) },
            confirmButton = {
                TextButton(onClick = {
                    state.messages.filter { it.key in msgSel }.forEach { vm.deleteMessage(it) }
                    confirmMsgDelete = false
                    msgSel.exit()
                }) { Text("Delete", color = Bit.Error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmMsgDelete = false }) { Text("Cancel", color = Color.White) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Bit.SurfaceHigh,
            title = { Text("Delete this chat?", fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) },
            text = { Text("This removes the messages from this phone.", color = Bit.TextDim) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteThreads(listOf(threadId)) }) { Text("Delete", color = Bit.Error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Color.White) } },
        )
    }
}

@Composable
private fun ThreadMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    pinned: Boolean,
    blocked: Boolean,
    isGroup: Boolean,
    recipients: List<Contact>,
    threadId: Long,
    vm: AppViewModel,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    DropdownMenu(open, onDismiss, containerColor = Bit.SurfaceHigh) {
        DropdownMenuItem(text = { Text(if (pinned) "Unpin" else "Pin") }, onClick = {
            onDismiss()
            if (!vm.togglePin(threadId)) Toast.makeText(context, "You can pin up to 6 chats", Toast.LENGTH_SHORT).show()
        })
        if (blocked) {
            DropdownMenuItem(text = { Text("Unblock") }, onClick = { onDismiss(); recipients.forEach { vm.unblock(it.address) } })
        } else {
            DropdownMenuItem(text = { Text(if (isGroup) "Block everyone" else "Block") }, onClick = { onDismiss(); vm.block(recipients.map { it.address }) })
        }
        recipients.singleOrNull()?.lookupUri?.let { lookup ->
            DropdownMenuItem(text = { Text("View contact") }, onClick = { onDismiss(); context.startActivity(Intent(Intent.ACTION_VIEW, lookup)) })
        }
        DropdownMenuItem(text = { Text("Delete chat", color = Bit.Error) }, onClick = { onDismiss(); onDelete() })
    }
}

@Composable
private fun OutlinedCircle(onClick: () -> Unit, size: Dp = 44.dp, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).border(2.dp, Bit.OutlineSoft, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun DayRule(label: String) {
    if (Bit.expressive) {
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier.clip(CircleShape).background(Bit.Pink).padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(24.dp).clip(Shapes.Sunny).background(Bit.PinkText))
                Spacer(Modifier.width(8.dp))
                Text(label, color = Bit.PinkText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Bit.Hairline))
        Text(label.uppercase(), style = mono(11f, spacing = 1.2f), modifier = Modifier.padding(horizontal = 10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Bit.Hairline))
    }
}

private fun Modifier.dashedBorder(width: Dp, color: Color, radius: Dp) = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RunBlock(
    run: ChatItem.Run,
    isGroup: Boolean,
    sender: Contact?,
    animateIn: Boolean,
    onImage: (Uri) -> Unit,
    onRetry: (Message) -> Unit,
    onDownload: (Message) -> Unit,
    onCopy: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    sel: Selection<String>,
) {
    val context = LocalContext.current
    val mine = run.outgoing
    val failed = run.failed
    val appear = remember(run.key) { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(run.key) { if (appear.value < 1f) appear.animateTo(1f, Bit.bouncy) }

    val shape = if (mine) RoundedCornerShape(26.dp, 26.dp, 8.dp, 26.dp) else RoundedCornerShape(26.dp, 26.dp, 26.dp, 8.dp)
    val fg = if (mine && !failed) Bit.OnAccent else Color.White
    val divider = if (mine && !failed) Bit.OnAccent.copy(alpha = 0.14f) else Bit.Outline

    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).graphicsLayer {
            val a = appear.value
            alpha = a.coerceIn(0f, 1f)
            scaleX = 0.86f + 0.14f * a
            scaleY = 0.86f + 0.14f * a
            translationY = (1f - a) * 46f
            transformOrigin = TransformOrigin(if (mine) 1f else 0f, 1f)
        },
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        if (isGroup && sender != null) {
            Text(
                (sender.name ?: Phone.format(context, sender.address)).uppercase(),
                style = mono(10.5f, spacing = 1f),
                modifier = Modifier.padding(start = 10.dp, bottom = 5.dp),
            )
        }
        if (Bit.expressive) {
            HomeRun(run, mine, failed, onImage, onRetry, onDownload, onCopy, onDelete, sel)
            return@Column
        }
        val blockMod = when {
            failed -> Modifier.dashedBorder(2.dp, Bit.Error, 22.dp).clip(RoundedCornerShape(22.dp))
            mine -> Modifier.clip(shape).background(Bit.Accent)
            else -> Modifier.clip(shape).border(2.dp, Bit.Outline, shape)
        }
        Column(
            Modifier.widthIn(max = 300.dp).then(blockMod)
                .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                .width(IntrinsicSize.Max),
        ) {
            run.messages.forEachIndexed { i, m ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.5.dp).background(divider))
                Segment(m, fg, first = i == 0, onImage, onRetry, onDownload, onCopy, onDelete, sel)
            }
            val last = run.last
            val status = when {
                !mine -> null
                failed -> null
                last.status == MessageStatus.SENDING -> "SENDING…"
                last.status == MessageStatus.DELIVERED -> "DELIVERED"
                else -> "SENT"
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val tagColor = if (mine && !failed) Bit.OnAccent.copy(alpha = 0.7f) else Bit.TextDim
                Text(shortClock(context, last.date), style = mono(10.5f, color = tagColor))
                if (status != null) {
                    Spacer(Modifier.width(14.dp))
                    Text(status, style = mono(10.5f, color = tagColor))
                }
            }
        }
        if (failed) {
            Row(
                Modifier.padding(top = 6.dp).clip(CircleShape).clickable { run.messages.filter { it.status == MessageStatus.FAILED }.forEach(onRetry) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Refresh, null, tint = Bit.Error, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("NOT SENT · TAP TO RETRY", style = mono(10.5f, color = Bit.Error))
            }
        }
    }
}

/** bitHome: each message is its own bubble. Theirs are purple-gray tiles, yours lavender pills, the newest one lighter. */
@Composable
private fun HomeRun(
    run: ChatItem.Run,
    mine: Boolean,
    failed: Boolean,
    onImage: (Uri) -> Unit,
    onRetry: (Message) -> Unit,
    onDownload: (Message) -> Unit,
    onCopy: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    sel: Selection<String>,
) {
    val context = LocalContext.current
    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        run.messages.forEachIndexed { i, m ->
            val last = i == run.messages.lastIndex
            val shape = when {
                mine && last -> RoundedCornerShape(26.dp, 26.dp, 8.dp, 26.dp)
                mine -> RoundedCornerShape(26.dp)
                last -> RoundedCornerShape(24.dp, 24.dp, 24.dp, 8.dp)
                else -> RoundedCornerShape(24.dp)
            }
            val bad = m.status == MessageStatus.FAILED
            val bg = when {
                bad -> Color.Transparent
                mine && last -> Bit.Light
                mine -> Bit.Accent
                else -> Bit.Tile
            }
            Box(
                Modifier.widthIn(max = 290.dp)
                    .then(if (bad) Modifier.dashedBorder(2.dp, Bit.Error, 22.dp) else Modifier)
                    .clip(shape).background(bg).width(IntrinsicSize.Max),
            ) {
                Segment(m, if (mine && !bad) Bit.OnAccent else Color.White, first = true, onImage, onRetry, onDownload, onCopy, onDelete, sel)
            }
        }
        val last = run.last
        val status = when {
            !mine || failed -> null
            last.status == MessageStatus.SENDING -> "Sending…"
            last.status == MessageStatus.DELIVERED -> "Delivered"
            else -> "Sent"
        }
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(shortClock(context, last.date), color = Bit.TextDim, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            if (status != null) {
                Text(" · $status", color = Bit.PinkText, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (failed) {
            Row(
                Modifier.clip(CircleShape).clickable { run.messages.filter { it.status == MessageStatus.FAILED }.forEach(onRetry) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Refresh, null, tint = Bit.Error, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Not sent · tap to retry", color = Bit.Error, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Segment(
    m: Message,
    fg: Color,
    first: Boolean,
    onImage: (Uri) -> Unit,
    onRetry: (Message) -> Unit,
    onDownload: (Message) -> Unit,
    onCopy: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    sel: Selection<String>,
) {
    val view = LocalView.current
    var menu by remember { mutableStateOf(false) }
    val picked = m.key in sel
    val press by animateFloatAsState(if (menu) 0.97f else 1f, Bit.bouncy, label = "press")
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().scale(press)
                .background(if (picked) fg.copy(alpha = 0.16f) else Color.Transparent)
                .combinedClickable(
                onClick = {
                    if (sel.active) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        sel.toggle(m.key)
                        return@combinedClickable
                    }
                    when (m.status) {
                        MessageStatus.FAILED -> onRetry(m)
                        MessageStatus.DOWNLOAD_FAILED, MessageStatus.PENDING_DOWNLOAD -> onDownload(m)
                        else -> Unit
                    }
                },
                onLongClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    if (sel.active) sel.toggle(m.key) else menu = true
                },
            ),
        ) {
            when (m.status) {
                MessageStatus.PENDING_DOWNLOAD, MessageStatus.DOWNLOADING -> Row(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(15.dp), color = Bit.Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("DOWNLOADING…", style = mono(11f, color = fg))
                }
                MessageStatus.DOWNLOAD_FAILED -> Row(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Download, null, tint = Bit.Accent, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("TAP TO DOWNLOAD", style = mono(11f, color = fg))
                }
                else -> {
                    m.attachments.forEach { a ->
                        if (a.mimeType.startsWith("image/")) {
                            val img = rememberImage(a.uri, 900)
                            Box(
                                Modifier.fillMaxWidth().widthIn(min = 220.dp).heightIn(min = 160.dp, max = 340.dp)
                                    .padding(if (first) 0.dp else 4.dp)
                                    .background(Bit.Raised).clickable { onImage(a.uri) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (img != null) {
                                    Image(img, "Picture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp))
                                } else {
                                    CircularProgressIndicator(Modifier.size(22.dp), color = Bit.Accent, strokeWidth = 2.dp)
                                }
                            }
                        } else {
                            Text(
                                "[ ${(a.name ?: a.mimeType).uppercase()} ]",
                                style = mono(11f, color = fg),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                    m.subject?.let {
                        Text(it, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
                    }
                    if (m.body.isNotBlank()) {
                        Text(
                            m.body.trimEnd(),
                            style = BubbleText,
                            color = fg,
                            fontWeight = if (fg == Bit.OnAccent) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
                        )
                    } else {
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
        DropdownMenu(menu, { menu = false }, containerColor = Bit.SurfaceHigh) {
            DropdownMenuItem(text = { Text("Select") }, onClick = { menu = false; sel.start(m.key) })
            if (m.status == MessageStatus.FAILED) DropdownMenuItem(text = { Text("Retry") }, onClick = { menu = false; onRetry(m) })
            if (m.body.isNotBlank()) DropdownMenuItem(text = { Text("Copy text") }, onClick = { menu = false; onCopy(m) })
            DropdownMenuItem(text = { Text("Delete", color = Bit.Error) }, onClick = { menu = false; onDelete(m) })
        }
    }
}

private fun shortClock(context: android.content.Context, millis: Long): String =
    android.text.format.DateFormat.getTimeFormat(context).format(millis).uppercase()

@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    images: MutableList<Uri>,
    sims: List<SimInfo>,
    subId: Int,
    onSim: (Int) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
) {
    val view = LocalView.current
    val canSend = text.isNotBlank() || images.isNotEmpty()
    val sendScale by animateFloatAsState(if (canSend) 1f else 0.84f, Bit.bouncy, label = "send")
    var simMenu by remember { mutableStateOf(false) }
    val sim = sims.firstOrNull { it.subId == subId } ?: sims.firstOrNull()

    Column(
        Modifier.fillMaxWidth()
            .background(
                if (Bit.expressive) Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0F1419).copy(alpha = 0.6f)))
                else Brush.verticalGradient(listOf(Color.Transparent, Bit.Black.copy(alpha = 0.75f), Bit.Black.copy(alpha = 0.85f))),
            )
            .navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
    ) {
        AnimatedVisibility(images.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 60.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
            ) {
                items(images, key = { it.toString() }) { uri ->
                    val img = rememberImage(uri, 300)
                    Box(Modifier.size(78.dp).animateItem(placementSpec = Bit.placement)) {
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).border(2.dp, Bit.Outline, RoundedCornerShape(18.dp)).background(Bit.Raised)) {
                            if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(Bit.Black.copy(alpha = 0.8f))
                                .clickable { images.remove(uri) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val ex = Bit.expressive
            Box(
                Modifier.padding(bottom = 3.dp).size(52.dp)
                    .then(if (ex) Modifier.clip(Shapes.Gem).background(Bit.Tile) else Modifier.clip(CircleShape).border(2.dp, Bit.OutlineSoft, CircleShape))
                    .clickable(onClick = onAttach),
                contentAlignment = Alignment.Center,
            ) {
                if (ex) Icon(Icons.Rounded.Add, "Attach picture", tint = Color.White, modifier = Modifier.size(24.dp))
                else Icon(Icons.Rounded.Image, "Attach picture", tint = Bit.Accent, modifier = Modifier.size(22.dp))
            }

            Row(
                Modifier.weight(1f).heightIn(min = 58.dp).clip(RoundedCornerShape(29.dp))
                    .then(if (ex) Modifier.background(Color(0xFF463E60)).border(2.5.dp, Bit.Accent.copy(alpha = 0.55f), RoundedCornerShape(29.dp)) else Modifier.border(2.5.dp, Color.White, RoundedCornerShape(29.dp)))
                    .padding(start = 18.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).padding(vertical = 16.dp)) {
                    if (text.isEmpty()) Text(if (images.isEmpty()) "Text message" else "Add a caption", color = Bit.TextDim, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold)
                    BasicTextField(
                        value = text,
                        onValueChange = onText,
                        textStyle = TextStyle(fontFamily = Body, color = Color.White, fontSize = 16.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(Bit.Accent),
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val counter = remember(text, images.isEmpty()) {
                    if (images.isNotEmpty() || text.length < 120) null else {
                        val r = SmsMessage.calculateLength(text, false)
                        if (r[0] > 1) "${r[2]}/${r[0]}" else "${r[2]}"
                    }
                }
                if (counter != null) Text(counter, style = mono(10.5f, FontWeight.Medium), modifier = Modifier.padding(horizontal = 4.dp))
                if (sims.size > 1 && sim != null) {
                    Box {
                        Box(
                            Modifier.height(30.dp).clip(CircleShape).background(Bit.Raised)
                                .clickable { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); simMenu = true }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("SIM ${sim.slotIndex + 1}", style = mono(11f, color = Bit.Accent)) }
                        DropdownMenu(simMenu, { simMenu = false }, containerColor = Bit.SurfaceHigh) {
                            sims.forEach { s ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("SIM ${s.slotIndex + 1} · ${s.label}", fontWeight = if (s.subId == sim.subId) FontWeight.Bold else FontWeight.Normal)
                                            s.number?.let { Text(it, style = mono(11f, FontWeight.Medium)) }
                                        }
                                    },
                                    onClick = { simMenu = false; onSim(s.subId) },
                                )
                            }
                        }
                    }
                }
            }

            Box(
                Modifier.padding(bottom = 0.dp).size(58.dp).scale(sendScale)
                    .clip(if (ex) Shapes.Triangle else CircleShape).background(if (canSend) Bit.Accent else Bit.Raised)
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.ArrowUpward, "Send", tint = if (canSend) Bit.OnAccent else Bit.TextDim,
                    modifier = Modifier.padding(top = if (ex) 8.dp else 0.dp).size(if (ex) 23.dp else 25.dp),
                )
            }
        }
    }
}
