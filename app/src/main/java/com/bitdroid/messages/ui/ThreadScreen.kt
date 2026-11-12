package com.bitdroid.messages.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.telephony.SmsMessage
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SimCard
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitdroid.messages.data.Message
import com.bitdroid.messages.data.MessageStatus
import com.bitdroid.messages.data.Phone
import com.bitdroid.messages.data.SimInfo
import kotlinx.coroutines.launch

@Composable
fun ThreadScreen(vm: AppViewModel, threadId: Long) {
    val state by vm.thread.collectAsStateWithLifecycle()
    val sims by vm.sims.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var viewer by remember { mutableStateOf<Uri?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val draft = vm.drafts[threadId]
    var text by remember(threadId) { mutableStateOf(draft?.text ?: "") }
    val images = remember(threadId) { mutableStateListOf<Uri>().apply { draft?.images?.let(::addAll) } }
    DisposableEffect(threadId) { onDispose { vm.saveDraft(threadId, text, images.toList()) } }

    // Keys present when the thread first loaded don't animate in; newer ones do.
    val initialKeys = remember(threadId, state.loaded) { if (state.loaded) state.messages.map { it.key }.toHashSet() else null }
    val reversed = remember(state.messages) { state.messages.asReversed() }

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
    val subtitle = when {
        isGroup -> "${state.recipients.size} people"
        state.recipients.size == 1 && state.recipients[0].name != null -> Phone.format(context, state.recipients[0].address)
        else -> null
    }
    val blocked = state.recipients.isNotEmpty() && state.recipients.all { vm.isBlocked(it.address) }

    Column(Modifier.fillMaxSize().background(Bit.Black).imePadding()) {
        // ----------------------------------------------------------- top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(68.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            Avatar(state.recipients, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, color = Bit.TextDim, fontSize = 13.sp, maxLines = 1)
            }
            if (!isGroup && state.recipients.size == 1 && !Phone.isEmail(state.recipients[0].address)) {
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", state.recipients[0].address, null)))
                }) { Icon(Icons.Rounded.Call, "Call", tint = Color.White) }
            }
            Box {
                IconButton(onClick = { menu = true; vm.loadBlocked() }) { Icon(Icons.Rounded.MoreVert, "Menu", tint = Color.White) }
                DropdownMenu(menu, { menu = false }, containerColor = Bit.SurfaceHigh) {
                    if (blocked) {
                        DropdownMenuItem(text = { Text("Unblock") }, onClick = {
                            menu = false
                            state.recipients.forEach { vm.unblock(it.address) }
                        })
                    } else {
                        DropdownMenuItem(text = { Text(if (isGroup) "Block everyone" else "Block") }, onClick = {
                            menu = false
                            vm.block(state.recipients.map { it.address })
                        })
                    }
                    state.recipients.singleOrNull()?.lookupUri?.let { lookup ->
                        DropdownMenuItem(text = { Text("View contact") }, onClick = {
                            menu = false
                            context.startActivity(Intent(Intent.ACTION_VIEW, lookup))
                        })
                    }
                    DropdownMenuItem(text = { Text("Delete conversation", color = Bit.Error) }, onClick = {
                        menu = false
                        confirmDelete = true
                    })
                }
            }
        }

        // ---------------------------------------------------------- messages
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(reversed.size, key = { reversed[it].key }) { i ->
                    val m = reversed[i]
                    val older = reversed.getOrNull(i + 1)
                    val newer = reversed.getOrNull(i - 1)
                    val joinsOlder = older != null && sameRun(older, m)
                    val joinsNewer = newer != null && sameRun(m, newer)
                    val showHeader = older == null || m.date - older.date > 60 * 60 * 1000L
                    val isLastOutgoing = m.outgoing && reversed.take(i).none { it.outgoing }
                    val animate = initialKeys != null && m.key !in initialKeys
                    Column(Modifier.animateItem(placementSpec = Bit.placement)) {
                        if (showHeader) {
                            Text(
                                dayHeader(context, m.date),
                                color = Bit.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                        MessageBubble(
                            m = m,
                            isGroup = isGroup,
                            joinsOlder = joinsOlder && !showHeader,
                            joinsNewer = joinsNewer,
                            showStatus = isLastOutgoing || m.status == MessageStatus.FAILED,
                            animateIn = animate,
                            senderName = if (isGroup && !m.outgoing && !(joinsOlder && !showHeader)) {
                                state.recipients.firstOrNull { Phone.same(it.address, m.address) }?.name
                                    ?: Phone.format(context, m.address)
                            } else null,
                            sender = if (isGroup && !m.outgoing) state.recipients.firstOrNull { Phone.same(it.address, m.address) } else null,
                            onImage = { viewer = it },
                            onRetry = { vm.retry(m) },
                            onDownload = { vm.download(m) },
                            onCopy = {
                                val cm = context.getSystemService(ClipboardManager::class.java)
                                cm?.setPrimaryClip(ClipData.newPlainText("message", m.body))
                            },
                            onDelete = { vm.deleteMessage(m) },
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------- composer
        if (blocked) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("You blocked this number", color = Bit.TextDim)
            }
        } else {
            Composer(
                text = text,
                onText = { text = it },
                images = images,
                sims = sims,
                subId = state.subId,
                onSim = { vm.chooseSim(it) },
                onAttach = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
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
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { viewer = null }, contentAlignment = Alignment.Center) {
                if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else CircularProgressIndicator(color = Bit.Lilac)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Bit.SurfaceHigh,
            title = { Text("Delete conversation?") },
            text = { Text("This removes the messages from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteThreads(listOf(threadId))
                }) { Text("Delete", color = Bit.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun sameRun(older: Message, newer: Message): Boolean =
    older.outgoing == newer.outgoing &&
        (older.outgoing || Phone.same(older.address.ifBlank { "x" }, newer.address.ifBlank { "x" }) || (older.address.isBlank() && newer.address.isBlank())) &&
        newer.date - older.date < 3 * 60 * 1000L

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    m: Message,
    isGroup: Boolean,
    joinsOlder: Boolean,
    joinsNewer: Boolean,
    showStatus: Boolean,
    animateIn: Boolean,
    senderName: String?,
    sender: com.bitdroid.messages.data.Contact?,
    onImage: (Uri) -> Unit,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var menu by remember { mutableStateOf(false) }
    val appear = remember(m.key) { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(m.key) { if (appear.value < 1f) appear.animateTo(1f, Bit.bouncy) }
    val pressScale by animateFloatAsState(if (menu) 0.96f else 1f, Bit.bouncy, label = "press")

    val big = 22.dp
    val small = 6.dp
    val shape = if (m.outgoing) {
        RoundedCornerShape(topStart = big, bottomStart = big, topEnd = if (joinsOlder) small else big, bottomEnd = if (joinsNewer) small else big)
    } else {
        RoundedCornerShape(topEnd = big, bottomEnd = big, topStart = if (joinsOlder) small else big, bottomStart = if (joinsNewer) small else big)
    }
    val failed = m.status == MessageStatus.FAILED
    val bg = when {
        m.outgoing && failed -> Bit.Lilac.copy(alpha = 0.55f)
        m.outgoing -> Bit.Lilac
        else -> Bit.Theirs
    }
    val fg = if (m.outgoing) Bit.OnLilac else Color.White

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = if (joinsOlder) 2.dp else 8.dp)
            .graphicsLayer {
                val a = appear.value
                alpha = a.coerceIn(0f, 1f)
                scaleX = 0.85f + 0.15f * a
                scaleY = 0.85f + 0.15f * a
                translationY = (1f - a) * 40f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (m.outgoing) 1f else 0f, 1f)
            },
        horizontalAlignment = if (m.outgoing) Alignment.End else Alignment.Start,
    ) {
        if (senderName != null) {
            Text(senderName, color = Bit.TextDim, fontSize = 12.sp, modifier = Modifier.padding(start = 50.dp, bottom = 3.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            if (isGroup && !m.outgoing) {
                if (!joinsNewer) Avatar(listOfNotNull(sender ?: com.bitdroid.messages.data.Contact(m.address, null, null, null)), 32.dp)
                else Spacer(Modifier.width(32.dp))
                Spacer(Modifier.width(8.dp))
            }
            if (m.outgoing && failed) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = Bit.Error, modifier = Modifier.padding(end = 6.dp, bottom = 8.dp).size(20.dp))
            }
            Box {
                Column(
                    Modifier
                        .widthIn(max = 290.dp)
                        .scale(pressScale)
                        .clip(shape)
                        .background(bg)
                        .combinedClickable(
                            onClick = {
                                when {
                                    failed -> onRetry()
                                    m.status == MessageStatus.DOWNLOAD_FAILED || m.status == MessageStatus.PENDING_DOWNLOAD -> onDownload()
                                }
                            },
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                menu = true
                            },
                        ),
                ) {
                    m.attachments.forEach { a ->
                        if (a.mimeType.startsWith("image/")) {
                            val img = rememberImage(a.uri, 900)
                            Box(
                                Modifier
                                    .widthIn(min = 120.dp, max = 290.dp)
                                    .heightIn(min = 120.dp, max = 360.dp)
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(19.dp))
                                    .background(Bit.SurfaceHigh)
                                    .clickable { onImage(a.uri) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (img != null) {
                                    Image(img, "Picture", contentScale = ContentScale.Crop, modifier = Modifier.widthIn(max = 290.dp).heightIn(max = 360.dp))
                                } else {
                                    CircularProgressIndicator(Modifier.size(24.dp), color = Bit.Lilac, strokeWidth = 2.dp)
                                }
                            }
                        } else {
                            Text(
                                "📎 ${a.name ?: a.mimeType}",
                                color = fg, style = BubbleText,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                    when (m.status) {
                        MessageStatus.PENDING_DOWNLOAD, MessageStatus.DOWNLOADING -> Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Bit.Lilac, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Downloading message…", color = fg, style = BubbleText)
                        }
                        MessageStatus.DOWNLOAD_FAILED -> Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Download, null, tint = Bit.Lilac, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Tap to download", color = fg, style = BubbleText)
                        }
                        else -> {
                            val body = listOfNotNull(m.subject?.let { "$it\n" }, m.body.ifBlank { null }).joinToString("")
                            if (body.isNotBlank()) {
                                Text(body.trimEnd(), color = fg, style = BubbleText, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                            }
                        }
                    }
                }
                DropdownMenu(menu, { menu = false }, containerColor = Bit.SurfaceHigh) {
                    if (failed) DropdownMenuItem(text = { Text("Retry") }, onClick = { menu = false; onRetry() })
                    if (m.body.isNotBlank()) DropdownMenuItem(text = { Text("Copy text") }, onClick = { menu = false; onCopy() })
                    DropdownMenuItem(text = { Text("Delete", color = Bit.Error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
        AnimatedVisibility(showStatus, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            val (label, color) = when (m.status) {
                MessageStatus.SENDING -> "Sending…" to Bit.TextDim
                MessageStatus.SENT -> "Sent · ${shortTime(context, m.date)}" to Bit.TextDim
                MessageStatus.DELIVERED -> "Delivered · ${shortTime(context, m.date)}" to Bit.TextDim
                MessageStatus.FAILED -> "Not sent · Tap to retry" to Bit.Error
                else -> "" to Bit.TextDim
            }
            if (label.isNotEmpty()) {
                Text(
                    label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp, end = 6.dp).let { if (failed) it.clickable { onRetry() } else it },
                )
            }
        }
    }
}

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
    val sendScale by animateFloatAsState(if (canSend) 1f else 0.82f, Bit.bouncy, label = "send")
    var simMenu by remember { mutableStateOf(false) }
    val sim = sims.firstOrNull { it.subId == subId } ?: sims.firstOrNull()

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
        AnimatedVisibility(images.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
            ) {
                items(images, key = { it.toString() }) { uri ->
                    val img = rememberImage(uri, 300)
                    Box(Modifier.size(76.dp).animateItem(placementSpec = Bit.placement)) {
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Bit.SurfaceHigh)) {
                            if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(Bit.Black.copy(alpha = 0.75f))
                                .clickable { images.remove(uri) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            IconButton(onClick = onAttach, modifier = Modifier.padding(bottom = 2.dp)) {
                Icon(Icons.Rounded.AddPhotoAlternate, "Attach picture", tint = Bit.Lilac)
            }
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(Bit.Surface)
                    .padding(start = 6.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (sims.size > 1 && sim != null) {
                    Box {
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(Bit.SurfaceHigh)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    simMenu = true
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.SimCard, null, tint = Bit.Lilac, modifier = Modifier.size(16.dp))
                            Text("${sim.slotIndex + 1}", color = Bit.Lilac, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(simMenu, { simMenu = false }, containerColor = Bit.SurfaceHigh) {
                            sims.forEach { s ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("SIM ${s.slotIndex + 1} · ${s.label}", fontWeight = if (s.subId == sim.subId) FontWeight.Bold else FontWeight.Normal)
                                            s.number?.let { Text(it, color = Bit.TextDim, fontSize = 12.sp) }
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.SimCard, null, tint = if (s.subId == sim.subId) Bit.Lilac else Bit.TextDim) },
                                    onClick = { simMenu = false; onSim(s.subId) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                Box(Modifier.weight(1f).padding(vertical = 13.dp)) {
                    if (text.isEmpty()) {
                        Text(if (images.isEmpty()) "Text message" else "Add a caption", color = Bit.TextDim, fontSize = 16.sp)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = onText,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, lineHeight = 21.sp),
                        cursorBrush = SolidColor(Bit.Lilac),
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
                if (counter != null) {
                    Text(counter, color = Bit.TextDim, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .padding(bottom = 1.dp)
                    .size(48.dp)
                    .scale(sendScale)
                    .clip(CircleShape)
                    .background(if (canSend) Bit.Lilac else Bit.SurfaceHigh)
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowUpward, "Send", tint = if (canSend) Bit.OnLilac else Bit.TextDim)
            }
        }
    }
}
